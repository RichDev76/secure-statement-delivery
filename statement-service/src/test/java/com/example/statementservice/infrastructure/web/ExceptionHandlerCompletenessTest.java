package com.example.statementservice.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.statementservice.statement.download.infrastructure.DownloadExceptionHandler;
import com.example.statementservice.statement.infrastructure.StatementExceptionHandler;
import com.example.statementservice.statement.search.infrastructure.SearchExceptionHandler;
import com.example.statementservice.statement.upload.infrastructure.UploadExceptionHandler;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.ExceptionHandler;

/**
 * Complements {@link ExceptionHandlerDisjointnessTest}: disjointness proves no exception type is
 * claimed twice; this test proves the chain is complete - every map-backed advice has metadata
 * for every handled type, and every production Throwable is claimed by an advice or whitelisted
 * with a rationale.
 */
class ExceptionHandlerCompletenessTest {

    private static final List<Class<?>> ADVICE_CLASSES = List.of(
            StatementExceptionHandler.class,
            UploadExceptionHandler.class,
            DownloadExceptionHandler.class,
            SearchExceptionHandler.class,
            GlobalExceptionHandler.class);

    // Production Throwables legally not claimed by any advice. The value is the rationale; an
    // entry without a real reason is a review flag, not a loophole.
    private static final Map<Class<?>, String> UNCLAIMED_EXCEPTION_WHITELIST = Map.of(
            com.example.statementservice.statement.StatementStorageUnavailableException.class,
            "translated to a DownloadOutcome (STORAGE_UNAVAILABLE) before the web layer",
            com.example.statementservice.statement.FileCipherException.class,
            "wrapped into StatementUploadException on upload; DECRYPTION_FAILED outcome on download");

    // Advices whose handler methods look up response metadata in a private static map keyed by
    // exception class.
    private record MapBackedAdvice(Class<?> adviceClass, String metadataFieldName, String handlerMethodName) {}

    private static final List<MapBackedAdvice> MAP_BACKED_ADVICES = List.of(
            new MapBackedAdvice(
                    UploadExceptionHandler.class, "VALIDATION_EXCEPTION_METADATA", "handleInputValidationExceptions"),
            new MapBackedAdvice(
                    DownloadExceptionHandler.class, "DOWNLOAD_EXCEPTION_METADATA", "handleDownloadExceptions"));

    @Test
    void GivenMapBackedAdvices_WhenComparingHandledTypesToMetadata_ThenEveryHandledTypeHasAMetadataEntry()
            throws Exception {
        for (var advice : MAP_BACKED_ADVICES) {
            // Given
            var handledTypes = handledTypesOfMethod(advice.adviceClass(), advice.handlerMethodName());

            // When
            var metadataField = advice.adviceClass().getDeclaredField(advice.metadataFieldName());
            metadataField.setAccessible(true);
            var metadataKeys = ((Map<?, ?>) metadataField.get(null)).keySet();

            // Then
            assertThat(handledTypes)
                    .as(
                            "%s.%s must have a metadata entry for every handled type (a missing entry "
                                    + "NPEs inside the handler) and no orphan entries",
                            advice.adviceClass().getSimpleName(), advice.metadataFieldName())
                    .containsExactlyInAnyOrderElementsOf((Set<Class<?>>) metadataKeys);
        }
    }

    @Test
    void GivenAllProductionExceptionTypes_WhenCheckedAgainstAdviceChain_ThenEachIsClaimedByAHandlerOrWhitelisted() {
        // Given: every concrete production Throwable
        var productionClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.example.statementservice");
        var productionThrowables = productionClasses.stream()
                .filter(javaClass -> javaClass.isAssignableTo(Throwable.class))
                .map(javaClass -> (Class<?>) javaClass.reflect())
                .filter(clazz -> !Modifier.isAbstract(clazz.getModifiers()))
                .toList();

        // When: collecting every explicitly claimed type across the advice chain (the catch-all
        // Exception.class does not count as a claim - it is the fallback this test guards)
        var claimedTypes = new HashSet<Class<?>>();
        for (var adviceClass : ADVICE_CLASSES) {
            for (var method : adviceClass.getDeclaredMethods()) {
                var annotation = method.getAnnotation(ExceptionHandler.class);
                if (annotation != null) {
                    claimedTypes.addAll(Arrays.asList(annotation.value()));
                }
            }
        }
        claimedTypes.remove(Exception.class);

        // Then
        assertThat(productionThrowables).isNotEmpty().allSatisfy(throwableType -> assertThat(
                        claimedTypes.contains(throwableType)
                                || UNCLAIMED_EXCEPTION_WHITELIST.containsKey(throwableType))
                .as(
                        "%s must be claimed by an @ExceptionHandler in the advice chain or "
                                + "whitelisted here with a rationale - otherwise it reaches "
                                + "clients as an unclassified 500",
                        throwableType.getName())
                .isTrue());
    }

    @Test
    void GivenUnclaimedExceptionWhitelist_WhenCheckedAgainstAdviceChain_ThenNoEntryIsAlsoClaimedByAHandler() {
        // Given / When: a whitelisted type later gaining a handler makes its whitelist entry stale
        var claimedTypes = new HashSet<Class<?>>();
        for (var adviceClass : ADVICE_CLASSES) {
            for (var method : adviceClass.getDeclaredMethods()) {
                var annotation = method.getAnnotation(ExceptionHandler.class);
                if (annotation != null) {
                    claimedTypes.addAll(Arrays.asList(annotation.value()));
                }
            }
        }

        // Then
        assertThat(UNCLAIMED_EXCEPTION_WHITELIST.keySet())
                .as("whitelist entries must be removed once the type gains a handler")
                .doesNotContainAnyElementsOf(claimedTypes);
    }

    private static Set<Class<?>> handledTypesOfMethod(Class<?> adviceClass, String methodName) {
        return Arrays.stream(adviceClass.getDeclaredMethods())
                .filter(method -> method.getName().equals(methodName))
                .map(method -> method.getAnnotation(ExceptionHandler.class))
                .filter(annotation -> annotation != null)
                .flatMap(annotation -> Arrays.stream(annotation.value()))
                .collect(HashSet::new, HashSet::add, HashSet::addAll);
    }
}
