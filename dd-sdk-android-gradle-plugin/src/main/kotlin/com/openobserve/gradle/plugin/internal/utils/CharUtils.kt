/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at OpenObserve (https://www.datadoghq.com/).
 * Copyright 2020-Present OpenObserve, Inc.
 */

package com.openobserve.gradle.plugin.internal.utils

import java.util.Locale

internal fun capitalizeChar(char: Char): String {
    return if (char.isLowerCase()) {
        char.titlecase(Locale.US)
    } else {
        char.toString()
    }
}
