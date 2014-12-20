package edu.valelab.GaussianFit.spotAssociations;

import edu.valelab.GaussianFit.DataCollectionForm;
import static edu.valelab.GaussianFit.DataCollectionForm.getInstance;
import edu.valelab.GaussianFit.data.GaussianSpotData;
import edu.valelab.GaussianFit.utils.RowData;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JOptionPane;

/**
 * This code looks for "tracks" of spots in consecutive frames
 * Any found track (of 1 or more spots) is merged into a single spot
 * that is inserted into the output row 
 * The resulting spot has the coordinates (frame, channel, position) of the first
 * spot of the track.  Its position is averaged and the intensity is summed.
 * @author nico
 */
public class SpotLinker {

   public SpotLinker() {
   }

   /**
    * Function that executes spot linkage.  Goes through a list of spots
    * and looks in every consecutive frames for the closest by spot (at a 
    * maximum distance of maxDistance.  If no spot is found, the link is added
    * and the linked (averaged) spot is added to the destination list 
    * through the function linkSpots.  This code could also be used for spot tracking
    * 
    * @param rowData
    * @param maxDistance 
    */
   public static void link(RowData rowData, double maxDistance) {
      try {
         ij.IJ.showStatus("Linking spotData...");
         boolean useFrames = rowData.nrFrames_ > rowData.nrSlices_;
         int nr = rowData.nrSlices_;
         if (useFrames) {
            nr = rowData.nrFrames_;
         }

         // linked spots go here:
         List<GaussianSpotData> destList = new ArrayList<GaussianSpotData>();
         /*
         // create a list with spots from the first frame, 
         for (int frame = 1; frame < nr; frame++) {
            //List<GaussianSpotData> frameSpots = rowData.frameIndexSpotList_.get(i);
            //List<GaussianSpotData> nextFrameSpots = rowData.frameIndexSpotList_.get(i+1);
            //for (int frame = 1; frame <= rowData_.get(row).nrFrames_; frame++) {
                  ij.IJ.showProgress(frame, rowData_.get(row).nrFrames_);

                  // Get points from both channels in each frame as ArrayLists        
                  ArrayList<GaussianSpotData> gsCh1 = new ArrayList<GaussianSpotData>();
                  ArrayList<Point2D.Double> xyPointsCh2 = new ArrayList<Point2D.Double>();
                  for (GaussianSpotData gs : rowData_.get(row).spotList_) {
                     if (gs.getFrame() == frame) {
                        if (gs.getChannel() == 1) {
                           gsCh1.add(gs);
                        } else if (gs.getChannel() == 2) {
                           Point2D.Double point = new Point2D.Double(gs.getXCenter(), gs.getYCenter());
                           xyPointsCh2.add(point);
                        }
                     }
                  } 
         }
         */

         // build a 2D array of lists with gaussian spots
         @SuppressWarnings("unchecked")
         List<GaussianSpotData>[][] spotImage = new ArrayList[rowData.width_][rowData.height_];
         for (int i = 1; i < nr; i++) {
            ij.IJ.showStatus("Linking spotData...");
            ij.IJ.showProgress(i, nr);
            List<GaussianSpotData> frameSpots = rowData.frameIndexSpotList_.get(i);
            if (frameSpots != null) {
               for (GaussianSpotData spot : frameSpots) {
                  if (spotImage[spot.getX()][spot.getY()] == null) {
                     spotImage[spot.getX()][spot.getY()] = new ArrayList<GaussianSpotData>();
                  } else {
                     List<GaussianSpotData> prevSpotList = spotImage[spot.getX()][spot.getY()];
                     GaussianSpotData lastSpot = prevSpotList.get(prevSpotList.size() - 1);
                     int lastFrame = lastSpot.getFrame();
                     if (!useFrames) {
                        lastFrame = lastSpot.getSlice();
                     }
                     if (lastFrame != i - 1) {
                        linkSpots(prevSpotList, destList, useFrames);
                        spotImage[spot.getX()][spot.getY()] = new ArrayList<GaussianSpotData>();
                     }
                  }
                  spotImage[spot.getX()][spot.getY()].add(spot);
               }
            } else {
               System.out.println("Empty row: " + i);
            }
         }

         // Finish links of all remaining spots
         ij.IJ.showStatus("Finishing linking spotData...");
         for (int w = 0; w < rowData.width_; w++) {
            for (int h = 0; h < rowData.height_; h++) {
               if (spotImage[w][h] != null) {
                  linkSpots(spotImage[w][h], destList, useFrames);
               }
            }
         }
         ij.IJ.showStatus("");
         ij.IJ.showProgress(1);

         // Add destList to rowData
         DataCollectionForm.getInstance().addSpotData(rowData.name_ + " Linked", rowData.title_, "", rowData.width_,
                 rowData.height_, rowData.pixelSizeNm_, rowData.zStackStepSizeNm_,
                 rowData.shape_, rowData.halfSize_, rowData.nrChannels_, rowData.nrFrames_,
                 0, 1, rowData.maxNrSpots_, destList,
                 rowData.timePoints_, false, DataCollectionForm.Coordinates.NM, false, 0.0, 0.0);
      } catch (OutOfMemoryError oome) {
         JOptionPane.showMessageDialog(getInstance(), "Out of memory");
      }
   }

   /**
    * Given a list of linked spots, create a single spot entry that will be
    * added to the destination list
    *
    * @param source - list of spots that all occur around the same pixel and in
    * linked frames
    * @param dest - list spots in which each entry represents multiple linked
    * spots
    */
   private static void linkSpots(List<GaussianSpotData> source, List<GaussianSpotData> dest,
           boolean useFrames) {
      if (source == null) {
         return;
      }
      if (dest == null) {
         return;
      }

      GaussianSpotData sp = new GaussianSpotData(source.get(0));

      double intensity = 0.0;
      double background = 0.0;
      double xCenter = 0.0;
      double yCenter = 0.0;
      double width = 0.0;
      double a = 0.0;
      double theta = 0.0;
      double sigma = 0.0;

      for (GaussianSpotData spot : source) {
         intensity += spot.getIntensity();
         background += spot.getBackground();
         xCenter += spot.getXCenter();
         yCenter += spot.getYCenter();
         width += spot.getWidth();
         a += spot.getA();
         theta += spot.getTheta();
         sigma += spot.getSigma();
      }

      background /= source.size();
      xCenter /= source.size();
      yCenter /= source.size();
      width /= source.size();
      a /= source.size();
      theta /= source.size();
      sigma /= source.size();

      // not sure if this is correct:
      sigma /= Math.sqrt(source.size());

      sp.setData(intensity, background, xCenter, yCenter, 0.0, width, a, theta, sigma);
      sp.originalFrame_ = source.get(0).getFrame();
      if (!useFrames) {
         sp.originalFrame_ = source.get(0).getSlice();
      }
      sp.nrLinks_ = source.size();

      dest.add(sp);
   }

}
