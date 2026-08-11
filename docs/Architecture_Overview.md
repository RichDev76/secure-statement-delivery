### Architecture Overview

This document describes the architecture of the **Statement Service Platform**, focusing on the `statement-service` component that implements secure file statement delivery with time‑limited, signed download links and full auditing.

### Summary

The **Statement Service Platform** implements a robust, production‑grade architecture for secure file statement delivery:

- **Statement upload** with strict validation, AES‑GCM encryption at rest, and hashed identifiers.
- **Time‑limited, signed download links** stored and validated server‑side, with optional single‑use semantics and cluster‑safe cleanup.
- **Comprehensive audit logging** of uploads, downloads, and link usage, enriched with client and user context.
- **Strong security model** using Keycloak, JWT, role‑based authorisation, and careful file‑handling practices.
- **Operational maturity** with distributed locking, logging aspects, correlation IDs, actuator endpoints, and a ready‑to‑run Docker image.

---

### High-Level Architecture Diagram
![High-Level Architecture Diagram](HighLevelArchitectureDiagram.svg)

---

### High‑Level System Context

#### Main Components

- **statement-service** (Spring Boot)
    - Uploads and encrypts monthly account statements (PDF)
    - Stores statement metadata and encrypted files
    - Generates and validates **time‑limited signed download links**
    - Streams decrypted statements for download
    - Produces and exposes **audit logs** for uploads, downloads, and link usage

- **config-server** (Spring Cloud Config)
    - Centralises configuration for `statement-service` and other services
    - Reads properties from a Git‑style config repo (`infra/config-repo`)
    - Integrates with **Vault** for secure secret management

- **PostgreSQL**
    - Primary relational database for `statement-service`
    - Stores statements, signed links, and audit logs

- **HashiCorp Vault**
    - Stores the **encryption master key** and sensitive configuration
    - Accessible only to trusted services (e.g. `statement-service`, `config-server`)

- **Keycloak**
    - Identity provider (OAuth2 / OpenID Connect)
    - Issues JWT access tokens with roles for admins and other calling systems

- **Infra / Docker Compose**
    - Orchestrates Postgres, Vault, Keycloak, Config Server, and other infra for local/dev
    - Provides a reproducible environment for development and testing

#### External Actors

- **Admin User / Backoffice System**
    - Calls the **upload endpoint** to register monthly account statements as PDFs
    - Uses Keycloak‑issued JWT with roles (e.g. `Upload`, `GenerateSignedLink`, `AuditLogsSearch`)

- **Customer Portal / Downstream System**
    - Calls APIs to request **signed download links** for statements
    - Uses JWT with appropriate role(s), typically `GenerateSignedLink` or `Search`

- **Operations & Security Teams**
    - Query **audit logs** for investigations and compliance
    - Monitor logs, metrics, and health endpoints

---

### Core Use Cases and Flows

#### 1. Upload Monthly Statement (Admin)

1. **Admin** obtains a JWT from Keycloak with the `Upload` role.
2. Admin computes `SHA-256` digest of the PDF and calls:
    - `POST /api/v1/statements/upload`
    - Headers: `Authorization: Bearer <token>`, `X-Message-Digest: <hex digest>`
    - Multipart form: `file` (PDF), `accountNumber`, `date`, etc.
3. `AdminController` delegates to `StatementUploadService` which:
    - Validates **content type** (`application/pdf`) and file **size**
    - Validates and **sanitises filename** (no path traversal, restricted characters, length limits)
    - Computes its own SHA‑256 digest and verifies it matches `X-Message-Digest`
    - Hashes the **account number** (for privacy) using SHA‑256
    - Encrypts the PDF using **AES‑GCM** via `EncryptionService`
    - Stores encrypted bytes on disk and saves metadata in Postgres
4. `AuditService` records an `UPLOAD_SUCCESS` event with:
    - `statementId`, hashed `accountNumber`, `performedBy`, client IP, user agent, and extra details
5. Response includes statement details (e.g. `statementId`, upload time, file metadata).

#### 2. Generate Signed Download Link

1. Caller (e.g. portal backend) obtains a JWT with role `GenerateSignedLink`.
2. Calls:
    - `GET /api/v1/statements/link/{statementId}`
3. Controller delegates to `SignedLinkService`, which:
    - Validates that the statement exists and is accessible
    - Creates a `SignedLink` entity including:
        - `id` (UUID)
        - `statementId`
        - `token` / signature
        - `expiresAt` (expiry time)
        - `singleUse` and `used` flags
    - Persists `SignedLink` in Postgres
    - Constructs a **download URL** like:
        - `/api/v1/statements/download/{fileName}?expires=<epochSeconds>&signature=<token>`
