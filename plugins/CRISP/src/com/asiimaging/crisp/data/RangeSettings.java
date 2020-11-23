///////////////////////////////////////////////////////////////////////////////
// AUTHOR:       Brandon Simpson
//
// COPYRIGHT:    Applied Scientific Instrumentation, 2020
//
// LICENSE:      This file is distributed under the BSD license.
//               License text is included with the source distribution.
//
//               This file is distributed in the hope that it will be useful,
//               but WITHOUT ANY WARRANTY; without even the implied warranty
//               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.

package com.asiimaging.crisp.data;

/**
 * This class contains the minimum and maximum ranges for the Spinners in the UI.
 *
 * These values should reflect the ASIStage and ASITiger property limits.
 * 
 */
public class RangeSettings {
	
	// LED intensity is a percentage and will never change
	public final static int MIN_LED_INTENSITY = 0;
	public final static int MAX_LED_INTENSITY = 100;
	
	// integer sliders
	public final static int MIN_GAIN = 0;
	public final static int MAX_GAIN = 100;
	public final static int MIN_NUM_AVERAGES = 0;
	public final static int MAX_NUM_AVERAGES = 10;
	public final static int MIN_POLL_RATE_MS = 10;
	public final static int MAX_POLL_RATE_MS = 1000;
	
	// float sliders
	public final static float MIN_LOCK_RANGE = 0.0f;
	public final static float MAX_LOCK_RANGE = 2.0f;
	public final static float MIN_OBJECTIVE_NA = 0.0f;
	public final static float MAX_OBJECTIVE_NA = 1.65f;
}
