package com.example.statementservice;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.example.statementservice.infrastructure.security.PublicEndpoint;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import java.io.File;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

@AnalyzeClasses(packages = ArchitectureTest.ROOT, importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    static final String ROOT = "com.example.statementservice";
    private static final String GENERATED_API = ROOT + ".api..";
    private static final String GENERATED_MODELS = ROOT + ".model.api..";
    private static final String SHARED_INFRASTRUCTURE = ROOT + ".infrastructure..";

    @ArchTest
    static final ArchRule topLevelSlicesAreFreeOfCycles = slices().matching(ROOT + ".(*)..")
            .should()
            .beFreeOfCycles()
            // Adapters implementing feature-owned ports, and a feature's own adapters depending on
            // shared infrastructure helpers, necessarily cross the top-level slice boundary in both
            // directions. That direction is governed precisely by
            // featureDomainCodeDoesNotDependOnInfrastructure; this rule instead watches for genuine
            // cross-feature cycles (e.g. statement <-> audit).
            .ignoreDependency(resideInAnyPackage("..infrastructure.."), DescribedPredicate.alwaysTrue())
            .ignoreDependency(DescribedPredicate.alwaysTrue(), resideInAnyPackage("..infrastructure.."));

    @ArchTest
    static final ArchRule featureDomainCodeDoesNotDependOnInfrastructure = noClasses()
            .that()
            .resideInAnyPackage(ROOT + ".statement..", ROOT + ".audit..")
            .and()
            .resideOutsideOfPackage("..infrastructure..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..infrastructure..");

    @ArchTest
    static final ArchRule sharedDependsOnNothingInternal = noClasses()
            .that()
            .resideInAPackage(ROOT + ".shared..")
            .should()
            .dependOnClassesThat(resideInAPackage(ROOT + "..").and(not(resideInAPackage(ROOT + ".shared.."))));

    @ArchTest
    static final ArchRule restControllersResideInAnInfrastructurePackage =
            classes().that().areAnnotatedWith(RestController.class).should().resideInAPackage("..infrastructure..");

    @ArchTest
    static final ArchRule generatedApiTypesAreAccessedOnlyFromAdapters = noClasses()
            .that()
            .resideOutsideOfPackages(GENERATED_API, GENERATED_MODELS)
            .and()
            .resideOutsideOfPackage("..infrastructure..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(GENERATED_API, GENERATED_MODELS);

    @ArchTest
    static final ArchRule constructorInjectionOnly = noFields().should().beAnnotatedWith(Autowired.class);

    // The filter chain only floors requests at authenticated(); every handler must state its own decision.
    @ArchTest
    static final ArchRule everyRestHandlerDeclaresItsAuthorization = methods()
            .that()
            .arePublic()
            .and()
            .areDeclaredInClassesThat()
            .areAnnotatedWith(RestController.class)
            .should()
            .beMetaAnnotatedWith(PreAuthorize.class)
            .orShould()
            .beAnnotatedWith(PublicEndpoint.class);

    @ArchTest
    static final ArchRule handlersDeclareExactlyOneAuthorizationDecision =
            methods().that().areMetaAnnotatedWith(PreAuthorize.class).should().notBeAnnotatedWith(PublicEndpoint.class);

    @ArchTest
    static final ArchRule fileCryptoAndObjectStorageAccessIsConfinedToInfrastructure = noClasses()
            .that()
            .resideOutsideOfPackage(SHARED_INFRASTRUCTURE)
            .should()
            .dependOnClassesThat(
                    assignableTo(File.class).or(resideInAnyPackage("javax.crypto..", "software.amazon.awssdk..")));
}
