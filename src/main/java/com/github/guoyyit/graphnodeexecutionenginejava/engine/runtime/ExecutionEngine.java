package com.github.guoyyit.graphnodeexecutionenginejava.engine.runtime;

import com.github.guoyyit.graphnodeexecutionenginejava.engine.def.DefinitionService;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.exec.ConditionEvaluator;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.exec.NodeExecutorRegistry;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.model.EdgeDefinition;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.model.EdgeState;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.model.EdgeStatus;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.model.FailureStrategy;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.model.GraphDefinition;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.model.JoinMode;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.model.NodeDefinition;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.model.NodeState;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.model.NodeStatus;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.model.RunState;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.model.RunStatus;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 执行引擎：负责运行的创建、查询、取消、恢复，以及调度器生命周期管理。
 *
 * <p>定义在运行开始时快照进运行状态，因此定义后续修改不影响已开始的运行；
 * 多个运行各自持有独立状态，互不干扰。
 */
public class ExecutionEngine {

    private final RunRepository repository;
    private final DefinitionService definitions;
    private final NodeExecutorRegistry executors;
    private final ConditionEvaluator conditionEvaluator = new ConditionEvaluator();
    private final int defaultConcurrency;
    private final FailureStrategy defaultFailureStrategy;
    private final ExecutorService executorService;
    private final Map<String, RunScheduler> schedulers = new ConcurrentHashMap<>();
    /** 内存中的运行状态，调度器与任务线程共享；取消等操作必须作用于此对象 */
    private final Map<String, RunState> activeRuns = new ConcurrentHashMap<>();
    private volatile boolean shuttingDown;

    public ExecutionEngine(Path dataDir,
                           DefinitionService definitions,
                           NodeExecutorRegistry executors,
                           int defaultConcurrency,
                           FailureStrategy defaultFailureStrategy,
                           int maxPoolThreads) {
        this.repository = new RunRepository(dataDir);
        this.definitions = definitions;
        this.executors = executors;
        this.defaultConcurrency = defaultConcurrency;
        this.defaultFailureStrategy = defaultFailureStrategy;
        this.executorService = new ThreadPoolExecutor(
                maxPoolThreads, maxPoolThreads, 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(),
                r -> {
                    Thread t = new Thread(r, "graph-node-" + UUID.randomUUID());
                    t.setDaemon(true);
                    return t;
                });
    }

    /**
     * 注册定义（整体校验），返回带版本号的定义。
     */
    public GraphDefinition registerDefinition(GraphDefinition definition) {
        return definitions.register(definition);
    }

    /**
     * 启动一次运行：快照定义、创建运行状态并启动调度器。
     */
    public synchronized RunState startRun(String definitionName,
                                          Integer definitionVersion,
                                          Integer concurrency,
                                          FailureStrategy failureStrategy) {
        GraphDefinition definition = resolveDefinition(definitionName, definitionVersion);
        int effectiveConcurrency = concurrency == null ? defaultConcurrency : Math.max(1, concurrency);
        FailureStrategy effectiveStrategy = failureStrategy == null ? defaultFailureStrategy : failureStrategy;

        String runId = UUID.randomUUID().toString();
        RunState run = new RunState(runId, definition, effectiveStrategy, effectiveConcurrency);
        run.setStatus(RunStatus.RUNNING);
        run.setStartedAt(Instant.now());
        repository.save(run);

        RunScheduler scheduler = new RunScheduler(run, this);
        schedulers.put(runId, scheduler);
        activeRuns.put(runId, run);
        scheduler.start();
        return run;
    }

    public RunState getRun(String runId) {
        RunState active = activeRuns.get(runId);
        if (active != null) {
            return active;
        }
        RunState run = repository.load(runId);
        if (run == null) {
            throw new IllegalArgumentException("run not found: " + runId);
        }
        return run;
    }

    public List<RunState> listRuns() {
        return repository.listAll();
    }

    /**
     * 取消运行。幂等：重复取消无副作用。
     */
    public RunState cancel(String runId) {
        RunState run = getRun(runId);
        synchronized (run) {
        if (run.isCancelRequested() || run.getStatus().isTerminal()) {
            return run;
        }
        run.setCancelRequested(true);
        if (run.getStatus() == RunStatus.RUNNING) {
            run.setStatus(RunStatus.CANCELLED);
            for (NodeState ns : run.getNodeStates().values()) {
                if (!ns.isTerminal()) {
                    ns.setStatus(NodeStatus.CANCELLED);
                    ns.setFinishedAt(Instant.now());
                }
            }
            run.setFinishedAt(Instant.now());
            repository.save(run);
            RunScheduler scheduler = schedulers.get(runId);
            if (scheduler != null) {
                scheduler.cancelRunningTasks();
                scheduler.interrupt();
            }
        } else {
            // 非 RUNNING 但未结束（理论上不会），直接落盘
            repository.save(run);
        }
        return run;
        }
    }

    /**
     * 恢复运行：把上次崩溃时尚在 RUNNING 的节点重置为 PENDING，然后重新启动调度。
     * 已完成节点的输出保留，不会被再次执行。
     */
    public RunState resume(String runId) {
        RunState run = getRun(runId);
        synchronized (run) {
        if (run.getStatus().isTerminal()) {
            return run;
        }
        if (schedulers.containsKey(runId)) {
            return run;
        }
        for (NodeState ns : run.getNodeStates().values()) {
            if (ns.getStatus() == NodeStatus.RUNNING) {
                ns.setStatus(NodeStatus.PENDING);
                ns.setStartedAt(null);
                ns.setAttempts(0);
            }
        }
        run.setStatus(RunStatus.RUNNING);
        if (run.getStartedAt() == null) {
            run.setStartedAt(Instant.now());
        }
        repository.save(run);

        RunScheduler scheduler = new RunScheduler(run, this);
        schedulers.put(runId, scheduler);
        activeRuns.put(runId, run);
        scheduler.start();
        return run;
        }
    }

