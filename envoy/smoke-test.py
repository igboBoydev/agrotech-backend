#!/usr/bin/env python3
"""
Proves the gRPC-Web gateway actually works, end to end, the same way a React
Native gRPC-Web client would use it: a plain HTTPS/1.1 request, hand-encoded as
a real gRPC-Web frame (no grpc/protobuf libraries — stdlib only, deliberately,
so this has zero setup beyond Python itself), sent through Envoy over TLS,
translated to real gRPC (h2c) against the Spring Boot backend, and the response
decoded back. Also fires enough requests at a rate-limited route to prove the
per-IP limit is actually enforced, not just present in the config.

Uses HTTPS with certificate verification DISABLED — this talks to the
self-signed dev cert in envoy/certs/ (see envoy/certs/README.md). That's fine
for this local smoke test; never disable verification like this in an app
that ships to a real device pointed at a real deployment.

Calls auth.AuthDataService/ValidateEmail — a public RPC (no JWT needed) with
the simplest possible shape (one string field in, two string fields out), and
one of the strict-rate-limited routes (10/min per IP) — good enough to prove
both the wire path and the rate limit without needing a JWT to bootstrap first.

Usage:
    python3 envoy/smoke-test.py [gateway_host:port] [email]

Exit code 0 = both checks passed. Non-zero = something's broken; the message
says what to check.
"""

import http.client
import ssl
import sys


def encode_varint(n: int) -> bytes:
    out = bytearray()
    while True:
        b = n & 0x7F
        n >>= 7
        if n:
            out.append(b | 0x80)
        else:
            out.append(b)
            return bytes(out)


def read_varint(data: bytes, i: int) -> tuple[int, int]:
    result = 0
    shift = 0
    while True:
        b = data[i]
        i += 1
        result |= (b & 0x7F) << shift
        if not (b & 0x80):
            return result, i
        shift += 7


def encode_string_field(field_number: int, value: str) -> bytes:
    tag = (field_number << 3) | 2  # wire type 2 = length-delimited
    value_bytes = value.encode("utf-8")
    return encode_varint(tag) + encode_varint(len(value_bytes)) + value_bytes


def decode_message(data: bytes) -> dict:
    fields = {}
    i = 0
    while i < len(data):
        tag, i = read_varint(data, i)
        field_number, wire_type = tag >> 3, tag & 0x7
        if wire_type == 2:
            length, i = read_varint(data, i)
            raw = data[i:i + length]
            i += length
            try:
                fields[field_number] = raw.decode("utf-8")
            except UnicodeDecodeError:
                fields[field_number] = raw
        elif wire_type == 0:
            value, i = read_varint(data, i)
            fields[field_number] = value
        else:
            break  # not needed for the simple messages this script exercises
    return fields


def make_grpc_web_frame(payload: bytes) -> bytes:
    return bytes([0x00]) + len(payload).to_bytes(4, "big") + payload


def parse_grpc_web_frames(body: bytes):
    frames = []
    i = 0
    while i < len(body):
        flag = body[i]
        length = int.from_bytes(body[i + 1:i + 5], "big")
        payload = body[i + 5:i + 5 + length]
        frames.append((flag, payload))
        i += 5 + length
    return frames


def parse_trailer_text(payload: bytes) -> dict:
    trailers = {}
    for line in payload.decode("utf-8", errors="replace").split("\r\n"):
        if ":" in line:
            key, _, value = line.partition(":")
            trailers[key.strip().lower()] = value.strip()
    return trailers


def insecure_ssl_context() -> ssl.SSLContext:
    # Dev-only: trusts the gateway's self-signed cert unconditionally. See the
    # module docstring — do not reuse this pattern against a real deployment.
    ctx = ssl.SSLContext(ssl.PROTOCOL_TLS_CLIENT)
    ctx.check_hostname = False
    ctx.verify_mode = ssl.CERT_NONE
    return ctx


def send_validate_email(host: str, port: int, email: str):
    request_message = encode_string_field(1, email)  # ValidateEmailRequest.email = 1
    body = make_grpc_web_frame(request_message)

    conn = http.client.HTTPSConnection(host, port, timeout=10, context=insecure_ssl_context())
    conn.request(
        "POST",
        "/auth.AuthDataService/ValidateEmail",
        body=body,
        headers={
            "Content-Type": "application/grpc-web+proto",
            "X-Grpc-Web": "1",
            "Accept": "application/grpc-web+proto",
        },
    )
    response = conn.getresponse()
    response_body = response.read()
    conn.close()
    return response, response_body


