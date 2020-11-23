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

package com.asiimaging.crisp;

import java.util.prefs.Preferences;

import com.asiimaging.crisp.data.DefaultSettings;
import com.asiimaging.crisp.ui.SpinnerPanel;

/**
 * This is a utility class for saving the user's settings.
 * TODO: investigate MM 2.0 user profiles when porting
 */
public class UserSettings {
	
	private final Preferences prefs;
	private final SpinnerPanel panel;
	private final CRISP crisp;
	
	private class Settings {
		public final static String POLL_RATE_MS = "pollRateMs";  // int
		public final static String POLL_CHECKED = "pollChecked"; // boolean
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
		final int pollRateMs = prefs.getInt(Settings.POLL_RATE_MS, DefaultSettings.POLL_RATE_MS);
		final boolean isPollChecked = prefs.getBoolean(Settings.POLL_CHECKED, DefaultSettings.POLL_CHECKED);
		// set values from settings
		panel.getPollRateSpinner().setInt(pollRateMs);
		panel.getPollingCheckBox().setSelected(isPollChecked);
		crisp.setPollRateMs(pollRateMs);
	}
	
	/**
	 * Save user settings.
	 */
	public void save() {
		final int pollRateMs = panel.getPollRateSpinner().getInt();
		final boolean isPollChecked = panel.getPollingCheckBox().isSelected();
		// save values into prefs
		prefs.putInt(Settings.POLL_RATE_MS, pollRateMs);
		prefs.putBoolean(Settings.POLL_CHECKED, isPollChecked);
	}
	
	/**
	 * Query CRISP and populate Spinner values with the results.
	 */
	public void queryController() {
		// get values from CRISP unit
		final int gainMult      = crisp.getGainMultiplier();
		final int numAverages   = crisp.getNumberOfAverages();
		final int LEDIntensity  = crisp.getLEDIntensity();
		final float lockRange   = crisp.getLockRange();
		final float objectiveNA = crisp.getObjectiveNA();
		// set spinner values to the results
		panel.getGainSpinner().setInt(gainMult);
		panel.getAveragesSpinner().setInt(numAverages);
		panel.getLEDSpinner().setInt(LEDIntensity);
		panel.getLockRangeSpinner().setFloat(lockRange);
		panel.getNASpinner().setFloat(objectiveNA);
	}
}
