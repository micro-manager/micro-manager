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

/** @author zack */
public class LedPlot extends JPanel {

  private double[][] led_coordinates;
  private int[][] led_values;
  private boolean[] led_mask;
  private double maxX, maxY, minX, minY;
  private int ledUsedCount;
  public int circleSize;
  public double padding_factor = 0.8;
  public double led_coordinates_to_pixel;
  private Color selected_color;

  public LedPlot(int ledCount) {
    led_values = new int[ledCount][3];
  }

  public void setLedCoordinates(double[][] led_coordinates, boolean[] led_mask, int ledUsedCount) {
    this.led_coordinates = led_coordinates;
    this.led_mask = led_mask;
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

  public int led_coordinates_to_pixels(double ledCoord, int wh) {
    return (int) (Math.round(ledCoord * led_coordinates_to_pixel)) + wh / 2 - circleSize / 2;
  }

  public double pxToLedCoords(int px) {
    return (double) px / led_coordinates_to_pixel;
  }

  public void clearArray() {
    for (int led_index = 0; led_index < led_coordinates.length; led_index++) {

      if (led_mask[led_index]) {
        led_values[led_index][0] = 0;
        led_values[led_index][1] = 0;
        led_values[led_index][2] = 0;
      }
    }
    repaint();
  }

  public void fillArray() {
    for (int led_index = 0; led_index < led_coordinates.length; led_index++) {

      if (led_mask[led_index]) {
        led_values[led_index][0] = selected_color.getRed();
        led_values[led_index][1] = selected_color.getGreen();
        led_values[led_index][2] = selected_color.getBlue();
      }
    }
    repaint();
  }

  public void setLedValue(int led_index) {
    if (led_mask[led_index]) {
      led_values[led_index][0] = selected_color.getRed();
      led_values[led_index][1] = selected_color.getGreen();
      led_values[led_index][2] = selected_color.getBlue();
    }
  }

  public void setLedValue(int led_index, Color newColor) {
    if (led_mask[led_index]) {
      led_values[led_index][0] = newColor.getRed();
      led_values[led_index][1] = newColor.getGreen();
      led_values[led_index][2] = newColor.getBlue();
      // System.out.println("Led set: " + Integer.toString(led_index));
    }
  }

  public void setColor(Color newColor) {
    selected_color = newColor;
  }

  public Color getSelectedColor() {
    return (selected_color);
  }

  @Override
  protected void paintComponent(Graphics g) {

    super.paintComponent(g);
    Graphics2D g2d = (Graphics2D) g.create();
    Color myColor;

    // Base circle Size on number of LEDs in window and window size
    int wh = Math.min(getWidth(), getHeight());
    circleSize = (int) Math.round(padding_factor * wh / Math.sqrt(ledUsedCount));
    for (int led_index = 0; led_index < led_coordinates.length; led_index++) {

      led_coordinates_to_pixel = wh / 2 * padding_factor / maxX;

      int x_px = led_coordinates_to_pixels(led_coordinates[led_index][0], getWidth());
      int y_px = led_coordinates_to_pixels(led_coordinates[led_index][1], getHeight());
      if (led_mask[led_index]) {
        myColor =
            new Color(led_values[led_index][0], led_values[led_index][1], led_values[led_index][2]);
        g2d.setColor(myColor);
        if (led_values[led_index][0] + led_values[led_index][1] + led_values[led_index][2] > 0)
          g2d.fillOval(x_px, y_px, circleSize, circleSize);

        g2d.setColor(Color.BLACK);
        g2d.drawOval(x_px, y_px, circleSize, circleSize);
      }
    }
    g2d.dispose();
  }
}
