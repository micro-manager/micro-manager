///////////////////////////////////////////////////////////////////////////////
//FILE:          ShowFlagsPanel.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Arthur Edelstein, 2009
//
// COPYRIGHT:    University of California, San Francisco, 2009
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
//
//
package org.micromanager.utils;

import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JLayeredPane;

import mmcorej.CMMCore;
import mmcorej.Configuration;
import mmcorej.DeviceType;

public class ShowFlagsPanel extends JLayeredPane {

	/**
	 * 
	 */
	private static final long serialVersionUID = 2414705031299832388L;
	private JCheckBox showCamerasCheckBox_;
	private JCheckBox showShuttersCheckBox_;
	private JCheckBox showStagesCheckBox_;
	private JCheckBox showStateDevicesCheckBox_;
	private JCheckBox showOtherCheckBox_;
	private Configuration initialCfg_;

	private ShowFlags flags_;
	private PropertyTableData data_;
	private CMMCore core_;


	public ShowFlagsPanel(PropertyTableData data, ShowFlags flags, CMMCore core, Configuration initialCfg) {
		data_ = data;
		flags_ = flags;
		core_ = core;
		initialCfg_ = initialCfg;
		setBorder(BorderFactory.createTitledBorder("Show"));
		createCheckboxes();
		initializeCheckboxes();
	}

	public void createCheckboxes() {
		showCamerasCheckBox_ = new JCheckBox();
		showCamerasCheckBox_.setFont(new Font("", Font.PLAIN, 10));
		showCamerasCheckBox_.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				flags_.cameras_ = showCamerasCheckBox_.isSelected();
				data_.updateRowVisibility(flags_);
			}
		});
		showCamerasCheckBox_.setText("cameras");
		add(showCamerasCheckBox_);
		showCamerasCheckBox_.setBounds(5,20,130,20);

		showShuttersCheckBox_ = new JCheckBox();
		showShuttersCheckBox_.setFont(new Font("", Font.PLAIN, 10));
		showShuttersCheckBox_.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				flags_.shutters_ = showShuttersCheckBox_.isSelected();
				data_.updateRowVisibility(flags_);
			}
		});
		showShuttersCheckBox_.setText("shutters");
		add(showShuttersCheckBox_);
		showShuttersCheckBox_.setBounds(5,40,130,20);

		showStagesCheckBox_ = new JCheckBox();
		showStagesCheckBox_.setFont(new Font("", Font.PLAIN, 10));
		showStagesCheckBox_.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				flags_.stages_ = showStagesCheckBox_.isSelected();
				data_.updateRowVisibility(flags_);
			}
		});
		showStagesCheckBox_.setText("stages");
		add(showStagesCheckBox_);
		showStagesCheckBox_.setBounds(5,60,130,20);

		showStateDevicesCheckBox_ = new JCheckBox();
		showStateDevicesCheckBox_.setFont(new Font("", Font.PLAIN, 10));
		showStateDevicesCheckBox_.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				flags_.state_ = showStateDevicesCheckBox_.isSelected();
				data_.updateRowVisibility(flags_);
			}
		});
		showStateDevicesCheckBox_.setText("wheels, turrets, etc.");
		add(showStateDevicesCheckBox_);
		showStateDevicesCheckBox_.setBounds(5,80,130,20);

		showOtherCheckBox_ = new JCheckBox();
		showOtherCheckBox_.setFont(new Font("", Font.PLAIN, 10));
		showOtherCheckBox_.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				flags_.other_ = showOtherCheckBox_.isSelected();
				data_.updateRowVisibility(flags_);
			}
		});
		showOtherCheckBox_.setText("other devices");
		add(showOtherCheckBox_);
		showOtherCheckBox_.setBounds(5,100,130,20);
	}

	protected void initializeCheckboxes() {
		try {

			// Setup checkboxes to reflect saved flags_ settings 
			showCamerasCheckBox_.setSelected(flags_.cameras_);
			showStagesCheckBox_.setSelected(flags_.stages_);
			showShuttersCheckBox_.setSelected(flags_.shutters_);
			showStateDevicesCheckBox_.setSelected(flags_.state_);
			showOtherCheckBox_.setSelected(flags_.other_);

			// get properties contained in the current config

			// change 'show' flags to always show contained devices
			for (int i=0; i< initialCfg_.size(); i++) {
				DeviceType dtype = core_.getDeviceType(initialCfg_.getSetting(i).getDeviceLabel());
				if (dtype == DeviceType.CameraDevice) {
					flags_.cameras_ = true;
					showCamerasCheckBox_.setSelected(true);
				} else if (dtype == DeviceType.ShutterDevice) {
					flags_.shutters_ = true;
					showShuttersCheckBox_.setSelected(true);
				} else if (dtype == DeviceType.StageDevice) {
					flags_.stages_ = true;
					showStagesCheckBox_.setSelected(true);
				} else if (dtype == DeviceType.StateDevice) {
					flags_.state_ = true;
					showStateDevicesCheckBox_.setSelected(true);
				} else {
					showOtherCheckBox_.setSelected(true);
					flags_.other_ = true;;
				}
			}
		} catch (Exception e) {
			handleException(e);
		}
	}
	
	private void handleException(Exception e) {
		ReportingUtils.logError(e);
	}

}
