package marca.ai.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public record RefreshTokenModel(UUID ID, UUID userID, UUID familyID, OffsetDateTime authAt, OffsetDateTime expiresIn, OffsetDateTime usedIn, OffsetDateTime revokedOn) {
}
