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

   private final LogicCell[] cells_;

   private final PLogicControlModel model_;

   public LogicCellsTab(final PLogicControlModel model) {
      model_ = Objects.requireNonNull(model);
      cells_ = new LogicCell[24];
      createUserInterface();
      createEventHandlers();
   }

   /**
    * Create the user interface.
    */
   private void createUserInterface() {
      // init logic cells (always init all 24 cells)
      for (int i = 0; i < 24; i++) {
         cells_[i] = new LogicCell(model_.plc(), i+1);
      }

      refreshUserInterface();
   }

   /**
    * Create the event handlers.
    */
   private void createEventHandlers() {

   }

   /**
    * Update the logic cell panel with the current number of cells.
    */
   public void refreshUserInterface() {
      removeAll();

      final int numCells = model_.plc().numCells();
      final int factor = (numCells > 16) ? 6 : 4;

      // add logic cells to panel
      for (int i = 0; i < numCells; i++) {
         String constraints = ((i+1) % factor == 0) ? "wrap" : "";
         if (i == cells_.length-1) {
            constraints = "";
         }
         add(cells_[i], "aligny top, " + constraints);
      }
   }

   /**
    * Remove all components from the cell.
    */
   public void clearLogicCells() {
      final int numCells = model_.plc().numCells();
      for (int i = 0; i < numCells; i++) {
         cells_[i].clearCell();
      }
   }

   /**
    * Initialize the logic cells.
    */
   public void initLogicCells() {
      LogicCell.UPDATE = false;
      final int numCells = model_.plc().numCells();
      for (int i = 0; i < numCells; i++) {
         if (!model_.isUpdating()) {
            return;
         }
         cells_[i].initCell();
      }
      LogicCell.UPDATE = true;
   }

}
