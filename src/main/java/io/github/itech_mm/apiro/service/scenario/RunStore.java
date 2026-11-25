package io.github.itech_mm.apiro.service.scenario;

import io.github.itech_mm.apiro.model.RunRecord;
import java.util.List;

public interface RunStore {

    RunStore NONE = new RunStore() {
        @Override
        public List<RunRecord> load(String scenarioId) {
            return List.of();
        }

        @Override
        public List<RunRecord> add(String scenarioId, RunRecord record) {
            return List.of(record);
        }

        @Override
        public void clear(String scenarioId) {
        }
    };

    List<RunRecord> load(String scenarioId);

    List<RunRecord> add(String scenarioId, RunRecord record);

    void clear(String scenarioId);
}
