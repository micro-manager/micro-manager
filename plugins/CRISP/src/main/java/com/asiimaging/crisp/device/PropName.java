/**
 * Project: ASI CRISP Control
 * License: BSD 3-clause, see LICENSE.md
 * Author: Brandon Simpson (brandon@asiimaging.com)
 * Copyright (c) 2014-2021, Applied Scientific Instrumentation
 */
package com.asiimaging.crisp.device;

/**
 * This class contains {@code String}s that correspond to the property names 
 * defined in the Micro-Manager device adapter.
 */
public class PropName {
    
    public static final String CRISP_STATE = "CRISP State";
    public static final String DESCRIPTION = "Description";
    
    public static final String SUM = "Sum";
    public static final String SNR = "Signal Noise Ratio";
    public static final String GAIN = "GainMultiplier";
    public static final String LOG_AMP_AGC = "LogAmpAGC";
    
    public static final String LED_INTENSITY = "LED Intensity";
    public static final String OBJECTIVE_NA = "Objective NA";
    public static final String NUMBER_OF_AVERAGES = "Number of Averages";
    public static final String MAX_LOCK_RANGE = "Max Lock Range(mm)";
    public static final String DITHER_ERROR = "Dither Error";
    
    public static final String SAVE_TO_CONTROLLER = "Save to Controller";
    public static final String REFRESH_PROP_VALUES = "RefreshPropertyValues";
    
    /**
     * PropName only on Tiger.
     */
    public class TIGER {
        public static final String AXIS_LETTER = "AxisLetter";
    }
    
    /**
     * PropName only on MS2000.
     */
    public class MS2000 {
        public static final String AXIS_LETTER = "Axis";
        public static final String OBTAIN_FOCUS_CURVE = "Obtain Focus Curve";
        public static final String FOCUS_CURVE_DATA_PREFIX = "Focus Curve Data"; // Focus Curve Data0 ... Focus Curve Data23
    }
}
