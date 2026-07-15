/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at OpenObserve (https://www.datadoghq.com/).
 * Copyright 2020-Present OpenObserve, Inc.
 */

package com.openobserve.gradle.plugin.utils.forge

import com.openobserve.gradle.plugin.internal.DdAppIdentifier
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory
import java.util.UUID

internal class IdentifierForgeryFactory : ForgeryFactory<DdAppIdentifier> {
    override fun getForgery(forge: Forge): DdAppIdentifier {
        return DdAppIdentifier(
            serviceName = forge.aStringMatching("[a-z]{3}(\\.[a-z]{5,10}){2,4}"),
            version = forge.aStringMatching("\\d\\.\\d{1,2}\\.\\d{1,3}"),
            versionCode = forge.aPositiveInt(),
            variant = forge.anAlphabeticalString(),
            buildId = forge.getForgery<UUID>().toString()
        )
    }
}
