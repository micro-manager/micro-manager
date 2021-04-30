/**
 * @author - Nico Stuurman,  2012
 * <p>
 * <p>
 * Copyright (c) 2012-2017, Regents of the University of California All rights reserved.
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

package edu.ucsf.valelab.gaussianfit.spotoperations;

import static edu.ucsf.valelab.gaussianfit.DataCollectionForm.getInstance;

import edu.ucsf.valelab.gaussianfit.DataCollectionForm;
import edu.ucsf.valelab.gaussianfit.data.GsSpotPair;
import edu.ucsf.valelab.gaussianfit.data.RowData;
import edu.ucsf.valelab.gaussianfit.data.SpotData;
import edu.ucsf.valelab.gaussianfit.data.TrackData;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.JOptionPane;

/**
 * This code looks for "tracks" of spots in consecutive frames Any found track (of 1 or more spots)
 * is merged into a single spot that is inserted into the output row The resulting spot has the
 * coordinates (frame, channel, position) of the first spot of the track.  Its position is averaged
 * and the intensity is summed.
 *
 * @author nico
 */
public class SpotLinker {

   public SpotLinker() {
   }

   /**
    * Function that executes spot linkage.  Goes through a list of spots and looks in every
    * consecutive frames for the closest by spot (at a maximum distance of maxDistance.  If no spot
    * is found, the link is added and the linked (averaged) spot is added to the destination list
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
         List<List<SpotData>> tracks =
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
                              gsSpots.add(new GsSpotPair(spot, null,
                                    new Point2D.Double(spot.getXCenter(), spot.getYCenter()),
                                    new Point2D.Double(0.0, 0.0)));
                           }
                           NearestPointByData nsp = new NearestPointByData(gsSpots, maxDistance);
                           List<List<SpotData>> removedTracks =
                                 new ArrayList<List<SpotData>>();
                           for (List<SpotData> track : tracks) {
                              SpotData tSpot = track.get(track.size() - 1);
                              // TODO: do we really need a copy here?
                              GsSpotPair newSpot = ((GsSpotPair) nsp.findKDWSE(new Point2D.Double(
                                    tSpot.getXCenter(), tSpot.getYCenter()))).copy();
                              if (newSpot == null) {
                                 // track could not be extended, finalize it
                                 linkSpots(track, destList, useFrames);
                                 // and remove from the list of tracks
                                 // to avoid a concurrent modification exception
                                 // the removal needs to be a two step process
                                 removedTracks.add(track);
                              } else {
                                 track.add(newSpot.getFirstSpot());
                                 markedSpots.add(newSpot.getFirstSpot());
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
         RowData.Builder builder = rowData.copy();
         builder.setName(rowData.getName() + " Linked").
               setMaxNrSpots(destList.size()).
               setSpotList(destList);
         DataCollectionForm.getInstance().addSpotData(builder);

      } catch (OutOfMemoryError oome) {
         JOptionPane.showMessageDialog(getInstance(), "Out of memory");
      }
   }

   /**
    * Given a list of linked spots, create a single spot entry that will be added to the destination
    * list
    *
    * @param source    - list of spots that all occur around the same pixel and in linked frames
    * @param dest      - list of spots in which each entry represents multiple linked spots
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
      final int n = source.size();

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

      // calculate the sample standard deviation (for x, y, and both)
      double sumx = 0.0;
      double sumy = 0.0;
      for (SpotData spot : source) {
         sumx += (spot.getXCenter() - xCenter) * (spot.getXCenter() - xCenter);
         sumy += (spot.getYCenter() - yCenter) * (spot.getYCenter() - yCenter);
      }
      double sum = sumx + sumy;

      // sample standard deviation
      double sampleWeightedSum = 1.0 / (n - 1) * sum;
      double stdDev = Math.sqrt(sampleWeightedSum);
      double stdDevX = Math.sqrt(1.0 / (n - 1) * sumx);
      double stdDevY = Math.sqrt(1.0 / (n - 1) * sumy);

      sp.setData(intensity, background, xCenter, yCenter, 0.0, width, a, theta, sigma);
      sp.originalFrame_ = source.get(0).getFrame();
      if (!useFrames) {
         sp.originalFrame_ = source.get(0).getSlice();
      }
      sp.nrLinks_ = n;

      sp.addKeyValue(SpotData.Keys.N, n);
      sp.addKeyValue(SpotData.Keys.STDDEV, stdDev);
      sp.addKeyValue(SpotData.Keys.STDDEVX, stdDevX);
      sp.addKeyValue(SpotData.Keys.STDDEVY, stdDevY);

      dest.add(sp);
   }


   /**
    * Function that executes spot linkage.  Goes through a list of spots and looks in every
    * consecutive frames for the closest by spot (at a maximum distance of maxDistance).  If no spot
    * is found, the link is added and the track is added as a row.  Only tracks longer then minNr
    * are created.
    *
    * @param rowData          - input Spot data (obtained through the "Fit" function
    * @param minNr            - track needs to be larger than this or it will not be saved
    * @param nrMissing        - Continue searching for this many frames if no spot was - found.
    * @param maxDistance      - Maximum distance between spot in consecutive frames - if larger, it
    *                         will not be added to the tracks
    * @param minTotalDistance - Minimum distance between first and last point in the track.  Track
    *                         will only be reported if distance is greater than this number.
    * @param combineChannels  - when true, combine track from multiple channels whose average
    *                         position is within maxPairDistance
    * @param maxPairDistance  - parameter only used when combineChannels is true
    * @return Number of tracks that were extracted.
    */
   public static int extractTracks(final RowData rowData, final int minNr,
         final int nrMissing, final double maxDistance, final double minTotalDistance,
         final boolean combineChannels, final double maxPairDistance) {

      int trackNr = 0;
      try {
         ij.IJ.showStatus("Extracting tracks...");

         // maintain active tracks here
         List<Integer> trackIndex;
         Map<List<Integer>, List<TrackData>> trackMap = new HashMap<List<Integer>, List<TrackData>>();
         for (int pos = 1; pos <= rowData.nrPositions_; pos++) {
            for (int ch = 1; ch <= rowData.nrChannels_; ch++) {
               for (int s = 1; s <= rowData.nrSlices_; s++) {
                  trackIndex = Collections.unmodifiableList(Arrays.asList(pos, ch, s));
                  List<TrackData> tracks =
                        new ArrayList<TrackData>();
                  for (int f = 1; f <= rowData.nrFrames_; f++) {
                     List<SpotData> spots = rowData.get(f, s, ch, pos);
                     if (spots != null) {
                        // keep track of spots in this frame added to tracks 
                        List<SpotData> markedSpots = new ArrayList<SpotData>();
                        // go through all tracks to see if they can be extended
                        if (tracks.size() > 0) {
                           NearestPointByData nsp = new NearestPointByData(spots, maxDistance);
                           List<TrackData> removedTracks =
                                 new ArrayList<TrackData>();
                           for (TrackData track : tracks) {
                              SpotData tSpot = track.get(track.size() - 1);
                              // TODO: do we really need a copy here?
                              SpotData newSpot = (SpotData) nsp.findKDWSE(new Point2D.Double(
                                    tSpot.getXCenter(), tSpot.getYCenter()));
                              if (newSpot == null || markedSpots.contains(newSpot)) {
                                 track.addMissing();
                                 if (track.missingMoreThan(nrMissing)) {
                                    // track could not be extended, finalize it
                                    // Write out the track:
                                    if (track.size() > minNr &&
                                          track.get(0).distance(track.get(track.size() - 1))
                                                > minTotalDistance) {
                                       writeTrack(rowData, track.getList(), trackNr);
                                       trackNr++;
                                       // and remove from the list of tracks
                                       // to avoid a concurrent modification exception
                                       // the removal needs to be a two step process
                                       removedTracks.add(track);
                                    }
                                 }
                              } else {
                                 track.resetMissing();
                                 track.add(newSpot);
                                 markedSpots.add(newSpot);
                              }
                           }
                           // second part of removing tracks
                           for (TrackData track : removedTracks) {
                              tracks.remove(track);
                           }
                        }
                        // go through spots and start a new track with any spot 
                        // that was not part of a previous track
                        for (SpotData spot : spots) {
                           if (!markedSpots.contains(spot)) {
                              TrackData track = new TrackData();
                              track.add(spot);
                              tracks.add(track);
                           }
                        }
                     }
                  }

                  // add tracks that made it to the end to destination list 
                  trackMap.put(trackIndex, tracks);

               }
            }
         }

         // take average position of track in first channel
         // if average position of closest track in another channel is within
         // maxPairDistance, combine the tracks 
         if (combineChannels) {
            for (int pos = 1; pos <= rowData.nrPositions_; pos++) {
               for (int s = 1; s <= rowData.nrSlices_; s++) {
                  Map<Integer, List<TrackData>> tracksByChannel =
                        new HashMap<Integer, List<TrackData>>();
                  Map<Integer, NearestPointByData> npsByChannel =
                        new HashMap<Integer, NearestPointByData>();
                  for (int ch = 1; ch <= rowData.nrChannels_; ch++) {
                     trackIndex = Collections.unmodifiableList(Arrays.asList(pos, ch, s));
                     tracksByChannel.put(ch, trackMap.get(trackIndex));
                     npsByChannel.put(ch, new NearestPointByData(
                           trackMap.get(trackIndex), maxPairDistance));
                  }
                  for (TrackData track : tracksByChannel.get(1)) {
                     if (track.size() > minNr
                           && track.get(0).distance(track.get(track.size() - 1))
                           > minTotalDistance) {
                        for (int ch = 2; ch <= rowData.nrChannels_; ch++) {
                           TrackData closestTrack =
                                 (TrackData) npsByChannel.get(ch).findKDWSE(track.getPoint());
                           if (closestTrack != null) {
                              if (closestTrack.size() > minNr
                                    && closestTrack.get(0).distance(
                                    closestTrack.get(closestTrack.size() - 1))
                                    > minTotalDistance) {
                                 track.add(closestTrack);
                                 trackMap.remove(Collections.unmodifiableList(
                                       Arrays.asList(pos, ch, s)));
                              }
                           }
                        }
                     }
                  }
               }
            }
         }

         for (int pos = 1; pos <= rowData.nrPositions_; pos++) {
            for (int ch = 1; ch <= rowData.nrChannels_; ch++) {
               for (int s = 1; s <= rowData.nrSlices_; s++) {
                  trackIndex = Collections.unmodifiableList(Arrays.asList(pos, ch, s));
                  List<TrackData> tracks = trackMap.get(trackIndex);
                  if (tracks != null) {
                     for (TrackData track : tracks) {
                        if (track.size() > minNr
                              && track.get(0).distance(track.get(track.size() - 1))
                              > minTotalDistance) {
                           writeTrack(rowData, track.getList(), trackNr);
                           trackNr++;
                        }
                     }
                  }
               }
            }
         }

         ij.IJ.showStatus("Extracted " + trackNr + " tracks");
      } catch (OutOfMemoryError oome) {
         JOptionPane.showMessageDialog(getInstance(), "Out of memory");
      }

      return trackNr;
   }

   private static void writeTrack(RowData rowData, List<SpotData> track, int trackNr) {
      RowData.Builder builder = rowData.copy();
      builder.setName(rowData.getName() + " Track " + trackNr).
            setNrSlices(1).
            setNrPositions(1).
            setMaxNrSpots(track.size()).
            setIsTrack(true).
            setSpotList(track);
      DataCollectionForm.getInstance().addSpotData(builder);

   }

}
