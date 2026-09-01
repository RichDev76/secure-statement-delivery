package com.example.statementservice;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;

@AnalyzeClasses(packages = ArchitectureTest.ROOT, importOptions = ImportOption.OnlyIncludeTests.class)
class TestNamingConventionTest {

    @ArchTest
    static final ArchRule testMethodsFollowGivenWhenThenNaming = methods()
            .that()
            .areAnnotatedWith(Test.class)
            .or()
            .areAnnotatedWith(ParameterizedTest.class)
            .should()
            .haveNameMatching("Given[a-zA-Z0-9]+_When[a-zA-Z0-9]+_Then[a-zA-Z0-9]+")
            .because("the testing standard mandates Given_When_Then names that read as behavioural statements");
}
