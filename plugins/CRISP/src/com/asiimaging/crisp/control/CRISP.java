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

package com.asiimaging.crisp.control;

import javax.swing.JComponent;
import javax.swing.JOptionPane;
import javax.swing.SwingWorker;

import org.micromanager.api.ScriptInterface;
import org.micromanager.utils.ReportingUtils;

import com.asiimaging.crisp.panels.PlotPanel;

import mmcorej.CMMCore;
import mmcorej.DeviceType;
import mmcorej.StrVector;

/**
 * The core class to control ASI CRISP Autofocus devices in Micro-Manager.
 * <p>
 * This class can be used with both the ASIStage and ASITiger device adapters.
 * <p>
 * You need to call {@code findDevice()} before using the {@code CRISP} class.
 * <p>
 * Example:
 * <blockquote><pre>
 * CRISP crisp = new CRISP();
 * crisp.findDevice();
 * crisp.getAxis(); // OK to call now
 * </pre></blockquote>
 * Documentation:
 * <blockquote>
 * <a href="http://asiimaging.com/docs/crisp_manual">ASI CRISP Manual</a>
 * </blockquote>
 */
public final class CRISP {
    
    @SuppressWarnings("unused")
    private final ScriptInterface gui;
    private final CMMCore core;

    private String deviceName;
    private ControllerType deviceType;
    
    // number of MM:Strings used for the focus curve data property
    private static final int FC_DATA_SIZE = 24;
    
    /** Stores the device descriptions. */
    private final class Description {
        public static final String TIGER  = "ASI CRISP AutoFocus";
        public static final String MS2000 = "ASI CRISP Autofocus adapter";
    }
    
    /**
     * Constructs a new CRISP object to be used in a Micro-Manager plugin.
     * 
     * @param studio the {@link ScriptInterface} instance
     */
    public CRISP(final ScriptInterface gui) {
        this.core = gui.getMMCore();
        this.gui = gui;
        deviceName = "";
        deviceType = ControllerType.NONE;
    }
    
    // Constructor for Micro-Manager 2.0
    // public CRISP(final Studio studio) {
    //     core = studio.core();
    //     deviceName = "";
    //     deviceType = ControllerType.NONE;
    // }
    
    @Override
    public String toString() {
        return String.format("%s[deviceName=%s, deviceType=%s]", 
            getClass().getSimpleName(), 
            deviceName.isEmpty() ? "\"\"" : deviceName, 
            deviceType);
    }
    
    /**
     * Returns true if the device was detected.
     * 
     * @return true if the device was detected
     */
    public boolean detectDevice() {
        boolean found = false;
        final StrVector devices = core.getLoadedDevicesOfType(DeviceType.AutoFocusDevice);
        for (final String device : devices) {
            final String text = getDescription(device);
            if (isDescriptionTiger(text)) {
                deviceType = ControllerType.TIGER;
                deviceName = device;
                found = true;
                break;
            }
            if (isDescriptionMS2000(text)) {
                deviceType = ControllerType.MS2000;
                deviceName = device;
                found = true;
                break;
            }
        }
        return found;
    }
    
    private boolean isDescriptionMS2000(final String text) {
        return text.equals(Description.MS2000);
    }
    
