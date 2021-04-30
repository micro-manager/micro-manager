/**
 * GaussianTrackThread contains two main functions: trackGaussians and trackFiducials for tracking
 * of spots.  The only difference is in the output: trackGaussians will calculate on-axis and
 * off-axis travel and plot these separately, whereas trackFiducials will plot all x-y positions
 * found
 * <p>
 * These functions can be run on a separate thread by setting the mode_ parameter to either TRACK or
 * FIDUCIAL.  GaussianTrackForm has the UI element that invokes these functions.
 * <p>
 * Author: Nico Stuurman
 * <p>
 * Copyright (c) 2010-2017, Regents of the University of California All rights reserved.
 * <p>
 * Redistribution and use in source and binary forms, with or without modification, are permitted
 * provided that the following conditions are met:
 * <p>
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions
 * and the following disclaimer. 2. Redistributions in binary form must reproduce the above
 * copyright notice, this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 * <p>
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY
 * WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 * <p>
 * The views and conclusions contained in the software and documentation are those of the authors
 * and should not be interpreted as representing official policies, either expressed or implied, of
 * the FreeBSD Project.
 */


package edu.ucsf.valelab.gaussianfit.fitmanagement;

import edu.ucsf.valelab.gaussianfit.DataCollectionForm;
import edu.ucsf.valelab.gaussianfit.algorithm.FindLocalMaxima;
import edu.ucsf.valelab.gaussianfit.algorithm.GaussianFit;
import edu.ucsf.valelab.gaussianfit.data.GaussianInfo;
import edu.ucsf.valelab.gaussianfit.data.RowData;
import edu.ucsf.valelab.gaussianfit.data.SpotData;
import edu.ucsf.valelab.gaussianfit.utils.ReportingUtils;
import ij.IJ;
import ij.ImagePlus;
import ij.gui.*;
import ij.process.ImageProcessor;
import java.awt.Polygon;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


/**
 * @author nico
 */
public class GaussianTrackThread extends GaussianInfo implements Runnable {

   public final static int TRACK = 1;
   public final static int FIDUCIAL = 2;

   public static boolean windowOpen_ = false;
   double[] params0_ = {16000.0, 5.0, 5.0, 1.0, 850.0};
   double[] steps_ = new double[5];
   String[] paramNames_ = {"A", "x_c", "y_c", "sigma", "b"};

   private int firstX_ = 0;
   private int firstY_ = 0;


   static final String XCOLNAME = "X";
   static final String YCOLNAME = "Y";

   private Thread t;

   private final FindLocalMaxima.FilterType preFilterType_;
   private boolean silent_;
   private final ImagePlus siPlusLocal_;


   public void setWindowClosed() {
      windowOpen_ = false;
   }


   /**
    * @param siPlus
    * @param preFilterType
    */
   public GaussianTrackThread(ImagePlus siPlus, FindLocalMaxima.FilterType preFilterType) {
      super();
      preFilterType_ = preFilterType;
      siPlusLocal_ = siPlus;
   }

   public void trackGaussians(boolean silent) {
      silent_ = silent;
      ArrayList<Point2D.Double> xyPoints = new ArrayList<Point2D.Double>();
      ArrayList<Double> timePoints = new ArrayList<Double>();
      resultList_ = Collections.synchronizedList(new ArrayList<SpotData>());

      if (siPlusLocal_ == null) {
         ReportingUtils.showError("Empty ImagePlus");
      }

      if (!track(siPlusLocal_, xyPoints, timePoints)) {
         return;
      }

      String name = siPlusLocal_.getWindow().getTitle() + "-" + firstX_ + "-" + firstY_;
      if (resultList_.size() > 0) {
         addListToForm(name, resultList_, siPlusLocal_, timePoints);
      }
   }