4. The generated link is returned to the caller, which may send it to the customer.
5. `AuditService` records link creation as part of the overall audit trail.

#### 3. Download Statement via Signed Link

1. Customer (or portal) calls the download URL:
    - `GET /api/v1/statements/download/{fileName}?expires=...&signature=...`
    - This endpoint is whitelisted and does not require authentication (configured via `security.endpoints.whitelist`).
2. `StatementsController` delegates to `DownloadService`, which invokes `SignedLinkService.validateAndConsume`:
    - Looks up `SignedLink` by token/signature
    - Validates not **expired** (`expiresAt`) and not already `used`
    - Marks link as `used` (for single-use)
3. If valid, `DownloadService`:
    - Retrieves associated statement metadata from Postgres
    - Streams the **encrypted file** from the storage location
    - Uses `EncryptionService` to decrypt on the fly (AES‑GCM, streaming)
    - Writes decrypted bytes to the HTTP response as `application/pdf`
4. `AuditService` records:
    - `DOWNLOAD_SUCCESS` with `statementId`, `signedLinkId`, `performedBy`, IP, UA, and timing
    - Or `DOWNLOAD_FAILED` with failure reason (expired, invalid token, used, missing file, decryption error, etc.)

#### 4. Query Audit Logs

1. An authorised user (with `AuditLogsSearch` role) calls:
    - `GET /api/v1/statements/audit/logs?accountNumber=...&startDate=...&endDate=...&page=0&size=20`
2. `AuditController` delegates to `AuditQueryService` which:
    - Applies filters (account number, date range) and paginates results
    - Returns `AuditLogPage` DTO to the caller.

#### 5. Periodic Cleanup of Signed Links

1. `SignedLinkCleanupService` runs on a **cron schedule** defined in configuration.
2. The job:
    - Generates a new **correlation ID** and puts it in MDC
    - Uses `SignedLinkRepository` to delete expired links in **batches**
    - Runs under a **ShedLock** distributed lock so only a single node processes cleanup in a cluster
3. Logs the number of deleted rows at INFO level and clears MDC at the end.

---

### Application Architecture (Statement Service)

#### Layered Structure

The `statement-service` module follows a traditional **layered architecture**:

- **API / Controller Layer** (`controller` package)
    - Implements OpenAPI‑generated interfaces (e.g. `AuditApi`)
    - Handles HTTP concerns: request mapping, validation annotations, response codes
    - Delegates business logic to services

- **Service Layer** (`service` package)
    - Core business logic for:
        - Uploading and encrypting statements (`StatementUploadService`)
        - Generating, persisting, and validating signed links (`SignedLinkService`)
        - Streaming decrypted downloads (`DownloadService`)
        - Searching and retrieving statements with pagination (`StatementQueryService`)
        - Querying audit logs (`AuditQueryService`)
        - Cleaning up expired/used signed links (`SignedLinkCleanupService`)
        - Managing encrypted file storage with structured directories (`FileStorageService`)
    - Orchestrates repositories, encryption, auditing, and external integrations

- **Persistence Layer** (`repository` package)
    - Spring Data JPA repositories for entities like `Statement`, `SignedLink`, and `AuditLog`
    - Custom queries for efficient operations (e.g. batch deletion of signed links)

- **Domain / Model Layer** (`model` packages)
    - JPA entities representing persistent objects
    - API DTOs and mappers connecting entities to OpenAPI interfaces

- **Cross‑Cutting Concerns**
    - **Security**: `SecurityConfig`, `SecurityEndpointsProperties`, Keycloak role conversion, JWT resource server
    - **Logging**: `LoggingAspect` for controllers and services
    - **Auditing**: `AuditService`, `AuditHelper` (convenience methods for common audit events), and background executor
    - **Request context**: `RequestInfoProvider` for IP, User-Agent, and user identity
    - **Utilities**: `SignatureUtil` (HMAC‑SHA256 signing), `ValidationUtil`, `CommonUtil`

---

### Security Architecture

#### Authentication & Authorisation

- The service acts as a **JWT resource server** using Spring Security.
- JWTs are issued by **Keycloak** and validated by `statement-service` using configured public keys.
- `KeycloakRoleConverter` maps Keycloak roles to Spring Security authorities:
    - Reads from top‑level `roles` claim or `realm_access.roles`
    - Produces `ROLE_<roleName>` authorities

##### Endpoint Roles (HTTP Layer)

`SecurityConfig` enforces role‑based access control:

