package com.asiimaging.plogic.ui.wizards;

import com.asiimaging.plogic.model.devices.ASIPLogic;
import com.asiimaging.plogic.ui.asigui.Panel;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;

public class SquareWaveDisplayPanel extends Panel {

   /** The number of logic cells required for this square wave pattern. */
   private int numCellsUsed_;

   /** The logic cell that will output the square wave signal. */
   private int outputCell_;

   /** The first logic cell to write the sequence of cells into. */
   private int startCell_;

   /** The source address for the trigger signal to start the square wave pattern. */
   private int sourceAddress_;

   // the following shape the square wave
   private int numPulses_;
   private int startDelay_;
   private int pulseDuration_;
   private int pulseDelay_;

   // use to draw and display the square wave
   private final ArrayList<Point2D.Double> data_;

   public SquareWaveDisplayPanel() {
      setMinimumSize(new Dimension(600, 100));
      data_ = new ArrayList<>();
      numCellsUsed_ = 1;
      outputCell_ = 1;
      // user parameters
      startCell_ = 1;
      sourceAddress_ = 0;
      numPulses_ = 1;
      startDelay_ = 0;
      pulseDuration_ = 1;
      pulseDelay_ = 1;
      createXYData();
   }

   private void createXYData() {
      final int scaleFactor = -16;
      data_.clear();
      data_.add(new Point2D.Double(0, 0)); // time = 0 ms
      for (int i = 0; i < numPulses_; i++) {
         final double risingEdgeX = startDelay_ + (i * pulseDuration_) + (i * pulseDelay_);
         final double fallingEdgeX = startDelay_ + ((i + 1) * pulseDuration_) + (i * pulseDelay_);
         data_.add(new Point2D.Double(risingEdgeX, 0));
         data_.add(new Point2D.Double(risingEdgeX, scaleFactor));
         data_.add(new Point2D.Double(fallingEdgeX, scaleFactor));
         data_.add(new Point2D.Double(fallingEdgeX, 0));
      }
      // point at the edge of the display
      data_.add(new Point2D.Double(600, 0));
   }

   @Override
   protected void paintComponent(Graphics g) {
      super.paintComponent(g);
      Graphics2D g2d = (Graphics2D) g;

      g2d.setColor(Color.BLACK);
      g2d.fillRect(0, 0, 600, 100);

      g2d.setColor(Color.LIGHT_GRAY);
      g2d.translate(0, 50);

      for (int i = 0; i < data_.size() - 1; i++) {
         g2d.draw(new Line2D.Double(data_.get(i), data_.get(i + 1)));
      }

   }

   /**
    * Return the number of logic cells used for this square wave pattern.
    *
    * @return the number of logic cells used
    */
   public int numCellsUsed() {
      return numCellsUsed_;
   }

   public int outputCell() {
      return outputCell_;
   }

   public void startCell(final int startCell) {
      startCell_ = startCell;
      outputCell_ = updateOutputCell();
   }

   public int startCell() {
      return startCell_;
   }

   public void sourceAddress(final int sourceAddress) {
      sourceAddress_ = sourceAddress;
   }

   public int sourceAddress() {
      return sourceAddress_;
   }

   public void numPulses(final int numPulses) {
      numPulses_ = numPulses;
      update();
   }

   public void startDelay(final int startDelay) {
      startDelay_ = startDelay;
      update();
   }

   public void pulseDuration(final int pulseWidth) {
      pulseDuration_ = pulseWidth;
      update();
   }

   public void pulseDelay(final int pulseDelay) {
      pulseDelay_ = pulseDelay;
      update();
   }

   /**
    * Updates the number of logic cells used and the output logic cell.
    *
    * <p>This method also updates the square wave pattern data and redraws it.
    */
   private void update() {
      numCellsUsed_ = updateNumCellsUsed();
      outputCell_ = updateOutputCell();
      createXYData();
      repaint();
   }

