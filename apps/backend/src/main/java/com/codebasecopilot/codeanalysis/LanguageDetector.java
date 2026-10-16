package com.codebasecopilot.codeanalysis;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

// Generic detection for the inventory (D-009): known file names, extension, then the shebang of
// files without extension. Returns a stable lowercase id, or "text" when nothing matches.
public final class LanguageDetector {

    public static final String TEXT = "text";

    private static final Map<String, String> NAMES =
            Map.ofEntries(
                    Map.entry("dockerfile", "dockerfile"),
                    Map.entry("containerfile", "dockerfile"),
                    Map.entry("makefile", "makefile"),
                    Map.entry("gnumakefile", "makefile"),
                    Map.entry("cmakelists.txt", "cmake"),
                    Map.entry("jenkinsfile", "groovy"),
                    Map.entry("gemfile", "ruby"),
                    Map.entry("rakefile", "ruby"),
                    Map.entry("vagrantfile", "ruby"));

    private static final Map<String, String> EXTENSIONS =
            Map.ofEntries(
                    Map.entry("java", "java"),
                    Map.entry("kt", "kotlin"),
                    Map.entry("kts", "kotlin"),
                    Map.entry("scala", "scala"),
                    Map.entry("groovy", "groovy"),
                    Map.entry("gradle", "groovy"),
                    Map.entry("js", "javascript"),
                    Map.entry("mjs", "javascript"),
                    Map.entry("cjs", "javascript"),
                    Map.entry("jsx", "javascript"),
                    Map.entry("ts", "typescript"),
                    Map.entry("mts", "typescript"),
                    Map.entry("cts", "typescript"),
                    Map.entry("tsx", "typescript"),
                    Map.entry("py", "python"),
                    Map.entry("rb", "ruby"),
                    Map.entry("go", "go"),
                    Map.entry("rs", "rust"),
                    Map.entry("c", "c"),
                    Map.entry("h", "c"),
                    Map.entry("cpp", "cpp"),
                    Map.entry("cc", "cpp"),
                    Map.entry("cxx", "cpp"),
                    Map.entry("hpp", "cpp"),
                    Map.entry("hh", "cpp"),
                    Map.entry("cs", "csharp"),
                    Map.entry("php", "php"),
                    Map.entry("swift", "swift"),
                    Map.entry("dart", "dart"),
                    Map.entry("lua", "lua"),
                    Map.entry("r", "r"),
                    Map.entry("ex", "elixir"),
                    Map.entry("exs", "elixir"),
                    Map.entry("erl", "erlang"),
                    Map.entry("hs", "haskell"),
                    Map.entry("clj", "clojure"),
                    Map.entry("pl", "perl"),
                    Map.entry("sh", "shell"),
                    Map.entry("bash", "shell"),
                    Map.entry("zsh", "shell"),
                    Map.entry("ps1", "powershell"),
                    Map.entry("sql", "sql"),
                    Map.entry("html", "html"),
                    Map.entry("htm", "html"),
                    Map.entry("css", "css"),
                    Map.entry("scss", "scss"),
                    Map.entry("sass", "scss"),
                    Map.entry("less", "less"),
                    Map.entry("vue", "vue"),
                    Map.entry("svelte", "svelte"),
                    Map.entry("json", "json"),
                    Map.entry("yaml", "yaml"),
                    Map.entry("yml", "yaml"),
                    Map.entry("toml", "toml"),
                    Map.entry("xml", "xml"),
                    Map.entry("md", "markdown"),
                    Map.entry("markdown", "markdown"),
                    Map.entry("rst", "restructuredtext"),
                    Map.entry("properties", "properties"),
                    Map.entry("ini", "ini"),
                    Map.entry("cfg", "ini"),
                    Map.entry("proto", "protobuf"),
                    Map.entry("graphql", "graphql"),
                    Map.entry("gql", "graphql"),
                    Map.entry("tf", "terraform"));

    private static final Map<String, String> INTERPRETERS =
            Map.ofEntries(
                    Map.entry("python", "python"),
                    Map.entry("python3", "python"),
                    Map.entry("node", "javascript"),
                    Map.entry("deno", "typescript"),
                    Map.entry("bash", "shell"),
                    Map.entry("sh", "shell"),
                    Map.entry("zsh", "shell"),
                    Map.entry("ruby", "ruby"),
                    Map.entry("perl", "perl"),
                    Map.entry("php", "php"));

    private LanguageDetector() {}

    // sample is the beginning of the file, only inspected for a shebang.
    public static String detect(String fileName, byte[] sample) {
        String name = fileName.toLowerCase(Locale.ROOT);
        String byName = NAMES.get(name);
        if (byName != null) {
            return byName;
        }
        if (name.startsWith("dockerfile.")) {
            return "dockerfile";
        }
        int dot = name.lastIndexOf('.');
        if (dot > 0 && dot < name.length() - 1) {
            return EXTENSIONS.getOrDefault(name.substring(dot + 1), TEXT);
        }
        return dot < 0 ? shebang(sample).orElse(TEXT) : TEXT;
    }

    // "#!/usr/bin/env python3" and "#!/bin/bash -e" both name the interpreter.
    private static Optional<String> shebang(byte[] sample) {
        if (sample.length < 2 || sample[0] != '#' || sample[1] != '!') {
            return Optional.empty();
        }
        String line = new String(sample, StandardCharsets.UTF_8).lines().findFirst().orElse("");
        String[] words = line.substring(2).trim().split("\\s+");
        for (String word : words) {
            String program = word.substring(word.lastIndexOf('/') + 1);
            if (!program.equals("env") && !program.startsWith("-")) {
                return Optional.ofNullable(INTERPRETERS.get(program));
            }
        }
        return Optional.empty();
    }
}
