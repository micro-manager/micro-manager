///////////////////////////////////////////////////////////////////////////////
//FILE:          PtcToolsExecutor.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nico Stuurman
//
// COPYRIGHT:    University of California, San Francisco, 2018, 2025
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
import ij.gui.Plot;
import ij.measure.ResultsTable;
import ij.plugin.ZProjector;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;
import ij.process.ShortProcessor;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JFrame;
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
import org.micromanager.internal.utils.NumberUtils;
import org.micromanager.internal.utils.ReportingUtils;
import org.micromanager.internal.utils.WindowPositioning;

public class PtcToolsExecutor extends Thread {
   private final Studio studio_;
   private final PropertyMap settings_;
   private final List<ExpMeanStdDev> expMeanStdDev_;
   private ImageStack stack_;

   /**
    * Simple class to hold Avg. Intensity, StdDev of Avg. intensities,
    * and Average of Std. Deviations for a stack of images at identical
    * exposure time.
    * Mean, meanStd_, and meanReadPlusShotNoise can be used to calculate
    * the Photon Conversion Factor,
    * stdDev_ can be used to estimate the stability of the light source.
    */
   private class ExpMeanStdDev {
      public double mean_;
      public double meanStd_; // Std. Dev per image
      public double meanReadPlusShotNoise_;
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
            studio_.logs().logError(ex);
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
         final SummaryMetadata.Builder smb = studio_.data().summaryMetadataBuilder();
         final Coords.Builder cb = Coordinates.builder();
         Coords coords = cb.t(nrFrames).build();
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
            rt.addValue("Noise", cemsd.meanStd_);
            rt.addValue("Read+Shot Noise", cemsd.meanReadPlusShotNoise_);
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

         final CMMCore core = studio_.getCMMCore(); // to reduce typing
         final int nrFrames = settings_.getInteger(PtcToolsTerms.NRFRAMES, 100);
         final ResultsTable rt = ResultsTable.getResultsTable();
         rt.setPrecision(4);

         // establish the exposure times we will use as a logarithmically spaced series
         int nrExposures = settings_.getInteger(PtcToolsTerms.NREXPOSURES, 30);
         double minExposure;
         double maxExposure;
         try {
            minExposure = NumberUtils.displayStringToDouble(
                  settings_.getString(PtcToolsTerms.MINIMUMEXPOSURE, "0.1"));
            maxExposure = NumberUtils.displayStringToDouble(
                  settings_.getString(PtcToolsTerms.MAXIMUMEXPOSURE, "100.0"));
         } catch (ParseException ex) {
            studio_.logs().showError("Minimum exposure should be a number");
            return;
         }
         if (minExposure <= 0.0) {
            minExposure = 0.1;
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
               studio_.logs().logError(ex);
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
               rt.addValue("Noise", cemsd.meanStd_);
               rt.addValue("Read+Shot Noise", cemsd.meanReadPlusShotNoise_);
               rt.addValue("Std.Dev", cemsd.stdDev_);
               store.close();
            } catch (IOException ex) {
               studio_.logs().showError(ex, "Error while calculating mean and stdDev");
               return;
            }
            System.gc();

         }

