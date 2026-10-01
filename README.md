# 图节点执行引擎（Graph Node Execution Engine）

一套本地进程内运行的工作流节点图执行引擎：把由节点和有向连线组成的执行定义，
按节点之间的依赖关系调度执行，支持条件分支、并行调度、超时重试、失败策略、
取消与重启恢复。执行定义与节点配置均来自本地，不依赖任何外部服务。

## 核心概念

### 执行定义（GraphDefinition）

一份定义包含：

- `name`：定义名称
- `version`：版本号，注册时由服务端按名称单调分配，运行开始时快照进运行状态
- `nodes`：节点列表，每个节点有
  - `id`：图内唯一
  - `type`：执行器类型（`noop` / `data` / `spel` / `sleep` / `flaky` / `effect`）
  - `config`：执行器配置（本地来源）
  - `timeout`：节点级超时（ISO-8601 时长，如 `PT5S`），为空表示不超时
  - `retries`：失败后最大重试次数（不含首次执行），默认 0
  - `backoff`：重试退避基准时长，默认 1s，按指数增长（`backoff * 2^(attempt-1)`，上限 60s）
  - `join`：汇聚语义，`ALL`（默认）或 `ANY`
- `edges`：有向连线列表，每条有
  - `source` / `target`：源节点与目标节点 id
  - `condition`：条件表达式（SpEL，用 `#output` 引用源节点输出），为空表示无条件恒成立

### 执行语义

1. **依赖调度**：节点必须在其全部前置边都解析（上游已结束）后才会被调度。
2. **条件分支**：上游成功后，对每条出边求值条件。条件不成立的边标记为 `INACTIVE`，
   其目标节点不会被执行，而是标记为 `SKIPPED`，跳过会沿出边级联传播。
3. **汇聚语义**：
   - `ALL`（默认）：所有前置边都成立才执行，任一边不成立则跳过。
   - `ANY`：至少一条前置边成立即执行，其余不成立的边忽略。
4. **并行推进**：相互独立的节点并行执行，并发度可配置（运行级 `concurrency`）。
   同一发货定义在相同并发度下，每个节点的可见结果一致；每个节点只执行一次，
   其输出只被提交一次，供下游节点引用。
5. **超时与重试**：节点执行超过 `timeout` 会被取消并计入一次失败尝试；
   失败后按 `retries` 重试，重试之间按 `backoff` 指数退避。
6. **失败策略**（`failureStrategy`）：
   - `ABORT`（默认）：重试耗尽后中止整个运行，不再调度新节点，
     未完成节点标记为 `CANCELLED`，运行状态为 `FAILED`。
   - `CONTINUE`：失败节点的下游被跳过（`SKIPPED`），其余独立分支继续执行，
     运行最终状态为 `FAILED`（存在失败节点）。
7. **取消**：对正在执行的运行发出取消后，不再调度新节点；运行中的节点被中断并标记为
   `CANCELLED`；重复取消幂等；取消后所有节点均为终态，状态自洽。
8. **持久化与恢复**：运行状态在每次状态提交时以临时文件 + 原子移动的方式落盘。
   进程重启后，未完成的运行从最后一个已提交位置继续：已完成节点的输出保留、不会被
   再次执行；崩溃时尚在 `RUNNING` 的节点会被重置为 `PENDING` 后重新调度。

### 定义校验

注册定义时整体校验，失败时拒绝且不产生任何执行，返回可区分的原因：

- `DUPLICATE_NODE_ID`：节点 id 重复
- `NODE_NOT_FOUND`：连线指向不存在的节点（悬空边）
- `CYCLE_DETECTED`：图中存在环（含自环）

### 内置执行器

| type     | 说明                                                          | config                          |
|----------|---------------------------------------------------------------|---------------------------------|
| `noop`   | 空操作，输出空 Map                                            | -                               |
| `data`   | 返回静态数据                                                  | `data`：输出 Map                |
| `spel`   | SpEL 求值，变量 `#inputs`（前置输出）、`#node`（节点定义）   | `expression`：表达式            |
| `sleep`  | 休眠指定毫秒数（用于超时/并发测试）                           | `ms`：毫秒数                    |
| `flaky`  | 前 `failTimes` 次尝试失败，之后成功（用于重试测试）           | `failTimes`：失败次数           |
| `effect` | 向文件追加一行（用于副作用/恢复测试）                         | `path`：文件路径                |

## 配置项

在 `application.properties` 中配置（也可通过命令行参数或环境变量覆盖）：

| 配置项                          | 默认值     | 说明                                   |
|---------------------------------|------------|----------------------------------------|
| `engine.data-dir`               | `./data`   | 本地数据目录（定义与运行状态落盘位置） |
| `engine.concurrency`            | `4`        | 默认并发度（运行可单独指定）           |
| `engine.max-pool-threads`       | `64`       | 节点任务线程池最大线程数               |
| `engine.default-failure-strategy` | `ABORT` | 默认失败策略（`ABORT` / `CONTINUE`） |

## REST API

### 定义管理

- `POST /api/definitions`：注册定义（整体校验，版本号由服务端分配）
- `GET /api/definitions`：列出全部定义
- `GET /api/definitions/{name}`：获取指定名称的最新版本
- `GET /api/definitions/{name}/versions/{version}`：获取指定版本

### 运行管理

- `POST /api/runs`：启动运行（请求体：`definitionName`、可选 `definitionVersion`、
  可选 `concurrency`、可选 `failureStrategy`）
- `GET /api/runs`：列出全部运行
- `GET /api/runs/{runId}`：查询运行状态（节点状态、输出、失败原因）
- `POST /api/runs/{runId}/cancel`：取消运行（幂等）
- `POST /api/runs/{runId}/resume`：恢复未完成运行（进程重启后会自动恢复）

## 持久化布局

```
${engine.data-dir}/
├── definitions/
│   └── <name>-<version>.json      # 版本化定义（不可变）
└── runs/
    └── <runId>.json               # 运行完整状态（定义快照、节点状态、边状态、输出）
```

## 测试

```bash
mvn test
```

覆盖场景：

- `DefinitionValidatorTest`：环检测、悬空边、重复 id、整体拒绝
- `ConditionalBranchTest`：条件分支跳过、级联跳过、ALL/ANY 汇聚语义
- `ConcurrencyTest`：并行推进、串行对照、同定义同并发度结果一致、输出只被采用一次
- `TimeoutRetryTest`：节点超时、重试退避、ABORT/CONTINUE 失败策略
- `CancellationTest`：取消后不再调度、运行中节点停止、幂等、终态自洽
- `RestartRecoveryTest`：崩溃恢复、已完成节点不重复执行、无重复副作用、启动自动恢复
- `WebIntegrationTest`：REST 接口端到端（注册、启动、查询、取消、非法定义 400）
