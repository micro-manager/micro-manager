///////////////////////////////////////////////////////////////////////////////
//FILE:          PtcToolsExecutor.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nico Stuurman
//
// COPYRIGHT:    University of California, San Francisco, 2018
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



package org.micromanager.ptctools;

import ij.ImagePlus;
import ij.process.FloatProcessor;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import mmcorej.CMMCore;
import mmcorej.TaggedImage;
import org.micromanager.PropertyMap;
import org.micromanager.Studio;
import org.micromanager.data.Coordinates;
import org.micromanager.data.Coords;
import org.micromanager.data.Datastore;
import org.micromanager.data.Image;
import org.micromanager.data.SummaryMetadata;
import org.micromanager.internal.utils.NumberUtils;

public class PtcToolsExecutor extends Thread  {
   private final Studio studio_;
   private final PropertyMap settings_;
   
   public PtcToolsExecutor(Studio studio, PropertyMap settings) {
      studio_ = studio;
      settings_ = settings;
   }

   @Override
   public void run() {
      boolean dr = ij.IJ.showMessageWithCancel("PTC Tools", "Prevent all light going to the"
              + " camera.  Press OK when ready");
      if (!dr) {
         return;
      }
      
      Datastore storeDark = studio_.data().createRAMDatastore();
      SummaryMetadata.Builder smbDark = studio_.data().getSummaryMetadataBuilder();
      Coords.Builder cb = Coordinates.builder();
      Coords coords = cb.c(1).p(1).
              t(settings_.getInteger(PtcToolsTerms.NRFRAMES, 100)).z(1).build();
      try {
         storeDark.setSummaryMetadata(smbDark.intendedDimensions(coords).startDate(
                 new SimpleDateFormat("yyyy.MM.dd.HH.mm.ss").format(new Date())).build());
      } catch (IOException ex) {
         // should never happen with a RAMDatastore...
      }
      
      CMMCore core = studio_.getCMMCore(); // to reduce typing
      double exposure;
      try {
         exposure = NumberUtils.displayStringToDouble(
                 settings_.getString(PtcToolsTerms.MINIMUMEXPOSURE, "0.1"));
      } catch (ParseException ex) {
         studio_.logs().showError("Minimum exposire should be a number");
         return;
      }
      try {
         core.setExposure(exposure);
      } catch (Exception ex) {
         studio_.logs().showError("Failed to set exposure time");
         return;
      }
      
      int nrFrames = settings_.getInteger(PtcToolsTerms.NRFRAMES, 100);
        
      try {
         core.startSequenceAcquisition(nrFrames, 0.0, true);
         int frCounter = 0;
         // TODO: this can hang
         while (core.isSequenceRunning() || core.getRemainingImageCount() > 0) {
            if (core.getRemainingImageCount() > 0) {
               TaggedImage nextImage = core.popNextTaggedImage();
               if (nextImage != null) {
                  Image img = studio_.data().convertTaggedImage(nextImage);
                  storeDark.putImage(img.copyAtCoords(cb.t(frCounter).build()));
                  frCounter++;
               }
            }
         }
         storeDark.freeze();
      } catch (Exception ex) {
         studio_.logs().showError(ex, "Error while acquiring images");
      }
      
      // TODO: make sure that we have 16-bit (short) images
      try {
         List<Object> lc = new ArrayList<Object>(nrFrames);
         for (int i = 0; i < nrFrames; i++) {
            lc.add(storeDark.getImage(cb.t(i).build()).getRawPixels());
         }
         Image ri = storeDark.getAnyImage();
         // mean offset
         FloatProcessor mean = getMeanImg(ri, lc);
         // stddev
         FloatProcessor stdDev = getStdDevImg(ri, mean, lc);
         
      
         ImagePlus tmp = new ImagePlus("mean", mean);
         tmp.show();
         ImagePlus tmp2 = new ImagePlus("stdDev", stdDev);
         tmp2.show();
      } catch (IOException ex) {
         //Logger.getLogger(PtcToolsExecutor.class.getName()).log(Level.SEVERE, null, ex);
      }

   }
   
   public FloatProcessor getMeanImg(Image ri, List<Object> lc) {
      FloatProcessor mean = new FloatProcessor(ri.getWidth(), ri.getHeight());
      for (int p = 0; p < mean.getPixelCount(); p++) {
         float sum = 0.0f;
         for (int i = 0; i < lc.size(); i++) {
            short[] rawPixels = (short[]) lc.get(i);
            sum += rawPixels[p] & 0xffff;
         }
         mean.setf(p, sum / lc.size());
      }
      return mean;
   }

   public FloatProcessor getStdDevImg(Image ri, FloatProcessor mean, List<Object> lc) {
      FloatProcessor stdDev = new FloatProcessor(ri.getWidth(), ri.getHeight());
      for (int p = 0; p < mean.getPixelCount(); p++) {
         float sumSqDif = 0.0f;
         for (int i = 0; i < lc.size(); i++) {
            short[] rawPixels = (short[]) lc.get(i);
            sumSqDif += (rawPixels[p] & 0xffff - mean.get(p)
                    * (rawPixels[p] & 0xffff - mean.get(p)));
         }
         stdDev.setf(p, (float) Math.sqrt(sumSqDif) / (lc.size() - 1));
      }
      return stdDev;
   }

}