   /**
    * Return the number of logic cells used by this square wave pattern.
    *
    * @return the number of logic cells
    */
   private int updateNumCellsUsed() {
      int count = 1;
      if (startDelay_ > 0) {
         count++;
      }
      if (numPulses_ > 1) {
         count += 2;
      }
      return count;
   }

   /**
    * Return the logic cell used to output this square wave pattern.<br>
    *
    * <p>Output Cells:<br>
    * 1 and 3 cells used: first logic cell<br>
    * 2 and 4 cells used: second logic cell
    *
    * @return the logic cell used for output
    */
   private int updateOutputCell() {
      return (numCellsUsed_ == 1 || numCellsUsed_ == 3) ? startCell_ : (startCell_ + 1);
   }

   /**
    * Create a PLogic program for the square wave parameters.
    *
    * <p>This can be anywhere from 1 to 4 logic cells in complexity.
    *
    * <p>Types:<br>
    * 1 cell: one pulse<br>
    * 2 cells: one pulse with start delay<br>
    * 3 cells: multiple pulses<br>
    * 4 cells: multiple pulses with start delay
    *
    * @param plc the PLogic device to program
    */
   public void createPLogicProgram(final ASIPLogic plc) {
      final int addrInvert = 64;
      final int addrEdge = 128;
      // start at initial cell, increment cellAddr for each additional logic cell
      int cellAddr = startCell_;
      if (startDelay_ > 0) {
         // cell 1: start delay
         plc.pointerPosition(cellAddr);
         plc.cellType(ASIPLogic.CellType.DELAY_NRT);
         plc.cellConfig(startDelay_); // duration
         plc.cellInput(1, sourceAddress_); // trigger
         plc.cellInput(2, addrEdge + addrInvert); // clock
         cellAddr++;
      }
      if (numPulses_ > 1) {
         // cell 2: high time counter
         // if start delay, trigger comes from delay cell
         final int triggerSource = (startDelay_ > 0) ? (cellAddr - 1) : sourceAddress_;
         plc.pointerPosition(cellAddr);
         plc.cellType(ASIPLogic.CellType.ONE_SHOT_OR2_NRT);
         plc.cellConfig(pulseDuration_); // duration
         plc.cellInput(1, triggerSource); // trigger A
         plc.cellInput(2, addrEdge + addrInvert); // clock
         plc.cellInput(3, (cellAddr + 2) + (addrEdge + addrInvert)); // reset
         plc.cellInput(4, cellAddr + 1); // trigger B
         cellAddr++;
         // cell 3: low time counter
         plc.pointerPosition(cellAddr);
         plc.cellType(ASIPLogic.CellType.DELAY_NRT);
         plc.cellConfig(pulseDelay_); // duration
         plc.cellInput(1, (cellAddr - 1) + addrInvert); // trigger
         plc.cellInput(2, addrEdge + addrInvert); // clock
         plc.cellInput(3, (cellAddr + 1) + (addrEdge + addrInvert)); // reset
         cellAddr++;
      }
      // cell 4: number of pulses (3 and 4 cells) or high time counter (1 and 2 cells)
      plc.pointerPosition(cellAddr);
      plc.cellType(ASIPLogic.CellType.ONE_SHOT_NRT);
      if (numPulses_ > 1) {
         // for the 3 and 4 cell case
         plc.cellConfig(numPulses_); // duration
         plc.cellInput(1, cellAddr - 2); // trigger
         plc.cellInput(2, (cellAddr - 2) + (addrEdge + addrInvert)); // clock
      } else {
         // for the 1 and 2 cell case
         // if start delay, trigger comes from delay cell
         final int triggerSource = (startDelay_ > 0) ? (cellAddr - 1) : sourceAddress_;
         plc.cellConfig(pulseDuration_); // duration
         plc.cellInput(1, triggerSource); // trigger
         plc.cellInput(2, addrEdge + addrInvert); // clock
      }
   }

}
