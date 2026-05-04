# Mobile ClojureDart Patterns

## Inspect first

Check:

```text
mobile/deps.edn
mobile/pubspec.yaml
mobile/lib/main.dart
mobile/src/**.cljd
deps.edn
pubspec.yaml
lib/main.dart
src/**.cljd
```

## Structure

Hospital-style mobile projects usually keep:

```text
lib/             # Dart or Flutter bridge, small and stable
src/             # ClojureDart .cljd business UI and logic
```

Prefer ClojureDart in `src/` for screens and UI behavior. Use Dart bridge code only where plugin or platform integration needs it.

## Commands

```sh
clj -M:cljd init
clj -M:cljd flutter
clj -M:cljd compile
clj -M:cljd clean
clj -M:cljd upgrade
clj -M:cljd test
```

Device:

```sh
flutter devices
clj -M:cljd flutter -d <device-id>
```

## Imports

```clojure
(ns hospital.mobile.main
  (:require
    ["package:flutter/material.dart" :as m]
    [cljd.flutter :as f]))
```

## Constructors and named args

```clojure
(m/Text "Hello"
  .maxLines 2
  .softWrap true)

(m/Scaffold
  .appBar (m/AppBar .title (m/Text "Hospital"))
  .body (m/Center .child (m/Text "Ready")))
```

## cljd.flutter threading

```clojure
(f/widget
  m/MaterialApp
  .home
  m/Scaffold
  .body
  m/Center
  (m/Text "Hospital"))
```

## State

Use the existing project pattern. If re-dash or another event model is present:

- Define handlers in handler namespaces.
- Define subscriptions or read functions in state namespaces.
- Keep render side-effect free.
- Trigger API calls through effects.
- Normalize API payloads before storing.

For simple screen-local state, atoms are acceptable:

```clojure
(defn search-box []
  (let [text (atom "")]
    (f/widget
      :watch [value text]
      (m/TextField
        .onChanged #(reset! text %)
        .decoration (m/InputDecoration .labelText "Search")
        .controller nil))))
```

When using `TextEditingController`, manage lifecycle with the project's approved pattern, preferably `:managed`.

## Interop reminders

- Dart package imports are string requires.
- Named args are `.arg value`.
- Static fields use `m/Colors.blue` style.
- Instance reads use `.-field`.
- Instance writes use `.-field!` or `set!`.
- Nullable hints use `^Type?`.
- Generic hints use `^#/(List Map)`.

## Testing

```sh
clj -M:cljd test
clj -M:cljd test -- -t widget
```

Keep pure logic tests separate from widget tests.
