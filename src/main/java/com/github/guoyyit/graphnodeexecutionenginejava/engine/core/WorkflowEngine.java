package com.github.guoyyit.graphnodeexecutionenginejava.engine.core;

import com.github.guoyyit.graphnodeexecutionenginejava.engine.model.NodeResult;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.model.NodeStatus;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.model.RunSnapshot;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.model.RunStatus;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.model.WorkflowDefinition;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.spi.NodeExecutorRegistry;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.store.DefinitionStore;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.store.RunStore;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.validation.DefinitionValidator;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 工作流引擎门面：提交运行、查询、取消、重启恢复。
 * 所有状态在本地进程内维护并持久化到本地存储，不依赖外部服务。
 */
public class WorkflowEngine implements AutoCloseable {

    private final DefinitionStore definitionStore;
    private final RunStore runStore;
    private final NodeExecutorRegistry executorRegistry;
    private final int maxConcurrency;
    private final ExecutorService attemptExecutor;
    private final Map<String, RunController> activeRuns = new ConcurrentHashMap<>();

    public WorkflowEngine(DefinitionStore definitionStore,
                          RunStore runStore,
                          NodeExecutorRegistry executorRegistry,
                          int maxConcurrency) {
        if (maxConcurrency < 1) {
            throw new IllegalArgumentException("maxConcurrency must be >= 1");
        }
        this.definitionStore = definitionStore;
        this.runStore = runStore;
        this.executorRegistry = executorRegistry;
        this.maxConcurrency = maxConcurrency;
        this.attemptExecutor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "node-attempt");
            t.setDaemon(true);
            return t;
        });
        recoverIncompleteRuns();
    }

    /** 注册新版本定义（整体校验，失败即拒绝）。 */
    public WorkflowDefinition registerDefinition(WorkflowDefinition definition) {
        return definitionStore.register(definition);
    }

    /**
     * 提交一次运行。定义在提交前再次整体校验，
     * 校验失败直接抛异常，不会先执行一部分再失败。
     *
     * @return runId
     */
    public String submit(String definitionId, String version, Map<String, Object> input) {
        WorkflowDefinition definition = definitionStore.find(definitionId, version)
                .orElseThrow(() -> new IllegalArgumentException(
                        "definition not found: " + definitionId + "@" + version));
        DefinitionValidator.validate(definition);

        RunSnapshot snapshot = new RunSnapshot();
        snapshot.setRunId(UUID.randomUUID().toString());
        snapshot.setDefinitionId(definitionId);
        snapshot.setDefinitionVersion(version);
        snapshot.setDefinition(definition);
        snapshot.setInput(input == null ? Map.of() : new LinkedHashMap<>(input));
        Map<String, NodeResult> results = new LinkedHashMap<>();
        definition.nodes().forEach(n -> results.put(n.id(), new NodeResult()));
        snapshot.setNodeResults(results);
        long now = System.currentTimeMillis();
        snapshot.setCreatedAtEpochMs(now);
        snapshot.setUpdatedAtEpochMs(now);
        runStore.save(snapshot);

        startController(snapshot);
        return snapshot.getRunId();
    }

    /** 查询运行快照（含每个节点的状态、输出、错误与尝试次数）。 */
    public Optional<RunSnapshot> getRun(String runId) {
        RunController controller = activeRuns.get(runId);
        if (controller != null) {
            return Optional.of(controller.snapshot());
        }
        return runStore.load(runId);
    }

    /**
     * 请求取消：幂等。取消后不再调度新节点，
     * 正在执行的节点被中断并标记为 CANCELLED。
     */
    public void cancel(String runId) {
        RunController controller = activeRuns.get(runId);
        if (controller != null) {
            controller.requestCancel();
            return;
        }
        // 已结束或未知运行：仅在仍处于运行态时落取消标记，保证幂等与自洽
        runStore.load(runId).ifPresent(snapshot -> {
            if (snapshot.getStatus() == RunStatus.RUNNING && !snapshot.isCancelRequested()) {
                snapshot.setCancelRequested(true);
                runStore.save(snapshot);
            }
        });
    }

    /** 阻塞等待运行到达终态。 */
    public void await(String runId) throws InterruptedException {
        RunController controller = activeRuns.get(runId);
        if (controller != null) {
            controller.await();
        }
    }

    public List<String> listRunIds() {
        return runStore.listRunIds();
    }

    /**
     * 进程重启后恢复：加载所有未完成的运行。
     * 已成功节点保留结果不再执行；崩溃时处于 RUNNING 的节点重置为 PENDING
     * 重新执行（执行器需幂等以保证不产生重复对外效果）；
     * 崩溃前已请求取消的运行继续按取消语义收敛。
     */
    private void recoverIncompleteRuns() {
        for (String runId : runStore.listRunIds()) {
            RunSnapshot snapshot = runStore.load(runId).orElse(null);
            if (snapshot == null || snapshot.getStatus() != RunStatus.RUNNING) {
                continue;
            }
            boolean changed = false;
            for (NodeResult result : snapshot.getNodeResults().values()) {
                if (result.getStatus() == NodeStatus.RUNNING) {
                    result.setStatus(NodeStatus.PENDING);
                    result.setStartedAtEpochMs(null);
                    changed = true;
                }
            }
            if (changed) {
                runStore.save(snapshot);
            }
            startController(snapshot);
        }
    }

    private void startController(RunSnapshot snapshot) {
        RunController controller = new RunController(
                snapshot, runStore, executorRegistry, maxConcurrency, attemptExecutor,
                () -> activeRuns.remove(snapshot.getRunId()));
        activeRuns.put(snapshot.getRunId(), controller);
        controller.start();
    }

    @Override
    public void close() {
        attemptExecutor.shutdownNow();
    }
}
