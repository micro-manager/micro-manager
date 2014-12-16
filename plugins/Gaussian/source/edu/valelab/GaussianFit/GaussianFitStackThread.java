package edu.valelab.GaussianFit;


import edu.valelab.GaussianFit.fitting.ZCalibrator;
import ij.ImagePlus;
import ij.process.ImageProcessor;
import java.util.List;
import java.util.concurrent.BlockingQueue;


/**
 *
 * @author nico
 */
public class GaussianFitStackThread extends GaussianInfo implements Runnable {

   Thread t_;
   boolean stopNow_ = false;


   public GaussianFitStackThread(BlockingQueue<GaussianSpotData> sourceList,
           List<GaussianSpotData> resultList, ImagePlus siPlus, int halfSize,
           int shape, int fitMode) {
      siPlus_ = siPlus;
      halfSize_ = halfSize;
      sourceList_ = sourceList;
      resultList_ = resultList;
      shape_ = shape;
      fitMode_ = fitMode;
   }

   public void listDone() {
      stop_ = true;
   }

   public void init() {
      stopNow_ = false;
      t_ = new Thread(this);
      t_.start();
   }

   public void stop() {
      stopNow_ = true;
   }

   public void join() throws InterruptedException {
      if (t_ != null)
         t_.join();
   }

   public void run() {
      GaussianFit gs_ = new GaussianFit(shape_, fitMode_);
      double cPCF = photonConversionFactor_ / gain_;
      ZCalibrator zc = DataCollectionForm.zc_;

      while (!stopNow_) {
         GaussianSpotData spot;
         synchronized (gfsLock_) {
            try {
               spot = sourceList_.take();
               // Look for signal that we are done, add back to queue if found
               if (spot.getFrame() == -1) {
                  sourceList_.add(spot);
                  return;
               }
            } catch (InterruptedException iExp) {
               ij.IJ.log("Thread interruped  " + Thread.currentThread().getName());
               return;
            }
         }

         try {
            // Note: the implementation will try to return a cached version of the ImageProcessor
            ImageProcessor ip = spot.getSpotProcessor(siPlus_, halfSize_);
            double[] paramsOut = gs_.doGaussianFit(ip, maxIterations_);
            // Note that the copy constructor will not copy pixel data, so we loose those when spot goes out of scope
            GaussianSpotData spotData = new GaussianSpotData(spot);
            double sx = 0;
            double sy = 0;
            double a = 1;
            double theta = 0;
            if (paramsOut.length >= 5) {
               double N = cPCF * paramsOut[GaussianFit.INT]
                       * (2 * Math.PI * paramsOut[GaussianFit.S] * paramsOut[GaussianFit.S]);
               double xMax = (paramsOut[GaussianFit.XC] - halfSize_ + spot.getX()) * pixelSize_;
               double yMax = (paramsOut[GaussianFit.YC] - halfSize_ + spot.getY()) * pixelSize_;
               double s = paramsOut[GaussianFit.S] * pixelSize_;
               // express background in photons after base level correction
               double bgr = cPCF * (paramsOut[GaussianFit.BGR] - baseLevel_);
               // calculate error using formular from Thompson et al (2002)
               // (dx)2 = (s*s + (a*a/12)) / N + (8*pi*s*s*s*s * b*b) / (a*a*N*N)
               double sigma = (s * s + (pixelSize_ * pixelSize_) / 12) / N
                       + (8 * Math.PI * s * s * s * s * bgr * bgr) / (pixelSize_ * pixelSize_ * N * N);
               sigma = Math.sqrt(sigma);

               if (paramsOut.length >= 6) {
                  sx = paramsOut[GaussianFit.S1] * pixelSize_;
                  sy = paramsOut[GaussianFit.S2] * pixelSize_;
                  a = sx / sy;
                  
                  double z = 0.0;              
               
                  if (zc.hasFitFunctions()) {
                     z = zc.getZ(2 * sx, 2 * sy);
                     spotData.setZCenter(z);
                  }
                  
               }

               if (paramsOut.length >= 7) {
                  theta = paramsOut[GaussianFit.S3];
               }

               double width = 2 * s;
               
               
               
               spotData.setData(N, bgr, xMax, yMax, 0.0, width, a, theta, sigma);

               if ((!useWidthFilter_ || (width > widthMin_ && width < widthMax_))
                       && (!useNrPhotonsFilter_ || (N > nrPhotonsMin_ && N < nrPhotonsMax_))) {
                  resultList_.add(spotData);
               }

            }
         } catch (Exception ex) {
            ex.printStackTrace();
            ij.IJ.log("Thread run out of memory  " + Thread.currentThread().getName());
            ij.IJ.error("Fitter out of memory", "Out of memory error");
            return;
         }
      }
   }
}
