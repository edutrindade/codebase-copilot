package com.codebasecopilot.shared.localroots;

import java.nio.file.Path;

// The absolute path never leaves the backend (D-018, D-029).
public record LocalRoot(String key, String label, Path path) {}
