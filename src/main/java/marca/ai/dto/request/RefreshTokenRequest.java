package marca.ai.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RefreshTokenRequest (@JsonProperty("token") String rawToken){
}
