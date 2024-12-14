package com.asiimaging.plogic.ui.wizards;

import com.asiimaging.plogic.model.devices.ASIPLogic;
import com.asiimaging.plogic.ui.asigui.Panel;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;

// TODO: improve efficiency of drawing and creating points
public class SquareWaveDisplayPanel extends Panel {

   /** The number of logic cells required for this square wave pattern. */
   private int numCellsUsed_;

   /** The logic cell that will output the square wave signal. */
   private int outputCell_;

   /** The first logic cell to write the sequence of cells into. */
   private int startCell_;

   /** The source address for the trigger signal to start the square wave pattern. */
   private int triggerAddress_;

   /** The source of the clock signal if using a custom clock source. */
   private int clockSource_;

   // the following shape the square wave
   private int numPulses_;
   private int startDelay_;
   private int pulseDuration_;
   private int pulseDelay_;

   private double[] x;
   private double[] y;

   private boolean useDefaultClockSource_;

   // use to draw and display the square wave
   private final ArrayList<Point2D.Double> data_;

   public SquareWaveDisplayPanel() {
      setMinimumSize(new Dimension(600, 100));
      data_ = new ArrayList<>();
      numCellsUsed_ = 1;
      outputCell_ = 1;
      // user parameters
      startCell_ = 1;
      triggerAddress_ = 0;
      numPulses_ = 1;
      startDelay_ = 0;
      pulseDuration_ = 1;
      pulseDelay_ = 1;
      useDefaultClockSource_ = true;
      createXYData();
   }

   private void createXYData() {
      double maxValueX = 0.0; // used for scaling
      final int scaleFactorY = -16; // height of pulses
      // square wave points
      data_.clear();
      // first point is used to connect to bottom of first rising edge
      data_.add(new Point2D.Double(0, 0));
      for (int i = 0; i < numPulses_; i++) {
         final double risingEdgeX = startDelay_ + (i * pulseDuration_) + (i * pulseDelay_);
         final double fallingEdgeX = startDelay_ + ((i + 1) * pulseDuration_) + (i * pulseDelay_);
         data_.add(new Point2D.Double(risingEdgeX, 0));
         data_.add(new Point2D.Double(risingEdgeX, scaleFactorY));
         data_.add(new Point2D.Double(fallingEdgeX, scaleFactorY));
         data_.add(new Point2D.Double(fallingEdgeX, 0));
         maxValueX = fallingEdgeX;
      }
      // point at the edge of the display
      data_.add(new Point2D.Double(600, 0));

      final int numPoints = data_.size();
      x = new double[numPoints];
      y = new double[numPoints];
      for (int i = 0; i < numPoints - 1; i++) {
         // scale values if x coordinate is off-screen
         if (maxValueX > 600.0) {
            x[i] = remap(data_.get(i).x, 0.0, maxValueX, 0.0, 594.0);
         } else {
            x[i] = data_.get(i).x;
         }
         y[i] = data_.get(i).y;
         //System.out.println("x: " + x[i] + ", y: " + y[i]);
      }
      // last point at the edge of the display
      x[numPoints - 1] = 600;
      y[numPoints - 1] = 0;
   }

   @Override
   protected void paintComponent(Graphics g) {
      super.paintComponent(g);
      Graphics2D g2d = (Graphics2D) g;

      // background
      g2d.setColor(Color.BLACK);
      g2d.fillRect(0, 0, 600, 100);
      g2d.translate(0, 50);

      // display start and stop time
      if (useDefaultClockSource_) {
         g2d.setColor(Color.WHITE);
         g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
               RenderingHints.VALUE_ANTIALIAS_ON);
         final double startDelayMs = (startDelay_ * 0.25);
         final double value = startDelayMs + (numPulses_ * (pulseDuration_ * 0.25))
               + ((numPulses_ - 1) * (pulseDelay_ * 0.25));
         g2d.drawString("first rising edge: " + startDelayMs + " ms", 10, 40);
         g2d.drawString("last falling edge: " + value + " ms", 450, 40);
      }

