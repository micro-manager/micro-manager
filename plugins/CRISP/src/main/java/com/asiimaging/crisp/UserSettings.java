/*
 * Project: ASI CRISP Control
 * License: BSD 3-clause, see LICENSE.md
 * Author: Brandon Simpson (brandon@asiimaging.com)
 * Copyright (c) 2014-2021, Applied Scientific Instrumentation
 */
package com.asiimaging.crisp;

import com.asiimaging.crisp.data.Defaults;
import com.asiimaging.devices.crisp.CRISP;
import com.asiimaging.devices.crisp.CRISPSettings;
import com.asiimaging.devices.crisp.CRISPTimer;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.micromanager.Studio;
import org.micromanager.UserProfile;
import org.micromanager.propertymap.MutablePropertyMapView;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Objects;

/**
 * This class provides a convenient wrapper around MM's UserProfile class.
 *
 * If you need access to everything MutablePropertyMapView can do, use the
 * get() method to retrieve a reference to the MutablePropertyMapView object.
 *
 */
public class UserSettings {

    private final String userName;
    private final UserProfile profile;
    private final MutablePropertyMapView settings;

    private final CRISP crisp;
    private final CRISPTimer timer;
    private final CRISPFrame frame;

    // these settings are independent from the saved software settings
    private static class Settings {
        public static final String POLL_RATE_MS = "pollRateMs";  // int
        public static final String POLL_CHECKED = "pollChecked"; // boolean
        public static final String WINDOW_X = "windowX"; // boolean
        public static final String WINDOW_Y = "windowY"; // boolean
    };

    public UserSettings(final Studio studio, final CRISP crisp, final CRISPTimer timer, final CRISPFrame frame) {
        Objects.requireNonNull(studio); // only used to get profile
        this.crisp = Objects.requireNonNull(crisp);
        this.timer = Objects.requireNonNull(timer);
        this.frame = Objects.requireNonNull(frame);
        // setup user profile
        profile = studio.getUserProfile();
        userName = profile.getProfileName();
        settings = profile.getSettings(UserSettings.class);
    }

    /**
     * Returns an object to save and retrieve settings.
     *
     * @return a reference to MutablePropertyMapView
     */
    public MutablePropertyMapView get() {
        return settings;
    }

    /**
     * Returns the name of the user profile.
     *
     * @return a String containing the name
     */
    public String getUserName() {
        return userName;
    }

    /**
     * Clears all user settings associated with this class name.
     */
    public void clear() {
        settings.clear();
    }

    /**
     * Load user settings.
     */
    public void load() {
        // get values from settings
        final int pollRateMs = settings.getInteger(Settings.POLL_RATE_MS, Defaults.POLL_RATE_MS);
        final boolean isPollChecked = settings.getBoolean(Settings.POLL_CHECKED, Defaults.POLL_CHECKED);

        // window location
        final int windowX = settings.getInteger(Settings.WINDOW_X, 0);
        final int windowY = settings.getInteger(Settings.WINDOW_Y, 0);

        // update ui elements
        frame.setLocation(windowX, windowY);
        frame.getSpinnerPanel().getPollRateSpinner().setInt(pollRateMs);
        frame.getSpinnerPanel().setPollingCheckBox(isPollChecked);
        timer.setPollRateMs(pollRateMs);

        // load software settings => skip first (default settings) and add all profiles to CRISP
        final String json = settings.getString("settings", CRISPSettings.SETTINGS_NOT_FOUND);
        if (!json.equals(CRISPSettings.SETTINGS_NOT_FOUND)) {
            final Type listType = new TypeToken<List<CRISPSettings>>(){}.getType();
            final List<CRISPSettings> list = new Gson().fromJson(json, listType);
            list.stream().skip(1).forEach(crisp.getSettingsList()::add);
        }
        frame.getSpinnerPanel().updateSoftwareSettingsComboBox();
        if (CRISPFrame.DEBUG) {
            System.out.println("LOADED JSON => " + json);
        }
    }

    /**
     * Save user settings.
     */
    public void save() {
        // get values from ui elements
        final int pollRateMs = frame.getSpinnerPanel().getPollRateSpinner().getInt();
        final boolean isPollChecked = frame.getSpinnerPanel().isPollingEnabled();

        // window location
        settings.putInteger(Settings.WINDOW_X, frame.getX());
        settings.putInteger(Settings.WINDOW_Y, frame.getY());

        // save values into settings
        settings.putInteger(Settings.POLL_RATE_MS, pollRateMs);
        settings.putBoolean(Settings.POLL_CHECKED, isPollChecked);

        // save software settings
        final String json = new Gson().toJson(crisp.getSettingsList());
        settings.putString("settings", json);
        if (CRISPFrame.DEBUG) {
            System.out.println("SAVED JSON => " + json);
        }
    }

}
