///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nico Stuurman, 2019
//
// COPYRIGHT:    University of California, San Francisco, 2019
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

package org.micromanager.display.internal.gearmenu;

import ij.ImagePlus;
import ij.ImageStack;
import ij.measure.Calibration;
import ij.process.ImageProcessor;
import java.io.IOException;
import org.micromanager.Studio;
import org.micromanager.data.Coordinates;
import org.micromanager.data.Coords;
import org.micromanager.data.DataProvider;
import org.micromanager.data.Image;
import org.micromanager.data.internal.DefaultImageJConverter;
import org.micromanager.display.DisplayGearMenuPlugin;
import org.micromanager.display.DisplayWindow;
import org.scijava.plugin.Plugin;
import org.scijava.plugin.SciJavaPlugin;

/**
 *
 * @author Nico
 */
@Plugin(type = DisplayGearMenuPlugin.class)
public final class CopyToImageJItem implements DisplayGearMenuPlugin, SciJavaPlugin {
   private Studio studio_;
   
   @Override
   public String getSubMenu() {
      return "";
   }

   @Override
   public void onPluginSelected(DisplayWindow display) {
      final boolean copy = false;
      final boolean setProps = true;
      // TODO: UI to set copy, give option to only do partial data, and multiple positions
      
      DataProvider dp = display.getDataProvider();
      Coords displayPosition = display.getDisplayPosition();
      int p = displayPosition.getP();
      
      ImagePlus iPlus = null;
      Image image = null;
      if (dp.getNumImages() == 1) {
         try {
            image = dp.getAnyImage();
            ImageProcessor iProc = DefaultImageJConverter.getInstance().createProcessor(image, copy);
            iPlus = new ImagePlus(dp.getName() + "-ij", iProc);
            
         } catch (IOException ex) {
            // TODO: report error
         }
      } else if (dp.getNumImages() > 1) {
         try {
            ImageStack imgStack = new ImageStack(dp.getAnyImage().getWidth(), 
                    dp.getAnyImage().getHeight());
            Coords.Builder cb = Coordinates.builder().c(0).t(0).p(p).z(0);
            for (int t = 0; t <= dp.getMaxIndices().getT(); t++) {
               for (int z = 0; z <= dp.getMaxIndices().getZ(); z++) {
                  for (int c = 0; c <= dp.getMaxIndices().getC(); c++) {
                     image = dp.getImage(cb.c(c).t(t).z(z).build());
                     ImageProcessor iProc = DefaultImageJConverter.getInstance().createProcessor(image, copy);
                     imgStack.addSlice(iProc);
                  }
               }
            }
            iPlus = new ImagePlus(dp.getName() + "-ij");
            iPlus.setOpenAsHyperStack(true);
            iPlus.setStack(imgStack, dp.getMaxIndices().getC() + 1, 
                    dp.getMaxIndices().getZ() + 1, dp.getMaxIndices().getT() + 1);
         } catch (IOException ex) {
            // TODO: report
         }
         
      }
      if (setProps && iPlus != null && image != null) {
         Calibration cal = new Calibration(iPlus);
         cal.pixelWidth = image.getMetadata().getPixelSizeUm();
         cal.pixelHeight = image.getMetadata().getPixelSizeUm();
         cal.pixelDepth = dp.getSummaryMetadata().getZStepUm();
         cal.frameInterval = dp.getSummaryMetadata().getWaitInterval() / 1000.0;  // MM in ms, IJ in s
         cal.setUnit("micron");
         iPlus.setCalibration(cal);
      }
      if (iPlus != null) {
         iPlus.show();
         // display.getZoom throws an unsupported exception!
         // iPlus.getCanvas().setMagnification(display.getZoom());
      }
      
      
   }

   @Override
   public void setContext(Studio studio) {
      studio_ = studio;
   }

   @Override
   public String getName() {
      return "To ImageJ...";
   }

   @Override
   public String getHelpText() {
      return "Makes selected data available in ImageJ";
   }

   @Override
   public String getVersion() {
      return "0.1";
   }

   @Override
   public String getCopyright() {
     return "Copyright (c) Regents of the University of California";
   }
   
   
}
