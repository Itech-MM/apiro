# Apiro endpoint manifest: design (revision 2)

Date: 2026-11-27
File: `docs/manifest-spec-design.md`. This is the single source of truth for the manifest contract.
Module: `apiro-spec` (Java 8, no dependencies). Producers: `apiro-spring-webmvc` now, other framework SDKs later. Consumer: `apiro-core` importer, then `apiro-desktop`.

Revision 2 folds in the architecture review (`minifest-design-review.md`). Read section 0 first. It says what is decided, what is proposed, and what is blocked, so everyone works from the same picture.

---

## 0. Team alignment

### 0.1 Status labels used in this document

| Label | Meaning |
|---|---|
| **DECIDED** | Agreed. Implement it. |
| **PROPOSED** | Recommended by the review and checked against the code. Needs a team yes or no before implementation. |
| **DEFERRED** | Real idea, intentionally postponed. Can be added later without a version bump. |
| **OPEN** | A question with a recommendation but no answer yet. |

### 0.2 What exists in the repository

| Area | State |
|---|---|
| Multi-module split, CI matrix | Done. |
| `apiro-spec` revision 2 | Done: bean classes, validator, golden files, tests. `manifestVersion` stays 1 because no producer ever wrote revision 1. |
| `apiro-spring-webmvc` | **Still a stub.** `ApiroManifestController` returns an empty endpoint list. |
| `apiro-spring-boot-starter` | Skeleton, off by default (`apiro.enabled`). |
| `apiro-core` | Reader, fetcher, importer and re-import diff exist and read revision 2. The `imported` snapshot (7.2) is not implemented: re-import matches on the endpoint ID string. |
| `apiro-desktop` | Import dialog and review pages exist. |

### 0.3 Known gaps

1. Re-import derives method, path and handler by splitting the ID, which section 6.1 forbids. The snapshot in 7.2 has to replace this and needs a `handler` field.
2. The snapshot stores only `bodySchemaKey`, so a changed schema under the same key is not detected.

### 0.4 Decision log

| # | Topic | Status | Decision or recommendation |
|---|---|---|---|
| D1 | Endpoint ID format | PROPOSED | Opaque string. Java producers use fully qualified erased types and the raw mapping pattern (section 6). |
| D2 | Extra mapping conditions | PROPOSED | Keep `consumes` and `produces` top-level. Add `conditions` for `params`, `headers` and `unsupported` (section 4.3). |
| D3 | Schema keys | PROPOSED | Canonical type name including type arguments, plus optional `title` for display (section 5). |
| D4 | Security model | DECIDED | Nested `authentication` / `authorization` (section 4.5). Replaces `security.required` and `security.schemes`. |
| D5 | Parameter `required` vs UI `enabled` | PROPOSED | Separate concepts. Importer stores a snapshot on `RequestItem` instead of widening `KeyValue` (section 7). |
| D6 | Path semantics | PROPOSED | Add raw `pattern`. Do not store an `effectivePath`. Derive the context path on the Apiro side when the app cannot know it (section 3.1). |
| D7 | Canonical JSON | PROPOSED | One policy table plus ordering rules (section 2.1). |
| D8 | `source` field | PROPOSED | Root field: `runtime`, `build` or `openapi` (section 3). |
| D9 | `capabilities` | DEFERRED | Belongs to the debug-bridge handshake. Can be added later without a version bump. |
| D10 | Examples | DECIDED | Never extracted in v1. A later opt-in property may enable it. |
| D11 | Methodless mappings | DECIDED | One endpoint with `method: "ANY"` (section 4.2). |
| D12 | Re-import ambiguity | PROPOSED | One-to-one matching, never auto-pick an ambiguous candidate (section 6.2). |
| D13 | Responses | DECIDED | Out of v1. Added in v1.1 as an additive change. |
| D14 | Schema limits | DECIDED | Depth 5, at most 500 named schemas. |
| D15 | Live endpoint security | PROPOSED | Secure by default: generated token when none is configured (section 8). |

