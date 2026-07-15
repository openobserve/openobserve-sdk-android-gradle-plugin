/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at OpenObserve (https://www.datadoghq.com/).
 * Copyright 2020-Present OpenObserve, Inc.
 */

package com.openobserve.gradle.plugin.kcp

import org.jetbrains.kotlin.backend.common.extensions.FirIncompatiblePluginAPI
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify

@OptIn(FirIncompatiblePluginAPI::class, UnsafeDuringIrConstructionAPI::class)
internal class ComposeTagExtension20Test : ComposeTagExtensionTest() {

    override fun verifyTransformerAccepted(annotationModeEnabled: Boolean) {
        val composeTagExtension = ComposeTagExtension(
            messageCollector = mockMessageCollector,
            annotationModeEnabled = annotationModeEnabled
        )

        composeTagExtension.generate(mockModuleFragment, mockPluginContext)

        verify(mockModuleFragment).accept(any<ComposeTagTransformer>(), eq(null))
    }

    @Test
    fun `M accept ComposeTagTransformer W generate with annotation mode disabled`() {
        verifyTransformerAccepted(false)
    }

    @Test
    fun `M accept ComposeTagTransformer W generate with annotation mode enabled`() {
        verifyTransformerAccepted(true)
    }
}
