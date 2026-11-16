package harmonizer.api;

import harmonizer.screening.DuplicateScreener;
import harmonizer.screening.ScreeningResult;
import harmonizer.service.DatasetStore;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Runs the Bloom-filter screening stage on the loaded dataset.
 *
 * <p>INTEGRATION POINT: {@code ScreeningResult.confirmedPairs()} is what the disjoint-set union
 * stage consumes: for every pair, union(recordId, matchedWithId). Record ids are 1-based.
 */
@RestController
@RequestMapping("/api/screen")
public class ScreeningController {

    private final DatasetStore store;

    public ScreeningController(DatasetStore store) {
        this.store = store;
    }

    /** POST /api/screen?fpr=0.01  (fpr = target Bloom false-positive rate, strictly between 0 and 1). */
    @PostMapping
    public ScreeningResult screen(@RequestParam(defaultValue = "0.01") double fpr) {
        DatasetStore.Dataset dataset = store.require();
        DuplicateScreener screener =
                new DuplicateScreener(r -> dataset.normalizer().signature(r.description()));
        return screener.screen(dataset.records(), fpr);
    }
}
