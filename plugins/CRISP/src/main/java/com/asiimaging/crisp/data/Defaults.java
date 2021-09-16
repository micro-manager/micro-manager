/*
 * Project: ASI CRISP Control
 * License: BSD 3-clause, see LICENSE.md
 * Author: Brandon Simpson (brandon@asiimaging.com)
 * Copyright (c) 2014-2021, Applied Scientific Instrumentation
 */
package com.asiimaging.crisp.data;

/**
 * This class stores default values.
 *
 */
public final class Defaults {
    
    // CRISPSettings
    public static final int LOOP_GAIN = 10;
    public static final int NUM_AVERAGES = 0;
    public static final int LED_INTENSITY = 50;
    public static final int UPDATE_RATE_MS = 10;
    public static final float LOCK_RANGE = 1.0f;
    public static final float OBJECTIVE_NA = 0.65f;

    // plugin settings
    public static final int POLL_RATE_MS = 250;
    public static final boolean POLL_CHECKED = true;
    
}
