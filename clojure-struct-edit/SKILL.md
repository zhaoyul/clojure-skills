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

The script is at `scripts/clj-struct-edit` — resolve relative to this skill's directory.

### Requirements

**Phase 2 (default):**
- [Babashka](https://babashka.org/) (`brew install babashka`)
- No Emacs needed

**Phase 1 (Emacs fallback, set `CLJ_STRUCT_EDIT_PHASE=1`):**
- Emacs with `emacsclient` (`brew install emacs`)
- `clojure-mode` or `clojure-ts-mode` (optional but recommended)

### Usage

```bash
# Via stdin (preferred)
echo '{"op":"snapshot","file":"src/app.clj"}' | scripts/clj-struct-edit

# Via pipe from file
cat op.json | scripts/clj-struct-edit
```

**Note:** `scripts/clj-struct-edit` is relative to the skill directory. In a pi agent session, this resolves automatically. For standalone use, prefix with the skill path or set an alias:
```bash
SKILL_DIR="$(dirname $(dirname $(readlink -f $0)))"
$SKILL_DIR/scripts/clj-struct-edit <<< '...'
```

## Operations Reference

### Phase 1 + Phase 2 (Core)

| op | Phase | Description | Emacs | BB |
|----|-------|-------------|:-----:|:--:|
| `snapshot` | 1 | Scan top-level forms, return range + sha256 | ✅ | ✅ |
| `get-form` | 1 | Read exact text of a form at target range | ✅ | ✅ |
| `replace-form` | 1 | Replace a complete form (with sha256 verify) | ✅ | ✅ |
| `insert-form-after` | 1 | Insert a form after the target form | ✅ | ✅ |
| `insert-form-before` | 1 | Insert a form before the target form | ✅ | ✅ |
| `remove-form` | 1 | Remove a complete form | ✅ | ✅ |
| `validate` | 1 | Run check-parens on file | ✅ | ✅ |

### Phase 2 Only (Babashka + rewrite-clj)

| op | Description |
|----|-------------|
| `wrap-form` | Wrap a form with a wrapper s-expression |
| `splice-form` | Splice/unwrap a form (remove outer wrapper) |
| `add-require` | Add a `:require` entry to the ns form |
| `rename-symbol` | LSP-based symbol rename across the project |
| `clean-ns` | LSP-based ns cleanup (remove unused requires) |
| `find-references` | LSP-based find all references to a symbol |

### Detail: Core Operations

#### snapshot
```json
{"op":"snapshot","file":"src/app.clj","save":false}
```
Returns list of top-level forms with index, kind, head, range [[l1,c1],[l2,c2]], preview, sha256.

#### get-form
```json
{"op":"get-form","file":"src/app.clj","target":{"range":[[10,1],[25,2]]},"save":false}
```
Returns the exact sexp text, range, and sha256.

#### replace-form
```json
{"op":"replace-form","file":"src/app.clj","target":{"range":[[10,1],[25,2]],"sha256":"def456..."},"new_form":"(defn handler [request]\n  (assoc response :status 200))","save":false}
```
**Must dry-run with `save: false` first!** Returns old_sha256, new_sha256, range.

#### insert-form-after / insert-form-before
```json
{"op":"insert-form-after","file":"src/app.clj","target":{"range":[[10,1],[25,2]]},"new_form":"(defn- helper [] nil)","save":false}
```
Inserts at same indentation level as target.

#### remove-form
```json
{"op":"remove-form","file":"src/app.clj","target":{"range":[[10,1],[25,2]],"sha256":"def456..."},"save":false}
```
Removes the form and trailing whitespace.

#### validate
```json
{"op":"validate","file":"src/app.clj","save":false}
```
Returns `{"ok":true,"check_parens":"ok"}` or error.

## Example Edit Session

```bash
# Step 1: Inspect file structure
scripts/clj-struct-edit <<< '{"op":"snapshot","file":"src/app/core.clj","save":false}'

# Step 2: Dry-run replace
scripts/clj-struct-edit <<< '{"op":"replace-form","file":"src/app/core.clj","target":{"range":[[12,3],[12,30]],"sha256":"old-hash"},"new_form":"(assoc response :status 200)","save":false}'

# Step 3: If ok, save
scripts/clj-struct-edit <<< '{"op":"replace-form","file":"src/app/core.clj","target":{"range":[[12,3],[12,30]],"sha256":"old-hash"},"new_form":"(assoc response :status 200)","save":true}'
```

## Phase 2 Only Operations

### wrap-form
Wrap a target form with a wrapper s-expression. `wrapper` is the outer form template with `%s` placeholder.

```json
{"op":"wrap-form","file":"src/core.clj","target":{"range":[[10,1],[10,15]],"sha256":"..."},"wrapper":"(when debug %s)","save":true}
```

### splice-form
Remove the outer wrapper, keeping the inner contents.

```json
{"op":"splice-form","file":"src/core.clj","target":{"range":[[10,1],[12,2]],"sha256":"..."},"save":true}
```

### add-require
Add a require entry to the ns form.

```json
{"op":"add-require","file":"src/core.clj","require":"[clojure.string :as str]","save":true}
```

### rename-symbol (LSP)
Requires clojure-lsp running. Renames symbol across the project.

```json
{"op":"rename-symbol","file":"src/core.clj","namespace":"my.app.core","symbol":"old-name","new_name":"new-name","save":true}
```

### clean-ns (LSP)
Remove unused requires via clojure-lsp.

```json
{"op":"clean-ns","file":"src/core.clj","save":true}
```

### find-references (LSP)
Find all references to a symbol.

```json
{"op":"find-references","file":"src/core.clj","namespace":"my.app.core","symbol":"my-fn","save":false}
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

## Limitations

- Target uses `range` (line/col) only. No stable `node_id` yet.
- Phase 2 (Babashka+rewrite-clj) is recommended. Phase 1 (Emacs) still available as fallback via `CLJ_STRUCT_EDIT_PHASE=1`.
- ClojureDart `.method` interop syntax may confuse sexp navigation.
- `rename-symbol`, `clean-ns`, `find-references` require clojure-lsp running.