D1, D2, D3, D6 and D8 are implemented in spec revision 2.

### 0.5 Work breakdown and dependencies

Assign an owner per row.

| Step | Work | Depends on | Can run in parallel with | Owner |
|---|---|---|---|---|
| 1 | Spec revision 2: bean classes, validator, goldens, this document | D4, D11 answered | none | TBD |
| 2 | Producer: `ManifestBuilder`, schema mapper, JSON writer, Spring reader | Step 1 | Steps 4 and 5 | TBD |
| 3 | `ApiroManifestController`: real output, token, optional file export | Step 2 | Steps 4 and 5 | TBD |
| 4 | Core: `ManifestReader` (Gson), `ManifestImporter`, snapshot field on `RequestItem` | Step 1 | Steps 2 and 3 | TBD |
| 5 | OpenAPI to manifest converter in core | Step 1 | Steps 2 to 4 | TBD |
| 6 | Core: re-import diff | Step 4 | Step 3 | TBD |
| 7 | Desktop: connect, import preview, diff view | Steps 3 and 6 | none | TBD |

Steps 4 and 5 only need the golden files from step 1, so core can start the moment spec revision 2 merges.

### 0.6 Ground rules for everyone

- Any change to a spec class must update the golden files and this document in the same pull request.
- No comments in Java files.
- `apiro-spec` and `apiro-spring-webmvc` stay Java 8, with no JSON library, no servlet types, no JavaFX.
- Do not add framework-specific types to `apiro-core`.

---

## 1. Purpose and non-goals

The manifest is the one contract between "a running or built API project" and Apiro. Every SDK emits it. Apiro reads only it.

Main principle: **framework-specific knowledge lives in framework SDKs. Apiro Core understands only the manifest.**

It carries **what the API is**: routes, parameters, request body shape, validation constraints, security facts when known, handler identity, shared schemas, and metadata about the application and producer.

It does **not** carry:
- real data, credentials, tokens, or environment values
- response bodies, request traffic, or runtime events (that is the debug bridge, a separate design)
- anything Apiro users edit later: saved requests, scenarios, assertions

Why a custom format when OpenAPI exists: OpenAPI stays an import source and is converted into the same manifest (section 3, `source`). The manifest exists for what OpenAPI usually lacks: the exact runtime route table, handler identity for debugging, validation constraints as the code sees them, and a stable endpoint identity so re-import can diff without clobbering user edits.

---

## 2. Design rules

1. **Plain JSON.** No framework types leak in.
2. **Additive evolution.** Producers may add optional fields without bumping the version. Consumers ignore unknown fields. Removing a field, changing its meaning or tightening a type bumps the major version.
3. **One endpoint = one HTTP method + one path + one set of mapping conditions.** A Spring mapping declaring `{GET, POST}` becomes two endpoints. A mapping with no method restriction becomes one endpoint with method `ANY` (D11).
4. **No secrets, ever.** Security describes requirements, never values.
5. **Report only what is known.** Unknown is not false. A producer never invents information the framework cannot determine.
6. **Deterministic output.** Same code, same manifest, byte for byte, except `generatedAt`.
7. **Java 8 representation.** Plain classes with getters. No records, no `Optional` fields.
8. **Cycle safe.** Object shapes are named schemas referenced by key.
9. **Names legal in every language.** JSON keys are valid identifiers in Java and most languages. This is why the schema reference is `ref` and the allowed-values list is `enumValues`.
10. **User data stays with the user.** The manifest owns endpoint metadata. Apiro owns saved headers, values, environments, auth values, assertions and scenarios. Re-import never silently overwrites user data.

### 2.1 Canonical output (D7, proposed)

Producers and golden files follow this policy. **Readers accept any valid JSON** and apply the defaults below.

