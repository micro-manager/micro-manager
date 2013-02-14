/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.micromanager.projector;

import ij.gui.Roi;
import java.awt.Polygon;
import java.awt.geom.AffineTransform;

/**
 *
 * @author arthur
 */
public interface ProjectionDevice {
   public void displaySpot(double x, double y);
   public void displaySpot(double x, double y, double intervalUs);
   public double getWidth();
   public double getHeight();
   public void turnOn();
   public void turnOff();
   public void setRois(Polygon[] rois);
   public void setPolygonRepetitions(int reps);
   public void runPolygons();
   public void addOnStateListener(OnStateListener listener);
   public String getChannel();
   public String getName();
   public void waitForDevice();
   public void setSpotInterval(long interval_us);
}
