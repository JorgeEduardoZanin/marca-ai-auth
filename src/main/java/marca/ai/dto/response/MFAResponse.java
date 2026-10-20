package marca.ai.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record MFAResponse(

        @JsonProperty("refresh_token")
        String refreshToken,

        @JsonProperty("token")
        String token,

        @JsonProperty("expires_in")
        long expiresIn)
{

    public static MFAResponse toMFAResponse (String refreshToken, TokenResponse tokenResponse) {

        return new MFAResponse(
                refreshToken,
                tokenResponse.token(),
                tokenResponse.duration()
        );
    }
}
