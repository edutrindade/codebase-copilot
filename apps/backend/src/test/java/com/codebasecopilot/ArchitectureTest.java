package com.codebasecopilot;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** Module boundaries (D-005, D-031). */
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
    static final ArchRule projectDoesNotDependOnImportJob =
            noClasses()
                    .that()
                    .resideInAPackage("com.codebasecopilot.project..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAPackage("com.codebasecopilot.importjob..");
}
