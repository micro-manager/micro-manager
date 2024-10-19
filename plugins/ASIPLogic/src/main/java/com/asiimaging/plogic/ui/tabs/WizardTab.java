package com.asiimaging.plogic.ui.tabs;

import com.asiimaging.plogic.PLogicControlModel;
import com.asiimaging.plogic.ui.wizards.SquareWaveConfigPanel;
import com.asiimaging.plogic.ui.wizards.SquareWaveDisplayPanel;
import com.asiimaging.plogic.ui.asigui.Button;
import com.asiimaging.plogic.ui.asigui.Panel;
import com.asiimaging.plogic.ui.utils.DialogUtils;
import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import javax.swing.SwingWorker;

public class WizardTab extends Panel {

   private Button btnAddPattern_;
   private Button btnRemovePattern_;
   private Button btnCreateProgram_;

   private final ArrayList<SquareWaveConfigPanel> configPanels_;
   private final ArrayList<SquareWaveDisplayPanel> displayPanels_;

   private Panel displayPanel_;
   private final TabPanel tab_;

   private final PLogicControlModel model_;

   public WizardTab(final PLogicControlModel model, final TabPanel tabPanel) {
      model_ = Objects.requireNonNull(model);
      tab_ = Objects.requireNonNull(tabPanel);
      configPanels_ = new ArrayList<>();
      displayPanels_ = new ArrayList<>();
      createUserInterface();
      createEventHandlers();
   }

   /**
    * Create the user interface.
    */
   private void createUserInterface() {
      displayPanel_ = new Panel();
      btnAddPattern_ = new Button("Add", 120, 30);
      btnRemovePattern_ = new Button("Remove", 120, 30);
      btnCreateProgram_ = new Button("Create PLogic Program", 160, 30);
      btnRemovePattern_.setEnabled(false);
      refreshComponents();
   }

   /**
    * Create event handlers.
    */
   private void createEventHandlers() {
      // add new signal generator
      btnAddPattern_.registerListener(e -> {
         // TODO: remove if timeline exists
         if (configPanels_.isEmpty()) {
            btnAddPattern_.setEnabled(false);
            btnRemovePattern_.setEnabled(true);
         }
         final SquareWaveDisplayPanel displayPanel = new SquareWaveDisplayPanel();
         configPanels_.add(new SquareWaveConfigPanel(displayPanel));
         displayPanels_.add(displayPanel);
         refreshComponents();
      });

      // remove last item
      btnRemovePattern_.registerListener(e -> {
         if (!configPanels_.isEmpty()) {
            configPanels_.remove(configPanels_.size() - 1);
         }
         if (!displayPanels_.isEmpty()) {
            displayPanels_.remove(displayPanels_.size() - 1);
         }
         // TODO: remove if timeline exists
         if (configPanels_.isEmpty()) {
            btnAddPattern_.setEnabled(true);
            btnRemovePattern_.setEnabled(false);
         }
         refreshComponents();
      });

      btnCreateProgram_.registerListener(e -> {
         if (displayPanels_.isEmpty()) {
            DialogUtils.showMessage(btnCreateProgram_,
                  "Add Pattern", "No square wave signal generator.\n" +
                        "Click Add to the left.");
            return; // early exit => no signal generator
         }
         if (model_.isUpdating()) {
            DialogUtils.showMessage(btnCreateProgram_,
                  "Updating", "Please wait for updates to complete.");
            return; // early exit => wait for serial traffic to end
         }
         createProgramThread();
      });
   }

   /**
    * Refresh the user interface with the current number of panels.
    */
   private void refreshComponents() {
      removeAll();
      add(btnAddPattern_, "split 3");
      add(btnRemovePattern_, "");
      add(btnCreateProgram_, "wrap");
      for (SquareWaveConfigPanel panel : configPanels_) {
         add(panel, "wrap");
      }
      addSquareWavePanels();
      revalidate();
      repaint();
   }

   /**
    * Adds square wave panels to ui.
    */
   private void addSquareWavePanels() {
      displayPanel_.removeAll();
      for (SquareWaveDisplayPanel panel : displayPanels_) {
         displayPanel_.add(panel, "wrap");
      }
      displayPanel_.revalidate();
      displayPanel_.repaint();
      add(displayPanel_, "wrap");
   }

   /**
    * Create the PLogic program on its own thread.
    */
   private void createProgramThread() {
      SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {

         @Override
         protected Void doInBackground() {
            model_.studio().logs().logMessage("Start Sending PLogic Program");
            model_.isUpdating(true);

            // clear ui
            tab_.getLogicCellsTab().clearLogicCells();
            //tab_.getIOCellsTab().clearIOCells();

            // TODO: only works on first panel for now
            // send serial commands to controller (updates plc state)
            configPanels_.get(0)
                  .getSquareWavePanel()
                  .createPLogicProgram(model_.plc());

            // update ui from plc state
            tab_.getLogicCellsTab().initLogicCells();
            //tab_.getIOCellsTab().initIOCells();

            model_.isUpdating(false);
            model_.studio().logs().logMessage("Finished Sending PLogic Program");
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

}
