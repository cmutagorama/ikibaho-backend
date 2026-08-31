# Ikibaho — Architecture

A JIRA-style project management tool. Spring Boot 4.1, Java 21, PostgreSQL.

> **Status: the nine planned phases, plus a tenth.** Migrations `V1`–`V13`,
> 125 tests green, `ModularityTests` enforcing boundaries in CI.
>
> Sections 1–4 are the original design rationale and still hold. Section 5 is the
> structure as it actually stands, §6 records what each phase turned out to
> involve, and **§8 is the part worth reading second** — the places where building
> it changed the plan, and why.

---

## 1. Architecture style: **Modular Monolith**

**Decision: single deployable, hard internal module boundaries. Not microservices.**

Why not microservices here:

- The JIRA domain is *aggressively* transactional and cross-cutting. One "update issue" call touches: the issue row, workflow transition validation, board rank, activity/audit log, notification fan-out, search index, webhook dispatch. Split across services, that becomes a saga with compensating transactions for a feature that should be one `@Transactional` method.
- Almost every read is a join across "services" (backlog = issues × sprints × users × statuses × ranks). Microservices turn that into N+1 network calls or a duplicated read model.
- Real Jira/Linear/GitHub Issues are monoliths (or a small number of large services). Atlassian did not split Jira into 30 services.
- Operationally: one JAR, one DB, one deploy, one debugger. You want to learn Spring Boot, not Kubernetes and distributed tracing.

**What you do instead:** enforce module boundaries *in the compiler*, so the code has the discipline of microservices without the ops tax — and a real extraction path if one module ever needs to scale independently.

Use **Spring Modulith**. It gives you:
- `ApplicationModules.of(IkibahoApplication.class).verify()` as a **JUnit test that fails the build** when `issue` reaches into `notification.internal`.
- `@ApplicationModuleListener` — a transactional, async event listener with a **built-in event publication registry (transactional outbox)**. This is the single highest-value dependency in the whole stack: it lets modules talk via events with at-least-once delivery, no message broker.
- Auto-generated C4 module diagrams and docs.

Add via the Modulith BOM (`spring-modulith-bom`) — check the version that matches Boot 4.1, don't hardcode from memory.

> **This said "start without it if you want; retrofitting is cheap." That was wrong.**
> The dependency went in at phase 6, and the first `verify()` run against five
> phases of existing code found three genuine boundary defects — including the
> shared kernel depending on one of its dependents, which would have made
> `identity` unextractable. Each was a ten-minute fix at that size and would have
> been a refactor at three times the size. Add it at phase 1. §8 has the details.

### The modules

| Module | Owns |
|---|---|
| `platform` | Base entity, auditing, error handling, tenancy, config, `JdbcClient` helpers. Shared kernel — everyone may depend on it. |
| `identity` | Users, organizations, memberships, groups, authentication, tokens. |
| `project` | Projects, project roles, permission schemes, components, versions (releases). |
| `issue` | Issues, hierarchy (epic→story→subtask), links, comments, attachments metadata, worklogs, custom field values. The core aggregate. |
| `workflow` | Statuses, status categories, workflow definitions, transitions, conditions/validators/post-functions. |
| `board` | Boards, sprints, backlog, column mapping, LexoRank ordering. |
| `search` | JQL-lite parser → SQL predicates, saved filters. |
| `notification` | In-app notifications, email, user preferences, digesting. |
| `storage` | Attachment blobs (S3/MinIO), presigned URLs. |
| `activity` | Issue history / activity stream (a product feature, not just audit). |
| `integration` | Outbound webhooks, API tokens, incoming hooks. |

Dependency direction: `platform` ← everything. `identity` ← `project` ← `issue` ← `board`/`search`. `notification`, `activity`, `integration` depend on **events only**, never called synchronously. That last rule is what keeps issue-update latency flat as features pile on.

The table above is the *intent*; three things landed elsewhere for reasons the compiler insisted on (§8), and some of what it lists was never built (§6). Specifically: `project` has no components or versions, `issue` has no worklogs, `integration` has no API tokens or incoming hooks, and `notification` has no per-user preferences.

`activity`, `search` and `integration` reach `issue` and `project` **only** through published interfaces — `IssueLookup`, `PermissionService`, `UserService`, `ProjectService` — and their event packages. That held for all four consumers without exception, which is the strongest evidence the boundaries are real rather than aspirational.

---

## 2. Data layer

### Database: PostgreSQL. Drop H2 entirely, including dev.

Your pom currently has H2 + `spring-boot-h2console`. Remove them. H2 will lie to you about: `jsonb`, GIN indexes, full-text search, `FOR UPDATE SKIP LOCKED`, window functions, `INSERT ... ON CONFLICT`, array types, and CTE behavior — all of which this design uses. Dev-vs-prod DB divergence is the classic way to build a schema that can't ship.

Instead:
- `compose.yaml` with Postgres + MinIO; Spring Boot's Docker Compose support (`spring-boot-docker-compose`) starts it automatically on `bootRun`.
- **Testcontainers** for tests (`spring-boot-testcontainers`), with `@ServiceConnection`. Reuse a single container across the suite.

### Migrations: Flyway, from commit #1

```properties
spring.jpa.hibernate.ddl-auto=validate
spring.flyway.enabled=true
```

`ddl-auto=validate` and nothing else, ever. `update` silently drifts and can't be reviewed, rolled back, or reasoned about in a PR.

### Persistence strategy: **JPA for writes, JDBC for reads**

This is the most important data-layer lesson in the project.

- **Writes / aggregates → Spring Data JPA.** Loading an `Issue`, mutating it, cascading to comments, optimistic locking, dirty checking. JPA is excellent here.
- **Reads / list screens → `JdbcClient` + hand-written SQL, projected straight into records.** The backlog view, board view, search results, dashboards. These are wide joins with pagination and sorting; JPA gives you N+1 queries, `MultipleBagFetchException`, and in-memory pagination warnings. A `JdbcClient` query returning `List<IssueRowView>` is faster, obvious, and tunable.

`JdbcClient` is built into Spring Framework 6.1+ — no extra dependency, and much nicer than `JdbcTemplate`:

