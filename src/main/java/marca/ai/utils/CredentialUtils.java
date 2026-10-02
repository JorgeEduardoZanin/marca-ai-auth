package marca.ai.utils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public final class CredentialUtils {

    private static final int five = 5;

    private static final int eight = 8;

    private static final int eleven = 11;

    private static final int fourteen = 15;

    private static final int seventeen = 17;

    private static final int twenty = 20;

    public static final OffsetDateTime PERMANENT_BLOCK = OffsetDateTime.of(9999, 12, 31, 23, 59, 59, 0, ZoneOffset.UTC);

    public CredentialUtils() {}

    public static OffsetDateTime calculateBlockedUntil(int attempts) {

        return switch (attempts) {
            case five -> OffsetDateTime.now().plusMinutes(1);
            case eight -> OffsetDateTime.now().plusMinutes(5);
            case eleven -> OffsetDateTime.now().plusMinutes(15);
            case fourteen -> OffsetDateTime.now().plusHours(1);
            case seventeen -> OffsetDateTime.now().plusHours(24);
            case twenty -> PERMANENT_BLOCK;
            default -> null;
        };
    }
}
