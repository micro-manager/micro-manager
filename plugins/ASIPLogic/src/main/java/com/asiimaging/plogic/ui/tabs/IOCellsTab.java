/*
 * Project: ASI PLogic Control
 * License: BSD 3-clause, see LICENSE.md
 * Author: Brandon Simpson (brandon@asiimaging.com)
 * Copyright (c) 2024, Applied Scientific Instrumentation
 */

package com.asiimaging.plogic.ui.tabs;

import com.asiimaging.plogic.PLogicControlModel;
import com.asiimaging.plogic.ui.IOCell;
import com.asiimaging.plogic.ui.asigui.Panel;
import java.util.Objects;

/**
 * Controls for the Physical I/O cells.
 */
public class IOCellsTab extends Panel {

   private IOCell[] cells_;

   private final PLogicControlModel model_;

   public IOCellsTab(final PLogicControlModel model) {
      model_ = Objects.requireNonNull(model);
      createUserInterface();
   }

   private void createUserInterface() {
      setMigLayout(
            "",
            "[center]5[center]",
            "[]5[]"
      );

      cells_ = new IOCell[16];
      for (int i = 0; i < 16; i++) {
         cells_[i] = new IOCell(model_.plc(), i + 1);
         add(cells_[i], ((i + 1) % 4 == 0) ? "wrap" : "");
      }
   }

   /**
    * Remove all components from the cell.
    */
   public void clearIOCells() {
      for (final IOCell cell : cells_) {
         cell.clearCell();
      }
   }

   /**
    * Initialize the physical I/O cells.
    */
   public void initIOCells() {
      IOCell.UPDATE = false;
      for (final IOCell cell : cells_) {
         cell.initCell();
      }
      IOCell.UPDATE = true;
   }

}
