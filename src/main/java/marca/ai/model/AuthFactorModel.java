package marca.ai.model;

public record AuthFactorModel(String secretCipher, boolean mfaActive, String email) {
}
