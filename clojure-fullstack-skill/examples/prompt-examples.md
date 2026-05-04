# Prompt Examples

## Add a backend feature

> 在 Hospital 项目里添加 appointment check-in API. 请先参考相关 AGENTS.md, 然后给出需要修改的完整文件, 包括 migration, HugSQL, service, route, controller, tests, 和验证命令.

## Add a web page

> 基于当前 frontend 的 antd + reagent 风格, 添加患者列表页面. 要有搜索, 表格, 分页, loading, error, 新建 Drawer. 给完整 cljs 文件.

## Add a mobile screen

> Mobile 端用 ClojureDart 添加 appointment detail screen. 参考 mobile/AGENTS.md, 保持 lib 作为桥接层, .cljd 放在 src. 给完整代码和运行命令.

## Cross-stack feature

> 实现医生排班管理. 后端 Kit API + migration + service, 前端 antd 页面, mobile 只读列表. 先列出涉及的文件, 然后给完整代码.

## Debug backend

> `(reset)` 报 Integrant key not found. 这是 resources/system.edn 和 namespace. 请定位原因并给完整修复.

## Debug frontend

> antd Table 不显示数据. 这是 cljs 代码和 API 返回. 请检查 reagent/antd interop, clj->js 边界, rowKey, 和状态更新.

## Debug mobile

> `clj -M:cljd flutter` 编译失败. 这是错误输出. 请判断是 deps.edn, ClojureDart, Dart package, Flutter device, 还是 runtime 问题, 然后给最小修复.

## Review PR

> Review 这批改动是否符合 Hospital 项目的 AGENTS.md. 重点看 Kit Integrant wiring, API contract, antd reagent 状态管理, ClojureDart interop, 权限, 审计, 测试.
