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

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.JLabel;
import javax.swing.Timer;

import org.micromanager.api.ScriptInterface;
import org.micromanager.utils.ReportingUtils;

import com.asiimaging.CRISPv2.ui.StatusPanel;

import mmcorej.CMMCore;
import mmcorej.DeviceType;
import mmcorej.StrVector;

/**
 * This is a utility class for the ASI CRISP autofocus device.
 * 
 * There is a Swing timer that polls the CRISP device at the polling rate set by the user.
 * Everytime the timer fires it updates the user interface with values from CRISP.
 * 
 * To update the UI you have pass in the object references to access the components you need.
 * 
 * You first need to call findAutofocusDevices() before you can query properties from CRISP.
 * 
 * A few properties have had their name change over the years and those changes are detected when
 * the user calls findAutofocusDevices().
 * 
 *   Methods that need to propNameXXXX:
 *   	1) getSNR() 
 *   	2) getLEDIntensity()
 *   	3) getGainMultiplier()
 *   	4) setLEDIntensity()
 *      5) setGainMultiplier()
 *   
 *   Properties that changed: SNR, LED Intensity, Gain Multiplier
 *   
 *   Device Property Strings:
 *     Signal Noise Ratio = Signal to Noise Ratio
 *     LED Intensity      = LED Intensity(%)
 *     GainMultiplier     = LoopGainMultiplier
 *     
 * Manual:
 *   http://asiimaging.com/docs/crisp_manual
 *   
 */

public final class CRISP {
	
	private final ScriptInterface gui;
	private final CMMCore core;
	
	private String deviceName;
	private ASIDeviceType deviceType;
	
	// timer variables
	private Timer timer;
	private int pollRateMs;
	private int skipRefresh;
	private int skipCounter;
	private ActionListener pollingTask;
	
	// references to ui elements from the plugin
	private JLabel axisLabel;
	private StatusPanel statusPanel;
	
	// these property names are detected at runtime
	private String propNameLEDIntensity;
	private String propNameGain;
	private String propNameSNR;
	
	public CRISP(final ScriptInterface app) {
		gui = app;
		core = gui.getMMCore();
		deviceName = "";
		deviceType = null;
		
		// timer variables
		pollRateMs = 250;
		skipRefresh = 20;
		skipCounter = 0;
	}
	
	/**
	 * Returns the name of the device.
	 * 
	 * @return deviceName
	 */
	public String getDeviceName() {
		return deviceName;
	}
	

	/**
	 * Returns the type of controller.
	 * 
	 * @return deviceType
	 */
	public String getDeviceTypeString() {
		return deviceType.toString();
	}
	
	/**
	 * Returns the type of controller.
	 * 
	 * @return deviceType
	 */
	public ASIDeviceType getDeviceType() {
		return deviceType;
	}
	
	/**
	 * Returns the polling rate in milliseconds.
	 * 
	 * @return
	 */
	public int getPollRateMs() {
		return pollRateMs;
	}
	
//	private void initTimer() {
//		
//	}
	
	/**
	 * This is used by the window closing event handler to stop the timer on exit.
	 */
	public void stopTimer() {
		timer.stop();
	}
	
	/**
	 * 
	 * @param label
	 */
	public void setAxisLabel(final JLabel label) {
		axisLabel = label;
	}
	
	/**
	 * 
	 * @param panel
	 */
	public void setStatusPanel(final StatusPanel panel) {
		statusPanel = panel;
	}
	
	/**
	 * Detects the type of controller the plugin is communicating with.
	 * 
	 * @param deviceName The name of the device to check.
	 * @return true if the device is a CRISP Autofocus unit.
	 */
	public boolean isDeviceCRISP(final String deviceName) throws Exception {
		boolean found = false;
		if (core.getProperty(deviceName, "Description").equals("ASI CRISP Autofocus adapter")) {
			deviceType = ASIDeviceType.MS2000;
			found = true;
		}
		if (core.getProperty(deviceName, "Description").startsWith("ASI CRISP AutoFocus")) {
			deviceType = ASIDeviceType.TIGER;
			found =  true;
		}
		return found;
	}
	
