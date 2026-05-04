# Frontend Ant Design Plus Reagent Patterns

## Inspect first

Check:

```text
shadow-cljs.edn
package.json
src/**/app.cljs
src/**/routes.cljs
src/**/api.cljs
src/**/antd.cljs
src/**/components/**
src/**/pages/**
```

## Antd integration

Use the existing style. Common options:

- Project wrappers, such as `hospital.frontend.antd`.
- `reagent-antd` wrappers.
- Direct `reagent.core/adapt-react-class`.
- `[:> Component props children]`.

Do not mix styles inside a new feature unless the project already does.

## Local wrapper example

```clojure
(ns hospital.frontend.antd
  (:require
    [reagent.core :as r]
    ["antd" :refer [Button Card Form Input Modal Table message]]))

(def button (r/adapt-react-class Button))
(def card (r/adapt-react-class Card))
(def form (r/adapt-react-class Form))
(def form-item (r/adapt-react-class (.-Item Form)))
(def input (r/adapt-react-class Input))
(def modal (r/adapt-react-class Modal))
(def table (r/adapt-react-class Table))

(defn success! [text] (.success message text))
(defn error! [text] (.error message text))
```

## Page pattern

```clojure
(ns hospital.frontend.pages.patients
  (:require
    [hospital.frontend.antd :as antd]
    [hospital.frontend.api :as api]
    [reagent.core :as r]))

(defn page []
  (let [state (r/atom {:loading? false
                       :rows []
                       :error nil
                       :pagination {:current 1 :page-size 20 :total 0}})]
    (fn []
      [antd/card {:title "Patients"}
       [antd/table {:rowKey "id"
                    :loading (:loading? @state)
                    :dataSource (clj->js (:rows @state))
                    :pagination (clj->js (:pagination @state))}]])))
```

## API client pattern

Centralize HTTP calls. Use the project's library if it already has one.

```clojure
(ns hospital.frontend.api
  (:require
    [ajax.core :as ajax]))

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
```

## State rules

- Keep page state explicit: `:loading?`, `:error`, `:rows`, `:pagination`, `:filters`.
- Use antd Form for form-local validation when appropriate.
- Backend validation remains authoritative.
- Convert Clojure maps to JS only at React component boundaries.
- Convert JS values back to Clojure at event boundaries.
- Keep side effects out of render functions.

## Tables

- Always set stable `rowKey`.
- Use server-side pagination for large medical/admin datasets.
- Avoid loading more patient data than the page needs.
- Put table column definitions in helper functions when they need callbacks or permissions.

## Forms

- Show safe error messages.
- Disable submit while saving.
- Reset form after successful create only when that matches UX.
- Prefer Drawer for complex edit flows and Modal for small confirmations.

## Verification

Use actual build IDs from `shadow-cljs.edn`:

```sh
npm install
npx shadow-cljs watch app
npx shadow-cljs compile app
npx shadow-cljs release app
```
