---
name: clojure-fullstack-development
description: Use this skill when developing, modifying, reviewing, debugging, or scaffolding full-stack Clojure systems. The default stack is Kit framework on the backend, Ant Design plus Reagent on the web frontend, shared Clojure/ClojureScript contracts, and ClojureDart plus Flutter for mobile. Use it for AGENTS.md driven project work, Integrant configuration, APIs, database migrations, Reagent UI, antd pages, ClojureDart mobile screens, cross-stack feature delivery, testing, security, and business-domain workflows.
---

# Clojure Full-Stack Development Skill

## Purpose

Act as a careful Clojure full-stack pair programmer for real-world business systems.

The default architecture is:

- Backend: Kit framework, Integrant, Aero, Reitit or the project's existing router, Ring, SQL, Migratus, HugSQL or the project's existing query layer.
- Web frontend: ClojureScript, Reagent, Ant Design, and the existing antd wrapper style used by the project.
- Shared contracts: `.cljc` namespaces for schemas, enums, permissions, validation helpers, and DTO transformations where useful.
- Mobile: ClojureDart and Flutter, with `.cljd` business UI in `src/` and Dart or Flutter bridge code in `lib/` only where needed.
- Development style: inspect first, respect `AGENTS.md`, keep modules small, use REPL-driven workflow, generate complete code when asked.

This skill is optimized for clinic, scheduling, medical record, operations, admin-dashboard, and other business systems.

## Prime Directive

Before proposing architecture or code, inspect the actual project files when available. Do not invent namespaces, routes, aliases, database libraries, antd wrapper APIs, or ClojureDart setup.

When project files are available, always check relevant `AGENTS.md` files first. Treat them as local law, not decoration.

## AGENTS.md Driven Workflow

Business-oriented projects may contain multiple `AGENTS.md` files. Use this precedence model:

1. Explicit user request in the current conversation.
2. Safety, privacy, and data protection constraints.
3. The nearest `AGENTS.md` to the files being changed.
4. Parent directory `AGENTS.md` files, walking up to the repository root.
5. This skill's default rules.

Discovery command:

```sh
find . -name AGENTS.md -print
```

Read order:

1. Root `AGENTS.md` for product, style, commands, and global constraints.
2. `backend/AGENTS.md`, `src/**/AGENTS.md`, or `resources/**/AGENTS.md` for Kit, SQL, routes, and migrations.
3. `frontend/AGENTS.md`, `cljs/**/AGENTS.md`, or `web/**/AGENTS.md` for Reagent and antd.
4. `mobile/AGENTS.md`, `cljd/**/AGENTS.md`, `lib/**/AGENTS.md`, or `src/**/AGENTS.md` for ClojureDart and Flutter.
5. `shared/AGENTS.md` or `common/**/AGENTS.md` for schemas, Transit, DTOs, and naming.
6. Test-specific `AGENTS.md` before editing tests.

When responding, summarize the relevant local rules briefly before implementation if they materially affect the answer.

If no `AGENTS.md` files are present, say that none were found and proceed with this skill's defaults.

## Project Lessons To Preserve

Use these as defaults when they do not conflict with actual project files:

- Keep product rules close to the code with `AGENTS.md`, especially backend, frontend, mobile, and shared-contract boundaries.
- Prefer explicit data flow over magic. A request should travel through route, controller, service/domain, repository/query, response mapping.
- Keep patient, appointment, clinical, billing, user, permission, and audit concepts separated unless the existing domain model says otherwise.
- Treat patient and staff data as sensitive. Avoid logging raw identifiers, names, notes, tokens, phone numbers, addresses, or medical content.
- Use RBAC or policy checks close to route/controller boundaries and domain actions.
- Use immutable audit events for sensitive record changes. Do not silently mutate clinical history unless the project explicitly models corrections that way.
- Keep UI state predictable. Web pages use Reagent atoms or the existing event model. Mobile screens use ClojureDart state idioms and any existing re-dash style rules.
- Keep render functions pure. Do not perform network requests, DB writes, logging side effects, subscriptions, or navigation inside render bodies unless the framework pattern explicitly allows it.
- Normalize keys at boundaries. Do not mix string keys, keyword keys, camelCase, snake_case, and Transit keys inside the same layer without a clear adapter.
- Use small, named adapters for API boundary conversion instead of scattering `js->clj`, `clj->js`, `keywordize-keys`, or manual key translation everywhere.

