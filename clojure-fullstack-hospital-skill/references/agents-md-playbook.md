# AGENTS.md Playbook For Hospital-Style Clojure Projects

## Why this matters

Hospital-style repositories often encode project-specific rules in `AGENTS.md` files. These files are closer to the code than a general skill, so they win when there is a conflict.

## Discovery

```sh
find . -name AGENTS.md -print
```

Read the root file first, then the nearest file for every edited path.

Example:

```text
AGENTS.md
src/hospital/backend/AGENTS.md
frontend/AGENTS.md
mobile/AGENTS.md
shared/AGENTS.md
```

If editing `mobile/src/hospital/mobile/screens/patient.cljd`, read:

1. `AGENTS.md`
2. `mobile/AGENTS.md`
3. Any deeper `AGENTS.md` under `mobile/src/**` if present

## Precedence

1. Current user request.
2. Safety and privacy requirements.
3. Nearest `AGENTS.md`.
4. Parent `AGENTS.md` files.
5. Skill defaults.

## What to extract

From each file, extract:

- Commands to run.
- Formatting and linting rules.
- Namespace conventions.
- Forbidden dependencies.
- API conventions.
- State management conventions.
- Testing expectations.
- Security and logging rules.
- Branch or commit workflow if mentioned.

## Response pattern after reading

```text
我会遵守这些本地规则:
1. Backend uses Kit and Integrant wiring in resources/system.edn.
2. Web pages use existing antd wrappers in hospital.frontend.antd.
3. Mobile business UI goes in .cljd files under mobile/src.
```

Then implement.

## Conflict handling

If two files conflict, explain the conflict and choose the nearest applicable file unless the user request or safety requirement overrides it.

## Missing AGENTS.md

If no files exist, proceed with the stack defaults:

- Backend: Kit, Integrant, Aero, SQL migrations.
- Web: Reagent and Ant Design.
- Mobile: ClojureDart and Flutter.
- Shared: `.cljc` contracts where useful.
