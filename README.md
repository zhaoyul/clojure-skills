# clojure-skills

A small repository of reusable Clojure-oriented agent skills.

## Install

Add this skill repository with:

```sh
npx skills@latest add zhaoyul/clojure-skills
```

After installation, this repository will make the included skill available in your local skills environment.

## What gets installed

This repository currently includes:

- `clojure-fullstack-skill`

## Included skill

### `clojure-fullstack-skill`

A full-stack Clojure development skill for projects that combine:

- Kit framework on the backend
- Reagent + Ant Design on the web frontend
- shared `.cljc` contracts
- ClojureDart + Flutter on mobile
- AGENTS.md-driven project workflows

Although the skill was originally shaped by hospital-style product work, it is also useful for broader business applications that use the same architecture and development patterns.

## Example prompts

You can invoke this skill with prompts like:

- `Use the clojure-fullstack-skill to scaffold a new Kit + Reagent + ClojureDart app.`
- `Use clojure-fullstack-skill to add a patient search page with a Kit API and Reagent frontend.`
- `Use clojure-fullstack-skill to review this full-stack Clojure codebase and suggest a safer AGENTS.md workflow.`
- `Use clojure-fullstack-skill to implement shared .cljc API contracts for backend, web, and mobile.`

## About `clojure-fullstack-skill`

This skill is intended for agent-assisted development work across backend, frontend, shared contracts, and mobile in a Clojure-centric stack. It emphasizes:

- inspecting the real project before proposing code
- respecting `AGENTS.md` instructions
- keeping boundaries clear across backend, web, shared, and mobile
- using practical full-stack delivery patterns for business applications

## Repository layout

```text
clojure-fullstack-skill/
  SKILL.md
  manifest.txt
  examples/
  references/
```

## Notes

- `SKILL.md` contains the main skill definition and operating guidance.
- `examples/` contains prompt examples.
- `references/` contains supporting implementation and architecture notes.
- `manifest.txt` lists the packaged files.

## GitHub

Repository: <https://github.com/zhaoyul/clojure-skills>
