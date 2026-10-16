package com.codebasecopilot;

// Tests never read the developer's config/application.yml or PROJECTS_ROOT (D-029); the roots
// directory and app.data-dir come from TestFileSystemInitializer.
final class TestProperties {

    static final String CONFIG_LOCATION = "spring.config.location=optional:classpath:/";
    static final String NO_PROJECTS_ROOT = "app.projects-root=";

    static final String ROOTS_DIR = "${test.roots-dir}";
    static final String DEFAULT_ROOT_KEY = "app.local-roots[0].key=default";
    static final String DEFAULT_ROOT_LABEL = "app.local-roots[0].label=Projetos";
    static final String DEFAULT_ROOT_PATH = "app.local-roots[0].path=" + ROOTS_DIR + "/default";
    static final String WORK_ROOT_KEY = "app.local-roots[1].key=work";
    static final String WORK_ROOT_LABEL = "app.local-roots[1].label=Trabalho";
    static final String WORK_ROOT_PATH = "app.local-roots[1].path=" + ROOTS_DIR + "/work";

    private TestProperties() {}
}
