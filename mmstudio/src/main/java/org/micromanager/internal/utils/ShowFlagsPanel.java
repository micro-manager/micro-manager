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
import java.awt.Insets;
import java.awt.event.ActionEvent;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import mmcorej.CMMCore;
import mmcorej.Configuration;
import mmcorej.DeviceType;
import net.miginfocom.swing.MigLayout;

public final class ShowFlagsPanel extends JPanel {

   // TODO consider changing checkbox ActionListeners to ItemListeners so that we can trigger
   //   the desired behavior using setSelected() programatically

   private static final long serialVersionUID = 2414705031299832388L;
         // need to change? added new field
   private JCheckBox showCamerasCheckBox_;
   private JCheckBox showShuttersCheckBox_;
   private JCheckBox showStagesCheckBox_;
   private JCheckBox showStateDevicesCheckBox_;
   private JCheckBox showOtherCheckBox_;
   private JTextField searchFilterText_;
   private JCheckBox showReadonlyCheckBox_;
   private final Configuration initialCfg_;

   private final ShowFlags flags_;
   private final PropertyTableData data_;
   private final CMMCore core_;

   public ShowFlagsPanel(PropertyTableData data, ShowFlags flags, CMMCore core,
                         Configuration initialCfg) {
      data_ = data;
      flags_ = flags;
      core_ = core;
      initialCfg_ = initialCfg;
      // We need very little vertical space between components.
      setLayout(new MigLayout("fill, insets 0, gap 0"));
      createComponents();
      initializeComponents();
   }

   public void createComponents() {
      Font font = new Font("", Font.PLAIN, 10);
      Font entryFont = new Font("", Font.PLAIN, 12);

      JPanel deviceTypePanel = new JPanel(new MigLayout("fill, insets 0, gap -3"));
      deviceTypePanel.setBorder(BorderFactory.createTitledBorder("Device type:"));

      JButton showAllTypesButton = new JButton("All");
      showAllTypesButton.setFont(font);
      showAllTypesButton.addActionListener((ActionEvent arg0) -> {
         selectAllTypes(true);
      });
      deviceTypePanel.add(showAllTypesButton, "growx, split 2, gapbottom 3");

      JButton showNoTypesButton = new JButton("None");
      showNoTypesButton.setFont(font);
      showNoTypesButton.addActionListener((ActionEvent arg0) -> {
         selectAllTypes(false);
      });
      deviceTypePanel.add(showNoTypesButton, "growx, wrap");

      showCamerasCheckBox_ = new JCheckBox("cameras");
      showCamerasCheckBox_.setFont(font);
      showCamerasCheckBox_.addActionListener((ActionEvent arg0) -> {
         flags_.cameras_ = showCamerasCheckBox_.isSelected();
         data_.setFlags(flags_);
         data_.refresh(true);
      });
      deviceTypePanel.add(showCamerasCheckBox_, "wrap");

      showShuttersCheckBox_ = new JCheckBox("shutters");
      showShuttersCheckBox_.setFont(font);
      showShuttersCheckBox_.addActionListener((ActionEvent arg0) -> {
         flags_.shutters_ = showShuttersCheckBox_.isSelected();
         data_.setFlags(flags_);
         data_.refresh(true);
      });
      deviceTypePanel.add(showShuttersCheckBox_, "wrap");

      showStagesCheckBox_ = new JCheckBox("stages");
      showStagesCheckBox_.setFont(font);
      showStagesCheckBox_.addActionListener((ActionEvent arg0) -> {
         flags_.stages_ = showStagesCheckBox_.isSelected();
         data_.setFlags(flags_);
         data_.refresh(true);
      });
      deviceTypePanel.add(showStagesCheckBox_, "wrap");

      showStateDevicesCheckBox_ = new JCheckBox("wheels, turrets, etc.");
      showStateDevicesCheckBox_.setFont(font);
      showStateDevicesCheckBox_.addActionListener((ActionEvent arg0) -> {
         flags_.state_ = showStateDevicesCheckBox_.isSelected();
         data_.setFlags(flags_);
         data_.refresh(true);
      });
      deviceTypePanel.add(showStateDevicesCheckBox_, "wrap");

      showOtherCheckBox_ = new JCheckBox("other devices");
      showOtherCheckBox_.setFont(font);
      showOtherCheckBox_.addActionListener((ActionEvent arg0) -> {
         flags_.other_ = showOtherCheckBox_.isSelected();
         data_.setFlags(flags_);
         data_.refresh(true);
      });
      deviceTypePanel.add(showOtherCheckBox_, "wrap");

      add(deviceTypePanel, "gapbottom 10, wrap");

      add(new JLabel("Device or property name:"), "gapleft 3, growx, wrap");
      searchFilterText_ = new JTextField();
      searchFilterText_.setFont(entryFont);
      searchFilterText_.getDocument().addDocumentListener(
            new DocumentListener() {
               @Override
               public void changedUpdate(DocumentEvent e) {
                  updateSearchFilter();
               }

               @Override
               public void insertUpdate(DocumentEvent e) {
                  updateSearchFilter();
               }

               @Override
               public void removeUpdate(DocumentEvent e) {
                  updateSearchFilter();
               }
            });
      add(searchFilterText_, "gapleft 3, gapbottom 10, growx, split 2");
      JButton clearFilterButton = new JButton("Clear");
      clearFilterButton.setFont(font);
      clearFilterButton.setMargin(new Insets(3, 6, 3, 6));
      clearFilterButton.addActionListener((ActionEvent e) -> {
         searchFilterText_.setText("");
      });
      add(clearFilterButton, "gapbottom 10, growy, aligny center, wrap");

      JPanel propertyTypePanel = new JPanel(new MigLayout("fill, insets 0, gap -3"));
      propertyTypePanel.setBorder(BorderFactory.createTitledBorder("Property type:"));

      showReadonlyCheckBox_ = new JCheckBox("Show read-only");
      showReadonlyCheckBox_.setFont(font);
      showReadonlyCheckBox_.addActionListener((ActionEvent e) -> {
         // show/hide read-only properties
         data_.setShowReadOnly(showReadonlyCheckBox_.isSelected());
         data_.update(false);
         data_.fireTableStructureChanged();
      });
      propertyTypePanel.add(showReadonlyCheckBox_, "wrap");

      add(propertyTypePanel, "growx");
   }