## First Response Checklist

For non-trivial tasks, quickly determine:

1. Is this a new project or an existing project?
2. Which `AGENTS.md` files apply?
3. Which stack area is touched: backend, web, shared, mobile, database, deployment, or tests?
4. Which modules and dependencies are already installed?
5. Which files must change?
6. Does the change require Integrant configuration?
7. Does the change require an API contract or shared schema?
8. Does the change require a migration?
9. Does the change require a web route, backend route, controller, page, component, modal, form, table, or mobile screen?
10. Does the change affect permissions, audit logs, or sensitive data?
11. What commands should verify the change?

If the user asks for code changes, provide complete code for each file you touch unless they ask for a patch only.

## Project Detection

Look for these files and infer the actual shape:

```text
AGENTS.md
deps.edn
build.clj
Dockerfile
resources/system.edn
resources/config.edn
resources/migrations/
resources/sql/
src/**
test/**
env/dev/src/user.clj
env/test/
env/prod/
shadow-cljs.edn
package.json
src/**.cljs
src/**.cljc
resources/public/
mobile/deps.edn
mobile/pubspec.yaml
mobile/lib/main.dart
mobile/src/**.cljd
pubspec.yaml
lib/main.dart
src/**.cljd
```

Adapt to reality. Some Kit projects keep Clojure and ClojureScript in one `deps.edn`; others split backend, frontend, and mobile into subprojects.

## Recommended Repository Shape

Do not force this shape onto existing code. Use it as a clean target for new projects:

```text
AGENTS.md
deps.edn
build.clj
resources/
  system.edn
  migrations/
  sql/
  public/
src/app/
  core.clj
  config.clj
  domain/
  infra/
  web/
    routes.clj
    controllers/
    middleware/
  shared/
    schema.cljc
    permissions.cljc
    api.cljc
frontend/
  AGENTS.md
  src/app/frontend/
    app.cljs
    api.cljs
    routes.cljs
    pages/
    components/
    antd.cljs
mobile/
  AGENTS.md
  deps.edn
  pubspec.yaml
  lib/main.dart
  src/app/mobile/
    main.cljd
    screens/
    components/
    state/
test/
```

For Kit generated projects, prefer the generated layout and add namespaces inside that structure rather than moving everything.

## Backend Rules: Kit Framework

Kit favors incremental complexity:

1. Start small.
2. Add capabilities through Kit modules only when needed.
3. Keep lifecycle wiring declarative in `resources/system.edn`.
4. Implement long-lived components through Integrant methods.
5. Prefer REPL-driven development with `go`, `halt`, and `reset`.
6. Keep deployment boring with `tools.build`, Docker, and uberjar packaging.

### Kit Project Creation

For a new backend or full-stack project, use the current project-pinned Kit template if available. If no version is known, show the command as a pattern and tell the developer to pin the template version used by the team.

```sh
clj -Ttools install io.github.kit-clj/kit-template '{:git/tag "<kit-version>"}' :as kit
clj -T:new :template kit :name app
cd app
clj -M:dev
```

In the dev REPL, use the helper functions defined by the project. Common helpers are:

```clojure
(go)
(reset)
(halt)
```

### Kit Module Workflow

Inspect before installing. Kit modules may automatically modify project files.

```clojure
(kit/sync-modules)
(kit/list-modules)
```

Common modules:

