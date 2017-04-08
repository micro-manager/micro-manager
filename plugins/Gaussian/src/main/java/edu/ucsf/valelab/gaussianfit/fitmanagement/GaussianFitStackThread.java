/*
Author: Nico Stuurman

Copyright (c) 2013-2017, Regents of the University of California
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
import edu.ucsf.valelab.gaussianfit.algorithm.GaussianFit;
import edu.ucsf.valelab.gaussianfit.data.GaussianInfo;
import edu.ucsf.valelab.gaussianfit.data.SpotData;
import edu.ucsf.valelab.gaussianfit.fitting.ZCalibrator;
import ij.ImagePlus;
import ij.process.ImageProcessor;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import edu.ucsf.valelab.gaussianfit.utils.ReportingUtils;


/**
 *
 * @author nico
 */
public class GaussianFitStackThread extends GaussianInfo implements Runnable {

   Thread t_;
   boolean stopNow_ = false;

   public GaussianFitStackThread(BlockingQueue<SpotData> sourceList,
           List<SpotData> resultList, ImagePlus siPlus) {
      siPlus_ = siPlus;
      sourceList_ = sourceList;
      resultList_ = resultList;
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

   @Override
   public void run() {
      GaussianFit gs_ = new GaussianFit(super.getShape(), super.getFitMode(),
            super.getUseFixedWidth(), super.getFixedWidthNm() / super.getPixelSize() / 2);
      double cPCF = photonConversionFactor_ / gain_;
      ZCalibrator zc = DataCollectionForm.zc_;

      while (!stopNow_) {
         SpotData spot;
         synchronized (GFSLOCK) {
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
            ImageProcessor ip = spot.getSpotProcessor(siPlus_, super.getHalfBoxSize());
            GaussianFit.Data fitResult = gs_.dogaussianfit(ip, maxIterations_);
            // Note that the copy constructor will not copy pixel data, so we loose 
            // those when spot goes out of scope
            SpotData spotData = new SpotData(spot);
            double sx;
            double sy;
            double a = 1;
            double theta = 0;
            double gs = super.getFixedWidthNm() / pixelSize_ / 2;
            if (fitResult.getParms().length >= 4) {

               double xMax = (fitResult.getParms()[GaussianFit.XC] - 
                       super.getHalfBoxSize() + spot.getX()) * pixelSize_;
               double yMax = (fitResult.getParms()[GaussianFit.YC] - 
                       super.getHalfBoxSize() + spot.getY()) * pixelSize_; 
               // express background in photons after base level correction
               double bgr = Math.sqrt(cPCF * (fitResult.getParms()[GaussianFit.BGR] - baseLevel_));
               
               if (fitResult.getParms().length >= 5) {
                  gs = fitResult.getParms()[GaussianFit.S];
               }
               double N = cPCF * fitResult.getParms()[GaussianFit.INT]
                       * (2 * Math.PI * gs * gs);
               
               // calculate error using formula from Thompson et al (2002)
               // (dx)2 = (s*s + (a*a/12)) / N + (8*pi*s*s*s*s * b*b) / (a*a*N*N)
               double s = gs * pixelSize_;
               double sasqr = s * s + (pixelSize_ * pixelSize_) / 12;
               double varX = sasqr / N
                       + (8 * Math.PI * s * s * s * s * bgr * bgr) / (pixelSize_ * pixelSize_ * N * N);
               double sigma = Math.sqrt(varX);
               
               // # of photons and background as calculated using the method by
               // Franke et al. : http://dx.doi/org/10.1038/nmeth.4073
               double NAperture = cPCF * fitResult.getApertureIntensity();
               double bgrAperture = Math.sqrt(cPCF * (fitResult.getApertureBackground() - baseLevel_));
               
               // Calculate error using the method by Mortenson et al.
               // http://dx.doi.org/10.1038/nmeth.1447
               // sasqr = s * s + (a * a)/12
               // d(x)2 = (sasqr /N) (16/9 + 8 * pi * sasqr * b * b / N * a * a)
               double mVarX = sasqr / N
                       * (16/9 + ( (8 * Math.PI * sasqr *  bgr * bgr) / 
                       (N * pixelSize_ * pixelSize_) ) );
               // If EM gain was used, add uncertainty due to Poisson distributed noise
               if (this.gain_ > 2.0) {
                  mVarX = 2 * mVarX;
               }
               double mSigma = Math.sqrt(mVarX);

               if (fitResult.getParms().length >= 6) {
                  sx = fitResult.getParms()[GaussianFit.S1] * pixelSize_;
                  sy = fitResult.getParms()[GaussianFit.S2] * pixelSize_;
                  a = sx / sy;
                  
                  double z;              
               
                  if (zc.hasFitFunctions()) {
                     z = zc.getZ(2 * sx, 2 * sy);
                     spotData.setZCenter(z);
                  }
                  
               }

               if (fitResult.getParms().length >= 7) {
                  theta = fitResult.getParms()[GaussianFit.S3];
               }

               double width = 2 * s;

               spotData.setData(N, bgr, xMax, yMax, 0.0, width, a, theta, sigma);
               spotData.addKeyValue(SpotData.Keys.APERTUREINTENSITY, NAperture);
               // Ratio of # of photons measured by Gaussian fit and Aperture method
               spotData.addKeyValue(SpotData.Keys.INTENSITYRATIO, N / NAperture);
               spotData.addKeyValue(SpotData.Keys.APERTUREBACKGROUND, bgrAperture);
               spotData.addKeyValue(SpotData.Keys.MSIGMA, mSigma);

               if ((!useWidthFilter_ || (width > widthMin_ && width < widthMax_))
                       && (!useNrPhotonsFilter_ || (N > nrPhotonsMin_ && N < nrPhotonsMax_))) {
                  resultList_.add(spotData);
               }

            }
         } catch (Exception ex) {
            ReportingUtils.logError(ex);
            ReportingUtils.logError("Thread run out of memory  " + 
                    Thread.currentThread().getName());
            ReportingUtils.showError("Fitter out of memory.\n" +
                    "Out of memory error");
            return;
         }
      }
   }
}
