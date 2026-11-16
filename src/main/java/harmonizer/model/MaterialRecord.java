package harmonizer.model;

/**
 * One row of a company's material master.
 *
 * @param id          1-based row number assigned on upload (stable handle used by every algorithm)
 * @param cpse        the company (CPSE) the record came from
 * @param code        that company's own material code
 * @param description free-text description
 * @param uom         unit of measure (may be blank)
 */
public record MaterialRecord(int id, String cpse, String code, String description, String uom) {
}
