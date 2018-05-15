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

import ij.CompositeImage;
import ij.ImagePlus;
import ij.ImageStack;
import ij.measure.ResultsTable;
import ij.plugin.ZProjector;
import ij.process.FloatProcessor;
import ij.process.ShortProcessor;
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
      
      CMMCore core = studio_.getCMMCore(); // to reduce typing
      final int nrFrames = settings_.getInteger(PtcToolsTerms.NRFRAMES, 100);
        
      
      boolean dr = ij.IJ.showMessageWithCancel("PTC Tools", "Prevent all light going to the"
              + " camera.  Press OK when ready");
      if (!dr) {
         return;
      }
      
      // Stack that holds the resulting images
      ImageStack stack = new ImageStack((int) core.getImageWidth(), 
              (int) core.getImageHeight());
      
      // temporary store to hold images while calculating mean and stdDev
      Datastore store = studio_.data().createRAMDatastore();
      final SummaryMetadata.Builder smb = studio_.data().getSummaryMetadataBuilder();
      final Coords.Builder cb = Coordinates.builder();
      Coords coords = cb.c(1).p(1).
              t(nrFrames).z(1).build();
      try {
         store.setSummaryMetadata(smb.intendedDimensions(coords).startDate(
                 new SimpleDateFormat("yyyy.MM.dd.HH.mm.ss").format(new Date())).build());
      } catch (IOException ex) {
         // should never happen with a RAMDatastore...
      }
      
      double exposure;
      try {
         exposure = NumberUtils.displayStringToDouble(
                 settings_.getString(PtcToolsTerms.MINIMUMEXPOSURE, "0.1"));
      } catch (ParseException ex) {
         studio_.logs().showError("Minimum exposure should be a number");
         return;
      }
      
      try {
         runSequence(core, store, nrFrames, exposure);
      } catch (Exception ex) {
         studio_.logs().showError(ex, "Error while acquiring images");
         return;
      }
           
      // TODO: make sure that we have 16-bit (short) images
      try {
         calculateAndAddToStack(stack, store);
         store.freeze();
         store.close();
      } catch (IOException ex) {
         studio_.logs().showError(ex, "Error while calculating mean and stdDev");
         return;
      }

      
      dr = ij.IJ.showMessageWithCancel("PTC Tools", "Now switch on the light, and make sure it can reach the"
              + " camera.  Press OK when ready");
      if (!dr) {
         return;
      }
      
      // establish the exposure times we will use as a logarthimically spaced series
      int nrExposures = settings_.getInteger(PtcToolsTerms.NREXPOSURES, 30);
      double minExposure, maxExposure;
      try {
         minExposure = NumberUtils.displayStringToDouble(
                 settings_.getString(PtcToolsTerms.MINIMUMEXPOSURE, "0.1"));
         maxExposure = NumberUtils.displayStringToDouble(
                 settings_.getString(PtcToolsTerms.MAXIMUMEXPOSURE, "100.0"));
      } catch (ParseException ex) {
         studio_.logs().showError("Minimum exposure should be a number");
         return;
      }
      double[] exposures = new double[nrExposures];
      double minExpLog = Math.log(minExposure);
      double maxExpLog = Math.log(maxExposure);
      double expLogStep = (maxExpLog - minExpLog) / (nrExposures -1);
      ResultsTable rt = ResultsTable.getResultsTable();
      rt.setPrecision(4);    
      
      for (int i = 0; i < nrExposures; i++) {
         ij.IJ.showStatus("PTCTools, working on exposure: " + i);
         ij.IJ.showProgress(i, nrExposures);
         exposures[i] = Math.exp(minExpLog + i * expLogStep);
         rt.incrementCounter(); 
         rt.addValue("Exposure", exposures[i]);
         store = studio_.data().createRAMDatastore();
         try {
            runSequence(core, store, nrFrames, exposures[i]);
         } catch (Exception ex) {
            studio_.logs().showError(ex, "Error while acquiring images");
            return;
         }

         // TODO: make sure that we have 16-bit (short) images
         try {
            calculateAndAddToStack(stack, store);
            store.close();
         } catch (IOException ex) {
            studio_.logs().showError(ex, "Error while calculating mean and stdDev");
            return;
         }
         System.gc();

      }
      
      rt.show("Results");
      ij.IJ.showProgress(1.0);
      ImagePlus imp = new ImagePlus("PTCTools stack", stack);
      imp.setDimensions(2, 1, stack.getSize() / 2);
      CompositeImage comp = new CompositeImage(imp, CompositeImage.COLOR);
      comp.show();

   }
   

   private void runSequence(CMMCore core, Datastore store, int nrFrames,
           double exposure) throws Exception {
      final Coords.Builder cb = Coordinates.builder().c(1).p(1).t(1).z(1);
      core.setExposure(exposure);
      core.startSequenceAcquisition(nrFrames, 0.0, true);
      int frCounter = 0;
      // TODO: this can hang
      while (core.isSequenceRunning() || core.getRemainingImageCount() > 0) {
         if (core.getRemainingImageCount() > 0) {
            TaggedImage nextImage = core.popNextTaggedImage();
            if (nextImage != null) {
               Image img = studio_.data().convertTaggedImage(nextImage);
               store.putImage(img.copyAtCoords(cb.t(frCounter).build()));
               frCounter++;
            }
         }
      }
      store.freeze();
   }
   
   private void calculateAndAddToStack(ImageStack stack, Datastore store) 
            throws IOException, OutOfMemoryError {
      final Coords.Builder cb = Coordinates.builder().c(1).p(1).t(1).z(1);
      int nrFrames = store.getAxisLength(Coords.T);
      ImageStack tmpStack = new ImageStack(stack.getWidth(), stack.getHeight());
      List<ShortProcessor> lc = new ArrayList<ShortProcessor>(nrFrames);
         for (int i = 0; i < nrFrames; i++) {
            ShortProcessor tmpShortProc = new ShortProcessor(stack.getWidth(), 
                    stack.getHeight());
            tmpShortProc.setPixels(store.getImage(cb.t(i).build()).getRawPixels());
            tmpStack.addSlice(tmpShortProc);
         }
         ZProjector zProj = new ZProjector(new ImagePlus("tmp", tmpStack));
         zProj.setMethod(ZProjector.AVG_METHOD);
         zProj.doProjection();
         ImagePlus  meanP = zProj.getProjection();
         FloatProcessor mean = (FloatProcessor) meanP.getProcessor().convertToFloat();
         zProj.setMethod(ZProjector.SD_METHOD);
         zProj.doProjection();
         ImagePlus  stdDevP = zProj.getProjection();
         FloatProcessor stdDev = (FloatProcessor) stdDevP.getProcessor().convertToFloat();
         
         stack.addSlice(mean);
         stack.addSlice(stdDev);
   }
   
  
}
