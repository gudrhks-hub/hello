package com.shop.api.common.error;

import com.shop.api.config.AppConfig;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

public record ErrorResponse(
        int status,
        String code,
        String message,
        String path,
        OffsetDateTime timestamp,
        List<FieldErrorDetail> errors) {

    public static ErrorResponse of(ErrorCode code, String path) {
        return of(code, path, List.of());
    }

    public static ErrorResponse of(ErrorCode code, String path, List<FieldErrorDetail> errors) {
        return new ErrorResponse(
                code.getStatus().value(),
                code.name(),
                code.getMessage(),
                path,
                OffsetDateTime.now(AppConfig.ZONE).truncatedTo(ChronoUnit.SECONDS),
                errors == null ? List.of() : errors);
    }
}
