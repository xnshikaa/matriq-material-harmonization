package harmonizer.api;

import harmonizer.algo.trie.Trie;
import harmonizer.model.MaterialRecord;
import harmonizer.service.DatasetStore;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Prefix search over material codes or descriptions, served by the Trie. */
@RestController
@RequestMapping("/api/autocomplete")
public class AutocompleteController {

    private static final int MAX_LIMIT = 50;
    private static final int RECORDS_PER_SUGGESTION = 5;

    public record Suggestion(String text, int matchingRecords, List<MaterialRecord> records) {
    }

    public record AutocompleteResponse(String field, String prefix, List<Suggestion> suggestions,
                                       double elapsedMicros) {
    }

    private final DatasetStore store;

    public AutocompleteController(DatasetStore store) {
        this.store = store;
    }

    /** GET /api/autocomplete?prefix=seam&field=description&limit=10   (field: code | description) */
    @GetMapping
    public AutocompleteResponse suggest(@RequestParam String prefix,
                                        @RequestParam(defaultValue = "description") String field,
                                        @RequestParam(defaultValue = "10") int limit) {
        if (prefix == null || prefix.isBlank()) {
            throw new IllegalArgumentException("prefix must not be blank.");
        }
        if (!field.equals("code") && !field.equals("description")) {
            throw new IllegalArgumentException("field must be 'code' or 'description', got '" + field + "'.");
        }
        int cappedLimit = Math.max(1, Math.min(limit, MAX_LIMIT));

        DatasetStore.Dataset dataset = store.require();
        Trie<List<Integer>> index;
        String searchPrefix;
        if (field.equals("code")) {
            index = dataset.codeIndex();
            searchPrefix = prefix.trim().toLowerCase(Locale.ROOT);
        } else {
            index = dataset.descriptionIndex();
            searchPrefix = dataset.normalizer().clean(prefix);
        }

        long start = System.nanoTime();
        List<Trie.Entry<List<Integer>>> entries =
                searchPrefix.isEmpty() ? List.of() : index.entriesWithPrefix(searchPrefix, cappedLimit);
        double micros = (System.nanoTime() - start) / 1_000.0;

        List<Suggestion> suggestions = new ArrayList<>();
        for (Trie.Entry<List<Integer>> entry : entries) {
            List<MaterialRecord> records = new ArrayList<>();
            for (int id : entry.value()) {
                if (records.size() >= RECORDS_PER_SUGGESTION) {
                    break;
                }
                records.add(dataset.byId().get(id));
            }
            suggestions.add(new Suggestion(entry.key(), entry.value().size(), records));
        }
        return new AutocompleteResponse(field, prefix, suggestions, micros);
    }
}
