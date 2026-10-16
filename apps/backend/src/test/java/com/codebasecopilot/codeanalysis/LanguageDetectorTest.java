package com.codebasecopilot.codeanalysis;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

class LanguageDetectorTest {

    @ParameterizedTest
    @CsvSource({
        "Main.java,         java",
        "App.TSX,           typescript",
        "index.mjs,         javascript",
        "build.gradle.kts,  kotlin",
        "pom.xml,           xml",
        "README.md,         markdown",
        "values.yml,        yaml",
        "Dockerfile,        dockerfile",
        "Dockerfile.dev,    dockerfile",
        "Makefile,          makefile",
        "CMakeLists.txt,    cmake",
        "Gemfile,           ruby",
        "notes.txt,         text",
        "LICENSE,           text",
        ".bashrc,           text",
        "archive.unknown,   text"
    })
    void detectsByNameAndExtension(String fileName, String language) {
        assertThat(LanguageDetector.detect(fileName, new byte[0])).isEqualTo(language);
    }

    static Stream<Arguments> shebangs() {
        return Stream.of(
                Arguments.of("#!/usr/bin/env python3\nprint(1)", "python"),
                Arguments.of("#!/bin/bash -e\necho", "shell"),
                Arguments.of("#!/usr/bin/env -S node", "javascript"),
                Arguments.of("#!/usr/bin/awk -f", "text"),
                Arguments.of("no shebang", "text"));
    }

    @ParameterizedTest
    @MethodSource("shebangs")
    void detectsShebangOfFilesWithoutExtension(String content, String language) {
        byte[] sample = content.getBytes(StandardCharsets.UTF_8);

        assertThat(LanguageDetector.detect("run", sample)).isEqualTo(language);
        assertThat(LanguageDetector.detect("run.txt", sample)).isEqualTo("text");
    }
}
