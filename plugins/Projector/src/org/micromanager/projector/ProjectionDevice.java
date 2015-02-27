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

import ij.process.FloatPolygon;
import java.awt.Polygon;
import java.util.List;

public interface ProjectionDevice {
   // Properties of device.
   public String getName();
   public String getChannel();
    public double getHeight();
   public double getWidth();
 
   // ## Alert when something has changed.
   public void addOnStateListener(OnStateListener listener);

   // ## Get/set internal exposure setting
   public long getExposure();
   public void setExposure(long interval_us);

   // ## Control illumination
   public void turnOn();
   public void turnOff();
   public void displaySpot(double x, double y);
   public void activateAllPixels();

   // ## ROIs
   public void loadRois(List<FloatPolygon> rois);
   public void setPolygonRepetitions(int reps);
   public void runPolygons();

   public void waitForDevice();
}