def check_round_trip(host: str, port: int, email: str) -> bool:
    print(f"-> POST https://{host}:{port}/auth.AuthDataService/ValidateEmail  (email={email!r})")

    try:
        response, response_body = send_validate_email(host, port, email)
    except ConnectionRefusedError:
        print(f"FAIL: connection refused on {host}:{port} — is Envoy running? "
              f"(envoy -c envoy/envoy.yaml)")
        return False
    except ssl.SSLError as e:
        print(f"FAIL: TLS handshake failed — {e}. Check envoy/certs/server.crt and "
              f"server.key exist (run envoy/certs/generate-dev-cert.sh) and envoy.yaml's "
              f"transport_socket points at them.")
        return False
    except Exception as e:  # noqa: BLE001 - smoke test, report anything and exit non-zero
        print(f"FAIL: could not complete the request — {e}")
        return False

    print(f"<- HTTP {response.status} {response.reason}")

    if response.status != 200:
        print(f"FAIL: expected HTTP 200 from Envoy, got {response.status}. "
              f"Check Envoy is pointed at the right upstream and the backend is running.")
        return False

    frames = parse_grpc_web_frames(response_body)
    data_frames = [p for flag, p in frames if not (flag & 0x80)]
    trailer_frames = [p for flag, p in frames if flag & 0x80]

    if not data_frames:
        print("FAIL: no gRPC-Web data frame in the response — the translation "
              "didn't produce a message. Check the backend is actually running on "
              "server.port (8080 by default — see envoy.yaml's cluster comment).")
        return False

    decoded = decode_message(data_frames[0])
    message_field = decoded.get(1, "<missing>")
    code_field = decoded.get(2, "<missing>")
    print(f"<- ValidateEmailResponse decoded: message={message_field!r} code={code_field!r}")

    grpc_status = None
    if trailer_frames:
        trailers = parse_trailer_text(trailer_frames[-1])
        grpc_status = trailers.get("grpc-status")
        print(f"<- grpc-status trailer: {grpc_status!r} ({trailers.get('grpc-message', '')!r})")

    if grpc_status not in (None, "0"):
        print(f"FAIL: backend returned grpc-status {grpc_status} (not OK).")
        return False

    print("PASS — the gRPC-Web gateway round-tripped a real RPC over TLS "
          "(Envoy(TLS) -> h2c -> Spring gRPC service -> Envoy -> HTTPS/1.1 response).\n")
    return True


def check_rate_limit(host: str, port: int) -> bool:
    # ValidateEmail is one of the strictly rate-limited public routes: 10 requests/minute
    # per source IP (envoy/envoy.yaml). Firing more than that in quick succession should
    # get at least one HTTP 429 back before we're done.
    attempts = 15
    print(f"-> Firing {attempts} rapid ValidateEmail requests to test the 10/min per-IP limit...")

    saw_429 = False
    for i in range(attempts):
        try:
            response, _ = send_validate_email(host, port, f"rate-limit-test-{i}@example.com")
        except Exception as e:  # noqa: BLE001
            print(f"FAIL: request {i + 1} errored — {e}")
            return False
        status = response.status
        marker = " <- 429 here" if status == 429 else ""
        print(f"   request {i + 1}/{attempts}: HTTP {status}{marker}")
        if status == 429:
            saw_429 = True
            break

    if not saw_429:
        print(f"FAIL: sent {attempts} requests in quick succession and never got a 429. "
              f"The rate limit isn't being enforced — check the local_ratelimit filter "
              f"config and that it's in the http_filters chain before the router.")
        return False

    print("PASS — the per-IP rate limit on the public auth routes is actually enforced, "
          "not just configured.\n")
    return True


def main():
    target = sys.argv[1] if len(sys.argv) > 1 else "localhost:8081"
    email = sys.argv[2] if len(sys.argv) > 2 else "grpc-web-smoke-test@example.com"
    host, _, port = target.partition(":")
    port = int(port) if port else 8081

    round_trip_ok = check_round_trip(host, port, email)
    rate_limit_ok = check_rate_limit(host, port)

    if round_trip_ok and rate_limit_ok:
        print("ALL CHECKS PASSED")
        sys.exit(0)
    else:
        print("ONE OR MORE CHECKS FAILED — see above.")
        sys.exit(1)


if __name__ == "__main__":
    main()
