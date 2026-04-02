package com.medicology.assessment.exception;

import org.springframework.http.HttpStatus;

public class ConflictException extends ApiException {

    public ConflictException(int code, String message) {
        super(HttpStatus.CONFLICT, code, message);
    }
}
