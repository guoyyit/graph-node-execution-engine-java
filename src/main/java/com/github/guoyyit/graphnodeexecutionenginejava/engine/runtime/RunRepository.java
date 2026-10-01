package com.github.guoyyit.graphnodeexecutionenginejava.engine.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.model.RunState;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 运行状态持久化：每次状态提交都以临时文件 + 原子移动的方式落盘，
 * 进程重启后可从最后一个已提交位置恢复。
 */
public class RunRepository {

    private final Path runsDir;
    private final ObjectMapper mapper;

    public RunRepository(Path dataDir) {
        this.runsDir = dataDir.resolve("runs");
        this.mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        try {
            Files.createDirectories(runsDir);
        } catch (IOException e) {
            throw new IllegalStateException("failed to create runs dir: " + runsDir, e);
        }
    }

    public void save(RunState state) {
        synchronized (state) {
            Path target = runsDir.resolve(state.getRunId() + ".json");
            // 使用唯一临时文件名，避免并发保存同一运行时的移动冲突
            Path tmp = runsDir.resolve(state.getRunId() + "." + System.nanoTime() + ".json.tmp");
            try {
                mapper.writerWithDefaultPrettyPrinter().writeValue(tmp.toFile(), state);
                Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                throw new IllegalStateException("failed to persist run state: " + state.getRunId(), e);
            }
        }
    }

    public RunState load(String runId) {
        Path file = runsDir.resolve(runId + ".json");
        if (!Files.exists(file)) {
            return null;
        }
        try {
            return mapper.readValue(file.toFile(), RunState.class);
        } catch (IOException e) {
            throw new IllegalStateException("failed to load run state: " + runId, e);
        }
    }

    public boolean exists(String runId) {
        return Files.exists(runsDir.resolve(runId + ".json"));
    }

    public List<String> listRunIds() {
        List<String> ids = new ArrayList<>();
        try (var stream = Files.list(runsDir)) {
            stream.filter(p -> p.toString().endsWith(".json"))
                    .forEach(p -> ids.add(p.getFileName().toString().replace(".json", "")));
        } catch (IOException e) {
            throw new IllegalStateException("failed to list runs", e);
        }
        ids.sort(Comparator.naturalOrder());
        return ids;
    }

    public List<RunState> listAll() {
        List<RunState> result = new ArrayList<>();
        for (String id : listRunIds()) {
            RunState state = load(id);
            if (state != null) {
                result.add(state);
            }
        }
        return result;
    }
}
