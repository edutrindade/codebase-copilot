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

    // The default test root has one more directory than this inside many/.
    static final String MAX_BROWSE_DIRECTORIES = "app.browse.max-directories=5";

    // Small enough for MockMvc tests to exceed without large files.
    static final String MAX_ZIP_BYTES = "app.import.max-zip-bytes=64KB";

    // The inventory fixture has one file above this limit.
    static final String MAX_INVENTORY_FILE_BYTES = "app.inventory.max-file-bytes=4KB";

    // Short queue intervals so index jobs start and report quickly.
    static final String INDEX_POLL_INTERVAL = "app.indexing.poll-interval=100ms";
    static final String INDEX_HEARTBEAT_INTERVAL = "app.indexing.heartbeat-interval=100ms";
    static final String INDEX_MAX_CONCURRENT = "app.indexing.max-concurrent=2";

    private TestProperties() {}
}
