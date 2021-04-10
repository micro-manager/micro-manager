/**
 * Implementation for the "Fit All" button
 * Fits all spots in the selected stack
 * 
 * Part of Micro-Manager's Localization Plugin
 * 
 * Author: Nico Stuurman, 
 * 
 * 
Copyright (c) 2012-2017, Regents of the University of California
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
   list of conditions and the following disclaimer.
2. Redistributions in binary form must reproduce the above copyright notice,
   this list of conditions and the following disclaimer in the documentation
   and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

The views and conclusions contained in the software and documentation are those
of the authors and should not be interpreted as representing official policies,
either expressed or implied, of the FreeBSD Project.
 */


package edu.ucsf.valelab.gaussianfit.fitmanagement;

import edu.ucsf.valelab.gaussianfit.DataCollectionForm;
import edu.ucsf.valelab.gaussianfit.algorithm.FindLocalMaxima;
import edu.ucsf.valelab.gaussianfit.utils.ProgressThread;
import edu.ucsf.valelab.gaussianfit.data.GaussianInfo;
import edu.ucsf.valelab.gaussianfit.data.RowData;
import edu.ucsf.valelab.gaussianfit.data.SpotData;
import edu.ucsf.valelab.gaussianfit.fitting.ZCalibrator;
import ij.IJ;
import ij.ImagePlus;
import ij.gui.Roi;
import ij.plugin.frame.RoiManager;
import ij.process.ImageProcessor;
import java.awt.Polygon;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.concurrent.LinkedBlockingQueue;
import edu.ucsf.valelab.gaussianfit.utils.ReportingUtils;
import ij.ImageStack;
import ij.plugin.HyperStackConverter;
import ij.process.ShortProcessor;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import org.micromanager.Studio;
import org.micromanager.data.Coords;
import org.micromanager.data.Coords.CoordsBuilder;
import org.micromanager.data.Image;
import org.micromanager.display.DisplayWindow;

/**
 *
 * @author nico
 */
public class FitAllThread extends GaussianInfo implements Runnable  {
   double[] params0_;
   double[] steps_ = new double[5];
   GaussianFitStackThread[] gfsThreads_;
   private volatile Thread t_ = null;
   private static boolean running_ = false;
   private final FindLocalMaxima.FilterType preFilterType_;
   private final String positionString_;
   private boolean showDataWindow_ = true;
   private final Studio studio_;
   private final ExecutorService threadPool_;
   private final int nrThreads_;

   public FitAllThread(Studio studio, 
           int nrThreads,
           ExecutorService threadPool,
           FindLocalMaxima.FilterType preFilterType, 
           String positions) {
      studio_ = studio;
      preFilterType_ = preFilterType;
      positionString_ = positions;
      nrThreads_ = nrThreads;
      threadPool_ = threadPool;
   }

   
   public synchronized void  init() {
      if (running_)
         return;
      t_ = new Thread(this);
      running_ = true;
      t_.start();
   }
   
   public synchronized void join(long millis) throws InterruptedException {
      if (!running_) {
         return;
      }
      t_.join(millis);
   } 

   public synchronized void stop() {
      if (gfsThreads_ != null) {
         for (GaussianFitStackThread gfsThread : gfsThreads_) {
            if (gfsThread != null) {
               gfsThread.stop();
            }
         }
      }
      t_ = null;
      running_ = false;
   }

   public boolean isRunning() {
      return running_;
   }

   public synchronized List<SpotData> getResults() {
      return resultList_;
   }
   
   public synchronized void showDataWindow(boolean flag) {
      showDataWindow_ = flag;
   }
   
