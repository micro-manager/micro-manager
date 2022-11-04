/*
 * Project: ASI Ring TIRF Control
 * License: BSD 3-clause, see LICENSE.md
 * Author: Brandon Simpson (brandon@asiimaging.com)
 * Copyright (c) 2022, Applied Scientific Instrumentation
 */
package com.asiimaging.tirf.model.data;

import com.asiimaging.tirf.model.TIRFControlModel;
import com.google.gson.Gson;
import org.micromanager.data.Datastore;

/**
 * The software settings. The variable names must match the JSON names.
 */
public final class Settings {

    public final int numImages;
    public final int numFastCircles;
    public final int startupScriptDelay;
    public final boolean useStartupScript;
    public final boolean useShutdownScript;
    public final String startupScriptPath;
    public final String shutdownScriptPath;
    public final String datastoreSavePath;
    public final String datastoreSaveFileName;
    public final Datastore.SaveMode datastoreSaveMode;

    private Settings() {
        numImages = Defaults.NUM_IMAGES;
        numFastCircles = Defaults.NUM_FAST_CIRCLES;
        startupScriptDelay = Defaults.STARTUP_SCRIPT_DELAY_MS;
        useStartupScript = Defaults.USE_STARTUP_SCRIPT;
        useShutdownScript = Defaults.USE_SHUTDOWN_SCRIPT;
        startupScriptPath = Defaults.STARTUP_SCRIPT_PATH;
        shutdownScriptPath = Defaults.SHUTDOWN_SCRIPT_PATH;
        datastoreSaveMode = Defaults.DATASTORE_SAVE_MODE;
        datastoreSavePath = Defaults.DATASTORE_SAVE_DIRECTORY;
        datastoreSaveFileName = Defaults.DATASTORE_SAVE_FILENAME;
    }

    public static Settings createDefaultSettings() {
        return new Settings();
    }

    public void setModelSettings(final TIRFControlModel model) {
        model.setNumImages(numImages);
        model.setNumFastCircles(numFastCircles);
        model.setScriptDelay(startupScriptDelay);
        model.setUseStartupScript(useStartupScript);
        model.setUseShutdownScript(useShutdownScript);
        model.setStartupScriptPath(startupScriptPath);
        model.setShutdownScriptPath(shutdownScriptPath);
        model.setDatastoreSaveMode(datastoreSaveMode);
        model.setDatastoreSavePath(datastoreSavePath);
        model.setDatastoreSaveFileName(datastoreSaveFileName);
    }

    public String toJson() {
        return new Gson().toJson(this);
    }

    public static Settings fromJson(final String json) {
        return new Gson().fromJson(json, Settings.class);
    }

}
