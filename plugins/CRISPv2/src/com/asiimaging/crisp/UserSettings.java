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

import com.asiimaging.crisp.control.CRISPTimer;
import com.asiimaging.crisp.data.Defaults;
import com.asiimaging.crisp.panels.SpinnerPanel;

/**
 * This is a utility class for saving and loading user settings.
 */
public class UserSettings {

    private final CRISPTimer timer;
    private final SpinnerPanel panel;
    
    private final Preferences prefs;
    
    private class Settings {
        public final static String POLL_RATE_MS = "pollRateMs";  // int
        public final static String POLL_CHECKED = "pollChecked"; // boolean
    };
    
    public UserSettings(final CRISPTimer timer, final SpinnerPanel panel) {
        prefs = Preferences.userNodeForPackage(this.getClass());
        this.timer = timer;
        this.panel = panel;
    }
    
    /**
     * Load user settings.
     */
    public void load() {
        final int pollRateMs = prefs.getInt(Settings.POLL_RATE_MS, Defaults.POLL_RATE_MS);
        final boolean isPollChecked = prefs.getBoolean(Settings.POLL_CHECKED, Defaults.POLL_CHECKED);
        // set values from settings
        panel.getPollRateSpinner().setInt(pollRateMs);
        panel.setPollingCheckBox(isPollChecked);
        timer.setPollRateMs(pollRateMs);
    }
    
    /**
     * Save user settings.
     */
    public void save() {
        final int pollRateMs = panel.getPollRateSpinner().getInt();
        final boolean isPollChecked = panel.isPollingEnabled();
        // save values into prefs
        prefs.putInt(Settings.POLL_RATE_MS, pollRateMs);
        prefs.putBoolean(Settings.POLL_CHECKED, isPollChecked);
    }
}
