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

import ij.CompositeImage;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.measure.Calibration;
import ij.process.ImageProcessor;
import ij.process.LUT;
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
            ImageProcessor iProc = DefaultImageJConverter.createProcessor(image, copy);
            iPlus = new ImagePlus(dp.getName() + "-ij", iProc);
            
         } catch (IOException ex) {
            // TODO: report error
         }
         if (setProps && iPlus != null && image != null) {
            setCalibration(iPlus, dp, image);
         }
         if (iPlus != null) {
            iPlus.show();   
            /* I can not figure out how to set zoom programmatically...
            iPlus.getCanvas().setMagnification(display.getZoom());
            iPlus.getWindow().pack();
            */
         }
      } else if (dp.getNumImages() > 1) {
         try {
            ImageStack imgStack = new ImageStack(dp.getAnyImage().getWidth(), 
                    dp.getAnyImage().getHeight());
            Coords.Builder cb = Coordinates.builder().c(0).t(0).p(p).z(0);
            for (int t = 0; t < dp.getNextIndex(Coords.T); t++) {
               for (int z = 0; z < dp.getNextIndex(Coords.Z); z++) {
                  for (int c = 0; c < dp.getNextIndex(Coords.C); c++) {
                     image = dp.getImage(cb.c(c).t(t).z(z).build());
                     ImageProcessor iProc;
                     if (image != null) {
                        iProc = DefaultImageJConverter.createProcessor(                            
                             image, copy);
                     } else { // handle missing images - should be handled by MM
                              // so remove this code once this is done nicely in MM
                        iProc = DefaultImageJConverter.createBlankProcessor(
                                dp.getAnyImage());
                     }
                     imgStack.addSlice(iProc);
                  }
               }
            }
            iPlus = new ImagePlus(dp.getName() + "-ij");
            iPlus.setOpenAsHyperStack(true);
            iPlus.setStack(imgStack, dp.getNextIndex(Coords.C),
                    dp.getNextIndex(Coords.Z), dp.getNextIndex(Coords.T));
            
            int displayMode;
            switch (display.getDisplaySettings().getColorMode()) {
               case COLOR: { displayMode = IJ.COLOR; break; }
               case COMPOSITE: { displayMode = IJ.COMPOSITE; break; }
               case GRAYSCALE: { displayMode = IJ.GRAYSCALE; break; }
               default: { displayMode = IJ.GRAYSCALE; break; }
            }
            iPlus.setDisplayMode(displayMode);  
            CompositeImage ci = new CompositeImage(iPlus, displayMode);
            ci.setTitle(dp.getName() + "-ij");
            for (int c = 0; c < dp.getNextIndex(Coords.C); c++) {
               ci.setChannelLut(
                       LUT.createLutFromColor(display.getDisplaySettings().getChannelColor(c)),
                       c + 1);
            }
            if (setProps && image != null) {
               setCalibration(ci, dp, image);
            }
            ci.show();
            // would like to also copy the zoom....
           
         } catch (IOException ex) {
            // TODO: report
         }
         
      }
      
      
   }
   
   private void setCalibration(ImagePlus iPlus, DataProvider dp, Image image) {
      Calibration cal = new Calibration(iPlus);
      Double pSize = image.getMetadata().getPixelSizeUm();
      if (pSize != null) {
         cal.pixelWidth = pSize;
         cal.pixelHeight = pSize;
      }
      Double zStep = dp.getSummaryMetadata().getZStepUm();
      if (zStep != null) {
         cal.pixelDepth = zStep;
      }
      Double waitInterval = dp.getSummaryMetadata().getWaitInterval();
      if (waitInterval != null) {
         cal.frameInterval = waitInterval / 1000.0;  // MM in ms, IJ in s
      }
      cal.setUnit("micron");
      iPlus.setCalibration(cal);
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
