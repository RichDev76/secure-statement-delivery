### Statement Service Platform - README

A multi-module platform for secure storage and delivery of monthly account statements.

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
- `curl` or HTTP client (Bruno, Postman, HTTPie) for exercising the API

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

# Statement Service
STATEMENT_STORAGE_DIR=/app/data/files
STATEMENT_MASTER_KEY=<32-byte-key>
STATEMENT_SIGNATURE_SECRET=<your-signature-secret>
```
```aiignore
Sample command to use if you want to generate master key and/or signature secret : 
openssl rand -base64 32

```
#### Step 2: Initial Bootstrap (First Time Only)

Run this **only once** when setting up a new environment or to start fresh:

```bash
cd infra
./bootstrap_all.sh
```

This script will:
1. Clean any existing environment (`clean_env.sh`)
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

### Option 2: Running Locally with Only KeyCloak and Postgres

This option runs only Keycloak and PostgreSQL in Docker, while running the Statement Service locally via Maven. This is useful for development and debugging.

#### Step 1: Configure Environment Variables

```bash
cd infra
cp .env.example .env
```

Edit `.env` with the required values (see Option 1 for details).

#### Step 2: Start Only Keycloak and PostgreSQL

```bash
cd infra
docker compose up -d db keycloak
```

Wait for both services to be healthy:

```bash
# Check health status
docker compose ps

# Verify Keycloak is ready
curl -s http://localhost:8081/realms/statement-service | jq .realm

# Verify PostgreSQL is ready
docker exec -it db pg_isready -U postgres
```

#### Step 3: Set Local Environment Variables

Export the required environment variables for the Statement Service:

```bash
export APP_DB_USER=statementuser
export APP_DB_PASSWORD=<your-app-db-password>
export STATEMENT_STORAGE_DIR=/tmp/statement-files
export STATEMENT_MASTER_KEY=<32-byte-key>
export STATEMENT_SIGNATURE_SECRET=<your-signature-secret>
```

Create the storage directory:

```bash
mkdir -p $STATEMENT_STORAGE_DIR
```

#### Step 4: Run Statement Service Locally

From the project root, run with the `local` profile:

```bash
mvn -pl statement-service spring-boot:run -Dspring-boot.run.profiles=local
```

The `local` profile (`application-local.yml`) is preconfigured to:
- Connect to PostgreSQL at `localhost:5432/statementdb`
- Use Keycloak at `http://localhost:8081/realms/statement-service` for JWT validation
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

```bash
curl -H "Authorization: Bearer $TOKEN" -X GET "http://localhost:8080/api/v1/statements/link/$STATEMENT_ID"
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
docker exec -it db psql -U statementuser -d statementdb -c "SELECT 1"
```

#### Statement Service Won't Start
- Ensure all environment variables are set
- Check that Keycloak and PostgreSQL are healthy
- Verify the `STATEMENT_STORAGE_DIR` exists and is writable

---

### Stopping Services

#### Full Docker Compose
```bash
cd infra
docker compose down
```

#### Only Keycloak and Postgres
```bash
cd infra
docker compose down db keycloak
```

To remove all data volumes (fresh start):
```bash
docker compose down -v
```
