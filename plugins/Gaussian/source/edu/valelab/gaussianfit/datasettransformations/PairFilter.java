package edu.valelab.gaussianfit.datasettransformations;

import edu.valelab.gaussianfit.DataCollectionForm;
import edu.valelab.gaussianfit.data.RowData;
import edu.valelab.gaussianfit.data.SpotData;
import edu.valelab.gaussianfit.spotoperations.NearestPoint2D;
import edu.valelab.gaussianfit.utils.ListUtils;
import edu.valelab.gaussianfit.utils.ReportingUtils;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 *
 * @author nico
 */
public class PairFilter {

   /**
    * Creates a new data set that only contains spot pairs that match our
    * criteria.
    *
    *
    * @param rowData - input source of data
    * @param maxDistance - maximum distance of separation between spots in two
    * channels for them to be considered a pair
    * @param deviationMax - maximum deviation from the mean (expressed in
    * standard deviations) above which a pair is rejected
    * @param nrQuadrants - NUmber of quadrants in which the image should be
    * divided (i.e., each quadrant will be filtered by itself) Valid numbers are
    * 1, and squares of integers (i.e. 4, 9, 16).
    *
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
                  Map<Integer, List<SpotData>> gsCh1 = new HashMap<Integer, List<SpotData>>(nrQuadrants);
                  for (int q = 0; q < nrQuadrants; q++) {
                     gsCh1.put(q, new ArrayList<SpotData>());
                  }
                  // leave channel 2 intact (i.e. do not split in quadrants)
                  List<Point2D.Double> xyPointsCh2 = new ArrayList<Point2D.Double>();

                  for (SpotData gs : rowData.spotList_) {
                     if (gs.getFrame() == frame) {
                        if (gs.getChannel() == 1) {
                           int yOffset = (int) Math.floor(gs.getYCenter() / qSize);
                           int xOffset = (int) Math.floor(gs.getXCenter() / qSize);
                           int q = yOffset * sqrtNrQuadrants + xOffset;
                           gsCh1.get(q).add(gs);
                        } else if (gs.getChannel() == 2) {
                           Point2D.Double point = new Point2D.Double(gs.getXCenter(), gs.getYCenter());
                           xyPointsCh2.add(point);
                        }
                     }
                  }

                  if (xyPointsCh2.isEmpty()) {
                     ReportingUtils.logError("Pairs function in Localization plugin: no points found in second channel in frame " + frame);
                     continue;
                  }

                  // we have the points of channel 1 in each quadrant
                  // find each matching partner, and do statistics on each quadrant
                  // only keep pairs that match what was requested
                  for (int q = 0; q < nrQuadrants; q++) {
                     // Find matching points in the two ArrayLists
                     Iterator it2 = gsCh1.get(q).iterator();
                     NearestPoint2D np = new NearestPoint2D(xyPointsCh2,
                             maxDistance);
                     ArrayList<Double> distances = new ArrayList<Double>();
                     ArrayList<Double> orientations = new ArrayList<Double>();

                     while (it2.hasNext()) {
                        SpotData gs = (SpotData) it2.next();
                        Point2D.Double pCh1 = new Point2D.Double(gs.getXCenter(), gs.getYCenter());
                        Point2D.Double pCh2 = np.findKDWSE(pCh1);
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
                        Point2D.Double pCh2 = np.findKDWSE(pCh1);
                        if (pCh2 != null) {
                           double d2 = NearestPoint2D.distance2(pCh1, pCh2);
                           double d = Math.sqrt(d2);
                           // we can possibly add the same criterium for orientation
                           if (d > distAvg - deviationMax * distStd
                                   && d < distAvg + deviationMax * distStd) {
                              correctedData.add(gs);
                              // we have to find the matching spot in channel 2!
                              for (SpotData gsCh2 : rowData.spotList_) {
                                 if (gsCh2.getFrame() == frame) {
                                    if (gsCh2.getChannel() == 2) {
                                       if (gsCh2.getXCenter() == pCh2.x && gsCh2.getYCenter() == pCh2.y) {
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
               DataCollectionForm.getInstance().addSpotData(
                       rowData.name_ + "-Pair-Corrected", rowData.title_, "", rowData.width_,
                       rowData.height_, rowData.pixelSizeNm_, rowData.zStackStepSizeNm_,
                       rowData.shape_, rowData.halfSize_, rowData.nrChannels_,
                       rowData.nrFrames_, rowData.nrSlices_, 1, rowData.maxNrSpots_,
                       correctedData, null, false, DataCollectionForm.Coordinates.NM, rowData.hasZ_,
                       rowData.minZ_, rowData.maxZ_);
               ij.IJ.showStatus("Finished pair correction");
            } catch (OutOfMemoryError oom) {
               System.gc();
               ij.IJ.error("Out of Memory");
            }
         }
      };

       
      (new Thread(doWorkRunnable)).start();
   }

}
