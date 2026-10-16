package com.codebasecopilot.importjob.scan;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class DefaultExclusionRulesTest {

    @ParameterizedTest
    @CsvSource({
        // rule id, path, directory
        "env-files,           .env,                        false",
        "env-files,           config/.env.local,           false",
        "private-keys,        certs/server.pem,            false",
        "private-keys,        tls.key,                     false",
        "private-keys,        .ssh-backup/id_rsa,          false",
        "private-keys,        id_rsa.pub,                  false",
        "private-keys,        id_ed25519,                  false",
        "private-keys,        id_ecdsa,                    false",
        "keystores,           app.p12,                     false",
        "keystores,           app.pfx,                     false",
        "keystores,           app.jks,                     false",
        "keystores,           release.keystore,            false",
        "credential-files,    .npmrc,                      false",
        "credential-files,    .pypirc,                     false",
        "credential-files,    .netrc,                      false",
        "terraform-state,     infra/terraform.tfstate,     false",
        "terraform-state,     terraform.tfstate.backup,    false",
        "terraform-state,     prod.tfvars,                 false",
        "credential-dirs,     .aws,                        true",
        "credential-dirs,     home/.ssh,                   true",
        "vcs,                 .git,                        true",
        "vcs,                 .hg,                         true",
        "vcs,                 .svn,                        true",
        "node-modules,        web/node_modules,            true",
        "vendor,              vendor,                      true",
        "python-venv,         .venv,                       true",
        "python-venv,         venv,                        true",
        "bower-components,    bower_components,            true",
        "gradle-cache,        .gradle,                     true",
        "maven-repository,    .m2,                         true",
        "target-dir,          target,                      true",
        "build-dir,           app/build,                   true",
        "dist-dir,            dist,                        true",
        "out-dir,             out,                         true",
        "js-framework-build,  .next,                       true",
        "js-framework-build,  .nuxt,                       true",
        "coverage-dir,        coverage,                    true",
        "python-cache,        pkg/__pycache__,             true",
        "python-cache,        mod.pyc,                     false",
        "compiled-artifacts,  Main.class,                  false",
        "compiled-artifacts,  lib.o,                       false",
        "compiled-artifacts,  lib.so,                      false",
        "compiled-artifacts,  lib.dll,                     false",
        "compiled-artifacts,  app.exe,                     false",
        "compiled-artifacts,  app.jar,                     false",
        "compiled-artifacts,  app.war,                     false",
        "ide-metadata,        .idea,                       true",
        "ide-metadata,        .DS_Store,                   false",
        "cache-dir,           .cache,                      true",
        "minified,            app.min.js,                  false",
        "minified,            app.min.css,                 false",
        "source-maps,         app.js.map,                  false",
        "lockfiles,           package-lock.json,           false",
        "lockfiles,           yarn.lock,                   false",
        "lockfiles,           pnpm-lock.yaml,              false",
        "images,              logo.png,                    false",
        "images,              icon.svg,                    false",
        "fonts,               font.woff2,                  false",
        "audio-video,         intro.mp4,                   false",
        "archives,            backup.tar.gz,               false",
        "archives,            repo.zip,                    false",
        "pdf-documents,       manual.pdf,                  false"
    })
    void matchesRule(String ruleId, String path, boolean directory) {
        assertThat(DefaultExclusionRules.match(DefaultExclusionRules.ALL, path, directory))
                .map(ExclusionRule::id)
                .contains(ruleId);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                ".env.example",
                ".env.sample",
                ".env.template",
                "src/Main.java",
                "environment.ts",
                "build.gradle",
                "Makefile",
                "keys.md"
            })
    void includesOrdinaryFiles(String path) {
        assertThat(DefaultExclusionRules.match(DefaultExclusionRules.ALL, path, false)).isEmpty();
    }

    @Test
    void directoryRulesDoNotMatchFilesWithTheSameName() {
        assertThat(DefaultExclusionRules.match(DefaultExclusionRules.ALL, "build", false))
                .isEmpty();
        assertThat(DefaultExclusionRules.match(DefaultExclusionRules.ALL, "vendor", false))
                .isEmpty();
    }

    @Test
    void everyRuleHasUniqueIdAndAtLeastOnePattern() {
        Set<String> ids =
                DefaultExclusionRules.ALL.stream()
                        .map(ExclusionRule::id)
                        .collect(Collectors.toSet());
        assertThat(ids).hasSameSizeAs(DefaultExclusionRules.ALL);
        assertThat(DefaultExclusionRules.ALL)
                .allSatisfy(r -> assertThat(r.patterns()).isNotEmpty());
    }

    @Test
    void sensitiveVcsAndMediaRulesCannotBeDisabled() {
        assertThat(DefaultExclusionRules.ALL)
                .allSatisfy(
                        rule ->
                                assertThat(rule.overridable())
                                        .isEqualTo(
                                                Set.of(
                                                                ExclusionCategory.DEPENDENCIES,
                                                                ExclusionCategory.BUILD_OUTPUT,
                                                                ExclusionCategory.IDE_CACHE,
                                                                ExclusionCategory.GENERATED)
                                                        .contains(rule.category())));
        assertThat(DefaultExclusionRules.find("vendor"))
                .map(ExclusionRule::overridable)
                .contains(true);
        assertThat(DefaultExclusionRules.find("missing")).isEqualTo(Optional.empty());
    }
}
