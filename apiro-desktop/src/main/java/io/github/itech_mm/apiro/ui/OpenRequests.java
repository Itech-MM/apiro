package io.github.itech_mm.apiro.ui;

import io.github.itech_framework.core.annotations.components.levels.Service;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

@Service
public class OpenRequests {

    private Supplier<Set<String>> dirty = Set::of;

    private Consumer<Set<String>> reloader = ids -> {
    };

    public void register(Supplier<Set<String>> dirtyIds, Consumer<Set<String>> reload) {
        dirty = dirtyIds == null ? Set::of : dirtyIds;
        reloader = reload == null ? ids -> {
        } : reload;
    }

    public Set<String> dirtyIds() {
        return dirty.get();
    }

    public void reload(Set<String> ids) {
        reloader.accept(ids);
    }
}
