/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at OpenObserve (https://www.datadoghq.com/).
 * Copyright 2020-Present OpenObserve, Inc.
 */

package com.openobserve.gradle.plugin.kcp

import com.openobserve.gradle.plugin.InstrumentationMode
import com.google.auto.service.AutoService
import org.jetbrains.kotlin.backend.common.extensions.FirIncompatiblePluginAPI
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.compiler.plugin.ComponentRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi

/**
 * Implementation of [OpenObservePluginRegistrar] with Kotlin 2.2.x support.
 */
@OptIn(ExperimentalCompilerApi::class, FirIncompatiblePluginAPI::class)
@AutoService(ComponentRegistrar::class)
class OpenObservePluginRegistrarImpl(overrideInstrumentationMode: InstrumentationMode? = null) :
    OpenObservePluginRegistrar(overrideInstrumentationMode) {
    override fun getCompatibleComposeNavHostExtension(
        messageCollector: MessageCollector,
        instrumentationMode: InstrumentationMode
    ): IrGenerationExtension {
        return ComposeNavHostExtension(
            messageCollector = messageCollector,
            annotationModeEnabled = instrumentationMode == InstrumentationMode.ANNOTATION
        )
    }

    override fun getCompatibleComposeTagExtension(
        messageCollector: MessageCollector,
        instrumentationMode: InstrumentationMode
    ): IrGenerationExtension {
        return ComposeTagExtension(
            messageCollector = messageCollector,
            annotationModeEnabled = instrumentationMode == InstrumentationMode.ANNOTATION
        )
    }
}
