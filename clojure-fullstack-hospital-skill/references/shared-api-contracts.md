# Shared API And Data Contract Patterns

## Goal

Keep backend, web, and mobile speaking the same language without leaking implementation details across layers.

## Recommended layers

```text
DB row map -> domain map -> API response DTO -> frontend/mobile normalized state -> UI props
```

Do not skip adapters when shapes differ.

## `.cljc` contracts

Use `.cljc` for definitions that truly work on JVM and CLJS:

```clojure
(ns hospital.shared.patient)

(def genders #{:male :female :other :unknown})

(def patient-statuses #{:active :inactive :merged :deceased})
```

If Malli or Spec exists, put shared schemas there. Do not introduce a new schema library without checking the project.

## Naming conventions

Follow existing project conventions. If none exist:

- Clojure: kebab-case keywords.
- SQL: snake_case columns.
- JSON: one convention, documented and enforced at API boundaries.
- UI state: normalized Clojure maps.
- Transit: keyword keys are acceptable for internal clients, but document it.

## Error shape

A safe default:

```clojure
{:error/code :patient/not-found
 :error/message "Patient not found."
 :error/details {:id id}}
```

Do not include SQL, stack traces, tokens, raw request payloads, or sensitive patient fields.

## Pagination shape

A safe default:

```clojure
{:items [...]
 :page {:limit 20
        :offset 0
        :total 124}}
```

For cursor pagination:

```clojure
{:items [...]
 :page {:next-cursor "..."
        :has-more? true}}
```

Use whichever shape the project already has.

## Adapter naming

Good names:

```clojure
row->patient
patient->response
request->create-command
api->appointment
appointment->view-model
```

Avoid anonymous conversion soup spread through controllers and components.