```java
List<BacklogRow> rows = jdbcClient.sql("""
        SELECT i.id, i.issue_key, i.summary, i.rank, s.name AS status, u.display_name AS assignee
        FROM issue i
        JOIN status s ON s.id = i.status_id
        LEFT JOIN app_user u ON u.id = i.assignee_id
        WHERE i.project_id = :projectId AND i.sprint_id IS NULL AND i.deleted_at IS NULL
        ORDER BY i.rank
        LIMIT :limit OFFSET :offset
        """)
    .param("projectId", projectId)
    .param("limit", limit).param("offset", offset)
    .query(BacklogRow.class)
    .list();
```

Don't put `@Query` monsters on JPA repositories to work around this. Two tools, two jobs.

### Identifiers

**Primary keys: UUIDv7.** Time-ordered, so they index well (unlike UUIDv4, which fragments B-trees), non-enumerable in URLs, and generatable client-side.

```java
@Id
@GeneratedValue
@UuidGenerator(style = UuidGenerator.Style.TIME)  // Hibernate 6.5+ → UUIDv7
private UUID id;
```

Column type `uuid`, not `varchar(36)`.

**Issue keys (`IKB-142`) are a separate concern.** They're a user-facing identifier and need a gapless-ish per-project counter. Do *not* use `COUNT(*)+1` (race) or a global sequence (keys must be per-project). Use an atomic increment:

```sql
UPDATE project SET issue_counter = issue_counter + 1
WHERE id = :projectId
RETURNING issue_counter;
```

That takes a row lock for the duration of the transaction — correct, and fine at this scale. It serializes issue creation *per project*, which is exactly the semantics you want.

### Ordering on boards: **LexoRank**

Users drag cards. If order is an `integer` position column, every drag rewrites every row after it. Jira solved this with LexoRank: store rank as a `varchar` string, and to place a card between two neighbors, compute a string that sorts lexicographically between them.

```
"0|hzzzzz:"  ...  "0|i0000o:"   → between them: "0|hzzzzz:i" (roughly)
```

- Column: `rank text not null`, index `(project_id, rank)`.
- A move = one UPDATE of one row. That's the whole point.

*As built:* base-36 digits read as a fraction, **without** the `0|` bucket prefixes. Buckets exist in Jira so a whole bucket can be rebalanced offline while another serves reads; at this scale the added parsing on every comparison buys nothing. `LexoRank.evenlySpaced(n)` reseeds a list, and `isDegenerate` flags ranks past 8 characters — the rebalance trigger, not yet wired to anything.

The subtle part is the floor. A rank of all-minimum digits has nothing below it, so "move to top" would become impossible; the generator therefore never emits one, and `between()` rejects such a bound explicitly rather than looping. `LexoRankTest` covers it, along with 50 consecutive insertions into the same gap.

### Custom fields: hybrid, not EAV

Jira's killer feature is user-defined fields. Three options:

1. **EAV** (`custom_field_value(issue_id, field_id, value)`) — flexible, but every list view becomes a pile of self-joins or pivots. Avoid as the primary model.
2. **Column per field** — fast, but DDL at runtime. No.
3. **`jsonb` column + a field-definition table** ← **do this.**

```sql
CREATE TABLE custom_field (
  id uuid PRIMARY KEY,
  project_id uuid,          -- NULL = global
  key text NOT NULL,        -- "story_points"
  name text NOT NULL,
  type text NOT NULL,       -- NUMBER | TEXT | SELECT | USER | DATE | MULTI_SELECT
  config jsonb              -- options for SELECT, min/max, etc.
);

ALTER TABLE issue ADD COLUMN custom_fields jsonb NOT NULL DEFAULT '{}';
CREATE INDEX idx_issue_custom_fields ON issue USING gin (custom_fields jsonb_path_ops);
```

Core fields (summary, status, assignee, priority, reporter, due date) stay as **real columns** — they're queried and joined constantly. Only user-defined ones go in `jsonb`. Validate values against `custom_field.type` in the application layer.

Map it with Hibernate 6's `@JdbcTypeCode(SqlTypes.JSON)` on a `Map<String, Object>` — no `hypersistence-utils` needed anymore.

### Concurrency: optimistic locking on `Issue`

```java
@Version
private long version;
```

Two people editing the same issue is the single most common real conflict in this domain. `@Version` turns it into an `OptimisticLockException` you map to HTTP **409 Conflict**, rather than a silent lost update. Return the current version in the issue DTO and require it on `PATCH`.

### Soft delete

`deleted_at timestamptz NULL` on `issue`, `project`, `comment`. Hibernate 6.4+ has `@SoftDelete` which rewrites queries for you. Add partial indexes: `CREATE INDEX ... WHERE deleted_at IS NULL`.

### Multi-tenancy

Even if you only ever have one org, design for it now — retrofitting a tenant column is brutal. Single database, `organization_id` discriminator on every tenant-owned table.

Hibernate 6 `@TenantId` + a `CurrentTenantIdentifierResolver` reading from the security context auto-filters every query and stamps every insert. That's the whole implementation — you cannot forget a `WHERE org_id = ?` because Hibernate adds it.

*As built:* the discriminator is there on every tenant-owned table, but **`@TenantId` was never wired up**. Scoping is explicit — services take an `organizationId` and pass it down, and reads go through `PermissionService.browsableProjectIds`. That is more code and one more thing to forget, and it is worth revisiting; the reason it never happened is that `PermissionService` already had to filter by *project* visibility, which `@TenantId` cannot express, so the org filter was never the only guard.

**One table is deliberately not tenant-scoped: `app_user`.** Since phase 10 an account is global and `organization_member` carries the tenancy. A tenant discriminator on the account would reintroduce exactly the problem that phase removed — see §8.

### Activity / history

Don't use Envers. The activity stream is a **product feature** with its own shape (grouped by actor+time, rendered as "Charlie changed Status from To Do to In Progress"), not a compliance audit log.

```sql
CREATE TABLE issue_history (
  id uuid PRIMARY KEY,
  issue_id uuid NOT NULL REFERENCES issue(id),
  actor_id uuid NOT NULL,
  occurred_at timestamptz NOT NULL,
  field text NOT NULL,        -- "status", "assignee", "custom:story_points"
  from_value jsonb,
  to_value jsonb
);
CREATE INDEX ON issue_history (issue_id, occurred_at DESC);
```

Write these rows in the same transaction as the change (a Hibernate entity listener or an explicit diff in the service — prefer explicit; entity listeners get magical fast).

### Core schema sketch (`V1__baseline.sql`)