	/**
	 * Find the CRISP autofocus device.
	 * 
	 * @return true if we found the device.
	 */
	public boolean findAutofocusDevices() {
		boolean found = false;
		final StrVector autoFocusDevices = core.getLoadedDevicesOfType(DeviceType.AutoFocusDevice);
		for (final String device : autoFocusDevices) {
			try { 
				if (isDeviceCRISP(device)) {
					// deviceType is detected in isDeviceCRISP
					found = true;
					deviceName = device;
					
					// determine what property names to use
					detectPropertyNames();
					
					// set the axis label on the spinner panel
					// deviceName on Tiger contains the Axis Letter
					final String text = deviceType.toString() + ":" + deviceName;
					if (axisLabel != null) {
						if (deviceType == ASIDeviceType.TIGER) {
							axisLabel.setText(text);
						} else {
							axisLabel.setText(text + ":" + getAxis());
						}
					}
					
					createPollingTask(); // pollingTask set here
					timer = new Timer(pollRateMs, pollingTask);
					
					// start the timer and begin polling data
					timer.start();
					if (deviceType == ASIDeviceType.TIGER) {
						setRefreshPropertyValues(true);
					}
				}
			} catch (Exception e) {
				Logger.getLogger(CRISP.class.getName()).log(Level.SEVERE, null, e);
			}
		}
		return found;
	}
	
