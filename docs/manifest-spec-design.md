# Apiro endpoint manifest: design (v1 draft)

Status: draft for review. Lives in `docs/manifest-spec-design.md`.
Module: `apiro-spec` (Java 8, no dependencies). Producers: `apiro-spring-webmvc` now, other framework SDKs later. Consumer: `apiro-core` importer, then `apiro-desktop`.

---

## 1. Purpose and non-goals

The manifest is the one contract between "a running or built API project" and Apiro. Every SDK emits it. Apiro reads only it.

It carries **what the API is**: routes, parameters, request body shape, security requirements, and where the handler lives in code.

It does **not** carry:
- real data, credentials, tokens, or environment values
- response bodies or traffic (that is the debug bridge, a separate design)
- anything Apiro users edit later (saved requests, scenarios, assertions)

Why a custom format when OpenAPI exists: OpenAPI stays the cheap path for frameworks that already emit it (we import it into the same internal model). The manifest exists for what OpenAPI loses: the exact runtime route table, handler identity for debugging, validation constraints as the code sees them, and a stable ID so re-import can diff without clobbering user edits.

---

## 2. Design rules

1. **Plain JSON.** No framework types leak in.
2. **Additive evolution.** Producers may add optional fields without bumping the version. Consumers ignore unknown fields.
3. **One endpoint = one HTTP method + one path.** A Spring mapping with `{GET, POST}` becomes two endpoints.
4. **No secrets, ever.** Not even examples that look like secrets. Security is described by scheme, never by value.
5. **Deterministic output.** Same code, same manifest, byte for byte (sorted endpoints, no volatile fields except `generatedAt`). This makes diffs and golden tests possible.
6. **Java 8 representation.** Plain classes with getters. No records, no `Optional` fields.
7. **Cycle safe.** Object shapes are named schemas referenced by name, so `Order` containing `List<Order>` does not recurse forever.
8. **Canonical output.** Producers omit every field that holds its default: null, empty string, empty array or object, `false`. Readers apply those defaults. Two exceptions are always written: `endpoints` (even when empty) and `Security.required` when known, because `false` and "unknown" differ. Whole numbers are written without a decimal point (`1`, not `1.0`). Fields are written in the order of this document. This makes golden-file comparison exact.
9. **Names that are legal in every language.** JSON keys are valid identifiers in Java and most other languages, so no consumer needs annotations or renaming. This is why the schema reference is `ref` (not `$ref`) and the allowed-values list is `enumValues` (not `enum`).

---

## 3. Document shape

```json
{
  "manifestVersion": 1,
  "generator": {
    "name": "apiro-spring-webmvc",
    "version": "1.0.0",
    "framework": "spring-webmvc",
    "frameworkVersion": "6.2.0"
  },
  "generatedAt": "2026-11-26T10:15:30Z",
  "application": {
    "name": "orders-service",
    "contextPath": "/shop",
    "servletPath": ""
  },
  "endpoints": [ ... ],
  "schemas": { ... }
}
```

| Field | Type | Required | Notes |
|---|---|---|---|
| `manifestVersion` | int | yes | Major format version. Starts at 1. |
| `generator` | object | yes | Who produced it. Used for bug reports and compatibility quirks. |
| `generatedAt` | string, ISO-8601 UTC | no | Excluded from golden-file comparisons. |
| `application.name` | string | no | `spring.application.name` or the context name. |
| `application.contextPath` | string | no | Prefix every endpoint path is served under. Empty string if none. Apiro prepends it when building URLs. |
| `application.servletPath` | string | no | Servlet mapping prefix, when the dispatcher is not mapped at the root. Empty string if none. Producers omit it when empty. |
| `endpoints` | array | yes | Sorted by `path`, then `method`. |
| `schemas` | map name to schema | no | Shared object shapes referenced from parameters and bodies. |

