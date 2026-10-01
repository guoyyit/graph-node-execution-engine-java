package com.github.guoyyit.graphnodeexecutionenginejava.engine.store;

import com.github.guoyyit.graphnodeexecutionenginejava.engine.model.RunSnapshot;

import java.util.List;
import java.util.Optional;

/** 运行快照持久化接口。 */
public interface RunStore {

    /** 原子地保存（创建或覆盖）运行快照。 */
    void save(RunSnapshot snapshot);

    Optional<RunSnapshot> load(String runId);

    /** 列出全部运行 id，用于进程重启后恢复未完成的运行。 */
    List<String> listRunIds();
}
