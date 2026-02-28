package com.ongard.game.exception;

import com.ongard.game.model.validation.ApiError;
import lombok.Getter;

@Getter
public class ConflictException extends RuntimeException {

    private final ApiError apiError;

    public ConflictException(String message) {
        super(message);
        this.apiError = ApiError.fromMessage(message);
    }

    public ConflictException(String code, String message) {
        super(message);
        this.apiError = ApiError.fromMessage(code, message);
    }
}
