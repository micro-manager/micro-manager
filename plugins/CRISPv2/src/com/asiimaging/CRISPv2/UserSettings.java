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

package com.asiimaging.CRISPv2;

import java.util.prefs.Preferences;

import com.asiimaging.CRISPv2.data.DefaultSettings;
import com.asiimaging.CRISPv2.ui.SpinnerPanel;

/**
 * This is a utility class for saving the user's settings.
 * TODO: investigate MM 2.0 user profiles when porting
 */
public class UserSettings {
	
	private final Preferences prefs;
	private final SpinnerPanel panel;
	private final CRISP crisp;
	
	private class Settings {
		public final static String GAIN          = "gainMult";     // int
		public final static String POLL_RATE_MS  = "pollRateMs";   // int
		public final static String NUM_AVERAGES  = "numAverages";  // int
		public final static String LED_INTENSITY = "LEDIntensity"; // int
		public final static String LOCK_RANGE    = "lockRange";    // float
		public final static String OBJECTIVE_NA  = "objectiveNA";  // float
	};
	
	public UserSettings(final CRISP crisp, final SpinnerPanel panel) {
		prefs = Preferences.userNodeForPackage(this.getClass());
		this.crisp = crisp;
		this.panel = panel;
	}
	
	/**
	 * Load user settings.
	 */
	public void load() {
//		final int gainMult      = prefs.getInt(Settings.GAIN,           DefaultSettings.GAIN);
//		final int pollRateMs    = prefs.getInt(Settings.POLL_RATE_MS,   DefaultSettings.POLL_RATE_MS);
//		final int numAverages   = prefs.getInt(Settings.NUM_AVERAGES,   DefaultSettings.NUM_AVERAGES);
//		final int LEDIntensity  = prefs.getInt(Settings.LED_INTENSITY,  DefaultSettings.LED_INTENSITY);
//		final float lockRange   = prefs.getFloat(Settings.LOCK_RANGE,   DefaultSettings.LOCK_RANGE);
//		final float objectiveNA = prefs.getFloat(Settings.OBJECTIVE_NA, DefaultSettings.OBJECTIVE_NA);
//		System.out.println("gainMult: " + gainMult);
//		System.out.println("pollRateMs: " + pollRateMs);
//		System.out.println("numAverages: " + numAverages);
//		System.out.println("LEDIntensity: " + LEDIntensity);
//		System.out.println("lockRange: " + lockRange);
//		System.out.println("objectiveNA: " + objectiveNA);
//		panel.getGainSpinner().setInt(gainMult);
//		panel.getPollRateSpinner().setInt(pollRateMs);
//		panel.getAveragesSpinner().setInt(numAverages);
//		panel.getLEDSpinner().setInt(LEDIntensity);
//		panel.getLockRangeSpinner().setFloat(lockRange);
//		panel.getNASpinner().setFloat(objectiveNA);
		
		final int pollRateMs    = prefs.getInt(Settings.POLL_RATE_MS,   DefaultSettings.POLL_RATE_MS);
		panel.getPollRateSpinner().setInt(pollRateMs);
	}
	
	/**
	 * Save user settings.
	 */
	public void save() {
//		final int gainMult = panel.getGainSpinner().getInt();
//		final int pollRateMs = panel.getPollRateSpinner().getInt();
//		final int numAverages = panel.getAveragesSpinner().getInt();
//		final int LEDIntensity = panel.getLEDSpinner().getInt();
//		final float lockRange = panel.getLockRangeSpinner().getFloat();
//		final float objectiveNA = panel.getNASpinner().getFloat();
//		prefs.putInt(Settings.GAIN,           gainMult);
//		prefs.putInt(Settings.POLL_RATE_MS,   pollRateMs);
//		prefs.putInt(Settings.NUM_AVERAGES,   numAverages);
//		prefs.putInt(Settings.LED_INTENSITY,  LEDIntensity);
//		prefs.putFloat(Settings.LOCK_RANGE,   lockRange);
//		prefs.putFloat(Settings.OBJECTIVE_NA, objectiveNA);
//		System.out.println("gainMult: " + gainMult);
//		System.out.println("pollRateMs: " + pollRateMs);
//		System.out.println("numAverages: " + numAverages);
//		System.out.println("LEDIntensity: " + LEDIntensity);
//		System.out.println("lockRange: " + lockRange);
//		System.out.println("objectiveNA: " + objectiveNA);
		
		final int pollRateMs = panel.getPollRateSpinner().getInt();
		prefs.putInt(Settings.POLL_RATE_MS,   pollRateMs);
	}
	
	/**
	 * Query CRISP and populate Spinner values with the results.
	 */
	public void queryController() {
		// send serial commands to CRISP
		final int gainMult      = crisp.getGainMultiplier();
		final int numAverages   = crisp.getNumberOfAverages();
		final int LEDIntensity  = crisp.getLEDIntensity();
		final float lockRange   = crisp.getLockRange();
		final float objectiveNA = crisp.getObjectiveNA();
		// set spinner values to results
		panel.getGainSpinner().setInt(gainMult);
		panel.getAveragesSpinner().setInt(numAverages);
		panel.getLEDSpinner().setInt(LEDIntensity);
		panel.getLockRangeSpinner().setFloat(lockRange);
		panel.getNASpinner().setFloat(objectiveNA);
	}
}
