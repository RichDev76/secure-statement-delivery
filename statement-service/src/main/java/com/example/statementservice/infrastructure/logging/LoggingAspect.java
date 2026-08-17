package com.example.statementservice.infrastructure.logging;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Aspect
@Component
public class LoggingAspect {

    @Pointcut("@within(org.springframework.web.bind.annotation.RestController)")
    public void restControllerBeans() {}

    @Pointcut("@within(org.springframework.stereotype.Service)")
    public void serviceBeans() {}

    @Around("restControllerBeans()")
    public Object logController(ProceedingJoinPoint pjp) throws Throwable {
        var className = pjp.getSignature().getDeclaringTypeName();
        var methodName = pjp.getSignature().getName();
        var start = System.nanoTime();

        if (log.isInfoEnabled()) {
            logInfoEntry(className, methodName);
        }

        try {
            var result = pjp.proceed();
            var tookMs = getTimeTaken(start);

            if (log.isInfoEnabled()) {
                logInfoExit(className, methodName, tookMs);
            }

            if (log.isDebugEnabled()) {
                logDebugExit(className, methodName, result, tookMs);
            }

            return result;
        } catch (Throwable ex) {
            logExceptionWarn(className, methodName, start, ex);
            throw ex;
        }
    }

    @Around("serviceBeans()")
    public Object logService(ProceedingJoinPoint pjp) throws Throwable {
        var className = pjp.getSignature().getDeclaringTypeName();
        var methodName = pjp.getSignature().getName();
        var start = System.nanoTime();

        if (log.isDebugEnabled()) {
            logDebugEntry(pjp, className, methodName);
        }

        try {
            var result = pjp.proceed();
            var tookMs = getTimeTaken(start);

            if (log.isDebugEnabled()) {
                logDebugExit(className, methodName, result, tookMs);
            }

            return result;
        } catch (Throwable ex) {
            logExceptionWarn(className, methodName, start, ex);
            throw ex;
        }
    }

    private long getTimeTaken(long start) {
        return (System.nanoTime() - start) / 1_000_000;
    }

    private String formatArgs(Object[] args) {
        if (args == null || args.length == 0) return "";
        return Arrays.stream(args)
                .map(this::safeToString)
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
    }

    // Type allowlist, not content truncation: strings routinely carry signed-link tokens and
    // account numbers, and printing any content defeats the masking done at the call sites.
    // The allowlist fails closed - a new token-shaped parameter type is summarized by default.
    private String safeToString(Object arg) {
        if (arg == null) return "null";

        if (arg instanceof MultipartFile file) {
            var originalName = file.getOriginalFilename();
            var contentType = file.getContentType();
            long size = -1L;
            try {
                size = file.getSize();
            } catch (Exception ignored) {
            }
            return "MultipartFile[name=" + originalName + ", contentType=" + contentType + ", size=" + size + "]";
        }

        if (arg instanceof CharSequence cs) {
            return "String[len=" + cs.length() + "]";
        }

        if (arg instanceof byte[] bytes) {
            return "byte[" + bytes.length + "]";
        }

        if (arg instanceof Resource) {
            return "Resource[" + ClassUtils.getShortName(arg.getClass()) + "]";
        }

        if (arg instanceof Optional<?> opt) {
            return "Optional[" + (opt.isPresent() ? safeToString(opt.get()) : "empty") + "]";
        }

        if (arg.getClass().isArray()) {
            return arg.getClass().getComponentType().getSimpleName() + "[]";
        }

        if (isValueSafeType(arg)) {
            return String.valueOf(arg);
        }

        String simple = ClassUtils.getShortName(arg.getClass());
        try {
            var idField = arg.getClass().getDeclaredField("id");
            idField.setAccessible(true);
            Object id = idField.get(arg);
            return simple + "{id=" + Objects.toString(id) + "}";
        } catch (NoSuchFieldException | IllegalAccessException ignored) {
        }

        return simple;
    }

    private boolean isValueSafeType(Object arg) {
        return arg instanceof UUID
                || arg instanceof Number
                || arg instanceof Boolean
                || arg.getClass().isEnum()
                || arg.getClass().getPackageName().startsWith("java.time");
    }

    private String summarizeResult(Object result) {
        switch (result) {
            case null -> {
                return "null";
            }
            case ResponseEntity<?> resp -> {
                int status = resp.getStatusCode().value();
                Object body = resp.getBody();
                String bodyType = body == null ? "null" : ClassUtils.getShortName(body.getClass());
                return "ResponseEntity(status=" + status + ", body=" + bodyType + ")";
            }
            case Resource resource -> {
                return "Resource[" + ClassUtils.getShortName(result.getClass()) + "]";
            }
            default -> {}
        }
        return safeToString(result);
    }

    private void logInfoEntry(String className, String methodName) {
        log.info("Entering {}.{}", className, methodName);
    }

    private void logInfoExit(String className, String methodName, long tookMs) {
        log.info("Exiting  {}.{} [OK] ({} ms)", className, methodName, tookMs);
    }

    private void logDebugEntry(ProceedingJoinPoint pjp, String className, String methodName) {
        log.debug("Entering {}.{}({})", className, methodName, formatArgs(pjp.getArgs()));
    }

    private void logDebugExit(String className, String methodName, Object result, long tookMs) {
        log.debug("Exiting  {}.{} -> {} ({} ms)", className, methodName, summarizeResult(result), tookMs);
    }

    private void logExceptionWarn(String className, String methodName, long start, Throwable ex) {
        log.warn("Exception in {}.{} after {} ms: {}", className, methodName, getTimeTaken(start), ex.toString());
    }
}
