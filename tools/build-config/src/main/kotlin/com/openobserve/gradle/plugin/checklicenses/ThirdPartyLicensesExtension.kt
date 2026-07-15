/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at OpenObserve (https://www.datadoghq.com/).
 * Copyright 2020-Present OpenObserve, Inc.
 */

package com.openobserve.gradle.plugin.checklicenses

import java.io.File

open class ThirdPartyLicensesExtension(
    var csvFile: File = File(DEFAULT_TP_LICENCE_FILENAME),
    var listDependencyOnce: Boolean = true,
    var transitiveDependencies: Boolean = false,
    var checkObsoleteDependencies: Boolean = false
) {
    companion object {
        const val DEFAULT_TP_LICENCE_FILENAME = "LICENSE-3rdparty.csv"
    }
}