- `POST /api/v1/statements/upload` → `ROLE_Upload`
- `GET /api/v1/statements/audit/logs` → `ROLE_AuditLogsSearch`
- `GET /api/v1/statements/search` → `ROLE_Search`
- `GET /api/v1/statements/link` → `ROLE_GenerateSignedLink`
- `GET /api/v1/statements/download` → Whitelisted (no authentication required)

Additional whitelisted endpoints (configured via `security.endpoints.whitelist`):
- `/api/v1/statements/actuator/health/**` – health check endpoints
- `/api/v1/statements/actuator/info` – application info
- `/api/v1/statements/v3/api-docs/**` – OpenAPI documentation
- `/api/v1/statements/swagger-ui/**` – Swagger UI

Unauthorized and forbidden errors are returned as **RFC 9457 ProblemDetail** JSON.

#### Correlation and Request Context

- Incoming requests may carry `x-correlation-id` header, which is placed in **MDC** as `correlationId`.
- `LoggingAspect` and logback pattern include `correlationId` for traceability.
- `RequestInfoProvider` extracts:
    - `clientIp` via `HttpServletRequest.getRemoteAddr()` (or `unknown`)
    - `userAgent` from `User-Agent` header
    - `performedBy` from the security context:
        - Prefer `preferred_username` claim from `JwtAuthenticationToken`
        - Fallback to `Authentication.getName()`
        - Fallback to `system` for unauthenticated or non‑request contexts

#### File Upload Security

- Accepts **only PDFs**:
    - Checks `Content-Type` for `application/pdf`
    - Inspects file signature (magic bytes: `%PDF-`) to guard against spoofed MIME types
- Enforces **maximum file size** using configuration properties and explicit checks.
- **Filename sanitisation**:
    - Strips directory components and path separators
    - Rejects `..` and other traversal markers
    - Restricts allowed characters (letters, digits, `.`, `_`, `-`)
    - Enforces maximum length (e.g. 100 chars)
    - Resolves file under a fixed storage root and verifies `target.startsWith(root)`
- Integrity check:
    - `X-Message-Digest` header (SHA‑256 hex) must match server‑computed digest

---

### Data Protection & Cryptography

#### Statement Storage

- PDFs are encrypted at rest using **AES‑GCM** (`AES/GCM/NoPadding`) via `EncryptionService`:
    - Random **12‑byte IV** generated per file
    - IV stored as prefix in the encrypted file
    - 128‑bit authentication tag ensures integrity and authenticity
- **Master key** is provided by `MasterKeyProvider`, backed by Vault.
- On download, `EncryptionService`:
    - Reads IV prefix
    - Initialises decryption cipher
    - Streams decrypted content using `CipherInputStream`

#### File Storage Structure

- `FileStorageService` manages encrypted file storage with a structured directory layout:
    - Base directory: configured via `statement.storage.base-dir` (default: `/data/files`)
    - Directory structure: `{baseDir}/statements/{accountNumberHash}/{year}/{month}/{statementId}.pdf.enc`
    - Account numbers are hashed (SHA‑256) before use in paths for privacy
    - Files are stored with `.pdf.enc` extension indicating encrypted PDF

#### Account Number Handling

- Clear‑text account numbers are **never stored directly**.
- Before persistence or search, account numbers are hashed (`SHA-256`), enabling lookups without storing PII in raw form.

#### Digests and Verification

- `EncryptionService` computes `SHA-256` digests of uploaded files.
- The computed digest is compared with the client‑provided `X-Message-Digest` header to ensure integrity.

---

### Signed Link Model and Lifecycle

#### Data Model (SignedLink)

A `SignedLink` entity typically contains:

- `id` – primary key (UUID)
- `statementId` – foreign key to `Statement`
- `token` – HMAC‑SHA256 signature computed from `{method}|{path}|{expires}` using a shared secret
- `expiresAt` – expiration timestamp (UTC+2), default 900 seconds (15 minutes) from creation
- `singleUse` – whether the link can be used only once (default: true)
- `used` – whether the link has already been consumed
- `createdAt` – creation timestamp
- `createdBy` – user or system that created the link

#### Creation & Validation

- **Creation**:
    - `SignedLinkService.createLink(statementId, options)` handles link generation.
    - Persists the `SignedLink` row and returns the fully‑formed URL to the caller.

- **Validation & Consumption** (`DownloadService` + `SignedLinkService`):
    - Validates that the token exists and is associated with the expected statement.
    - Checks that current time < `expiresAt`.
    - For `singleUse` links, marks as `used` on successful consumption.
    - Returns failure reasons for auditing if any check fails.

#### Cleanup

