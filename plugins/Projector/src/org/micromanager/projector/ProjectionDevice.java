///////////////////////////////////////////////////////////////////////////////
//FILE:          ProjectionDevice.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     Projector plugin
//-----------------------------------------------------------------------------
//AUTHOR:        Arthur Edelstein
//COPYRIGHT:     University of California, San Francisco, 2010-2014
//LICENSE:       This file is distributed under the BSD license.
//               License text is included with the source distribution.
//               This file is distributed in the hope that it will be useful,
//               but WITHOUT ANY WARRANTY; without even the implied warranty
//               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.

package org.micromanager.projector;

import ij.gui.Roi;
import java.awt.Polygon;
import java.awt.geom.AffineTransform;
import java.util.List;

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
   public void loadRois(List<Polygon> rois);
   public void setPolygonRepetitions(int reps);
   public void runPolygons();
   public void addOnStateListener(OnStateListener listener);
   public String getChannel();
   public String getName();
   public void waitForDevice();
   public void setDwellTime(long interval_us);
   public void activateAllPixels();
}
