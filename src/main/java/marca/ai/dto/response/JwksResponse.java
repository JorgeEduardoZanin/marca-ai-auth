package marca.ai.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record JwksResponse(
        @JsonProperty("keys")
        List<JwksKeyResponse> keys
) {
}
