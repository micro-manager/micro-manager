// Snap-on-Move Preview for Micro-Manager
//
// Author: Mark A. Tsuchida
//
// Copyright (C) 2016 Open Imaging, Inc.
//
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions are met:
//
// 1. Redistributions of source code must retain the above copyright notice,
// this list of conditions and the following disclaimer.
//
// 2. Redistributions in binary form must reproduce the above copyright notice,
// this list of conditions and the following disclaimer in the documentation
// and/or other materials provided with the distribution.
//
// 3. Neither the name of the copyright holder nor the names of its
// contributors may be used to endorse or promote products derived from this
// software without specific prior written permission.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
// AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
// IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
// ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
// LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
// CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
// SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
// INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
// CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
// ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
// POSSIBILITY OF SUCH DAMAGE.

package org.micromanager.plugins.snaponmove;

import java.awt.Component;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import mmcorej.CMMCore;
import mmcorej.DeviceType;
import net.miginfocom.swing.MigLayout;
import org.micromanager.internal.dialogs.ComponentTitledBorder;
import org.micromanager.internal.utils.MMDialog;
import org.micromanager.plugins.snaponmove.ChangeCriterion.XYDistanceCriterion;
import org.micromanager.plugins.snaponmove.ChangeCriterion.ZDistanceCriterion;

final class CriterionDialog extends MMDialog {
   private final MainController controller_;
   private ChangeCriterion result_;

   private final List<JPanel> sectionPanels_ =
         new ArrayList<JPanel>();

   private double focusThreshUm_ = 0.1;
   private double xyThreshUm_ = 0.1;

   private final JPanel focusPanel_;
   private final JPanel xyPanel_;

   private final JRadioButton focusRadio_;
   private final JRadioButton xyRadio_;
   private final JComboBox focusDeviceCombo_;
   private final JComboBox xyDeviceCombo_;
   private final JTextField focusThreshField_;
   private final JTextField xyThreshField_;
   private final JCheckBox shouldPollCheckBox_;

   CriterionDialog(final MainController controller, final Frame owner) {
      this(controller, null, owner);
   }

