/*
 * Project: ASI PLogic Control
 * License: BSD 3-clause, see LICENSE.md
 * Author: Brandon Simpson (brandon@asiimaging.com)
 * Copyright (c) 2024, Applied Scientific Instrumentation
 */

package com.asiimaging.plogic.ui;

import com.asiimaging.plogic.PLogicControlModel;
import com.asiimaging.plogic.ui.asigui.CheckBox;
import com.asiimaging.plogic.ui.asigui.Panel;
import com.asiimaging.plogic.ui.asigui.Spinner;
import java.awt.EventQueue;
import java.util.Objects;
import javax.swing.JLabel;

/**
 * A single row of input for a logic cell.
 */
public class LogicInputRow extends Panel {

   private JLabel lblText_;
   private JLabel lblValue_;
   private Spinner spnValue_;
   private CheckBox cbxInvert_;
   private CheckBox cbxEdge_;

   private final int cellNum_;
   private final int inputNum_; // input 1-4

   private final PLogicControlModel model_;

   public LogicInputRow(final PLogicControlModel model, final int cellNum, final int inputNum) {
      model_ = Objects.requireNonNull(model);
      cellNum_ = cellNum;
      inputNum_ = inputNum;
      createUserInterface();
      createEventHandlers();
   }

   /**
    * Create the user interface.
    */
   private void createUserInterface() {
      lblText_ = new JLabel("Input");
      // max value with invert and edge checked is 255
      spnValue_ = Spinner.createIntegerSpinner(0, 0, 63, 1);
      cbxInvert_ = new CheckBox(false);
      cbxEdge_ = new CheckBox(false);
      lblValue_ = new JLabel("0");
   }

   /**
    * Create the event handlers.
    */
   private void createEventHandlers() {
      spnValue_.registerListener(e -> {
         if (LogicCell.isUpdatingEnabled()) {
            setInputValue();
         }
      });
      cbxInvert_.registerListener(e -> {
         if (LogicCell.isUpdatingEnabled()) {
            setInputValue();
         }
      });
      cbxEdge_.registerListener(e -> {
         if (LogicCell.isUpdatingEnabled()) {
            setInputValue();
         }
      });
   }

   /**
    * Send the new input value to the controller and update ui.
    */
   private void setInputValue() {
      final boolean isInverted = cbxInvert_.isSelected();
      final boolean isEdge = cbxEdge_.isSelected();
      final int value = spnValue_.getInt() + (isInverted ? 64 : 0) + (isEdge ? 128 : 0);
      model_.plc().pointerPosition(cellNum_);
      model_.plc().cellInput(inputNum_, value);
      lblValue_.setText(String.valueOf(value));
   }

   /**
    * Initialize the logic cell input row. Set check boxes, spinners, and value text.
    */
   public void initInputRow() {
      final int input = model_.plc().state().cell(cellNum_).input(inputNum_);
      //final int input = plc_.cellInput(inputNum_);
      int inputTemp = input;
      if (inputTemp >= 128) {
         inputTemp -= 128;
         cbxEdge_.setSelected(true);
      } else {
         cbxEdge_.setSelected(false);
      }
      if (inputTemp >= 64) {
         inputTemp -= 64;
         cbxInvert_.setSelected(true);
      } else {
         cbxInvert_.setSelected(false);
      }
      spnValue_.setInt(inputTemp);
      lblValue_.setText(String.valueOf(input));
   }

   /**
    * Add logic cell input row components to panel.
    *
    * @param panel the panel to add components to
    */
   public void addToPanel(final Panel panel) {
      panel.add(lblText_, "");
      panel.add(spnValue_, "");
      panel.add(cbxInvert_, "");
      panel.add(cbxEdge_, "");
      panel.add(lblValue_, "wrap");
   }

   /**
    * Set the label of this logic cell input row.
    *
    * @param text the row label
    */
   public void setInputLabel(final String text) {
      lblText_.setText(text);
   }

   /**
    * Set this logic cell input row to edge sensitive.
    *
    * @param state true if edge sensitive
    */
   public void setEdgeSensitive(final boolean state) {
      // TODO: this could be improved to reduce serial traffic
      if (LogicCell.isUpdatingEnabled()) {
         // when changing cell type from the ui
         EventQueue.invokeLater(() -> {
            cbxEdge_.setSelected(state);
            cbxEdge_.setEnabled(!state);
         });
      } else {
         cbxEdge_.setEnabled(!state);
      }
   }

   /**
    * Clears the ui of the logic cell input row.
    */
   public void clearInput() {
      spnValue_.setInt(0);
      cbxInvert_.setSelected(false);
      cbxEdge_.setSelected(false);
      lblValue_.setText("0");
   }

}
