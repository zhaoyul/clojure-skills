# clojure-skills

一个可复用的 Clojure agent skills 仓库。  
A repository of reusable Clojure-oriented agent skills.

## 安装 / Install

使用下面的命令安装这个 skills 仓库：

Install this skills repository with:

```sh
npx skills@latest add zhaoyul/clojure-skills
```

安装后，仓库中的 skills 会被加入到你的本地 skills 环境中。  
After installation, the skills in this repository become available in your local skills environment.

## 当前包含的 skill / Included skill

- `clojure-fullstack-skill`

## `clojure-fullstack-skill`

这是一个面向全栈 Clojure 项目的开发 skill，适合以下组合：

This is a full-stack Clojure development skill for projects that combine:

- Kit framework backend
- Reagent + Ant Design web frontend
- shared `.cljc` contracts
- ClojureDart + Flutter mobile
- `AGENTS.md`-driven workflows

它适合后端、前端、共享契约、移动端协同开发，也适合做架构检查、代码生成、重构、测试与调试。  
It is useful for backend, frontend, shared-contract, and mobile work, as well as architecture review, code generation, refactoring, testing, and debugging.

## 使用示例 / Example prompts

- `Use the clojure-fullstack-skill to scaffold a new Kit + Reagent + ClojureDart app.`
- `Use clojure-fullstack-skill to add a patient search page with a Kit API and Reagent frontend.`
- `Use clojure-fullstack-skill to review this full-stack Clojure codebase and suggest a safer AGENTS.md workflow.`
- `Use clojure-fullstack-skill to implement shared .cljc API contracts for backend, web, and mobile.`

## 仓库结构 / Repository layout

```text
clojure-fullstack-skill/
  README.md
  SKILL.md
  manifest.txt
  examples/
  references/
```

## 说明 / Notes

- `SKILL.md` 是主要 skill 定义文件。 / `SKILL.md` is the main skill definition.
- `README.md` 提供 skill 的简要介绍。 / `README.md` provides a short overview.
- `examples/` 包含示例 prompts。 / `examples/` contains example prompts.
- `references/` 包含补充参考资料。 / `references/` contains supporting reference material.
- `manifest.txt` 列出打包内容。 / `manifest.txt` lists packaged files.

## GitHub

- Repository: <https://github.com/zhaoyul/clojure-skills>
