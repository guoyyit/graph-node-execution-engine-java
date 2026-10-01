package com.github.guoyyit.graphnodeexecutionenginejava.engine.core;

import com.github.guoyyit.graphnodeexecutionenginejava.engine.model.EdgeDefinition;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.model.FailurePolicy;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.model.NodeDefinition;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.model.NodeResult;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.model.NodeStatus;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.model.RunSnapshot;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.model.RunStatus;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.model.WorkflowDefinition;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.spi.NodeContext;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.spi.NodeExecutorRegistry;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.store.RunStore;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.validation.DefinitionValidator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 单运行的调度器：独立线程按依赖关系推进节点，
 * 每次状态变更后持久化快照。调度决策（就绪集合按拓扑序排序）是确定性的，
 * 同样定义与并发度下最终状态与每个节点的可见结果一致。
 */
public class RunController {

    /** 节点任务完成事件。 */
    private record NodeFinished(String nodeId) {
    }

    private final RunSnapshot snapshot;
    private final RunStore runStore;
    private final NodeExecutorRegistry executorRegistry;
    private final int maxConcurrency;
    private final ExecutorService attemptExecutor;
    private final Runnable onFinish;

    private final List<String> topoOrder;
    private final Map<String, Integer> topoIndex;
    private final Map<String, List<EdgeDefinition>> incomingEdges;
    private final BlockingQueue<NodeFinished> completions = new LinkedBlockingQueue<>();
    private final Map<String, Future<?>> inFlight = new ConcurrentHashMap<>();
    private final Map<String, Future<?>> attemptFutures = new ConcurrentHashMap<>();
    private final AtomicBoolean cancelFlag = new AtomicBoolean();
    private final Semaphore permits;

    private Thread schedulerThread;

    public RunController(RunSnapshot snapshot,
                         RunStore runStore,
                         NodeExecutorRegistry executorRegistry,
                         int maxConcurrency,
                         ExecutorService attemptExecutor,
                         Runnable onFinish) {
        this.snapshot = snapshot;
        this.runStore = runStore;
        this.executorRegistry = executorRegistry;
        this.maxConcurrency = maxConcurrency;
        this.attemptExecutor = attemptExecutor;
        this.onFinish = onFinish;
        this.topoOrder = DefinitionValidator.validate(snapshot.getDefinition());
        this.topoIndex = new HashMap<>();
        for (int i = 0; i < topoOrder.size(); i++) {
            topoIndex.put(topoOrder.get(i), i);
        }
        this.incomingEdges = new HashMap<>();
        for (EdgeDefinition edge : snapshot.getDefinition().edges()) {
            incomingEdges.computeIfAbsent(edge.to(), k -> new ArrayList<>()).add(edge);
        }
        this.permits = new Semaphore(maxConcurrency);
        this.cancelFlag.set(snapshot.isCancelRequested());
    }

    public String runId() {
        return snapshot.getRunId();
    }

    /** 启动调度线程。 */
    public void start() {
        schedulerThread = new Thread(this::runLoop, "run-" + snapshot.getRunId());
        schedulerThread.setDaemon(true);
        schedulerThread.start();
    }

    /** 请求取消：幂等，只设置标记并持久化，调度循环负责收敛。 */
    public void requestCancel() {
        if (cancelFlag.compareAndSet(false, true)) {
            synchronized (snapshot) {
                if (snapshot.getStatus() == RunStatus.RUNNING) {
                    snapshot.setCancelRequested(true);
                    persistLocked();
                }
            }
        }
    }

    public RunSnapshot snapshot() {
        return snapshot;
    }

    /** 等待运行到达终态。 */
    public void await() throws InterruptedException {
        Thread t = schedulerThread;
        if (t != null) {
            t.join();
        }
    }

    private void runLoop() {
        try {
            while (true) {
                if (cancelFlag.get()) {
                    interruptInFlightAttempts();
                    drainCompletions();
                    if (inFlight.isEmpty()) {
                        finishCancelled();
                        return;
                    }
                    waitForCompletion();
                    continue;
                }

                drainCompletions();
                dispatchReady();

                if (inFlight.isEmpty() && !hasPendingReadyOrWaiting()) {
                    finishNormally();
                    return;
                }
                if (!inFlight.isEmpty()) {
                    waitForCompletion();
                }
            }
        } catch (Throwable t) {
            synchronized (snapshot) {
                snapshot.setStatus(RunStatus.FAILED);
                snapshot.setFailureReason("scheduler error: " + t);
                persistLocked();
            }
        } finally {
            onFinish.run();
        }
    }