    /**
     * 启动时恢复所有未完成运行。
     */
    public void resumeAllOnStartup() {
        for (RunState run : repository.listAll()) {
            if (!run.getStatus().isTerminal()) {
                try {
                    resume(run.getRunId());
                } catch (Exception e) {
                    // 单个运行恢复失败不影响其他运行
                }
            }
        }
    }

    /**
     * 模拟进程崩溃：关闭线程池并中断调度线程，不做任何终结处理，
     * 磁盘上仅保留最后一个已提交状态。
     */
    public void shutdown() {
        shuttingDown = true;
        for (RunScheduler scheduler : schedulers.values()) {
            scheduler.interrupt();
        }
        executorService.shutdownNow();
    }

    public boolean isShuttingDown() {
        return shuttingDown;
    }

    // ------------------------------------------------------------------
    // 调度回调
    // ------------------------------------------------------------------

    /**
     * 节点成功后：解析出边（条件求值），传播跳过，检查完成。
     */
    public void onNodeSucceeded(RunState run, String nodeId) {
        synchronized (run) {
        if (run.getStatus() != RunStatus.RUNNING) {
            return;
        }
        Map<String, Object> output = run.getOutputs().get(nodeId);
        for (EdgeState edge : run.outgoingEdges(nodeId)) {
            EdgeDefinition edgeDef = findEdge(run, edge.getEdgeId());
            boolean active = conditionEvaluator.evaluate(edgeDef.condition(), output);
            edge.setStatus(active ? EdgeStatus.ACTIVE : EdgeStatus.INACTIVE);
        }
        repository.save(run);
        propagateSkips(run);
        checkCompletion(run);
        }
    }

    /**
     * 节点重试耗尽后：按失败策略处理。
     */
    public void onNodeFailed(RunState run, String nodeId) {
        synchronized (run) {
        if (run.getStatus() != RunStatus.RUNNING) {
            return;
        }
        if (run.getFailureStrategy() == FailureStrategy.ABORT) {
            run.setStatus(RunStatus.FAILED);
            run.setError("node failed after retries: " + nodeId
                    + " - " + run.nodeState(nodeId).getLastError());
            for (NodeState ns : run.getNodeStates().values()) {
                if (!ns.isTerminal()) {
                    ns.setStatus(NodeStatus.CANCELLED);
                    ns.setFinishedAt(Instant.now());
                }
            }
            run.setFinishedAt(Instant.now());
            repository.save(run);
            RunScheduler scheduler = schedulers.get(run.getRunId());
            if (scheduler != null) {
                scheduler.cancelRunningTasks();
            }
        } else {
            for (EdgeState edge : run.outgoingEdges(nodeId)) {
                edge.setStatus(EdgeStatus.INACTIVE);
            }
            repository.save(run);
            propagateSkips(run);
            checkCompletion(run);
        }
        }
    }

    /**
     * 跳过传播：所有前置边均已解析但不满足依赖语义的节点标记为 SKIPPED，
     * 其出边置为 INACTIVE，反复传播直到不动点。
     */
    public void propagateSkips(RunState run) {
        synchronized (run) {
        boolean changed = true;
        while (changed) {
            changed = false;
            for (NodeDefinition def : run.getDefinition().nodes()) {
                NodeState ns = run.nodeState(def.id());
                if (ns.getStatus() != NodeStatus.PENDING) {
                    continue;
                }
                List<EdgeState> incoming = run.incomingEdges(def.id());
                boolean allResolved = incoming.stream()
                        .noneMatch(e -> e.getStatus() == EdgeStatus.PENDING);
                if (!allResolved) {
                    continue;
                }
                boolean executable = def.joinOrDefault() == JoinMode.ALL
                        ? incoming.stream().allMatch(e -> e.getStatus() == EdgeStatus.ACTIVE)
                        : incoming.stream().anyMatch(e -> e.getStatus() == EdgeStatus.ACTIVE);
                if (!executable) {
                    ns.setStatus(NodeStatus.SKIPPED);
                    ns.setFinishedAt(Instant.now());
                    for (EdgeState edge : run.outgoingEdges(def.id())) {
                        edge.setStatus(EdgeStatus.INACTIVE);
                    }
                    changed = true;
                }
            }
        }
        repository.save(run);
        }
    }

    private void checkCompletion(RunState run) {
        synchronized (run) {
        if (run.getStatus() != RunStatus.RUNNING) {
            return;
        }
        if (run.allNodesTerminal()) {
            run.setStatus(run.hasFailure() ? RunStatus.FAILED : RunStatus.SUCCEEDED);
            run.setFinishedAt(Instant.now());
            repository.save(run);
        }
        }
    }

    // ------------------------------------------------------------------
    // 内部工具
    // ------------------------------------------------------------------

    private GraphDefinition resolveDefinition(String name, Integer version) {
        if (version != null) {
            return definitions.get(name, version)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "definition not found: " + name + ":" + version));
        }
        return definitions.getLatest(name)
                .orElseThrow(() -> new IllegalArgumentException(
                        "definition not found: " + name));
    }

    private EdgeDefinition findEdge(RunState run, String edgeId) {
        return run.getDefinition().edges().stream()
                .filter(e -> e.edgeId().equals(edgeId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown edge: " + edgeId));
    }

    public RunRepository getRepository() {
        return repository;
    }

    public NodeExecutorRegistry getExecutors() {
        return executors;
    }

    public ExecutorService getExecutorService() {
        return executorService;
    }

    public Optional<RunScheduler> findScheduler(String runId) {
        return Optional.ofNullable(schedulers.get(runId));
    }
}