    private boolean isDescriptionTiger(final String text) {
        return text.startsWith(Description.TIGER);
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
    
    /**
     * Returns the name of the device. 
     * <p>
     * If no device has been detected, it returns an empty {@code String}, 
     * otherwise it returns the {@code String} set during the {@code findDevice()} method.
     * 
     * @return the name of the device
     */
    public String getDeviceName() {
        return deviceName;
    }
    
    public boolean isFocusLocked() {
        return getState().equals("In Focus"); // TODO: propValue?
    }
    
    // TODO: prevent user from running this twice with done() method
    public void getFocusCurve(final PlotPanel panel) {
        // thread is mostly here to prevent edt hang logger from complaining
        final SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {
            
            @Override
            protected Void doInBackground() throws Exception {
                try {
                    core.setProperty(deviceName, PropName.MS2000.OBTAIN_FOCUS_CURVE, PropValue.MS2000.DO_IT);
                } catch (Exception e) {
                    reportError("Failed to obtain the focus curve.");
                }
                return null;
            }
            
            @Override
            protected void done() {
                panel.showPlotWindow();
                panel.getPlotButton().setEnabled(true);
            }
            
        };
        worker.execute();
    }
    
    /**
     * Returns part of the focus curve.
     * <p>
     * Used to get one of the {@code MM::String}s that stores part of the focus curve data.
     * @param n 
     * @return
     */
    private String getFocusCurveData(final int n) {
        String result = "";
        try {
            result = core.getProperty(deviceName, PropName.MS2000.FOCUS_CURVE_DATA_PREFIX + n);
        } catch (Exception e) {
            //
        }
        return result;
    }
    
    /**
     * Returns all of the focus curve data available.
     * 
     * @return the focus curve data concatenated into a {@code String}
     */
    public String getAllFocusCurveData() {
        String result = "";
        for (int i = 0; i < FC_DATA_SIZE; i++) {
            result += getFocusCurveData(i);
        }
        return result;
    }
    
    private String getDescription(final String deviceName) {
        String description = "";
        try {
            description = core.getProperty(deviceName, PropName.DESCRIPTION);
        } catch (Exception ignore) {
            // ignore any exceptions -> we only care about devices we can read
        }
        return description;
    }

    // +-----------------------------------+
    // |        Getters and Setters        |
    // +-----------------------------------+
    
    /**
     * Returns an information {@code String} to display on the user interface.
     * <p>
     * The {@code String} is in the form &ltcontroller type : device name : axis letter>.
     * <p>
     * Note: This method is more efficient on {@code TIGER}.
     * On {@code MS2000} the {@code getAxis()} method is called 
     * which requires serial communications through the controller.
     * 
     * @return {@code TIGER} or {@code MS2000} -> the information {@code String},<br>
     *         {@code NONE} -> an empty {@code String}
     */
    public String getAxisString() {
        String result = "";
        if (isTiger()) {
            // deviceName on the Tiger controller contains the Axis Letter
            result = deviceType.toString() + ":" + deviceName;
        } else if (isMS2000()) {
            result = deviceType.toString() + ":" + deviceName + ":" + getAxis();
        }
        return result;
    }
    
    // TODO: elaborate on this method and what it does
    /**
     * This method only works on Tiger, "RefreshPropertyValues" only exists there.
     * 
     * @param state true to refresh property values
     */
    public void setRefreshPropertyValues(final boolean state) {
        try {
            core.setProperty(deviceName, PropName.REFRESH_PROP_VALUES, state ? PropValue.YES : PropValue.NO);
        } catch (Exception e) {
            //logger.logError("CRISP: Failed to set RefreshPropertyValues to " + state + ".");
        }
    }
    
    /**
     * Returns the current state of CRISP.
     * 
     * @return
     */
    public String getState() {
        String result = "";
        try {
            result = core.getProperty(deviceName, PropName.CRISP_STATE);
        } catch (Exception e) {
            //logger.logError("CRISP: Failed to read the current state.");
        }
        return result;
    }
    
    /**
     * Returns the dither error.
     * 
     * @return
     */
    public String getDitherError() {
        String result = "";
        try {
            result = core.getProperty(deviceName, PropName.DITHER_ERROR);
        } catch (Exception e) {
            //logger.logError("CRISP: Failed to read the Dither Error.");
        }
        return result;
    }

    /**
     * Returns the signal to noise ratio.
     * 
     * @return
     */
    public String getSNR() {
        String result = "";
        try {
            result = core.getProperty(deviceName, PropName.SNR);
        } catch (Exception e) {
            //logger.logError("CRISP: Failed to read the SNR.");
        }
        return result;
    }
    
    /**
     * Returns the AGC.
     * 
     * @return
     */
    public String getAGC() {
        String result = "";
        try {
            result = core.getProperty(deviceName, PropName.LOG_AMP_AGC);
        } catch (Exception e) {
            //logger.logError("CRISP: Failed to read the AGC.");
        }
        return result;
    }
    

    /**
     * Returns the sum.
     * 
     * @return
     */
    public String getSum() {
        String result = "";
        try {
            result = core.getProperty(deviceName, PropName.SUM);
        } catch (Exception e) {
            //logger.logError("CRISP: Failed to read the Sum.");
        }
        return result;
    }
    
    /**
     * Returns the autofocus offset.
     * 
     * @return
     */
    public double getOffset() {
        double result = 0.0;
        try {
            result = core.getAutoFocusOffset();
        } catch (Exception e) {
            //logger.logError("CRISP: Failed to read the Autofocus Offset.");
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
            //logger.logError("CRISP: Failed to read the Autofocus Offset.");
        }
        return result;
    }
    
    /**
     * Returns the axis CRISP is set to control.
     * 
     * @return
     */
    public String getAxis() {
        String result = "";
        try {
            result = core.getProperty(deviceName, 
                isTiger() ? PropName.TIGER.AXIS_LETTER : PropName.MS2000.AXIS_LETTER);
        } catch (Exception e) {
            //logger.logError("CRISP: Failed to read the axis letter.");
        }
        return result;
    }

    /**
     * Returns the LED intensity.
     * 
     * @return
     */
    public int getLEDIntensity() {
        int result = 0;
        try {
            result = Integer.parseInt(core.getProperty(deviceName, PropName.LED_INTENSITY));
        } catch (Exception e) {
            //logger.logError("CRISP: Failed to read the LED Intensity.");
        }
        return result;
    }
    
    /**
     * Returns the gain multiplier.
     * 
     * @return
     */
    public int getGain() {
        int result = 0;
        try {
            result = Integer.parseInt(core.getProperty(deviceName, PropName.GAIN));
        } catch (Exception e) {
            //logger.logError("CRISP: Failed to read the Gain Multiplier.");
        }
        return result;
    }
    
    /**
     * Returns the number of averages.
     * 
     * @return
     */
    public int getNumAverages() {
        int result = 0;
        try {
            result = Integer.parseInt(core.getProperty(deviceName, PropName.NUMBER_OF_AVERAGES));
        } catch (Exception e) {
            //logger.logError("CRISP: Failed to read the Number of Averages.");
        }
        return result;
    }
    
    /**
     * Returns the objective's numerical aperture.
     * 
     * @return
     */
    public float getObjectiveNA() {
        float result = 0.0f;
        try {
            result = Float.parseFloat(core.getProperty(deviceName, PropName.OBJECTIVE_NA));
        } catch (Exception e) {
            //logger.logError("CRISP: Failed to read the Objective NA.");
        }
        return result;
    }
    
    /**
     * Returns the lock range.
     * 
     * @return
     */
    public float getLockRange() {
        float result = 0.0f;
        try {
            result = Float.parseFloat(core.getProperty(deviceName, PropName.MAX_LOCK_RANGE));
        } catch (Exception e) {
            //logger.logError("CRISP: Failed to read the Lock Range.");
        }
        return result;
    }

    /**
     * Sets the LED intensity.
     * 
     * @param value
     */
    public void setLEDIntensity(final int value) {
        try {
            core.setProperty(deviceName, PropName.LED_INTENSITY, value);
        } catch (Exception e) {
            //logger.logError("CRISP: Failed to set the LED intensity.");
        }
    }

    /**
     * Sets the gain multiplier.
     * 
     * @param value
     */
    public void setGain(final int value) {
        try {
            core.setProperty(deviceName, PropName.GAIN, value);
        } catch (Exception e) {
            //logger.logError("CRISP: Failed to set the Gain Multiplier.");
        }       
    }
    
    /**
     * 
     * @param value
     */
    public void setNumAverages(final int value) {
        try {
            core.setProperty(deviceName, PropName.NUMBER_OF_AVERAGES, value);
        } catch (Exception e) {
            //logger.logError("CRISP: Failed to set the Number of Averages.");
        }
    }
    
    /**
     * 
     * @param value
     */
    public void setObjectiveNA(final float value) {
        try {
            core.setProperty(deviceName, PropName.OBJECTIVE_NA, value);
        } catch (Exception e) {
            //logger.logError("CRISP: Failed to set the Objective NA.");
        }
    }
    
    // TODO: document what the lock range means
    /**
     * Sets the lock range in millimeters.
     * 
     * @param value the new lock range in millimeters
     */
    public void setLockRange(final float value) {
        try {
            core.setProperty(deviceName, PropName.MAX_LOCK_RANGE, value);
        } catch (Exception e) {
            reportError("Failed to set the state to Lock Range");
        }
    }
    
    /**
     * Sets the CRISP state to Idle.
     */
    public void setStateIdle() {
        try {
            core.setProperty(deviceName, PropName.CRISP_STATE, PropValue.STATE_IDLE);
        } catch (Exception e) {
            reportError("Failed to set the state to Idle");
        }
    }

    /**
     * Sets the CRISP state to Log Cal.
     */
    public void setStateLogCal() {
        try {
            core.setProperty(deviceName, PropName.CRISP_STATE, PropValue.STATE_LOG_CAL);
        } catch (Exception e) {
            reportError("Failed to set the state to loG_cal");
        }
    }
    
    public void setStateLogCal(final CRISPTimer timer) {
        try {
            // controller becomes unresponsive during loG_cal, skip polling a few times
            timer.onLogCal();
            core.setProperty(deviceName, PropName.CRISP_STATE, PropValue.STATE_LOG_CAL);
        } catch (Exception e) {
            reportError("Failed to set the state to loG_cal");
        }
    }
    
    /**
     * Sets the CRISP state to Dither.
     */
    public void setStateDither() {
        try {
            core.setProperty(deviceName, PropName.CRISP_STATE, PropValue.STATE_DITHER);
        } catch (Exception e) {
            reportError("Failed to set the state to Dither");
        }
    }
    
    /**
     * Sets the CRISP state to Gain Cal.
     */
    public void setStateGainCal() {
        try {
            core.setProperty(deviceName, PropName.CRISP_STATE, PropValue.STATE_GAIN_CAL);
        } catch (Exception e) {
            reportError("Failed to set the state to gain_Cal");
        }
    }
    
    /**
     * 
     */
    public void setResetOffsets() {
        try {
            core.setProperty(deviceName, PropName.CRISP_STATE, PropValue.RESET_FOCUS_OFFSET);
        } catch (Exception e) {
            reportError("Failed to reset the focus offsets.");
        }
    }
    
    /**
     * Locks the device.
     */
    public void lock() {
        try {
            core.enableContinuousFocus(true);
        } catch (Exception e) {
            //ReportingUtils.displayNonBlockingMessage("Failed to lock.");
            reportError("Failed to lock.");
        }
    }
    
    /**
     * Unlocks the device.
     */
    public void unlock() {
        try {
            core.enableContinuousFocus(false);
        } catch (Exception e) {
            //ReportingUtils.displayNonBlockingMessage("Failed to unlock.");
            reportError("Failed to unlock.");
        }
    }
    
    /**
     * Save current settings to the device.
     */
    public void save() {
        try {
            core.setProperty(deviceName, PropName.CRISP_STATE, PropValue.SAVE_TO_CONTROLLER);
        } catch (Exception e) {
            reportError("Failed to save settings to the controller.");
        }
    }
    
    public void reportError(final JComponent component, final String message) {
        JOptionPane.showMessageDialog(component,
            "Problem writing",
            "Error",
            JOptionPane.ERROR_MESSAGE
        );
    }
    
    public void reportError(final String message) {
        ReportingUtils.showError(message);
    }
}
