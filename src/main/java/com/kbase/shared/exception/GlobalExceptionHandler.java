package com.kbase.shared.exception;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import com.kbase.shared.response.ApiErrorResponse;

import jakarta.persistence.PersistenceException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.MissingRequestCookieException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.http.converter.HttpMessageNotReadableException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Central REST translation boundary for known and technical exceptions. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String GLOBAL_ERROR_FIELD = "_global";

    private final ConstraintViolationTranslator constraintViolationTranslator;

    public GlobalExceptionHandler() {
        this(new ConstraintViolationTranslator());
    }

    @Autowired
    public GlobalExceptionHandler(ConstraintViolationTranslator constraintViolationTranslator) {
        this.constraintViolationTranslator = constraintViolationTranslator;
    }

    @ExceptionHandler(KBaseException.class)
    public ResponseEntity<ApiErrorResponse> handleKBaseException(
            KBaseException exception,
            HttpServletRequest request,
            HttpServletResponse response) {
        String requestId = requestId(request, response);
        logKnownException(exception, request, requestId);
        return response(exception.getErrorCode(), exception.getSafeMessage(), null,
                request, response, requestId);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception,
            HttpServletRequest request,
            HttpServletResponse response) {
        return validationResponse(exception.getBindingResult(), request, response);
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiErrorResponse> handleBindException(
            BindException exception,
            HttpServletRequest request,
            HttpServletResponse response) {
        return validationResponse(exception.getBindingResult(), request, response);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(
            ConstraintViolationException exception,
            HttpServletRequest request,
            HttpServletResponse response) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (ConstraintViolation<?> violation : exception.getConstraintViolations()) {
            String field = normalizeField(violation.getPropertyPath() == null
                    ? null
                    : violation.getPropertyPath().toString());
            errors.putIfAbsent(field, safeValidationMessage(violation.getMessage()));
        }
        return validationResponse(errors, request, response);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodValidation(
            HandlerMethodValidationException exception,
            HttpServletRequest request,
            HttpServletResponse response) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (ParameterValidationResult result : exception.getParameterValidationResults()) {
            String field = result.getMethodParameter().getParameterName();
            if (field == null || field.isBlank()) {
                field = "parameter" + result.getMethodParameter().getParameterIndex();
            }
            for (var error : result.getResolvableErrors()) {
                errors.putIfAbsent(field, safeValidationMessage(error.getDefaultMessage()));
            }
        }
        for (var error : exception.getCrossParameterValidationResults()) {
            errors.putIfAbsent(GLOBAL_ERROR_FIELD, safeValidationMessage(error.getDefaultMessage()));
        }
        return validationResponse(errors, request, response);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleMalformedRequestBody(
            HttpMessageNotReadableException exception,
            HttpServletRequest request,
            HttpServletResponse response) {
        return response(ErrorCode.INVALID_REQUEST_BODY, null, null, request, response, null);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodArgumentTypeMismatch(
            MethodArgumentTypeMismatchException exception,
            HttpServletRequest request,
            HttpServletResponse response) {
        return response(ErrorCode.INVALID_PARAMETER, null, null, request, response, null);
    }

    @ExceptionHandler({
            MissingServletRequestParameterException.class,
            MissingPathVariableException.class,
            MissingRequestHeaderException.class,
            MissingRequestCookieException.class,
            MissingServletRequestPartException.class
    })
    public ResponseEntity<ApiErrorResponse> handleMissingRequestValue(
            Exception exception,
            HttpServletRequest request,
            HttpServletResponse response) {
        return response(ErrorCode.INVALID_PARAMETER, null, null, request, response, null);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleMaxUploadSize(
            MaxUploadSizeExceededException exception,
            HttpServletRequest request,
            HttpServletResponse response) {
        return response(ErrorCode.FILE_TOO_LARGE, null, null, request, response, null);
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ApiErrorResponse> handleMultipart(
            MultipartException exception,
            HttpServletRequest request,
            HttpServletResponse response) {
        return response(ErrorCode.INVALID_REQUEST_BODY, null, null, request, response, null);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleUnsupportedMediaType(
            HttpMediaTypeNotSupportedException exception,
            HttpServletRequest request,
            HttpServletResponse response) {
        return response(ErrorCode.UNSUPPORTED_MEDIA_TYPE, null, null, request, response, null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodNotAllowed(
            HttpRequestMethodNotSupportedException exception,
            HttpServletRequest request,
            HttpServletResponse response) {
        return response(ErrorCode.METHOD_NOT_ALLOWED, null, null, request, response, null);
    }

    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<ApiErrorResponse> handleResourceNotFound(
            Exception exception,
            HttpServletRequest request,
            HttpServletResponse response) {
        return response(ErrorCode.RESOURCE_NOT_FOUND, null, null, request, response, null);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleDataIntegrityViolation(
            DataIntegrityViolationException exception,
            HttpServletRequest request,
            HttpServletResponse response) {
        ErrorCode errorCode = constraintViolationTranslator.translate(exception);
        String requestId = requestId(request, response);
        if (errorCode == ErrorCode.INTERNAL_SERVER_ERROR) {
            LOGGER.error("Unrecognized database integrity failure requestId={} path={}",
                    requestId, request.getRequestURI(), exception);
        } else {
            LOGGER.warn("Database integrity rule rejected requestId={} code={}", requestId, errorCode);
        }
        return response(errorCode, null, null, request, response, requestId);
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiErrorResponse> handleDataAccessException(
            DataAccessException exception,
            HttpServletRequest request,
            HttpServletResponse response) {
        return handleTechnicalException(exception, request, response, "Database operation failed");
    }

    @ExceptionHandler({TransactionSystemException.class, PersistenceException.class})
    public ResponseEntity<ApiErrorResponse> handlePersistenceException(
            Exception exception,
            HttpServletRequest request,
            HttpServletResponse response) {
        return handleTechnicalException(exception, request, response, "Persistence operation failed");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnknownException(
            Exception exception,
            HttpServletRequest request,
            HttpServletResponse response) {
        return handleTechnicalException(exception, request, response, "Unexpected application error");
    }

    private ResponseEntity<ApiErrorResponse> validationResponse(
            BindingResult bindingResult,
            HttpServletRequest request,
            HttpServletResponse response) {
        return validationResponse(fieldErrors(bindingResult), request, response);
    }

    private ResponseEntity<ApiErrorResponse> validationResponse(
            Map<String, String> errors,
            HttpServletRequest request,
            HttpServletResponse response) {
        return response(ErrorCode.VALIDATION_ERROR, null, errors, request, response, null);
    }

    private Map<String, String> fieldErrors(BindingResult bindingResult) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError error : bindingResult.getFieldErrors()) {
            errors.putIfAbsent(normalizeField(error.getField()), safeValidationMessage(error.getDefaultMessage()));
        }
        for (ObjectError error : bindingResult.getGlobalErrors()) {
            errors.putIfAbsent(GLOBAL_ERROR_FIELD, safeValidationMessage(error.getDefaultMessage()));
        }
        return errors;
    }

    private ResponseEntity<ApiErrorResponse> handleTechnicalException(
            Exception exception,
            HttpServletRequest request,
            HttpServletResponse response,
            String logMessage) {
        String requestId = requestId(request, response);
        LOGGER.error("{} requestId={} path={}", logMessage, requestId, request.getRequestURI(), exception);
        return response(ErrorCode.INTERNAL_SERVER_ERROR, null, null, request, response, requestId);
    }

    private void logKnownException(KBaseException exception, HttpServletRequest request, String requestId) {
        if (exception instanceof InfrastructureException
                || exception.getErrorCode() == ErrorCode.INTERNAL_SERVER_ERROR) {
            LOGGER.error("Application infrastructure failure requestId={} code={} path={}",
                    requestId, exception.getErrorCode(), request.getRequestURI(), exception);
        } else {
            LOGGER.warn("Request rejected requestId={} code={} path={}",
                    requestId, exception.getErrorCode(), request.getRequestURI());
        }
    }

    private ResponseEntity<ApiErrorResponse> response(
            ErrorCode errorCode,
            String safeMessage,
            Map<String, String> errors,
            HttpServletRequest request,
            HttpServletResponse servletResponse,
            String knownRequestId) {
        String requestId = knownRequestId == null
                ? requestId(request, servletResponse)
                : knownRequestId;
        if (servletResponse != null) {
            servletResponse.setHeader(RequestIdFilter.REQUEST_ID_HEADER, requestId);
        }
        String path = request.getRequestURI() == null ? "" : request.getRequestURI();
        ApiErrorResponse body = new ApiErrorResponse(
                Instant.now(),
                errorCode.getStatus(),
                errorCode.getCode(),
                safeMessage == null || safeMessage.isBlank()
                        ? errorCode.getDefaultMessage()
                        : safeMessage,
                path,
                requestId,
                errors);
        return ResponseEntity.status(errorCode.getHttpStatus()).body(body);
    }

    private String requestId(HttpServletRequest request, HttpServletResponse response) {
        return RequestIdFilter.ensureRequestId(request, response);
    }

    private String normalizeField(String field) {
        return field == null || field.isBlank() ? GLOBAL_ERROR_FIELD : field;
    }

    private String safeValidationMessage(String message) {
        return message == null || message.isBlank() ? "Invalid value." : message;
    }
}
