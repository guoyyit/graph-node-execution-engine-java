package com.github.guoyyit.graphnodeexecutionenginejava.engine.def;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.model.GraphDefinition;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 定义服务：本地版本化存储。定义一经注册不可修改，
 * 运行开始时会把定义快照写入运行状态，因此后续修改不影响已开始的运行。
 */
public class DefinitionService {

    private final Path definitionsDir;
    private final DefinitionValidator validator = new DefinitionValidator();
    private final ObjectMapper mapper;

    public DefinitionService(Path dataDir) {
        this.definitionsDir = dataDir.resolve("definitions");
        this.mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        try {
            Files.createDirectories(definitionsDir);
        } catch (IOException e) {
            throw new IllegalStateException("failed to create definitions dir: " + definitionsDir, e);
        }
    }

    /**
     * 注册定义：整体校验通过后分配新版本号并落盘。
     */
    public synchronized GraphDefinition register(GraphDefinition definition) {
        validator.validate(definition);
        int nextVersion = nextVersion(definition.name());
        GraphDefinition versioned = new GraphDefinition(
                definition.name(), nextVersion, definition.nodes(), definition.edges());
        write(versioned);
        return versioned;
    }

    public Optional<GraphDefinition> get(String name, int version) {
        Path file = definitionsDir.resolve(name + "-" + version + ".json");
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        return Optional.of(read(file));
    }

    public Optional<GraphDefinition> getLatest(String name) {
        return list().stream()
                .filter(d -> d.name().equals(name))
                .max(Comparator.comparingInt(GraphDefinition::version));
    }

    public List<GraphDefinition> list() {
        List<GraphDefinition> result = new ArrayList<>();
        try (var stream = Files.list(definitionsDir)) {
            stream.filter(p -> p.toString().endsWith(".json")).forEach(p -> result.add(read(p)));
        } catch (IOException e) {
            throw new IllegalStateException("failed to list definitions", e);
        }
        result.sort(Comparator.comparing(GraphDefinition::name).thenComparingInt(GraphDefinition::version));
        return result;
    }

    private int nextVersion(String name) {
        int max = 0;
        for (GraphDefinition d : list()) {
            if (d.name().equals(name)) {
                max = Math.max(max, d.version());
            }
        }
        return max + 1;
    }

    private void write(GraphDefinition definition) {
        Path file = definitionsDir.resolve(definition.name() + "-" + definition.version() + ".json");
        try {
            mapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), definition);
        } catch (IOException e) {
            throw new IllegalStateException("failed to write definition: " + file, e);
        }
    }

    private GraphDefinition read(Path file) {
        try {
            return mapper.readValue(file.toFile(), GraphDefinition.class);
        } catch (IOException e) {
            throw new IllegalStateException("failed to read definition: " + file, e);
        }
    }
}
