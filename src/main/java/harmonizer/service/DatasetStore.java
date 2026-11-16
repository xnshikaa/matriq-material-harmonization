package harmonizer.service;

import harmonizer.algo.trie.Trie;
import harmonizer.model.MaterialRecord;
import harmonizer.normalize.TextNormalizer;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Holds the most recently uploaded dataset in memory (the project needs no database) together
 * with the two Trie indexes used for autocomplete.
 */
@Component
public class DatasetStore {

    /**
     * @param codeIndex        trie over lower-cased material codes -> record ids
     * @param descriptionIndex trie over cleaned descriptions -> record ids
     */
    public record Dataset(List<MaterialRecord> records,
                          Map<Integer, MaterialRecord> byId,
                          Trie<List<Integer>> codeIndex,
                          Trie<List<Integer>> descriptionIndex,
                          TextNormalizer normalizer,
                          List<String> warnings,
                          int totalWarnings) {
    }

    private final AtomicReference<Dataset> current = new AtomicReference<>();

    /** Replaces the current dataset and (re)builds the indexes. */
    public Dataset load(List<MaterialRecord> records, List<String> warnings, int totalWarnings) {
        TextNormalizer normalizer = TextNormalizer.withDefaults();
        Trie<List<Integer>> codeIndex = new Trie<>();
        Trie<List<Integer>> descriptionIndex = new Trie<>();
        Map<Integer, MaterialRecord> byId = new HashMap<>();

        for (MaterialRecord r : records) {
            byId.put(r.id(), r);
            addToIndex(codeIndex, r.code().trim().toLowerCase(Locale.ROOT), r.id());
            addToIndex(descriptionIndex, normalizer.clean(r.description()), r.id());
        }
        Dataset dataset = new Dataset(List.copyOf(records), byId, codeIndex, descriptionIndex,
                normalizer, List.copyOf(warnings), totalWarnings);
        current.set(dataset);
        return dataset;
    }

    /** The loaded dataset, or an IllegalStateException (HTTP 409) if nothing was uploaded yet. */
    public Dataset require() {
        Dataset dataset = current.get();
        if (dataset == null) {
            throw new IllegalStateException("No dataset is loaded yet. Upload a CSV first.");
        }
        return dataset;
    }

    private static void addToIndex(Trie<List<Integer>> index, String key, int recordId) {
        if (key.isEmpty()) {
            return;
        }
        String safeKey = key.length() > Trie.MAX_KEY_LENGTH ? key.substring(0, Trie.MAX_KEY_LENGTH) : key;
        List<Integer> ids = index.get(safeKey);
        if (ids == null) {
            ids = new ArrayList<>(1);
            index.put(safeKey, ids);
        }
        ids.add(recordId);
    }
}
