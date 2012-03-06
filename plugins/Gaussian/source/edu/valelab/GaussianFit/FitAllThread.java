/**
 * Implementation for the "Fit All" button
 * Fits all spots in the selected stack
 */

package edu.valelab.GaussianFit;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.Roi;
import ij.process.ImageProcessor;
import java.awt.Polygon;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.micromanager.api.MMWindow;
import org.micromanager.utils.MMScriptException;

/**
 *
 * @author nico
 */
public class FitAllThread extends GaussianInfo implements Runnable  {
   double[] params0_;
   double[] steps_ = new double[5];
   GaussianFitStackThread[] gfsThreads_;
   private volatile Thread t_;
   private static boolean running_ = false;
   private FindLocalMaxima.FilterType preFilterType_;

   public FitAllThread(int shape, int fitMode, FindLocalMaxima.FilterType preFilterType) {
      shape_ = shape;
      fitMode_ = fitMode;
      preFilterType_ = preFilterType;
   }

   public synchronized void  init() {
      if (running_)
         return;
      t_ = new Thread(this);
      running_ = true;
      t_.start();
   }

   public synchronized void stop() {
      if (gfsThreads_ != null) {
         for (int i=0; i<gfsThreads_.length; i++) {
            gfsThreads_[i].stop();
         }
      }
      running_ = false;
   }

   public boolean isRunning() {
      return t_ != null;
   }

   public void run() {

      // List with spot positions found through the Find Maxima command
      sourceList_ = new LinkedBlockingQueue<GaussianSpotData>();
      resultList_ = Collections.synchronizedList(new ArrayList<GaussianSpotData>());

      // take the active ImageJ image
      ImagePlus siPlus = null;
      try {
         siPlus = IJ.getImage();
      } catch (Exception ex) {
         return;
      }

      ImagePlus imp = siPlus;

      //siPlus = (ImagePlus) siPlus.clone();

      int nrThreads = ij.Prefs.getThreads();
         if (nrThreads > 8)
            nrThreads = 8;

      Roi originalRoi = siPlus.getRoi();

      long startTime = System.nanoTime();
      int nrPositions = 1;
      int nrChannels = siPlus.getNChannels();
      int nrFrames = siPlus.getNFrames();
      int nrSlices = siPlus.getNSlices();
      int maxNrSpots = 0;
      //double elapsedTimeMs0 = 0.0;
      //ArrayList<Double> timePoints = new ArrayList<Double>();


      try {
         Class mmw = Class.forName("org.micromanager.api.MMWindow");        
         MMWindow mw = new MMWindow(siPlus);

         if (!mw.isMMWindow()) {
            int nrSpots = analyzeImagePlus(siPlus, 1, nrThreads, originalRoi);
            if (nrSpots > maxNrSpots)
               maxNrSpots = nrSpots;
         } else { // MMImageWindow
            nrPositions = mw.getNumberOfPositions();
            for (int p = 1; p <= nrPositions; p++) {
               try {
                  mw.setPosition(p);
                  int nrSpots = analyzeImagePlus(siPlus, p, nrThreads, originalRoi);
                  if (nrSpots > maxNrSpots)
                     maxNrSpots = nrSpots;
               } catch (MMScriptException ex) {
                  Logger.getLogger(FitAllThread.class.getName()).log(Level.SEVERE, null, ex);
               }
            }
         }
      } catch (ClassNotFoundException ex) {
         int nrSpots = analyzeImagePlus(siPlus, 1, nrThreads, originalRoi);
         if (nrSpots > maxNrSpots)
            maxNrSpots = nrSpots;
      }


      long endTime = System.nanoTime();

      // Add data to data overview window
      DataCollectionForm dcForm = DataCollectionForm.getInstance();
      dcForm.addSpotData(siPlus.getWindow().getTitle(), siPlus.getTitle(), 
              siPlus.getWidth(), siPlus.getHeight(), (float) pixelSize_,
              shape_, halfSize_,
              nrChannels, nrFrames, nrSlices, nrPositions, resultList_.size(), 
              resultList_, null, false);
      dcForm.setVisible(true);

      // report duration of analysis
		double took = (endTime - startTime) / 1E6;
      print ("Analyzed " + resultList_.size() + " spots in " + took + " milliseconds");

      running_ = false;
      t_ = null;
   }


