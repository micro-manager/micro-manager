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
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;
import ij.process.ShortProcessor;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import mmcorej.CMMCore;
import mmcorej.TaggedImage;
import net.miginfocom.swing.MigLayout;
import org.micromanager.PropertyMap;
import org.micromanager.Studio;
import org.micromanager.data.Coordinates;
import org.micromanager.data.Coords;
import org.micromanager.data.Datastore;
import org.micromanager.data.Image;
import org.micromanager.data.SummaryMetadata;

// Imports for MMStudio internal packages
// Plugins should not access internal packages, to ensure modularity and
// maintainability. However, this plugin code is older than the current
// MMStudio API, so it still uses internal classes and interfaces. New code
// should not imitate this practice.
import org.micromanager.internal.utils.MMFrame;
import org.micromanager.internal.utils.NumberUtils;
import org.micromanager.internal.utils.ReportingUtils;

public class PtcToolsExecutor extends Thread  {
   private final Studio studio_;
   private final PropertyMap settings_;
   private final List<ExpMeanStdDev> expMeanStdDev_;
   private ImageStack stack_;
   
   /**
    * Simple class to hold Avg. Intensity and StdDev of Avg. intensities
    * for a stack of images at identical exposure time.  Used to
    * estimate stability of light source
    */
   private class ExpMeanStdDev {
      public double mean_;
      public double stdDev_;
   }
   
   public PtcToolsExecutor(Studio studio, PropertyMap settings) {
      studio_ = studio;
      settings_ = settings;
      expMeanStdDev_ = new ArrayList<>();
   }

   @Override
   public void run() {
      PtcSequenceRunner sr = new DarkSequence();
      showDialog("Prevent all ligt going to the camera.", sr);
         
   }
     
   private class DarkSequence implements PtcSequenceRunner {

      @Override
      public void doSequence(JLabel resultLabel) {

         try {
            SwingUtilities.invokeAndWait(() -> {
               resultLabel.setText("Collecting dark images...");
            });
         } catch (InterruptedException | InvocationTargetException ex) {
         }

         CMMCore core = studio_.getCMMCore(); // to reduce typing
         final int nrFrames = settings_.getInteger(PtcToolsTerms.NRFRAMES, 100);
         final ResultsTable rt = ResultsTable.getResultsTable();
         rt.setPrecision(4);

         // Stack that holds the resulting images
         stack_ = new ImageStack((int) core.getImageWidth(),
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
            calculateAndAddToStack(stack_, store);
            ExpMeanStdDev cemsd = calcExpMeanStdDev(store);
            expMeanStdDev_.add(cemsd);
            rt.incrementCounter();
            rt.addValue("Exposure", 0.0);
            rt.addValue("Mean", cemsd.mean_);
            rt.addValue("Std.Dev", cemsd.stdDev_);

            store.freeze();
            store.close();
         } catch (IOException ex) {
            studio_.logs().showError(ex, "Error while calculating mean and stdDev");
            return;
         }

         PtcSequenceRunner sr = new LightSequence();
         showDialog("Now switch on the light, and make sure it can reach the"
                 + " camera.", sr);
      }
   }

   private class LightSequence implements PtcSequenceRunner {

