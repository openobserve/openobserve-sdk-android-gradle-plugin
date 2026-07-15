/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at OpenObserve (https://www.datadoghq.com/).
 * Copyright 2020-Present OpenObserve, Inc.
 */

package com.openobserve.gradle.utils

import org.gradle.process.ExecOperations
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader

fun ExecOperations.execShell(vararg command: String): List<String> {
    val outputStream = ByteArrayOutputStream()
    this.exec {
        commandLine(*command)
        standardOutput = outputStream
    }

    val reader = InputStreamReader(ByteArrayInputStream(outputStream.toByteArray()))
    return reader.readLines()
}