| Module | Use |
|---|---|
| `:kit/sql` | Database, migrations, SQL query files, commonly next.jdbc, conman, migratus, HugSQL. |
| `:kit/html` | Server rendered HTML through Selmer. Use only if the project needs SSR or server templates. |
| `:kit/cljs` | ClojureScript frontend through shadow-cljs. |
| `:kit/auth` | Auth middleware, commonly Buddy-based. |
| `:kit/metrics` | Prometheus style metrics. |
| `:kit/nrepl` | nREPL support. |
| `:kit/sente` | WebSocket or push features. |
| `:kit/htmx` | HTMX flows, if the project uses server-driven UI. |

Before installing a module:

```sh
git status
git add .
git commit -m "Before installing Kit module"
```

Then install from the REPL if appropriate:

```clojure
(kit/install-module :kit/sql)
```

### Integrant Rules

Use Integrant for long-lived resources:

- Database pools.
- HTTP clients.
- Auth verifiers.
- Schedulers.
- Caches.
- Message queues.
- Metrics registries.
- Route trees that require dependencies.

Declare in `resources/system.edn`:

```clojure
:app.appointment/service
{:db #ig/ref :db.sql/connection
 :audit #ig/ref :app.audit/service}
```

Initialize in Clojure:

```clojure
(ns app.appointment.service
  (:require
    [integrant.core :as ig]))

(defmethod ig/init-key :app.appointment/service
  [_ opts]
  opts)

(defmethod ig/halt-key! :app.appointment/service
  [_ _service]
  nil)
```

Do not create global connections or clients in handlers:

```clojure
;; Avoid this inside controllers.
(def conn (jdbc/get-datasource db-spec))
```

### Aero Configuration

Preserve existing Aero profile forms. Do not hardcode production secrets.

```clojure
:app.external/insurance-client
{:base-url #profile {:dev "http://localhost:9010"
                     :test "http://localhost:9011"
                     :prod #env INSURANCE_BASE_URL}
 :api-key #env INSURANCE_API_KEY}
```

### Backend Layering

Prefer this flow:

```text
reitit route -> controller -> service/domain -> repository/query -> database or external client
                                     -> audit/policy/checks as explicit dependencies
```

Guidelines:

- Controllers parse request data, call services, and return Ring responses.
- Services enforce business rules, permissions, workflows, transactions, and audit events.
- Repositories or query namespaces perform persistence.
- Keep SQL out of controllers.
- Keep HTTP response maps out of domain functions.
- Keep domain functions testable with plain data.

### Backend Route Pattern

Adapt to the project's router style. A common pattern:

```clojure
(ns app.web.routes.appointments
  (:require
    [app.web.controllers.appointments :as appointments]))

(defn routes [{:keys [appointment-service]}]
  ["/api/appointments"
   [""
    {:get {:handler (partial appointments/list-appointments {:appointment-service appointment-service})}
     :post {:handler (partial appointments/create-appointment! {:appointment-service appointment-service})}}]
   ["/:id"
    {:get {:handler (partial appointments/get-appointment {:appointment-service appointment-service})}}]])
```

### Backend Controller Pattern

```clojure
(ns app.web.controllers.appointments
  (:require
    [ring.util.response :as response]))

(defn list-appointments
  [{:keys [appointment-service]} request]
  (let [params (:query-params request)
        result ((:list appointment-service) params)]
    (response/response result)))

(defn create-appointment!
  [{:keys [appointment-service]} request]
  (let [payload (:body-params request)
        result ((:create! appointment-service) payload)]
    (-> (response/response result)
        (response/status 201))))
```

If the project uses explicit functions instead of service maps, follow that convention.

### Database Rules

For schema changes, provide reversible migrations where possible:

```sql
-- resources/migrations/20260504100000-create-appointments.up.sql
CREATE TABLE appointments (
  id UUID PRIMARY KEY,
  patient_id UUID NOT NULL,
  doctor_id UUID NOT NULL,
  starts_at TIMESTAMP NOT NULL,
  ends_at TIMESTAMP NOT NULL,
  status VARCHAR(32) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX appointments_patient_id_idx ON appointments (patient_id);
CREATE INDEX appointments_doctor_id_starts_at_idx ON appointments (doctor_id, starts_at);
```

