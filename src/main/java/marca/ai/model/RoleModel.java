package marca.ai.model;

import java.util.Set;
import java.util.UUID;

public record RoleModel(UUID ID, String email, Set<String> role, Set<UUID> owner, Set<UUID> employee) {
}
