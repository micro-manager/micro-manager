package com.asiimaging.plogic.ui.wizards;

import com.asiimaging.plogic.ui.asigui.CheckBox;
import com.asiimaging.plogic.ui.asigui.Panel;
import com.asiimaging.plogic.ui.asigui.Spinner;
import java.util.Objects;
import javax.swing.JLabel;


public class SquareWaveConfigPanel extends Panel {

   private JLabel lblNumCellsUsed_;
   private JLabel lblOutputCell_;
   private JLabel lblClockSource_;

   private Spinner spnStartAddr_;
   private Spinner spnTriggerAddr_;
   private Spinner spnClockSource_;

   private Spinner spnNumPulses_;
   private Spinner spnStartDelay_;
   private Spinner spnPulseDuration_;
   private Spinner spnPulseDelay_;

   private CheckBox cbxCustomClock_;

   private JLabel lblStartDelayMs_;
   private JLabel lblPulseDurationMs_;
   private JLabel lblPulseDelayMs_;

   private final SquareWaveDisplayPanel wavePanel_;

   public SquareWaveConfigPanel(final SquareWaveDisplayPanel wavePanel) {
      super("Square Wave Signal Generator", Panel.BORDER_LEFT);
      wavePanel_ = Objects.requireNonNull(wavePanel);
      createUserInterface();
      createEventHandlers();
   }

   private void createUserInterface() {
      setMigLayout(
            "",
            "[]5[]",
            "[]5[]"
      );
      final JLabel lblStartAddr = new JLabel("First Logic Cell:");
      final JLabel lblTriggerAddr = new JLabel("Trigger Address:");
      lblClockSource_ = new JLabel("Clock Source:");
      final JLabel lblNumPulses = new JLabel("Number of Pulses:");
      final JLabel lblStartDelay = new JLabel("Start Delay:");
      final JLabel lblPulseDuration = new JLabel("Pulse Duration:");
      final JLabel lblDelayBetweenPulses = new JLabel("Pulse Delay:");

      // matches default settings when "Add" is clicked
      lblStartDelayMs_ = new JLabel("0 ms");
      lblPulseDurationMs_ = new JLabel("0.25 ms");
      lblPulseDelayMs_ = new JLabel("0.25 ms");

      lblNumCellsUsed_ = new JLabel("");
      lblOutputCell_ = new JLabel("");
      updateLabels();

      spnStartAddr_ = Spinner.createIntegerSpinner(1, 1, 16, 1);
      spnTriggerAddr_ = Spinner.createIntegerSpinner(0, 0, 48, 1);
      spnClockSource_ = Spinner.createIntegerSpinner(0, 0, 63, 1);

      // default to unchecked
      cbxCustomClock_ = new CheckBox("Custom Clock Src", false);
      setClockSourceEnabled(false);

      final int maxValue = 65535;
      spnNumPulses_ = Spinner.createIntegerSpinner(1, 1, maxValue, 1);
      spnStartDelay_ = Spinner.createIntegerSpinner(0, 0, maxValue, 1);
      spnPulseDuration_ = Spinner.createIntegerSpinner(1, 1, maxValue, 1);
      spnPulseDelay_ = Spinner.createIntegerSpinner(1, 1, maxValue, 1);

      add(lblStartAddr, "");
      add(spnStartAddr_, "");
      add(lblTriggerAddr, "");
      add(spnTriggerAddr_, "");
      add(lblClockSource_, "");
      add(spnClockSource_, "");
      add(cbxCustomClock_, "span 2, wrap");
      add(lblNumPulses, "");
      add(spnNumPulses_, "");
      add(lblStartDelay, "");
      add(spnStartDelay_, "");
      add(lblPulseDuration, "");
      add(spnPulseDuration_, "");
      add(lblDelayBetweenPulses, "");
      add(spnPulseDelay_, "wrap");
      add(lblNumCellsUsed_, "");
      add(lblOutputCell_, "span 2");
      add(lblStartDelayMs_, "align center");
      add(lblPulseDurationMs_, "skip 1, align center");
      add(lblPulseDelayMs_, "skip 1, align center");
   }

   private void createEventHandlers() {
      spnStartAddr_.registerListener(e  -> {
         wavePanel_.startCell(spnStartAddr_.getInt());
         lblOutputCell_.setText("Output Logic Cell: " + wavePanel_.outputCell());
      });

      spnTriggerAddr_.registerListener(e -> {
         wavePanel_.triggerAddress(spnTriggerAddr_.getInt());
      });

      spnClockSource_.registerListener(e -> {
         wavePanel_.clockSource(spnClockSource_.getInt());
      });

      cbxCustomClock_.registerListener(e -> {
         final boolean selected = cbxCustomClock_.isSelected();
         wavePanel_.useDefaultClockSource(!selected);
         wavePanel_.clockSource(spnClockSource_.getInt());
         wavePanel_.repaint(); // update display
         setClockSourceEnabled(selected); // update ui
         updateTimeLabels();
      });

      spnNumPulses_.registerListener(e -> {
         wavePanel_.numPulses(spnNumPulses_.getInt());
         updateLabels();
      });

      spnStartDelay_.registerListener(e -> {
         final int startDelay = spnStartDelay_.getInt();
         wavePanel_.startDelay(startDelay);
         updateLabels();
         if (!cbxCustomClock_.isSelected()) {
            lblStartDelayMs_.setText(startDelay * 0.25 + " ms");
         }
      });

      spnPulseDuration_.registerListener(e -> {
         final int pulseDuration = spnPulseDuration_.getInt();
         wavePanel_.pulseDuration(pulseDuration);
         updateLabels();
         if (!cbxCustomClock_.isSelected()) {
            lblPulseDurationMs_.setText(pulseDuration * 0.25 + " ms");
         }
      });

      spnPulseDelay_.registerListener(e -> {
         final int pulseDelay = spnPulseDelay_.getInt();
         wavePanel_.pulseDelay(pulseDelay);
         updateLabels();
         if (!cbxCustomClock_.isSelected()) {
            lblPulseDelayMs_.setText(pulseDelay * 0.25 + " ms");
         }
      });
   }

   /**
    * Update the {@code JLabel}s for numbers of cells used and the output logic cell.
    */
   private void updateLabels() {
      lblNumCellsUsed_.setText("Cells Used: " + wavePanel_.numCellsUsed());
      lblOutputCell_.setText("Output Logic Cell: " + wavePanel_.outputCell());
   }

   /**
    * Set to enable custom clock source UI controls.
    *
    * @param state {@code true} to enable
    */
   private void setClockSourceEnabled(final boolean state) {
      lblClockSource_.setEnabled(state);
      spnClockSource_.setEnabled(state);
   }

   /**
    * If using a custom clock source then translating
    * the timing from QMS to MS no longer makes sense.
    */
   private void updateTimeLabels() {
      if (!cbxCustomClock_.isSelected()) {
         lblStartDelayMs_.setText(spnStartDelay_.getInt() * 0.25 + " ms");
         lblPulseDurationMs_.setText(spnPulseDuration_.getInt() * 0.25 + " ms");
         lblPulseDelayMs_.setText(spnPulseDelay_.getInt() * 0.25 + " ms");
      } else {
         lblStartDelayMs_.setText("-");
         lblPulseDurationMs_.setText("-");
         lblPulseDelayMs_.setText("-");
      }
   }

   public SquareWaveDisplayPanel getSquareWavePanel() {
      return wavePanel_;
   }

}
