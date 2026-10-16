package com.codebasecopilot.shared.error;

import static java.util.Comparator.comparing;

import com.codebasecopilot.shared.web.RequestIdFilter;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.context.MessageSource;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.Errors;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    static final Locale LOCALE = Locale.of("pt", "BR");
    static final String REQUIRED = "validation.required";
    static final String INVALID_VALUE = "validation.invalid-value";

    private static final URI ABOUT_BLANK = URI.create("about:blank");
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final MessageSource messages;

    public GlobalExceptionHandler(MessageSource messages) {
        this.messages = messages;
    }

    @ExceptionHandler(ApiException.class)
    ResponseEntity<Object> handleApiException(ApiException ex, WebRequest request) {
        List<FieldErrorItem> errors =
                ex.violations().stream()
                        .map(v -> new FieldErrorItem(v.field(), message(v.messageKey())))
                        .sorted(comparing(FieldErrorItem::field))
                        .toList();
        return problem(ex.code(), errors, new HttpHeaders(), request);
    }

    // MaxUploadSizeExceededException is more specific and handled by the base class.
    @ExceptionHandler(MultipartException.class)
    ResponseEntity<Object> handleMultipart(MultipartException ex, WebRequest request) {
        return problem(ErrorCode.MALFORMED_REQUEST, List.of(), new HttpHeaders(), request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> handleUnexpected(Exception ex, WebRequest request) {
        log.error("Unhandled exception", ex);
        return problem(ErrorCode.INTERNAL_ERROR, List.of(), new HttpHeaders(), request);
    }

    @Override
    protected @Nullable ResponseEntity<Object> handleExceptionInternal(
            Exception ex,
            @Nullable Object body,
            HttpHeaders headers,
            HttpStatusCode statusCode,
            WebRequest request) {
        if (request instanceof ServletWebRequest servletRequest
                && servletRequest.getResponse() != null
                && servletRequest.getResponse().isCommitted()) {
            return null;
        }
        ErrorCode code = codeFor(ex, statusCode);
        if (code == ErrorCode.INTERNAL_ERROR) {
            log.error("Request failed", ex);
        }
        return problem(code, fieldErrors(ex), headers, request);
    }

    private static ErrorCode codeFor(Exception ex, HttpStatusCode status) {
        return switch (ex) {
            case MethodArgumentNotValidException _,
                    HandlerMethodValidationException _,
                    TypeMismatchException _,
                    MissingServletRequestParameterException _ ->
                    ErrorCode.VALIDATION_FAILED;
            case HttpMessageNotReadableException _ -> ErrorCode.MALFORMED_REQUEST;
            case NoResourceFoundException _, NoHandlerFoundException _ ->
                    ErrorCode.RESOURCE_NOT_FOUND;
            case HttpRequestMethodNotSupportedException _ -> ErrorCode.METHOD_NOT_ALLOWED;
            case HttpMediaTypeNotSupportedException _ -> ErrorCode.UNSUPPORTED_MEDIA_TYPE;
            case MaxUploadSizeExceededException _ -> ErrorCode.UPLOAD_TOO_LARGE;
            // Remaining framework errors keep a generic code for their status class.
            default ->
                    status.is5xxServerError()
                            ? ErrorCode.INTERNAL_ERROR
                            : ErrorCode.MALFORMED_REQUEST;
        };
    }

    private List<FieldErrorItem> fieldErrors(Exception ex) {
        Stream<FieldErrorItem> items =
                switch (ex) {
                    case MethodArgumentNotValidException e -> errors(e.getBindingResult());
                    case HandlerMethodValidationException e ->
                            e.getParameterValidationResults().stream()
                                    .flatMap(this::parameterErrors);
                    case TypeMismatchException e ->
                            Stream.of(
                                    new FieldErrorItem(
                                            Objects.requireNonNullElse(e.getPropertyName(), ""),
                                            message(INVALID_VALUE)));
                    case MissingServletRequestParameterException e ->
                            Stream.of(new FieldErrorItem(e.getParameterName(), message(REQUIRED)));
                    default -> Stream.empty();
                };
        return items.sorted(comparing(FieldErrorItem::field).thenComparing(FieldErrorItem::message))
                .toList();
    }

    private Stream<FieldErrorItem> parameterErrors(ParameterValidationResult result) {
        if (result instanceof ParameterErrors errors) {
            return errors(errors);
        }
        String field =
                Objects.requireNonNullElse(result.getMethodParameter().getParameterName(), "");
        return result.getResolvableErrors().stream()
                .map(error -> new FieldErrorItem(field, resolve(error)));
    }

    private Stream<FieldErrorItem> errors(Errors errors) {
        Stream<FieldErrorItem> fieldErrors =
                errors.getFieldErrors().stream()
                        .map(
                                error ->
                                        new FieldErrorItem(
                                                error.getField(),
                                                error.isBindingFailure()
                                                        ? message(INVALID_VALUE)
                                                        : resolve(error)));
        Stream<FieldErrorItem> globalErrors =
                errors.getGlobalErrors().stream()
                        .map(error -> new FieldErrorItem(error.getObjectName(), resolve(error)));
        return Stream.concat(fieldErrors, globalErrors);
    }

    /** Bean Validation messages are already interpolated from {@code messages.properties}. */
    private String resolve(MessageSourceResolvable error) {
        String message = error.getDefaultMessage();
        return message != null ? message : message(INVALID_VALUE);
    }

    private ResponseEntity<Object> problem(
            ErrorCode code, List<FieldErrorItem> errors, HttpHeaders headers, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatus(code.status());
        // Spring omits the default type; the contract always shows it (06-API-CONTRACTS
        // §2).
        problem.setType(ABOUT_BLANK);
        problem.setTitle(message("error." + code.name() + ".title"));
        problem.setDetail(message("error." + code.name() + ".detail"));
        problem.setProperty("code", code.name());
        problem.setProperty(
                "requestId",
                request.getAttribute(RequestIdFilter.ATTRIBUTE, RequestAttributes.SCOPE_REQUEST));
        if (!errors.isEmpty()) {
            problem.setProperty("errors", errors);
        }
        return new ResponseEntity<>(problem, headers, code.status());
    }

    private String message(String key) {
        return messages.getMessage(key, null, LOCALE);
    }
}
