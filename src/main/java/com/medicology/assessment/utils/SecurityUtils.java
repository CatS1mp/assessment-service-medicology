package com.medicology.assessment.utils;

import com.medicology.assessment.exception.ApiException;
import com.medicology.assessment.wrapper.UserPrincipal;
import java.util.UUID;
import org.springframework.http.HttpStatus;

public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static UUID requireUserId(UserPrincipal principal) {
        if (principal == null || principal.getId() == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, 1401, "Token không chứa mã người dùng hợp lệ.");
        }
        return principal.getId();
    }
}
