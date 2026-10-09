#!/usr/bin/env bash
# Generates a self-signed TLS certificate for the FarmIQ gRPC-Web gateway
# (envoy/envoy.yaml) LOCAL DEVELOPMENT ONLY. See README.md in this directory
# for why this must never be used as-is in a real deployment.
#
# Re-run this any time to regenerate/rotate the dev cert — it's derived, not
# something to hand-edit.
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")"

openssl req -x509 -newkey rsa:2048 -nodes \
  -keyout server.key \
  -out server.crt \
  -days 365 \
  -subj "/CN=localhost" \
  -addext "subjectAltName=DNS:localhost,IP:127.0.0.1"

chmod 600 server.key
echo "Generated envoy/certs/server.crt and envoy/certs/server.key (self-signed, dev only, valid 365 days)."
