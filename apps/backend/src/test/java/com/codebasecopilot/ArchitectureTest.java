package com.codebasecopilot;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import java.util.Arrays;

/** Module boundaries (D-005, D-006, D-031). */
@AnalyzeClasses(
        packages = "com.codebasecopilot",
        importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    private static final String[] DOMAIN_MODULES = {
        "com.codebasecopilot.workspace..",
        "com.codebasecopilot.project..",
        "com.codebasecopilot.importjob..",
        "com.codebasecopilot.indexing..",
        "com.codebasecopilot.codeanalysis..",
        "com.codebasecopilot.retrieval..",
        "com.codebasecopilot.answering..",
        "com.codebasecopilot.providers..",
        "com.codebasecopilot.history..",
    };

    private static final String PROVIDERS = "com.codebasecopilot.providers..";

    private static final String CODE_ANALYSIS = "com.codebasecopilot.codeanalysis..";

    @ArchTest
    static final ArchRule providerLibrariesOnlyInProviders =
            noClasses()
                    .that()
                    .resideOutsideOfPackage(PROVIDERS)
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("org.springframework.ai..", "com.openai..");

    @ArchTest
    static final ArchRule providersDoesNotDependOnDomainModules =
            noClasses()
                    .that()
                    .resideInAPackage(PROVIDERS)
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(
                            Arrays.stream(DOMAIN_MODULES)
                                    .filter(module -> !module.equals(PROVIDERS))
                                    .toArray(String[]::new));

    @ArchTest
    static final ArchRule sharedDoesNotDependOnDomainModules =
            noClasses()
                    .that()
                    .resideInAPackage("com.codebasecopilot.shared..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(DOMAIN_MODULES);

    @ArchTest
    static final ArchRule workspaceDoesNotDependOnProject =
            noClasses()
                    .that()
                    .resideInAPackage("com.codebasecopilot.workspace..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAPackage("com.codebasecopilot.project..");

    @ArchTest
    static final ArchRule codeAnalysisDoesNotDependOnDomainModules =
            noClasses()
                    .that()
                    .resideInAPackage(CODE_ANALYSIS)
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(
                            Arrays.stream(DOMAIN_MODULES)
                                    .filter(module -> !module.equals(CODE_ANALYSIS))
                                    .toArray(String[]::new));

    @ArchTest
    static final ArchRule projectDoesNotDependOnImportJob =
            noClasses()
                    .that()
                    .resideInAPackage("com.codebasecopilot.project..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAPackage("com.codebasecopilot.importjob..");

    @ArchTest
    static final ArchRule onlyIndexingDependsOnIndexing =
            noClasses()
                    .that()
                    .resideInAnyPackage(
                            "com.codebasecopilot.project..",
                            "com.codebasecopilot.importjob..",
                            "com.codebasecopilot.codeanalysis..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAPackage("com.codebasecopilot.indexing..");

    @ArchTest
    static final ArchRule historyDoesNotDependOnAnsweringOrRetrieval =
            noClasses()
                    .that()
                    .resideInAPackage("com.codebasecopilot.history..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(
                            "com.codebasecopilot.answering..", "com.codebasecopilot.retrieval..");

    @ArchTest
    static final ArchRule retrievalDoesNotDependOnAnsweringOrHistory =
            noClasses()
                    .that()
                    .resideInAPackage("com.codebasecopilot.retrieval..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(
                            "com.codebasecopilot.answering..", "com.codebasecopilot.history..");

    @ArchTest
    static final ArchRule onlyAnsweringDependsOnAnswering =
            noClasses()
                    .that()
                    .resideOutsideOfPackage("com.codebasecopilot.answering..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAPackage("com.codebasecopilot.answering..");
}
