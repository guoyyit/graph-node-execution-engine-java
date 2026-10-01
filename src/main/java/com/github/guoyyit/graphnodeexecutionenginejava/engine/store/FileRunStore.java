package com.github.guoyyit.graphnodeexecutionenginejava.engine.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.model.RunSnapshot;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * 基于本地文件的运行快照存储。每次状态变更后整体落盘，
 * 通过临时文件 + 原子移动保证崩溃时不会留下半写文件。
 */
public class FileRunStore implements RunStore {

    private final Path root;
    private final ObjectMapper objectMapper;

    public FileRunStore(Path root, ObjectMapper objectMapper) {
        this.root = root;
        this.objectMapper = objectMapper;
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public synchronized void save(RunSnapshot snapshot) {
        Path file = fileOf(snapshot.getRunId());
        try {
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            objectMapper.writeValue(tmp.toFile(), snapshot);
            Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public Optional<RunSnapshot> load(String runId) {
        Path file = fileOf(runId);
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(file.toFile(), RunSnapshot.class));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public List<String> listRunIds() {
        try (Stream<Path> files = Files.list(root)) {
            return files.map(f -> f.getFileName().toString())
                    .filter(n -> n.endsWith(".json"))
                    .map(n -> n.substring(0, n.length() - ".json".length()))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Path fileOf(String runId) {
        return root.resolve(runId + ".json");
    }
}
