package com.github.guoyyit.graphnodeexecutionenginejava.engine.runtime;

import com.github.guoyyit.graphnodeexecutionenginejava.engine.exec.NodeContext;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.exec.NodeExecutor;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.exec.NodeResult;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.model.EdgeState;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.model.EdgeStatus;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.model.JoinMode;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.model.NodeDefinition;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.model.NodeState;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.model.NodeStatus;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.model.RunState;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.model.RunStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * 单次运行的调度器。每个运行拥有一个调度线程，负责：
 * <ul>
 *   <li>依赖满足后提交节点任务（受运行级并发度限制）</li>
 *   <li>节点超时检测与取消</li>
 *   <li>轮询任务完成情况并驱动边解析与跳过传播</li>
 * </ul>
 *
 * <p>所有对 {@link RunState} 的修改都必须在 {@code synchronized(run)} 块内进行，
 * 以保证状态一致性与持久化快照的完整性。
 */
public class RunScheduler implements Runnable {

    private final RunState run;
    private final ExecutionEngine engine;
    private final Map<Future<?>, String> runningFutures = new ConcurrentHashMap<>();
    private volatile Thread schedulerThread;

    RunScheduler(RunState run, ExecutionEngine engine) {
        this.run = run;
        this.engine = engine;
    }

    public void start() {
        schedulerThread = new Thread(this, "graph-scheduler-" + run.getRunId());
        schedulerThread.start();
    }

    public void interrupt() {
        Thread t = schedulerThread;
        if (t != null) {
            t.interrupt();
        }
    }

    public void cancelRunningTasks() {
        for (Future<?> future : runningFutures.keySet()) {
            future.cancel(true);
        }
    }

    @Override
    public void run() {
        try {
            runLoop();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            // 关闭导致的中断属于“崩溃”，不做终结处理，保留已提交状态
            return;
        }
        if (!engine.isShuttingDown()) {
            finalizeRun();
        }
    }

    private void runLoop() throws InterruptedException {
        while (run.getStatus() == RunStatus.RUNNING) {
            if (engine.isShuttingDown()) {
                return;
            }
            if (run.isCancelRequested()) {
                finalizeCancelled();
                return;
            }
            engine.propagateSkips(run);
            submitReadyNodes();
            if (run.getStatus() != RunStatus.RUNNING) {
                return;
            }
            boolean progressed = awaitProgress(200, TimeUnit.MILLISECONDS);
            if (!progressed && runningFutures.isEmpty()) {
                engine.propagateSkips(run);
                if (run.allNodesTerminal()) {
                    return;
                }
                Thread.sleep(50);
            }
        }
    }

