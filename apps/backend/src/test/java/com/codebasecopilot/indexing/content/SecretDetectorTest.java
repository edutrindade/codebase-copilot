package com.codebasecopilot.indexing.content;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

// Samples are built by concatenation so this file does not look like a secret itself.
class SecretDetectorTest {

    private static final String BODY = "EVALSYNTHETICnotarealtoken0000000000";

    static Stream<Arguments> secrets() {
        return Stream.of(
                Arguments.of("RSA key", pem("RSA PRIVATE KEY")),
                Arguments.of("EC key", pem("EC PRIVATE KEY")),
                Arguments.of("PKCS#8 key", pem("PRIVATE KEY")),
                Arguments.of("encrypted key", pem("ENCRYPTED PRIVATE KEY")),
                Arguments.of("OpenSSH key", pem("OPENSSH PRIVATE KEY")),
                Arguments.of(
                        "PGP key",
                        "-----BEGIN PGP PRIVATE KEY BLOCK-----\nVersion: X\n\n" + BODY + "\n"),
                Arguments.of(
                        "PEM with headers",
                        "-----BEGIN RSA PRIVATE KEY-----\nProc-Type: 4,ENCRYPTED\n"
                                + "DEK-Info: AES-128-CBC,0011\n\n"
                                + BODY),
                Arguments.of(
                        "PEM in a string",
                        "KEY = \"-----BEGIN " + "PRIVATE KEY-----\\n" + BODY + "\\n\""),
                Arguments.of("AWS key id", "key: " + "AKIA" + "EVALSYNTHETIC123"),
                Arguments.of("AWS temporary key id", "ASIA" + "EVALSYNTHETIC123,"),
                Arguments.of("GitHub classic token", "token=" + "ghp_" + BODY),
                Arguments.of("GitHub server token", "gh" + "s_" + BODY + "abc"),
                Arguments.of(
                        "GitHub fine-grained token",
                        "github_" + "pat_" + "11ABCDEFG0123456789_" + BODY),
                Arguments.of("Stripe live key", "sk_" + "live_" + "EVALSYNTHETIC0123456789"),
                Arguments.of("Stripe restricted key", "rk_" + "test_" + "EVALSYNTHETIC0123456789"),
                Arguments.of("Stripe org key", "sk_" + "org_" + "EVALSYNTHETIC0123456789"),
                Arguments.of("Slack bot token", "xox" + "b-" + "EVALSYNTHETIC-notarealtoken"),
                Arguments.of("Slack app token", "xapp" + "-1-EVALSYNTHETIC-notarealtoken"),
                Arguments.of("OpenAI key", "OPENAI_API_KEY=" + "sk-" + "proj-" + BODY),
                Arguments.of("key after UTF-8 text", "senha da produção: " + "ghp_" + BODY));
    }

    static Stream<Arguments> notSecrets() {
        return Stream.of(
                Arguments.of(
                        "PEM delimiter in a parser",
                        "if (line.equals(\"-----BEGIN " + "PRIVATE KEY-----\")) {"),
                Arguments.of("public key", "-----BEGIN PUBLIC KEY-----\n" + BODY),
                Arguments.of("certificate", "-----BEGIN CERTIFICATE-----\n" + BODY),
                Arguments.of("AWS id too short", "AKIA" + "EVALSYNTHETIC12"),
                Arguments.of("AWS id too long", "AKIA" + "EVALSYNTHETIC1234"),
                Arguments.of("AWS id inside a word", "X" + "AKIA" + "EVALSYNTHETIC123"),
                Arguments.of("GitHub prefix only", "ghp_" + "short"),
                Arguments.of("Stripe publishable key", "pk_" + "live_" + "EVALSYNTHETIC0123456789"),
                Arguments.of("Slack prefix only", "xox" + "b-short"),
                Arguments.of(
                        "kebab-case name", "task-" + "sk-very-long-kebab-case-name-without-any"),
                Arguments.of("sk- without digits", "sk-" + "abcdefghijklmnopqrstuvwxyzabcdefgh"),
                Arguments.of("password assignment", "password=correct-horse-battery-staple"),
                Arguments.of("high entropy", "K8sJx2Qv9Lm4Tz7Wp1Rn6Yb3Hc5Gd0Fa"),
                Arguments.of("env reference", "token: ${GITHUB_TOKEN}"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("secrets")
    void detectsKnownFormats(String name, String content) {
        assertThat(SecretDetector.containsPossibleSecret(bytes(content))).isTrue();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("notSecrets")
    void ignoresLookAlikes(String name, String content) {
        assertThat(SecretDetector.containsPossibleSecret(bytes(content))).isFalse();
    }

    @Test
    void readsBytesThatAreNotUtf8() {
        byte[] latin1 = ("ação " + "ghp_" + BODY).getBytes(StandardCharsets.ISO_8859_1);

        assertThat(SecretDetector.containsPossibleSecret(latin1)).isTrue();
        assertThat(SecretDetector.containsPossibleSecret(new byte[] {(byte) 0xFF, 0, 1})).isFalse();
    }

    private static String pem(String type) {
        return "-----BEGIN " + type + "-----\n" + BODY + "\n-----END " + type + "-----\n";
    }

    private static byte[] bytes(String content) {
        return content.getBytes(StandardCharsets.UTF_8);
    }
}
