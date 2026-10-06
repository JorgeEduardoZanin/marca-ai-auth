package marca.ai.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record MFAResponse(

        @JsonProperty("uri")
        String uri,

        @JsonProperty("mfa_active")
        boolean mfaActive,

        @JsonProperty("expires_in")
        long expiresIn)
{}
