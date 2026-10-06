package marca.ai.enums;

public enum MFAPurpose {

    LOGIN("LOGIN"),
    STEP_UP("STEP_UP");

    private final String purpose;

    MFAPurpose(String purpose) {
        this.purpose = purpose;
    }

    public String getPurpose() {
        return purpose;
    }
}
