/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at OpenObserve (https://www.datadoghq.com/).
 * Copyright 2020-Present OpenObserve, Inc.
 */

package com.openobserve.gradle.plugin.internal

internal data class DdAppIdentifier(
    val serviceName: String,
    val version: String,
    val versionCode: Int,
    val variant: String,
    val buildId: String
) {

    override fun toString(): String {
        return "`service:$serviceName`, `version:$version`, `version_code:$versionCode`," +
            " `variant:$variant`, `build_id:$buildId`"
    }
}
