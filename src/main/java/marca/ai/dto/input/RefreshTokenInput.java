package marca.ai.dto.input;

import marca.ai.enums.AuthMethod;
import marca.ai.enums.ExpireDates;
import marca.ai.utils.HashUtils;

import java.time.OffsetDateTime;
import java.util.UUID;

public record RefreshTokenInput (UUID userID, UUID familyID, String tokenHash, String[] authMethod, OffsetDateTime authAt, OffsetDateTime expireIn, OffsetDateTime createdAt, String ipCreation, String userAgent){

    public static RefreshTokenInput toRefreshTokenInput (UUID userID, UUID familyID, String tokenHash, OffsetDateTime authAt, String ip, String userAgent) {

        return new RefreshTokenInput(
                userID,
                familyID,
                tokenHash,
                new String[]{AuthMethod.PWD.getMethod(), AuthMethod.OTP.getMethod()},
                authAt,
                OffsetDateTime.now().plusDays(ExpireDates.EXPIRE_IN_SEVEN_DAYS.getExpire()),
                OffsetDateTime.now(),
                ip,
                userAgent
        );
    }

}