```sql
-- resources/migrations/20260504100000-create-appointments.down.sql
DROP TABLE IF EXISTS appointments;
```

For HugSQL:

```sql
-- :name create-appointment! :! :n
INSERT INTO appointments (id, patient_id, doctor_id, starts_at, ends_at, status)
VALUES (:id, :patient_id, :doctor_id, :starts_at, :ends_at, :status);

-- :name list-appointments :? :*
SELECT id, patient_id, doctor_id, starts_at, ends_at, status, created_at, updated_at
FROM appointments
WHERE (:patient_id IS NULL OR patient_id = :patient_id)
ORDER BY starts_at DESC
LIMIT :limit OFFSET :offset;
```

Do not assume PostgreSQL, SQLite, or MySQL syntax. Inspect the configured database.

### Transactions

Use the project's existing transaction helper. If none exists and `next.jdbc` is used, prefer `next.jdbc/with-transaction` in service code, not controller code.

### API Error Shape

Follow the project. If no shape exists, use a simple consistent map:

```clojure
{:error/code :appointment/not-found
 :error/message "Appointment not found."
 :error/details {:id id}}
```

Do not leak stack traces, SQL, tokens, or patient details into client responses.

## Web Frontend Rules: Ant Design Plus Reagent

The frontend default is Reagent with Ant Design. Inspect the actual integration before writing UI code.

Look for:

```text
shadow-cljs.edn
package.json
src/**/app.cljs
src/**/antd.cljs
src/**/components/**
src/**/pages/**
src/**/routes.cljs
src/**/api.cljs
```

### Ant Design Interop Rule

Projects use different antd bindings. Do not invent a wrapper API.

Acceptable patterns:

1. Existing `reagent-antd` wrappers.
2. Project-local wrappers that adapt React components.
3. Direct npm imports from `antd` with `reagent.core/adapt-react-class`.
4. `[:> Component props children]` interop.

If no wrapper exists, create a small local adapter namespace:

```clojure
(ns app.frontend.antd
  (:require
    [reagent.core :as r]
    ["antd" :refer [Button Card DatePicker Drawer Form Input Modal Select Space Table Tag message]]))

(def button (r/adapt-react-class Button))
(def card (r/adapt-react-class Card))
(def date-picker (r/adapt-react-class DatePicker))
(def drawer (r/adapt-react-class Drawer))
(def form (r/adapt-react-class Form))
(def form-item (r/adapt-react-class (.-Item Form)))
(def input (r/adapt-react-class Input))
(def modal (r/adapt-react-class Modal))
(def select (r/adapt-react-class Select))
(def space (r/adapt-react-class Space))
(def table (r/adapt-react-class Table))
(def tag (r/adapt-react-class Tag))

(defn success! [text]
  (.success message text))

(defn error! [text]
  (.error message text))
```

If the build uses advanced compilation, check whether direct named imports and property access are already handled safely in the project.

### Reagent State Rules

- Keep API-loaded page state in one local atom or the existing event-store pattern.
- Keep antd form-local state inside Form when possible.
- Use explicit loading, error, and data states.
- Do not call APIs directly from render without guard logic.
- Do not mutate nested state with ad-hoc JS objects. Convert at the boundary.
- If the project uses re-frame, kee-frame, re-dash, or a custom event system, follow it instead of introducing a competing state layer.

### Web API Client Rule

Centralize HTTP calls:

```clojure
(ns app.frontend.api
  (:require
    [ajax.core :as ajax]
    [clojure.string :as str]))

(defn request!
  [{:keys [method uri params on-success on-error]}]
  (ajax/ajax-request
    {:method method
     :uri uri
     :params params
     :format (ajax/json-request-format)
     :response-format (ajax/json-response-format {:keywords? true})
     :handler (fn [[ok result]]
                (if ok
                  (on-success result)
                  (on-error result)))}))

(defn list-appointments! [params callbacks]
  (request! (merge {:method :get
                    :uri "/api/appointments"
                    :params params}
                   callbacks)))
```

