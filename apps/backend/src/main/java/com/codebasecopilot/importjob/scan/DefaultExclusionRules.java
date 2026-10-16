package com.codebasecopilot.importjob.scan;

import static com.codebasecopilot.importjob.scan.ExclusionCategory.BUILD_OUTPUT;
import static com.codebasecopilot.importjob.scan.ExclusionCategory.DEPENDENCIES;
import static com.codebasecopilot.importjob.scan.ExclusionCategory.GENERATED;
import static com.codebasecopilot.importjob.scan.ExclusionCategory.IDE_CACHE;
import static com.codebasecopilot.importjob.scan.ExclusionCategory.MEDIA_BINARY;
import static com.codebasecopilot.importjob.scan.ExclusionCategory.SENSITIVE;
import static com.codebasecopilot.importjob.scan.ExclusionCategory.VCS;

import java.util.List;
import java.util.Optional;

// Name patterns only: content-based secret detection is out of the MVP (D-036, resolves O-012).
// SENSITIVE, VCS and MEDIA_BINARY rules cannot be disabled.
public final class DefaultExclusionRules {

    public static final List<ExclusionRule> ALL =
            List.of(
                    fixed(
                            "env-files",
                            SENSITIVE,
                            List.of(".env", ".env.*"),
                            List.of(".env.example", ".env.sample", ".env.template")),
                    fixed(
                            "private-keys",
                            SENSITIVE,
                            List.of("*.pem", "*.key", "id_rsa*", "id_ecdsa*", "id_ed25519*")),
                    fixed("keystores", SENSITIVE, List.of("*.p12", "*.pfx", "*.jks", "*.keystore")),
                    fixed("credential-files", SENSITIVE, List.of(".npmrc", ".pypirc", ".netrc")),
                    fixed(
                            "terraform-state",
                            SENSITIVE,
                            List.of("*.tfstate", "*.tfstate.*", "*.tfvars")),
                    fixed("credential-dirs", SENSITIVE, List.of(".aws/", ".ssh/")),
                    fixed("vcs", VCS, List.of(".git/", ".hg/", ".svn/")),
                    overridable("node-modules", DEPENDENCIES, "node_modules/"),
                    overridable("vendor", DEPENDENCIES, "vendor/"),
                    overridable("python-venv", DEPENDENCIES, ".venv/", "venv/"),
                    overridable("bower-components", DEPENDENCIES, "bower_components/"),
                    overridable("gradle-cache", DEPENDENCIES, ".gradle/"),
                    overridable("maven-repository", DEPENDENCIES, ".m2/"),
                    overridable("target-dir", BUILD_OUTPUT, "target/"),
                    overridable("build-dir", BUILD_OUTPUT, "build/"),
                    overridable("dist-dir", BUILD_OUTPUT, "dist/"),
                    overridable("out-dir", BUILD_OUTPUT, "out/"),
                    overridable("js-framework-build", BUILD_OUTPUT, ".next/", ".nuxt/"),
                    overridable("coverage-dir", BUILD_OUTPUT, "coverage/"),
                    overridable("python-cache", BUILD_OUTPUT, "__pycache__/", "*.pyc"),
                    overridable(
                            "compiled-artifacts",
                            BUILD_OUTPUT,
                            "*.class",
                            "*.o",
                            "*.so",
                            "*.dll",
                            "*.exe",
                            "*.jar",
                            "*.war"),
                    overridable("ide-metadata", IDE_CACHE, ".idea/", ".DS_Store"),
                    overridable("cache-dir", IDE_CACHE, ".cache/"),
                    overridable("minified", GENERATED, "*.min.js", "*.min.css"),
                    overridable("source-maps", GENERATED, "*.map"),
                    overridable(
                            "lockfiles",
                            GENERATED,
                            "package-lock.json",
                            "yarn.lock",
                            "pnpm-lock.yaml"),
                    fixed(
                            "images",
                            MEDIA_BINARY,
                            List.of(
                                    "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp", "*.ico", "*.webp",
                                    "*.tif", "*.tiff", "*.psd", "*.svg")),
                    fixed(
                            "fonts",
                            MEDIA_BINARY,
                            List.of("*.woff", "*.woff2", "*.ttf", "*.otf", "*.eot")),
                    fixed(
                            "audio-video",
                            MEDIA_BINARY,
                            List.of(
                                    "*.mp3", "*.wav", "*.ogg", "*.flac", "*.mp4", "*.mov", "*.avi",
                                    "*.mkv", "*.webm")),
                    fixed(
                            "archives",
                            MEDIA_BINARY,
                            List.of(
                                    "*.zip", "*.tar", "*.gz", "*.tgz", "*.bz2", "*.xz", "*.7z",
                                    "*.rar")),
                    fixed("pdf-documents", MEDIA_BINARY, List.of("*.pdf")));

    private DefaultExclusionRules() {}

    // The first matching rule among the given ones (all of them, minus disabled ones).
    static Optional<ExclusionRule> match(
            List<ExclusionRule> rules, String relativePath, boolean directory) {
        return rules.stream().filter(r -> r.matches(relativePath, directory)).findFirst();
    }

    public static Optional<ExclusionRule> find(String id) {
        return ALL.stream().filter(r -> r.id().equals(id)).findFirst();
    }

    private static ExclusionRule fixed(
            String id, ExclusionCategory category, List<String> patterns) {
        return fixed(id, category, patterns, List.of());
    }

    private static ExclusionRule fixed(
            String id, ExclusionCategory category, List<String> patterns, List<String> exceptions) {
        return new ExclusionRule(id, category, patterns, exceptions, false);
    }

    private static ExclusionRule overridable(
            String id, ExclusionCategory category, String... patterns) {
        return new ExclusionRule(id, category, List.of(patterns), List.of(), true);
    }
}