    /** 派发当前就绪的节点，受并发度约束；就绪集合按拓扑序排列保证确定性。 */
    private void dispatchReady() {
        List<String> ready = new ArrayList<>();
        synchronized (snapshot) {
            for (String nodeId : topoOrder) {
                if (permits.availablePermits() <= 0) {
                    break;
                }
                NodeResult result = snapshot.getNodeResults().get(nodeId);
                if (result.getStatus() != NodeStatus.PENDING) {
                    continue;
                }
                List<EdgeDefinition> incoming = incomingEdges.getOrDefault(nodeId, List.of());
                if (incoming.isEmpty()) {
                    ready.add(nodeId);
                    continue;
                }
                Boolean taken = edgeResolution(nodeId, incoming);
                if (taken == null) {
                    continue; // 仍有上游未终态
                }
                if (taken) {
                    ready.add(nodeId);
                } else {
                    result.setStatus(NodeStatus.SKIPPED);
                    result.setFinishedAtEpochMs(System.currentTimeMillis());
                    touchLocked();
                }
            }
            for (String nodeId : ready) {
                if (!permits.tryAcquire()) {
                    break;
                }
                NodeResult result = snapshot.getNodeResults().get(nodeId);
                result.setStatus(NodeStatus.RUNNING);
                result.setStartedAtEpochMs(System.currentTimeMillis());
                persistLocked();
                inFlight.put(nodeId, submitNodeTask(nodeId));
            }
        }
    }

    /**
     * 判断节点入边的解析结果：
     * true=至少一条入边被采用可调度；false=全部解析完但无一采用（跳过）；null=仍有上游未终态。
     */
    private Boolean edgeResolution(String nodeId, List<EdgeDefinition> incoming) {
        boolean anyTaken = false;
        for (EdgeDefinition edge : incoming) {
            NodeResult up = snapshot.getNodeResults().get(edge.from());
            if (up.getStatus() == NodeStatus.PENDING || up.getStatus() == NodeStatus.RUNNING) {
                return null;
            }
            if (up.getStatus() == NodeStatus.SUCCESS
                    && (edge.unconditional()
                        || ConditionEvaluator.evaluate(edge.condition(), up.getOutput()))) {
                anyTaken = true;
            }
        }
        return anyTaken;
    }

    private Future<?> submitNodeTask(String nodeId) {
        CompletableFuture<Void> done = new CompletableFuture<>();
        Thread worker = new Thread(() -> {
            try {
                executeWithRetry(nodeId);
            } finally {
                completions.add(new NodeFinished(nodeId));
                done.complete(null);
            }
        }, "node-" + snapshot.getRunId() + "-" + nodeId);
        worker.setDaemon(true);
        worker.start();
        return done;
    }

    /** 节点级重试与超时：每次尝试在独立 future 中执行以便超时中断。 */
    private void executeWithRetry(String nodeId) {
        WorkflowDefinition definition = snapshot.getDefinition();
        NodeDefinition node = definition.node(nodeId);
        int maxAttempts = node.maxRetries() + 1;
        Throwable lastError = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            if (cancelFlag.get()) {
                markNode(nodeId, NodeStatus.CANCELLED, "cancelled", attempt - 1);
                return;
            }
            Map<String, Object> output;
            try {
                output = runAttempt(node, attempt);
            } catch (Throwable t) {
                lastError = t;
                if (attempt < maxAttempts && !cancelFlag.get()) {
                    sleepBackoff(node.retryBackoffMs() * attempt);
                    continue;
                }
                break;
            }
            // 成功：输出深拷贝为不可变快照，只被采用一次
            synchronized (snapshot) {
                NodeResult result = snapshot.getNodeResults().get(nodeId);
                result.setStatus(NodeStatus.SUCCESS);
                result.setOutput(Collections.unmodifiableMap(
                        output == null ? Map.of() : new LinkedHashMap<>(output)));
                result.setAttempts(attempt);
                result.setFinishedAtEpochMs(System.currentTimeMillis());
                persistLocked();
            }
            return;
        }