      // draw square wave
      g2d.setColor(Color.LIGHT_GRAY);
      for (int i = 0; i < data_.size() - 1; i++) {
         g2d.draw(new Line2D.Double(x[i], y[i], x[i + 1], y[i + 1]));
         //g2d.draw(new Line2D.Double(data_.get(i), data_.get(i + 1)));
      }
   }

   // remaps the range of input from (min1 to max1) to (min2 to max2)
   private double remap(final double input,
                        final double min1,
                        final double max1,
                        final double min2,
                        final double max2) {
      return min2 + (input - min1) * (max2 - min2) / (max1 - min1);
   }

   /**
    * Set to {@code true} to use the default clock source of 192.
    *
    * @param state {@code true} for the default clock source
    */
   public void useDefaultClockSource(final boolean state) {
      useDefaultClockSource_ = state;
   }

   /**
    * Return the number of logic cells used for this square wave pattern.
    * <p> Note: set by update()
    *
    * @return the number of logic cells used
    */
   public int numCellsUsed() {
      return numCellsUsed_;
   }

   /**
    * Return the output logic cell for this square wave pattern.
    * <p> Note: set by update()
    *
    * @return the output logic cell
    */
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

   public void triggerAddress(final int address) {
      triggerAddress_ = address;
   }

   public int triggerAddress() {
      return triggerAddress_;
   }

   public void numPulses(final int numPulses) {
      numPulses_ = numPulses;
      update();
   }

   public int numPulses() {
      return numPulses_;
   }

   public void startDelay(final int delay) {
      startDelay_ = delay;
      update();
   }

   public int startDelay() {
      return startDelay_;
   }

   public void pulseDuration(final int duration) {
      pulseDuration_ = duration;
      update();
   }

   public int pulseDuration() {
      return pulseDuration_;
   }

   public void pulseDelay(final int delay) {
      pulseDelay_ = delay;
      update();
   }

   public int pulseDelay() {
      return pulseDelay_;
   }

   public void clockSource(final int source) {
      clockSource_ = source;
   }

   public int clockSource() {
      return clockSource_;
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
         plc.cellInput(1, triggerAddress_ + addrEdge); // trigger
         plc.cellInput(2, useDefaultClockSource_ ? (addrInvert + addrEdge) : (clockSource_ + addrEdge)); // clock
         cellAddr++;
      }
      if (numPulses_ > 1) {
         // cell 2: high time counter
         // if start delay, trigger comes from delay cell
         final int triggerSource = (startDelay_ > 0) ? (cellAddr - 1) : triggerAddress_;
         plc.pointerPosition(cellAddr);
         plc.cellType(ASIPLogic.CellType.ONE_SHOT_OR2_NRT);
         plc.cellConfig(pulseDuration_); // duration
         plc.cellInput(1, triggerSource + addrEdge); // trigger A
         plc.cellInput(2, useDefaultClockSource_ ? (addrInvert + addrEdge) : (clockSource_ + addrEdge)); // clock
         plc.cellInput(3, (cellAddr + 2) + (addrInvert + addrEdge)); // reset
         plc.cellInput(4, (cellAddr + 1) + addrEdge); // trigger B
         cellAddr++;
         // cell 3: low time counter
         plc.pointerPosition(cellAddr);
         plc.cellType(ASIPLogic.CellType.DELAY_NRT);
         plc.cellConfig(pulseDelay_); // duration
         plc.cellInput(1, (cellAddr - 1) + addrInvert + addrEdge); // trigger
         plc.cellInput(2, useDefaultClockSource_ ? (addrInvert + addrEdge) : (clockSource_ + addrEdge)); // clock
         plc.cellInput(3, (cellAddr + 1) + (addrInvert + addrEdge)); // reset
         cellAddr++;
      }
      // cell 4: number of pulses (3 and 4 cells) or high time counter (1 and 2 cells)
      plc.pointerPosition(cellAddr);
      plc.cellType(ASIPLogic.CellType.ONE_SHOT_NRT);
      if (numPulses_ > 1) {
         // for the 3 and 4 cell case
         plc.cellConfig(numPulses_); // duration
         plc.cellInput(1, (cellAddr - 2) + addrEdge); // trigger
         plc.cellInput(2, (cellAddr - 2) + addrInvert + addrEdge); // clock
      } else {
         // for the 1 and 2 cell case
         // if start delay, trigger comes from delay cell
         final int triggerSource = (startDelay_ > 0) ? (cellAddr - 1) : triggerAddress_;
         plc.cellConfig(pulseDuration_); // duration
         plc.cellInput(1, triggerSource + addrEdge); // trigger
         plc.cellInput(2, addrInvert + addrEdge); // clock
      }
   }

}