	/**
	 * Create the event handler that is called at the polling rate in milliseconds.
	 */
	public void createPollingTask() {
		pollingTask = new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent event) {
				if (skipCounter > 0) {
					skipCounter--;
					// update the CRISP state JLabel
					final String seconds = Float.toString((pollRateMs*skipCounter)/1000);
					final String text = "Calibrating..." + seconds + "s";
					statusPanel.getStateLabel().setText(text);
				} else {
					// call CRISP methods that wrap getProperty() to update JLabels
					statusPanel.update();
				}
			}
		};
	}
	
	/**
	 * Sets the polling state of CRISP.
	 * 
	 * Note: starts and stops the Swing pollingTask() Timer.
	 * 
	 * @param state Enabled or disabled
	 */
	public void setPollingState(final boolean state) {
		if (state) {
			timer.start();
			if (deviceType == ASIDeviceType.TIGER) {
				setRefreshPropertyValues(true);
			}
		} else {
			if (deviceType == ASIDeviceType.TIGER) {
				setRefreshPropertyValues(false);
			}
			timer.stop();
		}
	}

	/**
	 * Set the polling rate of the Swing timer.
	 * 
	 * @param pollingRate The polling rate in milliseconds.
	 */
	public void setPollRateMs(final int pollingRate) {
		pollRateMs = pollingRate;
		timer.setDelay(pollRateMs);
	}
	
	/**
	 * This method only works on Tiger, "RefreshPropertyValues" only exists there.
	 * 
	 * @param state
	 */
	public void setRefreshPropertyValues(final boolean state) {
		try {
			core.setProperty(deviceName, "RefreshPropertyValues", (state == true) ? "Yes" : "No");
		} catch (Exception e) {
			ReportingUtils.showError("Failed to set RefreshPropertyValues to " + state + ".");
		}
	}
	
	// the following methods that return "" because we only need a String to update the panel
	
	/**
	 * Returns the current state of CRISP.
	 */
	public String getState() {
		try {
			return core.getProperty(deviceName, "CRISP State");
		} catch (Exception e) {
			// ReportingUtils.showError("Failed to read the CRISP state.");
			return "";
		}
	}
	
	/**
	 * Returns the dither error.
	 */
	public String getDitherError() {
		try {
			return core.getProperty(deviceName, "Dither Error");
		} catch (Exception e) {
			// ReportingUtils.showError("Failed to read the Dither Error.");
			return "";
		}
	}

	/**
	 * Returns the signal to noise ratio.
	 * 
	 * <LEGACY_SUPPORT>
	 */
	public String getSNR() {
		try {
			return core.getProperty(deviceName, propNameSNR);
		} catch (Exception e) {
			// ReportingUtils.showError("Failed to read the SNR.");
			return "";
		}
	}
	
	/**
	 * Returns the AGC.
	 */
	public String getAGC() {
		try {
			return core.getProperty(deviceName, "LogAmpAGC");
		} catch (Exception e) {
			// ReportingUtils.showError("Failed to read the AGC.");
			return "";
		}
	}
	
	/**
	 * Returns the sum.
	 */
	public String getSum() {
		try {
			return core.getProperty(deviceName, "Sum");
		} catch (Exception e) {
			// ReportingUtils.showError("Failed to read the Sum.");
			return "";
		}
	}
	
	/**
	 * Returns the offset.
	 */
	public String getOffset() {
		try {
			return Double.toString(core.getAutoFocusOffset());
		} catch (Exception e) {
			// ReportingUtils.showError("Failed to read the Autofocus Offset.");
			return "";
		}
	}
	
	/**
	 * Returns the axis CRISP is set to control.
	 */
	public String getAxis() {
		final String axisPropertyName = (deviceType == ASIDeviceType.TIGER) ? "AxisLetter" : "Axis";
		try {
			return core.getProperty(deviceName, axisPropertyName);
		} catch (Exception e) {
			ReportingUtils.showError("Failed to read the Axis from CRISP.");
			return "";
		}
	}

	/**
	 * Returns the LED intensity.
	 * 
	 * <LEGACY_SUPPORT>
	 */
	public int getLEDIntensity() {
		try {
			return Integer.parseInt(core.getProperty(deviceName, propNameLEDIntensity));
		} catch (Exception e) {
			ReportingUtils.showError("Failed to read the LED Intensity from CRISP.");
			return 0;
		}
	}
	
	/**
	 * Returns the gain multiplier.
	 * 
	 * <LEGACY_SUPPORT>
	 */
	public int getGainMultiplier() {
		try {
			return Integer.parseInt(core.getProperty(deviceName, propNameGain));
		} catch (Exception e) {
			ReportingUtils.showError("Failed to read the Gain Multiplier from CRISP.");
			return 0;
		}
	}
	
	/**
	 * Returns the number of averages.
	 */
	public int getNumberOfAverages() {
		try {
			return Integer.parseInt(core.getProperty(deviceName, "Number of Averages"));
		} catch (Exception e) {
			ReportingUtils.showError("Failed to read the Number of Averages from CRISP.");
			return 0;
		}
	}
	
	/**
	 * Returns the objective numerical aperture.
	 */
	public float getObjectiveNA() {
		try {
			return Float.parseFloat(core.getProperty(deviceName, "Objective NA"));
		} catch (Exception e) {
			ReportingUtils.showError("Failed to read the Objective NA from CRISP.");
			return 0.0f;
		}
	}
	
	/**
	 * Returns the lock range.
	 */
	public float getLockRange() {
		try {
			return Float.parseFloat(core.getProperty(deviceName, "Max Lock Range(mm)"));
		} catch (Exception e) {
			ReportingUtils.showError("Failed to read the Lock Range from CRISP.");
			return 0.0f;
		}
	}

	/**
	 * Sets the LED intensity.
	 * 
	 * @param value
	 */
	public void setLEDIntensity(final int value) {
		try {
			core.setProperty(deviceName, propNameLEDIntensity, value);
		} catch (Exception e) {
			ReportingUtils.showError("Failed to set the LED intensity.");
		}
	}

	/**
	 * Sets the gain multiplier.
	 * 
	 * @param value
	 */
	public void setGainMultiplier(final int value) {
		try {
			core.setProperty(deviceName, propNameGain, value);
		} catch (Exception e) {
			ReportingUtils.showError("Failed to set the Gain Multiplier.");
		}		
	}
	
	/**
	 * 
	 * @param value
	 */
	public void setNumberOfAverages(final int value) {
		try {
			core.setProperty(deviceName, "Number of Averages", value);
		} catch (Exception e) {
			ReportingUtils.showError("Failed to set the Number of Averages.");
		}
	}
	
	/**
	 * 
	 * @param value
	 */
	public void setObjectiveNA(final float value) {
		try {
			core.setProperty(deviceName, "Objective NA", value);
		} catch (Exception e) {
			ReportingUtils.showError("Failed to set the Objective NA.");
		}
	}
	
	/**
	 * 
	 * @param value
	 */
	public void setLockRange(final float value) {
		try {
			core.setProperty(deviceName, "Max Lock Range(mm)", value);
		} catch (Exception e) {
			ReportingUtils.showError("Failed to set the Lock Range.");
		}
	}
	
	/**
	 * Sets the CRISP state to Idle.
	 */
	public void setStateIdle() {
		try {
			core.setProperty(deviceName, "CRISP State", "Idle");
		} catch (Exception e) {
			ReportingUtils.showError("Failed to set the CRISP state to Idle.");
		}
	}
	
	/**
	 * Sets the CRISP state to Log Cal.
	 */
	public void setStateLogCal() {
		try {
			// controller becomes unresponsive during Log Cal, skip polling a few times
			if (timer.isRunning()) {
				skipCounter = skipRefresh;
				timer.restart();
				final String seconds = Float.toString((pollRateMs*skipCounter)/1000);
				statusPanel.getStateLabel().setText("Calibrating..." + seconds + "s");
			}
			core.setProperty(deviceName, "CRISP State", "loG_cal");
		} catch (Exception e) {
			ReportingUtils.showError("Failed to set the CRISP state to Log Cal.");
		}
	}
	
	/**
	 * Sets the CRISP state to Dither.
	 */
	public void setStateDither() {
		try {
			core.setProperty(deviceName, "CRISP State", "Dither");
		} catch (Exception e) {
			ReportingUtils.showError("Failed to set the CRISP state to Dither.");
		}
	}
	
	/**
	 * Sets the CRISP state to Gain Cal.
	 */
	public void setStateGainCal() {
		try {
			core.setProperty(deviceName, "CRISP State", "gain_Cal");
		} catch (Exception e) {
			ReportingUtils.showError("Failed to set the CRISP state to Set Gain.");
		}		
	}
	
	/**
	 * 
	 */
	public void resetOffsets() {
		try {
			core.setProperty(deviceName, "CRISP State", "Reset Focus Offset");
		} catch (Exception e) {
			ReportingUtils.showError("Failed to reset the focus offset.");
		}
	}
	
	/**
	 * Locks CRISP.
	 */
	public void lock() {
		try {
			core.enableContinuousFocus(true);
		} catch (Exception e) {
			ReportingUtils.displayNonBlockingMessage("Failed to lock.");
		}
	}
	
	/**
	 * Unlocks CRISP.
	 */
	public void unlock() {
		try {
			core.enableContinuousFocus(false);
		} catch (Exception e) {
			ReportingUtils.displayNonBlockingMessage("Failed to unlock.");
		}
	}
	
	/**
	 * Save settings to the CRISP device.
	 */
	public void save() {
		try {
			core.setProperty(deviceName, "CRISP State", "Save to Controller");
		} catch (Exception e) {
			ReportingUtils.showError("Failed to aquire the focus curve.");
		}
	}
	
	// useful helper methods, they return null on failure
	// TODO: consider using Optional in Java 8 for MM 2.0
	
	public void printDeviceProperties() {
		final StrVector properties = getDevicePropertyNames(deviceName);
		for (int i = 0; i < properties.size(); i++) {
		    final String property = properties.get(i);
		    final String value = getProperty(deviceName, property);
		    System.out.println("Property Name: " + property + ", Value: " + value);
		}
	}
	
	private StrVector getDevicePropertyNames(final String deviceName) {
		StrVector properties = null;
		try {
			properties = core.getDevicePropertyNames(deviceName);
		} catch (Exception e) {
			ReportingUtils.showError("Failed to get device property names!");
		}
		return properties;
	}
	
	private String getProperty(final String deviceName, final String property) {
		String value = null;
		try {
			value = core.getProperty(deviceName, property);
		} catch (Exception e) {
			ReportingUtils.showError("Failed to get property " + property + " from device " + deviceName);
		}
		return value;
	}
	
	/**
	 * Detect property names from the CRISP device and sets the names that 
	 * vary across device adapters. Any additional variations discovered  
	 * should be handled here.
	 * 
	 */
	public void detectPropertyNames() {
		final StrVector properties = getDevicePropertyNames(deviceName);
		
		for (int i = 0; i < properties.size(); i++) {
		    final String property = properties.get(i);
		    
		    if (property.equals("Signal Noise Ratio") || 
		    	property.equals("Signal to Noise Ratio")) {
		    	propNameSNR = property;		    	
		    }
		    
		    if (property.equals("GainMultiplier") || 
		    	property.equals("LoopGainMultiplier")) {
		    	propNameGain = property;
		    }
		    
		    if (property.equals("LED Intensity") || 
		    	property.equals("LED Intensity(%)")) {
		    	propNameLEDIntensity = property;
		    }
		}
		
		// System.out.println("propNameSNR: " + propNameSNR);
		// System.out.println("propNameGain: " + propNameGain);
		// System.out.println("propNameLEDIntensity: " + propNameLEDIntensity);
	}
}
