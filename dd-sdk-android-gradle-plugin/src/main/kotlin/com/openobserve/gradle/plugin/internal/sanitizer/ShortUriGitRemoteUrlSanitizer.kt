/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at OpenObserve (https://www.datadoghq.com/).
 * Copyright 2020-Present OpenObserve, Inc.
 */

package com.openobserve.gradle.plugin.internal.sanitizer

internal class ShortUriGitRemoteUrlSanitizer : FullUriGitRemoteUrlSanitizer() {
    override fun sanitize(url: String): String {
        val sanitizedUri = super.sanitize("$SSH_SCHEMA_PREFIX$url")
        return sanitizedUri.removePrefix(SSH_SCHEMA_PREFIX)
    }

    companion object {
        const val SSH_SCHEMA_PREFIX = "ssh://"
    }
}
