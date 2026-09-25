package marca.ai.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CEPResponse(
        @JsonProperty("state")
        String state,

        @JsonProperty("city")
        String city,

        @JsonProperty("neighborhood")
        String neighborhood,

        @JsonProperty("street")
        String street
) {}
