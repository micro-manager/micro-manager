/*
 * Project: ASI CRISP Control
 * License: BSD 3-clause, see LICENSE.md
 * Author: Brandon Simpson (brandon@asiimaging.com)
 * Copyright (c) 2014-2021, Applied Scientific Instrumentation
 */
package com.asiimaging.devices.crisp;

import java.util.ArrayList;
import java.util.Objects;

import org.micromanager.Studio;

import mmcorej.CMMCore;
import mmcorej.DeviceType;
import mmcorej.StrVector;

/**
 * The core class to control ASI CRISP Autofocus devices in Micro-Manager.
 *
 * <p>This class can be used with both the ASIStage and ASITiger device adapters.
 *
 * <p>You need to call {@code findDevice()} before calling other methods on the
 * {@code CRISP} class.
 *
 * <p>Example:
 * <blockquote><pre>
 * CRISP crisp = new CRISP();
 * crisp.detectDevice();
 * crisp.getAxis(); // OK to call now
 * </pre></blockquote>
 * Documentation:
 * <blockquote>
 * <a href="http://asiimaging.com/docs/crisp_manual">ASI CRISP Manual</a>
 * </blockquote>
 */
public class CRISP {

    private final Studio studio;
    private final CMMCore core;

    /** The name of the device. */
    private String deviceName;

    /** The version number of the firmware. */
    private double firmwareVersion;

    /** The letter after the firmware version number (MS2000 only). */
    private char firmwareVersionLetter;

    /** The type of controller CRISP is connected to. */
    private ControllerType deviceType;

    /** Number of MM::Strings used for the focus curve data property. */
    private static final int FC_DATA_SIZE = 24;

    /** Software settings stored in the plugin. */
    private final ArrayList<CRISPSettings> settings;

    /** Currently selected software settings profile index. */
    private int settingsIndex = 0;

    /** Device adapter names for CRISP. */
    private static final class DeviceLibrary {
        public static final String TIGER  = "ASITiger";
        public static final String MS2000  = "ASIStage";
    }

    /** Stores the device descriptions for TIGER and MS2000. */
    private static final class Description {
        public static final String TIGER  = "ASI CRISP AutoFocus";
        public static final String MS2000 = "ASI CRISP Autofocus adapter";
    }
    
    /**
     * Constructs a new CRISP device to be used in a Micro-Manager plugin.
     * 
     * @param studio the {@link Studio} instance
     */
    public CRISP(final Studio studio) {
        this.studio = Objects.requireNonNull(studio);
        this.core = studio.core();

        deviceName = "";
        deviceType = ControllerType.NONE;
        settings = new ArrayList<>();

        firmwareVersion = 0.0;
        firmwareVersionLetter = ' ';

        // always start with the default settings
        settings.add(new CRISPSettings(CRISPSettings.DEFAULT_PROFILE_NAME));
    }
    
    @Override
    public String toString() {
        return String.format(
                "%s[deviceName=%s, deviceType=%s]",
                getClass().getSimpleName(),
                deviceName, deviceType
        );
    }

    /**
     * @return the ArrayList of software settings
     */
    public ArrayList<CRISPSettings> getSettingsList() {
        return settings;
    }

    public CRISPSettings getSettings() {
        return settings.get(settingsIndex);
    }

    public CRISPSettings getSettingsByIndex(final int index) {
        return settings.get(index);
    }

    /**
     * @return the number of software settings
     */
    public int getNumSettings() {
        return settings.size();
    }

    /**
     * Increases the number of software settings.
     *
     * @return the name of the profile
     */
    public String addSettings() {
        final String name = CRISPSettings.NAME_PREFIX + settings.size();
        settings.add(new CRISPSettings(name));
        return name;
    }

    /**
     * Removes the last software settings profile in {@code settings}.
     *
     * @return true if successful
     */
    public boolean removeSettings() {
        final int size = settings.size();
        if (size == 1) {
            return false;
        } else {
            settings.remove(size - 1);
            return true;
        }
    }

    public void setSettingsIndex(final int index) {
        settingsIndex = index;
    }
    
    // query CRISP through serial
    public CRISPSettings getSettingsFromDevice() {
        return new CRISPSettings(
            "Current Values",
            getGain(),
            getLEDIntensity(),
            getUpdateRateMs(),
            getNumAverages(),
            getObjectiveNA(),
            getLockRange()
        );
    }
    
