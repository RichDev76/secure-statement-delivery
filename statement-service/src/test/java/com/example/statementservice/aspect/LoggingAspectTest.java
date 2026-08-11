package com.example.statementservice.aspect;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
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
    }

    static class PlainBean {
        public String run() {
            return "ran";
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
}
