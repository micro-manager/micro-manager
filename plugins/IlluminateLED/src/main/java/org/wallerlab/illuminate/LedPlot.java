/*
Copyright (c) 2019, Regents of the University of California
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:
    * Redistributions of source code must retain the above copyright
      notice, this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above copyright
      notice, this list of conditions and the following disclaimer in the
      documentation and/or other materials provided with the distribution.
    * Neither the name of the <organization> nor the
      names of its contributors may be used to endorse or promote products
      derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL REGENTS OF THE UNIVERSITY OF CALIFORNIA BE LIABLE 
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.wallerlab.illuminate;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import javax.swing.JPanel;

/**
 * @author zack
 */


public class LedPlot extends JPanel {

   private double[][] ledCoordinates;
   private int[][] ledValues;
   private boolean[] ledMask;
   private double maxX;
   private double maxY;
   private double minX;
   private double minY;
   private int ledUsedCount;
   public int circleSize;
   public double paddingFactor = 0.8;
   public double ledCoordinatesToPixel;
   private Color selectedColor;

   public LedPlot(int ledCount) {
      ledValues = new int[ledCount][3];
   }

   public void setLedCoordinates(double[][] ledCoordinates, boolean[] ledMask, int ledUsedCount) {
      this.ledCoordinates = ledCoordinates;
      this.ledMask = ledMask;
      this.ledUsedCount = ledUsedCount;
   }

   public void setBounds(double maxX, double maxY) {
      this.maxX = maxX;
      this.maxY = maxY;
   }

   @Override
   public Dimension getPreferredSize() {
      return new Dimension(600, 500);
   }

   public int ledCoordinatesToPixels(double ledCoord, int wh) {
      return (int) (Math.round(ledCoord * ledCoordinatesToPixel)) + wh / 2 - circleSize / 2;
   }

   public double pxToLedCoords(int px) {
      return (double) px / ledCoordinatesToPixel;
   }

   public void clearArray() {
      for (int ledIndex = 0; ledIndex < ledCoordinates.length; ledIndex++) {

         if (ledMask[ledIndex]) {
            ledValues[ledIndex][0] = 0;
            ledValues[ledIndex][1] = 0;
            ledValues[ledIndex][2] = 0;
         }
      }
      repaint();
   }

   public void fillArray() {
      for (int ledIndex = 0; ledIndex < ledCoordinates.length; ledIndex++) {

         if (ledMask[ledIndex]) {
            ledValues[ledIndex][0] = selectedColor.getRed();
            ledValues[ledIndex][1] = selectedColor.getGreen();
            ledValues[ledIndex][2] = selectedColor.getBlue();
         }
      }
      repaint();
   }

   public void setLedValue(int ledIndex) {
      if (ledMask[ledIndex]) {
         ledValues[ledIndex][0] = selectedColor.getRed();
         ledValues[ledIndex][1] = selectedColor.getGreen();
         ledValues[ledIndex][2] = selectedColor.getBlue();
      }

   }

   public void setLedValue(int ledIndex, Color newColor) {
      if (ledMask[ledIndex]) {
         ledValues[ledIndex][0] = newColor.getRed();
         ledValues[ledIndex][1] = newColor.getGreen();
         ledValues[ledIndex][2] = newColor.getBlue();
         //System.out.println("Led set: " + Integer.toString(ledIndex));
      }
   }

   public void setColor(Color newColor) {
      selectedColor = newColor;
   }

   public Color getSelectedColor() {
      return (selectedColor);
   }

   @Override
   protected void paintComponent(Graphics g) {

      super.paintComponent(g);
      Graphics2D g2d = (Graphics2D) g.create();
      Color myColor;

      // Base circle Size on number of LEDs in window and window size
      int wh = Math.min(getWidth(), getHeight());
      circleSize = (int) Math.round(paddingFactor * wh / Math.sqrt(ledUsedCount));
      for (int ledIndex = 0; ledIndex < ledCoordinates.length; ledIndex++) {

         ledCoordinatesToPixel = wh / 2 * paddingFactor / maxX;

         int xPx = ledCoordinatesToPixels(ledCoordinates[ledIndex][0], getWidth());
         int yPx = ledCoordinatesToPixels(ledCoordinates[ledIndex][1], getHeight());
         if (ledMask[ledIndex]) {
            myColor = new Color(ledValues[ledIndex][0], ledValues[ledIndex][1],
                  ledValues[ledIndex][2]);
            g2d.setColor(myColor);
            if (ledValues[ledIndex][0] + ledValues[ledIndex][1] + ledValues[ledIndex][2] > 0) {
               g2d.fillOval(xPx, yPx, circleSize, circleSize);
            }

            g2d.setColor(Color.BLACK);
            g2d.drawOval(xPx, yPx, circleSize, circleSize);
         }

      }
      g2d.dispose();
   }

}