    /**
     * Returns true if the device was detected and sets deviceType and deviceName.
     * 
     * @return true if the device was detected
     */
    public boolean detectDevice() {
        boolean found = false;
        final StrVector devices = core.getLoadedDevicesOfType(DeviceType.AutoFocusDevice);
        for (final String device : devices) {
            final String deviceLibrary = getDeviceLibrary(device);
            if (deviceLibrary.equals(DeviceLibrary.TIGER)) {
                if (getDescription(device).startsWith(Description.TIGER)) {
                    deviceType = ControllerType.TIGER;
                    deviceName = device;
                    found = true;
                    break;
                }
            } else if (deviceLibrary.equals(DeviceLibrary.MS2000)) {
                if (getDescription(device).equals(Description.MS2000)) {
                    deviceType = ControllerType.MS2000;
                    deviceName = device;
                    found = true;
                    break;
                }
            }
        }
        if (found) {
            // set firmwareVersion and firmwareVersionLetter
            setFirmwareVersion();
        }
        return found;
    }
    
    public boolean isTiger() {
        return deviceType == ControllerType.TIGER;
    }
    
    public boolean isMS2000() {
        return deviceType == ControllerType.MS2000;
    }
    
    public ControllerType getDeviceType() {
        return deviceType;
    }

    public String getDeviceLibrary(final String deviceName) {
        String deviceLibrary = "";
        try {
            deviceLibrary = core.getDeviceLibrary(deviceName);
        } catch (Exception e) {
            studio.logs().showError("Problem getting device library!");
        }
        return deviceLibrary;
    }

    /**
     * Returns the name of the device. 
     *
     * <p>If no device has been detected, it returns an empty {@code String}.
     * 
     * @return the name of the device
     */
    public String getDeviceName() {
        return deviceName;
    }

    public double getFirmwareVersion() {
        return firmwareVersion;
    }

    public char getFirmwareVersionLetter() {
        return firmwareVersionLetter;
    }

    // TODO: base class for generic methods? FirmwareVersion in prop names?
    /**
     * Sets firmwareVersion and firmwareVersionLetter by querying the device and parsing the String.
     */
    private void setFirmwareVersion() {
        try {
            if (deviceType == ControllerType.TIGER) {
                final String version = core.getProperty(deviceName, "FirmwareVersion");
                firmwareVersion = Double.parseDouble(version);
            } else { // MS2000
                final String version = core.getProperty(deviceName,"Version");
                final String v = version.split("-")[1];
                firmwareVersion = Double.parseDouble(v.substring(0, v.length()-2));
                firmwareVersionLetter = v.charAt(v.length()-2);
            }
        } catch (Exception e) {
            studio.logs().showError("could not get the firmware version!");
        }
    }

    /**
     * Returns {@code true} if the device is in the "In Focus" state.
     *
     * @return {@code true} if the device is focus locked
     */
    public boolean isFocusLocked() {
        return getState().equals("In Focus");
    }
    
    // NOTE: this is a long-running task, use a separate thread when calling this
    public void getFocusCurve() {
        try {
            core.setProperty(deviceName, PropName.MS2000.OBTAIN_FOCUS_CURVE, PropValue.MS2000.DO_IT);
        } catch (Exception e) {
            studio.logs().showError("Failed to obtain the focus curve.");
        }
    }
    
    /**
     * Returns part of the focus curve.
     *
     * <p>Used to get one of the {@code MM::String}s that stores part of the focus curve data.
     *
     * @param n the index of the focus curve data
     * @return a part of the focus curve data as a String
     */
    private String getFocusCurveData(final int n) {
        String result = "";
        try {
            result = core.getProperty(deviceName, PropName.MS2000.FOCUS_CURVE_DATA_PREFIX + n);
        } catch (Exception e) {
            studio.logs().showError("could not get the data from " + PropName.MS2000.FOCUS_CURVE_DATA_PREFIX + n);
        }
        return result;
    }
    
    /**
     * Returns all of the focus curve data available.
     * 
     * @return the focus curve data concatenated into a {@code String}
     */
    public String getAllFocusCurveData() {
        final StringBuilder result = new StringBuilder();
        for (int i = 0; i < FC_DATA_SIZE; i++) {
            result.append(getFocusCurveData(i));
        }
        return result.toString();
    }

