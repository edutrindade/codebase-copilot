package com.codebasecopilot.shared.openapi;

import com.codebasecopilot.shared.error.ErrorCode;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Domain error codes an endpoint can return, published in the OpenAPI contract. Generic codes are
 * derived automatically by {@link OpenApiConfiguration}.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ApiErrors {

    ErrorCode[] value();
}
