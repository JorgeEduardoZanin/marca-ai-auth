package marca.ai.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public record LoginResponse(

        @JsonProperty("raw_token")
        String rawToken,

        @JsonProperty("uri")
        String uri,

        @JsonProperty("mfa_active")
        boolean mfaActive,

        @JsonProperty("expires_in")
        int expiresIn
) {}
