package marca.ai.model;

import java.util.Set;
import java.util.UUID;

public record MFAModel(UUID ID, String email, Integer attempts, String secretCipher, Set<String> role, Set<UUID> owner, Set<UUID> employee) {
}
