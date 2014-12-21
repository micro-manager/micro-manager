package edu.valelab.GaussianFit.spotAssociations;

import edu.valelab.GaussianFit.DataCollectionForm;
import static edu.valelab.GaussianFit.DataCollectionForm.getInstance;
import edu.valelab.GaussianFit.data.GaussianSpotData;
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
         List<GaussianSpotData> destList = new ArrayList<GaussianSpotData>();
         // maintain active tracks here
         List< List<GaussianSpotData>> tracks = 
                 new ArrayList<List<GaussianSpotData>>();
         for (int pos = 1; pos <= rowData.nrPositions_; pos++) {
            for (int ch = 1; ch <= rowData.nrChannels_; ch++) {
               for (int s = 1; s <= rowData.nrSlices_; s++) {
                  for (int f = 1; f <= rowData.nrFrames_; f++) {
                     List<GaussianSpotData> spots = rowData.get(f, s, ch, pos);
                     if (spots != null) {
                        // keep track of spots in this frame added to tracks 
                        List<GaussianSpotData> markedSpots = new ArrayList<GaussianSpotData>();
                        // go through all tracks to see if they can be extended
                        if (tracks.size() > 0) {
                           ArrayList<GsSpotPair> gsSpots = new ArrayList<GsSpotPair>();
                           for (GaussianSpotData spot : spots) {
                              gsSpots.add(new GsSpotPair(spot,
                                      new Point2D.Double(spot.getXCenter(), spot.getYCenter()),
                                      new Point2D.Double(0.0, 0.0)));
                           }
                           NearestPointGsSpotPair nsp = new NearestPointGsSpotPair(gsSpots, maxDistance);
                           List<List<GaussianSpotData>> removedTracks = 
                                   new ArrayList<List<GaussianSpotData>>();
                           for (List<GaussianSpotData> track : tracks) {
                              GaussianSpotData tSpot = track.get(track.size() - 1);
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
                           for (List<GaussianSpotData> track : removedTracks) {
                              tracks.remove(track);
                           }
                        }
                        // go through spots and start a new track with any spot 
                        // that was not part of a track
                        for (GaussianSpotData spot : spots) {
                           if (!markedSpots.contains(spot)) {
                              List<GaussianSpotData> track = new ArrayList<GaussianSpotData>();
                              track.add(spot);
                              tracks.add(track);
                           }
                        }
                     }
                  }
                  // add tracks that made it to the end to destination list
                  for (List<GaussianSpotData> track : tracks) {
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
