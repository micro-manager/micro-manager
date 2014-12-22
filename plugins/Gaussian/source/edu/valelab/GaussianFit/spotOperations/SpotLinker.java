package edu.valelab.GaussianFit.spotOperations;

import edu.valelab.GaussianFit.DataCollectionForm;
import static edu.valelab.GaussianFit.DataCollectionForm.getInstance;
import edu.valelab.GaussianFit.data.SpotData;
import edu.valelab.GaussianFit.data.GsSpotPair;
import edu.valelab.GaussianFit.data.RowData;
import java.awt.geom.Point2D;
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
         List<SpotData> destList = new ArrayList<SpotData>();
         // maintain active tracks here
         List< List<SpotData>> tracks = 
                 new ArrayList<List<SpotData>>();
         for (int pos = 1; pos <= rowData.nrPositions_; pos++) {
            for (int ch = 1; ch <= rowData.nrChannels_; ch++) {
               for (int s = 1; s <= rowData.nrSlices_; s++) {
                  for (int f = 1; f <= rowData.nrFrames_; f++) {
                     List<SpotData> spots = rowData.get(f, s, ch, pos);
                     if (spots != null) {
                        // keep track of spots in this frame added to tracks 
                        List<SpotData> markedSpots = new ArrayList<SpotData>();
                        // go through all tracks to see if they can be extended
                        if (tracks.size() > 0) {
                           ArrayList<GsSpotPair> gsSpots = new ArrayList<GsSpotPair>();
                           for (SpotData spot : spots) {
                              gsSpots.add(new GsSpotPair(spot,
                                      new Point2D.Double(spot.getXCenter(), spot.getYCenter()),
                                      new Point2D.Double(0.0, 0.0)));
                           }
                           NearestPointGsSpotPair nsp = new NearestPointGsSpotPair(gsSpots, maxDistance);
                           List<List<SpotData>> removedTracks = 
                                   new ArrayList<List<SpotData>>();
                           for (List<SpotData> track : tracks) {
                              SpotData tSpot = track.get(track.size() - 1);
                              GsSpotPair newSpot = nsp.findKDWSE(new Point2D.Double(
                                      tSpot.getXCenter(), tSpot.getYCenter()));
                              if (newSpot == null) {
                                 // track could not be extended, finalize it
                                 linkSpots(track, destList, useFrames);
                                 // and remove from the list of tracks
                                 // to avoid a concurrent modification exception
                                 // the removal needs to be a two step process
                                 removedTracks.add(track);
                              } else {
                                 track.add(newSpot.getGSD());
                                 markedSpots.add(newSpot.getGSD());
                              }
                           }
                           // second part of removing tracks
                           for (List<SpotData> track : removedTracks) {
                              tracks.remove(track);
                           }
                        }
                        // go through spots and start a new track with any spot 
                        // that was not part of a track
                        for (SpotData spot : spots) {
                           if (!markedSpots.contains(spot)) {
                              List<SpotData> track = new ArrayList<SpotData>();
                              track.add(spot);
                              tracks.add(track);
                           }
                        }
                     }
                  }
                  // add tracks that made it to the end to destination list
                  for (List<SpotData> track : tracks) {
                     linkSpots(track, destList, useFrames);
                  }
                  tracks.clear();
               }
            }
         }

      
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
    *                 linked frames
    * @param dest - list of spots in which each entry represents multiple linked
    *               spots
    * @param useFrames - whether or not the single axis of the image stack lies about its identity
    */
   private static void linkSpots(List<SpotData> source, List<SpotData> dest,
           boolean useFrames) {
      if (source == null) {
         return;
      }
      if (dest == null) {
         return;
      }

      SpotData sp = new SpotData(source.get(0));

      double intensity = 0.0;
      double background = 0.0;
      double xCenter = 0.0;
      double yCenter = 0.0;
      double width = 0.0;
      double a = 0.0;
      double theta = 0.0;
      double sigma = 0.0;

      for (SpotData spot : source) {
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