      @Override
      public void doSequence(JLabel resultLabel) {

         CMMCore core = studio_.getCMMCore(); // to reduce typing
         final int nrFrames = settings_.getInteger(PtcToolsTerms.NRFRAMES, 100);
         final ResultsTable rt = ResultsTable.getResultsTable();
         rt.setPrecision(4);

         // establish the exposure times we will use as a logarithmically spaced series
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
         double expLogStep = (maxExpLog - minExpLog) / (nrExposures - 1);

         for (int i = 0; i < nrExposures; i++) {
            final int nr = i;
            try {
               SwingUtilities.invokeAndWait(() -> {
                  resultLabel.setText("working on exposure: " + nr + "(of " + nrExposures + ")");
               });
            } catch (InterruptedException | InvocationTargetException ex) {
            }
            
            exposures[i] = Math.exp(minExpLog + i * expLogStep);

            Datastore store = studio_.data().createRAMDatastore();
            try {
               runSequence(core, store, nrFrames, exposures[i]);
            } catch (Exception ex) {
               studio_.logs().showError(ex, "Error while acquiring images");
               return;
            }

            // TODO: make sure that we have 16-bit (short) images
            try {
               calculateAndAddToStack(stack_, store);
               ExpMeanStdDev cemsd = calcExpMeanStdDev(store);
               double realExposure;
               try {
                  realExposure = core.getExposure();
               } catch (Exception e) {
                  ReportingUtils.showError(e);
                  return;
               }
               expMeanStdDev_.add(cemsd);
               rt.incrementCounter();
               rt.addValue("Exposure", realExposure);
               rt.addValue("Mean", cemsd.mean_);
               rt.addValue("Std.Dev", cemsd.stdDev_);
               store.close();
            } catch (IOException ex) {
               studio_.logs().showError(ex, "Error while calculating mean and stdDev");
               return;
            }
            System.gc();

         }

         rt.show("Results");
         ij.IJ.showProgress(1.0);
         ImagePlus imp = new ImagePlus("PTCTools stack", stack_);
         imp.setDimensions(2, 1, stack_.getSize() / 2);
         CompositeImage comp = new CompositeImage(imp, CompositeImage.COLOR);
         comp.show();
      }
   }
   
   
   private void showDialog(final String label, final PtcSequenceRunner sr) {
      final MMFrame dialog = new MMFrame();
      dialog.setBounds(settings_.getInteger(PtcToolsTerms.WINDOWX, 100), 
              settings_.getInteger(PtcToolsTerms.WINDOWY, 100), 400, 100);
      dialog.addComponentListener(new ComponentAdapter() {
         @Override
         public void componentMoved(ComponentEvent e) {
            dialog.savePosition();
         }
      });
      dialog.setLayout(new MigLayout());
      dialog.setTitle("PTC Tools");
      dialog.add(new JLabel(label), "wrap");
      dialog.add(new JLabel("Press OK when ready"), "wrap");
      JButton cancelButton = new JButton("Cancel");
      final JLabel resultLabel = new JLabel("Not started yet...");
      cancelButton.addActionListener((ActionEvent e) -> {
         dialog.dispose();
      });
      JButton okButton = new JButton("OK");
      okButton.addActionListener((ActionEvent e) -> {
         Thread t = new Thread(() -> {
            sr.doSequence(resultLabel);
            try {
               SwingUtilities.invokeAndWait(() -> {
                  dialog.dispose();
               });
            } catch (InterruptedException | InvocationTargetException ex) {
               // Logger.getLogger(PtcToolsExecutor.class.getName()).log(Level.SEVERE, null, ex);
            }
         });
         t.start();
      });
      dialog.add(cancelButton, "split 2, tag cancel");
      dialog.add(okButton, "tag ok, wrap");
      dialog.add(resultLabel);
      dialog.pack();
      dialog.setVisible(true);
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
      List<ShortProcessor> lc = new ArrayList<>(nrFrames);
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
   
   
   private ExpMeanStdDev calcExpMeanStdDev(Datastore store) throws IOException {
      ExpMeanStdDev result = new ExpMeanStdDev();
      final Coords.Builder cb = Coordinates.builder().c(1).p(1).t(1).z(1);
      final int nrFrames = store.getAxisLength(Coords.T);
      double[] means = new double[nrFrames];
      for (int i = 0; i < nrFrames; i++) {
         Image image = store.getImage(cb.t(i).build());
         ImageProcessor proc = studio_.data().ij().createProcessor(image);
         ImageStatistics stats = ImageStatistics.getStatistics(proc,
              ImageStatistics.MEAN, null);
         means[i] = stats.mean;
      }
      result.mean_ = avg(means);
      result.stdDev_ = stdDev(means, result.mean_);
      
      return result;
   }
   
   public static double avg(double[] numbers) {
      double sum = 0.0;
      for (double num : numbers) {
         sum += num;
      }
      return sum / numbers.length;
   }
   
      
   public static double stdDev(double[] numbers, double avg) {
      double result = 0.0;
      for (double val : numbers) {
         result += (val - avg) * (val - avg);
      }
      if (numbers.length < 2) {
         return 0.0;
      }
      result /= (numbers.length -1);
      
      return Math.sqrt(result);
   }
   
  
}