    /**
     * Returns the description in the device property browser of the device.
     *
     * @param deviceName the name of the device
     * @return the device description
     */
    private String getDescription(final String deviceName) {
        String description = "";
        try {
            description = core.getProperty(deviceName, PropName.DESCRIPTION);
        } catch (Exception ignore) {
            // ignore any exceptions => we only care about devices we can read
        }
        return description;
    }

    // +-----------------------------------+
    // |        Getters and Setters        |
    // +-----------------------------------+
    
    /**
     * Returns an information {@code String} to display on the user interface.
     *
     * <p>The {@code String} is in the form (controller type : device name : axis letter).
     *
     * @return the device information string
     */
    public String getAxisString() {
        String result = "";
        switch (deviceType) {
            case TIGER:
                // The deviceName on Tiger controller contains the Axis Letter
                result = deviceType + ":" + deviceName;
                break;
            case MS2000:
                // The MS2000 needs to query the controller to get the Axis Letter
                result = deviceType + ":" + deviceName + ":" + getAxis();
                break;
        }
        return result;
    }

    /**
     * This method only works on Tiger, "RefreshPropertyValues" only exists there.
     * 
     * @param state true to refresh property values
     */
    public void setRefreshPropertyValues(final boolean state) {
        try {
            core.setProperty(deviceName, PropName.REFRESH_PROP_VALUES, state ? PropValue.YES : PropValue.NO);
        } catch (Exception e) {
            //studio.logs().showError("CRISP: Failed to set RefreshPropertyValues to " + state + ".");
        }
    }
    
    /**
     * Returns the current state of CRISP.
     * 
     * @return the current state
     */
    public String getState() {
        String result = "";
        try {
            result = core.getProperty(deviceName, PropName.CRISP_STATE);
        } catch (Exception e) {
            //studio.logs().showError("CRISP: Failed to read the current state.");
        }
        return result;
    }
    
    /**
     * Returns the dither error.
     * 
     * @return the dither error
     */
    public String getDitherError() {
        String result = "";
        try {
            result = core.getProperty(deviceName, PropName.DITHER_ERROR);
        } catch (Exception e) {
            //studio.logs().showError("CRISP: Failed to read the Dither Error.");
        }
        return result;
    }

    /**
     * Returns the signal to noise ratio.
     * 
     * @return the signal to noise ratio
     */
    public String getSNR() {
        String result = "";
        try {
            result = core.getProperty(deviceName, PropName.SNR);
        } catch (Exception e) {
            //studio.logs().showError("CRISP: Failed to read the SNR.");
        }
        return result;
    }
    
    /**
     * Returns the AGC.
     * 
     * @return the agc
     */
    public String getAGC() {
        String result = "";
        try {
            result = core.getProperty(deviceName, PropName.LOG_AMP_AGC);
        } catch (Exception e) {
            //studio.logs().showError("CRISP: Failed to read the AGC.");
        }
        return result;
    }

    /**
     * Returns the sum.
     * 
     * @return the sum
     */
    public String getSum() {
        String result = "";
        try {
            result = core.getProperty(deviceName, PropName.SUM);
        } catch (Exception e) {
            //studio.logs().showError("CRISP: Failed to read the Sum.");
        }
        return result;
    }
    
    /**
     * Returns the autofocus offset.
     * 
     * @return the autofocus offset
     */
    public double getOffset() {
        double result = 0.0;
        try {
            result = core.getAutoFocusOffset();
        } catch (Exception e) {
            //studio.logs().showError("CRISP: Failed to read the Autofocus Offset.");
        }
        return result;
    }
    
    /**
     * Returns the autofocus offset as a {@code String}.
     * 
     * @return the autofocus offset
     */
    public String getOffsetString() {
        String result = "";
        try {
            result = Double.toString(core.getAutoFocusOffset());
        } catch (Exception e) {
            //studio.logs().showError("CRISP: Failed to read the Autofocus Offset.");
        }
        return result;
    }

    /**
     * Returns the axis CRISP is set to control.
     *
     * @return axis information String
     */
    public String getAxis() {
        String result = "";
        try {
            result = core.getProperty(deviceName, 
                isTiger() ? PropName.TIGER.AXIS_LETTER : PropName.MS2000.AXIS_LETTER);
        } catch (Exception e) {
            //studio.logs().showError("CRISP: Failed to read the axis letter.");
        }
        return result;
    }

