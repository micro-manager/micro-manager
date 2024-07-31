/*
 * Project: ASI PLogic Control
 * License: BSD 3-clause, see LICENSE.md
 * Author: Brandon Simpson (brandon@asiimaging.com)
 * Copyright (c) 2024, Applied Scientific Instrumentation
 */

package com.asiimaging.plogic.ui.tabs;

import com.asiimaging.plogic.PLogicControlFrame;
import com.asiimaging.plogic.PLogicControlModel;
import com.asiimaging.plogic.model.devices.ASIPLogic;
import com.asiimaging.plogic.ui.asigui.Panel;
import com.asiimaging.plogic.ui.asigui.TabbedPane;
import com.asiimaging.plogic.ui.utils.DialogUtils;
import java.util.Objects;
import javax.swing.SwingWorker;

public class TabPanel extends Panel {

   private DeviceTab deviceTab_;
   private LogicCellsTab logicCellsTab_;
   private IOCellsTab ioCellsTab_;

   private final TabbedPane tabbedPane_;

   private final PLogicControlModel model_;
   private final PLogicControlFrame frame_;

   public TabPanel(final PLogicControlModel model,
                   final PLogicControlFrame frame) {
      model_ = Objects.requireNonNull(model);
      frame_ = Objects.requireNonNull(frame);
      tabbedPane_ = new TabbedPane();
      createUserInterface();
   }

   /**
    * Create the user interface.
    */
   private void createUserInterface() {
      // create tabs
      deviceTab_ = new DeviceTab(model_, this);
      logicCellsTab_ = new LogicCellsTab(model_);
      ioCellsTab_ = new IOCellsTab(model_);

      // add in order from left to right
      tabbedPane_.addTab(createTabTitle("Device"), deviceTab_);
      tabbedPane_.addTab(createTabTitle("Logic Cells"), logicCellsTab_);
      tabbedPane_.addTab(createTabTitle("Physical I/O"), ioCellsTab_);

      add(tabbedPane_, "growx, growy");
   }

   /**
    * Update the cells using a worker thread.
    */
   public void updateCells() {
      SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {
         @Override
         protected Void doInBackground() {
            model_.studio().logs().logMessage(
                  "Start of updateCells().");
            model_.isUpdating(true);
            // clear cells
            logicCellsTab_.clearLogicCells();
            ioCellsTab_.clearIOCells();
            // ask to turn on cell updates
            if (!checkForAutoCellUpdates()) {
               return null; // early exit => will not update correctly
            }
            // update from controller
            logicCellsTab_.initLogicCells();
            ioCellsTab_.initIOCells();
            model_.isUpdating(false);
            model_.studio().logs().logMessage(
                  "End of updateCells().");
            return null;
         }
      };
      worker.execute();
   }

   /**
    * Stop updating cells if currently updating.
    */
   public void stopUpdateCells() {
      if (model_.isUpdating()) {
         model_.isUpdating(false);
         // wait for update to stop
         while (model_.isUpdating()) {
            try {
               Thread.sleep(10);
            } catch (InterruptedException ex) {
               throw new RuntimeException(ex);
            }
         }
         model_.studio().logs().logMessage(
               "Stop updateCells().");
      }
   }

   /**
    * Return true if EditUpdateCellsAutomatically is set to Yes.
    *
    * <p>This property should always be set to Yes so that the cells can be updated correctly.
    */
   public boolean checkForAutoCellUpdates() {
      boolean isUpdating = model_.plc().isAutoUpdateCellsOn();
      if (!isUpdating) {
         // ask to set auto update cells
         final boolean result = DialogUtils.showConfirmDialog(this, "Change Settings",
               "<html>The <b>" + ASIPLogic.Properties.EDIT_CELL_UPDATE_AUTO
                     + "</b> property should always be set to <b>Yes</b>.<br>"
                     + "Change the value to <b>Yes</b>?</html>");
         if (result) {
            // calls action handler to change property and UI
            deviceTab_.getEditCellUpdatesCheckBox().setSelected(true);
            isUpdating = true;
         }
      }
      return isUpdating;
   }

   /**
    * Return a styled HTML String of tab title.
    *
    * @param title the title text on the tab
    * @return an HTML String
    */
   private String createTabTitle(final String title) {
      return "<html><body leftmargin=10 topmargin=8 marginwidth=10 marginheight=5><b><font size=4>"
            + title + "</font></b></body></html>";
   }

   public LogicCellsTab getLogicCellsTab() {
      return logicCellsTab_;
   }

   public void updateFrame() {
      frame_.pack();
   }
}