| Field | When empty or default | Rule |
|---|---|---|
| `endpoints` | empty | Always written, as `[]` |
| `schemas` | empty | Omit |
| `application` | all fields empty | Omit the object |
| `application.contextPath`, `application.servletPath` | empty | Omit. Reader defaults to empty. |
| `consumes`, `produces`, `parameters` | empty | Omit |
| `conditions` | all parts empty | Omit the object |
| `deprecated`, `required` (parameter, body, schema list), `nullable` | `false` | Omit |
| Any string | empty or null | Omit |
| Any optional metadata | absent | Omit |
| `security` | nothing known | Omit the object |

Ordering and formatting:
- Object keys are written in the order this document lists them.
- `endpoints`: by `path`, then `method`, then `id`.
- `schemas` keys: sorted by UTF-8 byte order (equals Unicode code point order). Java's `String.compareTo` uses UTF-16 order, which differs only for characters outside the Basic Multilingual Plane. Producers should use a code point comparator.
- `properties`: declaration order of the source type.
- `parameters`: signature order of the handler. Path variables the handler does not declare are appended after, in path order.
- `enumValues`: declaration order.
- Media types, `conditions` expressions, `schemes`: sorted alphabetically.
- Compact output, UTF-8, no byte order mark. Whole numbers have no decimal point (`1`, not `1.0`). Control characters escape as `\u00xx` with lowercase hex. Other characters are written as is.
- `generatedAt` is excluded from golden comparisons.

---

## 3. Document shape

```json
{
  "manifestVersion": 1,
  "source": "runtime",
  "generator": {
    "name": "apiro-spring-webmvc",
    "version": "1.0.0",
    "framework": "spring-webmvc",
    "frameworkVersion": "6.2.0"
  },
  "generatedAt": "2026-11-26T10:15:30Z",
  "application": {
    "name": "orders-service",
    "contextPath": "/shop"
  },
  "endpoints": [ ... ],
  "schemas": { ... }
}
```

| Field | Type | Required | Notes |
|---|---|---|---|
| `manifestVersion` | int | yes | Major format version. Starts at 1. |
| `source` | string | no (D8, proposed) | `runtime`, `build` or `openapi`. Tells consumers what information to expect. An `openapi` manifest has no `handler`, and Apiro never fabricates one. |
| `generator` | object | yes | Who produced it. |
| `generatedAt` | string, ISO-8601 UTC | no | Excluded from golden comparisons. |
| `application.name` | string | no | `spring.application.name` or the context name. |
| `application.contextPath` | string | no | Web application prefix. See 3.1. |
| `application.servletPath` | string | no | Servlet mapping prefix when the dispatcher is not at the root. |
| `endpoints` | array | yes | Sorted. |
| `schemas` | map key to schema | no | Shared shapes. See section 5. |

There is **no `baseUrl`** and **no `effectivePath`.** Host and port belong to whoever fetched the manifest. The full path is `contextPath + servletPath + path`, derived by the reader, so it can never disagree with the parts.

`capabilities` is not part of v1 (D9, deferred). Runtime features such as live events belong to the debug-bridge handshake.

### 3.1 Context path

A producer that cannot know the context path leaves it out. In plain Spring MVC the producer deliberately avoids servlet types, so it may not know it. The consumer then derives it from the URL it fetched the manifest from:

```
contextPath = fetchedPath - servletPath - "/apiro/manifest"
```

A producer that does know it (for example from `server.servlet.context-path` or `apiro.context-path`) writes it. This also covers a deployment behind a path-rewriting proxy only if the user supplies the value. v1 documents one dispatcher servlet per manifest. Multiple dispatcher servlets are out of scope for v1.

---

## 4. Endpoint

