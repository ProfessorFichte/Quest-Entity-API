package com.qeapi.datagen;

import java.util.LinkedHashMap;
import java.util.Map;

// Collects literal English text registered by QuestProvider builder calls (e.g. .name("Wheat Harvest")),
// keyed by the same translation key Quest.defaultNameKey would look up at runtime. LangProvider reads
// this after re-running quest collection to write the actual lang JSON.
final class LangEntries {

    private static final Map<String, String> ENTRIES = new LinkedHashMap<>();

    private LangEntries() {}

    static void add(String key, String value) {
        ENTRIES.put(key, value);
    }

    static Map<String, String> getAll() {
        return ENTRIES;
    }

    static void clear() {
        ENTRIES.clear();
    }
}
