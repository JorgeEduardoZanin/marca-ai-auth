package marca.ai.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;

public record MFARequest(

        @JsonProperty("token")
        String rawToken,

        String code
){}