```
organization(id, name, slug, created_at)
app_user(id, org_id, email, password_hash, display_name, avatar_url, status, created_at)
user_group(id, org_id, name)          group_member(group_id, user_id)

project(id, org_id, key, name, lead_id, type, permission_scheme_id,
        workflow_id, issue_counter, deleted_at)
project_role(id, project_id, name)     -- ADMIN / MEMBER / VIEWER / custom
project_role_actor(role_id, user_id NULL, group_id NULL)

status_category(id, name)              -- TODO | IN_PROGRESS | DONE
status(id, workflow_id, name, category_id, position)
workflow(id, org_id, name)
transition(id, workflow_id, name, from_status_id NULL, to_status_id)  -- NULL from = global
transition_rule(id, transition_id, kind, type, config jsonb)          -- CONDITION|VALIDATOR|POST_FUNCTION

issue_type(id, org_id, name, icon, hierarchy_level)  -- 1=epic 0=story -1=subtask
issue(id, org_id, project_id, issue_key, type_id, status_id, priority,
      summary, description, reporter_id, assignee_id, parent_id,
      sprint_id, rank, story_points, due_date, custom_fields jsonb,
      version, created_at, updated_at, deleted_at)
issue_link(id, source_id, target_id, type)   -- BLOCKS | RELATES | DUPLICATES
comment(id, issue_id, author_id, body, created_at, updated_at, deleted_at)
attachment(id, issue_id, uploader_id, filename, content_type, size_bytes, storage_key)
worklog(id, issue_id, author_id, seconds_spent, started_at, comment)
watcher(issue_id, user_id)             vote(issue_id, user_id)
label(id, org_id, name)                issue_label(issue_id, label_id)

board(id, project_id, name, type)      -- SCRUM | KANBAN
board_column(id, board_id, name, position)
board_column_status(column_id, status_id)
sprint(id, board_id, name, goal, state, start_date, end_date, complete_date)

permission_scheme(id, org_id, name)
permission_grant(id, scheme_id, permission, holder_type, holder_ref)
  -- holder_type: PROJECT_ROLE | GROUP | USER | REPORTER | ASSIGNEE | PROJECT_LEAD | ANY_LOGGED_IN

issue_history(...)  notification(...)  webhook(...)  refresh_token(...)
```

**Index the access patterns, not the columns:**
`issue(project_id, status_id) WHERE deleted_at IS NULL`, `issue(assignee_id, status_id)`, `issue(sprint_id, rank)`, unique `issue(issue_key)`, GIN on `to_tsvector(summary || description)`.

---

## 3. Authentication & Authorization

### Authentication: self-issued JWT, validated as a resource server

```xml
spring-boot-starter-security
spring-boot-starter-oauth2-resource-server
```

**Do not** use `spring-boot-starter-oauth2-client` or an external IdP for v1 — you'd learn Auth0's console, not Spring Security. And don't hand-roll a `OncePerRequestFilter` that parses JWTs with jjwt either; you'd reimplement (badly) what the resource server already does.

The sweet spot: **you issue the tokens, Spring Security validates them.**

- Generate an RSA (or EC P-256) keypair. Publish the public key at `/.well-known/jwks.json`.
- `JwtEncoder` (Nimbus, comes with the resource-server starter) signs access tokens in your `/auth/login` endpoint.
- `spring.security.oauth2.resourceserver.jwt.jwk-set-uri` points at your own JWKS → every request is validated by the framework, and you get a real `JwtAuthenticationToken` in the security context.

This is genuinely how production systems work, and it means swapping in Keycloak or Spring Authorization Server later is a config change, not a rewrite.

**Tokens:**

| | Access token | Refresh token |
|---|---|---|
| Format | JWT, signed | Opaque random 256-bit |
| Lifetime | 10–15 min | 14–30 days |
| Storage | Client memory | `refresh_token` table, **SHA-256 hashed** |
| Claims | `sub`, `org`, `email`, `roles`, `jti`, `exp` | — |

Refresh tokens: **rotate on every use**, and implement **reuse detection** — if a token that's already been rotated is presented again, revoke the entire token family (someone stole it). Store `family_id` on the row.

*Since phase 10, `org` and `roles` are properties of the session, not of the account.* An account is global and holds a membership per workspace, so the same person is `ADMIN` in one and `MEMBER` in another. Both claims come from the membership chosen at login.

That makes the login flow two-step when it has to be: authenticate against the one account, *then* resolve the workspace. With one active membership it resolves implicitly; with several the response carries the list and no token, and the client re-posts a choice. Returning that list only **after** the password check is the point — offering it first would let anyone enumerate the workspaces an address belongs to.

Refresh tokens carry `organization_id` and re-check the membership on every rotation, so removing someone from a workspace cuts their refresh there at once without disturbing their sessions elsewhere.

Passwords: `DelegatingPasswordEncoder` defaulting to **bcrypt** (cost 12) or Argon2id. Never configure `NoOpPasswordEncoder`, not even in tests.

Also needed: email verification, password reset (single-use hashed tokens, 1h TTL), and **personal API tokens** for the integration module (opaque, hashed, scoped — a separate `AuthenticationProvider`).