        if (cancelFlag.get()) {
            markNode(nodeId, NodeStatus.CANCELLED, "cancelled", maxAttempts);
            return;
        }
        String error = String.valueOf(lastError);
        if (node.failurePolicy() == FailurePolicy.ABORT_RUN) {
            markNode(nodeId, NodeStatus.FAILED, error, maxAttempts);
            synchronized (snapshot) {
                snapshot.setFailureReason("node " + nodeId + " failed: " + error);
                persistLocked();
            }
            cancelFlag.set(true); // 触发整体中止，不再调度新节点
        } else {
            markNode(nodeId, NodeStatus.FAILED, error, maxAttempts);
        }
    }

    private Map<String, Object> runAttempt(NodeDefinition node, int attempt) throws Exception {
        NodeContext context = buildContext(node, attempt);
        Future<Map<String, Object>> future = attemptExecutor.submit(
                () -> executorRegistry.require(node.type()).execute(context));
        attemptFutures.put(node.id(), future);
        try {
            if (node.timeoutMs() > 0) {
                return future.get(node.timeoutMs(), TimeUnit.MILLISECONDS);
            }
            return future.get();
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new NodeTimeoutException(
                    "node " + node.id() + " timed out after " + node.timeoutMs() + "ms");
        } catch (java.util.concurrent.ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception ex) {
                throw ex;
            }
            throw new RuntimeException(cause);
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new RuntimeException("interrupted", e);
        } finally {
            attemptFutures.remove(node.id());
        }
    }

    /** 中断所有在途节点尝试，使其按取消语义尽快收敛。 */
    private void interruptInFlightAttempts() {
        attemptFutures.values().forEach(f -> f.cancel(true));
    }

    private NodeContext buildContext(NodeDefinition node, int attempt) {
        synchronized (snapshot) {
            Map<String, Map<String, Object>> upstream = new LinkedHashMap<>();
            for (EdgeDefinition edge : incomingEdges.getOrDefault(node.id(), List.of())) {
                NodeResult up = snapshot.getNodeResults().get(edge.from());
                if (up.getStatus() == NodeStatus.SUCCESS) {
                    upstream.put(edge.from(), up.getOutput());
                }
            }
            return new NodeContext(
                    snapshot.getRunId(),
                    node.id(),
                    attempt,
                    node.config(),
                    Collections.unmodifiableMap(new LinkedHashMap<>(snapshot.getInput())),
                    Collections.unmodifiableMap(upstream));
        }
    }

    private void markNode(String nodeId, NodeStatus status, String error, int attempts) {
        synchronized (snapshot) {
            NodeResult result = snapshot.getNodeResults().get(nodeId);
            result.setStatus(status);
            result.setError(error);
            result.setAttempts(Math.max(result.getAttempts(), attempts));
            result.setFinishedAtEpochMs(System.currentTimeMillis());
            persistLocked();
        }
    }

    private void drainCompletions() {
        NodeFinished finished;
        while ((finished = completions.poll()) != null) {
            inFlight.remove(finished.nodeId());
            permits.release();
        }
    }

    private void waitForCompletion() {
        try {
            NodeFinished finished = completions.poll(50, TimeUnit.MILLISECONDS);
            if (finished != null) {
                inFlight.remove(finished.nodeId());
                permits.release();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** 是否仍有未终态节点（PENDING 且可能在未来就绪）。 */
    private boolean hasPendingReadyOrWaiting() {
        synchronized (snapshot) {
            return snapshot.getNodeResults().values().stream()
                    .anyMatch(r -> r.getStatus() == NodeStatus.PENDING);
        }
    }

    private void finishNormally() {
        synchronized (snapshot) {
            // 无在途且无 PENDING：将剩余不可达节点标记为跳过（防御性，正常不会存在）
            for (NodeResult result : snapshot.getNodeResults().values()) {
                if (result.getStatus() == NodeStatus.PENDING) {
                    result.setStatus(NodeStatus.SKIPPED);
                    result.setFinishedAtEpochMs(System.currentTimeMillis());
                }
            }
            boolean anyFailed = snapshot.getNodeResults().values().stream()
                    .anyMatch(r -> r.getStatus() == NodeStatus.FAILED);
            snapshot.setStatus(anyFailed ? RunStatus.FAILED : RunStatus.SUCCESS);
            if (anyFailed && snapshot.getFailureReason() == null) {
                List<String> failed = snapshot.getNodeResults().entrySet().stream()
                        .filter(e -> e.getValue().getStatus() == NodeStatus.FAILED)
                        .map(Map.Entry::getKey)
                        .sorted()
                        .toList();
                snapshot.setFailureReason("nodes failed (policy=CONTINUE): " + failed);
            }
            persistLocked();
        }
    }

    private void finishCancelled() {
        synchronized (snapshot) {
            for (NodeResult result : snapshot.getNodeResults().values()) {
                if (result.getStatus() == NodeStatus.PENDING) {
                    result.setStatus(NodeStatus.CANCELLED);
                    result.setFinishedAtEpochMs(System.currentTimeMillis());
                }
            }
            if (snapshot.getFailureReason() != null
                    && snapshot.getNodeResults().values().stream()
                            .anyMatch(r -> r.getStatus() == NodeStatus.FAILED)) {
                snapshot.setStatus(RunStatus.FAILED);
            } else {
                snapshot.setStatus(RunStatus.CANCELLED);
            }
            persistLocked();
        }
    }

    private void sleepBackoff(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void touchLocked() {
        snapshot.setUpdatedAtEpochMs(System.currentTimeMillis());
    }

    private void persistLocked() {
        touchLocked();
        runStore.save(snapshot);
    }

    /** 节点单次尝试超时。 */
    public static class NodeTimeoutException extends RuntimeException {
        public NodeTimeoutException(String message) {
            super(message);
        }
    }
}
