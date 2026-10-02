package marca.ai.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record LoginResponse(

        @JsonProperty("access_token")
        String accessToken,

        @JsonProperty("expires_in")
        long expiresIn,

        @JsonProperty("mfa_active")
        boolean mfaActive,

        @JsonProperty("message")
        String message
) {}
