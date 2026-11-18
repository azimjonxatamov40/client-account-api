package com.is.clientaccountapi.model.result;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class ApiResponse<T> {
    private T data;
    private Boolean success;
    private ErrorInfo error;
    @JsonIgnore
    private int httpStatus;

    // Muvaffaqiyatli javob (status beriladi: 200 yoki 201)
    public static <T> ApiResponse<T> ok(T data, int httpStatus) {
        return new ApiResponse<>(data, true, null, httpStatus);
    }

    // Xato javob
    public static <T> ApiResponse<T> error(ErrorCode code, String cause, List<String> fields) {
        ErrorInfo errorInfo = ErrorInfo.builder()
                .code(code.getCode())
                .message(code.name())
                .cause(cause)
                .fields(fields)
                .build();
        return new ApiResponse<>(null, false, errorInfo, code.getHttpStatus());
    }

    public static <T> ApiResponse<T> error(ErrorCode code, List<String> fields) {
        return error(code, code.getCause(), fields);
    }
}