Use the project's existing HTTP library if present.

### Antd Page Pattern

A typical admin page contains:

- Search form.
- Table with server pagination.
- Create or edit drawer/modal.
- Permission-aware actions.
- Loading and error display.

```clojure
(ns app.frontend.pages.appointments
  (:require
    [app.frontend.antd :as antd]
    [app.frontend.api :as api]
    [reagent.core :as r]))

(defn- columns []
  #js [#js {:title "Patient"
            :dataIndex "patientName"
            :key "patientName"}
       #js {:title "Doctor"
            :dataIndex "doctorName"
            :key "doctorName"}
       #js {:title "Time"
            :dataIndex "startsAt"
            :key "startsAt"}
       #js {:title "Status"
            :dataIndex "status"
            :key "status"}])

(defn page []
  (let [state (r/atom {:loading? false
                       :rows []
                       :pagination {:current 1 :page-size 20 :total 0}
                       :error nil})]
    (fn []
      [antd/card {:title "Appointments"}
       [antd/table {:rowKey "id"
                    :loading (:loading? @state)
                    :columns (columns)
                    :dataSource (clj->js (:rows @state))
                    :pagination (clj->js (:pagination @state))}]])))
```

For production code, add a controlled fetch path and adapt field names to the actual API.

### Frontend Naming

Prefer consistent names:

- `pages.*` for route-level screens.
- `components.*` for reusable UI.
- `api.*` for HTTP client calls.
- `state.*` or project event namespaces for state management.
- `shared.*` or `.cljc` contracts for schemas and constants.

### Date, Time, And Locale

Healthcare workflows are date-sensitive. Always inspect project conventions for timezone handling.

- Keep backend instants unambiguous.
- Display local clinic time intentionally.
- Avoid parsing date strings in multiple places.
- Centralize formatting helpers.
- Validate appointment intervals on backend even if the frontend checks first.

## Shared Contract Rules

Use `.cljc` for definitions shared by backend and ClojureScript where practical:

```clojure
(ns app.shared.appointment
  (:require
    [malli.core :as m]))

(def appointment-statuses
  #{:scheduled :checked-in :completed :cancelled :no-show})

(def AppointmentCreate
  [:map
   [:patient-id :uuid]
   [:doctor-id :uuid]
   [:starts-at inst?]
   [:ends-at inst?]
   [:status [:enum :scheduled]]])

(defn valid-create? [x]
  (m/validate AppointmentCreate x))
```

Guidelines:

- Put domain enums in one place.
- Use schemas for request and response maps when the project already has Malli, Spec, or another schema library.
- Do not introduce Malli if the project already uses Spec consistently, unless requested.
- Keep DB row maps, domain maps, and API DTOs distinct when their shapes differ.
- Use adapters with names like `row->appointment`, `appointment->response`, and `request->command`.

### Key Naming

Follow the existing project. If no convention exists:

- Clojure layers: kebab-case keywords.
- SQL columns: snake_case.
- JSON API: either kebab-case strings or camelCase, but choose one and centralize conversion.
- Transit internal APIs: EDN keywords are acceptable, but document the wire shape.
- ClojureDart mobile: normalize API data before putting it into screen state.

Never let every component invent key translations independently. That creates a swamp with a login screen.

## Mobile Rules: ClojureDart Plus Flutter

When a Mobile端 is present or requested, use ClojureDart.

Inspect:

```text
mobile/deps.edn
mobile/pubspec.yaml
mobile/lib/main.dart
mobile/src/**.cljd
pubspec.yaml
lib/main.dart
src/**.cljd
```

Business-oriented mobile defaults:

