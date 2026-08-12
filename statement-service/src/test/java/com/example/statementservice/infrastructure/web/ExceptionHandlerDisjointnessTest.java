package com.example.statementservice.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.statementservice.statement.download.infrastructure.DownloadExceptionHandler;
import com.example.statementservice.statement.infrastructure.StatementExceptionHandler;
import com.example.statementservice.statement.search.infrastructure.SearchExceptionHandler;
import com.example.statementservice.statement.upload.infrastructure.UploadExceptionHandler;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.ExceptionHandler;

class ExceptionHandlerDisjointnessTest {

    private static final List<Class<?>> FEATURE_HANDLER_CLASSES = List.of(
            StatementExceptionHandler.class,
            UploadExceptionHandler.class,
            DownloadExceptionHandler.class,
            SearchExceptionHandler.class);

    @Test
    void
            GivenFeatureOwnedExceptionHandlers_WhenCollectingDeclaredExceptionTypes_ThenNoTypeIsClaimedByMoreThanOneHandler() {
        // Given / When
        Map<Class<?>, List<Class<?>>> handlersByExceptionType = new HashMap<>();
        for (var handlerClass : FEATURE_HANDLER_CLASSES) {
            for (var method : handlerClass.getDeclaredMethods()) {
                var annotation = method.getAnnotation(ExceptionHandler.class);
                if (annotation == null) {
                    continue;
                }
                for (var exceptionType : annotation.value()) {
                    handlersByExceptionType
                            .computeIfAbsent(exceptionType, key -> new ArrayList<>())
                            .add(handlerClass);
                }
            }
        }

        // Then
        var overlaps = handlersByExceptionType.entrySet().stream()
                .filter(entry -> entry.getValue().size() > 1)
                .toList();
        assertThat(overlaps)
                .as("each exception type must be claimed by exactly one feature-owned handler")
                .isEmpty();
    }
}
