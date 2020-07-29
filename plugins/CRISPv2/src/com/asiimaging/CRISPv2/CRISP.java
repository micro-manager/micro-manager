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
 * Note:
 *   There is a special tag to mark methods that support legacy versions of the CRISP firmware.
 *   This tag will be located in the Javadoc of any methods that implement this behavior.
 *   The tag is as follows: <LEGACY_SUPPORT>
 *   
 *   The currently tagged methods:
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
 *     TODO: which are the modern firmware versions? "Device Property Strings" correct?
 *     
 * Manual:
 *   http://asiimaging.com/docs/crisp_manual
 *   
 */
public final class CRISP {
	
	private final ScriptInterface gui;
	private final CMMCore core;
	
	private Timer timer;
	private String deviceName;
	private int skipRefresh;
	private int skipCounter;
	private int pollingRateMs;
	private ActionListener pollingTask;
	
	// references to ui elements from the plugin
	private JLabel axisLabel;
	private StatusPanel statusPanel;
	
	public CRISP(final ScriptInterface app) {
		gui = app;
		core = gui.getMMCore();
		deviceName = "";
		
		// timer variables
		pollingRateMs = 100;
		skipRefresh = 20;
		skipCounter = 0;
	}
	
	/**
	 * Returns the name of the device as a String.
	 */
	public String getDeviceName() {
		return deviceName;
	}
	
//	private void initTimer() {
//		
//	}
	
	/**
	 * 
	 */
	public void stopTimer() {
		timer.stop();
	}
	
	/**
	 * 
	 */
//	public void startTimer() {
//		timer.start();
//	}
	
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
	 * Prints all CRISP autofocus properties to the console for use while debugging.
	 */
	public void printAllProperties() {
		try {
			final StrVector properties = core.getDevicePropertyNames(deviceName);
			for (int i = 0; i < properties.size(); i++) {
				final String property = properties.get(i);
				final String value = core.getProperty(deviceName, property);
				System.out.println("Property Name: " + property + ", Value: " + value);
			}
		} catch (Exception e) {
			ReportingUtils.showError("Failed to list all CRISP device properties!");
		}
	}
	
	/**
	 * Find the CRISP autofocus device.
	 */
	public boolean findAutofocusDevices() {
		boolean found = false;
		final StrVector autoFocusDevices = core.getLoadedDevicesOfType(DeviceType.AutoFocusDevice);
		for (String device : autoFocusDevices) {
			try {
				if (core.hasProperty(device, "Description")) {
					if (core.getProperty(device, "Description").equals("ASI CRISP Autofocus adapter") || 
						core.getProperty(device, "Description").startsWith("ASI CRISP AutoFocus")) { // this line is for Tiger
						
						found = true;
						deviceName = device;
						createPollingTask();
						timer = new Timer(pollingRateMs, pollingTask);
						final String axis = getAxis();
						final String text = device + ":" + axis;
						
						// set the axis label on the spinner panel
						if (axisLabel != null) {
							axisLabel.setText(text);
						}
						
						// start the timer and begin polling data from CRISP
						timer.start();
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
					final String seconds = Float.toString((pollingRateMs*skipCounter)/1000);
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
			setRefreshPropertyValues(true);
		} else {
			setRefreshPropertyValues(false);
			timer.stop();
		}
	}


	/**
	 * Set the polling rate of the Swing timer.
	 * 
	 * @param pollingRate The polling rate in milliseconds.
	 */
	public void setPollingRate(final int pollingRate) {
		pollingRateMs = pollingRate;
	}
	
	/**
	 * 
	 * @param state
	 */
	public void setRefreshPropertyValues(final boolean state) {
		try {
			final String value = (state == true) ? "Yes" : "No";
//			String value = "No";
//			if (state) {
//				value = "Yes";
//			}
			core.setProperty(deviceName, "RefreshPropertyValues", value);
		} catch (Exception e) {
			// TODO: trying to set RefreshPropertyValues will always throw an error so just don't report it
			// ReportingUtils.showError("Failed to set RefreshPropertyValues to " + state + ".");
		}
	}
	
	/**
	 * 
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
	 * 
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
		String snr = null;
		try {
			snr = core.getProperty(deviceName, "Signal Noise Ratio");
		} catch (Exception e1) {
			try {
				snr = core.getProperty(deviceName, "Signal to Noise Ratio");
			} catch (Exception e2) {
				// ReportingUtils.showError("Failed to read the SNR.");
				return "";
			}
		}
		return snr;
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
	 * 
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
	 * 
	 */
	public String getAxis() {
		try {
			return core.getProperty(deviceName, "Axis");
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
		String intensity;
		try {
			intensity = core.getProperty(deviceName, "LED Intensity");
		} catch (Exception e1) {
			try {
				// sometimes this property is also called led intesity with a %
				intensity = core.getProperty(deviceName, "LED Intensity(%)");
			} catch (Exception e2) {
				ReportingUtils.showError("Failed to read the LED Intensity from CRISP.");
				return 0;
			}
		}
		final int value = Integer.parseInt(intensity);
		return value;
	}
	
	/**
	 * Returns the gain multiplier.
	 * 
	 * <LEGACY_SUPPORT>
	 */
	public int getGainMultiplier() {
		String gain;
		try {
			gain = core.getProperty(deviceName, "GainMultiplier");
		} catch (Exception e1) {
			try {
				gain = core.getProperty(deviceName, "LoopGainMultiplier");
			} catch (Exception e2) {
				ReportingUtils.showError("Failed to read the Gain Multiplier from CRISP.");
				return 0;
			}
		}
		final int value = Integer.parseInt(gain);
		return value;
	}
	
	/**
	 * 
	 */
	public int getNumberOfAverages() {
		try {
			final String averages = core.getProperty(deviceName, "Number of Averages");
			final int value = Integer.parseInt(averages);
			return value;
		} catch (Exception e) {
			ReportingUtils.showError("Failed to read the Number of Averages from CRISP.");
			return 0;
		}
	}
	
	/**
	 * Returns the Objective NA.
	 */
	public float getObjectiveNA() {
		try {
			final String objNA = core.getProperty(deviceName, "Objective NA");
			final float value = Float.parseFloat(objNA);
			return value;
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
			final String lockRange = core.getProperty(deviceName, "Max Lock Range(mm)");
			final float value = Float.parseFloat(lockRange);
			return value;
		} catch (Exception e) {
			ReportingUtils.showError("Failed to read the Lock Range from CRISP.");
			return 0.0f;
		}
	}
	
	/**
	 * Sets the LED intensity.
	 * 
	 * <LEGACY_SUPPORT>
	 * 
	 * @param value
	 */
	public void setLEDIntensity(final int value) {
		try {
			core.setProperty(deviceName, "LED Intensity", value);
		} catch (Exception e1) {
			try {
				core.setProperty(deviceName, "LED Intensity(%)", value);
			} catch (Exception e2) {
				ReportingUtils.showError("Failed to set the LED intensity.");
			}
		}
	}
	

	/**
	 * Sets the gain multiplier.
	 * 
	 * <LEGACY_SUPPORT>
	 * 
	 * @param value
	 */
	public void setGainMultiplier(final int value) {
		try {
			core.setProperty(deviceName, "GainMultiplier", value);
		} catch (Exception e1) {
			try {
				core.setProperty(deviceName, "LoopGainMultiplier", value);
			} catch (Exception e2) {
				ReportingUtils.showError("Failed to set the Gain Multiplier.");
			}
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
				final String seconds = Float.toString((pollingRateMs*skipCounter)/1000);
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
}
