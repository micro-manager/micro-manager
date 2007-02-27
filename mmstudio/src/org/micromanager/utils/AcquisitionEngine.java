///////////////////////////////////////////////////////////////////////////////
//FILE:          AcquisitionEngine.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nenad Amodaj, nenad@amodaj.com, November 1, 2005
//
// COPYRIGHT:    University of California, San Francisco, 2006
//
// LICENSE:      This file is distributed under the BSD license.
//               License text is included with the source distribution.
//
//               This file is distributed in the hope that it will be useful,
//               but WITHOUT ANY WARRANTY; without even the implied warranty
//               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//
// CVS:          $Id$
//
package org.micromanager.utils;

/**
 * Acquisition engine interface.
 */
public interface AcquisitionEngine {
   public void startAcquisition();
   public void stopAcquisition();
   public boolean isAcquisitionRunning();
   public int getCurrentFrameCount();
   public double getFrameIntervalMs();
   public double getSliceZStepUm();
   public double getSliceZBottomUm();
   public void updateImageGUI();
}