   CriterionDialog(final MainController controller,
           final ChangeCriterion initialCriterion,
           final Frame owner)
   {
      super(owner);
      controller_ = controller;

      setModal(true);
      setResizable(false);
      setLocationRelativeTo(owner);

      setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
      addWindowListener(new WindowAdapter() {
         @Override
         public void windowClosing(WindowEvent e) {
            handleCancel();
         }
      });

      setTitle("Edit Movement Detection Criterion");
      setLayout(new MigLayout("fill",
              "[fill, grow]",
              "[]rel[]rel[]unrel[]"));

      // Radio buttons for section titles
      final ButtonGroup radioGroup = new ButtonGroup();

      focusPanel_ = new JPanel(new MigLayout("fill"));
      focusRadio_ = new JRadioButton("Focus Movement");
      focusRadio_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            disableOtherSections(focusPanel_);
            repaint();
         }
      });
      radioGroup.add(focusRadio_);

      xyPanel_ = new JPanel(new MigLayout("fill"));
      xyRadio_ = new JRadioButton("XY Stage Movement");
      xyRadio_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            disableOtherSections(xyPanel_);
            repaint();
         }
      });
      radioGroup.add(xyRadio_);

      // Focus Movement section
      focusPanel_.setBorder(new ComponentTitledBorder(focusRadio_,
            focusPanel_, BorderFactory.createEtchedBorder()));
      focusPanel_.add(new JLabel("Focus Device: "));
      focusDeviceCombo_ = new JComboBox(getAvailableFocusDevices());
      focusPanel_.add(focusDeviceCombo_, "wrap");
      focusPanel_.add(new JLabel("Threshold: "));
      focusThreshField_ = new JTextField(Double.toString(focusThreshUm_));
      focusThreshField_.setColumns(5);
      focusThreshField_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            double threshUm = focusThreshUm_;
            try {
               threshUm = Double.parseDouble(focusThreshField_.getText());
            }
            catch (NumberFormatException nfe) {
            }
            if (threshUm >= 0.0) {
               focusThreshUm_ = threshUm;
            }
            focusThreshField_.setText(Double.toString(threshUm));
            focusThreshField_.transferFocusUpCycle();
         }
      });
      focusPanel_.add(focusThreshField_, "split 2");
      focusPanel_.add(new JLabel(" um"), "wrap");
      add(focusPanel_, "grow, wrap");
      sectionPanels_.add(focusPanel_);

      // XY Stage Movement section
      xyPanel_.setBorder(new ComponentTitledBorder(xyRadio_,
            xyPanel_, BorderFactory.createEtchedBorder()));
      xyPanel_.add(new JLabel("XY Stage Device: "));
      xyDeviceCombo_ = new JComboBox(getAvailableXYStageDevices());
      xyPanel_.add(xyDeviceCombo_, "wrap");
      xyPanel_.add(new JLabel("Threshold: "));
      xyThreshField_ = new JTextField(Double.toString(xyThreshUm_));
      xyThreshField_.setColumns(5);
      xyThreshField_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            double threshUm = xyThreshUm_;
            try {
               threshUm = Double.parseDouble(xyThreshField_.getText());
            }
            catch (NumberFormatException nfe) {
            }
            if (threshUm >= 0.0) {
               xyThreshUm_ = threshUm;
            }
            xyThreshField_.setText(Double.toString(threshUm));
            xyThreshField_.transferFocusUpCycle();
         }
      });
      xyPanel_.add(xyThreshField_, "split 2");
      xyPanel_.add(new JLabel(" um"), "wrap");
      add(xyPanel_, "grow, wrap");
      sectionPanels_.add(xyPanel_);

      shouldPollCheckBox_ = new JCheckBox("Poll this device");
      add(shouldPollCheckBox_);

      final JButton cancelButton = new JButton("Cancel");
      cancelButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            handleCancel();
         }
      });
      add(cancelButton, "split 2, tag cancel");
      final JButton okButton = new JButton("OK");
      okButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            handleOK();
         }
      });
      add(okButton, "tag ok, wrap");

      // Initial selection and availability
      focusRadio_.setEnabled(focusDeviceCombo_.getItemCount() > 0);
      xyRadio_.setEnabled(xyDeviceCombo_.getItemCount() > 0);
      if (focusRadio_.isEnabled()) {
         focusRadio_.setSelected(true);
         disableOtherSections(focusPanel_);
      }
      else if (xyRadio_.isEnabled()) {
         xyRadio_.setSelected(true);
         disableOtherSections(xyPanel_);
      }
      else {
         disableOtherSections(null); // Disable all
         okButton.setEnabled(false);
      }

      if (initialCriterion != null) {
         initializeValues(initialCriterion);
         okButton.setEnabled(true);
      }

      pack();
   }

   private void initializeValues(ChangeCriterion criterion) {
      // Since the device combo boxes are constructed with currently
      // used devices excluded, we need to add the device given in the
      // criterion.
      if (criterion instanceof ZDistanceCriterion) {
         focusRadio_.setEnabled(true);
         focusRadio_.setSelected(true);
         disableOtherSections(focusPanel_);
         focusDeviceCombo_.addItem(
               criterion.getMonitoredItem().getDeviceLabel());
         focusThreshUm_ =
               ((ZDistanceCriterion) criterion).getDistanceThresholdUm();
         focusThreshField_.setText(Double.toString(focusThreshUm_));
      }
      else if (criterion instanceof XYDistanceCriterion) {
         xyRadio_.setEnabled(true);
         xyRadio_.setSelected(true);
         disableOtherSections(xyPanel_);
         xyDeviceCombo_.addItem(
               criterion.getMonitoredItem().getDeviceLabel());
         xyThreshUm_ =
               ((XYDistanceCriterion) criterion).getDistanceThresholdUm();
         xyThreshField_.setText(Double.toString(xyThreshUm_));
      }
      shouldPollCheckBox_.setSelected(criterion.requiresPolling());
   }

   private void handleCancel() {
      setVisible(false);
   }

   private void handleOK() {
      boolean shouldPoll = shouldPollCheckBox_.isSelected();
      if (focusRadio_.isSelected()) {
         result_ = ChangeCriterion.createZDistanceCriterion(
               (String) focusDeviceCombo_.getSelectedItem(),
               Double.parseDouble(focusThreshField_.getText()),
               shouldPoll);
      }
      else if (xyRadio_.isSelected()) {
         result_ = ChangeCriterion.createXYDistanceCriterion(
                 (String) xyDeviceCombo_.getSelectedItem(),
                 Double.parseDouble(xyThreshField_.getText()),
                 shouldPoll);
      }
      setVisible(false);
   }

   private void disableOtherSections(JPanel panel) {
      for (JPanel p : sectionPanels_) {
         p.setEnabled(p == panel);
         for (Component c : p.getComponents()) {
            c.setEnabled(p == panel);
         }
      }
   }

   ChangeCriterion getResult() {
      return result_;
   }

   private String[] getAvailableFocusDevices() {
      return getAvailableDevicesOfTypeForCriteria(DeviceType.StageDevice,
            ZDistanceCriterion.class);
   }

   private String[] getAvailableXYStageDevices() {
      return getAvailableDevicesOfTypeForCriteria(DeviceType.XYStageDevice,
            XYDistanceCriterion.class);
   }

   private String[] getAvailableDevicesOfTypeForCriteria(
         DeviceType deviceType,
         Class<? extends ChangeCriterion> criterionClass)
   {
      CMMCore core = controller_.getCore();
      if (core == null) {
         return new String[] {};
      }

      List<String> devs = Arrays.asList(
              core.getLoadedDevicesOfType(deviceType).toArray());

      List<ChangeCriterion> criteria = controller_.getChangeCriteria();
      Set<String> devsInUse = new HashSet<String>();
      for (ChangeCriterion c : criteria) {
         if (criterionClass.isInstance(c)) {
            devsInUse.add(c.getMonitoredItem().getDeviceLabel());
         }
      }

      List<String> availableDevs = new ArrayList<String>();
      for (String d : devs) {
         if (!devsInUse.contains(d)) {
            availableDevs.add(d);
         }
      }
      return availableDevs.toArray(new String[] {});
   }
}
