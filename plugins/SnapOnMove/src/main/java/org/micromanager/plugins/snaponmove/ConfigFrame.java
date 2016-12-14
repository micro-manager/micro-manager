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

import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.WindowConstants;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import net.miginfocom.swing.MigLayout;
import org.micromanager.internal.utils.MMFrame;

/**
 * Allow user to enable/disable Snap-on-Move.
 *
 * Shows a check box to enable/disable movement monitoring.
 * This is for testing and may be removed or replaced in the final version.
 */
final class ConfigFrame extends MMFrame {
   private static final String ENABLE_BUTTON = "Start";
   private static final String DISABLE_BUTTON = "Stop";
   
   private final MainController controller_;

   private final JTable criteriaTable_;
   private final JButton addButton_;
   private final JButton removeButton_;
   private final JButton editButton_;

   public ConfigFrame(final MainController controller) {
      controller_ = controller;

      setTitle("Snap-on-Move Preview");
      setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
      setLayout(new MigLayout("fill",
            "[]rel[]rel[grow, fill]rel[]", "[]rel[]unrel[]unrel[]rel[grow, fill]"));

      add(new JLabel("Make sure to stop before starting MDA!"), "span 2, wrap");

      add(new JLabel("Snap-on-Move Preview: "));

      final JButton enableButton = new JButton(ENABLE_BUTTON);
      enableButton.setText(controller.isEnabled() ?
            DISABLE_BUTTON : ENABLE_BUTTON);
      enableButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            boolean shouldEnable = enableButton.getText().equals(ENABLE_BUTTON);
            controller.setEnabled(shouldEnable);
            enableButton.setText(controller.isEnabled() ?
                  DISABLE_BUTTON : ENABLE_BUTTON);
            updateButtonStates();
         }
      });
      add(enableButton, "wrap");

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
               }
               catch (NumberFormatException nfe) {
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
            new ListSelectionListener() {
         @Override
         public void valueChanged(ListSelectionEvent e) {
            updateButtonStates();
         }
      });
      final JScrollPane criteriaPane = new JScrollPane(criteriaTable_,
            JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
            JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
      criteriaPane.setPreferredSize(new Dimension(200, 200));
      add(criteriaPane, "span 3, grow");

      addButton_ = new JButton("Add...");
      addButton_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            final CriterionDialog dialog =
                  new CriterionDialog(controller, ConfigFrame.this);
            dialog.setVisible(true);
            ChangeCriterion criterion = dialog.getResult();
            if (criterion != null) {
               List<ChangeCriterion> criteria = controller.getChangeCriteria();
               criteria.add(criterion);
               controller.setChangeCriteria(criteria);
               ((CriteriaTableModel) criteriaTable_.getModel()).
                     fireTableRowsInserted(criteria.size(),
                           criteria.size() + 1);
               criteriaTable_.setRowSelectionInterval(criteria.size(),
                     criteria.size() + 1);
               updateButtonStates();
            }
         }
      });

      removeButton_ = new JButton("Remove");
      removeButton_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            int row = criteriaTable_.getSelectedRow();
            if (row < 0) {
               return; // None selected
            }
            List<ChangeCriterion> criteria = controller.getChangeCriteria();
            criteria.remove(row);
            controller.setChangeCriteria(criteria);
            ((CriteriaTableModel) criteriaTable_.getModel()).
                    fireTableRowsDeleted(row, row + 1);
            updateButtonStates();
         }
      });

      editButton_ = new JButton("Edit...");
      editButton_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
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
               ((CriteriaTableModel) criteriaTable_.getModel()).
                       fireTableRowsUpdated(row, row + 1);
               updateButtonStates();
            }
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
      loadPosition(600, 200);
   }

   private void updateButtonStates() {
      boolean rowSelected = (criteriaTable_.getSelectedRow() >= 0);
      addButton_.setEnabled(true);
      removeButton_.setEnabled(rowSelected);
      editButton_.setEnabled(rowSelected);
   }
}
