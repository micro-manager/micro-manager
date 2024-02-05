// Snap-on-Move for Micro-Manager
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

import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.WindowConstants;
import net.miginfocom.swing.MigLayout;
import org.micromanager.Studio;
import org.micromanager.internal.utils.WindowPositioning;
import org.micromanager.propertymap.MutablePropertyMapView;

// Imports for MMStudio internal packages
// Plugins should not access internal packages, to ensure modularity and
// maintainability. However, this plugin code is older than the current
// MMStudio API, so it still uses internal classes and interfaces. New code
// should not imitate this practice.

/**
 * Allow user to enable/disable Snap-on-Move.
 *
 * <p>Shows a check box to enable/disable movement monitoring.
 * This is for testing and may be removed or replaced in the final version.
 */
final class ConfigFrame extends JFrame {
   private static final String ENABLE_BUTTON = "Start";
   private static final String DISABLE_BUTTON = "Stop";

   private static final String USESNAP = "UseSnap";
   private static final String USETESTACQ = "UseTestAcq";

   private final JTable criteriaTable_;
   private final JButton addButton_;
   private final JButton removeButton_;
   private final JButton editButton_;

   public ConfigFrame(final Studio studio, final MainController controller) {

      final MutablePropertyMapView settings = studio.profile().getSettings(this.getClass());
      setTitle("Snap-on-Move");
      setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
      setLayout(new MigLayout("fill",
            "[]rel[]rel[grow, fill]rel[]", "[]rel[]unrel[]unrel[]rel[grow, fill]"));

      add(new JLabel("Snap-on-Move: "));

      final JButton enableButton = new JButton(ENABLE_BUTTON);
      enableButton.setText(controller.isEnabled() ? DISABLE_BUTTON : ENABLE_BUTTON);
      enableButton.addActionListener(e -> {
         boolean shouldEnable = enableButton.getText().equals(ENABLE_BUTTON);
         controller.setEnabled(shouldEnable);
         enableButton.setText(controller.isEnabled() ? DISABLE_BUTTON : ENABLE_BUTTON);
         updateButtonStates();
      });
      add(enableButton, "wrap");

      final JRadioButton selectSnap = new JRadioButton("Snap");
      final JRadioButton selectTestAcq = new JRadioButton(("Test acquisition"));
      selectSnap.setSelected(settings.getBoolean(USESNAP, true));
      selectTestAcq.setSelected(settings.getBoolean(USETESTACQ, false));
      if (selectSnap.isSelected()) {
         controller.useSnap();
      } else if (selectTestAcq.isSelected()) {
         controller.useTestAcq();
      }
      final ActionListener al = e -> {
         if (selectSnap.isSelected()) {
            controller.useSnap();
            settings.putBoolean(USESNAP, true);
            settings.putBoolean(USETESTACQ, false);
         } else if (selectTestAcq.isSelected()) {
            controller.useTestAcq();
            settings.putBoolean(USESNAP, false);
            settings.putBoolean(USETESTACQ, true);
         }
      };
      selectSnap.addActionListener(al);
      selectTestAcq.addActionListener(al);
      ButtonGroup bg = new ButtonGroup();
      bg.add(selectSnap);
      bg.add(selectTestAcq);
      add(selectSnap, "span 4, split 2");
      add(selectTestAcq, "wrap");

      add(new JSeparator(), "span 4, grow, wrap");

      add(new JLabel("Polling Interval: "));

      final JTextField intervalField =
            new JTextField(Long.toString(controller.getPollingIntervalMs()));
      intervalField.setColumns(5);
      intervalField.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            long intervalMs = controller.getPollingIntervalMs();
            try {
               intervalMs = Math.round(Double.parseDouble(intervalField.getText()));
            } catch (NumberFormatException nfe) {
               System.out.println("Caught NumberFormatException");
            }
            if (intervalMs > 0) {
               controller.setPollingIntervalMs(intervalMs);
            }
            intervalField.setText(Long.toString(controller.getPollingIntervalMs()));
            intervalField.transferFocusUpCycle();
         }
      });
      add(intervalField, "split 2");
      add(new JLabel(" ms"), "wrap");

      criteriaTable_ = new JTable(new CriteriaTableModel(controller));
      criteriaTable_.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
      criteriaTable_.getSelectionModel().addListSelectionListener(
              e -> updateButtonStates());
      final JScrollPane criteriaPane = new JScrollPane(criteriaTable_,
            JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
            JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
      criteriaPane.setPreferredSize(new Dimension(200, 200));
      add(criteriaPane, "span 3, grow");

      addButton_ = new JButton("Add...");
      addButton_.addActionListener(e -> {
         final CriterionDialog dialog =
               new CriterionDialog(controller, ConfigFrame.this);
         dialog.setVisible(true);
         ChangeCriterion criterion = dialog.getResult();
         if (criterion != null) {
            List<ChangeCriterion> criteria = controller.getChangeCriteria();
            criteria.add(criterion);
            controller.setChangeCriteria(criteria);
            ((CriteriaTableModel) criteriaTable_.getModel())
                  .fireTableRowsInserted(criteria.size(),
                        criteria.size() + 1);
            criteriaTable_.setRowSelectionInterval(criteria.size(),
                  criteria.size() + 1);
            updateButtonStates();
         }
      });

      removeButton_ = new JButton("Remove");
      removeButton_.addActionListener(e -> {
         int row = criteriaTable_.getSelectedRow();
         if (row < 0) {
            return; // None selected
         }
         List<ChangeCriterion> criteria = controller.getChangeCriteria();
         criteria.remove(row);
         controller.setChangeCriteria(criteria);
         ((CriteriaTableModel) criteriaTable_.getModel()).fireTableRowsDeleted(row, row + 1);
         updateButtonStates();
      });

      editButton_ = new JButton("Edit...");
      editButton_.addActionListener(e -> {
         int row = criteriaTable_.getSelectedRow();
         if (row < 0) {
            return; // None selected
         }
         List<ChangeCriterion> criteria = controller.getChangeCriteria();
         ChangeCriterion criterion = criteria.get(row);
         final CriterionDialog dialog =
               new CriterionDialog(controller, criterion, ConfigFrame.this);
         dialog.setVisible(true);
         criterion = dialog.getResult();
         if (criterion != null) {
            criteria.set(row, criterion);
            controller.setChangeCriteria(criteria);
            ((CriteriaTableModel) criteriaTable_.getModel()).fireTableRowsUpdated(row, row + 1);
            updateButtonStates();
         }
      });

      final JPanel criteriaButtonPanel = new JPanel(
            new MigLayout("filly, flowy, insets 0",
                  "[grow, fill]", "[]rel[]rel[]push"));
      criteriaButtonPanel.add(addButton_);
      criteriaButtonPanel.add(removeButton_);
      criteriaButtonPanel.add(editButton_);
      add(criteriaButtonPanel, "wrap");

      setMinimumSize(new Dimension(360, 210));
      pack();

      super.setIconImage(Toolkit.getDefaultToolkit().getImage(
            getClass().getResource("/org/micromanager/icons/microscope.gif")));
      super.setLocation(600, 200);
      WindowPositioning.setUpLocationMemory(this, this.getClass(), null);
   }

   private void updateButtonStates() {
      boolean rowSelected = (criteriaTable_.getSelectedRow() >= 0);
      addButton_.setEnabled(true);
      removeButton_.setEnabled(rowSelected);
      editButton_.setEnabled(rowSelected);
   }
}
