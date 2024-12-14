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
import java.util.concurrent.ExecutionException;
import javax.swing.SwingWorker;

public class TabPanel extends Panel {

   private DeviceTab deviceTab_;
   private LogicCellsTab logicCellsTab_;
   private IOCellsTab ioCellsTab_;
   private WizardTab wizardTab_;

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
      wizardTab_ = new WizardTab(model_, this);

      // add in order from left to right
      tabbedPane_.addTab(createTabTitle("Device"), deviceTab_);
      tabbedPane_.addTab(createTabTitle("Logic Cells"), logicCellsTab_);
      tabbedPane_.addTab(createTabTitle("Physical I/O"), ioCellsTab_);
      tabbedPane_.addTab(createTabTitle("Wizards"), wizardTab_);

      add(tabbedPane_, "growx, growy");
   }

   /**
    * Update the Logic Cells and Physical I/O tabs using a worker thread.
    *
    * <p>Update {@code PLogicState} by sending serial commands to the
    * controller and then update the UI from {@code PLogicState}.
    */
   public void updateTabsFromController() {
      SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {
         @Override
         protected Void doInBackground() {
            model_.studio().logs().logMessage("Start Updates");
            model_.isUpdating(true);

            // clear ui
            logicCellsTab_.clearLogicCells();
            ioCellsTab_.clearIOCells();

            // ask to turn on cell updates with dialog box
            if (!checkForAutoCellUpdates()) {
               model_.isUpdating(false);
               return null; // early exit => will not update correctly
            }

            // update plc state from controller
            model_.plc().updateState(model_, frame_);

            // update ui from plc state
            logicCellsTab_.initLogicCells();
            ioCellsTab_.initIOCells();

            model_.isUpdating(false);
            model_.studio().logs().logMessage("Stop Updates");
            return null;
         }

         @Override
         protected void done() {
            // Note: need to do this to catch exceptions
            try {
               get();
            } catch (final InterruptedException ex) {
               throw new RuntimeException(ex);
            } catch (final ExecutionException ex) {
               throw new RuntimeException(ex.getCause());
            }
         }

      };
      worker.execute();
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
    * Return a styled HTML {@code String} for the tab title.
    *
    * @param title the title text on the tab
    * @return an HTML {@code String}
    */
   private String createTabTitle(final String title) {
      return "<html><body leftmargin=10 topmargin=8 marginwidth=10 marginheight=5><b><font size=4>"
            + title + "</font></b></body></html>";
   }

   /**
    * Return the logic cells tab.
    *
    * @return the {@code LogicCellsTab} tab
    */
   public LogicCellsTab getLogicCellsTab() {
      return logicCellsTab_;
   }

   /**
    * Return physical I/O tab.
    *
    * @return the {@code IOCellTab} tab
    */
   public IOCellsTab getIOCellsTab() {
      return ioCellsTab_;
   }

   /**
    * Return Wizards tab.
    *
    * @return the {@code WizardTab} tab
    */
   public WizardTab getWizardsTab() {
      return wizardTab_;
   }

   /**
    * Used to resize the frame when a PLogic device
    * with a different number of cells is selected.
    */
   public void packFrame() {
      frame_.pack();
   }

   /**
    * Return the main plugin frame.
    *
    * @return the main plugin frame
    */
   public PLogicControlFrame getFrame() {
      return frame_;
   }

}