```json
{
  "id": "POST /orders/{customerId}#com.shop.OrderController.create(long,boolean,java.lang.String,com.shop.CreateOrder)",
  "method": "POST",
  "path": "/orders/{customerId}",
  "group": "OrderController",
  "name": "create",
  "consumes": ["application/json"],
  "produces": ["application/json"],
  "parameters": [
    { "name": "customerId", "in": "path",   "required": true, "schema": { "type": "integer", "format": "int64" } },
    { "name": "dryRun",     "in": "query",  "defaultValue": "false", "schema": { "type": "boolean" } },
    { "name": "X-Trace",    "in": "header", "schema": { "type": "string" } }
  ],
  "body": {
    "contentType": "application/json",
    "required": true,
    "schema": { "ref": "com.shop.CreateOrder" }
  },
  "security": {
    "authentication": { "status": "declared", "schemes": ["bearer"], "source": "configuration" },
    "authorization": { "status": "declared", "access": "restricted", "source": "method-annotation" }
  },
  "handler": {
    "className": "com.shop.OrderController",
    "methodName": "create",
    "parameterTypes": ["long", "boolean", "java.lang.String", "com.shop.CreateOrder"]
  }
}
```

### 4.1 Fields

Key order is the order listed here.

| Field | Notes |
|---|---|
| `id` | Opaque stable identity. See section 6. |
| `method` | `GET`, `POST`, `PUT`, `PATCH`, `DELETE`, `HEAD`, `OPTIONS`, or `ANY` (D11). |
| `path` | Normalized template without context path. `{name}` placeholders only. Regex segments like `{id:\d+}` become `{id}`, and the regex moves to `parameters[].schema.pattern`. `{*rest}` becomes `{rest}`. |
| `pattern` | The original framework pattern, exactly as reported. Omitted when it equals `path`. |
| `group` | Controller short name. Becomes an Apiro folder. |
| `name` | Handler method name. Becomes the request name. |
| `summary`, `deprecated` | Optional. |
| `consumes`, `produces` | Media types declared on the mapping. |
| `conditions` | Other mapping conditions. See 4.3. |
| `parameters` | Path, query, header, cookie, form, part. |
| `body` | Absent when the endpoint takes no body. |
| `security` | See 4.5. Absent when nothing is known. |
| `handler` | Declaring class, method name, erased fully qualified parameter types. Used for the debug bridge and for re-import matching. Absent for `openapi` sources. |

### 4.2 Methodless mappings (D11)