   private boolean track(ImagePlus siPlus, ArrayList<Point2D.Double> xyPoints,
         ArrayList<Double> timePoints) {

      GaussianFit gs = new GaussianFit(super.getShape(), super.getFitMode());

      double cPCF = photonConversionFactor_ / gain_;

      // for now, take the active ImageJ image 
      // (this should be an image of a difraction limited spot)

      Roi originalRoi = siPlus.getRoi();
      if (null == originalRoi) {
         if (!silent_) {
            IJ.error("Please draw a Roi around the spot you want to track");
         }
         return false;
      }

      Polygon pol = FindLocalMaxima.FindMax(siPlus, 2 * super.getHalfBoxSize(),
            super.getNoiseTolerance(), preFilterType_);
      if (pol.npoints == 0) {
         if (!silent_) {
            ReportingUtils.showError("No local maxima found in ROI");
         } else {
            ReportingUtils.logError("No local maxima found in ROI");
         }
         return false;
      }

      int xc = pol.xpoints[0];
      int yc = pol.ypoints[0];
      // not sure if needed, but look for the maximum local maximum
      int max = siPlus.getProcessor().getPixel(pol.xpoints[0], pol.ypoints[0]);
      if (pol.npoints > 1) {
         for (int i = 1; i < pol.npoints; i++) {
            if (siPlus.getProcessor().getPixel(pol.xpoints[i], pol.ypoints[i]) > max) {
               max = siPlus.getProcessor().getPixel(pol.xpoints[i], pol.ypoints[i]);
               xc = pol.xpoints[i];
               yc = pol.ypoints[i];
            }
         }
      }

      long startTime = System.nanoTime();

      // This is confusing.   We like to accomodate stacks with multiple slices
      // and stacks with multiple frames (which is actually the correct way

      int ch = siPlus.getChannel();
      Boolean useSlices = siPlus.getNSlices() > siPlus.getNFrames();

      int n = siPlus.getSlice();
      int nMax = siPlus.getNSlices();
      if (!useSlices) {
         n = siPlus.getFrame();
         nMax = siPlus.getNFrames();
      }
      boolean stop = false;
      int missedFrames = 0;
      int size = 2 * super.getHalfBoxSize();

      for (int i = n; i <= nMax && !stop; i++) {
         SpotData spot;

         // Give user feedback
         ij.IJ.showStatus("Tracking...");
         ij.IJ.showProgress(i, nMax);

         // Search in next slice in same Roi for local maximum
         Roi searchRoi = new Roi(xc - size, yc - size, 2 * size + 1, 2 * size + 1);
         if (useSlices) {
            siPlus.setSliceWithoutUpdate(siPlus.getStackIndex(ch, i, 1));
         } else {
            siPlus.setSliceWithoutUpdate(siPlus.getStackIndex(ch, 1, i));
         }
         siPlus.setRoi(searchRoi, false);

         // Find maximum in Roi, might not be needed....
         pol = FindLocalMaxima.FindMax(siPlus, size, noiseTolerance_, preFilterType_);

         // do not stray more than 2 pixels in x or y.  
         // This velocity maximum parameter should be tunable by the user
         if (pol.npoints >= 1) {
            if (Math.abs(xc - pol.xpoints[0]) < 2 && Math.abs(yc - pol.ypoints[0]) < 2) {
               xc = pol.xpoints[0];
               yc = pol.ypoints[0];
            }
         }

         // Reset ROI to the original
         if (i == n) {
            firstX_ = xc;
            firstY_ = yc;
         }

         // Set Roi for fitting centered around maximum
         Roi spotRoi = new Roi(xc - super.getHalfBoxSize(), yc - super.getHalfBoxSize(),
               size, size);
         siPlus.setRoi(spotRoi, false);
         ImageProcessor ip;
         try {
            if (siPlus.getRoi() != spotRoi) {
               ReportingUtils.logError(
                     "There seems to be a thread synchronization issue going on that causes this weirdness");
            }
            ip = siPlus.getProcessor().crop();
         } catch (ArrayIndexOutOfBoundsException aex) {
            ReportingUtils.logError(aex,
                  "ImageJ failed to crop the image, not sure why");
            siPlus.setRoi(spotRoi, true);
            ip = siPlus.getProcessor().crop();
         }

         spot = new SpotData(ip, ch, 1, i, 1, i, xc, yc);
         GaussianFit.Data fitResult = gs.dogaussianfit(ip, maxIterations_);
         spot = SpotDataConverter.convert(spot, fitResult, this, null);

         if (fitResult.getParms().length >= 4) {
            if ((!useWidthFilter_ || (spot.getWidth() > widthMin_ && spot.getWidth() < widthMax_))
                  && (!useNrPhotonsFilter_ || (spot.getIntensity() > nrPhotonsMin_
                  && spot.getIntensity() < nrPhotonsMax_))) {
               // If we have a good fit, update position of the box
               double xpc = fitResult.getParms()[GaussianFit.XC];
               double ypc = fitResult.getParms()[GaussianFit.YC];
               if (xpc > 0 && xpc < (size) && ypc > 0 && ypc < (size)) {
                  xc += (int) xpc - getHalfBoxSize();
                  yc += (int) ypc - getHalfBoxSize();
               }
               xyPoints.add(new Point2D.Double(spot.getXCenter(), spot.getYCenter()));
               timePoints.add(i * timeIntervalMs_);
               resultList_.add(spot);
               missedFrames = 0;
            } else {
               missedFrames += 1;
            }
         } else {
            missedFrames += 1;
         }
         if (endTrackAfterBadFrames_) {
            if (missedFrames >= this.endTrackAfterNBadFrames_) {
               stop = true;
            }
         }
      }

      long endTime = System.nanoTime();
      double took = (endTime - startTime) / 1E6;

      print("Calculation took: " + took + " milli seconds");

      ij.IJ.showStatus("");

      siPlus.setSlice(n);
      siPlus.setRoi(originalRoi);

      return true;
   }

   private void addListToForm(String name, List<SpotData> resultList, ImagePlus siPlus,
         ArrayList<Double> timePoints) {
      // Add data to data overview window
      RowData.Builder builder = new RowData.Builder();
      builder.setName(name).setTitle(siPlus.getTitle()).
            setWidth(siPlus.getWidth()).setHeight(siPlus.getHeight()).
            setPixelSizeNm(pixelSize_).setZStackStepSizeNm(0.0f).
            setShape(super.getShape()).setHalfSize(super.getHalfBoxSize()).
            setNrChannels(siPlus.getNChannels()).setNrFrames(siPlus.getNFrames()).
            setNrSlices(siPlus.getNSlices()).setNrPositions(1).
            setMaxNrSpots(resultList.size()).setSpotList(resultList).
            setTimePoints(timePoints).setIsTrack(true).
            setCoordinate(DataCollectionForm.Coordinates.NM).
            setHasZ(false).setMinZ(0.0).setMaxZ(0.0);
      DataCollectionForm.getInstance().addSpotData(builder);

      DataCollectionForm.getInstance().setVisible(true);
   }


   public void init() {
      t = new Thread(this);
      t.start();
   }

   @Override
   public void run() {
      trackGaussians(false);
   }

}
