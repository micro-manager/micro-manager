package com.asiimaging.plogic.ui.wizards;

import com.asiimaging.plogic.ui.asigui.Panel;
import com.asiimaging.plogic.ui.asigui.Spinner;
import java.util.Objects;
import javax.swing.JLabel;


public class SquareWaveConfigPanel extends Panel {

   private JLabel lblNumCellsUsed_;
   private JLabel lblOutputCell_;
   private Spinner spnStartAddr_;
   private Spinner spnSourceAddr_;

   private Spinner spnNumPulses_;
   private Spinner spnStartDelay_;
   private Spinner spnPulseDuration_;
   private Spinner spnPulseDelay_;

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
      final JLabel lblSourceAddr = new JLabel("Source Address:");
      final JLabel lblNumPulses = new JLabel("Number of Pulses:");
      final JLabel lblStartDelay = new JLabel("Start Delay:");
      final JLabel lblPulseDuration = new JLabel("Pulse Duration:");
      final JLabel lblDelayBetweenPulses = new JLabel("Pulse Delay:");

      lblNumCellsUsed_ = new JLabel("");
      lblOutputCell_ = new JLabel("");
      updateLabels();

      spnStartAddr_ = Spinner.createIntegerSpinner(1, 1, 16, 1);
      spnSourceAddr_ = Spinner.createIntegerSpinner(0, 0, 48, 1);

      final int maxValue = 65535;
      spnNumPulses_ = Spinner.createIntegerSpinner(1, 1, maxValue, 1);
      spnStartDelay_ = Spinner.createIntegerSpinner(0, 0, maxValue, 1);
      spnPulseDuration_ = Spinner.createIntegerSpinner(1, 1, maxValue, 1);
      spnPulseDelay_ = Spinner.createIntegerSpinner(1, 1, maxValue, 1);

      add(lblStartAddr, "");
      add(spnStartAddr_, "");
      add(lblSourceAddr, "");
      add(spnSourceAddr_, "");
      add(lblNumCellsUsed_, "");
      add(lblOutputCell_, "span 2, wrap");
      add(lblNumPulses, "");
      add(spnNumPulses_, "");
      add(lblStartDelay, "");
      add(spnStartDelay_, "");
      add(lblPulseDuration, "");
      add(spnPulseDuration_, "");
      add(lblDelayBetweenPulses, "");
      add(spnPulseDelay_, "");
   }

   private void createEventHandlers() {
      spnStartAddr_.registerListener(e  -> {
         wavePanel_.startCell(spnStartAddr_.getInt());
         lblOutputCell_.setText("Output Logic Cell: " + wavePanel_.outputCell());
      });

      spnSourceAddr_.registerListener(e -> {
         wavePanel_.sourceAddress(spnSourceAddr_.getInt());
      });

      spnNumPulses_.registerListener(e -> {
         wavePanel_.numPulses(spnNumPulses_.getInt());
         updateLabels();
      });

      spnStartDelay_.registerListener(e -> {
         wavePanel_.startDelay(spnStartDelay_.getInt());
         updateLabels();
      });

      spnPulseDuration_.registerListener(e -> {
         wavePanel_.pulseDuration(spnPulseDuration_.getInt());
         updateLabels();
      });

      spnPulseDelay_.registerListener(e -> {
         wavePanel_.pulseDelay(spnPulseDelay_.getInt());
         updateLabels();
      });
   }

   /**
    * Update the {@code JLabel}s for numbers of cells used and the output logic cell.
    */
   private void updateLabels() {
      lblNumCellsUsed_.setText("Cells Used: " + wavePanel_.numCellsUsed());
      lblOutputCell_.setText("Output Logic Cell: " + wavePanel_.outputCell());
   }

   public SquareWaveDisplayPanel getSquareWavePanel() {
      return wavePanel_;
   }

}
