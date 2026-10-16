package com.codebasecopilot.importjob.scan;

public enum ExclusionReason {
    DEFAULT_RULE,
    CUSTOM_PATTERN,
    SYMLINK,
    SPECIAL_FILE,
    DEPTH_LIMIT,
    TOO_LARGE,
    BINARY,
    UNREADABLE
}
