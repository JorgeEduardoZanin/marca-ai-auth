package marca.ai.model;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

public record LoginModel(UUID id, String password, LocalDateTime emailVerifiedOn, int failedAttempts, LocalDateTime blockedUntil, LocalDateTime passwordChangedOn, boolean mfaActive, Set<String> role, Set<UUID> owner, Set<UUID> employee) {}
