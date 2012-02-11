/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.micromanager.projector;

import ij.gui.Roi;
import java.awt.geom.AffineTransform;

/**
 *
 * @author arthur
 */
public interface ProjectionDevice {
   public void displaySpot(double x, double y);
   public double getWidth();
   public double getHeight();
   public void turnOn();
   public void turnOff();
   public void setRoi(Roi roi, AffineTransform transform);
}
