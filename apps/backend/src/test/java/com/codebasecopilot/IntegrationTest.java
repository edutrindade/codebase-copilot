package com.codebasecopilot;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;

// One shared configuration so Spring caches a single context and PostgreSQL container.
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(
        properties = {
            TestProperties.CONFIG_LOCATION,
            TestProperties.NO_PROJECTS_ROOT,
            TestProperties.DEFAULT_ROOT_KEY,
            TestProperties.DEFAULT_ROOT_LABEL,
            TestProperties.DEFAULT_ROOT_PATH,
            TestProperties.WORK_ROOT_KEY,
            TestProperties.WORK_ROOT_LABEL,
            TestProperties.WORK_ROOT_PATH,
            TestProperties.MAX_BROWSE_DIRECTORIES,
            TestProperties.MAX_ZIP_BYTES,
            TestProperties.MAX_INVENTORY_FILE_BYTES,
            TestProperties.INDEX_POLL_INTERVAL,
            TestProperties.INDEX_HEARTBEAT_INTERVAL,
            TestProperties.INDEX_MAX_CONCURRENT,
            TestProperties.EMBEDDING_PROVIDER,
            TestProperties.NO_EMBEDDING_API_KEY,
            TestProperties.GENERATION_PROVIDER,
            TestProperties.NO_GENERATION_API_KEY,
            TestProperties.NO_GENERATION_PRICE_INPUT,
            TestProperties.NO_GENERATION_PRICE_OUTPUT
        })
@AutoConfigureMockMvc
@ContextConfiguration(initializers = TestFileSystemInitializer.class)
@Import(TestcontainersConfiguration.class)
public @interface IntegrationTest {}