    /**
     * Returns the LED intensity.
     * 
     * @return the LED intensity
     */
    public int getLEDIntensity() {
        int result = 0;
        try {
            result = Integer.parseInt(core.getProperty(deviceName, PropName.LED_INTENSITY));
        } catch (Exception e) {
            //studio.logs().showError("CRISP: Failed to read the LED Intensity.");
        }
        return result;
    }
    
    /**
     * Returns the gain multiplier.
     * 
     * @return the gain multiplier
     */
    public int getGain() {
        int result = 0;
        try {
            result = Integer.parseInt(core.getProperty(deviceName, PropName.GAIN));
        } catch (Exception e) {
            //studio.logs().showError("CRISP: Failed to read the Gain Multiplier.");
        }
        return result;
    }
    
    /**
     * Returns the number of averages.
     * 
     * @return the number of average
     */
    public int getNumAverages() {
        int result = 0;
        try {
            result = Integer.parseInt(core.getProperty(deviceName, PropName.NUMBER_OF_AVERAGES));
        } catch (Exception e) {
            //studio.logs().showError("CRISP: Failed to read the Number of Averages.");
        }
        return result;
    }
    
    /**
     * Returns the objective's numerical aperture.
     * 
     * @return the objective's NA
     */
    public float getObjectiveNA() {
        float result = 0.0f;
        try {
            result = Float.parseFloat(core.getProperty(deviceName, PropName.OBJECTIVE_NA));
        } catch (Exception e) {
            //studio.logs().showError("CRISP: Failed to read the Objective NA.");
        }
        return result;
    }
    
     /**
     * Returns the number of skips.
     *
     * @return the number of skips
     */
    public int getUpdateRateMs() {
        int result = 0;
        try {
            result = Integer.parseInt(core.getProperty(deviceName, PropName.NUMBER_OF_SKIPS));
        } catch (Exception e) {
            //studio.logs().showError("CRISP: Failed to read the Number of Skips.");
        }
        return result;
    }
    
    /**
     * Returns the lock range.
     * 
     * @return the lock range
     */
    public float getLockRange() {
        float result = 0.0f;
        try {
            result = Float.parseFloat(core.getProperty(deviceName, PropName.MAX_LOCK_RANGE));
        } catch (Exception e) {
            //studio.logs().showError("CRISP: Failed to read the Lock Range.");
        }
        return result;
    }

    /**
     * Sets the LED intensity.
     * 
     * @param value the LED intensity
     */
    public void setLEDIntensity(final int value) {
        try {
            core.setProperty(deviceName, PropName.LED_INTENSITY, value);
        } catch (Exception e) {
            //studio.logs().showError("CRISP: Failed to set the LED intensity.");
        }
    }

    /**
     * Sets the gain multiplier.
     * 
     * @param value the gain multiplier
     */
    public void setGain(final int value) {
        try {
            core.setProperty(deviceName, PropName.GAIN, value);
        } catch (Exception e) {
            //studio.logs().showError("CRISP: Failed to set the Gain Multiplier.");
        }       
    }
    
    /**
     * Sets the number of averages.
     *
     * @param value the number of averages
     */
    public void setNumAverages(final int value) {
        try {
            core.setProperty(deviceName, PropName.NUMBER_OF_AVERAGES, value);
        } catch (Exception e) {
            //studio.logs().showError("CRISP: Failed to set the Number of Averages.");
        }
    }
    
     /**
     * Sets the number of skips.
     *
     * @param value the number of skips
     */
    public void setUpdateRateMs(final int value) {
        try {
            core.setProperty(deviceName, PropName.NUMBER_OF_SKIPS, value);
        } catch (Exception e) {
            //studio.logs().showError("CRISP: Failed to set the Number of Skips.");
        }
    }
    
    /**
     * Sets the numerical aperture of the objective.
     *
     * @param value the objective NA
     */
    public void setObjectiveNA(final float value) {
        try {
            core.setProperty(deviceName, PropName.OBJECTIVE_NA, value);
        } catch (Exception e) {
            //studio.logs().showError("CRISP: Failed to set the Objective NA.");
        }
    }

    /**
     * Sets the lock range in millimeters.
     * 
     * @param value the new lock range in millimeters
     */
    public void setLockRange(final float value) {
        try {
            core.setProperty(deviceName, PropName.MAX_LOCK_RANGE, value);
        } catch (Exception e) {
            //studio.logs().showError("Failed to set the Lock Range");
        }
    }
    
