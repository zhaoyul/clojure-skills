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
clj -M:cljd flutter -d chrome
clj -M:cljd flutter -d macos
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

Treat `clj -M:cljd flutter -d <device>` as the normal watch workflow. It keeps generated Dart and the Flutter runner alive, and hot-reloads `.cljd` edits. Use `chrome` for quick web checks and `macos` when validating desktop permissions, plugin behavior, file picker, camera, networking, or platform-specific entitlements.

When a hot reload touches top-level atoms, timers, WebSocket channels, or app initialization, do a full app restart. Hot reload can keep old sockets, timers, and controllers alive.

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

For long lists or workflow pages that refresh from API/watch state, preserve scroll position explicitly:

```clojure
(def orders-scroll-controller (m/ScrollController))

(m/ListView
  .key (m/PageStorageKey "driver-orders")
  .controller orders-scroll-controller
  .children [...])
```

Avoid changing list keys on each refresh. If an operation fails, do not trigger a full data refresh just to show an error.

## Networking and realtime

Use backend APIs as the source of truth. Do not hide backend errors with local demo fallback for mutating operations such as order status changes, payments, certification, or uploads. Demo fallback is acceptable for read-only catalog or mock screens, but state transitions must surface `4xx/5xx` errors so illegal domain transitions are visible.

For WebSocket features:

- Subscribe to stable topics such as `driver:<id>`, `shipper:<id>`, or `order:<id>`.
- Any event message can increment the existing refresh token and let watched API reads reload.
- Track a visible realtime state, for example `{:connected? true :message "实时已连接"}`.
- Add a watchdog timer. If not connected, clear the stored channel before reconnecting; failed async connects can leave a non-nil channel object.
- After backend restarts, verify with a server stats endpoint or logs that subscriptions return.

Sketch:

```clojure
(def realtime-channel (atom nil))
(def realtime-state (atom {:connected? false}))

(defn mark-closed! []
  (reset! realtime-channel nil)
  (reset! realtime-state {:connected? false}))

(defn start-watchdog! []
  (async/Timer.periodic
    (Duration. .seconds 3)
    (fn [_]
      (when-not (:connected? @realtime-state)
        (reset! realtime-channel nil))
      (connect-realtime!))))
```

When an event payload identifies an active entity, update local selection before refreshing. Example: a shipper app receiving `quote-submitted` with `order-id` should select that order before refetching quotes, otherwise it may refresh the previous order.

## Domain workflow UI

Model action buttons from the backend domain state machine. For order fulfillment, show only the legal next action (`accepted -> loading -> in-transit -> unloading -> completed`) and show waiting text for states such as `quote-requested`, `pending-payment`, or `pending-dispatch`.

Do not render every possible status button and rely on backend `409` responses as the normal UX.

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

For interactive mobile work, also run smoke flows through the backend:

1. Create the source-side record, such as a shipper quote request.
2. Verify the recipient app receives a realtime event and reloads the expected API.
3. Complete the counterpart action, such as driver quote or order status transition.
4. Verify both apps read the same backend state and that the broadcast topic had subscribers.
