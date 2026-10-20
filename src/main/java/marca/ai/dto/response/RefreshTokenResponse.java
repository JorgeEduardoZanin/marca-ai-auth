package marca.ai.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RefreshTokenResponse(

        @JsonProperty("refresh_token")
        String refreshToken,

        @JsonProperty("token")
        String token,

        @JsonProperty("expires_in")
        long expiresIn)
{}
