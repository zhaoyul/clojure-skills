# Backend Kit Patterns

## Inspect first

Check:

```text
deps.edn
resources/system.edn
resources/sql/
resources/migrations/
src/**/core.clj
src/**/web/routes*.clj
src/**/web/controllers/**
src/**/domain/**
src/**/infra/**
env/dev/src/user.clj
```

## Layering

```text
route -> controller -> service/domain -> repository/query -> database
                         -> policy
                         -> audit
```

## Integrant component

`resources/system.edn`:

```clojure
:hospital.patient/service
{:db #ig/ref :db.sql/connection
 :audit #ig/ref :hospital.audit/service}
```

Implementation:

```clojure
(ns hospital.patient.service
  (:require
    [integrant.core :as ig]))

(defn make-service [{:keys [db audit]}]
  {:get (fn [id] ...)
   :create! (fn [command] ...)})

(defmethod ig/init-key :hospital.patient/service
  [_ opts]
  (make-service opts))

(defmethod ig/halt-key! :hospital.patient/service
  [_ _service]
  nil)
```

## Controller

```clojure
(ns hospital.web.controllers.patients
  (:require
    [ring.util.response :as response]))

(defn get-patient
  [{:keys [patient-service]} request]
  (let [id (get-in request [:path-params :id])]
    (if-let [patient ((:get patient-service) id)]
      (response/response patient)
      (-> (response/response {:error/code :patient/not-found
                              :error/message "Patient not found."})
          (response/status 404)))))
```

## Migration rules

- Every schema change gets an up migration.
- Add a down migration where practical.
- Add indexes for frequent filters and foreign keys.
- Match the configured database dialect.
- Avoid destructive migrations without a careful migration plan.

## SQL rules

For fixed queries, prefer existing HugSQL conventions when present:

```sql
-- :name get-patient :? :1
SELECT id, name, birth_date, gender
FROM patients
WHERE id = :id;
```

For dynamic search with many optional filters, use the project's existing query builder if present. Do not introduce HoneySQL just because one query is dynamic.

## Transaction rule

Business transactions belong in service/domain code. Controllers should not open transactions unless the project explicitly uses that pattern.

## Realtime and cross-client updates

When a workflow spans multiple clients, broadcast after the database save succeeds. Typical events:

- A source user creates work for another user, such as quote request creation.
- A counterpart responds, such as driver quote submitted.
- A payment or approval unlocks the next party's action.
- An order/status transition changes what another client should display.

Keep broadcasts small and non-sensitive: ids, status, event name, and coarse metadata. Do not put raw addresses, phone numbers, identity documents, payment account data, or message bodies in realtime payloads unless the product explicitly requires it and the channel is authorized.

Add lightweight observability for development:

```clojure
(log/info "websocket-broadcast"
          {:topic topic
           :event event
           :subscribers subscriber-count})
```

A local stats endpoint or REPL helper that returns topic subscriber counts is useful for distinguishing these cases:

- The backend did not broadcast.
- The app did not subscribe.
- The app received the event but refreshed the wrong entity.
- The app refreshed correctly but the backend state transition failed.

## Sensitive logging rule

Safe:

```clojure
(log/info "appointment created" {:appointment-id id :actor-id actor-id})
```

Avoid:

```clojure
(log/info "appointment created" payload)
```

## Verification

Common commands:

```sh
clj -M:dev
clj -M:test
clj -T:build uber
```

REPL:

```clojure
(reset)
```