There is **no `baseUrl` field.** The host and port are known to whoever fetched the manifest (the user's environment variable), not to the app. A manifest fetched from `http://localhost:8080/apiro/manifest` suggests `baseUrl` to the importer, but the manifest never asserts it.

---

## 4. Endpoint

```json
{
  "id": "POST /orders/{customerId}#com.shop.OrderController.create(long,boolean,String,CreateOrder)",
  "method": "POST",
  "path": "/orders/{customerId}",
  "group": "OrderController",
  "name": "create",
  "consumes": ["application/json"],
  "produces": ["application/json"],
  "parameters": [
    { "name": "customerId", "in": "path",   "required": true,  "schema": { "type": "integer", "format": "int64" } },
    { "name": "dryRun",     "in": "query",  "defaultValue": "false", "schema": { "type": "boolean" } },
    { "name": "X-Trace",    "in": "header", "schema": { "type": "string" } }
  ],
  "body": {
    "contentType": "application/json",
    "required": true,
    "schema": { "ref": "CreateOrder" }
  },
  "security": { "required": true, "schemes": ["bearer"] },
  "handler": {
    "className": "com.shop.OrderController",
    "methodName": "create",
    "parameterTypes": ["long", "boolean", "java.lang.String", "com.shop.CreateOrder"]
  }
}
```

| Field | Notes |
|---|---|
| `id` | Stable identity. See section 6. |
| `method` | One of GET, POST, PUT, PATCH, DELETE, HEAD, OPTIONS. Matches Apiro's `HttpMethod`. Mappings with no method restriction expand to one endpoint per supported method, or are skipped with a warning. Decide in review. |
| `path` | Framework path template with `{name}` placeholders, **without** `contextPath`. Regex segments such as `{id:\\d+}` are normalized to `{id}`, with the regex kept in `parameters[].schema.pattern`. |
| `group` | Controller short name. Becomes an Apiro folder. |
| `name` | Handler method name. Becomes the request name. |
| `summary`, `deprecated` | Optional. Summary comes from a docs annotation if present, otherwise empty. |
| `consumes`, `produces` | Media types declared on the mapping. Empty array when undeclared. |
| `parameters` | Path, query, header, cookie. Order follows the method signature. |
| `body` | Absent when the endpoint takes no body. |
| `security` | `required` plus scheme names. Detection details are decided per framework (see open questions). |
| `handler` | Class, method name, erased parameter types. Used for the debug bridge and for ID matching. |

### Parameter

| Field | Values |
|---|---|
| `in` | `path`, `query`, `header`, `cookie`, `form`, `part` |
| `required` | boolean. For `path` always true. |
| `defaultValue` | string, optional. Never a computed secret. |
| `schema` | inline schema (section 5) |
| `description` | optional |

`form` is a field in `application/x-www-form-urlencoded`. `part` is a multipart part, with `schema.type = "file"` for uploads.

---

## 5. Schema

A deliberately small subset of JSON Schema. Enough to generate a sample body and to show constraints. Not a validator.

| Field | Notes |
|---|---|
| `type` | `string`, `integer`, `number`, `boolean`, `array`, `object`, `file`, `any` |
| `format` | `int32`, `int64`, `float`, `double`, `date`, `date-time`, `uuid`, `email`, `byte`, free text otherwise |
| `ref` | Name in the top-level `schemas` map. When present, other fields are ignored. |
| `items` | Schema, for `array` |
| `properties` | Map of name to schema, for `object` |
| `required` | Array of property names, for `object` |
| `enumValues` | Array of allowed string values |
| `nullable` | boolean |
| `minimum`, `maximum` | numbers |
| `minLength`, `maxLength`, `pattern` | string constraints |
| `minItems`, `maxItems` | array constraints |
| `example` | optional; only values the generator can derive without guessing real data |

Mapping from Bean Validation, which the Spring SDK should read when present on the classpath (read by annotation name, so no compile-time dependency): `@NotNull`/`@NotBlank` feeds `required`, `@Size` feeds length and item limits, `@Min`/`@Max`/`@Positive` feed number limits, `@Pattern` feeds `pattern`, `@Email` feeds `format`.

Generics are resolved to concrete types where the signature allows it. Anything unresolvable becomes `{ "type": "any" }`.

---

## 6. Stable ID and re-import

```
id = METHOD + " " + path + "#" + className + "." + methodName + "(" + simpleParamTypes + ")"
```

Precise definition, so every producer builds the same string:
- `path` is the normalized path from section 4, without `contextPath`.
- `className` is the fully qualified name of the declaring class (nested classes use `$`, as `Class.getName()` does).
- `simpleParamTypes` lists **every** parameter of the handler method, in declaration order, separated by a comma with no spaces. Each entry is the erased type's simple name (`Class.getSimpleName()`: `long`, `String`, `CreateOrder`, `String[]`). Generics are erased, so `List<Order>` is `List`.
- `handler.parameterTypes` carries the same parameters in the same order, but with fully qualified erased names (`Class.getName()` style, `java.lang.String`). The ID is for humans and diffs. `handler` is for exact matching.
- A method with no parameters ends in `()`.

Re-import matching order:
1. exact `id`
2. same `method` + `path` (handler was renamed or moved)
3. same `handler` (path changed)

Anything unmatched is new. Anything in Apiro but absent from the new manifest is marked "removed from code", never deleted automatically. User edits to matched requests (headers, saved bodies, assertions) are kept. Only fields the manifest owns are refreshed, and the user is shown a diff first.

Apiro stores `manifestEndpointId` on imported `RequestItem`s. This needs a new optional field in `apiro-core`, and the project JSON loader must tolerate its absence.

---

## 7. Mapping into Apiro's model

| Manifest | Apiro |
|---|---|
| `group` | `Folder` (name = group) |
| endpoint | `RequestItem` (name = `name`, method = `method`) |
| URL | `${baseUrl}` + `contextPath` + path, with `{x}` rewritten to `${x}` |
| path parameter | variable `x` on the folder, empty value for the user to fill |
| query parameter | `KeyValue` in `query`, `enabled` = `required` |
| header parameter | `KeyValue` in `headers`, `enabled` = `required` |
| body, JSON | `RequestBody` type JSON, text generated from schema |
| body, form / multipart | `RequestBody` FORM / MULTIPART with parts |
| `security.schemes` | folder `AuthConfig` suggestion (bearer, basic, api key); values stay empty |

Apiro writes `${...}` references (see `VariableResolver.REFERENCE`), which is why path templates are rewritten on import. The importer never writes real values, only empty placeholders.

---

## 8. Transport

Same JSON, two ways out:

1. **Live:** `GET /apiro/manifest` on the running app. Disabled unless `apiro.enabled=true`. Intended for localhost development. Requires a shared token header `X-Apiro-Token` once the real detector lands.
2. **File:** written on startup to `apiro.export-file`, or later by a build plugin. Importable offline.

Security notes, binding for the implementation:
- Off by default. Opt in with a property.
- The manifest exposes class names and the full route surface. Treat it as sensitive.
- The Spring SDK should log a clear warning when enabled with a non-dev profile active.

---

## 9. Versioning policy

- `manifestVersion` is an integer major version.
- Additive, optional changes do **not** bump it.
- Removing a field, changing a meaning, or tightening a type bumps it.
- Consumers reject a manifest with a higher major version than they know and say so plainly. They accept lower majors as long as they still ship a reader.

---

## 10. Contract tests (how the two JSON implementations stay aligned)

The webmvc module writes JSON by hand (no Jackson, no Gson). `apiro-core` reads JSON with Gson. Two implementations of one format will drift, so pin them with shared golden files.

1. Put sample manifests in `apiro-spec/src/main/resources/examples/*.json` (they double as documentation).
2. In `apiro-spring-webmvc`, a test builds a manifest from real test controllers and asserts the written JSON equals the golden file, ignoring `generatedAt`.
3. In `apiro-core`, a test parses the same golden file with Gson into the spec classes and asserts selected fields.
4. Neither module depends on the other. Both depend on `apiro-spec`, which owns the goldens.

Add a second golden file for each real framework quirk found later.

---

## 11. Decisions and open questions

**Decided**
- Methodless mappings: skipped with a warning. (Question 1 below.)
- Responses: left out of v1, added in v1.1 as an additive change. (Question 2 below.)

**Proceeding with the recommendations unless changed** (questions 3 to 5): security reported only when an annotation proves it, otherwise omitted; recursion capped at 5 levels and 500 schemas; `contextPath` overridable by property.

1. **Methodless mappings.** `@RequestMapping("/x")` with no method. Expand to all seven methods, only GET, or skip with a warning? Recommendation: skip with a warning. Apiro users rarely want seven requests per route.
2. **Response side.** Leave responses out of v1? Recommendation: yes, add `responses[]` (status and schema) in v1.1. It is additive, so no bump.
3. **Security detection.** Spring Security rules are code, not annotations. For v1: only report `security.required` when `@PreAuthorize`, `@Secured` or `@RolesAllowed` is present, otherwise omit the object (unknown, not "false").
4. **Request-body schema depth limit.** Cap recursion at 5 levels and total schema count at 500 per manifest, to avoid huge output on JPA entity graphs.
5. **Where `contextPath` comes from** in plain Spring MVC, where `ServletContext` is not always available at the point the manifest is built. May need a configuration property override.

---

## 12. Implementation order

1. `apiro-spec`: POJOs for sections 3 to 5, validator, golden example files, Java 8 only. No JSON code. **(done: step 1)**
2. `apiro-spring-webmvc`: `ManifestBuilder` reading `RequestMappingHandlerMapping`, and a small hand-written JSON writer. Test against controllers on every version in the CI matrix.
3. `apiro-spring-webmvc`: serve it through `ApiroManifestController`, replacing the stub that returns an empty list.
4. `apiro-core`: `ManifestReader` (Gson) and `ManifestImporter` into `Folder`/`RequestItem`, including the new `manifestEndpointId`.
5. `apiro-core`: re-import diff.
6. `apiro-desktop`: import dialog, diff view.
