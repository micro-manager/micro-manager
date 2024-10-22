/*
 * Project: ASI PLogic Control
 * License: BSD 3-clause, see LICENSE.md
 * Author: Brandon Simpson (brandon@asiimaging.com)
 * Copyright (c) 2024, Applied Scientific Instrumentation
 */

package com.asiimaging.plogic.ui.tabs;

import com.asiimaging.plogic.PLogicControlModel;
import com.asiimaging.plogic.ui.LogicCell;
import com.asiimaging.plogic.ui.asigui.Panel;
import java.util.Objects;

/**
 * Controls for the Logic Cells.
 */
public class LogicCellsTab extends Panel {

   public static final int MAX_LOGIC_CELLS = 24;

   private final LogicCell[] cells_;

   private final PLogicControlModel model_;

   public LogicCellsTab(final PLogicControlModel model) {
      model_ = Objects.requireNonNull(model);
      // Note: always use MAX_LOGIC_CELLS so that we never have to
      // recreate the LogicCell array when switching devices.
      cells_ = new LogicCell[MAX_LOGIC_CELLS];
      createUserInterface();
   }

   /**
    * Create the user interface.
    */
   private void createUserInterface() {
      // initialize logic cell array
      for (int i = 0; i < MAX_LOGIC_CELLS; i++) {
         cells_[i] = new LogicCell(model_, i + 1);
      }
      updateCellTypeComboBoxes();
      refreshTab();
   }

   /**
    * Update the logic cell panel with the current number of cells.
    */
   public void refreshTab() {
      removeAll();

      final int numCells = model_.plc().numCells();
      final int rowLength = (numCells > 16) ? 6 : 4; // row length based on build

      // add logic cells to panel
      for (int i = 0; i < numCells; i++) {
         String constraints = ((i + 1) % rowLength == 0) ? "wrap" : "";
         if (i == cells_.length - 1) {
            constraints = "";
         }
         add(cells_[i], "aligny top, " + constraints);
      }
   }

   /**
    * Remove every component from all logic cells.
    */
   public void clearLogicCells() {
      final int numCells = model_.plc().numCells();
      for (int i = 0; i < numCells; i++) {
         cells_[i].clearCell();
      }
   }

   /**
    * Updates all logic cells with cell type input labels for the current firmware.
    */
   public void updateCellTypeComboBoxes() {
      final int numCells = model_.plc().numCells();
      for (int i = 0; i < numCells; i++) {
         cells_[i].updateCellTypeComboBox();
      }
   }

   /**
    * Initialize the logic cells.
    */
   public void initLogicCells() {
      LogicCell.isUpdatingEnabled(false);
      final int numCells = model_.plc().numCells();
      for (int i = 0; i < numCells; i++) {
         cells_[i].initLogicCell();
      }
      LogicCell.isUpdatingEnabled(true);
   }

   /**
    * After the "Clear Logic Cells" button is pressed on the "Device" tab,
    * set cell types to constant with a configuration of 0.
    */
   public void clearLogicCellsFromButton() {
      LogicCell.isUpdatingEnabled(false);
      final int numCells = model_.plc().numCells();
      for (int i = 0; i < numCells; i++) {
         cells_[i].clearToConstant();
      }
      LogicCell.isUpdatingEnabled(true);
   }

}
