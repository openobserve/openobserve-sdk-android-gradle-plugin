/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at OpenObserve (https://www.datadoghq.com/).
 * Copyright 2020-Present OpenObserve, Inc.
 */

package com.openobserve.gradle.plugin

/**
 * Defines the action which plugin should take if OpenObserve SDK is missing
 * in the project dependencies.
 */
enum class SdkCheckLevel {
    /**
     * Do nothing if SDK is missing in the dependencies.
     */
    NONE,

    /**
     * Log a warning if SDK is missing in the dependencies.
     */
    WARN,

    /**
     * Fail the build if SDK is missing in the dependencies.
     */
    FAIL
}
