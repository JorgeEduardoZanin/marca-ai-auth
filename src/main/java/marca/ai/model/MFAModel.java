package marca.ai.model;

import java.time.OffsetDateTime;

public record MFAModel(String purpose, String tokenHash, Integer attempts, OffsetDateTime expiresIn) {
}