Keep the API **stateless**: `SessionCreationPolicy.STATELESS`, CSRF disabled (safe *only* because you're stateless and token-in-header; if you ever put the token in a cookie, CSRF protection comes back).

### Authorization: this is where a Jira clone gets interesting

Global roles are nearly useless here. **Permissions are project-scoped**, and Jira models this with *permission schemes*. Get this right and the rest of the app falls into place.

```
Permission (enum)  →  granted to  →  Holders
BROWSE_PROJECT           PROJECT_ROLE("Developers")
CREATE_ISSUE             GROUP("engineering")
EDIT_ISSUE               USER(specific)
DELETE_ISSUE             REPORTER          ← dynamic: the issue's reporter
ASSIGN_ISSUE             ASSIGNEE          ← dynamic: the current assignee
TRANSITION_ISSUE         PROJECT_LEAD
MANAGE_SPRINT            ANY_LOGGED_IN
ADMINISTER_PROJECT
```

A `permission_scheme` is attached to a project; a `permission_grant` row says "permission X is held by holder Y". Resolution: given (user, project, permission, optional issue) → collect the user's project roles + groups + dynamic relationships to the issue → check for a matching grant.

**Wire it into Spring Security properly**, don't scatter `if` statements:

```java
@PreAuthorize("hasPermission(#projectId, 'PROJECT', 'CREATE_ISSUE')")
public IssueDto create(UUID projectId, CreateIssueCommand cmd) { ... }

@PreAuthorize("hasPermission(#issueId, 'ISSUE', 'TRANSITION_ISSUE')")
public void transition(UUID issueId, UUID transitionId) { ... }
```

backed by a custom `PermissionEvaluator`. Cache resolved permission sets **per request** (a `@RequestScope` bean) — a board render checks permissions hundreds of times and you do not want hundreds of round trips. Cache scheme *definitions* in Caffeine with eviction on change events; they're read constantly and written almost never.

**Two rules that matter:**

1. **Never filter after fetching.** For list endpoints, resolve the set of projects the user can `BROWSE_PROJECT` *first*, then push `AND i.project_id IN (:ids)` into the SQL. Post-filtering breaks pagination (page 1 returns 3 rows) and leaks total counts.
2. **404, not 403, for objects the user can't browse.** Returning 403 confirms the resource exists — that's an information leak. Only return 403 when the user can see the object but can't perform the action.

Workflow transitions get a **second** authorization layer: `transition_rule` rows of kind `CONDITION` (e.g. "only assignee may move to In Review"). Permission check and workflow condition are distinct — evaluate both.

---

## 4. Integration

### Internal: events, via Spring Modulith

```java
// in the issue module — published inside the transaction
events.publishEvent(new IssueTransitioned(issueId, fromStatus, toStatus, actorId));

// in notification module — different module, no compile-time coupling
@ApplicationModuleListener
void on(IssueTransitioned event) { ... }
```

`@ApplicationModuleListener` = `@Async` + `@Transactional(propagation = REQUIRES_NEW)` + `@TransactionalEventListener(AFTER_COMMIT)`, **plus** Modulith persists the event to an `event_publication` table before delivery and marks it complete after. That's a transactional outbox with zero infrastructure: if notification handling throws or the app crashes, the event is replayed on restart. No Kafka, no RabbitMQ, no lost emails.

This is what keeps `POST /issues/{id}/transitions` at ~20ms while it fans out to notifications, search indexing, webhooks, and activity logging.

### Outbound webhooks

Consume the same domain events → `webhook_delivery` table → a poller (`FOR UPDATE SKIP LOCKED`) → HTTP POST.

- **HMAC-SHA256 signature** in `X-Ikibaho-Signature`, per-webhook secret. Non-negotiable — otherwise anyone can forge your payloads.
- Exponential backoff, 6 attempts, capped at 15 minutes, then the delivery is marked `FAILED`. *Not built:* auto-disabling the endpoint or notifying its owner. `WebhookService.retry` requeues a delivery manually once the endpoint is fixed.
- Timeouts: 10s connect and read. A slow consumer must not be able to back up your worker pool.
- **SSRF guard**: reject `localhost`, `169.254.169.254`, and private ranges as webhook targets. This is a real, commonly exploited vulnerability class in exactly this feature.

The SSRF check resolves the host **at delivery time, not at registration** — a hostname that resolved publicly when the webhook was created can be repointed at a private address an hour later (DNS rebinding). Redirects are not followed for the same reason: a receiver that 302s elsewhere would bounce a signed request at an internal address.

Signing covers `<timestamp>.<body>`, not the body alone. Signing only the body makes every delivery replayable forever, since a captured request stays valid. Verification is constant-time; `String.equals` returns as soon as two bytes differ, and that timing difference is enough to recover a signature byte by byte.

*As built:* plain `RestClient`, not `@HttpExchange` — the URL is per-webhook data rather than a compile-time endpoint, which is not what declarative clients are for. It is also built with `RestClient.builder()` rather than the injected shared builder; see §7.

Enqueue and delivery are **separate**: the listener writes rows inside its transaction, a scheduled job does the HTTP. A hostile or merely slow endpoint must never hold an event-listener thread, and retry has to survive a restart.

### Realtime (live board updates)

**Start with SSE.** Board updates are server→client only; you don't need WebSocket's duplex channel, and SSE survives proxies and reconnects for free.

*As built:* `GET /api/v1/notifications/stream`, not a per-project stream, and it lives in `notification` rather than `integration`. The stream carries notifications, and an endpoint in `integration` would have meant that module reading notification's data — which `ModularityTests` refused, correctly (see §8).

The endpoint takes **no user id**: it is derived from the token. An endpoint accepting one would need a check that it matches the caller, and that is the kind of check that gets forgotten.

Single instance: an in-memory registry of emitters keyed by user, which is what exists. A notification is therefore pushed live only to sockets on the instance that produced it — acceptable because the badge count is authoritative and polled on load, so a missed push costs latency, not a notification. Multi-instance fan-out means a shared bus; that is a real cost for a small gain. The heartbeat is deliberately **not** ShedLock'd: every instance must keep its own connections alive.

### Email

`spring-boot-starter-mail` + Thymeleaf templates. Always async (off the event listener). **Digest aggressively** — a chatty sprint generates hundreds of notifications; batch per-user on a schedule instead of sending per-event. Per-user, per-project notification preferences from day one. MailHog or Mailpit in `compose.yaml` for local dev.

### File storage

**S3-compatible (MinIO locally), never the database, never the local filesystem.**

Use **presigned URLs** in both directions: the client asks your API for an upload URL, PUTs the bytes straight to S3, then tells your API "done" with the storage key. Downloads are presigned GETs with a short TTL. Your app never streams file bytes — that's what kills Spring Boot apps with 100MB attachments and a 200-thread pool.

Validate content type and size *server-side* in the presign request, and check `BROWSE_PROJECT` before minting a download URL.

*Correction from building it:* **a presigned PUT cannot enforce size.** Only a presigned POST policy carries conditions, and the AWS SDK's `presignPutObject` has nowhere to put them. So size is checked twice instead: the declared size is refused before a URL is issued, and the *actual* size is read back with a `HEAD` when the client reports the upload complete. That third step is not a formality — without it the attachment list shows files that were never uploaded, at whatever size the client felt like claiming.

Content-Type **is** signed, which matters: it stops a URL issued for a PNG being used to store an HTML page. Uploads of `text/html` and `image/svg+xml` are refused outright, and downloads force `Content-Disposition: attachment` — a stored XSS payload otherwise waits for one misconfiguration.

Filenames are reduced to their basename. Stripping separators in place turns `../../etc/passwd` into `....etcpasswd`, which is harmless only by accident and preserves an attempt at traversal for whatever reads it next.

### Search

**PostgreSQL full-text search**, generated `tsvector` + GIN. Comfortably handles hundreds of thousands of issues. Elasticsearch is a whole second datastore to keep in sync — add it only when Postgres actually stops being enough. It probably won't.

*As built, with one significant change:* the `tsvector` is **not** a column on `issue`. `search` owns its own denormalized `issue_search_index` table, kept current by listeners on issue events.

Two reasons. Boundaries: `search` must not read tables `issue` owns. And speed: a JQL query mixes free text with status, assignee and type filters, so against the live schema every search would join `issue`, `status`, `issue_type`, `project` and `comment`. Against the index it is one scan of one table. It also makes comments searchable, which a column on `issue` cannot do.

```sql
search_vector tsvector GENERATED ALWAYS AS (
    setweight(to_tsvector('english', coalesce(issue_key,'') || ' ' || coalesce(summary,'')), 'A') ||
    setweight(to_tsvector('english', coalesce(description,'')),  'B') ||
    setweight(to_tsvector('english', coalesce(comment_text,'')), 'C')
) STORED;
```

A generated column rather than a trigger: Postgres recomputes it on every write and it cannot drift. Triggers can be disabled, forgotten in a bulk load, or skipped by `COPY`.

`plainto_tsquery`, not `to_tsquery` — it takes whatever a person typed and never throws on punctuation. `to_tsquery` turns a stray `&` into a 500 on the search box.

**JQL-lite** is the fun part, and it is a real recursive-descent parser: `JqlLexer` → `JqlParser` → sealed `Jql` AST → `JqlCompiler` → parameterized SQL. Two invariants hold in the compiler and neither is negotiable:

1. **No user text reaches the SQL string.** Values become named parameters; column names come from the `JqlField` enum, so an unrecognised field is rejected at parse time and never reaches the compiler. Column names cannot be bound as parameters, and the only safe way to put one in a query is to have never accepted it as input.
2. **Project scope is ANDed on last**, outside the user's expression, which is wrapped in its own parentheses first. That is what stops `project = mine OR project = theirs` from escaping the scope. A user with no browsable projects compiles to `AND false`.

`currentUser()` is resolved at **compile** time, not parse time — which is what lets one saved filter mean something correct and different for each person who runs it.

### Caching, jobs, observability

- **Caching**: Caffeine (`spring-boot-starter-cache`) for permission schemes, workflow definitions, custom field metadata, user profiles. Redis when you go multi-instance.
- **Scheduled jobs**: `@Scheduled` + **ShedLock** so sprint-close and digest jobs don't double-fire across instances. Rank rebalancing, webhook retries, token cleanup, digest sends.
- **Observability**: Actuator (already in your pom) + Micrometer + OpenTelemetry. `spring.threads.virtual.enabled=true` — Java 21 virtual threads, and this workload (blocking JDBC + HTTP calls) is exactly what they're for.

### API surface

- REST, `/api/v1/...`. Spring MVC in Boot 4 has first-class API versioning (`@RequestMapping(version = "1.0")`) if you'd rather version by header — either is fine, pick one.
- **springdoc-openapi** for docs — annotate as you go, not at the end.
- **RFC 9457 Problem Details** for errors: Spring's `ProblemDetail` + `@RestControllerAdvice`. One consistent error shape across the API.
- DTOs are Java **records**, separate from entities. Never expose an entity from a controller — it leaks your schema, triggers lazy-loading outside the session, and couples your API to Hibernate. Map explicitly (MapStruct, or by hand — by hand is fine and clearer at this size).
- Pagination: cursor-based (keyset) for issue lists, not `OFFSET`. `OFFSET 50000` scans 50,000 rows.
- Bean Validation (`spring-boot-starter-validation`) on request records.
- **Idempotency keys** on `POST /issues` — double-clicked "Create" should not make two tickets.

---

## 5. Project structure

Package **by module, then by layer** — never a top-level `controllers/`, `services/`, `repositories/`. Layer-first packaging means every feature is smeared across the codebase and nothing can ever be extracted.

The Modulith convention: **the module's root package is its public API; anything in `internal` is private and the build test enforces it.**

```
src/main/java/com/charlie/ikibaho/
├── IkibahoApplication.java
│
├── platform/                        # shared kernel — @ApplicationModule(type = OPEN)
│   ├── package-info.java            #   its sub-packages ARE its API; see §8
│   ├── domain/BaseEntity.java
│   ├── error/DomainException, NotFound, Conflict, Validation, Forbidden, Unauthorized
│   ├── security/SecurityConfig, CurrentUser, JwtProperties, JwksController, Cors…
│   ├── web/GlobalExceptionHandler, ApiVersion, Cursor, PageResponse
│   └── config/JpaConfig, AsyncConfig, SchedulingConfig
│
├── identity/                        # ── PUBLIC API of the module ──
│   ├── UserService.java, UserSummary.java
│   ├── events/UserRegistered.java + package-info.java   # @NamedInterface("events")
│   └── internal/                    # ── PRIVATE ──
│       ├── domain/User (the account), Organization, OrganizationMember,
│       │         MemberRole, MembershipStatus, RefreshToken, Invitation, UserGroup
│       ├── persistence/UserRepository, OrganizationMemberRepository,
│       │              OrganizationRepository, UserGroupRepository, …
│       ├── application/AuthService, TokenService, InvitationService,
│       │              UserServiceImpl, LoginResult
│       └── web/AuthController, UserController, OrganizationMemberController, dto/
│
├── project/
│   ├── ProjectService, ProjectSummary, PermissionService, Permission,
│   │   IssueContext, IssueContextResolver
│   ├── events/ProjectCreated.java + package-info.java
│   └── internal/  domain/ persistence/ application/ security/ web/
│
├── issue/                           # the core aggregate
│   ├── IssueLookup, IssueSummary, BoardIssue, IssueIndexView
│   ├── events/IssueCreated, IssueUpdated, IssueDeleted,
│   │          IssueTransitioned, IssueAssigned, IssueCommented + package-info.java
│   └── internal/
│       ├── domain/Issue, Comment, IssueLink, IssueType, Status, Attachment,
│       │         CustomFieldDefinition, Priority, StatusCategory, LinkType
│       ├── rank/LexoRank.java        # NOT in board — see §8
│       ├── persistence/IssueRepository, IssueQueries (JdbcClient), StatusRepository,
│       │              AttachmentRepository, CommentRepository, …
│       ├── application/IssueService, IssueTransitionService, IssueRankingService,
│       │              CommentService, AttachmentService, CustomFieldValidator,
│       │              IssueMetadataProvisioning, IssueLookupImpl, AbandonedUploadSweeper
│       └── web/IssueController, CommentController, AttachmentController,
│                IssueMetadataController, IssueAssembler, dto/
│
├── workflow/
│   ├── WorkflowService, WorkflowProvisioning, AvailableTransition,
│   │   IssueFacts, TransitionEffect
│   └── internal/{domain, persistence, application/{conditions,validators,postfunctions}}
│
├── board/
│   ├── BoardService, BoardView, BacklogView, SprintResponse
│   └── internal/
│       ├── domain/Sprint, SprintState, SprintIssue     # membership is a join table
│       ├── persistence/SprintRepository, SprintIssueRepository
│       ├── application/BoardServiceImpl, SprintService
│       └── web/BoardController
│
├── search/
│   ├── SearchService, SearchHit, SearchResults, SavedFilterResponse
│   └── internal/
│       ├── jql/JqlLexer, JqlParser, Jql (sealed AST), JqlField, JqlOperator,
│       │       JqlSyntaxException
│       ├── query/JqlCompiler, SearchContext
│       ├── persistence/SearchIndexWriter, SearchQueries, SavedFilterRepository
│       ├── domain/SavedFilter
│       ├── application/SearchServiceImpl, SearchIndexer, SavedFilterService
│       └── web/SearchController
│
├── notification/
│   └── internal/
│       ├── domain/Notification, NotificationKind, DigestState
│       ├── persistence/NotificationRepository, DigestStateRepository
│       ├── application/NotificationDispatcher, NotificationService,
│       │              NotificationStream, DigestJob
│       ├── email/EmailSender
│       └── web/NotificationController, NotificationStreamController, dto/
│
├── storage/                         # generic: presign up/down, head, delete
│   ├── StorageService.java          #   knows nothing about issues
│   └── internal/S3StorageAdapter, StorageConfig, StorageProperties
│
├── activity/
│   └── internal/
│       ├── domain/IssueHistory, HistoryKind
│       ├── persistence/IssueHistoryRepository
│       ├── application/IssueHistoryRecorder, ActivityService
│       └── web/ActivityController, dto/ActivityEntry
│
└── integration/
    └── internal/
        ├── domain/Webhook, WebhookDelivery, WebhookEvent
        ├── security/HmacSigner
        ├── persistence/WebhookRepository, WebhookDeliveryRepository
        ├── delivery/WebhookDispatcher (enqueue), WebhookDeliveryJob (schedule+lock),
        │            WebhookDeliveryRunner (transactions), WebhookSender,
        │            HttpWebhookSender, WebhookPayload
        ├── application/WebhookService
        └── web/WebhookController

src/main/resources/db/migration/
├── V1__baseline.sql              V7__federated_identity_groundwork.sql
├── V2__identity.sql              V8__invitations.sql
├── V3__project.sql               V9__events_and_activity.sql
├── V4__issue.sql                 V10__sprints_and_ranking.sql
├── V5__soft_delete_timestamp.sql V11__search.sql
├── V6__workflow.sql              V12__integration.sql
│                                 V13__global_accounts.sql

src/test/java/com/charlie/ikibaho/
├── ModularityTests.java             # ApplicationModules.verify() — the boundary guard
├── AbstractIntegrationTest.java     # Testcontainers Postgres, truncate between tests
├── support/TestFixtures.java, InMemoryStorage.java
├── events/EventPipelineIntegrationTest.java
├── identity/MembershipIntegrationTest, GlobalAccountMigrationTest
├── issue/IssueServiceIntegrationTest, AttachmentIntegrationTest, rank/LexoRankTest
├── board/BoardIntegrationTest.java
├── search/JqlParserTest, JqlCompilerTest, SearchIntegrationTest
└── integration/HmacSignerTest, WebhookIntegrationTest,
                internal/delivery/RecordingWebhookSender
```

**Rules that keep this honest:**

1. `internal` is invisible across modules. `issue` may call `ProjectService`; it may **never** import `project.internal.domain.Project`.
2. Cross-module reads go through the module's public interface, returning **records, not entities**. No JPA relationship may cross a module boundary — reference other modules' aggregates by `UUID`, not by `@ManyToOne`. (Inside a module, `@ManyToOne` is fine and encouraged.)
3. Anything reactive to a change (notify, index, log activity, fire webhook) is an **event listener**, never a synchronous call from `IssueService`.
4. `ModularityTests` runs in CI. When it fails, you have an architecture decision to make — that's the point.

---

## 6. Build order — as built

Each phase ended with something you could hit with `curl`. That held, and it was the right call: every phase surfaced a design problem that would have been invisible on paper.

| # | Phase | Migration | Landed |
|---|---|---|---|
| 1 | **Foundations** — Postgres, compose, Flyway, Testcontainers, `ProblemDetail`, `BaseEntity` | `V1` | ✅ |
| 2 | **Identity** — register, login, JWT + JWKS + resource server, refresh rotation, invitations | `V2`, `V7`, `V8` | ✅ |
| 3 | **Projects & permissions** — roles, permission schemes, `PermissionEvaluator`, `@PreAuthorize` | `V3` | ✅ |
| 4 | **Issues CRUD** — types, key generation, comments, optimistic locking, `jsonb` custom fields | `V4`, `V5` | ✅ |
| 5 | **Workflow** — statuses, transitions, conditions/validators/post-functions | `V6` | ✅ |
| 6 | **Events & activity** — Modulith, `@ApplicationModuleListener`, `issue_history`, notifications | `V9` | ✅ |
| 7 | **Boards & sprints** — LexoRank, backlog, board view, sprint lifecycle | `V10` | ✅ |
| 8 | **Search** — Postgres FTS, JQL parser, saved filters | `V11` | ✅ |
| 9 | **Integration** — attachments, webhooks with HMAC + retry, digests, SSE | `V12` | ✅ |
| 10 | **Global accounts** — one account per person, membership per organization | `V13` | ✅ |

Phase 10 was not in the original plan. It came out of a question the plan could not answer — "can somebody sign up with their Gmail address and join an existing workspace?" — which turned out to be blocked by an inconsistency nobody had noticed: the schema said `UNIQUE (organization_id, email)` while every code path assumed email was globally unique. See §8.

Doing permissions at phase 3, before issues, was the single best sequencing decision. Every service written afterwards takes an `actorId` and checks a permission as its first statement, because there was never a version of the code where it didn't.

**"Prove the outbox works by killing the app mid-handler"** turned out to be the wrong test, and a useful lesson. The first attempt reset `completion_date` by SQL and called `resubmitIncompletePublications` — which redelivered nothing, so the test passed even with the dedupe key deliberately broken. What actually proves it: deliver one identical event twice and assert a single history row. Verified by breaking the key on purpose and watching it fail (`expected 1L but was 2L`). See §8.

### What is deliberately not built

- **Components and versions/releases** — `project` owns them in §1's table; the tables do not exist.
- **Worklogs and time tracking** — the `issue` module's remit, never built.
- **Idempotency keys on `POST /issues`** (§4). Double-clicking Create still makes two tickets.
- **Rank rebalancing.** `LexoRank.isDegenerate` flags ranks that have grown past 8 characters; nothing acts on it yet.
- **Notification preferences.** §4 says per-user, per-project "from day one"; there is one global digest and no opt-out.
- **Virtual threads.** `spring.threads.virtual.enabled` is not set.
- **Federated sign-in.** `V7` made `password_hash` nullable for it and phase 10 made the account global, which is the hard prerequisite — but there is no `user_identity` table and no OAuth client. Note that `ck_app_user_authenticatable` currently *rejects* an active account with no password, exactly as its own comment warns; that constraint has to change when SSO lands.
- **Email verification and password reset.** §3 lists both as needed. Neither exists.

---

## 7. Dependencies — as built

H2 and `spring-boot-h2console` are gone. Everything on the original list is in, plus the Modulith artifacts:

```
spring-modulith-starter-core
spring-modulith-starter-jpa      (event publication registry)
spring-modulith-actuator         (runtime — exposes incomplete publications)
spring-modulith-starter-test     (test — brings Awaitility)
```

Two things the plan didn't anticipate:

**Boot 4.1 ships Jackson 3** (`tools.jackson.databind`). Jackson 2 is still on the classpath transitively via the AWS SDK, so `com.fasterxml.jackson.databind.ObjectMapper` *imports* fine and then fails to inject — there is no bean of that type. Anything wanting an `ObjectMapper` must use `tools.jackson.databind`. Jackson 3 also made its exceptions unchecked: catch `tools.jackson.core.JacksonException`, not `JsonProcessingException`.

**`RestClient.Builder` is not auto-configured** under a plain `spring-boot-starter-webmvc`. `HttpWebhookSender` builds its own client via `RestClient.builder()` — which is better anyway: the shared builder carries whatever interceptors the app registers globally, and one that propagates the caller's `Authorization` header would attach this API's credentials to a stranger's server.

**Unused, kept:** `spring-boot-starter-cache` and `caffeine`. Nothing uses `@Cacheable`; `PermissionCache` is a hand-rolled request-scoped memo. Either wire them up or drop them.

**On Lombok:** it is in the pom and used essentially nowhere — the code hand-writes constructors and getters. That was accidental rather than decided, but the result reads consistently, so it stayed. DTOs, commands, events and query projections are all **records**. No entity carries `@Data`/`@EqualsAndHashCode`: generated `equals`/`hashCode` over all fields triggers lazy loading and breaks `Set` semantics for detached entities. Entity equality is id-based only, in `BaseEntity`.

---

## 8. What building it changed

The design above survived contact with the compiler largely intact. These are the places it didn't, and the traps that only appear once something runs.

### Where code ended up somewhere other than §1 said

**`LexoRank` is in `issue`, not `board`.** Ranks are stored on the issue row and only `issue` may write those. Putting the algorithm in `board` means board writing issue tables, or `issue` depending on `board` and closing a cycle. Board consumes the resulting order rather than producing it.

**SSE is in `notification`, not `integration`** — for the same reason, and `ModularityTests` proved it: placing `NotificationStream` in `integration` produced `integration → notification → integration` immediately.

**Attachment metadata is in `issue`, not `storage`.** Attachments hang off an issue and their permission checks are issue-context checks. `storage` stayed a generic capability that knows nothing about issues: presign up, presign down, head, delete.

**`UserGroup` moved from `project` to `identity`.** §1 assigned groups to `project`, but the only code that touches them is `UserServiceImpl` — and `identity` owning a repository over a `project` entity was a genuine cycle.

**Sprint membership is a `sprint_issue` join table**, not a `sprint_id` column on `issue`. Sprints are board's concept; the issue table must not grow a column only board understands. This is also what keeps the two modules acyclic.

**`search` owns a denormalized index table** rather than a `tsvector` on `issue`. See §4.

### The identity model, and why it changed

The original schema had `app_user.organization_id` and `UNIQUE (organization_id, email)` — a user belonged to one workspace. Every code path disagreed: `findByEmail` and `existsByEmail` were global, so the same address could never actually reach two organizations. The schema permitted something the application forbade, and nothing failed because the stricter rule won by accident.

That surfaced as a product question — can somebody use their Gmail address to join an existing workspace? — with three possible answers:

| Model | Login knows the org by | Cost |
|---|---|---|
| **Per-org users** | A workspace slug in the request, or a subdomain | One person spanning workspaces needs one account *per workspace*, each with its own password |
| **Global account + membership** | Nothing; resolved after authenticating | One password per person; org is a session property |
| Two-step discovery | Enter email, pick from a list | Impossible here, and leaks: per-org users have per-org passwords, so there is nothing to verify before the org is known |

**Jira Cloud is the second.** An Atlassian account is global, `accountId` is its stable identifier, and site access is granted per site; `yourcompany.atlassian.net` routes you to a site you already belong to rather than telling the system who you are. Jira Data Center is the *first* — one instance, one tenant, one user directory — which is the right model for genuinely isolated tenants and the wrong one for a SaaS product.

Ikibaho took the Jira Cloud model in phase 10. The consequences worth knowing:

- **The role had to move with it.** `GlobalRole.SITE_ADMIN` on the user became `MemberRole.ADMIN` on the membership. Left where it was, being invited into a second workspace would have made you an admin of it because you were an admin of your first — a privilege escalation by invitation, which is now pinned by a test.
- **`org` and `roles` became session properties.** Both come from the membership chosen at login, so refresh tokens carry `organization_id` and re-check the membership on rotation.
- **Accepting an invitation split in two.** A *new* account sets its first password from the link. An account that already has one must be **signed in** to accept — a link proves only that an email was received, and letting it set a password on an established account turns a forwarded invitation into an account takeover.
- **The abstraction was already right before the schema was.** `UserService.userExistsInOrganization(userId, orgId)` is a membership predicate, not a field read, so its only caller — `ProjectServiceImpl` — did not change at all. Only three sites read `User.getOrganizationId()`.

### Boundary decisions the gate forced

Running `ApplicationModules.verify()` for the first time — at phase 6, against five phases of existing code — found three real defects, not nitpicks:

1. **`GlobalExceptionHandler` named identity's exception classes.** The shared kernel depended on one of its dependents, which is a cycle and the reason `identity` could never have been extracted. Fixed by adding `platform.error.UnauthorizedException` for the handler to catch; identity's two exceptions subclass it.
2. **`SecurityConfig` took `IkibahoPermissionEvaluator`** from `project.internal`. It only ever needed Spring's `PermissionEvaluator` interface.
3. **`Permission` was in `project.internal.domain`** while the public `PermissionService` exposed it. §1 always said it should be public; the code disagreed. Moved to `project`.

Two structural declarations make the rest work:

- **`platform` is `@ApplicationModule(type = OPEN)`.** Its sub-packages are its API — every module is meant to extend `BaseEntity` and throw `NotFoundException`. The default rule, that sub-packages are private, describes a feature module, and platform is not one. What OPEN does *not* relax is direction: nothing in `platform` may depend on a feature module, and the gate still fails the build if it does.
- **Each `events` package is a `@NamedInterface("events")`.** Without it a sub-package is module-private, which is right for `persistence` and exactly wrong for events, whose entire purpose is to be consumed elsewhere.

### Traps worth remembering

**`@Transactional` self-invocation.** `WebhookDeliveryJob.deliverDue()` called `this.claim()` and `this.deliverOne()`. Spring's transaction support works through a proxy, so a self-call goes straight to the target and the annotation is silently ignored — no transaction, no dirty checking, every delivery stuck at `PENDING` while the code looked correct. Fixed by splitting the transactional work into `WebhookDeliveryRunner`, whose methods are **public** because Spring skips proxying non-public methods and would have reintroduced it quietly.

**`@Modifying(clearAutomatically = true)` clears the entire persistence context**, not just the rows it deleted. A bulk delete of sprint membership detached the `Sprint` the caller was holding, so the subsequent `complete()` was dropped at commit. Worse, the test passed: it asserted against the returned object, which was the detached instance. The response said `COMPLETED`; the row said `ACTIVE`. Assert against a re-read, not the object you just mutated.

**`@Async` without `@EnableAsync` is not an error.** The method runs inline, on the publisher's thread, inside the publisher's transaction. `@ApplicationModuleListener` is meta-annotated `@Async`, so everything appears to work while silently losing the isolation the whole design depends on. `AsyncConfig` exists for that one annotation.

**A missing `@Bean` fails late, not at startup.** `SchedulingConfig` declared `LockProvider lockProvider(DataSource)` without the annotation. `@EnableSchedulerLock` still installed its advisor, so the context came up clean and every locked job threw `NoSuchBeanDefinitionException` at *invocation* — visible only when a job actually ran.

**At-least-once means writing for redelivery.** `issue_history` and `notification` both carry a unique `dedupe_key` derived from the event, because a duplicated audit line reads as the user having done the thing twice. The search index needs no such key: an upsert is idempotent by construction, since rewriting a document writes the same thing.

**Awaitility polls on its own thread**, and `SecurityContextHolder` is a `ThreadLocal`. Any assertion calling a permission-checked service needs `.pollInSameThread()` or it fails with "No authenticated user" no matter how long it waits.

**A green suite does not mean a migration was tested.** Every test starts from an empty database, so all thirteen migrations run over nothing — which means the *backfill* in `V13`, the one statement in the project capable of destroying data, was executed by no test at all while 124 of them passed.

`GlobalAccountMigrationTest` closes that: its own Postgres container, migrate to `V12`, write rows in the old shape, *then* apply `V13` and assert. Deliberately no Spring context — `ddl-auto=validate` would reject the `V12` schema, because the entities describe the world after it. Verified load-bearing by changing the backfill to `FROM app_user u WHERE false`, a migration that succeeds and silently migrates nothing: `expected 2L but was 0L`.

The general rule: **a migration that transforms existing rows needs a test that gives it existing rows.** Schema-only migrations do not, because the schema is asserted by `ddl-auto=validate` on every other test.

### Known sharp edges

- **The search indexer has no per-issue ordering.** Two events for one issue can interleave across the pool and a stale read can win, leaving the index behind until the next event. The fix is a guard on the upsert: `WHERE issue_search_index.updated_at <= EXCLUDED.updated_at` — `<=` not `<`, because `IssueCommented` does not change `issue.updated_at`.
- **Search pagination is offset-based**, unlike every other list endpoint. With a user-supplied `ORDER BY` over a dozen columns, a keyset cursor would have to encode the sort key's type and direction. `limit` is capped at 100; `offset` is not capped.
- **The count query runs the full predicate twice.** Deliberate — `COUNT(*) OVER ()` would materialise every match to show twenty — but a broad query costs two scans.
- **`event_publication` sits on the write path of every domain change**, and now has four consumers (activity, notification, search, integration). Publications that never complete accumulate there. `spring-modulith-actuator` is exposed at `/actuator/modulith` for exactly this; eventually it wants `completion-mode=archive`.
- **`@Scheduled` jobs run during integration tests.** The webhook tests call `drainNow()` directly rather than waiting for the scheduler, since ShedLock's `lockAtLeastFor` correctly suppresses repeat runs and a test that waits for a scheduler is a test that waits.
- **Deactivated members keep their `project_role_actor` rows.** Inert — permission checks go through `userExistsInOrganization`, which requires an ACTIVE membership — but they still appear in project role admin screens. Wants a sweep.
- **The login contract changed in phase 10 and `ikibaho-ui` has not caught up.** `POST /auth/login` now returns `{userId, tokens, organizations}` with a **nullable** `tokens`, and `POST /auth/switch-organization` is new. The UI needs a workspace picker and switcher before it can authenticate against this.
