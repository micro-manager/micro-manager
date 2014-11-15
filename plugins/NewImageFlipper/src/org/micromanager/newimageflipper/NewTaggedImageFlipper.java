///////////////////////////////////////////////////////////////////////////////
//FILE:          NewTaggedImageFlipper.java
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

package org.micromanager.newimageflipper;

import org.micromanager.api.MMProcessorPlugin;

/**
 * Example demonstrating the use of DataProcessors.  DataProcessors can 
 * get hold of images coming out of the acquisition engine before they 
 * are inserted into the ImageCache.  DataProcessors can modify images 
 * or even generate totally new ones.
 * 
 * 
 * @author arthur
 */
public class NewTaggedImageFlipper implements MMProcessorPlugin {
   public static String menuName = "Image Flipper";
   public static String tooltipDescription = "Mirrors, flips and rotates images on the fly";
  
   public static Class<?> getProcessorClass() {
      return NewImageFlippingProcessor.class;
   }

   public void configurationChanged() {
      throw new UnsupportedOperationException("Not supported yet.");
   }

   @Override
   public String getDescription() {
      return "Rotates and/or mirrors images coming from the selected camera";
   }

   @Override
   public String getInfo() {
      return "Not supported yet.";
   }

   @Override
   public String getVersion() {
      return "Version 0.2";
   }

   @Override
   public String getCopyright() {
      return "Copyright University of California San Francisco, 2014";
   }

}
