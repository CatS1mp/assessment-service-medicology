package com.medicology.assessment.exception;

import org.springframework.http.HttpStatus;

public class NotFoundException extends ApiException {

    public NotFoundException(int code, String message) {
        super(HttpStatus.NOT_FOUND, code, message);
    }
}
