package com.codebasecopilot.providers;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

// OPENAI_LOG=debug makes the SDK print request bodies to stderr; the providers turn it off.
class OpenAiLogLevelTest {

    @Test
    void providerPrintsNoRequestBodyWithOpenAiLogDebug() throws Exception {
        String output = runProbe("provider");

        assertThat(output).contains(OpenAiLogProbe.DONE + 1).doesNotContain(OpenAiLogProbe.CHUNK);
    }

    @Test
    void generationPrintsNoRequestOrResponseBodyWithOpenAiLogDebug() throws Exception {
        String output = runProbe("generation");

        assertThat(output).contains(OpenAiLogProbe.DONE + 1).doesNotContain(OpenAiLogProbe.CHUNK);
    }

    @Test
    void sdkPrintsRequestBodyWithOpenAiLogDebugWhenNotOverridden() throws Exception {
        String output = runProbe("raw");

        assertThat(output).contains(OpenAiLogProbe.DONE + 1).contains(OpenAiLogProbe.CHUNK);
    }

    private static String runProbe(String mode) throws IOException, InterruptedException {
        ProcessBuilder builder =
                new ProcessBuilder(
                                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                                "-cp",
                                System.getProperty("java.class.path"),
                                OpenAiLogProbe.class.getName(),
                                mode)
                        .redirectErrorStream(true);
        builder.environment().put("OPENAI_LOG", "debug");
        Process process = builder.start();
        byte[] output = process.getInputStream().readAllBytes();
        assertThat(process.waitFor(60, TimeUnit.SECONDS)).isTrue();
        String text = new String(output, StandardCharsets.UTF_8);
        assertThat(process.exitValue()).as(text).isZero();
        return text;
    }
}
