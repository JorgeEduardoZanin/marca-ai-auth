package marca.ai.exception.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record ErrorsResponse(
        @JsonProperty("errors")
        List<String> errors
) {}
