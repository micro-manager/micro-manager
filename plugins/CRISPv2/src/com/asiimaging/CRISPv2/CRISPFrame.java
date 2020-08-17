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

import java.awt.Color;
import java.awt.Font;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;

import org.micromanager.api.ScriptInterface;
import org.micromanager.utils.MMFrame;

import com.asiimaging.CRISPv2.ui.ButtonPanel;
import com.asiimaging.CRISPv2.ui.SpinnerPanel;
import com.asiimaging.CRISPv2.ui.StatusPanel;

import mmcorej.CMMCore;
import net.miginfocom.swing.MigLayout;

@SuppressWarnings("serial")
public class CRISPFrame extends MMFrame {
	
	// flag to turn on debug mode when editing the user interface
	private final static boolean DEBUG = false;
	
	private final ScriptInterface gui;
	private final CMMCore core;
	
	private final CRISP crisp;
	private final UserSettings settings;
	
	private JLabel title;
	private JPanel leftPanel;
	private JPanel rightPanel;
	private ButtonPanel buttonPanel;
	private StatusPanel statusPanel;
	private SpinnerPanel spinnerPanel;
	
	public CRISPFrame(final ScriptInterface app) throws Exception {
		gui = app;
		core = gui.getMMCore();
		
		crisp = new CRISP(gui);
		createUserInterface();
		
		// create the user settings after we create the ui
		// we set spinner values in the constructor
		settings = new UserSettings(crisp, spinnerPanel);
		
		// wait to find CRISP until after we create the ui
		// CRISP needs to reference ui elements to update text
		init();
		
		// load saved settings into the ui after we
		// find CRISP to send the settings to the unit
		settings.queryController();
		settings.load();
	}

	/**
	 * Find CRISP and update spinners and status.
	 * @throws Exception Autofocus device not found.
	 */
	private void init() throws Exception {
		// set references to update swing components 
		crisp.setStatusPanel(statusPanel);
		crisp.setAxisLabel(spinnerPanel.getAxisLabel());
		
		final boolean found = crisp.findAutofocusDevices();
		if (!found) {
			throw new Exception("This plugin requires an ASI CRISP Autofocus device.");
		}
		
		// get values from CRISP and update text
		spinnerPanel.update();
		statusPanel.update();
	}

	/**
	 * Stop polling values from CRISP and stop the Swing timer when the frame closes.
	 */
	private void createWindowEventHandlers() {
		addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(final WindowEvent event) {
            	// stop CRISP polling
            	if (!crisp.getDeviceName().isEmpty()) {
            		if (crisp.getDeviceType() == ASIDeviceType.TIGER) {
            			crisp.setRefreshPropertyValues(false);
            		}
	        		crisp.stopTimer();
            	}
            	// save user settings
            	settings.save();
            }
        });
	}
	
	/**
	 * Create the user interface for the plugin.
	 */
	private void createUserInterface() {
		setTitle(CRISPPlugin.menuName);
		loadAndRestorePosition(200, 200);
		setResizable(false);
		
		// stop polling when the frame is closed
		createWindowEventHandlers();

		// use MigLayout as the layout manager
		setLayout(new MigLayout(
			"insets 10 10 10 10",
			"",
			""
		));
		
		// draw the title in bold
		title = new JLabel(CRISPPlugin.menuName + " v" + CRISPPlugin.version);
		title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
		
		// create panels for ui elements
		spinnerPanel = new SpinnerPanel(crisp,
			"",
			"[]10[]",
			"[]10[]"
		);
		
		buttonPanel = new ButtonPanel(crisp,
			"",
			"[]10[]",
			"[]10[]"
		);
		
		statusPanel = new StatusPanel(crisp,
			"",
			"[]10[]",
			"[]10[]"
		);

		// main layout panels
		leftPanel = new JPanel();
		rightPanel = new JPanel();

		leftPanel.setLayout(new MigLayout(
			"",
			"[]10[]",
			"[]50[]"
		));
		
		rightPanel.setLayout(new MigLayout(
			"",
			"",
			""
		));
		
		// color the panels to make editing the ui easy
		if (DEBUG) {
			leftPanel.setBackground(Color.RED);
			rightPanel.setBackground(Color.BLUE);		
		}
		
		// add subpanels to the main layout panels
		leftPanel.add(spinnerPanel, "wrap");
		leftPanel.add(statusPanel, "center");
		rightPanel.add(buttonPanel, "");
		
		// add swing components to the frame
		add(title, "span, center, wrap");
		add(leftPanel, "");
		add(rightPanel, "");
		
		// set the window size automatically
		pack();
		
		// set the window icon to be a microscope
		setIconImage(WindowUtils.MICROSCOPE_ICON.getImage());
		
		// clean up resources when the frame is closed
		setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
	}
	
	/**
	 * TODO: implement this feature
	 * code left here for future reference
	 * this was MS2000 need to port focus curve to Tiger Device Adapter
	 */
	public void plotFocusCurve() {
//		String device = crisp.getDeviceName();
//		core.setProperty(device, "Obtain Focus Curve", "Do it");
//		core.loadDevice("Port", "SerialManager", "COM1");
//		core.setProperty("Port", "StopBits", "1");
//		core.setProperty("Port", "Parity", "None");
//		
//		core.initializeDevice("Port");
//		
//		core.setSerialPortCommand("Port", "LK F=97", "\r");
//		String answer = core.getSerialPortAnswer("Port", "\r");
//		System.out.println("ANSWER = " + answer);
		
//		String device = crisp.getDeviceName();
//		try {
//			core.setProperty(device, "SerialCommand", "who"); // "LK F=97"
//			String response = core.getProperty(device, "SerialResponse");
//			System.out.println("RESPONSE = " + response);
//		} catch (Exception e) {
//			e.printStackTrace();
//		}
//		String p = null;
//		try {
//			p = core.getProperty(crisp.getDeviceName(), "Focus Curve Data0");
//		} catch (Exception e) {
//			e.printStackTrace();
//		}
//		System.out.println(p);
		
		// load graph
//		final String filepath = "C:\\";
//		final ArrayList<String> lines = FileUtils.loadTextFile(filepath);
//		final PlotFrame plot = new PlotFrame("Focus Curve Plot");
//		plot.create("Focus Curve", "Z Position", "Error", PlotFrame.createDataset(lines));
//		plot.saveAsPNG("C:\\Users\\Brandon\\Desktop\\plot.png");
	}
}
