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
import edu.ucsf.valelab.gaussianfit.utils.ReportingUtils;
import ij.ImagePlus;
import ij.process.ImageProcessor;
import java.util.List;
import java.util.concurrent.BlockingQueue;


/**
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
      if (t_ != null) {
         t_.join();
      }
   }

   @Override
   public void run() {
      GaussianFit gs_ = new GaussianFit(super.getShape(), super.getFitMode(),
            super.getUseFixedWidth(), super.getFixedWidthNm() / super.getPixelSize() / 2);
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
            SpotData spotData = SpotDataConverter.convert(spot, fitResult, this, zc);

            if (fitResult.getParms().length > 1 &&
                  (!useWidthFilter_ ||
                        (spotData.getWidth() > widthMin_ && spotData.getWidth() < widthMax_))
                  && (!useNrPhotonsFilter_ ||
                  (spotData.getIntensity() > nrPhotonsMin_
                        && spotData.getIntensity() < nrPhotonsMax_))) {
               resultList_.add(spotData);
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