         rt.show("Results");
         showPtcPlot();
         ij.IJ.showProgress(1.0);
         ImagePlus imp = new ImagePlus("PTCTools stack", stack_);
         imp.setDimensions(2, 1, stack_.getSize() / 2);
         CompositeImage comp = new CompositeImage(imp, CompositeImage.COLOR);
         comp.show();
      }
   }


   private void showDialog(final String label, final PtcSequenceRunner sr) {
      final JFrame dialog = new JFrame();
      dialog.setBounds(settings_.getInteger(PtcToolsTerms.WINDOWX, 100),
            settings_.getInteger(PtcToolsTerms.WINDOWY, 100), 400, 100);
      dialog.setLayout(new MigLayout());
      dialog.setTitle("PTC Tools");
      dialog.add(new JLabel(label), "wrap");
      dialog.add(new JLabel("Press OK when ready"), "wrap");
      dialog.setIconImage(Toolkit.getDefaultToolkit().getImage(
            getClass().getResource("/org/micromanager/icons/microscope.gif")));
      WindowPositioning.setUpBoundsMemory(dialog, dialog.getClass(), null);
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
      final Coords.Builder cb = Coordinates.builder();
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
      final Coords.Builder cb = Coordinates.builder();
      int nrFrames = store.getNextIndex(Coords.T);
      ImageStack tmpStack = new ImageStack(stack.getWidth(), stack.getHeight());
      for (int i = 0; i < nrFrames; i++) {
         tmpStack.addSlice(studio_.data().ij().createProcessor(store.getImage(cb.t(i).build())));
      }
      ZProjector zProj = new ZProjector(new ImagePlus("tmp", tmpStack));
      zProj.setMethod(ZProjector.AVG_METHOD);
      zProj.doProjection();
      ImagePlus meanP = zProj.getProjection();
      FloatProcessor mean = (FloatProcessor) meanP.getProcessor().convertToFloat();
      zProj.setMethod(ZProjector.SD_METHOD);
      zProj.doProjection();
      ImagePlus stdDevP = zProj.getProjection();
      FloatProcessor stdDev = (FloatProcessor) stdDevP.getProcessor().convertToFloat();

      stack.addSlice(mean);
      stack.addSlice(stdDev);
   }


   private ExpMeanStdDev calcExpMeanStdDev(Datastore store) throws IOException {
      ExpMeanStdDev result = new ExpMeanStdDev();
      final Coords.Builder cb = Coordinates.builder();
      final int nrFrames = store.getNextIndex(Coords.T);
      if (nrFrames < 2) {
         ReportingUtils.showError(
                  "Need at least 2 frames to calculate expected mean and standard deviation.");
         return result;
      }
      double[] means = new double[nrFrames - 1];
      double[] stdDevs = new double[nrFrames - 1];
      double[] readPlusShotNoise = new double[nrFrames - 1];
      ImageProcessor firstProc = studio_.data().ij().createProcessor(store.getImage(cb.build()));
      FloatProcessor firstFloat = (FloatProcessor) firstProc.convertToFloat();
      for (int i = 1; i < nrFrames; i++) {
         Image image = store.getImage(cb.t(i).build());
         ImageProcessor proc = studio_.data().ij().createProcessor(image);
         ImageStatistics stats = ImageStatistics.getStatistics(proc,
               ImageStatistics.MEAN, null);
         means[i - 1] = stats.mean;
         stdDevs[i - 1] = stats.stdDev;
         FloatProcessor secondFloat = (FloatProcessor) proc.convertToFloat();
         float[] firstPixels = (float[]) firstFloat.getPixels();
         float[] secondPixels = (float[]) secondFloat.getPixels();
         float[] diffPixels = new float[firstPixels.length];
         for (int j = 0; j < firstPixels.length; j++) {
            diffPixels[j] = secondPixels[j] - firstPixels[j];
         }
         FloatProcessor diffProc = new FloatProcessor(secondFloat.getWidth(),
               secondFloat.getHeight(), diffPixels);
         stats = ImageStatistics.getStatistics(diffProc, ImageStatistics.MEAN, null);
         readPlusShotNoise[i - 1] = stats.stdDev / Math.sqrt(2);

      }
      result.mean_ = avg(means);
      result.meanStd_ = avg(stdDevs);
      result.meanReadPlusShotNoise_ = avg(readPlusShotNoise);
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
      result /= (numbers.length - 1);

      return Math.sqrt(result);
   }

   private void showPtcPlot() {
      // First entry is the dark frame; use its mean as the offset to subtract
      double darkMean = expMeanStdDev_.isEmpty() ? 0.0 : expMeanStdDev_.get(0).mean_;

      // Collect mean and stdDev values, skipping entries where either is <= 0
      // (log-log plot requires positive values); skip the dark frame itself (index 0)
      List<Double> means = new ArrayList<>();
      List<Double> stdDevs = new ArrayList<>();
      for (int i = 1; i < expMeanStdDev_.size(); i++) {
         ExpMeanStdDev emsd = expMeanStdDev_.get(i);
         double correctedMean = emsd.mean_ - darkMean;
         if (correctedMean > 0.0 && emsd.meanReadPlusShotNoise_ > 0.0) {
            means.add(correctedMean);
            stdDevs.add(emsd.meanReadPlusShotNoise_);
         }
      }
      if (means.isEmpty()) {
         return;
      }

      double[] logMeans = new double[means.size()];
      double[] logStdDevs = new double[stdDevs.size()];
      for (int i = 0; i < means.size(); i++) {
         logMeans[i] = Math.log10(means.get(i));
         logStdDevs[i] = Math.log10(stdDevs.get(i));
      }

      // Find the largest contiguous subset of points whose least-squares fitted slope
      // is within tolerance of 0.5 (shot-noise dominated region), searching from high
      // signal downward so that a noisy low-signal region is not selected in preference
      // to the true shot-noise region.
      final int MIN_POINTS = 3;
      final double SLOPE_TOLERANCE = 0.15;
      int bestStart = -1;
      int bestEnd = -1;
      double bestSlopeDiff = Double.MAX_VALUE;
      int nPts = logMeans.length;
      outer:
      for (int end = nPts - 1; end >= MIN_POINTS - 1; end--) {
         for (int start = end - MIN_POINTS + 1; start >= 0; start--) {
            int len = end - start + 1;
            double sumX = 0.0;
            double sumY = 0.0;
            double sumXX = 0.0;
            double sumXY = 0.0;
            for (int k = start; k <= end; k++) {
               sumX += logMeans[k];
               sumY += logStdDevs[k];
               sumXX += logMeans[k] * logMeans[k];
               sumXY += logMeans[k] * logStdDevs[k];
            }
            double denom = len * sumXX - sumX * sumX;
            if (denom == 0.0) {
               continue;
            }
            double slope = (len * sumXY - sumX * sumY) / denom;
            double diff = Math.abs(slope - 0.5);
            if (diff <= SLOPE_TOLERANCE) {
               if (bestStart < 0 || len > (bestEnd - bestStart + 1)
                     || (len == (bestEnd - bestStart + 1) && diff < bestSlopeDiff)) {
                  bestSlopeDiff = diff;
                  bestStart = start;
                  bestEnd = end;
               }
               // Found a qualifying window ending at this 'end'; no need to
               // extend further left for this end point
               break;
            }
         }
         // Once we have found a qualifying region, stop searching lower end points
         if (bestStart >= 0) {
            break outer;
         }
      }

      Plot plot = new Plot("Photon Transfer Curve", "log10(Mean)", "log10(Std.Dev.)");
      plot.addPoints(logMeans, logStdDevs, Plot.LINE);
      plot.addPoints(logMeans, logStdDevs, Plot.CIRCLE);

      String description = settings_.getString(PtcToolsTerms.DESCRIPTION, "");
      String descriptionPrefix = description.isEmpty() ? "" : description + "\n";

      if (bestStart < 0 || bestSlopeDiff > 0.15) {
         plot.addLabel(0.05, 0.15, descriptionPrefix
                  + "No shot-noise region found (slope \u2248 0.5).\n" // degree
                  + "Try a wider exposure range.");
      } else {
         final int n = bestEnd - bestStart + 1;
         double sumX = 0.0;
         double sumY = 0.0;
         double sumXX = 0.0;
         double sumXY = 0.0;
         for (int k = bestStart; k <= bestEnd; k++) {
            sumX += logMeans[k];
            sumY += logStdDevs[k];
            sumXX += logMeans[k] * logMeans[k];
            sumXY += logMeans[k] * logStdDevs[k];
         }
         final double slope = (n * sumXY - sumX * sumY) / (n * sumXX - sumX * sumX);

         // Gain: with slope fixed at 0.5: log(stdDev) = 0.5*log(mean) + b
         // => stdDev^2 = mean / g  =>  g = 10^(-2*b)
         double interceptFixed = (sumY - 0.5 * sumX) / n;
         final double gain = Math.pow(10.0, -2.0 * interceptFixed);

         // Extend the fitted line leftward until it reaches the minimum noise level
         // (i.e. the lowest logStdDev in the data), and rightward to the last selected point
         double minLogStdDev = Double.MAX_VALUE;
         for (double v : logStdDevs) {
            if (v < minLogStdDev) {
               minLogStdDev = v;
            }
         }
         // From line equation: logStdDev = 0.5 * logMean + interceptFixed
         // => logMean = (logStdDev - interceptFixed) / 0.5
         double xFitMin = (minLogStdDev - interceptFixed) / 0.5;
         double xFitMax = logMeans[bestEnd];
         double[] fitX = {xFitMin, xFitMax};
         double[] fitY = {minLogStdDev, 0.5 * xFitMax + interceptFixed};
         plot.setColor(java.awt.Color.RED);
         plot.addPoints(fitX, fitY, Plot.LINE);
         plot.setColor(java.awt.Color.BLACK);

         double darkStdDev = expMeanStdDev_.get(0).meanReadPlusShotNoise_;
         double readNoise = darkStdDev * gain;

         // Draw a horizontal green line at the dark-frame noise level (read noise in ADU)
         double logDarkStdDev = Math.log10(darkStdDev);
         double[] rnX = {logMeans[0], logMeans[nPts - 1]};
         double[] rnY = {logDarkStdDev, logDarkStdDev};
         plot.setColor(java.awt.Color.GREEN);
         plot.addPoints(rnX, rnY, Plot.LINE);
         plot.setColor(java.awt.Color.BLACK);

         plot.addLabel(0.05, 0.15,
               String.format("%sShot-noise fit slope: %.3f\n"
                        + "Gain (mean/\u03C3\u00B2): %.4f electron/ADU\n" // sigma squared
                        + "Read noise: %.2f electrons",
                     descriptionPrefix, slope, gain, readNoise));
      }

      plot.show();
   }


}
