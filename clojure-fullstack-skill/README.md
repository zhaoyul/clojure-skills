# clojure-fullstack-skill

一个面向全栈 Clojure 项目的 agent skill。  
An agent skill for full-stack Clojure development.

## 安装 / Install

从仓库安装：

Install from the repository:

```sh
npx skills@latest add zhaoyul/clojure-skills
```

## 适用场景 / Use cases

适合以下技术组合：

Best suited for stacks that combine:

- Kit backend
- Reagent + Ant Design frontend
- shared `.cljc` contracts
- ClojureDart + Flutter mobile
- `AGENTS.md`-guided project conventions

## 你可以这样使用 / Example prompts

- `Use the clojure-fullstack-skill to scaffold a new Kit app with shared contracts.`
- `Use clojure-fullstack-skill to add a new CRUD flow across backend, web, and mobile.`
- `Use clojure-fullstack-skill to inspect AGENTS.md files and propose an implementation plan.`
- `Use clojure-fullstack-skill to review Integrant wiring, routes, schemas, and frontend state management.`

## 文件说明 / Files

```text
README.md
SKILL.md
manifest.txt
examples/
references/
```

- `SKILL.md`: 主 skill 定义 / main skill definition
- `examples/`: 示例 prompt / example prompts
- `references/`: 参考资料 / supporting references
- `manifest.txt`: 打包清单 / package manifest
