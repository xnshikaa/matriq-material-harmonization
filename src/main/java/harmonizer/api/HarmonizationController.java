package harmonizer.api;

import harmonizer.harmonization.HarmonizationEngine;
import harmonizer.harmonization.HarmonizationPipeline.HarmonizationResult;
import harmonizer.service.DatasetStore;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Runs the harmonization stage (KMP specification matching + attribute verification + DSU grouping)
 * over the currently loaded dataset.
 *
 * <p>INTEGRATION POINT: this is where the algorithms become a product feature. Upload a CSV through
 * {@code POST /api/dataset/upload}, then:
 * <ul>
 *   <li>{@code POST /api/harmonize} — verify relationships and build groups</li>
 *   <li>{@code POST /api/harmonize/reject?recordA=1&recordB=2} — reject one verified relationship and
 *       rebuild the groups from the remaining ones</li>
 * </ul>
 *
 * <p>The response contains the verified edges, the rejected candidates with their reasons, and every
 * group with its original company codes and national material code.
 */
@RestController
@RequestMapping("/api/harmonize")
@org.springframework.context.annotation.Profile("pipeline-api")
public class HarmonizationController {

    private final DatasetStore store;
    private final HarmonizationEngine engine;

    public HarmonizationController(DatasetStore store, HarmonizationEngine engine) {
        this.store = store;
        this.engine = engine;
    }

    /** POST /api/harmonize */
    @PostMapping
    public HarmonizationResult harmonize() {
        return engine.run(store.require().records());
    }

    /** POST /api/harmonize/reject?recordA=1&recordB=2 */
    @PostMapping("/reject")
    public HarmonizationResult reject(@RequestParam int recordA, @RequestParam int recordB) {
        return engine.reject(recordA, recordB);
    }
}
