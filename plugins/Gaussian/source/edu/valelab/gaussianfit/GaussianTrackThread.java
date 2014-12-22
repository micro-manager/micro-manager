/**
 * GaussianTrackThread contains two main functions: trackGaussians and
 * trackFiducials for tracking of spots.  The only difference is in the output:
 * trackGaussians will calculate on-axis and off-axis travel and plot these
 * separately, whereas trackFiducials will plot all x-y positions found
 *
 * These functions can be run on a separate thread by setting the mode_ parameter
 * to either TRACK or FIDUCIAL.  GaussianTrackForm has the UI element that invokes
 * these functions.
 *
 */

package edu.valelab.gaussianfit;

import edu.valelab.gaussianfit.data.GaussianInfo;
import edu.valelab.gaussianfit.data.SpotData;
import edu.valelab.gaussianfit.utils.ReportingUtils;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.prefs.Preferences;


import ij.gui.*;
import ij.ImagePlus;
import ij.process.ImageProcessor;
import ij.IJ;
import java.awt.Polygon;
import java.util.Collections;
import java.util.List;


/**
 *
 * @author nico
 */
public class GaussianTrackThread extends GaussianInfo implements Runnable  {
   public final static int TRACK = 1;
   public final static int FIDUCIAL = 2;

   public static boolean windowOpen_ = false;
   double[] params0_ = {16000.0, 5.0, 5.0, 1.0, 850.0};
   double[] steps_ = new double[5];
   String [] paramNames_ = {"A", "x_c", "y_c", "sigma", "b"};
   private Preferences mainPrefs_;

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
    *
    * 
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
      
      if (!track(siPlusLocal_, xyPoints, timePoints))
         return;


      String name = siPlusLocal_.getWindow().getTitle() + "-" + firstX_ + "-" + firstY_;
      if (resultList_.size() > 0)
         addListToForm(name, resultList_, siPlusLocal_, timePoints);
   }


   private boolean track(ImagePlus siPlus,  ArrayList<Point2D.Double> xyPoints,
           ArrayList<Double> timePoints) {


      GaussianFit gs = new GaussianFit(shape_, fitMode_);
     
      double cPCF = photonConversionFactor_ / gain_;

      // for now, take the active ImageJ image 
      // (this should be an image of a difraction limited spot)

      Roi originalRoi = siPlus.getRoi();
      if (null == originalRoi) {
         if (!silent_)
            IJ.error("Please draw a Roi around the spot you want to track");
         return false;
      }
      
      Polygon pol = FindLocalMaxima.FindMax(siPlus, halfSize_, noiseTolerance_, preFilterType_);
      if (pol.npoints == 0) {
         if (!silent_)
            ReportingUtils.showError("No local maxima found in ROI" );
         else
            ReportingUtils.logError("No local maxima found in ROI");
         return false;
      }
      
      int xc = pol.xpoints[0];
      int yc = pol.ypoints[0];
      // not sure if needed, but look for the maximum local maximum
      int max = siPlus.getProcessor().getPixel(pol.xpoints[0], pol.ypoints[0]);
      if (pol.npoints > 1) {
         for (int i=1; i < pol.npoints; i++) {
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
      int size = 2 * halfSize_;

      
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
         pol = FindLocalMaxima.FindMax(siPlus, 2 * halfSize_, noiseTolerance_, preFilterType_);

         // do not stray more than 2 pixels in x or y.  
         // This velocity maximum parameter should be tunable by the user
         if (pol.npoints >= 1) {
            if (Math.abs(xc - pol.xpoints[0]) < 2 && Math.abs(yc - pol.ypoints[0]) < 2) {
               xc = pol.xpoints[0];
               yc = pol.ypoints[0];
            }
         }

         // Reset ROI to the original
         if (i==n) {
            firstX_ = xc;
            firstY_ = yc;
         }

         // Set Roi for fitting centered around maximum
         Roi spotRoi = new Roi(xc - halfSize_, yc - halfSize_, 2 * halfSize_, 2*halfSize_);
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
            
         spot = new SpotData(ip, ch, 1, i, 1, i, xc,yc);
         double[] paramsOut = gs.dogaussianfit(ip, maxIterations_);
         double sx;
         double sy;
         double a = 1.0;
         double theta = 0.0;
         if (paramsOut.length >= 4) {
            //anormalize the intensity from the Gaussian fit
            double N = cPCF * paramsOut[GaussianFit.INT] * (2 * Math.PI * paramsOut[GaussianFit.S] * paramsOut[GaussianFit.S]);           
            double xpc = paramsOut[GaussianFit.XC];
            double ypc = paramsOut[GaussianFit.YC];
            double x = (xpc - halfSize_ + xc) * pixelSize_;
            double y = (ypc - halfSize_ + yc) * pixelSize_;

               
            double s = paramsOut[GaussianFit.S] * pixelSize_;
            // express background in photons after base level correction
            double bgr = cPCF * (paramsOut[GaussianFit.BGR] - baseLevel_);
            // calculate error using formular from Thompson et al (2002)
            // (dx)2 = (s*s + (a*a/12)) / N + (8*pi*s*s*s*s * b*b) / (a*a*N*N)
            double sigma = (s * s + (pixelSize_ * pixelSize_) / 12) / 
                    N + (8 * Math.PI * s * s * s * s * bgr * bgr) / (pixelSize_ * pixelSize_ * N * N);
            sigma = Math.sqrt(sigma);

            double width = 2 * s;
            if (paramsOut.length >= 6) {
                sx = paramsOut[GaussianFit.S1] * pixelSize_;
                sy = paramsOut[GaussianFit.S2] * pixelSize_;
                a = sx/sy;
             }

             if (paramsOut.length >= 7) {
                theta = paramsOut[GaussianFit.S3];
             }
            if ((!useWidthFilter_ || (width > widthMin_ && width < widthMax_))
                    && (!useNrPhotonsFilter_ || (N > nrPhotonsMin_ && N < nrPhotonsMax_))) {
               // If we have a good fit, update position of the box
               if (xpc > 0 && xpc < (2 * halfSize_) && ypc > 0 && ypc < (2 * halfSize_)) {
                  xc += (int) xpc - halfSize_;
                  yc += (int) ypc - halfSize_;
               }
               spot.setData(N, bgr, x, y, 0.0, 2 * s, a, theta, sigma);
               xyPoints.add(new Point2D.Double(x, y));
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

   private void addListToForm(String name, List<SpotData> resultList, ImagePlus siPlus, ArrayList<Double> timePoints) {
      // Add data to data overview window
      DataCollectionForm dcForm = DataCollectionForm.getInstance();
      dcForm.addSpotData(name, siPlus.getTitle(), "", siPlus.getWidth(), 
              siPlus.getHeight(),  pixelSize_, (float) 0.0, shape_,
              halfSize_, siPlus.getNChannels(), siPlus.getNFrames(),
              siPlus.getNSlices(), 1, resultList.size(), resultList, 
              timePoints, true, DataCollectionForm.Coordinates.NM, false, 
              0.0, 0.0);
      dcForm.setVisible(true);
   }

  
   public void init() {
      t = new Thread(this);
      t.start();
   }

   public void run() {
      trackGaussians(false);
   }
   
}
