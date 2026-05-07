---
name: clj-paren-repair
description: A shell command for LLM coding assistants that don't support hooks. When the LLM encounters a Clojure/ClojureDart delimiter error, call this tool to fix it instead of trying to repair it manually.
---

# clj-paren-repair

A shell command for LLM coding assistants that don't support hooks (like Gemini CLI and Codex CLI). When the LLM encounters a delimiter error, it calls this tool to fix it instead of trying to repair it manually.

The key insight: When we observe an AI in the "Paren Edit Death Loop"—repeatedly failing to fix delimiter errors—we're witnessing a desperate search for a solution. `clj-paren-repair` provides an escape route that short-circuits this behavior.

Why this works: Modern SOTA models produce very accurate edits with only small delimiter discrepancies. The errors are minor enough that parinfer can reliably fix them. This simple solution works surprisingly well.

## Hooks vs clj-paren-repair

Hooks are the clear winner when available—they use zero tokens and happen without LLM invocation. However, `clj-paren-repair` works universally with any LLM that has shell access. When Gemini CLI gets hooks support, we should use them. Until then, `clj-paren-repair` is sufficient.

Using both together: Even with hooks configured, having `clj-paren-repair` available provides complete coverage. Hooks handle Edit/Write tools, but LLMs can also edit files via Bash (sed, awk, etc.). Having both tools catches all cases.

## How It Helps

- Provides escape route from "Paren Edit Death Loop"
- LLM calls it when it encounters delimiter errors
- Works with any LLM that has shell access
- Automatically formats files with cljfmt

## Installation

```sh
bbin install https://github.com/bhauman/clojure-mcp-light.git --tag v0.2.2 --as clj-paren-repair --main-opts '["-m" "clojure-mcp-light.paren-repair"]'
```

Or from local checkout:

```sh
bbin install . --as clj-paren-repair --main-opts '["-m" "clojure-mcp-light.paren-repair"]'
```

## Usage

```sh
# Single file
clj-paren-repair path/to/file.clj

# Multiple files
clj-paren-repair src/core.clj src/util.clj test/core_test.clj

# Help
clj-paren-repair --help
```

## When to Use

**Call `clj-paren-repair` when:**
- Clojure/ClojureDart compilation fails with "Unmatched delimiter" errors
- You encounter a "Paren Edit Death Loop" where repeated manual fixes don't work
- You need to quickly fix delimiter balance after large edits

**Do NOT use `clj-paren-repair` when:**
- Working with ClojureDart `.cljd` files that contain `.method` interop syntax (e.g., `.then`, `.catchError`, `.styleFrom`). The tool may incorrectly restructure method calls like `(obj.method arg)` into `(obj.method) arg`, breaking the code.
- The file syntax is fundamentally wrong (not just delimiter mismatch)

## Setup: Custom Instructions

Add to your global or local custom instructions file (`GEMINI.md`, `AGENTS.md`, `CLAUDE.md`, etc.):

```markdown
# Clojure Parenthesis Repair

The command `clj-paren-repair` is installed on your path.

Examples:
`clj-paren-repair <files>`
`clj-paren-repair path/to/file1.clj path/to/file2.clj path/to/file3.clj`

**IMPORTANT:** Do NOT try to manually repair parenthesis errors.
If you encounter unbalanced delimiters, run `clj-paren-repair` on the file
instead of attempting to fix them yourself. If the tool doesn't work,
report to the user that they need to fix the delimiter error manually.

The tool automatically formats files with cljfmt when it processes them.
```

## Workflow

1. Encounter delimiter error during compilation
2. Run `clj-paren-repair` on the affected file(s)
3. Re-run compilation to verify
4. If the tool fails or the file is `.cljd` with method interop, restore from git and apply changes more carefully
