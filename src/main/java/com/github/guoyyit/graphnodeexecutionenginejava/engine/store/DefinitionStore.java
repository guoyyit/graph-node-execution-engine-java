package com.github.guoyyit.graphnodeexecutionenginejava.engine.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.model.WorkflowDefinition;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.validation.DefinitionValidator;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * 基于本地文件的版本化定义存储。
 * 目录布局：{@code <root>/<definitionId>/<version>.json}。
 * 定义写入前整体校验；同一 id 的多个版本可并存；已写入版本不可覆盖。
 */
public class DefinitionStore {

    private final Path root;
    private final ObjectMapper objectMapper;

    public DefinitionStore(Path root, ObjectMapper objectMapper) {
        this.root = root;
        this.objectMapper = objectMapper;
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** 注册新版本定义。先整体校验，同 id+version 已存在时拒绝覆盖。 */
    public synchronized WorkflowDefinition register(WorkflowDefinition definition) {
        DefinitionValidator.validate(definition);
        Path file = fileOf(definition.id(), definition.version());
        if (Files.exists(file)) {
            throw new IllegalStateException("definition already exists: "
                    + definition.id() + "@" + definition.version());
        }
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            objectMapper.writeValue(tmp.toFile(), definition);
            Files.move(tmp, file, java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return definition;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public Optional<WorkflowDefinition> find(String id, String version) {
        Path file = fileOf(id, version);
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(file.toFile(), WorkflowDefinition.class));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public List<String> versions(String id) {
        Path dir = root.resolve(id);
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(dir)) {
            return files.map(f -> f.getFileName().toString())
                    .filter(n -> n.endsWith(".json"))
                    .map(n -> n.substring(0, n.length() - ".json".length()))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Path fileOf(String id, String version) {
        return root.resolve(id).resolve(version + ".json");
    }
}
