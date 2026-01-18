package in.annupaper.domain.model;

/**
 * Confluence type based on how many timeframes are in buy zone.
 */
public enum ConfluenceType {
    NONE(0),
    SINGLE(1),
    DOUBLE(2),
    TRIPLE(3);

    private final int count;

    ConfluenceType(int count) {
        this.count = count;
    }

    public int getCount() {
        return count;
    }

    public static ConfluenceType fromCount(int count) {
        return switch (count) {
            case 0 -> NONE;
            case 1 -> SINGLE;
            case 2 -> DOUBLE;
            case 3 -> TRIPLE;
            default -> count > 3 ? TRIPLE : NONE;
        };
    }
}
