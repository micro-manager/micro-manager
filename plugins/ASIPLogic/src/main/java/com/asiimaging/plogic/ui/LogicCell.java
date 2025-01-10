/*
 * Project: ASI PLogic Control
 * License: BSD 3-clause, see LICENSE.md
 * Author: Brandon Simpson (brandon@asiimaging.com)
 * Copyright (c) 2024, Applied Scientific Instrumentation
 */

package com.asiimaging.plogic.ui;

import com.asiimaging.plogic.PLogicControlModel;
import com.asiimaging.plogic.model.devices.ASIPLogic;
import com.asiimaging.plogic.ui.asigui.ComboBox;
import com.asiimaging.plogic.ui.asigui.Panel;
import com.asiimaging.plogic.ui.asigui.RadioButton;
import com.asiimaging.plogic.ui.asigui.Spinner;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.util.Arrays;
import java.util.Objects;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.SwingConstants;

public class LogicCell extends Panel {

   private static String[] cellTypes_ = new String[]{""};
   private static boolean isUpdatingEnabled_ = true;

   private ComboBox cmbCellType_;
   private Spinner spnConfig_; // standard display for cell configuration
   private RadioButton radConfig_; // only used for CellType.CONSTANT configuration
   private final LogicInputRow[] inputs_; // inputs 1-4

   private final int cellNum_;
   private final String title_;

   private final PLogicControlModel model_;

   public LogicCell(final PLogicControlModel model, final int cellNum) {
      model_ = Objects.requireNonNull(model);
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
         inputs_[i] = new LogicInputRow(model_, cellNum_, i + 1);
      }

      cmbCellType_ = new ComboBox(cellTypes_, cellTypes_[0], 150, 22);

      final JLabel lblConfig = new JLabel("Configuration:");
      lblConfig.setMinimumSize(new Dimension(70, 20));

      // max value is unsigned 16-bit int
      spnConfig_ = Spinner.createIntegerSpinner(0, 0, 65535, 1);

      radConfig_ = new RadioButton(new String[]{"Low", "High"}, "Low");
      radConfig_.setMigLayout("insets 0 0 0 0", "", "");

      // width chosen to prevent cells from moving
      radConfig_.setMinimumSize(new Dimension(186, 20));

      add(lblTitle, "");
      add(cmbCellType_, "span 3, align left, wrap");

      add(lblConfig, "");
      add(spnConfig_, "");
      add(lblInvert, "");
      add(lblEdge, "");
      add(lblValue, "wrap");
      for (final LogicInputRow input : inputs_) {
         input.addToPanel(this);
      }
   }

   /**
    * Create the event handlers.
    */
   private void createEventHandlers() {

      cmbCellType_.registerListener(e -> {
         final ASIPLogic.CellType cellType =
               ASIPLogic.CellType.fromString(cmbCellType_.getSelected());
         refreshUI(cellType);
         if (isUpdatingEnabled_) {
            // Note: setting the cell type clears the config and inputs
            model_.plc().pointerPosition(cellNum_);
            model_.plc().cellType(cellType);
            // clear the config and inputs in the ui
            if (cellType == ASIPLogic.CellType.CONSTANT) {
               radConfig_.setSelected("Low", true);
            }
            spnConfig_.setInt(0);
            for (final LogicInputRow input : inputs_) {
               input.clearInput();
            }
         }
      });

      spnConfig_.registerListener(e -> {
         if (isUpdatingEnabled_) {
            model_.plc().pointerPosition(cellNum_);
            model_.plc().cellConfig(spnConfig_.getInt());
         }
      });

      // Note: no update guard (UPDATES_ENABLED) =>
      // radConfig_.setSelected() does not trigger an ActionEvent
      radConfig_.registerListener(e -> {
         final String selected = radConfig_.getSelectedButtonText();
         model_.plc().pointerPosition(cellNum_);
         model_.plc().cellConfig(selected.equals("High") ? 1 : 0);
      });

   }

   /**
    * Update the {@code ComboBox} item labels to match available cell types
    * in the current firmware version.
    */
   public void updateCellTypeComboBox() {
      // always at least 16 types for even the oldest firmware
      int maxIndex = 15;
      final String[] cellTypes = ASIPLogic.CellType.toArray();
      if (model_.plc().firmwareVersion() >= 3.50) {
         maxIndex = 17;
      }
      if (model_.plc().firmwareVersion() >= 3.51) {
         maxIndex = 18;
      }
      // array used for all logic cells
      cellTypes_ = Arrays.copyOf(cellTypes, maxIndex + 1);
      cmbCellType_.updateItems(cellTypes_); // update ui
   }

   /**
    * Update the user interface to reflect the cell type.
    * Called when a cell type {@code ComboBox} item is selected.
    *
    * @param cellType the type of cell
    */
   private void refreshUI(final ASIPLogic.CellType cellType) {
      removeAll(); // clear components

      final JLabel lblTitle = new JLabel(title_);
      lblTitle.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));

      final JLabel lblConfig = new JLabel(cellType.configName());
      lblConfig.setHorizontalAlignment(SwingConstants.CENTER);
      lblConfig.setMinimumSize(new Dimension(70, 20));
      if (cellType == ASIPLogic.CellType.CONSTANT) {
         // constant logic cells use the radio button
         add(lblTitle, "");
         add(cmbCellType_, "span 3, align left, wrap");
         add(lblConfig, "");
         add(radConfig_, "");
      } else {
         // all other cell types use spinners
         final JLabel lblInvert = new JLabel("Invert?");
         final JLabel lblEdge = new JLabel("Edge?");
         final JLabel lblValue = new JLabel("Value");

         add(lblTitle, "");
         add(cmbCellType_, "span 3, align left, wrap");

         // set the input labels and enable/disable check boxes
         for (int i = 0; i < cellType.numInputs(); i++) {
            inputs_[i].setEdgeSensitive(cellType.isEdgeSensitive(i + 1));
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
         inputs_[i].addToPanel(this);
      }

      setMinimumSize(new Dimension(281, 174));
      updateUI();
      repaint();
   }

   /**
    * Initialize the logic cell using {@code PLogicState}.
    */
   public void initLogicCell() {
      final ASIPLogic.CellType cellType = model_.plc().state().cell(cellNum_).type();
      final int config = model_.plc().state().cell(cellNum_).config();
      // calls the event handler for cell type
      cmbCellType_.setSelected(cellType.toString());
      if (cellType == ASIPLogic.CellType.CONSTANT) {
         // configuration
         final String buttonName = (config == 1) ? "High" : "Low";
         radConfig_.setSelected(buttonName, true); // Note: does not trigger and ActionEvent
      } else {
         // configuration
         if (!cellType.configName().isEmpty()) {
            spnConfig_.setInt(config);
         }
         // input rows
         for (int i = 0; i < cellType.numInputs(); i++) {
            inputs_[i].initInputRow();
         }
      }
   }

   /**
    * Remove all components from the cell.
    */
   public void clearCell() {
      removeAll();
      setMinimumSize(new Dimension(281, 174));
      updateUI();
   }

   /**
    * Reset the cell after the "Clear Logic Cells" button is pressed.
    * Only update the radio button because we are using the constant cell type.
    */
   public void clearToConstant() {
      radConfig_.setSelected("Low", true);
      cmbCellType_.setSelected(ASIPLogic.CellType.CONSTANT.toString());
   }

   public static void isUpdatingEnabled(final boolean state) {
      isUpdatingEnabled_ = state;
   }

   public static boolean isUpdatingEnabled() {
      return isUpdatingEnabled_;
   }

}

