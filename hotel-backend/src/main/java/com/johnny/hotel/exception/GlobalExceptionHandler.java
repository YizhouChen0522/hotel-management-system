package com.johnny.hotel.exception;


import com.johnny.hotel.common.Result;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Result<Void> handleMissingResource(Exception e) { return Result.error(404, "Resource not found"); }

    @ExceptionHandler(BusinessException.class)
    public org.springframework.http.ResponseEntity<Result<Void>> handleBusinessException(BusinessException e) {
        int status = e.getCode() >= 400 && e.getCode() <= 599 ? e.getCode() : 400;
        return org.springframework.http.ResponseEntity.status(status).body(Result.error(status, e.getMessage()));
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult()
                .getFieldErrors()
                .get(0)
                .getDefaultMessage();

        return Result.error(400, message);
    }
    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Result<Void> handleAccessDeniedException(AccessDeniedException e) {

        return Result.error(403, "Forbidden");
    }

    @ExceptionHandler({org.springframework.http.converter.HttpMessageNotReadableException.class,
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleMalformed(Exception e) { return Result.error(400, "Invalid request"); }

    @ExceptionHandler(org.springframework.dao.DataAccessException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Result<Void> handleDatabase(Exception e) { return Result.error(409, "Conflicting data or concurrent operation; retry or verify the resource"); }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleException(Exception e) {
        return Result.error(500, "Internal server error");
    }
}

