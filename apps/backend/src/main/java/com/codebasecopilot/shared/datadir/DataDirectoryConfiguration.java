package com.codebasecopilot.shared.datadir;

import com.codebasecopilot.shared.localroots.LocalRoots;
import java.nio.file.Path;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(DataDirectoryProperties.class)
class DataDirectoryConfiguration {

    @Bean
    DataDirectory dataDirectory(DataDirectoryProperties properties, LocalRoots roots) {
        return DataDirectory.from(
                properties.dataDir(),
                roots,
                Path.of(System.getProperty("user.home")),
                Path.of(System.getProperty("user.dir")));
    }
}
