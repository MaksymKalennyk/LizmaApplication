package com.example.lizma.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Translates the {@code message} of successful replies, e.g. {"message": "Post deleted"}. */
@RestControllerAdvice
@RequiredArgsConstructor
public class LocalizedMessageAdvice implements ResponseBodyAdvice<Object> {

    private final MessageTranslator translator;

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType contentType,
                                  Class<? extends HttpMessageConverter<?>> converterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        Locale locale = LocaleContextHolder.getLocale();
        if (body instanceof Map<?, ?> map && map.get("message") instanceof String message
                && MessageTranslator.translates(locale)) {
            Map<Object, Object> translated = new LinkedHashMap<>(map);
            translated.put("message", translator.translate(message, locale));
            return translated;
        }
        return body;
    }
}
