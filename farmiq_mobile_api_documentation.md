# FarmIQ — Mobile API Documentation

**Audience:** the team building the React Native mobile app against this backend.
**Scope:** documents the gRPC API as actually implemented in this repository today — 8 services, 68 RPCs, all in a single Java/Spring Boot process. It does **not** describe `farmiq_api_specification.html` or `agritech_microservices_architecture.html` in this same repo root — those are earlier aspirational sketches (REST gateway, Kafka, polyglot microservices) superseded by the 2026-08-20 decision to build everything as one Java app. If you're looking at those files for endpoint names, stop — they don't match what's running. This document is the source of truth for the mobile build.

---

## 1. What you're integrating with

- **Transport:** real gRPC on the backend, fronted by a gRPC-Web gateway (Envoy) for mobile — see below. No REST gateway, no GraphQL layer anywhere in this codebase.
- **Backend gRPC port — correction from an earlier version of this doc:** gRPC is served **on the same port as HTTP/actuator traffic**, `server.port` (`8080` by default, `application.yaml`). The `grpc.server.port: 3030` key that appears in `application-local.yaml`/`application-prod.yaml` looks like it configures a separate dedicated gRPC port — **it doesn't**. It's a misnamed/dead property (the real Spring property is `spring.grpc.server.port`, note the `spring.` prefix); gRPC actually rides Tomcat as a servlet (`spring.grpc.server.servlet.enabled` defaults to `true`). Confirmed with `grpcurl` while building the gateway below — nothing listens on 3030 at all. If you see `3030` referenced anywhere else (including possibly stale copies of this doc), it's wrong.
- **TLS:** terminates **at the gateway** (below), not on the backend itself — the backend still has zero TLS story of its own (Envoy→backend stays plaintext h2c on localhost, the standard edge-termination pattern). The gateway currently runs a **self-signed dev certificate** — real TLS protection, but not yet a certificate any device trusts out of the box. See the gateway section below and `envoy/certs/README.md` before any real-device build ships.
- **Proto sources:** `src/main/proto/**/*.proto` in this repo — compile these directly with `protoc` (or your codegen tool of choice) to generate client stubs. They are the actual contract; nothing here is paraphrased from memory.

### Connecting from React Native: the gRPC-Web gateway

React Native's JS runtime doesn't support HTTP/2, which real gRPC requires — plain `@grpc/grpc-js` **will not work** in an RN app. This is solved: **`envoy/envoy.yaml`** in this repo runs an Envoy proxy that translates gRPC-Web (plain HTTP/1.1, works from `fetch`/`XHR` — what RN gRPC-Web client libraries use) into real gRPC against the backend, and back — **over TLS**, with per-client rate limiting.

