---
name: clojure-struct-edit
description: Safe structural editing for Clojure/ClojureDart code. Use this skill when editing .clj, .cljs, .cljc, or .cljd files. Never use raw text patches - always use structural operations via clj-struct-edit.
---

# Clojure Structural Editing Skill

Use this skill whenever editing Clojure, ClojureScript, ClojureDart, or EDN code.

## When to Use

- Editing `.clj`, `.cljs`, `.cljc`, `.cljd`, or `.edn` files
- Refactoring, adding functions, modifying forms
- Any operation that would normally require counting parentheses

## When NOT to Use

- Creating new files from scratch (use write tool)
- Editing non-Clojure files (use regular edit/write tools)
- Full file replacements requested by user

## Hard Rules

1. **Never use raw text patches** for Clojure files. No `+`/`-` line diffs.
2. **Never target by line number alone**. Always use snapshot + range + sha256.
3. **Never insert unmatched delimiters**. The tool prevents this.
4. **One operation at a time**. Snapshot -> edit -> verify -> next edit.
5. **Always use `save: false` first** (dry-run), verify diff, then `save: true`.

## Workflow

```
1. snapshot    -> See file structure, get form ranges and hashes
2. get-form    -> (optional) Read exact form text before editing
3. edit op     -> replace-form / insert-form-after / remove-form / etc.
                  Always use save: false first!
4. Review result -> Check diff, verify ok: true
5. save        -> Re-run same op with save: true if satisfied
```

## Tool: clj-struct-edit

### Installation

The tool requires:
- Emacs with `emacsclient` (install via `brew install emacs` or `apt install emacs`)
- `clojure-mode` or `clojure-ts-mode` in Emacs (optional but recommended)

The tool is at `scripts/clj-struct-edit` in the project. If missing, the skill will guide setup.

### Usage

```bash
# Via stdin (preferred)
echo '{"op":"snapshot","file":"src/app.clj"}' | scripts/clj-struct-edit

# Via pipe from file
cat op.json | scripts/clj-struct-edit
```

### Operations

#### snapshot

Return top-level forms with ranges and SHA256 hashes.

```json
{
  "op": "snapshot",
  "file": "src/app.clj",
  "save": false
}
```

Returns:
```json
{
  "ok": true,
  "forms": [
    {
      "index": 1,
      "kind": "ns",
      "head": "ns",
      "range": [[1,1],[8,2]],
      "preview": "(ns my.app.core ...)",
      "sha256": "abc123..."
    },
    {
      "index": 2,
      "kind": "def",
      "head": "defn",
      "range": [[10,1],[25,2]],
      "preview": "(defn handler [request] ...)",
      "sha256": "def456..."
    }
  ],
  "file": "src/app.clj",
  "line_count": 123
}
```

#### get-form

Read exact text of a form at target range.

```json
{
  "op": "get-form",
  "file": "src/app.clj",
  "target": {
    "range": [[10,1],[25,2]]
  },
  "save": false
}
```

Returns:
```json
{
  "ok": true,
  "text": "(defn handler [request] ...)",
  "range": [[10,1],[25,2]],
  "sha256": "def456..."
}
```

#### replace-form

Replace a complete form. **Must use `save: false` first!**

```json
{
  "op": "replace-form",
  "file": "src/app.clj",
  "target": {
    "range": [[10,1],[25,2]],
    "sha256": "def456..."
  },
  "new_form": "(defn handler [request]\n  (assoc response :status 200))",
  "save": false
}
```

Returns:
```json
{
  "ok": true,
  "changed": true,
  "old_sha256": "def456...",
  "new_sha256": "ghi789...",
  "range": [[10,1],[11,30]],
  "dry_run": true
}
```

#### insert-form-after

Insert a form after the target form.

```json
{
  "op": "insert-form-after",
  "file": "src/app.clj",
  "target": {
    "range": [[10,1],[25,2]]
  },
  "new_form": "(defn- helper [] nil)",
  "save": false
}
```

#### insert-form-before

Insert a form before the target form.

```json
{
  "op": "insert-form-before",
  "file": "src/app.clj",
  "target": {
    "range": [[10,1],[25,2]]
  },
  "new_form": "(def ^:private config {})",
  "save": false
}
```

#### remove-form

Remove a complete form.

```json
{
  "op": "remove-form",
  "file": "src/app.clj",
  "target": {
    "range": [[10,1],[25,2]],
    "sha256": "def456..."
  },
  "save": false
}
```

#### validate

Run check-parens on file without modifying.

```json
{
  "op": "validate",
  "file": "src/app.clj",
  "save": false
}
```

Returns:
```json
{
  "ok": true,
  "check_parens": "ok",
  "file": "src/app.clj"
}
```

## Example Edit Session

```bash
# Step 1: Inspect file structure
echo '{"op":"snapshot","file":"src/app/core.clj","save":false}' | scripts/clj-struct-edit

# Step 2: Dry-run replace
echo '{
  "op": "replace-form",
  "file": "src/app/core.clj",
  "target": {"range":[[12,3],[12,30]],"sha256":"old-hash"},
  "new_form": "(assoc response :status 200)",
  "save": false
}' | scripts/clj-struct-edit

# Step 3: If ok, save
echo '{
  "op": "replace-form",
  "file": "src/app/core.clj",
  "target": {"range":[[12,3],[12,30]],"sha256":"old-hash"},
  "new_form": "(assoc response :status 200)",
  "save": true
}' | scripts/clj-struct-edit
```

## Safety Guarantees

- **Atomic changes**: All edits use `atomic-change-group`. Errors auto-rollback.
- **Paren check**: `check-parens` runs after every mutating operation.
- **Project boundary**: Files outside project root are rejected.
- **Hash verification**: Optional SHA256 prevents editing stale targets.
- **Dry-run default**: Always use `save: false` first to preview changes.

## Error Responses

```json
{
  "ok": false,
  "error": "check-parens failed",
  "rolled_back": true
}
```

```json
{
  "ok": false,
  "error": "Target hash mismatch",
  "rolled_back": true
}
```

```json
{
  "ok": false,
  "error": "No sexp found at target line 42, col 5",
  "rolled_back": true
}
```

## Phase 1 Limitations

- Target uses `range` (line/col) only. No stable `node_id` yet.
- No `add-require`, `wrap-form`, `splice-form` yet.
- No `clj-kondo` integration yet (only `check-parens`).
- ClojureDart `.method` interop syntax may confuse sexp navigation.

## Phase 2+ Roadmap

- Stable `node_id` via rewrite-clj
- `add-require`, `remove-require`
- `wrap-form`, `splice-form`, `raise-form`
- clj-kondo lint integration
- `clean-ns`
