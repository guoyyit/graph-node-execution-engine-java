# Graph Node Execution Engine

在本地进程内执行"节点 + 有向连线"工作流定义的引擎。执行定义与节点配置全部来自本地，
不依赖任何外部服务。每次运行可查询、可取消、可在进程重启后恢复。

## 核心概念

| 概念 | 说明 |
| --- | --- |
| `WorkflowDefinition` | 执行定义：`id` + `version` + 节点列表 + 有向连线列表，可版本化，写入后不可变 |
| `NodeDefinition` | 节点：`id`、`type`（执行器类型）、`config`、超时、重试、退避、失败策略 |
| `EdgeDefinition` | 连线：`from` -> `to`，可携带条件表达式 |
| `RunSnapshot` | 一次运行的可持久化状态，内嵌提交时刻的定义副本 |
| `NodeExecutor` | 节点执行器 SPI，按 `type` 注册，必须线程安全且幂等 |

## 执行语义

### 校验（整体拒绝）

提交前对定义做整体校验，失败即拒绝并返回**可区分的原因**，不会先执行一部分再失败：

| 原因 | 含义 |
| --- | --- |
| `CYCLE_DETECTED` | 定义中存在环（错误信息列出环上节点） |
| `UNKNOWN_NODE` | 连线指向不存在的节点 |
| `DUPLICATE_NODE` | 节点 id 重复 |
| `MALFORMED` | 缺少必要字段 |

### 调度

- 节点在其**全部入边解析完成**且**至少一条入边被采用**后才会被调度。
- 入边被采用 = 上游节点 `SUCCESS` 且（无条件 或 条件表达式对上游输出求值为真）。
- 全部入边解析完但无一被采用时，节点标记为 `SKIPPED`，其下游按同样规则继续传播。
- 相互独立的节点并行推进，并发度由 `engine.max-concurrency` 控制。
- 调度决策是确定性的：就绪集合按拓扑序（平局按节点 id 字典序）派发，
  同样定义与并发度下，最终结果与每个节点的可见结果一致。
- 节点输出在成功时一次性写入快照并以**不可变副本**提供给下游，只被采用一次。

### 条件表达式

基于上游节点输出求值，支持 `&&` / `||` 组合（`&&` 优先）：

```
flag == true
count >= 3 && status == 'ok'
result.code != 0 || retry
```

操作数支持 `'string'`、`"string"`、数字、`true/false`、`null`；
`>`、`>=`、`<`、`<=` 仅用于数值；裸 `key` 做真值判断；`a.b.c` 逐层取嵌套值。

### 节点状态机

`PENDING -> RUNNING -> SUCCESS / FAILED / SKIPPED / CANCELLED`

### 运行状态机

`RUNNING -> SUCCESS / FAILED / CANCELLED`

- `SUCCESS`：所有节点终态为 `SUCCESS` 或 `SKIPPED`。
- `FAILED`：任一节点 `FAILED`（含 `CONTINUE` 策略下失败但运行继续完成的情形），
  `failureReason` 与已完成节点的结果均可查询。
- `CANCELLED`：运行被取消且无失败节点。

## 超时、重试与失败策略

节点级配置（`NodeDefinition`）：

| 字段 | 默认 | 说明 |
| --- | --- | --- |
| `timeoutMs` | 0（不限） | 单次尝试超时，超时按失败处理并触发重试，执行线程被中断 |
| `maxRetries` | 0 | 失败后的最大重试次数（不含首次尝试） |
| `retryBackoffMs` | 0 | 退避基数，第 n 次重试前等待 `retryBackoffMs * n`（线性退避） |
| `failurePolicy` | `ABORT_RUN` | 重试耗尽后的策略 |

失败策略：

- `ABORT_RUN`：节点标记 `FAILED`，整个运行中止——不再调度新节点，
  在途节点被中断并标记 `CANCELLED`，未调度节点标记 `CANCELLED`，运行终态 `FAILED`。
- `CONTINUE`：节点标记 `FAILED`，其下游因入边未被采用而标记 `SKIPPED`，
  其余分支继续推进；运行结束后若存在失败节点，运行终态为 `FAILED` 并附原因。

## 取消语义

- `cancel(runId)` 幂等：重复取消、对终态运行取消都不改变结果。
- 取消后不再调度任何新节点；正在执行的节点尝试被中断并标记 `CANCELLED`；
  未调度节点标记 `CANCELLED`；已完成节点保持 `SUCCESS` 及其输出。
- 取消标记随快照持久化，崩溃恢复后继续按取消语义收敛。

## 持久化与重启恢复

- 每次状态变更后，运行快照通过"临时文件 + 原子移动"整体落盘到 `engine.runs-dir`。
- 进程重启时加载全部 `RUNNING` 状态的运行：
  - `SUCCESS` 节点保留结果，**不会被再次执行**；
  - 崩溃时处于 `RUNNING` 的节点重置为 `PENDING` 重新执行——
    **执行器实现必须幂等**，以保证不产生重复的对外效果；
  - 崩溃前已请求取消的运行继续收敛到 `CANCELLED`。

## 版本化与隔离

- 定义存储在 `engine.definitions-dir/<id>/<version>.json`，同一 id 的多个版本并存，
  已写入的版本不可覆盖（返回 `ALREADY_EXISTS`）。
- 提交运行时把定义副本嵌入运行快照，定义后续修改不影响已开始的运行。
- 多个运行并发进行时各自维护独立快照，状态互不干扰。

## 配置项

`application.properties`：

| 配置 | 默认 | 说明 |
| --- | --- | --- |
| `engine.max-concurrency` | 4 | 单运行内节点级最大并发度 |
| `engine.definitions-dir` | `data/definitions` | 版本化定义存储目录 |
| `engine.runs-dir` | `data/runs` | 运行快照存储目录 |

## REST API

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST` | `/api/definitions` | 注册新版本定义；校验失败返回 400 + 可区分 `reason` |
| `GET` | `/api/definitions/{id}/versions` | 列出版本 |
| `POST` | `/api/runs?definitionId=&version=` | 提交运行（body 为运行输入 JSON），返回 `runId` |
| `GET` | `/api/runs/{runId}` | 查询运行状态与每个节点结果 |
| `POST` | `/api/runs/{runId}/cancel` | 取消运行（幂等） |

## 自定义节点执行器

```java
registry.register("my-type", ctx -> {
    // ctx.config() 节点配置；ctx.input() 运行输入；
    // ctx.upstreamOutputs() 上游输出（不可变）；ctx.attempt() 当前尝试序号
    if (ctx.isCancelRequested()) {
        return Map.of(); // 尽快返回
    }
    return Map.of("result", 42);
});
```

内置 `echo` 执行器返回节点配置与运行输入，便于联调。

## 构建与测试

```bash
mvn test
```

测试覆盖：环检测与悬空连线、条件分支与汇聚、并发执行与确定性、
超时与重试退避、取消幂等、进程重启恢复。
