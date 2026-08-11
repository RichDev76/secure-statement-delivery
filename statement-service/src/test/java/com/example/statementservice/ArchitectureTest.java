package com.example.statementservice;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
import static com.tngtech.archunit.library.freeze.FreezingArchRule.freeze;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import java.io.File;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RestController;

@AnalyzeClasses(packages = ArchitectureTest.ROOT, importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    static final String ROOT = "com.example.statementservice";
    private static final String GENERATED_API = ROOT + ".api..";
    private static final String GENERATED_MODELS = ROOT + ".model.api..";
    private static final String SHARED_INFRASTRUCTURE = ROOT + ".infrastructure..";

    @ArchTest
    static final ArchRule topLevelSlicesAreFreeOfCycles =
            freeze(slices().matching(ROOT + ".(*)..").should().beFreeOfCycles());

    @ArchTest
    static final ArchRule featureDomainCodeDoesNotDependOnInfrastructure = freeze(noClasses()
            .that()
            .resideInAnyPackage(ROOT + ".statement..", ROOT + ".audit..")
            .and()
            .resideOutsideOfPackage("..infrastructure..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..infrastructure.."));

    @ArchTest
    static final ArchRule sharedDependsOnNothingInternal = freeze(noClasses()
            .that()
            .resideInAPackage(ROOT + ".shared..")
            .should()
            .dependOnClassesThat(resideInAPackage(ROOT + "..").and(not(resideInAPackage(ROOT + ".shared..")))));

    @ArchTest
    static final ArchRule restControllersResideInAnInfrastructurePackage = freeze(
            classes().that().areAnnotatedWith(RestController.class).should().resideInAPackage("..infrastructure.."));

    @ArchTest
    static final ArchRule generatedApiTypesAreAccessedOnlyFromAdapters = freeze(noClasses()
            .that()
            .resideOutsideOfPackages(GENERATED_API, GENERATED_MODELS)
            .and()
            .resideOutsideOfPackage("..infrastructure..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(GENERATED_API, GENERATED_MODELS));

    @ArchTest
    static final ArchRule constructorInjectionOnly = freeze(noFields().should().beAnnotatedWith(Autowired.class));

    @ArchTest
    static final ArchRule fileAndCryptoAccessIsConfinedToInfrastructure = freeze(noClasses()
            .that()
            .resideOutsideOfPackage(SHARED_INFRASTRUCTURE)
            .should()
            .dependOnClassesThat(assignableTo(File.class).or(resideInAnyPackage("javax.crypto.."))));
}
