/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at OpenObserve (https://www.datadoghq.com/).
 * Copyright 2020-Present OpenObserve, Inc.
 */

package com.openobserve.gradle.plugin.kcp

import com.openobserve.gradle.plugin.InstrumentationMode
import com.tschuchort.compiletesting.KotlinCompilation
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi

@OptIn(ExperimentalCompilerApi::class)
class ComposeNavHostCompilation22Test : ComposeNavHostCompilationTest() {
    override fun registerOpenObservePluginRegistrar(
        compilation: KotlinCompilation,
        instrumentationMode: InstrumentationMode
    ) {
        compilation.componentRegistrars = listOf(OpenObservePluginRegistrarImpl(instrumentationMode))
    }
}
