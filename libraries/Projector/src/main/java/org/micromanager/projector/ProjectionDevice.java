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
import java.util.List;
import org.micromanager.projector.internal.OnStateListener;

/**
 * Abstraction of Projection device.  We currently have Galvos (single point at a time,
 * could conceivably display patterns), and SLMs (which is the same as a display screen,
 * i.e. an image with width, height and possibly intensity).
 */
public interface ProjectionDevice {

   // Properties of device.
   String getName();

   /**
    * Strange function that only seems to have an effect on Rapp bleacher. This may need to be
    * deprecated
    *
    * @return channel name
    */
   String getChannel();

   double getXRange();

   double getYRange();

   double getXMinimum();

   double getYMinimum();

   /**
    * Provides the name of a valid shutter device in Micro-Manager The shutter will be opened and
    * closed as if it were an illumination source controlled by the Projection device, i.e., it will
    * open for the specified amount of time in the displaySpot function, etc.. If the Shutter is a
    * null object or an empty string or not a valid MM device it will be ignored
    *
    * @param shutter Name of a shutter device
    */
   void setExternalShutter(String shutter);

   /**
    * returns the external shutter device.  Will be null if no external shutter device was
    * previously set, or if it was not a valid MM device
    *
    * @return external shutter
    */
   String getExternalShutter();

   // ## Alert when something has changed.
   void addOnStateListener(OnStateListener listener);

   // ## Get/set internal exposure setting
   long getExposure();

   /**
    * Sets exposure of the projection device in Micro-Seconds.
    *
    * @param intervalUs Exposure in Micro-Seconds
    */
   void setExposure(long intervalUs);

   // ## Control illumination
   void turnOn();

   void turnOff();

   /**
    * Displays a spot at location x, y (in Projector coordinates) for a duration set in setExposure.
    * This function will display a spot on an SLM, or point a pointing device to the desired
    * location, and illuminate for the desired duration
    *
    * @param x x position (in Projector coordinates)
    * @param y y position (in Porjector coordinates)
    */
   void displaySpot(double x, double y);

   /**
    * Switches all pixels of an SLM device to the "on"/"Active" position. This function has no
    * effect on a Galvo device
    */
   void activateAllPixels();

   /**
    * Projects an x * y checkboard pattern on the SLM has no effect on Galvo device.
    *
    * @param x # of fields (black + white) in x direction
    * @param y # of fields (black + white) in y direction
    */
   void showCheckerBoard(int x, int y);

   // ## ROIs
   void loadRois(List<FloatPolygon> rois);

   void setPolygonRepetitions(int reps);

   void runPolygons();

   void waitForDevice();
}
