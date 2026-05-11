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

# 结构化编辑工具
npx skills@latest add zhaoyul/clojure-skills --skill clojure-struct-edit

# 括号修复工具
npx skills@latest add zhaoyul/clojure-skills --skill clj-paren-repair

# 全栈开发 skill
npx skills@latest add zhaoyul/clojure-skills --skill clojure-fullstack-skill

# 括号守卫（编辑前验证 + 格式化）
npx skills@latest add zhaoyul/clojure-skills --skill clojure-bracket-guard
```

安装后，skills 会被加入到你的本地 skills 环境中。  
After installation, the skills become available in your local skills environment.

## 当前包含的 skills / Included skills

| Skill | Description |
|---|---|
| `clojure-fullstack-skill` | 面向 Kit + Reagent + ClojureDart 全栈项目的开发 skill |
| `clojure-struct-edit` | 结构化编辑 Clojure 代码，原子操作 + 括号验证 + diff 预览 |
| `clojure-repl-eval` | 通过 nREPL 评估 Clojure 代码，验证编译、测试函数 |
| `clj-paren-repair` | 修复 Clojure/ClojureDart 括号不匹配问题，自动格式化 |
| `clojure-bracket-guard` | 编辑前验证括号平衡，支持 fuzzy matching、CRLF/BOM、clojure-lsp 格式化 |

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

## `clojure-struct-edit`

通过 Emacs 结构化编辑安全地修改 Clojure 代码的 skill. 核心设计原则:

- **禁止原始文本 patch**: LLM 只输出 JSON 操作指令, 真正的括号操作由 Emacs dispatcher 完成
- **原子事务**: 所有编辑在 `atomic-change-group` 内执行, 失败自动回滚
- **强制验证**: 每次编辑后自动运行 `check-parens`
- **diff 预览**: 先 `save: false` 预览, 确认后再 `save: true` 保存

### 支持的操作 (Phase 1)

| 操作 | 说明 |
|---|---|
| `snapshot` | 查看文件结构, 获取所有顶层 form 的行号/列号/SHA256 |
| `get-form` | 读取指定 form 的完整文本 |
| `replace-form` | 替换完整 form |
| `insert-form-after` | 在 form 后插入 |
| `insert-form-before` | 在 form 前插入 |
| `remove-form` | 删除完整 form |
| `validate` | 验证文件括号平衡 |

### 使用示例 / Example prompts

- `Use clojure-struct-edit to snapshot src/app/core.clj and list all defn forms.`
- `Use clojure-struct-edit to replace the handler function in src/app/core.clj with a new implementation.`
- `Use clojure-struct-edit to add a private helper function after the current handler.`

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

## `clojure-bracket-guard`

编辑 Clojure 代码时，在写入前验证括号平衡的 skill。是内置 edit 工具的安全替代。适合：

- LLM 编辑 .clj/.cljs/.cljc/.cljd/.edn 文件时防止括号不匹配
- 支持 fuzzy matching（智能引号、Unicode 破折号、特殊空格）
- 支持 CRLF/BOM 透明处理
- 可选的 clojure-lsp 格式化

核心理念：把错误消灭在写入之前，而不是写入后修复。

A safe replacement for built-in edit tools when editing Clojure code. Validates bracket balance before writing. Features:

- Fuzzy matching (smart quotes, Unicode dashes, special spaces)
- CRLF/BOM transparent handling
- Optional clojure-lsp formatting
- All edits matched against original file (pi-style semantics)

Core idea: prevent bracket errors before writing, rather than fixing them after.

### 使用示例 / Example prompts

- `Use clojure-bracket-guard to edit src/core.clj — change the add function to accept 3 args.`
- `Use clojure-bracket-guard to refactor the greet function and format the result.`

## 仓库结构 / Repository layout

```text
clojure-fullstack-skill/
  README.md
  SKILL.md
  manifest.txt
  examples/
  references/

clojure-struct-edit/
  SKILL.md

clojure-repl-eval/
  SKILL.md

clj-paren-repair/
  SKILL.md

clojure-bracket-guard/
  SKILL.md
  scripts/
    validate.clj      # 括号验证
    safe-edit.clj      # 安全编辑（替代内置 edit）
```

## 说明 / Notes

- `SKILL.md` 是主要 skill 定义文件。 / `SKILL.md` is the main skill definition.
- `README.md` 提供 skill 的简要介绍。 / `README.md` provides a short overview.
- `examples/` 包含示例 prompts。 / `examples/` contains example prompts.
- `references/` 包含补充参考资料。 / `references/` contains supporting reference material.
- `manifest.txt` 列出打包内容。 / `manifest.txt` lists packaged files.

## GitHub

- Repository: <https://github.com/zhaoyul/clojure-skills>
