package com.portfolio.cdc.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Request lifecycle interceptor that logs correlation metadata on every
 * inbound HTTP request and guarantees ThreadLocal cleanup on completion.
 *
 * <p>This CDC service has no multi-tenancy, so no tenant context is managed
 * here. The interceptor is retained as a clean extension point for future
 * request-scoped state (e.g., request-id propagation for distributed tracing).
 *
 * <p><strong>Thread safety:</strong> Spring MVC guarantees that
 * {@code afterCompletion} is called on the same thread as {@code preHandle},
 * so any ThreadLocal written in {@code preHandle} is safely cleared here.
 */
@Component
public class RequestLifecycleInterceptor implements HandlerInterceptor, WebMvcConfigurer {

    private static final Logger log = LoggerFactory.getLogger(RequestLifecycleInterceptor.class);

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) {
        if (log.isDebugEnabled()) {
            log.debug("Inbound {} {} from {}",
                    request.getMethod(),
                    request.getRequestURI(),
                    request.getRemoteAddr());
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request,
                                HttpServletResponse response,
                                Object handler,
                                Exception ex) {
        // Extension point: clear any request-scoped ThreadLocals here.
        // Currently a no-op; retained for future request-id propagation.
        if (ex != null) {
            log.warn("Request {} {} completed with exception: {}",
                    request.getMethod(), request.getRequestURI(), ex.getMessage());
        }
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(this).addPathPatterns("/**");
    }
}
