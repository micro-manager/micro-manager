/*
 * Project: ASI CRISP Control
 * License: BSD 3-clause, see LICENSE.md
 * Author: Brandon Simpson (brandon@asiimaging.com)
 * Copyright (c) 2014-2021, Applied Scientific Instrumentation
 */
package com.asiimaging.crisp.device;

/**
 * This class contains {@code String}s that correspond to the property values 
 * defined in the Micro-Manager device adapter.
 */
public class PropValue {
    
    public static final String NO = "No";
    public static final String YES = "Yes";
    
    public static final String STATE_IDLE = "Idle";
    public static final String STATE_LOG_CAL = "loG_cal";
    public static final String STATE_DITHER = "Dither";
    public static final String STATE_GAIN_CAL = "gain_Cal";

    public static final String STATE_IN_FOCUS = "gain_Cal";

    public static final String RESET_FOCUS_OFFSET = "Reset Focus Offset";
    public static final String SAVE_TO_CONTROLLER = "Save to Controller";
    
    /**
     * PropValue only on MS2000.
     */
    public class MS2000 { 
        public static final String DO_IT = "Do it";
    }
}