   private void selectAllTypes(boolean all) {
      showCamerasCheckBox_.setSelected(all);
      flags_.cameras_ = all;
      showShuttersCheckBox_.setSelected(all);
      flags_.shutters_ = all;
      showStagesCheckBox_.setSelected(all);
      flags_.stages_ = all;
      showStateDevicesCheckBox_.setSelected(all);
      flags_.state_ = all;
      showOtherCheckBox_.setSelected(all);
      flags_.other_ = all;
      data_.setFlags(flags_);
      data_.refresh(true);
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
         showReadonlyCheckBox_.setSelected(flags_.readonly_);
         data_.setShowReadOnly(showReadonlyCheckBox_.isSelected());

         // get properties contained in the current config

         // change 'show' flags to always show contained devices
         for (int i = 0; i < initialCfg_.size(); i++) {
            DeviceType dtype = core_.getDeviceType(initialCfg_.getSetting(i).getDeviceLabel());
            if (dtype == DeviceType.CameraDevice) {
               flags_.cameras_ = true;
               showCamerasCheckBox_.setSelected(true);
            }
            else if (dtype == DeviceType.ShutterDevice) {
               flags_.shutters_ = true;
               showShuttersCheckBox_.setSelected(true);
            }
            else if (dtype == DeviceType.StageDevice) {
               flags_.stages_ = true;
               showStagesCheckBox_.setSelected(true);
            }
            else if (dtype == DeviceType.StateDevice) {
               flags_.state_ = true;
               showStateDevicesCheckBox_.setSelected(true);
            }
            else {
               showOtherCheckBox_.setSelected(true);
               flags_.other_ = true;
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