   @Override
   public void run() {

      // List with spot positions found through the Find Maxima command
      sourceList_ = new LinkedBlockingQueue<SpotData>();
      resultList_ = Collections.synchronizedList(new ArrayList<SpotData>());

      // take the active ImageJ image
      ImagePlus siPlus;
      Roi originalRoi = null;
      try {
         siPlus = IJ.getImage();
         originalRoi = siPlus.getRoi();
      } catch (Exception ex) {
         stop();
         return;
      }
      
      DisplayWindow dw = studio_.displays().getCurrentWindow();

      long startTime = System.nanoTime();
      int nrPositions = 1;
      int nrChannels = siPlus.getNChannels();
      int nrFrames = siPlus.getNFrames();
      int nrSlices = siPlus.getNSlices();
      // int maxNrSpots = 0;

      // If we have a Micro-Manager window:
      if (dw != null) {

         String[] parts = positionString_.split("-");
         nrPositions = dw.getDataProvider().getNextIndex(Coords.STAGE_POSITION);
         nrChannels = dw.getDataProvider().getNextIndex(Coords.CHANNEL);
         nrFrames = dw.getDataProvider().getNextIndex(Coords.T);
         nrSlices = dw.getDataProvider().getNextIndex(Coords.Z);
         int startPos = 1; int endPos = 1;
         if (parts.length > 0) {
            startPos = Integer.parseInt(parts[0]);
         }
         if (parts.length > 1) {
            endPos = Integer.parseInt(parts[1]);
         }
         if (endPos > nrPositions) {
            endPos = nrPositions;
         }
         if (endPos < startPos) {
            endPos = startPos;
         }

         try {
            CoordsBuilder builder = dw.getDisplayedImages().get(0).getCoords().copy();
            for (int p = startPos - 1; p <= endPos - 1; p++) {

               Image image = dw.getDataProvider().getImage(builder.stagePosition(p).build());
               int width = image.getWidth();
               int height = image.getHeight();
               ImageStack stack = new ImageStack(width, height);
               for (int f = 0; f < nrFrames; f++) {
                  for (int z = 0; z < nrSlices; z++) {
                     for (int c = 0; c < nrChannels; c++) {
                        image = dw.getDataProvider().getImage(builder.stagePosition(p).
                                channel(c).time(f).z(z).build());
                        ImageProcessor iProcessor;
                        if (image != null) {
                           iProcessor = studio_.data().ij().createProcessor(image);
                        } else {
                           iProcessor = new ShortProcessor(width, height);
                        }
                        stack.addSlice(iProcessor);
                     }
                  }
               }

               ImagePlus tmpSP = (new ImagePlus("tmp", stack)).duplicate();
               tmpSP = HyperStackConverter.toHyperStack(tmpSP, nrChannels,
                       nrSlices, nrFrames);
               //tmpSP.show();

               siPlus.deleteRoi();

               analyzeImagePlus(tmpSP, p + 1, originalRoi);

               siPlus.setRoi(originalRoi);
            }

         } catch (IOException ioe) {
            ReportingUtils.logError(ioe, "In Gaussian plugin");
         }
      } else {
         analyzeImagePlus(siPlus, 1, originalRoi);
      }

      long endTime = System.nanoTime();

      // Add data to data overview window
      if (resultList_.size() < 1) {
         ReportingUtils.showError("No spots found");
         running_ = false;
         return;
      }
      
      DataCollectionForm dcForm = DataCollectionForm.getInstance();

      double zMax = resultList_.get(0).getZCenter();
      if (zMax < 0.0) {
         zMax = 0.0;
      }
      double zMin = zMax;
      ZCalibrator zc = DataCollectionForm.zc_;
      if (zc != null) {
         for (SpotData spot : resultList_) {
            double zTmp = spot.getZCenter();
            if (zMax < zTmp) {
               zMax = zTmp;
            }
            if (zMin > zTmp && zTmp > 0.0) {
               zMin = zTmp;
            }
         }
      }


      ArrayList<Double> timePoints = new ArrayList<Double>();
      // ugly code to deal with 1-based frame numbers and their relation to timePoints
      timePoints.add(0.0);
      for (int i = 1; i <= nrFrames; i++) {
         timePoints.add((i - 1) * timeIntervalMs_);
      }

      String title = siPlus.getTitle();
      if (nrPositions > 1) {
         title += "_Pos" + positionString_;
      }
      
      RowData.Builder builder = new RowData.Builder();
      builder.setName(title).setTitle(siPlus.getTitle()).setDisplayWindow(dw).
              setWidth(siPlus.getWidth()).setHeight(siPlus.getHeight()).
              setPixelSizeNm(pixelSize_).setZStackStepSizeNm(zStackStepSize_).
              setShape(super.getShape()).setHalfSize(super.getHalfBoxSize()).
              setNrChannels(nrChannels).setNrFrames(nrFrames).
              setNrSlices(nrSlices).setNrPositions(nrPositions).
              setMaxNrSpots(resultList_.size()).setSpotList(resultList_).
              setTimePoints(timePoints).setIsTrack(false).
              setCoordinate(DataCollectionForm.Coordinates.NM).
              setHasZ(DataCollectionForm.zc_.hasFitFunctions()).
              setMinZ(zMin).setMaxZ(zMax);
      dcForm.addSpotData(builder);

      if (showDataWindow_) {
         dcForm.setVisible(true);
      }

      // report duration of analysis
      double took = (endTime - startTime) / 1E9;
      double rate = resultList_.size() / took;
      DecimalFormat df2 = new DecimalFormat("#.##");
      DecimalFormat df0 = new DecimalFormat("#");
      studio_.alerts().postAlert("Spot analysis results", FitAllThread.class, 
              "Analyzed " + resultList_.size() + " spots in " + df2.format(took)
              + " seconds (" + df0.format(rate) + " spots/sec.)");

      running_ = false;
   }

