///////////////////////////////////////////////////////////////////////////////
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
package org.micromanager.internal.utils;

import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.BorderFactory;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

import mmcorej.CMMCore;
import mmcorej.Configuration;
import mmcorej.DeviceType;

import net.miginfocom.swing.MigLayout;

public class ShowFlagsPanel extends JPanel {

   private static final long serialVersionUID = 2414705031299832388L;
   private JCheckBox showCamerasCheckBox_;
   private JCheckBox showShuttersCheckBox_;
   private JCheckBox showStagesCheckBox_;
   private JCheckBox showStateDevicesCheckBox_;
   private JCheckBox showOtherCheckBox_;
   private JTextField searchFilterText_;
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
      // We need very little vertical space between components.
      setLayout(new MigLayout("fill, insets 0, gap -3"));
      createComponents();
      initializeComponents();
   }

   public void createComponents() {
      Font font = new Font("", Font.PLAIN, 10);
      showCamerasCheckBox_ = new JCheckBox("cameras");
      showCamerasCheckBox_.setFont(font);
      showCamerasCheckBox_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent arg0) {
            flags_.cameras_ = showCamerasCheckBox_.isSelected();
            data_.updateRowVisibility(flags_);
         }
      });
      add(showCamerasCheckBox_, "wrap");

      showShuttersCheckBox_ = new JCheckBox("shutters");
      showShuttersCheckBox_.setFont(font);
      showShuttersCheckBox_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent arg0) {
            flags_.shutters_ = showShuttersCheckBox_.isSelected();
            data_.updateRowVisibility(flags_);
         }
      });
      add(showShuttersCheckBox_, "wrap");

      showStagesCheckBox_ = new JCheckBox("stages");
      showStagesCheckBox_.setFont(font);
      showStagesCheckBox_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent arg0) {
            flags_.stages_ = showStagesCheckBox_.isSelected();
            data_.updateRowVisibility(flags_);
         }
      });
      add(showStagesCheckBox_, "wrap");

      showStateDevicesCheckBox_ = new JCheckBox("wheels, turrets, etc.");
      showStateDevicesCheckBox_.setFont(font);
      showStateDevicesCheckBox_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent arg0) {
            flags_.state_ = showStateDevicesCheckBox_.isSelected();
            data_.updateRowVisibility(flags_);
         }
      });
      add(showStateDevicesCheckBox_, "wrap");

      showOtherCheckBox_ = new JCheckBox("other devices");
      showOtherCheckBox_.setFont(font);
      showOtherCheckBox_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent arg0) {
            flags_.other_ = showOtherCheckBox_.isSelected();
            data_.updateRowVisibility(flags_);
         }
      });
      add(showOtherCheckBox_, "wrap");

      add(new JLabel("Filter by name:"), "gaptop 1, gapbottom 1, wrap");
      searchFilterText_ = new JTextField();
      searchFilterText_.getDocument().addDocumentListener(
            new DocumentListener() {
         @Override
         public void changedUpdate(DocumentEvent e) {
            updateSearchFilter();
         }
         public void insertUpdate(DocumentEvent e) {
            updateSearchFilter();
         }
         public void removeUpdate(DocumentEvent e) {
            updateSearchFilter();
         }
      });
      add(searchFilterText_, "growx, wrap");
   }

   private void updateSearchFilter() {
      flags_.searchFilter_ = searchFilterText_.getText();
      data_.updateRowVisibility(flags_);
   }

   protected void initializeComponents() {
      try {

         // Setup checkboxes to reflect saved flags_ settings 
         showCamerasCheckBox_.setSelected(flags_.cameras_);
         showStagesCheckBox_.setSelected(flags_.stages_);
         showShuttersCheckBox_.setSelected(flags_.shutters_);
         showStateDevicesCheckBox_.setSelected(flags_.state_);
         showOtherCheckBox_.setSelected(flags_.other_);
         searchFilterText_.setText(flags_.searchFilter_);

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