- `SignedLinkCleanupService` removes **expired** or **used** links periodically:
    - Configurable via `SignedLinkCleanupProperties`:
        - `enabled`
        - `cron`
        - `retentionPeriod` (grace period post‑expiry)
        - `batchSize`
        - `lockAtMostFor`, `lockAtLeastFor`
    - Uses `SignedLinkRepository.deleteExpiredOrUsed(cutoff, batchSize)` in a loop for batched deletion.
    - Logs total deleted entries per run.
    - Protected by ShedLock (`@SchedulerLock`) to avoid concurrent execution across instances.

---

### Auditing & Observability

#### Audit Logging

- `AuditService` records actions such as:
    - `UPLOAD_SUCCESS` – statement uploaded successfully
    - `DOWNLOAD_SUCCESS` – statement downloaded successfully
    - `DOWNLOAD_FAILED` – download attempt failed (expired, invalid, used link, etc.)
    - `LINK_GENERATED` – signed download link created successfully
    - `LINK_GENERATION_FAILED` – failed to generate download link (statement not found, unexpected error)
- Each `AuditLog` entry contains:
    - `id` (UUID)
    - `action`
    - `statementId`
    - `accountNumber` (or hashed variant)
    - `signedLinkId`
    - `performedBy`
    - `performedAt`
    - `details` (map with IP, user agent, error messages, reasons, etc.)
- Writes are **asynchronous**, typically using a dedicated executor (virtual threads), to avoid impacting API latency.

- `AuditController` exposes logs via:
    - `GET /api/v1/statements/audit/logs` (paged, filterable)

#### Logging Aspect

- `LoggingAspect` applies cross‑cutting logging to controllers and services:
    - **Controllers** (`com.example.statementservice.controller..*`):
        - INFO: entry/exit with timing
        - DEBUG: optional detailed result summaries
    - **Services** (`com.example.statementservice.service..*`):
        - DEBUG: entry with arguments and exit with result + timing
- `safeToString` prevents large or sensitive data from overwhelming logs:
    - Special handling for `MultipartFile`, `byte[]`, `Resource`, `Optional`, and long strings
    - Truncates large outputs

#### Correlation IDs & MDC

- `x-correlation-id` header → MDC `correlationId`
- `SignedLinkCleanupService` generates a new UUID correlation ID per run and places it in MDC.
- Logging pattern (in Logback) includes `correlationId` so logs across layers are easily correlated.

#### Actuator & Metrics

- Standard Spring Boot **Actuator** endpoints exposed under `/api/v1/statements/actuator`:
    - `/health`, `/info`, `/metrics`, etc.
- Metrics can be scraped by Prometheus (configuration‑dependent) for alerting and dashboards.

---

### Deployment & Runtime Architecture

#### Packaging & Docker

- The service is packaged as a single **Spring Boot fat JAR**.
- Docker uses a **multi‑stage build**:
    - Stage 1 (build): `maven:3.9-eclipse-temurin-25` builds the `statement-service` module.
    - Stage 2 (runtime): `eclipse-temurin:25-jre` runs the resulting JAR.
- Entrypoint scripts (`wait-for-config.sh`, `entrypoint.sh`) ensure the Config Server is available before starting the app.
- The container exposes port **8080** and is configured via environment variables (profiles, Config Server URL, etc.).

#### External Dependencies at Runtime

- **Config Server**: provides externalised properties; optional per profile.
- **Postgres**: primary database for statements, links, audit logs.
- **Vault**: supplies encryption master key and secrets.
- **Keycloak**: issues JWTs and defines roles.

In production, these components may be deployed as separate containers or on Kubernetes, with:

- Network segmentation
- TLS termination / mTLS between services
- Centralized logging (e.g., ELK, Loki) and metrics (Prometheus, Grafana)

---

### Non‑Functional Characteristics

#### Security

- JWT‑based authentication with Keycloak
- Granular RBAC per endpoint
- Encrypted storage for PDF statements (AES‑GCM)
- Hashed account numbers
- Strong validation for file uploads (type, size, integrity, filenames)
- Signed, time‑limited, and optionally single‑use download links

#### Reliability & Scalability

- Stateless API layer → horizontally scalable behind a load balancer
- Idempotent operations where possible (e.g. downloads, reads)
- Distributed lock (ShedLock) for cleanup tasks in a multi‑instance deployment
- Batch operations for cleanup to avoid DB hotspots

#### Observability

- Structured logging with correlation IDs
- Comprehensive audit events for all critical actions
- Actuator metrics and health checks

#### Extensibility

- Clear separation of concerns in layered architecture
- Well‑defined service and repository interfaces
- OpenAPI specification drives consistent APIs and generated interfaces
- Config Server and Vault simplify environment‑specific overrides without code changes

---

