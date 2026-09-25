package marca.ai.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public record CreateUserResponse(

        @JsonProperty("id")
        UUID id,

        @JsonProperty("message")
        String message
) {}
