/*
 * Project: ASI PLogic Control
 * License: BSD 3-clause, see LICENSE.md
 * Author: Brandon Simpson (brandon@asiimaging.com)
 * Copyright (c) 2024, Applied Scientific Instrumentation
 */

package com.asiimaging.plogic.ui;

import com.asiimaging.plogic.model.devices.ASIPLogic;
import com.asiimaging.plogic.ui.asigui.ComboBox;
import com.asiimaging.plogic.ui.asigui.Panel;
import com.asiimaging.plogic.ui.asigui.RadioButton;
import com.asiimaging.plogic.ui.asigui.Spinner;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.util.Objects;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.SwingConstants;

public class LogicCell extends Panel {

   public static boolean UPDATE;

   private ComboBox cmbCellType_;

   private LogicInputRow[] inputs_;

   // standard display for cell configuration
   private Spinner spnConfig_;

   // only used for CellType.CONSTANT cells
   private RadioButton radConfig_;

   private final int cellNum_;
   private final String title_;
   private final ASIPLogic plc_;

   public LogicCell(final ASIPLogic plc, final int cellNum) {
      plc_ = Objects.requireNonNull(plc);
      title_ = "Cell " + cellNum;
      cellNum_ = cellNum;
      inputs_ = new LogicInputRow[4];
      createUserInterface();
      createEventHandlers();
   }

   /**
    * Create the user interface.
    */
   private void createUserInterface() {
      setMigLayout(
            "",
            "[center]10[center]",
            "[]5[]"
      );

      final JLabel lblTitle = new JLabel(title_);
      lblTitle.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));

      //setBackground(Color.BLACK);
      setBorder(BorderFactory.createLineBorder(Color.GRAY));

      final JLabel lblInvert = new JLabel("Invert?");
      final JLabel lblEdge = new JLabel("Edge?");
      final JLabel lblValue = new JLabel("Value");

      // init input rows
      for (int i = 0; i < 4; i++) {
         inputs_[i] = new LogicInputRow(plc_, cellNum_,i + 1);
      }

      final String[] cellTypes = ASIPLogic.CellType.toArray();
      cmbCellType_ = new ComboBox(cellTypes, cellTypes[0], 150, 22);

      final JLabel lblConfig = new JLabel("Configuration:");
      lblConfig.setMinimumSize(new Dimension(70, 20));

      // max value is unsigned 16-bit int
      spnConfig_ = Spinner.createIntegerSpinner(0, 0, 65535, 1);

      radConfig_ = new RadioButton(new String[]{"Low", "High"}, "Low");
      radConfig_.setMigLayout("insets 0 0 0 0", "", "");
      radConfig_.setMinimumSize(new Dimension(186, 20)); // width chosen to prevent cells from moving

      add(lblTitle, "");
      add(cmbCellType_, "span 3, align left, wrap");

      add(lblConfig, "");
      add(spnConfig_, "");
      add(lblInvert, "");
      add(lblEdge, "");
      add(lblValue, "wrap");
      for (final LogicInputRow input : inputs_) {
         input.addToComponent(this);
      }
   }

   /**
    * Create the event handlers.
    */
   private void createEventHandlers() {

      cmbCellType_.registerListener(e -> {
         final ASIPLogic.CellType cellType =
               ASIPLogic.CellType.fromString(cmbCellType_.getSelected());
         refreshUserInterface(cellType);
         if (UPDATE) {
            plc_.pointerPosition(cellNum_);
            plc_.cellType(cellType);
            for (final LogicInputRow input : inputs_) {
               input.clearInput();
            }
         }
      });

      spnConfig_.registerListener(e -> {
         if (UPDATE) {
            plc_.pointerPosition(cellNum_);
            plc_.cellConfig(spnConfig_.getInt());
         }
      });

      // Note: no update guard => radConfig_.setSelected() does not trigger an ActionEvent
      radConfig_.registerListener(e -> {
         final String selected = radConfig_.getSelectedButtonText();
         plc_.pointerPosition(cellNum_);
         plc_.cellConfig(selected.equals("High") ? 1 : 0);
      });

   }

   private void refreshUserInterface(final ASIPLogic.CellType cellType) {
      removeAll(); // clear components

      final JLabel lblTitle = new JLabel(title_);
      lblTitle.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));

      final JLabel lblConfig = new JLabel(cellType.configName());
      lblConfig.setHorizontalAlignment(SwingConstants.CENTER);
      lblConfig.setMinimumSize(new Dimension(70, 20));
      if (cellType == ASIPLogic.CellType.CONSTANT) {
         add(lblTitle, "");
         add(cmbCellType_, "span 3, align left, wrap");
         add(lblConfig, "");
         add(radConfig_, "");
      } else {
         // all other cell types
         final JLabel lblInvert = new JLabel("Invert?");
         final JLabel lblEdge = new JLabel("Edge?");
         final JLabel lblValue = new JLabel("Value");

         add(lblTitle, "");
         add(cmbCellType_, "span 3, align left, wrap");

         // set the input labels
         for (int i = 0; i < cellType.numInputs(); i++) {
            inputs_[i].setInputLabel(cellType.inputName(i + 1));
         }

         add(lblConfig, "");
         if (cellType.configName().isEmpty()) {
            add(new JLabel(""), "");
         } else {
            add(spnConfig_, "");
         }
         add(lblInvert, "");
         add(lblEdge, "");
         add(lblValue, "wrap");
      }

      // add inputs
      for (int i = 0; i < cellType.numInputs(); i++) {
         inputs_[i].addToComponent(this);
      }

      setMinimumSize(new Dimension(281, 174));
      updateUI();
   }

   /**
    * Remove all components from the cell.
    */
   public void clearCell() {
      removeAll();
      setMinimumSize(new Dimension(281, 174));
      updateUI();
   }

   public void initCell() {
      plc_.pointerPosition(cellNum_);
      final ASIPLogic.CellType cellType = plc_.cellType();
      // calls the event handler for cell type
      cmbCellType_.setSelected(cellType.toString());
      if (cellType == ASIPLogic.CellType.CONSTANT) {
         // configuration
         final String buttonName = (plc_.cellConfig() == 1) ? "High" : "Low";
         radConfig_.setSelected(buttonName, true); // Note: does not trigger and ActionEvent
      } else {
         // configuration
         if (!cellType.configName().isEmpty()) {
            final int config = plc_.cellConfig();
            spnConfig_.setInt(config);
         }
         // input rows
         for (int i = 0; i < cellType.numInputs(); i++) {
            inputs_[i].initRow();
         }
      }
   }

}

