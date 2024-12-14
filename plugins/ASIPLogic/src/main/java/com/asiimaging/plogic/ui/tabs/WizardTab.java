package com.asiimaging.plogic.ui.tabs;

import com.asiimaging.plogic.PLogicControlModel;
import com.asiimaging.plogic.ui.asigui.Button;
import com.asiimaging.plogic.ui.asigui.Panel;
import com.asiimaging.plogic.ui.utils.DialogUtils;
import com.asiimaging.plogic.ui.wizards.SquareWaveConfigPanel;
import com.asiimaging.plogic.ui.wizards.SquareWaveDisplayPanel;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingWorker;

public class WizardTab extends Panel {

   /** Display debug output for internal PLogic state. */
   private static final boolean DEBUG = false;

   private Button btnDebug_;
   private JTextArea txtDebug_;
   private JScrollPane scrollPane_;

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
      hideWizards();
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
      btnCreateProgram_.setEnabled(false);
      // debug output for internal PLogic state
      if (DEBUG) {
         btnDebug_ = new Button("Refresh Debug", 120, 30);
         txtDebug_ = new JTextArea();
         txtDebug_.setEditable(false);
         scrollPane_ = new JScrollPane(txtDebug_);
         scrollPane_.setMinimumSize(new Dimension(600, 620));
         scrollPane_.setMaximumSize(new Dimension(600, 620));
      }
      refreshUserInterface();
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
            btnCreateProgram_.setEnabled(true);
         }
         final SquareWaveDisplayPanel displayPanel = new SquareWaveDisplayPanel();
         configPanels_.add(new SquareWaveConfigPanel(displayPanel));
         displayPanels_.add(displayPanel);
         refreshUserInterface();
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
            btnCreateProgram_.setEnabled(false);
         }
         refreshUserInterface();
      });

      btnCreateProgram_.registerListener(e -> {
         if (model_.isUpdating()) {
            DialogUtils.showMessage(btnCreateProgram_,
                  "Updating", "Please wait for updates to finish.");
            return; // early exit => wait for serial traffic to end
         }
         final SquareWaveDisplayPanel panel = configPanels_.get(0).getSquareWavePanel();
         final int lastCell = panel.startCell() + panel.numCellsUsed() - 1;
         if (lastCell > model_.plc().numCells()) {
            DialogUtils.showMessage(btnCreateProgram_,
                  "Not Enough Cells", "PLogic program would end at cell " + lastCell + ".");
            return; // early exit => would write beyond number of logic cells
         }
         createProgramThread();
      });

      if (DEBUG) {
         btnDebug_.registerListener(e -> {
            txtDebug_.setText(model_.plc().state().toPrettyJson());
            txtDebug_.setCaretPosition(0);
         });
      }
   }

   /**
    * Refresh the user interface with the current number of panels.
    */
   private void refreshUserInterface() {
      removeAll();
      add(btnAddPattern_, "split 3");
      add(btnRemovePattern_, "");
      add(btnCreateProgram_, "wrap");
      for (SquareWaveConfigPanel panel : configPanels_) {
         add(panel, "wrap");
      }
      addSquareWavePanels();
      if (DEBUG) {
         add(btnDebug_, "wrap");
         add(scrollPane_, "");
      }
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
    *
    * <p>Note: only need to update the Logic Cells tab.
    */
   private void createProgramThread() {
      SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {

         @Override
         protected Void doInBackground() {
            model_.studio().logs().logMessage("Start Sending PLogic Program");
            model_.isUpdating(true);

            // clear ui
            tab_.getLogicCellsTab().clearLogicCells();

            // TODO: only works on first panel for now
            // send serial commands to controller (updates plc state)
            configPanels_.get(0)
                  .getSquareWavePanel()
                  .createPLogicProgram(model_.plc());

            // update ui from plc state
            tab_.getLogicCellsTab().initLogicCells();

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

   /**
    * This method is used to disable the Wizards tab when the PLogic device does
    * not meet the minimum firmware requirements.
    */
   public void hideWizards() {
      if (model_.plc().firmwareVersion() >= 3.50) {
         refreshUserInterface();
      } else {
         // no wizards ui
         removeAll();
         add(new JLabel("This feature requires firmware version 3.50 or greater."), "wrap");
         add(new JLabel("Please contact ASI if you would like to update."), "");
         revalidate();
         repaint();
      }
   }

}
