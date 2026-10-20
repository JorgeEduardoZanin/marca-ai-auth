package marca.ai.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record JwksKeyResponse(

        @JsonProperty("kty")
        String kty,

        @JsonProperty("use")
        String use,

        @JsonProperty("kid")
        String kid,

        @JsonProperty("alg")
        String alg,

        @JsonProperty("n")
        String n,

        @JsonProperty("e")
        String e
) {
}