- `lib/` is the Flutter/Dart bridge layer.
- `src/` contains `.cljd` business UI and logic.
- Keep Dart bridge code small and boring.
- Prefer ClojureDart for screens, domain state, view composition, API adapters, and tests.
- Use Flutter platform code only for platform APIs or plugins that need native setup.

### ClojureDart Commands

```sh
clj -M:cljd init
clj -M:cljd flutter
clj -M:cljd compile
clj -M:cljd clean
clj -M:cljd upgrade
clj -M:cljd test
```

Run on a specific device:

```sh
flutter devices
clj -M:cljd flutter -d <device-id>
```

### ClojureDart Interop Rules

Dart package imports use strings:

```clojure
(ns app.mobile.main
  (:require
    ["package:flutter/material.dart" :as m]
    [cljd.flutter :as f]))
```

Constructors are called with the type in function position:

```clojure
(m/Text "Hello")
(m/Scaffold .body (m/Text "Body"))
```

Named arguments use dotted names:

```clojure
(m/Text "Hello"
  .maxLines 2
  .softWrap true
  .overflow m/TextOverflow.fade)
```

Static values:

```clojure
m/Colors.blue
m/TextAlign.left
```

Instance property access:

```clojure
(.-height size)
(.-value! controller "new value")
(set! (.-value controller) "new value")
```

Object field destructuring:

```clojure
(let [{:flds [height width]} size]
  ...)
```

Nullability hints:

```clojure
^String
^String?
```

Generics:

```clojure
^#/(List Map) x
```

### cljd.flutter Widget Rules

Prefer `cljd.flutter` for Flutter UI:

```clojure
(f/widget
  m/MaterialApp
  .home
  m/Scaffold
  .body
  m/Center
  (m/Text "App"))
```

Use:

- `f/run` for entry.
- `f/widget` for widget composition.
- `:watch` for reactive values.
- `:managed` for controllers, focus nodes, animation controllers, streams, and disposable resources.
- `:key` for changing sibling widgets.
- `:let` for local bindings.

Example:

```clojure
(defn counter-screen []
  (let [n (atom 0)]
    (f/widget
      :watch [value n]
      m/Scaffold
      .appBar (m/AppBar .title (m/Text "Counter"))
      .body
      m/Center
      (m/Text (str value))
      .floatingActionButton
      (m/FloatingActionButton
        .onPressed #(swap! n inc)
        .child (m/Icon m/Icons.add)))))
```

### Mobile State Rules

If the project uses re-dash or an event/effect model:

- Put event handlers in handler namespaces.
- Put subscriptions or read models in state namespaces.
- Keep render functions side-effect free.
- Trigger API calls through events or effects.
- Normalize API payloads before storing.
- Keep TextField local state near the widget unless it is needed globally.
- Dispose controllers, focus nodes, streams, and animation controllers.

### Mobile API Rule

Use the same backend contract as the web frontend. Keep mobile-specific DTO adapters small:

```text
backend response -> mobile api client -> adapter -> normalized mobile state -> screen widgets
```

Do not let widgets parse raw server maps repeatedly.

## Cross-Stack Feature Algorithm

When adding a feature across backend, web, and mobile:

1. Read applicable `AGENTS.md` files.
2. Inspect existing implementation patterns for a similar feature.
3. Define the domain command and read model.
4. Add or update shared schemas and enums if the project uses them.
5. Add database migration if persistence changes.
6. Add SQL or repository functions.
7. Add service/domain logic with permission and audit hooks.
8. Add backend route and controller.
9. Add API response mapping and error handling.
10. Add web API client call.
11. Add Reagent antd page, form, table, modal, or drawer.
12. Add mobile ClojureDart screen if Mobile端 is in scope.
13. Add tests at the lowest useful layer first, then integration tests.
14. Run formatting, linting, backend tests, frontend build/tests, and mobile tests as applicable.
15. Summarize changed files and verification commands.

## Security, Privacy, And Compliance Defaults

For regulated or sensitive data, apply these engineering defaults:

