package com.codebasecopilot;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;

// Second (and only other) cached context: no local roots configured (D-029).
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(properties = {TestProperties.CONFIG_LOCATION, TestProperties.NO_PROJECTS_ROOT})
@AutoConfigureMockMvc
@ContextConfiguration(initializers = TestFileSystemInitializer.class)
@Import(TestcontainersConfiguration.class)
public @interface IntegrationTestWithoutLocalRoots {}