   private int analyzeImagePlus(ImagePlus siPlus, int position, int nrThreads, Roi originalRoi) {

      int nrSpots = 0;
      // Start up IJ.Prefs.getThreads() threads for gaussian fitting
      gfsThreads_ = new GaussianFitStackThread[nrThreads];
      for (int i=0; i<nrThreads; i++) {
         gfsThreads_[i] = new GaussianFitStackThread(sourceList_,resultList_, siPlus, halfSize_,
                 shape_, fitMode_);

         // TODO: more efficient way of passing through settings!
         gfsThreads_[i].setPhotonConversionFactor(photonConversionFactor_);
         gfsThreads_[i].setGain(gain_);
         gfsThreads_[i].setPixelSize(pixelSize_);
         gfsThreads_[i].setTimeIntervalMs(timeIntervalMs_);
         gfsThreads_[i].setBaseLevel(baseLevel_);
         gfsThreads_[i].setNoiseTolerance(noiseTolerance_);
         gfsThreads_[i].setSigmaMax(widthMax_);
         gfsThreads_[i].setSigmaMin(widthMin_);
         gfsThreads_[i].setNrPhotonsMin(nrPhotonsMin_);
         gfsThreads_[i].setNrPhotonsMax(nrPhotonsMax_);
         gfsThreads_[i].setMaxIterations(maxIterations_);
         gfsThreads_[i].setUseWidthFilter(useWidthFilter_);
         gfsThreads_[i].setUseNrPhotonsFilter(useNrPhotonsFilter_);
         gfsThreads_[i].init();
      }

      int nrImages = siPlus.getNChannels() * siPlus.getNSlices() * siPlus.getNFrames();
      int imageCount = 0;
      try {
      for (int c = 1; c <= siPlus.getNChannels(); c++) {
         for (int z = 1; z <= siPlus.getNSlices(); z++) {
            for (int f = 1; f <= siPlus.getNFrames(); f++ ) {

               ij.IJ.showStatus("Finding Maxima...");
               imageCount++;
               
               ImageProcessor siProc;
               Polygon p;
               synchronized(GaussianSpotData.lockIP) {
                  siPlus.setPositionWithoutUpdate(c, z, f);
                  siPlus.setRoi(originalRoi, false);
                  siProc = siPlus.getProcessor(); 

                  p = FindLocalMaxima.FindMax(siPlus, 1, noiseTolerance_, preFilterType_);
               }

               ij.IJ.showProgress(imageCount, nrImages);


               if (p.npoints > nrSpots)
                  nrSpots = p.npoints;
               int[][] sC = new int[p.npoints][2];
               for (int j=0; j < p.npoints; j++) {
                  sC[j][0] = p.xpoints[j];
                  sC[j][1] = p.ypoints[j];
               }
                   

               Arrays.sort(sC, new SpotSortComparator());
               
               for (int j=0; j<sC.length; j++) {
                  // filter out spots too close to the edge
                  if (sC[j][0] > halfSize_ && sC[j][0] < siPlus.getWidth() - halfSize_ &&
                          sC[j][1] > halfSize_ && sC[j][1] < siPlus.getHeight() - halfSize_) {
                     ImageProcessor sp = GaussianSpotData.getSpotProcessor(siProc,
                             halfSize_, sC[j][0], sC[j][1]);
                     GaussianSpotData thisSpot = new GaussianSpotData(sp, c, z, f,
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

      // start ProgresBar thread
      ProgressThread pt = new ProgressThread(sourceList_);
      pt.init();
      
      
      } catch (OutOfMemoryError ome) {
         ij.IJ.error("Out Of Memory");
      }

      // Send working threads signal that we are done:
      GaussianSpotData lastSpot = new GaussianSpotData(null, -1, 1, -1, -1, -1, -1, -1);
      try {
         sourceList_.put(lastSpot);
      } catch (InterruptedException iex) {
         Thread.currentThread().interrupt();
         throw new RuntimeException("Unexpected interruption");
      }

      // wait for worker threads to finish
      for (int i=0; i<nrThreads; i++) {
         try {
            gfsThreads_[i].join();
         } catch (InterruptedException ie) {
         }
      }

      sourceList_.clear();
      return nrSpots;
   }

   private class SpotSortComparator implements Comparator {

      // Return the result of comparing the two row arrays
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


   


}