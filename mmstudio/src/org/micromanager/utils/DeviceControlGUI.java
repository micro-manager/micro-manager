///////////////////////////////////////////////////////////////////////////////
//FILE:          AcquisitionData.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nenad Amodaj, nenad@amodaj.com, December 3, 2006
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
 * Interface to exectute commands in the main panel.
 */
public interface DeviceControlGUI {
   public void updateGUI();
   public void initializeGUI();
   public void updateImageGUI();
   public boolean updateImage();
   public boolean okToAcquire();
   public void stopAllActivity();
   public void applyContrastSettings(ContrastSettings contrast8_, ContrastSettings contrast16_);
   public ContrastSettings getContrastSettings();
   public boolean is16bit();
}
