///////////////////////////////////////////////////////////////////////////////
//FILE:          TaggedImageFlipper.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Arthur Edelstein, Nico Stuurman
//
// COPYRIGHT:    University of California, San Francisco, 2011, 2012
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

package org.micromanager.imageflipper;

import org.micromanager.api.AcquisitionEngine;
import org.micromanager.api.MMPlugin;
import org.micromanager.api.ScriptInterface;

/**
 * Example demonstrating the use of DataProcessors.  DataProcessors can 
 * get hold of images coming out of the acquisition engine before they 
 * are inserted into the ImageCache.  DataProcessors can modify images 
 * or even generate totally new ones.
 * 
 * This specific example has grown out to modify images only from a specific camera
 * and is therefore very useful when using multiple cameras
 * 
 * @author arthur
 */
public class TaggedImageFlipper implements MMPlugin {
   public static String menuName = "Image Flipper";
   public static String tooltipDescription = "Mirrors, flips and rotates images on the fly";
   private ScriptInterface gui_;
   private ImageFlipperControls ctls_;
   

   public void dispose() {
      //throw new UnsupportedOperationException("Not supported yet.");
   }

   public void setApp(ScriptInterface app) {
      gui_ = app;
   }

   public void show() {
      if (ctls_ == null)
         ctls_ = new ImageFlipperControls();
      else
         ctls_.updateCameras();
      AcquisitionEngine eng = gui_.getAcquisitionEngine();
      eng.addImageProcessor(ctls_.getProcessor());
      ctls_.setVisible(true);
   }

   public void configurationChanged() {
      throw new UnsupportedOperationException("Not supported yet.");
   }

   public String getDescription() {
      throw new UnsupportedOperationException("Not supported yet.");
   }

   public String getInfo() {
      throw new UnsupportedOperationException("Not supported yet.");
   }

   public String getVersion() {
      throw new UnsupportedOperationException("Not supported yet.");
   }

   public String getCopyright() {
      throw new UnsupportedOperationException("Not supported yet.");
   }

}
