package com.example.statementservice.infrastructure.logging;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.statementservice.shared.Identified;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;

class LoggingAspectTest {

    private final LoggingAspect aspect = new LoggingAspect();
    private ListAppender<ILoggingEvent> appender;
    private Logger aspectLogger;
    private Level originalLevel;

    // Fixtures deliberately sit outside the legacy controller/service packages:
    // advice must bind by annotation, independent of package location.
    @RestController
    static class SampleController {
        public String handle() {
            return "handled";
        }
    }

    @Service
    static class SampleService {
        public String work() {
            return "worked";
        }

        public String redeem(String token, UUID linkId) {
            return "secret-plaintext-result";
        }

        public String process(IdentifiedFixture fixture) {
            return "processed";
        }
    }

    static class PlainBean {
        public String run() {
            return "ran";
        }
    }

    record IdentifiedFixture(UUID fixtureId) implements Identified {
        @Override
        public UUID getId() {
            return fixtureId;
        }
    }

    @BeforeEach
    void captureAspectLogs() {
        aspectLogger = (Logger) LoggerFactory.getLogger(LoggingAspect.class);
        originalLevel = aspectLogger.getLevel();
        aspectLogger.setLevel(Level.DEBUG);
        appender = new ListAppender<>();
        appender.start();
        aspectLogger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        aspectLogger.detachAppender(appender);
        aspectLogger.setLevel(originalLevel);
    }

    private <T> T advisedProxy(T target) {
        var factory = new AspectJProxyFactory(target);
        factory.addAspect(aspect);
        return factory.getProxy();
    }

    @Test
    void GivenRestControllerAnnotatedBeanInAnyPackage_WhenMethodInvoked_ThenEntryAndExitAreLogged() {
        var controller = advisedProxy(new SampleController());

        var result = controller.handle();

        assertThat(result).isEqualTo("handled");
        assertThat(appender.list)
                .extracting(ILoggingEvent::getFormattedMessage)
                .anySatisfy(message -> assertThat(message).contains("Entering").contains("SampleController.handle"))
                .anySatisfy(message -> assertThat(message).contains("Exiting").contains("SampleController.handle"));
    }

    @Test
    void GivenServiceAnnotatedBeanInAnyPackage_WhenMethodInvoked_ThenEntryAndExitAreLogged() {
        var service = advisedProxy(new SampleService());

        var result = service.work();

        assertThat(result).isEqualTo("worked");
        assertThat(appender.list)
                .extracting(ILoggingEvent::getFormattedMessage)
                .anySatisfy(message -> assertThat(message).contains("Entering").contains("SampleService.work"))
                .anySatisfy(message -> assertThat(message).contains("Exiting").contains("SampleService.work"));
    }

    @Test
    void GivenUnannotatedBean_WhenMethodInvoked_ThenNothingIsLogged() {
        var bean = advisedProxy(new PlainBean());

        var result = bean.run();

        assertThat(result).isEqualTo("ran");
        assertThat(appender.list).isEmpty();
    }

    @Test
    void GivenServiceMethodWithStringTokenArgument_WhenDebugLogged_ThenTokenContentDoesNotAppearInLogOutput() {
        // Given
        var service = advisedProxy(new SampleService());
        var token = "super-secret-signed-link-token";

        // When
        service.redeem(token, UUID.randomUUID());

        // Then: strings are summarized as lengths, never printed
        assertThat(appender.list)
                .extracting(ILoggingEvent::getFormattedMessage)
                .noneSatisfy(message -> assertThat(message).contains(token));
        assertThat(appender.list)
                .extracting(ILoggingEvent::getFormattedMessage)
                .anySatisfy(message ->
                        assertThat(message).contains("Entering").contains("String[len=" + token.length() + "]"));
    }

    @Test
    void GivenServiceMethodWithUuidArgument_WhenDebugLogged_ThenUuidValueAppears() {
        // Given
        var service = advisedProxy(new SampleService());
        var linkId = UUID.randomUUID();

        // When
        service.redeem("token-value", linkId);

        // Then: UUIDs are on the value allowlist
        assertThat(appender.list)
                .extracting(ILoggingEvent::getFormattedMessage)
                .anySatisfy(message -> assertThat(message).contains(linkId.toString()));
    }

    @Test
    void GivenServiceMethodWithIdentifiedArgument_WhenDebugLogged_ThenTypeAndIdAppearWithoutContents() {
        // Given
        var service = advisedProxy(new SampleService());
        var fixtureId = UUID.randomUUID();

        // When
        service.process(new IdentifiedFixture(fixtureId));

        // Then
        assertThat(appender.list)
                .extracting(ILoggingEvent::getFormattedMessage)
                .anySatisfy(message -> assertThat(message).contains("IdentifiedFixture{id=" + fixtureId + "}"));
    }

    @Test
    void GivenServiceMethodReturningString_WhenDebugLogged_ThenReturnContentIsSummarizedNotPrinted() {
        // Given
        var service = advisedProxy(new SampleService());

        // When
        service.redeem("token-value", UUID.randomUUID());

        // Then
        assertThat(appender.list)
                .extracting(ILoggingEvent::getFormattedMessage)
                .noneSatisfy(message -> assertThat(message).contains("secret-plaintext-result"));
        assertThat(appender.list)
                .extracting(ILoggingEvent::getFormattedMessage)
                .anySatisfy(message -> assertThat(message).contains("Exiting").contains("String[len="));
    }
}