- **Point your mobile app at the gateway, never directly at the backend.** Local dev: `https://<your-machine's-LAN-IP>:8081` — **`https://`, not `http://`.** On a physical device/simulator this needs your dev machine's actual network IP, not `localhost`.
- **Run it locally:** `brew install envoy` (or `docker compose up`, see `docker-compose.yml` — needs a one-line edit first, commented in that file). First time only (or to rotate): `envoy/certs/generate-dev-cert.sh`. Then `envoy -c envoy/envoy.yaml`. The backend itself must already be running (`mvn spring-boot:run`).
- **Client library:** any gRPC-Web JS client (e.g. `grpc-web`'s own generated stubs from `protoc-gen-grpc-web`, or a thinner hand-rolled client) works against this gateway — it speaks standard gRPC-Web, nothing FarmIQ-specific.
- **Certificate trust — read this before your app fails to connect with a TLS error:** the shipped cert (`envoy/certs/server.crt`) is self-signed, valid for `localhost`/`127.0.0.1` only, and not trusted by any device or OS by default. For local dev you have two options: (a) disable certificate verification in your gRPC-Web client — **dev only, never in a build that could reach a real user**, this defeats TLS entirely if left in; or (b) install `envoy/certs/server.crt` as a trusted certificate on your simulator/device. Before any real deployment, replace the cert with one from a real CA (Let's Encrypt or your org's CA) issued for the gateway's real hostname — see `envoy/certs/README.md`. `envoy/smoke-test.py` uses option (a) for its own testing, with an explicit warning attached; treat that as an example of what *not* to ship, not a pattern to copy into the app.
- **Rate limiting — design your retry/backoff around this:** the gateway limits requests **per source IP**, in two tiers. The public, unauthenticated routes (`FarmerLogin`, `GenerateToken`, `ValidateToken`, `ValidateEmail`, `ValidatePhone`, `CreateFarmer`, `CreateBusiness`) are capped at **10 requests/minute per IP** — deliberately strict, since nothing else gates those calls (anti brute-force/OTP-spam/signup-abuse). Every other RPC (all of which already require a JWT) gets a more generous **120 requests/minute per IP** (~2/s sustained). A rate-limited request comes back as a plain HTTP **`429`**, with `x-ratelimit-limit`/`x-ratelimit-remaining`/`x-ratelimit-reset` headers your client can use to back off intelligently instead of just failing. Both numbers are starting points, not load-tested figures — if legitimate usage patterns hit these limits in practice, that's a signal to tune `envoy/envoy.yaml`, not a bug to work around client-side.
- **Auth still works exactly the same:** gRPC-Web maps HTTP headers 1:1 onto gRPC metadata, so send the JWT as a normal `authorization: Bearer <token>` HTTP header (section 2) — the backend's auth interceptor doesn't know or care that the request came through a gRPC-Web translation.
- **Verify it's working:** `python3 envoy/smoke-test.py <gateway-host>:8081` — a dependency-free script that sends a real RPC over TLS through the full path and confirms it decodes correctly, then fires enough requests to confirm the rate limit is actually enforced. Run this after any change to the gateway config or the backend.
- **Not yet done:** CORS on the gateway is wide open (`*`) for local dev convenience — needs tightening before a real deployment, flagged directly in `envoy/envoy.yaml`'s comments. The backend itself (as opposed to the gateway) still has no TLS of its own — fine as-is since that hop never leaves localhost, but worth knowing if the deployment topology ever changes.

---

## 2. Authentication

- **Login RPC:** `auth.AuthDataService/FarmerLogin` — `{email, password}` → `{token, message, code}` on success (`code = "200"`). The token is a signed JWT (HS256), default 1-hour expiry (`app.jwt.expiration-ms`, configurable).
- **How to send it:** every protected RPC call must include a gRPC metadata entry:
  ```
  authorization: Bearer <token>
  ```
  Missing, malformed, or expired/invalid tokens are rejected with gRPC status `UNAUTHENTICATED` **before the call reaches any business logic** (enforced by `JwtAuthServerInterceptor`, a global interceptor covering all 8 services).
- **Public RPCs (no token required)** — everything else needs a valid bearer token:
  - `auth.AuthDataService/FarmerLogin`
  - `auth.AuthDataService/GenerateToken` (issues an OTP)
  - `auth.AuthDataService/ValidateToken` (redeems an OTP)
  - `auth.AuthDataService/ValidateEmail` (availability check)
  - `auth.AuthDataService/ValidatePhone` (availability check)
  - `users.UsersService/CreateFarmer` (signup)
  - `users.UsersService/CreateBusiness` (signup)
- **What's in the token today, and a real caveat:** claims are `{userId: <internal numeric Users.id>, role: FARMER|BUSINESS|ADMIN}`, subject = email. **`userId` is not the same as the `uuid` every other RPC in this API uses to identify a farmer or business** (see section 3). There is currently no RPC-level authorization that checks a caller's token against the resource they're acting on — a valid token authenticates *a* caller, it doesn't yet prove they own the farm/loan/listing they're calling about. Don't build client-side assumptions that the backend is enforcing per-resource ownership; it isn't yet.
- **`ChangeFarmerPassword`** (`auth.AuthDataService`) is a stub — it always returns `code = "501"`. Don't build a working "change password" screen against it yet.

---

## 3. Conventions used across every service

- **IDs are UUID strings**, not the internal database numeric IDs, for every "get/update by id" RPC (e.g. `farm_id`, `loan_id`, `listing_id`, `subscription_id`). Generate/store these as opaque strings client-side.
  - **One documented exception:** `FarmProducts` (the sellable crop/produce item) has no `uuid` — `Products.id` (in `users/products.proto`) and `Marketplace­Listing.product_id` are both the internal numeric id, sent as a string. This was a pre-existing inconsistency found while building Marketplace, not fixed (see `CLAUDE.md`'s Phase 3b notes) — treat it as a special case, don't assume it behaves like every other id field.
- **Every RPC response carries its own `code`/`message` fields** in addition to the gRPC transport status — e.g. a business-rule rejection (like "listing is not active") comes back as gRPC status `FAILED_PRECONDITION` with `code = "409"` and a human-readable `message`. Treat the gRPC status as the thing to branch logic on; treat `message` as user-facing/debug text.
- **gRPC status codes actually used** across this API: `OK`, `NOT_FOUND` (404 equivalent), `FAILED_PRECONDITION` (409/412 equivalent — invalid state transition, e.g. approving an already-decided loan), `PERMISSION_DENIED` (403 — e.g. a listing product doesn't belong to the calling farmer, or an Analytics subscription tier is too low), `INVALID_ARGUMENT` (400 — e.g. an unparseable product id), `ALREADY_EXISTS` (409 — duplicate signup email/phone), `UNAUTHENTICATED` (401 — see section 2).
- **Dates** are plain strings (`YYYY-MM-DD`), not a structured timestamp type — send/parse accordingly.
- **Money and quantity fields are `double`**, not a fixed-point/decimal proto type — the server converts to `BigDecimal` internally, but the wire format is a plain double. Be careful with floating-point display formatting client-side (e.g. round to 2dp for currency in the UI).
- **Enums are prefixed per-type** (e.g. `LOAN_APPLICATION_PENDING`, not `PENDING`) — this is a protobuf constraint (enum values share their package's namespace), not a naming quirk to work around. Full enum reference is in section 12.

---

## 4. Auth & OTP service — `auth.AuthDataService` (`auth.proto`)

| RPC | Request → Response | Auth | Notes |
|---|---|---|---|
| `FarmerLogin` | `FarmerLoginRequest{email, password}` → `FarmerLoginResponse{token, message, code}` | Public | `code="401"` on bad credentials, `"403"` if account disabled/locked. |
| `GenerateToken` | `GenerateTokenRequest{email}` → `GenerateTokenResponse{token, refresh_token}` | Public | Issues a 6-digit OTP (email/SMS delivery is out of scope of this RPC — see Notification service, section 10, which is **not yet wired to fire automatically** from this call). |
| `ValidateToken` | `TokenRequest{token, validateType: ValidateType}` → `TokenResponse{message, code}` | Public | `validateType` is `PHONE\|EMAIL\|LOGIN` (see `users/enum.proto`). Single-use, expiry-checked. |
| `ValidateEmail` | `ValidateEmailRequest{email}` → `ValidateEmailResponse{message, code}` | Public | Availability check — use before signup to show inline "email taken" feedback. |
| `ValidatePhone` | `ValidatePhoneRequest{phone}` → `ValidatePhoneResponse{message, code}` | Public | Same, for phone. |
| `ChangeFarmerPassword` | `ChangeFarmerPasswordRequest{password}` → `ChangeFarmerPasswordResponse{message, code}` | Requires token | **Stub — always returns `code="501"`.** No way to identify the caller yet (see section 2). |

---

## 5. Users / Onboarding service — `users.UsersService` (`users/service.proto`)

Covers farmer signup, KYC, farms, farm products, and business (buyer-side) accounts.

| RPC | Request → Response | Auth | Notes |
|---|---|---|---|
| `CreateFarmer` | `CreateFarmerRequest{first_name, last_name, dob, home_address, lga, state, country, email, phone, password}` → `CreateFarmerResponse{code, message}` | Public | Creates the linked `Users` login row too. `code="401"` on duplicate email/phone. |
| `AddFarmerKyc` | `AddFarmerKycRequest{id (farmer uuid), documentsAndIdentity: repeated Document, corporate_customer_address: CorporateUserAddress, signature, bvn, tin, passport_photograph, kyc_level, tier, tin_verified, driving_license, international_passport_number, voters_card_number}` → `AddFarmerKycResponse{code, message}` | Requires token | `code="404"` if farmer not found. |
| `GetFarmerProfile` | `FarmerProfileRequest{user_id (farmer uuid)}` → `FarmerProfileResponse{farmerDetails: Farmer}` | Requires token | `Farmer` nests KYC and all of the farmer's farms. |
| `AddFarm` | `AddFarmRequest{address, lga, state, country, land_size, is_lease, is_own_property, lease_years, num_of_employees, employees: repeated Employee, user_id (farmer uuid), plant_type, plant_season_start_date, plant_season_end_date, farm_type}` → `AddFarmResponse{code, message}` | Requires token | |
| `GetFarms` | `GetFarmsRequest{user_id (farmer uuid)}` → `GetFarmsResponse{farms: repeated Farm}` | Requires token | |
| `CheckKYCStatus` | `KYCRequest{user_id (farmer uuid)}` → `KYCStatus{verification_status: VerificationStatus, message}` | Requires token | Aggregate status computed from the farmer's documents: no KYC → `NULL`, no documents yet → `PENDING`, any `REJECTED` → `REJECTED`, all `APPROVED` → `APPROVED`, else `PENDING`. |
| `GetFarmProducts` | `GetProductsRequest{farm_id}` → `GetProductsResponse{product: repeated Products}` | Requires token | |
| `AddFarmProduct` | `AddFarmProductRequest{farm_id, product_name, category, product_type, description, quantity, unit, price_per_unit, harvest_date, expiry_date, is_available}` → `AddFarmProductResponse{code, message}` | Requires token | `Products.id` in responses is the **internal numeric id**, not a uuid — see section 3's exception. |
| `CreateBusiness` | `CreateBusinessRequest{company_name, company_registration_no, date_of_registration, country_of_registration, company_address, state, city, country, last_name, bvn, tin, address}` → `CreateBusinessResponse{code, message}` | Public | `code="409"` on duplicate registration number. |
| `GetBusinessProfile` | `GetBusinessProfileRequest{user_id (business uuid)}` → `GetBusinessProfileResponse{...54 fields declared...}` | Requires token | **Only 12 fields are actually populated**: `company_name, company_registration_no, date_of_registration, country_of_registration, company_address, state, city, country, last_name, bvn, tin, address`. The other 42 (KYB fields like `principal_officer_*`, `nin`, `id_type`, etc.) are proto zero-values — this is a known, documented gap (no full KYB flow built yet), not a bug. Don't build UI that assumes those fields are populated. |

---

## 6. Fintech service — `fintech.FintechService` (`fintech/service.proto`)

Loans, insurance, and credit scoring. Credit scoring feeds loan pricing and MFB referral fees — these three aren't independent features.

| RPC | Request → Response | Auth | Notes |
|---|---|---|---|
| `ComputeCreditScore` | `ComputeCreditScoreRequest{farmer_id}` → `ComputeCreditScoreResponse{code, message, score: CreditScoreRecord}` | Requires token | Heuristic 300–850 score from KYC/validation/farm-activity/loan-history signals (not ML — documented as a rule-based heuristic in code). Persists a new record each call (history, not overwrite). Band: `POOR/FAIR/GOOD/VERY_GOOD/EXCELLENT`. |
| `GetCreditScore` | `GetCreditScoreRequest{farmer_id}` → `GetCreditScoreResponse{code, message, score}` | Requires token | Latest record. `code="404"` if none computed yet. |
| `ApplyForLoan` | `ApplyForLoanRequest{farmer_id, requested_amount, term_months, purpose}` → `ApplyForLoanResponse{code, message, application: LoanApplication}` | Requires token | `interest_rate` is **server-computed** from the farmer's latest credit band (12% `EXCELLENT` → 30% `POOR`; defaults to 25% if no score computed yet) — not caller-supplied. |
| `ReviewLoanApplication` | `ReviewLoanApplicationRequest{application_id, approve: bool}` → `ReviewLoanApplicationResponse{code, message, account: LoanAccount}` | Requires token | Approve creates a `LoanAccount` (`principal × (1 + rate)` as the total repayable). `code="409"` if already decided. |
| `RecordRepayment` | `RecordRepaymentRequest{loan_id, amount}` → `RecordRepaymentResponse{code, message, account}` | Requires token | Balance hits zero → account flips to `LOAN_COMPLETED`. |
| `GetLoan` | `GetLoanRequest{loan_id}` → `GetLoanResponse{code, message, account}` | Requires token | Includes nested `repayments: repeated LoanRepayment`. |
| `GetLoansByFarmer` | `GetLoansByFarmerRequest{farmer_id}` → `GetLoansByFarmerResponse{accounts: repeated LoanAccount}` | Requires token | |
| `CreatePolicy` | `CreatePolicyRequest{farmer_id, policy_type, coverage_amount, premium_amount, start_date, end_date}` → `CreatePolicyResponse{code, message, policy: InsurancePolicy}` | Requires token | `commission_amount` is a fixed 20% of `premium_amount`, server-computed. |
| `FileClaim` | `FileClaimRequest{policy_id, claim_amount, description}` → `FileClaimResponse{code, message, claim: InsuranceClaim}` | Requires token | `code="409"` if policy isn't `POLICY_ACTIVE`. |
| `ReviewClaim` | `ReviewClaimRequest{claim_id, approve: bool, payout_amount}` → `ReviewClaimResponse{code, message, claim}` | Requires token | Terminal states: `CLAIM_APPROVED` / `CLAIM_REJECTED` — there is **no further "paid out" RPC**, disbursement isn't modeled. |
| `GetPolicy` | `GetPolicyRequest{policy_id}` → `GetPolicyResponse{code, message, policy}` | Requires token | Includes nested `claims: repeated InsuranceClaim`. |
| `GetPoliciesByFarmer` | `GetPoliciesByFarmerRequest{farmer_id}` → `GetPoliciesByFarmerResponse{policies: repeated InsurancePolicy}` | Requires token | |
| `ReferToMfb` | `ReferToMfbRequest{farmer_id, mfb_name}` → `ReferToMfbResponse{code, message, referral: MfbReferral}` | Requires token | **Requires an existing credit score** — `code="412"` (`FAILED_PRECONDITION`) otherwise. `referral_fee` scales ₦3,000 (`POOR`) → ₦10,000 (`EXCELLENT`) by band. |

---

## 7. Marketplace service — `marketplace.MarketplaceService` (`marketplace/service.proto`)

Listings, orders, and commission. "Escrow" here is bookkeeping state only — there's no real payment gateway anywhere in this backend, so no funds actually move.

| RPC | Request → Response | Auth | Notes |
|---|---|---|---|
| `CreateListing` | `CreateListingRequest{farmer_id, product_id, price_per_unit, quantity_available}` → `CreateListingResponse{code, message, listing: MarketplaceListing}` | Requires token | `product_id` is `FarmProducts`' numeric id (section 3 exception). `code="403"` if the product doesn't belong to a farm owned by `farmer_id`. |
| `GetListing` | `GetListingRequest{listing_id}` → `GetListingResponse{code, message, listing}` | Requires token | |
| `GetListingsBySeller` | `GetListingsBySellerRequest{farmer_id}` → `GetListingsBySellerResponse{listings: repeated MarketplaceListing}` | Requires token | |
| `GetActiveListings` | `GetActiveListingsRequest{}` → `GetActiveListingsResponse{listings}` | Requires token | Marketplace-wide browse — all sellers, `LISTING_ACTIVE` only. This is your product-discovery/search feed for the buyer-side UI. |
| `PlaceOrder` | `PlaceOrderRequest{listing_id, buyer_id, buyer_type: BuyerType, quantity}` → `PlaceOrderResponse{code, message, order: MarketplaceOrder}` | Requires token | `buyer_id` is **any** farmer or business uuid — not FK-enforced. `buyer_type` is `BUYER_FARMER\|BUYER_BUSINESS`. `commission_amount` = 3.5% of `total_amount`, server-computed. `code="409"` if `quantity` exceeds what's available. |
| `ConfirmOrder` | `ConfirmOrderRequest{order_id, accept: bool}` → `ConfirmOrderResponse{code, message, order}` | Requires token | Seller-side accept/reject. Reject restores the listing's quantity. |
| `CompleteOrder` | `CompleteOrderRequest{order_id}` → `CompleteOrderResponse{code, message, order}` | Requires token | Only valid from `ORDER_ACCEPTED`. Sets `ESCROW_RELEASED` (bookkeeping only, per the note above). |
| `CancelOrder` | `CancelOrderRequest{order_id}` → `CancelOrderResponse{code, message, order}` | Requires token | Valid from `ORDER_PENDING`/`ORDER_ACCEPTED` only; restores listing quantity. |
| `GetOrder` | `GetOrderRequest{order_id}` → `GetOrderResponse{code, message, order}` | Requires token | |
| `GetOrdersByBuyer` | `GetOrdersByBuyerRequest{buyer_id}` → `GetOrdersByBuyerResponse{orders: repeated MarketplaceOrder}` | Requires token | This is your buyer-side "my orders" screen. |
| `GetOrdersBySeller` | `GetOrdersBySellerRequest{farmer_id}` → `GetOrdersBySellerResponse{orders}` | Requires token | Seller-side "my sales" screen. |

---

## 8. Telemetry service — `telemetry.TelemetryService` (`telemetry/service.proto`)

GPS, sensor readings, and satellite vegetation-index ingestion. All attach to a `farm_id` (uuid).

| RPC | Request → Response | Auth | Notes |
|---|---|---|---|
| `RecordSensorReading` | `RecordSensorReadingRequest{farm_id, sensor_type: SensorType, value, unit, source: DataSource, recorded_at}` → `RecordSensorReadingResponse{code, message, reading: SensorReading}` | Requires token | `recorded_at` defaults to today only if left blank — it represents when the physical reading was taken, so send the real timestamp if you have one (e.g. from an IoT device or IoT-gateway app screen). |
| `GetSensorReadings` | `GetSensorReadingsRequest{farm_id, sensor_type}` → `GetSensorReadingsResponse{readings: repeated SensorReading}` | Requires token | Filtered by sensor type — no "all types" option; call once per type or use the summary RPC below. |
| `GetLatestSensorReading` | `GetLatestSensorReadingRequest{farm_id, sensor_type}` → `GetLatestSensorReadingResponse{code, message, reading}` | Requires token | `code="404"` if nothing recorded for that type yet. |
| `RecordGpsLocation` | `RecordGpsLocationRequest{farm_id, latitude, longitude, accuracy_meters, recorded_at}` → `RecordGpsLocationResponse{code, message, location: GpsLocation}` | Requires token | |
| `GetGpsHistory` | `GetGpsHistoryRequest{farm_id}` → `GetGpsHistoryResponse{locations: repeated GpsLocation}` | Requires token | |
| `GetLatestGpsLocation` | `GetLatestGpsLocationRequest{farm_id}` → `GetLatestGpsLocationResponse{code, message, location}` | Requires token | |
| `RecordVegetationIndex` | `RecordVegetationIndexRequest{farm_id, ndvi_value, source, captured_at}` → `RecordVegetationIndexResponse{code, message, index: VegetationIndex}` | Requires token | `health_band` is **server-computed** from `ndvi_value` (`<0.2 POOR, <0.4 MODERATE, <0.6 GOOD, else EXCELLENT`) — don't send it, it's ignored/not a request field. |
| `GetVegetationHistory` | `GetVegetationHistoryRequest{farm_id}` → `GetVegetationHistoryResponse{indices: repeated VegetationIndex}` | Requires token | |
| `GetLatestVegetationIndex` | `GetLatestVegetationIndexRequest{farm_id}` → `GetLatestVegetationIndexResponse{code, message, index}` | Requires token | |
| `GetFarmTelemetrySummary` | `GetFarmTelemetrySummaryRequest{farm_id}` → `GetFarmTelemetrySummaryResponse{code, message, summary: FarmTelemetrySummary}` | Requires token | **The one call your farm dashboard screen should use** — bundles latest GPS fix, latest vegetation reading, and the latest reading per sensor type in a single round trip. Use proto's generated `has*()` presence checks (`hasLatestGps()`, `hasLatestVegetation()`) — any stream with no data yet is simply absent, not zero-filled. |

---

## 9. Notification service — `notification.NotificationService` (`notification/service.proto`)

SMS/push/WhatsApp. **No real gateway integration exists** — `SendNotification` simulates dispatch (logs it, marks `SENT`) unless the recipient's preferences block the channel. Don't expect an actual SMS/push to arrive on a device from this backend today.

| RPC | Request → Response | Auth | Notes |
|---|---|---|---|
| `SendNotification` | `SendNotificationRequest{recipient_id, recipient_type: RecipientType, channel: NotificationChannel, title, body}` → `SendNotificationResponse{code, message, notification: Notification}` | Requires token | If the recipient has disabled that channel, the notification is saved as `NOTIFICATION_FAILED` with an honest reason — it doesn't silently drop or fake success. **Nothing else in this backend calls this automatically yet** (e.g. loan approval doesn't trigger a notification) — if your app needs "you got a loan!" push alerts, that server-side wiring doesn't exist and would need to be built. |
| `GetNotification` | `GetNotificationRequest{notification_id}` → `GetNotificationResponse{code, message, notification}` | Requires token | |
| `GetNotificationsByRecipient` | `GetNotificationsByRecipientRequest{recipient_id}` → `GetNotificationsByRecipientResponse{notifications: repeated Notification}` | Requires token | Your in-app notification feed/inbox screen. |
| `MarkAsRead` | `MarkAsReadRequest{notification_id}` → `MarkAsReadResponse{code, message, notification}` | Requires token | Only valid from `NOTIFICATION_SENT`. |
| `GetUnreadCount` | `GetUnreadCountRequest{recipient_id}` → `GetUnreadCountResponse{unread_count: int32}` | Requires token | For a notification-bell badge count. |
| `SetNotificationPreferences` | `SetNotificationPreferencesRequest{recipient_id, sms_enabled, push_enabled, whatsapp_enabled}` → `SetNotificationPreferencesResponse{code, message, preference: NotificationPreference}` | Requires token | |
| `GetNotificationPreferences` | `GetNotificationPreferencesRequest{recipient_id}` → `GetNotificationPreferencesResponse{code, message, preference}` | Requires token | Returns synthesized all-`true` defaults (not persisted) if the recipient has never set preferences — safe to call on first app launch without special-casing "no settings yet." |

---

## 10. Analytics service — `analytics.AnalyticsService` (`analytics/service.proto`)

**B2B-only — not relevant to the farmer-facing side of the mobile app**, included here in case the mobile app also serves business/agro-dealer users. Tiered data subscriptions gating aggregate views over data the other services collect.

| RPC | Request → Response | Auth | Notes |
|---|---|---|---|
| `CreateSubscription` | `CreateSubscriptionRequest{business_id, tier: SubscriptionTier}` → `CreateSubscriptionResponse{code, message, subscription: Subscription}` | Requires token | `price` is server-set by tier (₦200,000 / ₦1,400,000 / ₦27,500,000 per year for `TIER_AGRO_DEALER`/`TIER_FOOD_PROCESSOR`/`TIER_GOV_NGO`) — no payment is actually taken, this just records the subscription. `end_date` = today + 1 year. |
| `GetSubscription` | `GetSubscriptionRequest{business_id}` → `GetSubscriptionResponse{code, message, subscription}` | Requires token | Latest subscription only. |
| `CancelSubscription` | `CancelSubscriptionRequest{subscription_id}` → `CancelSubscriptionResponse{code, message, subscription}` | Requires token | |
| `GetRegionalCropSummary` | `GetRegionalCropSummaryRequest{business_id, state, plant_type}` → `GetRegionalCropSummaryResponse{code, message, farm_count, total_quantity_listed, average_price_per_unit}` | Requires token | Needs an **active `TIER_AGRO_DEALER`+** subscription — `code="403"` otherwise. |
| `GetMarketplaceActivitySummary` | `GetMarketplaceActivitySummaryRequest{business_id}` → `GetMarketplaceActivitySummaryResponse{code, message, completed_order_count, gross_merchandise_value, average_order_value}` | Requires token | Needs `TIER_FOOD_PROCESSOR`+. |
| `GetFoodSecurityIndicators` | `GetFoodSecurityIndicatorsRequest{business_id, state}` → `GetFoodSecurityIndicatorsResponse{code, message, farm_count, vegetation_health_distribution: repeated VegetationHealthBandCount}` | Requires token | Needs `TIER_GOV_NGO` specifically (top tier only, not "and above"). |

---

## 11. AI/ML service — `aiml.AiMlService` (`aiml/service.proto`)

Rule-based crop diagnosis and heuristic yield prediction — **not** a trained ML model or image-based diagnosis. If your screen design assumes "upload a photo of a sick plant," that's not what this API does; it takes structured symptom selections instead. Credit-scoring AI (also on the architecture doc's original wishlist) already lives in the Fintech service (section 6) — not duplicated here.

| RPC | Request → Response | Auth | Notes |
|---|---|---|---|
| `DiagnoseCropIssue` | `DiagnoseCropIssueRequest{farm_id, crop_type, symptoms: repeated CropSymptom}` → `DiagnoseCropIssueResponse{code, message, diagnosis: CropDiagnosis}` | Requires token | Build the symptom picker as multi-select checkboxes (`CropSymptom` enum, section 12), not a free-text or photo field. If nothing matches, you get an honest `"Unable to determine a specific cause..."` response with `confidence=0.0` rather than a fabricated diagnosis — surface that as a real "consult an agronomist" state in the UI, not an error. |
| `GetDiagnosisHistory` | `GetDiagnosisHistoryRequest{farm_id}` → `GetDiagnosisHistoryResponse{diagnoses: repeated CropDiagnosis}` | Requires token | |
| `PredictYield` | `PredictYieldRequest{farm_id}` → `PredictYieldResponse{code, message, prediction: YieldPrediction}` | Requires token | Uses the farm's own `plant_type`/`land_size` (from Users service) plus its latest Telemetry vegetation reading if one exists — no separate crop-type input needed. |
| `GetLatestYieldPrediction` | `GetLatestYieldPredictionRequest{farm_id}` → `GetLatestYieldPredictionResponse{code, message, prediction}` | Requires token | |
| `GetAgronomicAdvice` | `GetAgronomicAdviceRequest{crop_type, growth_stage}` → `GetAgronomicAdviceResponse{code, message, advice: string}` | Requires token | Stateless lookup, not farm-specific — safe to call speculatively for a "tips" screen. Falls back to a generic tip string if the crop/stage combination isn't in the (small, hardcoded) table. |

---

## 12. Enum reference

Java/proto enum values are identical strings — send/compare exactly as listed.

**`users/enum.proto`** — `VerificationStatus`: `NULL, PENDING, APPROVED, REJECTED`. `ValidateType` (auth OTP flows): `PHONE, EMAIL, LOGIN`.

**`fintech/enum.proto`** — `LoanApplicationStatus`: `LOAN_APPLICATION_PENDING, LOAN_APPLICATION_APPROVED, LOAN_APPLICATION_REJECTED`. `LoanAccountStatus`: `LOAN_ACTIVE, LOAN_COMPLETED, LOAN_DEFAULTED`. `PolicyStatus`: `POLICY_ACTIVE, POLICY_EXPIRED, POLICY_CANCELLED`. `ClaimStatus`: `CLAIM_FILED, CLAIM_APPROVED, CLAIM_REJECTED, CLAIM_PAID` (note: `CLAIM_PAID` is declared but never reached — see section 6). `ReferralStatus`: `REFERRAL_PENDING, REFERRAL_ACCEPTED, REFERRAL_DECLINED, REFERRAL_PAID` (same caveat — only `REFERRAL_PENDING` is ever actually set). `CreditScoreBand`: `POOR, FAIR, GOOD, VERY_GOOD, EXCELLENT`.

**`marketplace/enum.proto`** — `ListingStatus`: `LISTING_ACTIVE, LISTING_SOLD_OUT, LISTING_CLOSED`. `BuyerType`: `BUYER_FARMER, BUYER_BUSINESS`. `OrderStatus`: `ORDER_PENDING, ORDER_ACCEPTED, ORDER_REJECTED, ORDER_COMPLETED, ORDER_CANCELLED`. `EscrowStatus`: `ESCROW_HELD, ESCROW_RELEASED, ESCROW_REFUNDED`.

**`telemetry/enum.proto`** — `SensorType`: `SENSOR_SOIL_MOISTURE, SENSOR_TEMPERATURE, SENSOR_HUMIDITY, SENSOR_RAINFALL`. `DataSource`: `SOURCE_SENSOR, SOURCE_SATELLITE, SOURCE_MANUAL`. `VegetationHealthBand`: `VEGETATION_POOR, VEGETATION_MODERATE, VEGETATION_GOOD, VEGETATION_EXCELLENT`.

**`notification/enum.proto`** — `RecipientType`: `RECIPIENT_FARMER, RECIPIENT_BUSINESS, RECIPIENT_ADMIN`. `NotificationChannel`: `CHANNEL_SMS, CHANNEL_PUSH, CHANNEL_WHATSAPP`. `NotificationStatus`: `NOTIFICATION_PENDING, NOTIFICATION_SENT, NOTIFICATION_FAILED, NOTIFICATION_READ`.

**`analytics/enum.proto`** — `SubscriptionTier`: `TIER_AGRO_DEALER, TIER_FOOD_PROCESSOR, TIER_GOV_NGO` (ordered low→high access, not just labels). `SubscriptionStatus`: `SUBSCRIPTION_ACTIVE, SUBSCRIPTION_EXPIRED, SUBSCRIPTION_CANCELLED`.

**`aiml/enum.proto`** — `CropSymptom`: `SYMPTOM_LEAF_DISCOLORATION, SYMPTOM_LEAF_SPOTS, SYMPTOM_WILTING, SYMPTOM_PEST_SIGHTED, SYMPTOM_STUNTED_GROWTH`.

---

## 13. What's deliberately not built yet (don't design screens around these)

Pulled from this repo's own `CLAUDE.md` build log, listed here so mobile design doesn't get ahead of the backend:

- No payment gateway anywhere — loan disbursement, repayment, insurance premiums/payouts, marketplace escrow, and analytics subscription fees are all bookkeeping records, not real money movement.
- No event bus / cross-domain automation — e.g., approving a loan does not automatically send a notification; each domain must be called explicitly.
- No per-resource authorization — a valid token doesn't yet prove the caller owns the specific farm/loan/listing they're acting on (section 2).
- `ChangeFarmerPassword` is a non-functional stub.
- `GetBusinessProfile` only returns 12 of its 54 declared fields (section 5).
- The gateway's TLS cert is self-signed (section 1/gateway section) — real encryption, but not a cert any device trusts without extra client-side setup. Treat "replace with a CA-signed cert" as blocking for any real-device or app-store build, same as any other pre-production TLS gap.
- The gateway's rate limits (10/min public routes, 120/min everything else, per IP) are untuned starting points, not load-tested figures.