    /**
     * Sets the CRISP state to Idle.
     */
    public void setStateIdle() {
        try {
            core.setProperty(deviceName, PropName.CRISP_STATE, PropValue.STATE_IDLE);
        } catch (Exception e) {
            studio.logs().showError("Failed to set the state to Idle");
        }
    }

    /**
     * Sets the CRISP state to Log Cal.
     */
    public void setStateLogCal() {
        try {
            core.setProperty(deviceName, PropName.CRISP_STATE, PropValue.STATE_LOG_CAL);
        } catch (Exception e) {
            studio.logs().showError("Failed to set the state to loG_cal");
        }
    }

    /**
     * Sets the CRISP state to Log Cal and restart the timer.
     *
     * <p>The controller becomes unresponsive during Log Cal,
     * so we skip polling for a few timer ticks.
     * 
     * @param timer the {@link CRISPTimer} to interact with
     */
    public void setStateLogCal(final CRISPTimer timer) {
        try {
            // controller becomes unresponsive during loG_cal => skip polling for a few timer ticks
            if (deviceType == ControllerType.TIGER) {
                if (firmwareVersion < 3.38) {
                    timer.onLogCal();
                }
            } else { // MS2000 (this has been fixed in Whizkid at least since ~2016)
                if (firmwareVersion < 9.2 && firmwareVersionLetter < 'j') {
                    timer.onLogCal();
                }
            }
            core.setProperty(deviceName, PropName.CRISP_STATE, PropValue.STATE_LOG_CAL);
        } catch (Exception e) {
            studio.logs().showError("Failed to set the state to loG_cal");
        }
    }
    
    /**
     * Sets the CRISP state to Dither.
     */
    public void setStateDither() {
        try {
            core.setProperty(deviceName, PropName.CRISP_STATE, PropValue.STATE_DITHER);
        } catch (Exception e) {
            studio.logs().showError("Failed to set the state to Dither");
        }
    }
    
    /**
     * Sets the CRISP state to Gain Cal.
     */
    public void setStateGainCal() {
        try {
            core.setProperty(deviceName, PropName.CRISP_STATE, PropValue.STATE_GAIN_CAL);
        } catch (Exception e) {
            studio.logs().showError("Failed to set the state to gain_Cal");
        }
    }

    /**
     * Sets the CRISP state to Reset Focus Offset.
     */
    public void setResetOffsets() {
        try {
            core.setProperty(deviceName, PropName.CRISP_STATE, PropValue.RESET_FOCUS_OFFSET);
        } catch (Exception e) {
            studio.logs().showError("Failed to reset the focus offsets.");
        }
    }
    
    /**
     * Locks the CRISP.
     */
    public void lock() {
        try {
            core.enableContinuousFocus(true);
        } catch (Exception e) {
            studio.logs().showError("Failed to lock.");
        }
    }
    
    /**
     * Unlocks the CRISP.
     */
    public void unlock() {
        try {
            core.enableContinuousFocus(false);
        } catch (Exception e) {
            studio.logs().showError("Failed to unlock.");
        }
    }
    
    /**
     * Save current settings to the device firmware.
     */
    public void save() {
        try {
            core.setProperty(deviceName, PropName.CRISP_STATE, PropValue.SAVE_TO_CONTROLLER);
        } catch (Exception e) {
            studio.logs().showError("Failed to save settings to the controller.");
        }
    }

    // TODO: put these methods into a base class
    public void setOnlySendSerialCommandOnChange(final boolean state) {
        try {
            core.setProperty("TigerCommHub", "OnlySendSerialCommandOnChange", state ? "Yes" : "No");
        } catch (Exception e) {
            studio.logs().logError("TigerCommHub could not set OnlySendSerialCommandOnChange to " + state);
        }
    }

    public void sendSerialCommand(final String command) {
        try {
            core.setProperty("TigerCommHub", "SerialCommand", command);
        } catch (Exception e) {
            studio.logs().logError("TigerCommHub could not send the serial command.");
        }
    }

    public String getSerialResponse() {
        String result = "";
        try {
            result = core.getProperty("TigerCommHub", "SerialResponse");
        } catch (Exception e) {
            studio.logs().logError("TigerCommHub could not get the serial response.");
        }
        return result;
    }
}
