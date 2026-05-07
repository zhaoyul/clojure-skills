# clojure-skills

一个可复用的 Clojure agent skills 仓库。  
A repository of reusable Clojure-oriented agent skills.

## 安装 / Install

### 安装整个仓库（所有 skills）

```sh
npx skills@latest add zhaoyul/clojure-skills
```

### 单独安装某个 skill

```sh
# Clojure REPL 评估工具
npx skills@latest add zhaoyul/clojure-skills --skill clojure-repl-eval

# 括号修复工具
npx skills@latest add zhaoyul/clojure-skills --skill clj-paren-repair

# 全栈开发 skill
npx skills@latest add zhaoyul/clojure-skills --skill clojure-fullstack-skill
```

安装后，skills 会被加入到你的本地 skills 环境中。  
After installation, the skills become available in your local skills environment.

## 当前包含的 skills / Included skills

| Skill | Description |
|---|---|
| `clojure-fullstack-skill` | 面向 Kit + Reagent + ClojureDart 全栈项目的开发 skill |
| `clojure-repl-eval` | 通过 nREPL 评估 Clojure 代码，验证编译、测试函数 |
| `clj-paren-repair` | 修复 Clojure/ClojureDart 括号不匹配问题，自动格式化 |

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

### 使用示例 / Example prompts

- `Use the clojure-fullstack-skill to scaffold a new Kit + Reagent + ClojureDart app.`
- `Use clojure-fullstack-skill to add a patient search page with a Kit API and Reagent frontend.`
- `Use clojure-fullstack-skill to review this full-stack Clojure codebase and suggest a safer AGENTS.md workflow.`
- `Use clojure-fullstack-skill to implement shared .cljc API contracts for backend, web, and mobile.`

## `clojure-repl-eval`

通过 nREPL 评估 Clojure 代码的 skill。适合：

- 验证编辑后的文件是否能编译
- 交互式测试函数行为
- 调试代码
- 在提交前验证改动

### 使用示例 / Example prompts

- `Use clojure-repl-eval to test if my namespace compiles.`
- `Use clojure-repl-eval to discover nREPL ports and evaluate expressions.`

## `clj-paren-repair`

修复 Clojure/ClojureDart 括号不匹配问题的 skill。适合：

- 编译失败时的 delimiter 错误修复
- 跳出 "Paren Edit Death Loop"
- 大量编辑后的快速格式化

### 使用示例 / Example prompts

- `Use clj-paren-repair to fix the delimiter error in src/core.clj.`

## 仓库结构 / Repository layout

```text
clojure-fullstack-skill/
  README.md
  SKILL.md
  manifest.txt
  examples/
  references/

clojure-repl-eval/
  SKILL.md

clj-paren-repair/
  SKILL.md
```

## 说明 / Notes

- `SKILL.md` 是主要 skill 定义文件。 / `SKILL.md` is the main skill definition.
- `README.md` 提供 skill 的简要介绍。 / `README.md` provides a short overview.
- `examples/` 包含示例 prompts。 / `examples/` contains example prompts.
- `references/` 包含补充参考资料。 / `references/` contains supporting reference material.
- `manifest.txt` 列出打包内容。 / `manifest.txt` lists packaged files.

## GitHub

- Repository: <https://github.com/zhaoyul/clojure-skills>
