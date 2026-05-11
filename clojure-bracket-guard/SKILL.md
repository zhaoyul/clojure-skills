---
name: clojure-bracket-guard
description: >
  Validates Clojure code for balanced parentheses/brackets/braces before
  writing to files. Use when writing or editing .clj, .cljs, .cljc, .cljd,
  or .edn files to prevent broken code from being saved. A feature-complete
  replacement for built-in edit tools with added bracket validation and
  optional clojure-lsp formatting.
---

# Clojure Bracket Guard

## Rule

When working with **any** Clojure-family file — **.clj, .cljs, .cljc, .cljd, .edn** — you MUST:

1. **Never use the built-in `edit` tool.** Use `safe-edit` instead.
2. **Never use `write` without validating first.** Use `validate` before writing.

## Setup

Requires [babashka](https://github.com/babashka/babashka) (`bb`) on PATH.

Optional: [clojure-lsp](https://clojure-lsp.io/) for `--format` flag.

The scripts are in the `scripts/` directory of this skill. Resolve paths relative to the skill directory.

## Writing — validate before `write`

```bash
bb scripts/validate.clj --code '<CONTENT>'
bb scripts/validate.clj --file <TARGET_FILE>
```

Only call `write` after validation passes.

## Editing — use `safe-edit` instead of `edit`

**Never use the built-in `edit` tool for Clojure-family files.** It writes before validation, so broken brackets are already saved when the error is detected.

`safe-edit` is a feature-complete replacement with added bracket validation. It matches pi-style edit semantics:

- **All edits matched against the original file** (not sequential)
- **Fuzzy matching** — smart quotes, Unicode dashes, special spaces normalized
- **BOM + CRLF handling**
- **Overlap & uniqueness checks**
- **Bracket validation via rewrite-clj**

### Single edit

```bash
bb scripts/safe-edit.clj --file src/core.clj --old 'old text' --new 'new text'
```

### Multiple edits (all matched against original)

```bash
bb scripts/safe-edit.clj --file src/core.clj \
  --edit '(defn foo [x]>(defn foo [x y]' \
  --edit '(+ x 1)>(+ x y 1)'

bb scripts/safe-edit.clj --file src/core.clj \
  --edits '[{:old "(defn foo [x]" :new "(defn foo [x y]"} {:old "(+ x 1)" :new "(+ x y 1)"}]'
```

### Custom separator (when `>` appears in code)

```bash
bb scripts/safe-edit.clj --file src/core.clj --sep '|||' \
  --edit '(-> x|||(->> x'
```

### Format with clojure-lsp

```bash
bb scripts/safe-edit.clj --file src/core.clj --format --old 'old' --new 'new'
```

### Dry run

```bash
bb scripts/safe-edit.clj --dry-run --file src/core.clj --old 'old' --new 'new'
```

## What safe-edit does

1. Reads the target file (strips BOM, detects line endings)
2. Finds all oldText matches against the original (fuzzy-aware)
3. Checks for overlaps and duplicate matches
4. Applies replacements in reverse order (stable offsets)
5. Restores BOM and original line endings
6. Validates the result with rewrite-clj
7. **If valid**: writes back
8. **If `--format`**: runs `clojure-lsp format` on the file
9. **If invalid**: prints error, **file NOT modified**, exits 1

## On Failure

1. Report the error to the user
2. Fix the bracket issue
3. Re-run safe-edit
4. Only proceed after successful edit

## Common LLM Bracket Mistakes

- Missing closing `)` at the end of `defn`, `let`, `if`, etc.
- Extra `]` or `}` from miscounting nested structures
- Unclosed string literals containing brackets
- Mismatched bracket types (e.g., `(let [x 1) ...)`)
