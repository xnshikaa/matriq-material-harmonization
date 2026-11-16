package harmonizer.screening;

import java.util.List;

public record ScreeningResult(List<MatchPair> confirmedPairs,
                              List<SkippedRecord> skipped,
                              ScreeningStats stats) {
}