A Spring mapping such as `@RequestMapping("/x")` with no method matches every method. Skipping it loses a real endpoint (Spring Boot's own `/error` controller is of this kind).

A producer writes **one** endpoint with `"method": "ANY"`.
- The manifest states the truth: any method is accepted. It invents no restriction and no seven-way expansion.
- The importer creates a request defaulting to `GET`, with a visible note "accepts any method". On re-import the method the user picked is kept.
- Producers exclude known framework controllers by default (Boot's `BasicErrorController`) and support a configurable exclude list (`apiro.exclude-packages`), so documentation endpoints from other libraries do not flood the manifest.

### 4.3 Mapping conditions (D2, proposed)

Two handlers can share a method and path and differ only by conditions. Record the effective routing semantics.

```json
"conditions": {
  "params": ["version=2"],
  "headers": ["X-Client=mobile"],
  "unsupported": ["ApiVersionRequestCondition"]
}
```

- `params` and `headers` hold the raw framework expressions (Spring forms: `name`, `name=value`, `name!=value`, `!name`). They are **not parsed** in v1.
- `unsupported` lists custom or newer condition types the producer detected but cannot describe. Spring Framework 7 adds API-version mapping as far as I know. Verify against the Spring 7 documentation. Conditions must never be dropped silently.
- A negated media type such as `consumes = "!text/plain"` is reported in `unsupported` until a producer can represent negation.

### 4.4 Parameter

| Field | Values |
|---|---|
| `name` | Parameter name |
| `in` | `path`, `query`, `header`, `cookie`, `form`, `part` |
| `required` | boolean. Always true for `path`. |
| `defaultValue` | string, optional. Developer-authored constant. |
| `description` | optional |
| `schema` | inline schema (section 5) |

`required` describes the API contract. It says nothing about whether a user wants the parameter in a particular request (section 7).

### 4.5 Security model (D4)

Authorization annotations such as `@PreAuthorize`, `@Secured` and `@RolesAllowed` prove that access is restricted. They do **not** prove how a client authenticates. A role check does not mean Bearer.

The shape separates the two ideas:

```json
"security": {
  "authorization": { "status": "declared", "access": "restricted", "source": "method-annotation" }
}
```

| Field | Values |
|---|---|
| `authentication.status` | `unknown` or `declared` |
| `authentication.schemes` | `bearer`, `basic`, `apiKey`, ... Only when `declared`. |
| `authentication.source` | `configuration` (the developer stated it, for example `apiro.security.authentication=bearer`). `filter-chain` is a later phase, when a producer reads the Spring Security filter chain. |
| `authorization.status` | `unknown` or `declared` |
| `authorization.access` | `restricted` or `open` (`@PermitAll`) |
| `authorization.source` | `method-annotation` or `class-annotation` |

Rules:
- Unknown is **never** read as unauthenticated.
- A JVM producer attaches configured authentication only to endpoints whose authorization is declared `restricted`.
- `authentication` and `authorization` are each omitted when their status is `unknown`. The whole `security` object is omitted when nothing is known.
- The importer suggests an `AuthConfig` only when `authentication.status` is `declared` and names a scheme. It never writes secret values.
- Role names and expressions (`hasRole('ADMIN')`) are not exported in v1. They describe policy, not API shape.

This replaces revision 1's `security.required` and `security.schemes`. The bean classes and validator change with it.

---

## 5. Schema

A deliberately small subset of JSON Schema. Enough to generate a sample body and show constraints. Not a validator.

| Field | Notes |
|---|---|
| `type` | `string`, `integer`, `number`, `boolean`, `array`, `object`, `file`, `any` |
| `title` | Optional display name (for example `ApiResponse<User>`). Producers set it so the UI does not show long keys. |
| `format` | `int32`, `int64`, `float`, `double`, `date`, `date-time`, `time`, `uuid`, `uri`, `email`, `byte`, free text otherwise |
| `ref` | Key in the top-level `schemas` map. When present, the schema carries no other field. |
| `items` | Schema, for `array` |
| `properties` | Map of name to schema, for `object` |
| `required` | Property names, for `object` |
| `enumValues` | Allowed string values |
| `nullable` | boolean |
| `minimum`, `maximum` | numbers |
| `minLength`, `maxLength`, `pattern` | string constraints |
| `minItems`, `maxItems` | array constraints |
| `example` | Never produced in v1 (D10). |

### 5.1 Schema keys (D3, proposed)

Keys in `schemas`, and every `ref`, use the canonical name of the fully resolved type, **including type arguments**:

```json
"schemas": {
  "com.shop.dto.User": { "type": "object", "title": "User", ... },
  "com.admin.dto.User": { "type": "object", "title": "User", ... },
  "com.shop.ApiResponse<com.shop.dto.User>": { "type": "object", "title": "ApiResponse<User>", ... },
  "com.shop.ApiResponse<com.shop.Order>": { "type": "object", "title": "ApiResponse<Order>", ... }
}
```

- JVM producers use `Type.getTypeName()` of the resolved type. Nested classes keep `$`.
- Non-JVM producers use any stable unique string.
- Generic classes are resolved by substituting type arguments into property types. Unresolvable type variables become `{ "type": "any" }`.
- The importer shows `title`, falling back to the last segment of the key.
- Limits (D14): nesting depth 5, at most 500 named schemas. A producer that hits a limit emits `any` and reports a warning.

### 5.2 Bean Validation

Read by annotation name, so there is no compile-time dependency. Both `jakarta.validation.constraints` and `javax.validation.constraints` are recognised.

| Annotation | Result |
|---|---|
| `@NotNull`, `@NotBlank`, `@NotEmpty` | property listed in `required` |
| `@NotBlank` | string `minLength` at least 1 |
| `@NotEmpty` | string `minLength` or array `minItems` at least 1 |
| `@Size` | length limits on strings, item limits on arrays |
| `@Min`, `@Max`, `@DecimalMin`, `@DecimalMax` | `minimum`, `maximum` |
| `@Positive`, `@PositiveOrZero` | `minimum` 1 or 0 for integers |
| `@Pattern` | `pattern` |
| `@Email` | `format` `email` |

Jackson `@JsonProperty` renames a property and `@JsonIgnore` drops it, both read by name.

---

## 6. Endpoint identity and re-import (D1, D12)

### 6.1 The ID

The ID is an **opaque string**. It is unique inside one manifest and stable across runs of the same code. Consumers compare IDs for equality only. They never parse them.

Why opaque: only the producer family needs to agree on a format. Apiro compares IDs against earlier imports of the same project. A PHP or Node SDK defines its own handler identity.

Normative format for JVM producers:

```
id = METHOD " " pattern "#" className "." methodName "(" typeNames ")"
```

- `METHOD`: the endpoint method, including `ANY`.
- `pattern`: the **raw** mapping pattern as the framework reports it (the same string as the `pattern` field, or `path` when they are equal). Raw, not normalized, so `/x/{id:\d+}` and `/x/{id:[a-z]+}` on one handler stay distinct.
- `className`: `Class.getName()` of the declaring class (the user class, not a proxy).
- `typeNames`: every handler parameter in declaration order, comma-separated, no spaces, each `Class.getTypeName()` of the erased type (`long`, `java.lang.String`, `com.shop.dto.User`, `java.lang.String[]`).
- No parameters gives `()`.

Mapping conditions are **not** in the ID. Two handlers that share a method and path always differ in handler, so their IDs differ. One handler cannot declare the same method and pattern twice.

### 6.2 Re-import matching

Matching needs the snapshot from section 7.2 and is strictly one-to-one: each manifest endpoint matches at most one existing request, and the reverse.

1. Match every endpoint by exact `id`.
2. Among the rest, match by `method` + `path` where the candidate is unique.
3. Among the rest, match by `handler` where the candidate is unique.
4. After each pass, remove matched items and re-evaluate the rest.
5. Anything with several candidates is **ambiguous**. The user chooses. Apiro never picks.
6. Unmatched manifest endpoints are **new**. Existing requests with no match are marked **removed from code** and are never deleted automatically.

---

## 7. Mapping into Apiro's model

| Manifest | Apiro |
|---|---|
| `group` | `Folder` (name = group) |
| endpoint | `RequestItem` (name = `name`, method = `method`; `ANY` imports as GET with a note) |
| URL | `${baseUrl}` + `contextPath` + `servletPath` + `path`, with `{x}` rewritten to `${x}` |
| path parameter | variable `x` on the folder, empty value for the user to fill |
| query, header parameter | `KeyValue` in `query` / `headers`. `enabled` is chosen independently of `required` (see below). |
| body, JSON | `RequestBody` type JSON, text generated from the schema |
| body, form / multipart | `RequestBody` FORM / MULTIPART with parts |
| `security.authentication` | folder `AuthConfig` suggestion when status is `declared`. Values stay empty. |

`${...}` is Apiro's variable syntax (`VariableResolver.REFERENCE`). The importer writes only empty placeholders, never real values.

### 7.1 Required is not enabled (D5, proposed)

`KeyValue` is `(key, value, enabled)`. Setting `enabled = required` mixes two ideas. `required` is the contract. `enabled` is whether this request sends the parameter.

- Do not widen `KeyValue`.
- Initial `enabled` is an importer policy: required parameters and parameters with a `defaultValue` start enabled. Other optional parameters are listed but start unchecked, because sending a blank optional parameter often changes behavior. The default can be changed in settings.

### 7.2 Imported snapshot (D5, proposed)

Add an optional field to `RequestItem`:

```
imported { endpointId, method, path, parameters [name, in, required, schemaRef/type], bodyContentType, bodySchemaKey }
```

The snapshot records what the manifest said **last time**. It is what makes a three-way comparison possible: manifest then, manifest now, and the user's current request. Without it, Apiro cannot tell a user edit from a manifest-owned field.

| Owned by the manifest (refreshed after the user sees a diff) | Owned by the user (never overwritten) |
|---|---|
| method, path, parameter set (name, location, required, schema), body content type and schema, name, group | parameter values and enabled state, headers the user added, auth values, edited body text, assertions, scenarios, extractors |

If the schema changed and the user edited the body text, the preview shows the schema diff and leaves the text alone.

The project JSON loader must tolerate the field being absent. Old projects keep working.

---

## 8. Transport

Same JSON, two ways out.

1. **Live:** `GET /apiro/manifest` on the running app.
   - Off unless `apiro.enabled=true`.
   - Header `X-Apiro-Token`, compared in constant time.
   - **Secure by default (D15, proposed):** if enabled and `apiro.token` is not set, the producer generates a random token at startup and logs it once. Apiro Desktop asks for it on connect. An open endpoint is possible only by explicit configuration.
   - Log a clear warning when enabled while a non-dev profile is active.
2. **File:** written at startup to `apiro.export-file`, or by a build plugin later. Importable offline. Protected by file permissions, not by token.

Binding rules:
- The manifest exposes class names and the whole route surface. Treat it as sensitive.
- Never include real values, credentials or runtime data.

---

## 9. Versioning policy

- `manifestVersion` is an integer major version.
- Additive optional changes do not bump it.
- Removing a field, changing a meaning or tightening a type bumps it.
- Readers reject a manifest with a higher major version than they support and say so plainly.

---

## 10. Validation policy

Two levels, so strictness does not lock out other producers.

- **`validate`** (used by readers): reports only problems that make an import impossible or unsafe. Missing generator name, unsupported version, bad method, path not starting with `/`, undeclared path variable, unknown `ref`. It must **not** reject two endpoints that share a method and path. Today it does (section 0.3, item 1).
- **`validateCanonical`** (used by producers and golden tests): everything above plus endpoint order, ref-only schemas, schema count limit, unsorted collections, non-canonical empties.

Both return a list of readable problems and never throw on bad input.

---

## 11. Contract tests

The producer writes JSON by hand with no Jackson or Gson. `apiro-core` reads it with Gson. Two implementations of one format drift, so shared golden files pin them.

1. Golden files live in `apiro-spec/src/main/resources/examples/`. They double as documentation.
2. `apiro-spring-webmvc` builds a manifest from real test controllers and asserts the written JSON equals the golden file (ignoring `generatedAt`).
3. `apiro-core` parses the same file with Gson and checks fields, versions and refs.
4. Neither module depends on the other. Both depend on `apiro-spec`.

Golden files needed for revision 2:

| File | Shows |
|---|---|
| `minimal.json` | smallest valid manifest |
| `orders.json` | typical controller, validation constraints, nested schemas |
| `conditions.json` | same method and path, different `consumes` or `params`; `unsupported` |
| `methodless.json` | `ANY` endpoint  |
| `generics.json` | `ApiResponse<User>` and `ApiResponse<Order>`, same simple name in two packages |
| `security.json` | each `authentication` and `authorization` combination |

---

## 12. Open questions

1. **Snapshot placement.** Field on `RequestItem` (recommended), or a project-level map keyed by endpoint ID?
2. **Exclusions.** Which framework controllers are excluded by default? Recommendation: Boot's error controller only, with a configurable list for everything else.

---

## 13. Implementation order

See the dependency table in section 0.5. Summary:

1. Spec revision 2 (done).
2. Producer, then live endpoint. In parallel, core reader and importer, and the OpenAPI converter.
3. Re-import diff.
4. Desktop import UI.

Each step ends green on the CI matrix with the golden files as the shared fixture.
