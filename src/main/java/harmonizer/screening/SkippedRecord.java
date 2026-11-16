package harmonizer.screening;

/** A record that could not be screened, with the reason. Never silently dropped. */
public record SkippedRecord(int recordId, String reason) {
}
