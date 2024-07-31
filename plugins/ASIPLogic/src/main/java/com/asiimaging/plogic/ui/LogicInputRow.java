/*
 * Project: ASI PLogic Control
 * License: BSD 3-clause, see LICENSE.md
 * Author: Brandon Simpson (brandon@asiimaging.com)
 * Copyright (c) 2024, Applied Scientific Instrumentation
 */

package com.asiimaging.plogic.ui;

import com.asiimaging.plogic.model.devices.ASIPLogic;
import com.asiimaging.plogic.ui.asigui.CheckBox;
import com.asiimaging.plogic.ui.asigui.Panel;
import com.asiimaging.plogic.ui.asigui.Spinner;
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
   private final ASIPLogic plc_;

   public LogicInputRow(final ASIPLogic plc, final int cellNum, final int inputNum) {
      plc_ = Objects.requireNonNull(plc);
      cellNum_ = cellNum;
      inputNum_ = inputNum;
      createUserInterface();
      createEventHandlers();
   }

   private void createUserInterface() {
      lblText_ = new JLabel("Input");
      // max value with invert and edge checked is 255
      spnValue_ = Spinner.createIntegerSpinner(0, 0, 63, 1);
      cbxInvert_ = new CheckBox(false);
      cbxEdge_ = new CheckBox(false);
      lblValue_ = new JLabel("255");
   }

   private void createEventHandlers() {
      spnValue_.registerListener(e -> {
         if (LogicCell.UPDATE) {
            setInputValue();
         }
      });
      cbxInvert_.registerListener(e -> {
         if (LogicCell.UPDATE) {
            setInputValue();
         }
      });
      cbxEdge_.registerListener(e -> {
         if (LogicCell.UPDATE) {
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
      plc_.pointerPosition(cellNum_);
      plc_.cellInput(inputNum_, value);
      lblValue_.setText(String.valueOf(value));
   }

   public void initRow() {
      final int input = plc_.cellInput(inputNum_);
      int inputTemp = input;
      if (inputTemp > 128) {
         inputTemp -= 128;
         cbxEdge_.setSelected(true);
      }
      if (inputTemp > 64) {
         inputTemp -= 64;
         cbxInvert_.setSelected(true);
      }
      spnValue_.setInt(inputTemp);
      lblValue_.setText(String.valueOf(input));
   }

   public void addToComponent(final Panel panel) {
      panel.add(lblText_, "");
      panel.add(spnValue_, "");
      panel.add(cbxInvert_, "");
      panel.add(cbxEdge_, "");
      panel.add(lblValue_, "wrap");
   }

   public void setInputLabel(final String text) {
      lblText_.setText(text);
   }

   public void clearInput() {
      spnValue_.setInt(0);
      cbxInvert_.setSelected(false);
      cbxEdge_.setSelected(false);
      lblValue_.setText("0");
   }
}
