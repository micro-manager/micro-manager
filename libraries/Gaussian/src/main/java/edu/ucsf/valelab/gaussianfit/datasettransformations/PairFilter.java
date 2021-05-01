/*
Copyright (c) 2010-2017, Regents of the University of California
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

package edu.ucsf.valelab.gaussianfit.datasettransformations;

import edu.ucsf.valelab.gaussianfit.DataCollectionForm;
import edu.ucsf.valelab.gaussianfit.data.RowData;
import edu.ucsf.valelab.gaussianfit.data.SpotData;
import edu.ucsf.valelab.gaussianfit.spotoperations.NearestPoint2D;
import edu.ucsf.valelab.gaussianfit.utils.ListUtils;
import edu.ucsf.valelab.gaussianfit.utils.ReportingUtils;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author nico
 */
public class PairFilter {

   public static AtomicBoolean isRunning_ = new AtomicBoolean(false);

   public static void resetLock() {
      isRunning_.set(false);
   }

   /**
    * Creates a new data set that only contains spot pairs that match our criteria. The filter finds
    * pairs of a spot in channel 1 and 2 (in the same frame and position) within a distance
    * maxdistance.  It then divides the image in n quadrants (where n is a square of an integer),
    * and calculates the average distance between the spots within this quadrant. Pairs with a
    * distance that is greater than deviationMax * the standard deviation away from the mean, are
    * rejected, all others make it into the output.
    *
    * @param rowData      - input source of data
    * @param maxDistance  - maximum distance of separation between spots in two channels for them to
    *                     be considered a pair
    * @param deviationMax - maximum deviation from the mean (expressed in standard deviations) above
    *                     which a pair is rejected
    * @param nrQuadrants  - Number of quadrants in which the image should be divided (i.e., each
    *                     quadrant will be filtered by itself) Valid numbers are 1, and squares of
    *                     integers (i.e. 4, 9, 16).
    */
   public static void filter(final RowData rowData, final double maxDistance,
         final double deviationMax, final int nrQuadrants) {

      if (rowData.spotList_.size() <= 1) {
         return;
      }

      final int sqrtNrQuadrants = (int) Math.sqrt(nrQuadrants);
      if (nrQuadrants != (sqrtNrQuadrants * sqrtNrQuadrants)) {
         ReportingUtils.showError("nrQuadrants should be a square of an integer");
         return;
      }

      final double qSize = rowData.width_ * rowData.pixelSizeNm_ / sqrtNrQuadrants;

      ij.IJ.showStatus("Executing pair selection");

      Runnable doWorkRunnable = new Runnable() {

         @Override
         public void run() {
            try {
               List<SpotData> correctedData = new ArrayList<SpotData>();

               for (int frame = 1; frame <= rowData.nrFrames_; frame++) {
                  ij.IJ.showProgress(frame, rowData.nrFrames_);

                  // Get points from both channels in each frame as ArrayLists 
                  // split channel 1 into the nrQuadrants
                  Map<Integer, List<SpotData>> gsCh1 = new HashMap<Integer, List<SpotData>>(
                        nrQuadrants);
                  for (int q = 0; q < nrQuadrants; q++) {
                     gsCh1.put(q, new ArrayList<SpotData>());
                  }
                  // index channel 2 by position
                  ArrayList<List<Point2D.Double>> xyPointsCh2 =
                        new ArrayList<List<Point2D.Double>>(rowData.nrPositions_);
                  ArrayList<List<SpotData>> xySpotsCh2 =
                        new ArrayList<List<SpotData>>(rowData.nrPositions_);
                  for (int position = 1; position <= rowData.nrPositions_; position++) {
                     xyPointsCh2.add(position - 1, new ArrayList<Point2D.Double>());
                     xySpotsCh2.add(position - 1, new ArrayList<SpotData>());
                  }

                  for (SpotData gs : rowData.spotList_) {
                     if (gs.getFrame() == frame) {
                        if (gs.getChannel() == 1) {
                           int yOffset = (int) Math.floor(gs.getYCenter() / qSize);
                           int xOffset = (int) Math.floor(gs.getXCenter() / qSize);
                           int q = yOffset * sqrtNrQuadrants + xOffset;
                           if (q >= 0 && q < nrQuadrants) {
                              gsCh1.get(q).add(gs);
                           }
                        } else if (gs.getChannel() == 2) {
                           xySpotsCh2.get(gs.getPosition() - 1).add(gs);
                           Point2D.Double point = new Point2D.Double(gs.getXCenter(),
                                 gs.getYCenter());
                           xyPointsCh2.get(gs.getPosition() - 1).add(point);
                        }
                     }
                  }

                  if (xyPointsCh2.isEmpty()) {
                     ReportingUtils.logError(
                           "Pairs function in Localization plugin: no points found in second channel in frame "
                                 + frame);
                     continue;
                  }

                  // we have the points of channel 1 in each quadrant
                  // find each matching partner, and do statistics on each quadrant
                  // only keep pairs that match what was requested

                  // First set up the nearestPoint maps for all positions
                  List<NearestPoint2D> npsByPosition = new ArrayList<NearestPoint2D>(
                        rowData.nrPositions_);
                  for (int position = 1; position <= rowData.nrPositions_; position++) {
                     npsByPosition.add(position - 1,
                           new NearestPoint2D(xyPointsCh2.get(position - 1), maxDistance));
                  }
                  for (int q = 0; q < nrQuadrants; q++) {
                     ij.IJ.showProgress((q + 1) * frame, (nrQuadrants + 1) * rowData.nrFrames_);
                     // Find matching points in the two ArrayLists
                     Iterator it2 = gsCh1.get(q).iterator();
                     ArrayList<Double> distances = new ArrayList<Double>();
                     ArrayList<Double> orientations = new ArrayList<Double>();

                     while (it2.hasNext()) {
                        SpotData gs = (SpotData) it2.next();
                        Point2D.Double pCh1 = new Point2D.Double(gs.getXCenter(), gs.getYCenter());
                        Point2D.Double pCh2 = npsByPosition.get(gs.getPosition() - 1)
                              .findKDWSE(pCh1);
                        if (pCh2 != null) {
                           double d2 = NearestPoint2D.distance2(pCh1, pCh2);
                           double d = Math.sqrt(d2);
                           distances.add(d);
                           orientations.add(NearestPoint2D.orientation(pCh1, pCh2));
                        }
                     }
                     double distAvg = ListUtils.listAvg(distances);
                     double distStd = ListUtils.listStdDev(distances, distAvg);
                     double orientationAvg = ListUtils.listAvg(orientations);
                     double orientationStd = ListUtils.listStdDev(orientations,
                           orientationAvg);

                     // now repeat going through the list and apply the criteria
                     it2 = gsCh1.get(q).iterator();
                     while (it2.hasNext()) {
                        SpotData gs = (SpotData) it2.next();
                        Point2D.Double pCh1 = new Point2D.Double(gs.getXCenter(), gs.getYCenter());
                        Point2D.Double pCh2 = npsByPosition.get(gs.getPosition() - 1)
                              .findKDWSE(pCh1);
                        if (pCh2 != null) {
                           double d2 = NearestPoint2D.distance2(pCh1, pCh2);
                           double d = Math.sqrt(d2);
                           // we can possibly add the same criterium for orientation
                           if (d > distAvg - deviationMax * distStd
                                 && d < distAvg + deviationMax * distStd) {
                              correctedData.add(gs);
                              // we have to find the matching spot in channel 2!
                              for (SpotData gsCh2 : xySpotsCh2.get(gs.getPosition() - 1)) {
                                 if (gsCh2.getFrame() == frame) {
                                    if (gsCh2.getChannel() == 2) {
                                       if (gsCh2.getXCenter() == pCh2.x
                                             && gsCh2.getYCenter() == pCh2.y) {
                                          correctedData.add(gsCh2);
                                       }
                                    }
                                 }
                              }
                           }
                        }
                     }
                  }
               }

               // Add transformed data to data overview window
               RowData.Builder builder = rowData.copy();
               builder.setName(rowData.getName() + "-Pair-Corrected").
                     setSpotList(correctedData);
               DataCollectionForm.getInstance().addSpotData(builder);

               ij.IJ.showStatus("Finished pair correction");
            } catch (OutOfMemoryError oom) {
               System.gc();
               ij.IJ.error("Out of Memory");
            }
            isRunning_.set(false);
         }
      };

      if (!isRunning_.get()) {
         isRunning_.set(true);
         (new Thread(doWorkRunnable)).start();
      } else {
         // TODO: let the user know
      }
   }

}
