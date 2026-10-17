package com.codebasecopilot;

// Tests never read the developer's config/application.yml or PROJECTS_ROOT (D-029); the roots
// directory and app.data-dir come from TestFileSystemInitializer.
public final class TestProperties {

    public static final String CONFIG_LOCATION = "spring.config.location=optional:classpath:/";
    public static final String NO_PROJECTS_ROOT = "app.projects-root=";

    public static final String ROOTS_DIR = "${test.roots-dir}";
    public static final String DEFAULT_ROOT_KEY = "app.local-roots[0].key=default";
    public static final String DEFAULT_ROOT_LABEL = "app.local-roots[0].label=Projetos";
    public static final String DEFAULT_ROOT_PATH =
            "app.local-roots[0].path=" + ROOTS_DIR + "/default";
    public static final String WORK_ROOT_KEY = "app.local-roots[1].key=work";
    public static final String WORK_ROOT_LABEL = "app.local-roots[1].label=Trabalho";
    public static final String WORK_ROOT_PATH = "app.local-roots[1].path=" + ROOTS_DIR + "/work";

    // The default test root has one more directory than this inside many/.
    public static final String MAX_BROWSE_DIRECTORIES = "app.browse.max-directories=5";

    // Small enough for MockMvc tests to exceed without large files.
    public static final String MAX_ZIP_BYTES = "app.import.max-zip-bytes=64KB";

    // The inventory fixture has one file above this limit.
    public static final String MAX_INVENTORY_FILE_BYTES = "app.inventory.max-file-bytes=4KB";

    // Short queue intervals so index jobs start and report quickly.
    public static final String INDEX_POLL_INTERVAL = "app.indexing.poll-interval=100ms";
    public static final String INDEX_HEARTBEAT_INTERVAL = "app.indexing.heartbeat-interval=100ms";
    public static final String INDEX_MAX_CONCURRENT = "app.indexing.max-concurrent=2";

    // application.yml imports the developer's .env; tests never call a real provider (D-047).
    public static final String EMBEDDING_PROVIDER = "app.embeddings.provider=fake";
    public static final String NO_EMBEDDING_API_KEY = "app.embeddings.api-key=";
    public static final String GENERATION_PROVIDER = "app.generation.provider=fake";
    public static final String NO_GENERATION_API_KEY = "app.generation.api-key=";

    private TestProperties() {}
}
