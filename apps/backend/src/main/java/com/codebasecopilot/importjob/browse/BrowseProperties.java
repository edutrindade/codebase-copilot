package com.codebasecopilot.importjob.browse;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("app.browse")
record BrowseProperties(@DefaultValue("1000") int maxDirectories) {

    BrowseProperties {
        if (maxDirectories < 1) {
            throw new IllegalStateException("app.browse.max-directories must be at least 1");
        }
    }
}