- Never log raw patient names, identifiers, medical notes, addresses, phone numbers, tokens, or credentials.
- Do not put sensitive data in frontend localStorage unless the project explicitly approves and protects it.
- Enforce authorization on the backend. Frontend hiding is convenience, not security.
- Prefer deny-by-default permissions.
- Add audit logs for sensitive create, update, delete, export, and view actions when the domain requires it.
- Avoid returning more patient data than the screen needs.
- Use parameterized SQL or query libraries. Never concatenate untrusted SQL fragments.
- Validate input on backend even if frontend validates.
- Use stable error codes and safe messages.
- Treat exports, printing, bulk actions, and search endpoints as high-risk.

## Testing Rules

Follow the existing test stack. If no test stack is visible, recommend the smallest conventional path.

Backend:

```sh
clj -M:test
```

Check whether the project uses Kaocha:

```sh
clj -M:test:kaocha
```

ClojureScript frontend:

```sh
npx shadow-cljs compile app
npx shadow-cljs test browser-test
```

Use the actual build IDs from `shadow-cljs.edn`.

ClojureDart mobile:

```sh
clj -M:cljd test
clj -M:cljd test -- -t widget
```

Test strategy:

- Unit test pure domain functions.
- Integration test DB queries with test profile and migrations.
- Controller tests should verify status, body shape, auth behavior, and error cases.
- Frontend tests should cover data adapters and important UI behavior, not every antd pixel.
- Mobile tests should separate pure logic from widget tests.
- Add regression tests for permission bugs and sensitive-data leaks.

## Build And Run Commands

Backend dev:

```sh
clj -M:dev
```

Backend REPL:

```clojure
(go)
(reset)
(halt)
```

Backend build:

```sh
clj -T:build uber
java -jar target/<app>.jar
```

Frontend dev, adapt to actual project:

```sh
npm install
npx shadow-cljs watch app
```

Frontend release, adapt to actual build ID:

```sh
npx shadow-cljs release app
```

Mobile dev:

```sh
clj -M:cljd init
clj -M:cljd flutter
```

Mobile clean:

```sh
clj -M:cljd clean
```

Lint and format, if available:

```sh
clj-kondo --lint src test
cljfmt check
cljfmt fix
```

## Code Generation Rules

When generating code:

- Use complete namespaces with `ns` declarations.
- Match existing namespace roots.
- Show full file contents for changed files unless asked for a diff.
- Preserve require ordering style if visible.
- Do not add dependencies casually. Check whether existing Kit modules or project libraries already provide the capability.
- Do not create global mutable runtime components.
- Keep long-lived resources in Integrant.
- Validate inputs before persistence.
- Keep handlers small.
- Use explicit permissions for sensitive actions.
- Keep side effects out of render functions.
- Prefer adapters at boundaries instead of leaking raw external data across layers.
- Do not bypass CSRF for server-rendered forms.
- Do not hide backend errors in frontend code. Surface safe messages and keep details in logs.

## Review Checklist

### Whole Project

- Did the answer respect applicable `AGENTS.md` files?
- Are files placed in the existing project structure?
- Are names consistent with the existing namespace and route conventions?
- Are commands accurate for the project aliases?
- Are sensitive data rules respected?

### Backend

- Is every long-lived dependency Integrant-managed?
- Are `#ig/ref` dependencies correct?
- Are `ig/init-key` and `ig/halt-key!` methods loaded?
- Are routes mounted by the actual route tree?
- Are controllers thin?
- Are domain rules in services or domain namespaces?
- Are migrations reversible where practical?
- Are SQL queries named consistently?
- Are DB-specific SQL details correct for the configured database?
- Are permissions and audit events handled?
- Does `(reset)` restart cleanly?

### Web Frontend

- Does the code use the project's actual antd wrapper style?
- Are React interop props converted correctly?
- Are `clj->js` and `js->clj` limited to boundaries?
- Are loading, error, and empty states handled?
- Are table row keys stable?
- Are forms validated on frontend and backend?
- Are API calls centralized?
- Are render functions side-effect free?

