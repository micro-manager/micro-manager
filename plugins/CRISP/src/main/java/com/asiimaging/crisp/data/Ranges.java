/**
 * Project: ASI CRISP Control
 * License: BSD 3-clause, see LICENSE.md
 * Author: Brandon Simpson (brandon@asiimaging.com)
 * Copyright (c) 2014-2021, Applied Scientific Instrumentation
 */
package com.asiimaging.crisp.data;

/**
 * This class contains the minimum and maximum ranges for the Spinners in the UI.
 *
 * These values should reflect the ASIStage and ASITiger device adapter property limits.
 * 
 */
public class Ranges {
    
    // LED intensity is a percentage
    public static final int MIN_LED_INTENSITY = 0;
    public static final int MAX_LED_INTENSITY = 100;
    
    // integer sliders
    public static final int MIN_GAIN = 1;
    public static final int MAX_GAIN = 10;
    public static final int MIN_NUM_AVERAGES = 0;
    public static final int MAX_NUM_AVERAGES = 8;
    public static final int MIN_POLL_RATE_MS = 50;
    public static final int MAX_POLL_RATE_MS = 5000;
    
    // float sliders
    public static final float MIN_LOCK_RANGE = 0.0f;
    public static final float MAX_LOCK_RANGE = 2.0f;
    public static final float MIN_OBJECTIVE_NA = 0.0f;
    public static final float MAX_OBJECTIVE_NA = 2.0f;
}
