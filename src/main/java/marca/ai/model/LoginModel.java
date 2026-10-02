package marca.ai.model;

import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

public record LoginModel(UUID ID, String password, OffsetDateTime emailVerifiedOn, OffsetDateTime blockedUntil, OffsetDateTime passwordChangedOn, boolean mfaActive, Set<String> role, Set<UUID> owner, Set<UUID> employee) {}