   @SuppressWarnings("unchecked")
   private int analyzeImagePlus(ImagePlus siPlus, int position, Roi originalRoi) {
      int halfSize = super.getHalfBoxSize();
      int nrSpots = 0;
      // Start up IJ.Prefs.getThreads() threads for gaussian fitting
      gfsThreads_ = new GaussianFitStackThread[nrThreads_];
      Future<?>[] futures = new Future<?>[nrThreads_];
      for (int i = 0; i < nrThreads_; i++) {
         gfsThreads_[i] = new GaussianFitStackThread(sourceList_, resultList_, siPlus);
         gfsThreads_[i].copy(this);
         futures[i] = threadPool_.submit(gfsThreads_[i]);
      }
      int shownChannel = siPlus.getChannel();
      int shownSlice = siPlus.getSlice();
      int shownFrame = siPlus.getFrame();
      
      // work around strange bug that happens with freshly opened images
      for (int i = 1; i <= siPlus.getNChannels(); i++) {
         siPlus.setPosition(i, siPlus.getCurrentSlice(), siPlus.getFrame());
      }
      
      int nrChannels = siPlus.getNChannels();
      if (skipChannels_) {
         nrChannels -= channelsToSkip_.length;
      }
      int nrImages = nrChannels * siPlus.getNSlices() * siPlus.getNFrames();
      int imageCount = 0;
      try {
         for (int c = 1; c <= siPlus.getNChannels(); c++) {
            if (!running_) {
               break;
            }
            if (!skipChannels_ || !inArray(channelsToSkip_, c)) {
               for (int z = 1; z <= siPlus.getNSlices(); z++) {
                  if (!running_) {
                     break;
                  }
                  for (int f = 1; f <= siPlus.getNFrames(); f++) {
                     if (!running_) {
                        break;
                     }
                     // to avoid making a gigantic sourceList and running out of memory
                     // sleep a bit when the sourcesize gets too big
                     // once we have very fast multi-core computers, this constant can be increased
                     if (sourceList_.size() > 100000) {
                        try {
                           Thread.sleep(1000);
                        } catch (InterruptedException ex) {
                           // not sure what to do
                        }
                     }

                     imageCount++;
                     ij.IJ.showStatus("Processing image " + imageCount);

                     ImageProcessor siProc = null;
                     Polygon p = new Polygon();
                     synchronized (SpotData.LOCK_IP) {
                        siPlus.setPositionWithoutUpdate(c, z, f);
                        // If ROI manager is used, use RoiManager Rois
                        //  may be dangerous if the user is not aware
                        RoiManager roiM = RoiManager.getInstance();
                        Roi[] rois = null;
                        //if (roiM != null && roiM.getSelectedIndex() > -1 ) {
                        //   rois = roiM.getSelectedRoisAsArray();
                        //}
                        if (rois != null && rois.length > 0) {
                           for (Roi roi : rois) {
                              siPlus.setRoi(roi, false);
                              siProc = siPlus.getProcessor();
                              Polygon q = FindLocalMaxima.FindMax(siPlus, 
                                      2 * super.getHalfBoxSize(), noiseTolerance_,
                                      preFilterType_);
                              for (int i = 0; i < q.npoints; i++) {
                                 p.addPoint(q.xpoints[i], q.ypoints[i]);
                              }
                           }
                        } else {  // no Rois in RoiManager
                           siPlus.setRoi(originalRoi, false);
                           siProc = siPlus.getProcessor();
                           p = FindLocalMaxima.FindMax(siPlus, 2 * super.getHalfBoxSize(), noiseTolerance_,
                                   preFilterType_);
                        }
                     }

                     ij.IJ.showProgress(imageCount, nrImages);

                     if (p.npoints > nrSpots) {
                        nrSpots = p.npoints;
                     }
                     int[][] sC = new int[p.npoints][2];
                     for (int j = 0; j < p.npoints; j++) {
                        sC[j][0] = p.xpoints[j];
                        sC[j][1] = p.ypoints[j];
                     }

                     Arrays.sort(sC, new SpotSortComparator());

                     for (int j = 0; j < sC.length; j++) {
                        // filter out spots too close to the edge
                        if (sC[j][0] > halfSize && sC[j][0] < siPlus.getWidth() - halfSize
                                && sC[j][1] > halfSize && sC[j][1] < siPlus.getHeight() - halfSize) {
                           ImageProcessor sp = SpotData.getSpotProcessor(siProc,
                                   halfSize, sC[j][0], sC[j][1]);
                           if (sp == null) {
                              continue;
                           }
                           int channel = c;

                           SpotData thisSpot = new SpotData(sp, channel, z, f,
                                   position, j, sC[j][0], sC[j][1]);
                           try {
                              sourceList_.put(thisSpot);
                           } catch (InterruptedException iex) {
                              Thread.currentThread().interrupt();
                              throw new RuntimeException("Unexpected interruption");
                           }
                        }
                     }
                  }
               }
            }
         }
         // start ProgresBar thread
         ProgressThread pt = new ProgressThread(sourceList_);
         pt.init();


      } catch (OutOfMemoryError ome) {
         ij.IJ.error("Out Of Memory");
      }

      // Send working threads signal that we are done:
      SpotData lastSpot = new SpotData(null, -1, 1, -1, -1, -1, -1, -1);
      try {
         sourceList_.put(lastSpot);
      } catch (InterruptedException iex) {
         Thread.currentThread().interrupt();
         throw new RuntimeException("Unexpected interruption");
      }

      // wait for worker threads to finish
      for (int i=0; i<nrThreads_; i++) {
         try {
            futures[i].get();
            gfsThreads_[i] = null;
         } catch (ExecutionException | InterruptedException ie) {
         }
      }

      siPlus.setPosition(shownChannel, shownSlice, shownFrame);
       
      sourceList_.clear();
      return nrSpots;
   }
   

   private class SpotSortComparator implements Comparator {

      // Return the result of comparing the two row arrays
      @Override
      public int compare(Object o1, Object o2) {
         int[] p1 = (int[]) o1;
         int[] p2 = (int[]) o2;
         if (p1[0] < p2[0]) {
            return -1;
         }
         if (p1[0] > p2[0]) {
            return 1;
         }
         if (p1[0] == p2[0]) {
            if (p1[1] < p2[1]) {
               return -1;
            }
            if (p1[1] > p2[1]) {
               return 1;
            }
         }
         return 0;
      }
   }


   private static boolean inArray(int[] input, final int c) {
      for (final int n : input) {
         if (n == c) {
            return true;
         }
      }
      return false;
   }
   


}
