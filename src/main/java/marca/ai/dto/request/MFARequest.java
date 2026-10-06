package marca.ai.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.rmi.server.UID;
import java.util.UUID;

public record MFARequest(

        @JsonProperty("id")
        UUID ID,

        String code
){}
