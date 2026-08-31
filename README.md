# Ikibaho

A JIRA-style project management backend. Spring Boot 4.1, Java 21, PostgreSQL.

Issues with a workflow engine, boards and sprints, JQL search, attachments,
webhooks and live notifications — built as a **modular monolith** with module
boundaries enforced by a failing test rather than by good intentions.

- **[ARCHITECTURE.md](ARCHITECTURE.md)** — why it is built this way, what changed
  during the building, and the traps found along the way. Start there if you are
  reading the code rather than running it.
- This file — how to run it.

The React client lives in a separate repository, `ikibaho-ui`.

---

## Prerequisites

- **JDK 21** — `java -version` should report 21.x
- **Docker**, running. Postgres and MinIO come from `compose.yml`, and the test
  suite uses Testcontainers, so there is no way to build without it.

No Maven install needed; use the wrapper (`./mvnw`).

## Running it

```bash
# 1. Generate the JWT signing keypair — required, see below
mkdir -p keys
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out keys/app.key
openssl rsa -in keys/app.key -pubout -out keys/app.pub

# 2. Start Postgres (5433) and MinIO (9000/9001)
docker compose up -d

# 3. Run
./mvnw spring-boot:run
```

The API is on `http://localhost:8080/api/v1`. Flyway applies all 14 migrations on
first start; `ddl-auto` is `validate`, so a schema that disagrees with the
entities fails the boot rather than silently drifting.

Spring Boot's Docker Compose support starts the containers for you on
`spring-boot:run` — `docker compose up -d` is only needed if you want them
running independently of the app.

### The signing keys are not in the repository

`keys/` is gitignored, and `keys/app.key` is the private RSA key that signs every
access token — so it stays out of git deliberately. **A fresh clone has no keys
and will not start**, including the test suite: `@SpringBootTest` loads the
security configuration, which reads both files.

Step 1 above is therefore mandatory, not optional, and CI does the same thing
(see `.github/workflows/ci.yml`).

Rotating the key invalidates every issued access token. Refresh tokens survive —
they are opaque random values in the database, not signed — so clients recover on
their next refresh.

For anything beyond a laptop, the key belongs in a secret manager and reaches the
process through `IKIBAHO_SECURITY_JWT_PRIVATE_KEY` rather than a file on disk.

## Running the tests

```bash
./mvnw test          # 144 tests
```

Testcontainers starts a real Postgres 17, so the first run pulls an image. There
is no H2 and no in-memory shortcut: the schema uses `jsonb`, GIN indexes,
full-text search, partial indexes, generated columns and `SKIP LOCKED`, and H2
would lie about most of them.

Two tests are worth knowing about:

| Test | What it guards |
|---|---|
| `ModularityTests` | Module boundaries. Fails the build when one module reaches into another's `internal` package, or when two modules form a cycle. |
| `GlobalAccountMigrationTest` | Runs `V13` against **data**, not an empty schema — the rest of the suite starts from a fresh database, so a destructive backfill would otherwise be untested. |

`ModularityTests` also writes C4 module diagrams to
`target/spring-modulith-docs/`.

## Configuration

Everything lives under `ikibaho:` in `application.yml`. The defaults work against
`compose.yml` with no environment variables set.

| Setting | Default | Notes |
|---|---|---|
| `ikibaho.security.jwt.*` | `keys/app.{key,pub}`, 15 min access / 30 day refresh | Public key served at `/.well-known/jwks.json` |
| `ikibaho.security.google.client-id` | *(blank)* | **Blank disables Google sign-in.** There is deliberately no default — an empty audience check would accept ID tokens minted for any Google application. |
| `ikibaho.security.cors.allowed-origins` | `localhost:5173`, `localhost:3000` | Any origin listed can drive the API with the user's refresh cookie |
| `ikibaho.storage.*` | MinIO on `localhost:9000`, 25 MiB cap | Leave `endpoint` blank for real S3 |
| `ikibaho.webhooks.allow-private-addresses` | `false` | Local development only. On, this server will deliver to loopback and RFC1918 addresses — i.e. become an internal-network probe. |
| `ikibaho.notifications.email.enabled` | `false` | Digests log instead of sending until SMTP is configured |

To enable Google sign-in, set `GOOGLE_CLIENT_ID` to an OAuth client id from the
Google Cloud console.

## The API

`/api/v1`, RFC 9457 Problem Details for errors, keyset pagination on issue lists.
`postman/ikibaho.postman_collection.json` has a working collection. Swagger UI is
at `/swagger-ui.html`.

Authentication is a self-issued RS256 JWT — this application issues the tokens and
validates them as an OAuth2 resource server, so swapping in Keycloak later is
configuration rather than a rewrite.

```
POST /auth/register                    create an account and a workspace
POST /auth/login                       → { userId, tokens, organizations }
POST /auth/switch-organization         reissue against another workspace
POST /auth/google                      sign in with a Google ID token
POST /auth/refresh                     rotate; reuse revokes the family

GET  /projects/{id}/board              active sprint, laid out in columns
GET  /projects/{id}/backlog            uncommitted work plus planned sprints
PUT  /issues/{id}/rank                 reorder by naming the two neighbours
GET  /search?jql=...                   assignee = currentUser() AND status != Done
GET  /notifications/stream             Server-Sent Events
```

**`tokens` in the login response is nullable.** An account can belong to several
workspaces, and the password proves who you are without saying which workspace
you meant — so when there is more than one, the response carries the list and no
token, and the client re-posts with a choice. That contract is currently ahead of
`ikibaho-ui`, which cannot yet authenticate against it.

## Layout

```
src/main/java/com/charlie/ikibaho/
├── platform/       shared kernel — BaseEntity, errors, security, web plumbing
├── identity/       accounts, organizations, memberships, tokens, Google sign-in
├── project/        projects, roles, permission schemes
├── issue/          issues, comments, attachments, custom fields, LexoRank
├── workflow/       statuses, transitions, conditions and post-functions
├── board/          sprints and the board/backlog read models
├── search/         JQL lexer → parser → compiler, saved filters
├── notification/   in-app notifications, SSE, email digests
├── activity/       issue history
├── integration/    outbound webhooks with HMAC and retry
└── storage/        presigned S3 URLs
```

A module's root package is its public API; everything under `internal` is private
and `ModularityTests` enforces it. Cross-module reads go through a published
interface returning records — never an entity, never a JPA relationship across a
boundary.

Modules react to each other through Spring Modulith events with a transactional
outbox, so an issue transition fans out to history, notifications, search
indexing and webhooks without any of them being on the request path. Watch the
registry at `/actuator/modulith`: publications that never complete mean a
listener is failing every retry.

## Known gaps

Enough is missing that it is worth listing rather than discovering:

- **No email verification or password reset.** Both matter more than they look —
  linking a Google identity to an existing account is justified by being no
  weaker than a password reset, and there is not one to compare against.
- **`ikibaho-ui` cannot log in** against the current response shape (above).
- **No components, versions, or worklogs**, despite being in the module table.
- **No idempotency keys on `POST /issues`** — double-clicking Create makes two.
- **No rank rebalancing.** `LexoRank.isDegenerate` flags overlong ranks; nothing
  acts on it.
- **Deactivated members keep their project role rows.** Inert, since permission
  checks require an active membership, but they still show in admin screens.

ARCHITECTURE.md §6 and §8 have the full list with reasoning.
