/*
 * Project: ASI Ring TIRF Control
 * License: BSD 3-clause, see LICENSE.md
 * Author: Brandon Simpson (brandon@asiimaging.com)
 * Copyright (c) 2022, Applied Scientific Instrumentation
 */
package com.asiimaging.tirf.model.devices;

import mmcorej.CMMCore;
import org.micromanager.Studio;

import java.util.Objects;

public class Camera {

    private final Studio studio;
    private final CMMCore core;

    private String cameraName;
    private String deviceName;

    private boolean isSupported;

    public static class Properties {

        public static class Keys {
            public static String TRIGGER_MODE = "";
        }

        public static class Values {
            public static String EXTERNAL_TRIGGER = "";
            public static String INTERNAL_TRIGGER = "";
        }
    }

    public Camera(final Studio studio) {
        this.studio = Objects.requireNonNull(studio);
        core = studio.core();
        isSupported = false;
        cameraName = "";
        deviceName = "";
    }

    public boolean isSupported() {
        return isSupported;
    }

    public void setDeviceName(final String name) {
        deviceName = name;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public void setup() {
        deviceName = core.getCameraDevice();
        cameraName = getCameraName();
        setupDeviceProperties();
    }

    private void setupDeviceProperties() {
        switch (cameraName) {
            case "C14440-20UP": // Hamamatsu Fusion
                Properties.Keys.TRIGGER_MODE = "TRIGGER SOURCE";
                Properties.Values.EXTERNAL_TRIGGER = "EXTERNAL";
                Properties.Values.INTERNAL_TRIGGER = "INTERNAL";
                setScanModeFast();
                setTriggerPolarity();
                isSupported = true;
                break;
            default:
                isSupported = false;
                break;
        }
    }

    public void setTriggerModeExternal() {
        try {
            core.setProperty(deviceName, Properties.Keys.TRIGGER_MODE, Properties.Values.EXTERNAL_TRIGGER);
        } catch (Exception e) {
            studio.logs().showMessage("could not set the trigger mode to external.");
        }
    }

    public void setTriggerModeInternal() {
        try {
            core.setProperty(deviceName, Properties.Keys.TRIGGER_MODE, Properties.Values.INTERNAL_TRIGGER);
        } catch (Exception e) {
            studio.logs().showMessage("could not set the trigger mode to internal.");
        }
    }

    public boolean isTriggerModeExternal() {
        String result = "";
        try {
            result = core.getProperty(deviceName, Properties.Keys.TRIGGER_MODE);
        } catch (Exception e) {
            studio.logs().showError("could not get the camera trigger mode.");
        }
        return result.equals(Properties.Values.EXTERNAL_TRIGGER);
    }

    /////// Hamamatsu
    public void setScanModeFast() {
        try {
            core.setProperty(deviceName, "ScanMode", "3");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void setTriggerPolarity() {
        try {
            core.setProperty(deviceName, "TriggerPolarity", "POSITIVE");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    ///////

    public double getExposure() {
        double exposure = 0.0;
        try {
            exposure = core.getExposure();
        } catch (Exception e) {
            studio.logs().showError("could not get exposure!");
        }
        return exposure;
    }

    public String getCameraName() {
        String result = "";
        try {
            result = core.getProperty(deviceName, "CameraName");
        } catch (Exception e) {
            studio.logs().showError("could not get the camera name!");
        }
        return result;
    }

    // Burst Acquisition

    public boolean isSequenceRunning() {
        boolean running = false;
        try {
            running = core.isSequenceRunning(deviceName);
        } catch (Exception e) {
            studio.logs().showError("could not determine if sequence is running!");
        }
        return running;
    }

    public void startSequenceAcquisition(final int numImages) {
        try {
            core.startSequenceAcquisition(deviceName, numImages, 0, true);
        } catch (Exception e) {
            studio.logs().showError("could not start sequence acquisition!");
        }
    }

    public void stopSequenceAcquisition() {
        try {
            if (isSequenceRunning()) {
                core.stopSequenceAcquisition(deviceName);
            }
        } catch (Exception e) {
            studio.logs().showError("could not stop sequence acquisition!");
        }
    }

//    public String getDeviceLibrary() {
//        String result = "";
//        try {
//            result = core.getDeviceLibrary(deviceName);
//        } catch (Exception e) {
//            studio.logs().showError("could not get the device library!");
//        }
//        return result;
//    }

//    public String getDeviceLibraryName() {
//        String result = "";
//        try {
//            result = core.getDeviceName(deviceName);
//        } catch (Exception e) {
//            studio.logs().showError("could not get the device library!");
//        }
//        return result;
//    }
}
