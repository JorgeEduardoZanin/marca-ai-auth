package marca.ai.dto.response;

public record TokenResponse (String token, long duration, boolean mfaActive){}
