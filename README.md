### Secure Statement Service Delivery - README

A multi-module platform for secure storage and delivery of monthly account statements.

---

### TL;DR

Spring Boot service for uploading, storing, and delivering monthly account statements as encrypted
PDFs, gated by JWT role-based access (Keycloak), with audit logging and time-limited HMAC-signed
download links.

- **Fastest path to a running stack:** [Option 1](#option-1-running-full-docker-compose-all-services)
  — `cd infra && cp .env.example .env` (fill in the secrets), then `./bootstrap_all.sh`.
- **Actively developing the service:** [Option 2](#option-2-running-locally-via-maven-supporting-services-via-docker)
  — Docker for Postgres/Keycloak/Redis/Floci, `mvn spring-boot:run` for the service itself.
- **Verify it's up:** `curl http://localhost:8080/api/v1/statements/actuator/health`, then Swagger UI
  at `http://localhost:8080/api/v1/statements/swagger-ui/index.html`.
- **Run the tests:** `cd statement-service && mvn clean verify` (see [Tests & Coverage](#tests--coverage)).
- **Understand the design:** [Architecture Overview](docs/Architecture_Overview.md) and the
  [ADR log](docs/adr/README.md).

---

### Repository Structure

- `statement-service/` - Spring Boot application exposing the Statement Upload & Search API
- `config-server/` - Spring Cloud Config Server (reads configuration from `infra/config-repo` and Vault)
- `infra/` - Docker Compose infrastructure (database, Vault, Keycloak, Config Server)
- `infra/config-repo/` - Git-style configuration repo mounted into Config Server

---

### Architecture & Documentation

For detailed information about the system architecture, components, and design decisions:

- **[Architecture Overview](docs/Architecture_Overview.md)** - Comprehensive architecture documentation covering:
    - High-level system context and components
    - Core use cases and flows (upload, download, signed links, audit logging)
    - Security architecture and authentication
    - Data protection and cryptography
    - Deployment and runtime architecture

---
### Prerequisites

- Java 25 (for running locally via Maven)
- Maven 3.9+
- Docker and Docker Compose
- `curl` or an HTTP client for exercising the API; the [Bruno CLI](https://docs.usebruno.com/bru-cli/overview)
  (`bru`) is needed only if you want to run the collection under `statement-service/bruno/` (see
  Tests & Coverage below)

---

### Option 1: Running Full Docker Compose (All Services)

This starts the complete infrastructure including PostgreSQL, Vault, Keycloak, Config Server, and Statement Service.

#### Step 1: Configure Environment Variables

```bash
cd infra
cp .env.example .env
```

Edit `.env` and fill in all required values:

```properties
# Keycloak Admin
KEYCLOAK_ADMIN_USER=admin
KEYCLOAK_ADMIN_PASSWORD=<your-admin-password>

# Keycloak Clients
KEYCLOAK_ADMIN_CLIENT=statement-service-admin-client
KEYCLOAK_ADMIN_CLIENT_SECRET=<your-admin-client-secret>
KEYCLOAK_CONSUMER_CLIENT=statement-service-consumer-client
KEYCLOAK_CONSUMER_CLIENT_SECRET=<your-consumer-client-secret>

KEYCLOAK_REDIRECT_URI=http://localhost:8081/*
KEYCLOAK_WEB_ORIGIN=http://localhost:8081
KEYCLOAK_SSL_REQUIRED=none
KEYCLOAK_ACCESS_TOKEN_LIFESPAN=3600
KEYCLOAK_SSO_SESSION_IDLE_TIMEOUT=4200
KEYCLOAK_SSO_SESSION_MAX_LIFESPAN=4200
KEYCLOAK_CLIENT_TOKEN_LIFESPAN=3600

# PostgreSQL Superuser
POSTGRES_DB=postgres
POSTGRES_USER=postgres
POSTGRES_PASSWORD=<your-postgres-password>

# Application Database
APP_DB=statementdb
APP_DB_USER=statementuser
APP_DB_PASSWORD=<your-app-db-password>

# Config Server basic auth (statement-service authenticates with these; health stays open)
CONFIG_SERVER_USER=config-user
CONFIG_SERVER_PASSWORD=<your-config-server-password>

# Statement Service (S3-compatible storage via Floci - see ADR 0014)
S3_BUCKET=statements
AWS_S3_REGION=eu-west-1
AWS_ACCESS_KEY_ID=test
AWS_SECRET_ACCESS_KEY=test
STATEMENT_MASTER_KEY=<32-byte-key>
STATEMENT_SIGNATURE_SECRET=<your-signature-secret>
```
```aiignore
Sample command to use if you want to generate master key and/or signature secret : 
openssl rand -base64 32

```
#### Step 2: Initial Bootstrap (First Time Only)

> **⚠️ Destructive.** This runs `clean_env.sh` first, which does `docker compose down -v`, explicitly
> removes the `vault-data`, `db-data`, and `keycloak-data` volumes, and deletes `./vault/init`
> (including the Vault root token and unseal key). If you already have a running environment with
> data you want to keep, skip this step and go straight to Step 3.

Run this **only once** when setting up a new environment or to start fresh:

```bash
cd infra
./bootstrap_all.sh
```

This script will:
1. Clean any existing environment (`clean_env.sh`) — **wipes all local volumes and Vault state, see warning above**
2. Bootstrap Vault (`bootstrap_vault.sh`)
3. Start all services (`start_services.sh`)

#### Step 3: Start/Stop Services (Day-to-Day)

After initial bootstrap, use standard Docker Compose commands:

```bash
cd infra

# Start all services
docker compose up -d

# View logs
docker compose logs -f

# Stop all services
docker compose down
```

#### Services Exposed

| Service | URL |
|---------|-----|
| PostgreSQL | `localhost:5432` |
| Redis | `localhost:6379` |
| Floci (S3-compatible storage) | `http://localhost:4566` |
| Vault | `http://localhost:8200` |
| Keycloak | `http://localhost:8081` |
| Config Server | `http://localhost:8888` |
| Statement Service | `http://localhost:8080` |

#### Verify Services Are Healthy

```bash
# Check all container health status
docker compose ps

# Check Statement Service health
curl http://localhost:8080/api/v1/statements/actuator/health
```

---

### Option 2: Running Locally via Maven (Supporting Services via Docker)

This option runs Keycloak, PostgreSQL, Redis, and Floci (S3-compatible storage) in Docker, while
running the Statement Service locally via Maven. This is useful for development and debugging.

> The `local` profile talks to all four of these — Redis backs the statement ciphertext cache and
> Floci is where statement files actually live, so both need to be up, not just Postgres/Keycloak,
> or `/actuator/health` will report `DOWN` and uploads/downloads will fail.

#### Step 1: Configure Environment Variables

```bash
cd infra
cp .env.example .env
```

Edit `.env` with the required values (see Option 1 for details).

#### Step 2: Start Supporting Services

```bash
cd infra
docker compose up -d db keycloak redis floci
```

Wait for all four to be healthy:

```bash
# Check health status
docker compose ps

# Verify Keycloak is ready
curl -s http://localhost:8081/realms/statement-service | jq .realm

# Verify PostgreSQL is ready
docker exec db pg_isready -U postgres

# Verify Floci's bucket is reachable
aws s3api head-bucket --bucket statements --endpoint-url http://localhost:4566
```

#### Step 3: Set Local Environment Variables

Export the required environment variables for the Statement Service:

```bash
export APP_DB_USER=statementuser
export APP_DB_PASSWORD=<your-app-db-password>
export S3_BUCKET=statements
export AWS_S3_REGION=eu-west-1
export AWS_ACCESS_KEY_ID=test
export AWS_SECRET_ACCESS_KEY=test
export STATEMENT_MASTER_KEY=<32-byte-key>
export STATEMENT_SIGNATURE_SECRET=<your-signature-secret>
```

Statements are stored in Floci (an S3-compatible emulator, started by `infra/docker-compose.yml` and
exposed on `localhost:4566`) — no local storage directory to create. Floci does not enforce auth
locally, so `test`/`test` works for the `AWS_ACCESS_KEY_ID`/`AWS_SECRET_ACCESS_KEY` pair above; use
a least-privilege IAM role instead in real deployments.

#### Step 4: Run Statement Service Locally

From the project root, run with the `local` profile:

```bash
mvn -pl statement-service spring-boot:run -Dspring-boot.run.profiles=local
```

The `local` profile (`application-local.yml`) is preconfigured to:
- Connect to PostgreSQL at `localhost:5432/statementdb`
- Use Keycloak at `http://localhost:8081/realms/statement-service` for JWT validation
- Use Redis at `localhost:6379` for the statement ciphertext cache
- Use Floci at `localhost:4566` for statement storage
- Disable Config Server dependency

#### Step 5: Verify the Service

```bash
# Health check
curl http://localhost:8080/api/v1/statements/actuator/health

# Swagger UI
open http://localhost:8080/api/v1/statements/swagger-ui/index.html
```

---

### Obtaining Access Tokens from Keycloak

To interact with the API, you need a JWT token from Keycloak.

#### Admin Client Token (All Permissions)

```bash
curl -X POST "http://localhost:8081/realms/statement-service/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials" \
  -d "client_id=statement-service-admin-client" \
  -d "client_secret=<KEYCLOAK_ADMIN_CLIENT_SECRET>"
```

#### Consumer Client Token (Search & Generate Link Only)

```bash
curl -X POST "http://localhost:8081/realms/statement-service/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials" \
  -d "client_id=statement-service-consumer-client" \
  -d "client_secret=<KEYCLOAK_CONSUMER_CLIENT_SECRET>"
```

Extract the `access_token` from the response and use it in API requests:

```bash
export TOKEN=<access_token_value>
```

---

### API Usage Examples

#### Upload a Statement

```bash
# Compute SHA-256 digest of your PDF
DIGEST=$(shasum -a 256 statement.pdf | cut -d' ' -f1)

curl -X POST "http://localhost:8080/api/v1/statements/upload" \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-Message-Digest: $DIGEST" \
  -F "file=@statement.pdf;type=application/pdf" \
  -F "accountNumber=123456789" \
  -F "date=2025-11-01"
```

#### Get a Signed Download Link

`accountNumber` must match the statement's actual account — a mismatch returns 404, identical to
an unknown `statementId`.

```bash
curl -H "Authorization: Bearer $TOKEN" -X GET "http://localhost:8080/api/v1/statements/link/$STATEMENT_ID?accountNumber=123456789"
```

#### Search Statements

Search for statements by account number and date range. All three parameters (`accountNumber`, `startDate`, `endDate`) are required.

```bash
curl -H "Authorization: Bearer $TOKEN" -X GET "http://localhost:8080/api/v1/statements/search?accountNumber=123456789&startDate=2025-01-01&endDate=2025-01-31"
```

Optional pagination and sorting parameters:
- `page` - Page number (0-based, default: 0)
- `size` - Page size (1-100, default: 50)
- `sort` - Sort criteria (e.g., `uploadedAt,desc`)

#### Query Audit Logs

```bash
curl -H "Authorization: Bearer $TOKEN" -X GET "http://localhost:8080/api/v1/statements/audit/logs?accountNumber=123456789&startDate=2025-12-01&endDate=2025-12-30&page=0&size=20"
```

---

### Roles and Permissions

| Role | Permissions |
|------|-------------|
| `Upload` | Upload statements |
| `Search` | Search statements |
| `GenerateSignedLink` | Generate signed download links |
| `AuditLogsSearch` | Query audit logs |

| Client | Roles |
|--------|-------|
| `statement-service-admin-client` | All roles (Admin group) |
| `statement-service-consumer-client` | Search, GenerateSignedLink |

---

### Tests & Coverage

```bash
cd statement-service
mvn clean verify   # unit tests (Surefire) + integration tests (Failsafe/Testcontainers) + coverage report
```

Coverage is measured by JaCoCo across both unit and integration runs, excluding code
generated from the OpenAPI contract, and gated at `verify` (`jacoco:check`, 90% instruction /
80% branch — see `statement-service/pom.xml`); a coverage drop below that fails the build.
Test counts change as the suite grows, so they aren't hardcoded here — after `mvn verify`, see
the Surefire/Failsafe summary in the console output, or the generated report at
`statement-service/target/site/jacoco/index.html` for the current coverage percentage.

#### API Test Collection (Bruno)

`statement-service/bruno/Statement Service V1/` is a runnable Bruno collection that exercises the
live HTTP API end-to-end — a smoke/regression pass against a real running stack, complementary to
the Maven test suite rather than a duplicate of it. Requires the [Bruno CLI](https://docs.usebruno.com/bru-cli/overview)
(`brew install bruno-cli` or `npm i -g @usebruno/cli`) and the full stack up (Option 1 or 2 above).

```bash
cd "statement-service/bruno/Statement Service V1"
bru run --env local -r \
  --env-var ADMIN_CLIENT_SECRET=<KEYCLOAK_ADMIN_CLIENT_SECRET> \
  --env-var CONSUMER_CLIENT_SECRET=<KEYCLOAK_CONSUMER_CLIENT_SECRET>
```

This runs the full collection recursively: auth token retrieval, an upload → signed-link → download
chain (each request feeds the next via post-response scripts), and negative-path coverage per
endpoint (validation failures, wrong role, no auth, digest mismatch, oversized upload, tampered
signature, rate limiting). Every request asserts on status code and `errorCode`, so a clean run
means something, not just "no connection errors."

Two requests are intentionally excluded from a fast pass and worth knowing about before a full
`-r` run surprises you:

- **`Download with expired link`** takes ~185s on its own — it mints a fresh link and genuinely
  waits out the real 3-minute signed-link expiry rather than faking it, so it's slow but
  deterministic.
- **`Download during S3 outage (manual)`** cannot pass via `bru run` alone — Bruno's script
  sandbox has no docker access. Verify it manually instead: upload a statement and mint a signed
  link, stop the object store with `docker compose -f infra/docker-compose.yml stop floci`, run
  the download request (expect `503` with `errorCode: STORAGE_UNAVAILABLE` and a `Retry-After`
  header), then restart it with `docker compose -f infra/docker-compose.yml start floci`.

---

### Troubleshooting

#### Keycloak Not Ready
```bash
# Check Keycloak logs
docker compose logs keycloak

# Verify realm exists
curl http://localhost:8081/realms/statement-service
```

#### Database Connection Issues
```bash
# Check if database is running
docker compose ps db

# Test connection
docker exec db psql -U statementuser -d statementdb -c "SELECT 1"
```

#### Statement Service Won't Start
- Ensure all environment variables are set
- Check that Keycloak, PostgreSQL, and Redis are healthy: `docker compose ps`
- Verify Floci is healthy and the `statements` bucket exists: `docker compose ps floci` and
  `aws s3api head-bucket --bucket statements --endpoint-url http://localhost:4566`

---

### Stopping Services

#### Full Docker Compose
```bash
cd infra
docker compose down
```

#### Only the Option 2 Supporting Services
```bash
cd infra
docker compose down db keycloak redis floci
```

To remove all data volumes (fresh start):
```bash
docker compose down -v
```