    /**
     * 提交所有依赖已满足、可以执行的节点，直到并发上限。
     */
    private void submitReadyNodes() {
        synchronized (run) {
            for (NodeDefinition def : run.getDefinition().nodes()) {
                if (runningFutures.size() >= run.getConcurrency()) {
                    break;
                }
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
                    engine.propagateSkips(run);
                    continue;
                }
                ns.setStatus(NodeStatus.RUNNING);
                ns.setStartedAt(Instant.now());
                engine.getRepository().save(run);
                Future<?> future = engine.getExecutorService().submit(new NodeTask(def.id()));
                runningFutures.put(future, def.id());
            }
        }
    }

    /**
     * 等待至多 timeout，期间处理已完成任务与超时任务。
     */
    private boolean awaitProgress(long timeout, TimeUnit unit) throws InterruptedException {
        long deadline = System.currentTimeMillis() + unit.toMillis(timeout);
        while (System.currentTimeMillis() < deadline) {
            if (run.isCancelRequested() || run.getStatus() != RunStatus.RUNNING) {
                return true;
            }
            boolean progressed = false;
            for (Iterator<Map.Entry<Future<?>, String>> it = runningFutures.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<Future<?>, String> entry = it.next();
                Future<?> future = entry.getKey();
                String nodeId = entry.getValue();
                if (future.isDone()) {
                    it.remove();
                    progressed = true;
                } else if (isTimedOut(nodeId)) {
                    future.cancel(true);
                    it.remove();
                    progressed = true;
                }
            }
            if (progressed) {
                return true;
            }
            if (runningFutures.isEmpty()) {
                return false;
            }
            Thread.sleep(50);
        }
        return false;
    }

    private boolean isTimedOut(String nodeId) {
        NodeDefinition def = findDefinition(nodeId);
        Duration timeout = def.timeout();
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            return false;
        }
        NodeState ns = run.nodeState(nodeId);
        if (ns.getStartedAt() == null) {
            return false;
        }
        return Duration.between(ns.getStartedAt(), Instant.now()).compareTo(timeout) > 0;
    }

    private void finalizeCancelled() {
        synchronized (run) {
            cancelRunningTasks();
            for (NodeState ns : run.getNodeStates().values()) {
                if (!ns.isTerminal()) {
                    ns.setStatus(NodeStatus.CANCELLED);
                    ns.setFinishedAt(Instant.now());
                }
            }
            run.setStatus(RunStatus.CANCELLED);
            run.setFinishedAt(Instant.now());
            engine.getRepository().save(run);
        }
    }

    private void finalizeRun() {
        synchronized (run) {
            for (NodeState ns : run.getNodeStates().values()) {
                if (!ns.isTerminal()) {
                    ns.setStatus(NodeStatus.CANCELLED);
                    ns.setFinishedAt(Instant.now());
                }
            }
            if (run.getStatus() == RunStatus.RUNNING) {
                run.setStatus(run.hasFailure() ? RunStatus.FAILED : RunStatus.SUCCEEDED);
            }
            if (run.getFinishedAt() == null) {
                run.setFinishedAt(Instant.now());
            }
            engine.getRepository().save(run);
        }
    }

    private NodeDefinition findDefinition(String nodeId) {
        return run.getDefinition().nodes().stream()
                .filter(n -> n.id().equals(nodeId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown node: " + nodeId));
    }

    /**
     * 节点任务：负责执行、重试退避、提交输出、失败/取消标记。
     * 状态修改均在 synchronized(run) 块内，执行在锁外。
     */
    private class NodeTask implements Runnable {

        private final String nodeId;

        NodeTask(String nodeId) {
            this.nodeId = nodeId;
        }

        @Override
        public void run() {
            NodeState ns = run.nodeState(nodeId);
            NodeDefinition def = findDefinition(nodeId);
            NodeExecutor executor = engine.getExecutors().require(def.type());
            int maxAttempts = def.retriesOrDefault() + 1;

            for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                NodeContext context;
                synchronized (run) {
                    if (run.isCancelRequested() || run.getStatus() != RunStatus.RUNNING) {
                        markCancelled(ns);
                        engine.getRepository().save(run);
                        return;
                    }
                    ns.setStatus(NodeStatus.RUNNING);
                    ns.setAttempts(attempt);
                    engine.getRepository().save(run);
                    context = buildContext(attempt);
                }
                try {
                    NodeResult result = executor.execute(context);
                    synchronized (run) {
                        ns.setStatus(NodeStatus.SUCCEEDED);
                        ns.setFinishedAt(Instant.now());
                        run.putOutput(nodeId, result.data());
                        engine.getRepository().save(run);
                        engine.onNodeSucceeded(run, nodeId);
                    }
                    return;
                } catch (Exception e) {
                    if (run.isCancelRequested() || run.getStatus() != RunStatus.RUNNING) {
                        synchronized (run) {
                            markCancelled(ns);
                            engine.getRepository().save(run);
                        }
                        return;
                    }
                    if (engine.isShuttingDown()) {
                        // 模拟进程崩溃：不做终结处理，保留最后提交状态（RUNNING），等待重启恢复
                        return;
                    }
                    if (attempt < maxAttempts) {
                        synchronized (run) {
                            ns.setLastError(e.getMessage());
                            engine.getRepository().save(run);
                        }
                        if (!backoff(def, attempt)) {
                            synchronized (run) {
                                markCancelled(ns);
                                engine.getRepository().save(run);
                            }
                            return;
                        }
                    } else {
                        synchronized (run) {
                            ns.setStatus(NodeStatus.FAILED);
                            ns.setLastError(e.getMessage());
                            ns.setFinishedAt(Instant.now());
                            engine.getRepository().save(run);
                            engine.onNodeFailed(run, nodeId);
                        }
                        return;
                    }
                }
            }
        }

        private NodeContext buildContext(int attempt) {
            Map<String, Map<String, Object>> inputs = new LinkedHashMap<>();
            for (EdgeState edge : run.incomingEdges(nodeId)) {
                if (edge.getStatus() == EdgeStatus.ACTIVE) {
                    inputs.put(edge.getSource(), run.getOutputs().get(edge.getSource()));
                }
            }
            return new NodeContext(run.getRunId(), findDefinition(nodeId), attempt, inputs);
        }

        /**
         * 指数退避：base * 2^(attempt-1)，上限 60s。中断或运行终止返回 false。
         */
        private boolean backoff(NodeDefinition def, int attempt) {
            long baseMs = def.backoffOrDefault().toMillis();
            long delayMs = Math.min(baseMs * (1L << (attempt - 1)), TimeUnit.SECONDS.toMillis(60));
            try {
                Thread.sleep(delayMs);
                return run.getStatus() == RunStatus.RUNNING && !run.isCancelRequested();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }

        private void markCancelled(NodeState ns) {
            if (!ns.isTerminal()) {
                ns.setStatus(NodeStatus.CANCELLED);
                ns.setFinishedAt(Instant.now());
            }
        }
    }
}