### Shared

- Are schemas or DTOs in `.cljc` only when they truly work on both JVM and CLJS?
- Are keys normalized consistently?
- Are enum values centralized?
- Are API response shapes documented or discoverable?

### Mobile

- Is mobile implemented in ClojureDart when requested?
- Are Dart imports string-based in `:require`?
- Are named arguments dotted?
- Are constructors called correctly?
- Are widgets using `cljd.flutter` idioms where appropriate?
- Are controllers and streams disposed or `:managed`?
- Is TextField state handled locally unless global state is needed?
- Are API payloads normalized before screen state?

## Debugging Guide

### Integrant key not found

Likely causes:

- Key exists in `system.edn`, but no `ig/init-key` method is loaded.
- Namespace containing the defmethod is not required during startup.
- Typo in a namespaced keyword.
- Profile removed the dependency unexpectedly.

Actions:

1. Check `resources/system.edn`.
2. Check the namespace defining `defmethod ig/init-key`.
3. Check startup requires.
4. Run `(reset)`.

### Backend route returns 404

Likely causes:

- Route namespace is not mounted.
- Method mismatch.
- Context path mismatch.
- Handler failed during system init.

Actions:

1. Inspect central route aggregation.
2. Confirm method and path.
3. Check system startup logs.
4. Print or inspect route data in dev if the project supports it.

### HugSQL query missing

Likely causes:

- SQL file not loaded.
- Query annotation mismatch.
- Wrong query namespace or dispatch key.
- Query function not injected.

Actions:

1. Inspect `resources/sql/*.sql`.
2. Confirm `-- :name` annotation.
3. Confirm Integrant query component.
4. Confirm controller or service call.

### Antd component does not render

Likely causes:

- Wrong wrapper API.
- Passing Clojure maps where JS objects are required.
- Incorrect child style for adapted React component.
- CSS not imported.
- Package version mismatch.

Actions:

1. Inspect existing antd usage.
2. Check `package.json` and imports.
3. Convert props with `clj->js` only at the component boundary.
4. Confirm `antd/dist/reset.css` or project CSS setup if needed.

### ClojureDart compile error

Classify first:

1. Clojure CLI or `deps.edn`.
2. ClojureDart syntax or compiler.
3. Dart package or `pubspec.yaml`.
4. Flutter platform or device.
5. Runtime widget lifecycle.
6. Test runner.

Common checks:

- Dart packages use string imports.
- Named arguments use `.arg value`.
- Constructors do not use `new` or Java trailing-dot style.
- `:main` namespace exists and defines `main`.
- Run `clj -M:cljd clean` for stale generated state.

## Response Format For Development Tasks

Prefer this structure:

```text
要改的文件:
1. ...
2. ...

实现思路:
...

完整代码:
...

运行方式:
...

检查点:
...
```

For reviews:

```text
结论:
...

主要问题:
...

建议修改:
...

完整修正代码:
...

验证命令:
...
```

For debugging:

```text
最可能的故障层:
...

证据:
...

最小修复:
...

完整代码或配置:
...

验证命令:
...
```

## When To Ask A Follow-Up

Ask a follow-up only when implementation would be unsafe or ambiguous in a way that cannot be reasonably resolved by inspecting files.

Prefer making a grounded best-effort answer when:

- The user asks for a skill or template.
- The exact code is not available, but the desired stack is clear.
- A partial artifact is more useful than waiting.

When code is missing, explicitly say which project files were not available and make the generated result adaptable.

## Local Reference Files

Read these for larger tasks:

- `references/agents-md-playbook.md`
- `references/backend-kit-patterns.md`
- `references/frontend-antd-reagent-patterns.md`
- `references/mobile-clojuredart-patterns.md`
- `references/shared-api-contracts.md`
- `references/security-testing-checklist.md`
- `examples/prompt-examples.md`
