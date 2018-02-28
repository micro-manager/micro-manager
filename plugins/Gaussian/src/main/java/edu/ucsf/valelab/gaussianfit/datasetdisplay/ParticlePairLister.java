/*
 * Copyright (c) 2015-2017, Regents the University of California
 * Author: Nico Stuurman
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package edu.ucsf.valelab.gaussianfit.datasetdisplay;

import edu.ucsf.valelab.gaussianfit.DataCollectionForm;
import edu.ucsf.valelab.gaussianfit.ResultsTableListener;
import edu.ucsf.valelab.gaussianfit.Terms;
import edu.ucsf.valelab.gaussianfit.data.GsSpotPair;
import edu.ucsf.valelab.gaussianfit.data.SpotData;
import edu.ucsf.valelab.gaussianfit.fitting.FittingException;
import edu.ucsf.valelab.gaussianfit.fitting.Gaussian1DFitter;
import edu.ucsf.valelab.gaussianfit.fitting.P2DFitter;
import edu.ucsf.valelab.gaussianfit.spotoperations.NearestPoint2D;
import edu.ucsf.valelab.gaussianfit.spotoperations.NearestPointByData;
import edu.ucsf.valelab.gaussianfit.utils.CalcUtils;
import edu.ucsf.valelab.gaussianfit.utils.GaussianUtils;
import edu.ucsf.valelab.gaussianfit.utils.ListUtils;
import edu.ucsf.valelab.gaussianfit.utils.NumberUtils;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.Arrow;
import ij.measure.ResultsTable;
import ij.text.TextPanel;
import ij.text.TextWindow;
import java.awt.Color;
import java.awt.Frame;
import java.awt.event.KeyListener;
import java.awt.event.MouseListener;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.apache.commons.math3.exception.TooManyEvaluationsException;
import org.micromanager.internal.MMStudio;
import org.micromanager.internal.utils.ReportingUtils;

/**
 *
 * @author nico
 */
public class ParticlePairLister {

   final private int[] rows_;
   final private Double maxDistanceNm_; //maximum distance in nm for two spots in different
                                        // channels to be considered a pair
   final private Boolean showPairs_;
   final private Boolean showTrack_ = false;  // TODO: remove
   final private Boolean showSummary_;
   final private Boolean showOverlay_;
   final private Boolean p2d_;
   final private Boolean doGaussianEstimate_;
   final private Boolean fitSigmaInP2D_;
   final private Boolean showXYHistogram_;
   final private Boolean useSigmaUserGuess_;
   final private Boolean useVectorDistances_;
   final private Boolean estimateP2DError_;
   final private Boolean useIndividualSigmas_;
   final private Boolean showHistogram_;
   final private Double sigmaUserGuess_;
   

   public static class Builder {

      private int[] rows_;
      private Double maxDistanceNm_; 
      private Boolean showPairs_;
      private Boolean showSummary_;
      private Boolean showOverlay_;
      private Boolean p2d_;
      private Boolean doGaussianEstimate_;
      private Boolean fitSigma_;
      private Boolean showXYHistogram_;
      private Boolean useSigmaUserGuess_;
      private Boolean useVectorDistances_;
      private Boolean estimateP2DError_;
      private Boolean useIndividualSigmas_;
      private Boolean showHistogram_;
      private Double sigmaEstimate_;
       
      
      public ParticlePairLister build() {
         if (useVectorDistances_) {
            useIndividualSigmas_ = false;
         }
         if (fitSigma_) {
            useIndividualSigmas_ = false;
         }
         return new ParticlePairLister(this);
      }

      public Builder rows(int[] rows) {
         rows_ = rows;
         return this;
      }

      public Builder maxDistanceNm(Double maxDistanceNm) {
         maxDistanceNm_ = maxDistanceNm;
         return this;
      }
      
      public Builder showPairs(Boolean showPairs) {
         showPairs_ = showPairs;
         return this;
      }
      
      public Builder showHistogram(Boolean show) {
         showHistogram_ = show;
         return this;
      }
      
      public Builder showXYHistogram(Boolean show) {
         showXYHistogram_ = show;
         return this;
      }

      public Builder showSummary(Boolean showSummary) {
         showSummary_ = showSummary;
         return this;
      }

      public Builder showOverlay(Boolean showOverlay) {
         showOverlay_ = showOverlay;
         return this;
      }

      public Builder p2d(Boolean p2d) {
         p2d_ = p2d;
         return this;
      }

      public Builder doGaussianEstimate(Boolean doGaussianEstimate) {
         doGaussianEstimate_ = doGaussianEstimate;
         return this;
      }

      public Builder fitSigma(Boolean fixSigma) {
         fitSigma_ = fixSigma;
         return this;
      }

      public Builder useSigmaEstimate(Boolean useSigmaEstimate) {
         useSigmaUserGuess_ = useSigmaEstimate;
         return this;
      }
      
      public Builder useVectorDistances(Boolean useVectorDistances) {
         useVectorDistances_ = useVectorDistances;
         return this;
      }

      public Builder sigmaEstimate(Double sigmaEstimate) {
         sigmaEstimate_ = sigmaEstimate;
         return this;
      }

      public Builder useIndividualSigmas(Boolean use) {
         useIndividualSigmas_ = use;
         return this;
      }
      
      public Builder estimateP2DError(Boolean estimateP2DError) {
         estimateP2DError_ = estimateP2DError;
         return this;
      }

   }

   public ParticlePairLister(Builder builder) {
      rows_ = builder.rows_;
      maxDistanceNm_ = builder.maxDistanceNm_;
      showPairs_ = builder.showPairs_;
      showSummary_ = builder.showSummary_;
      showOverlay_ = builder.showOverlay_;
      p2d_ = builder.p2d_;
      doGaussianEstimate_ = builder.doGaussianEstimate_;
      fitSigmaInP2D_ = builder.fitSigma_;
      showXYHistogram_ = builder.showXYHistogram_;
      useSigmaUserGuess_ = builder.useSigmaUserGuess_;
      useVectorDistances_ = builder.useVectorDistances_;
      sigmaUserGuess_ = builder.sigmaEstimate_;
      useIndividualSigmas_ = builder.useIndividualSigmas_;
      estimateP2DError_ = builder.estimateP2DError_;
      showHistogram_ = builder.showHistogram_;
   }

   public Builder copy() {
      return new Builder().
              rows(rows_).
              maxDistanceNm(maxDistanceNm_).
              showPairs(showPairs_).
              showSummary(showSummary_).
              showOverlay(showOverlay_).
              p2d(p2d_).
              doGaussianEstimate(doGaussianEstimate_).
              fitSigma(fitSigmaInP2D_).
              useSigmaEstimate(useSigmaUserGuess_).
              useVectorDistances(useVectorDistances_).
              sigmaEstimate(sigmaUserGuess_).
              showHistogram(showHistogram_).
              showXYHistogram(showXYHistogram_).
              estimateP2DError(estimateP2DError_);
   }

   /**
    * Cycles through the spots of the selected data set and finds the most
    * nearby spot in channel 2. It will list this as a pair if the two spots are
    * within MAXMATCHDISTANCE nm of each other.
    *
    * Once all pairs are found, it will go through all frames and try to build
    * up tracks. If the spot is within MAXMATCHDISTANCE between frames, the code
    * will consider the particle to be identical.
    *
    * All "tracks" of particles will be listed
    *
    * In addition, it will list the average distance, and average distance in x
    * and y for each frame.
    *
    * Needed input variables are set through a builder.
    *
    */
   public void listParticlePairTracks() {

      Runnable doWorkRunnable = new Runnable() {

         @Override
         public void run() {

            final DataCollectionForm dc = DataCollectionForm.getInstance();

            // Show Particle List as linked Results Table
            ResultsTable rt = new ResultsTable();
            rt.reset();
            rt.setPrecision(2);

            // Show Particle Summary as Linked Results Table
            ResultsTable rt2 = new ResultsTable();
            rt2.reset();
            rt2.setPrecision(1);

            // Saves output of P2D fitting            
            ResultsTable rt3 = new ResultsTable();
            rt3.reset();
            rt3.setPrecision(2);

            int rowCounter = 0;
            for (int row : rows_) {
               rowCounter++;
               ij.IJ.showStatus("Creating Pairs for row " + rowCounter);

               Map<Integer, ArrayList<ArrayList<GsSpotPair>>> spotPairsByFrame
                       = new HashMap<Integer, ArrayList<ArrayList<GsSpotPair>>>();

               // index spots by position
               Map<Integer, ArrayList<SpotData>> spotListsByPosition = new HashMap<Integer, ArrayList<SpotData>>();
               // and keep track of the positions that are actually used
               List<Integer> positions = new ArrayList<Integer>();
               for (SpotData spot : dc.getSpotData(row).spotList_) {
                  if (positions.indexOf(spot.getPosition()) == -1) {
                     positions.add(spot.getPosition());
                  }
                  if (spotListsByPosition.get(spot.getPosition()) == null) {
                     spotListsByPosition.put(spot.getPosition(), new ArrayList<SpotData>());
                  }
                  spotListsByPosition.get(spot.getPosition()).add(spot);
               }
               Collections.sort(positions);

               // First go through all frames to find all pairs, organize by position
               for (int pos : positions) {
                  spotPairsByFrame.put(pos, new ArrayList<ArrayList<GsSpotPair>>());

                  for (int frame = 1; frame <= dc.getSpotData(row).nrFrames_; frame++) {
                     // TODO: show correct progress
                     ij.IJ.showProgress(frame, dc.getSpotData(row).nrFrames_);

                     spotPairsByFrame.get(pos).add(new ArrayList<GsSpotPair>());

                     // Get points from both channels as ArrayLists   
                     ArrayList<SpotData> gsCh1 = new ArrayList<SpotData>();
                     ArrayList<SpotData> gsCh2 = new ArrayList<SpotData>();
                     ArrayList<Point2D.Double> xyPointsCh2 = new ArrayList<Point2D.Double>();
                     for (SpotData gs : spotListsByPosition.get(pos)) {
                        if (gs.getFrame() == frame) {
                           if (gs.getChannel() == 1) {
                              gsCh1.add(gs);
                           } else if (gs.getChannel() == 2) {
                              gsCh2.add(gs);
                              Point2D.Double point = new Point2D.Double(
                                      gs.getXCenter(), gs.getYCenter());
                              xyPointsCh2.add(point);
                           }
                        }
                     }

                     if (xyPointsCh2.isEmpty()) {
                        MMStudio.getInstance().alerts().postAlert("No points found error", null,
                                "Pairs function in Localization plugin: no points found in second channel in frame "
                                + frame);
                        continue;
                     }

                     // Find matching points in the two ArrayLists
                     Iterator it2 = gsCh1.iterator();
                     NearestPoint2D np = new NearestPoint2D(xyPointsCh2, maxDistanceNm_);
                     while (it2.hasNext()) {
                        SpotData ch1Spot = (SpotData) it2.next();
                        Point2D.Double pCh1 = new Point2D.Double(
                                ch1Spot.getXCenter(), ch1Spot.getYCenter());
                        Point2D.Double pCh2 = np.findKDWSE(pCh1);
                        if (pCh2 != null) {
                           // find this point in the ch2 spot list
                           SpotData ch2Spot = null;
                           for (int i = 0; i < gsCh2.size() && ch2Spot == null; i++) {
                              if (pCh2.x == gsCh2.get(i).getXCenter()
                                      && pCh2.y == gsCh2.get(i).getYCenter()) {
                                 ch2Spot = gsCh2.get(i);
                              }
                           }
                           if (ch2Spot != null) {
                              GsSpotPair pair = new GsSpotPair(ch1Spot, ch2Spot, pCh1, pCh2);
                              spotPairsByFrame.get(pos).get(frame - 1).add(pair);
                           } else {
                              // this should never happen!
                              System.out.println("Failed to find spot");
                           }
                        }
                     }
                  }
               }

               if (showPairs_ ) {
                  ResultsTable pairTable = new ResultsTable();
                  pairTable.setPrecision(2);
                  for (int pos : positions) {
                     for (ArrayList<GsSpotPair> pairList : spotPairsByFrame.get(pos)) {
                        for (GsSpotPair pair : pairList) {
                           pairTable.incrementCounter();
                           pairTable.addValue(Terms.FRAME, pair.getFirstSpot().getFrame());
                           pairTable.addValue(Terms.SLICE, pair.getFirstSpot().getSlice());
                           pairTable.addValue(Terms.CHANNEL, pair.getFirstSpot().getSlice());
                           pairTable.addValue(Terms.POSITION, pair.getFirstSpot().getPosition());
                           pairTable.addValue(Terms.XPIX, pair.getFirstSpot().getX());
                           pairTable.addValue(Terms.YPIX, pair.getFirstSpot().getY());
                           pairTable.addValue("X1", pair.getFirstSpot().getXCenter());
                           pairTable.addValue("Y1", pair.getFirstSpot().getYCenter());
                           pairTable.addValue("Sigma1", pair.getFirstSpot().getSigma());
                           pairTable.addValue("X2", pair.getSecondSpot().getXCenter());
                           pairTable.addValue("Y2", pair.getSecondSpot().getYCenter());
                           pairTable.addValue("Sigma2", pair.getSecondSpot().getSigma());
                           double d2 = NearestPoint2D.distance2(pair.getFirstPoint(), pair.getSecondPoint());
                           double d = Math.sqrt(d2);
                           pairTable.addValue("Distance", d);
                           pairTable.addValue("Orientation (sine)",
                                   NearestPoint2D.orientation(pair.getFirstPoint(), pair.getSecondPoint()));
                        }
                     }
                  }
                  //  show Pairs panel and attach listener
                  TextPanel tp;
                  TextWindow win;

                  String rtName = "Pairs found in " + dc.getSpotData(row).getName();
                  pairTable.show(rtName);
                  ImagePlus siPlus = ij.WindowManager.getImage(dc.getSpotData(row).title_);
                  Frame frame = WindowManager.getFrame(rtName);
                  if (frame != null && frame instanceof TextWindow && siPlus != null) {
                     win = (TextWindow) frame;
                     tp = win.getTextPanel();

                     // TODO: the following does not work, there is some voodoo going on here
                     for (MouseListener ms : tp.getMouseListeners()) {
                        tp.removeMouseListener(ms);
                    }
                     for (KeyListener ks : tp.getKeyListeners()) {
                        tp.removeKeyListener(ks);
                     }

                     ResultsTableListener myk = new ResultsTableListener(
                             dc.getSpotData(row).dw_, siPlus,
                             pairTable, win, dc.getSpotData(row).halfSize_);
                     tp.addKeyListener(myk);
                     tp.addMouseListener(myk);
                     frame.toFront();
                  }
               }
               
               // We have all pairs, assemble in tracks
               ij.IJ.showStatus("Analyzing pairs for row " + rowCounter);

               ArrayList<ArrayList<GsSpotPair>> tracks = new ArrayList<ArrayList<GsSpotPair>>();

               for (int pos : positions) {
                  // prepare NearestPoint objects to speed up finding closest pair 
                  ArrayList<NearestPointByData> npsp = new ArrayList<NearestPointByData>();
                  for (int frame = 1; frame <= dc.getSpotData(row).nrFrames_; frame++) {
                     npsp.add(new NearestPointByData(
                             spotPairsByFrame.get(pos).get(frame - 1), maxDistanceNm_));
                  }

                  for (int firstFrame = 1; firstFrame <= dc.getSpotData(row).nrFrames_; firstFrame++) {
                     Iterator<GsSpotPair> iSpotPairs = spotPairsByFrame.get(pos).get(firstFrame - 1).iterator();
                     while (iSpotPairs.hasNext()) {
                        GsSpotPair spotPair = iSpotPairs.next();
                        // for now, we only start tracks at frame number 1
                        if (!spotPair.partOfTrack()) {
                           for (int frame = firstFrame; frame <= dc.getSpotData(row).nrFrames_; frame++) {
                              if (!spotPair.partOfTrack() && spotPair.getFirstSpot().getFrame() == frame) {
                                 ArrayList<GsSpotPair> track = new ArrayList<GsSpotPair>();
                                 track.add(spotPair);
                                 int searchInFrame = frame + 1;
                                 while (searchInFrame <= dc.getSpotData(row).nrFrames_) {
                                    GsSpotPair newSpotPair = (GsSpotPair) npsp.get(searchInFrame - 1).findKDWSE(
                                            new Point2D.Double(spotPair.getFirstPoint().getX(),
                                                    spotPair.getFirstPoint().getY()));
                                    if (newSpotPair != null && !newSpotPair.partOfTrack()) {
                                       newSpotPair.useInTrack(true);
                                       spotPair = newSpotPair;
                                       track.add(spotPair);
                                    }
                                    searchInFrame++;
                                 }
                                 tracks.add(track);
                              }
                           }
                        }
                     }
                  }                  
               }
               
               if (tracks.isEmpty()) {
                  MMStudio.getInstance().alerts().postAlert("P2D fit error", 
                            null, "ID: " + dc.getSpotData(row).ID_ + 
                            ", No Pairs found");
               }

               Iterator<ArrayList<GsSpotPair>> itTracks = tracks.iterator();
               int spotId = 0;
               while (itTracks.hasNext()) {
                  ArrayList<GsSpotPair> track = itTracks.next();
                  Iterator<GsSpotPair> itTrack = track.iterator();
                  while (itTrack.hasNext()) {
                     GsSpotPair spot = itTrack.next();
                     rt.incrementCounter();
                     rt.addValue("Spot ID", spotId);
                     rt.addValue(Terms.FRAME, spot.getFirstSpot().getFrame());
                     rt.addValue(Terms.SLICE, spot.getFirstSpot().getSlice());
                     rt.addValue(Terms.CHANNEL, spot.getFirstSpot().getChannel());
                     rt.addValue(Terms.POSITION, spot.getFirstSpot().getPosition());
                     rt.addValue(Terms.XPIX, spot.getFirstSpot().getX());
                     rt.addValue(Terms.YPIX, spot.getFirstSpot().getY());
                     double distance = Math.sqrt(
                             NearestPoint2D.distance2(spot.getFirstPoint(), spot.getSecondPoint()));
                     rt.addValue("Distance", distance);

                     rt.addValue("sigma1", spot.getFirstSpot().getSigma());
                     rt.addValue("sigma2", spot.getSecondSpot().getSigma());
                     double distanceStdDev = CalcUtils.stdDev(
                                   spot.getFirstPoint().x, spot.getSecondPoint().x,
                                   spot.getFirstPoint().y, spot.getSecondPoint().y,
                                   spot.getFirstSpot().getSigma(),
                                   spot.getFirstSpot().getSigma(),
                                   spot.getFirstSpot().getSigma(),
                                   spot.getFirstSpot().getSigma() );
                     rt.addValue("stdDev-distance", distanceStdDev);
                     
                     rt.addValue("Orientation (sine)",
                             NearestPoint2D.orientation(spot.getFirstPoint(), spot.getSecondPoint()));
                  }
                  spotId++;
               }

               TextPanel tp;
               TextWindow win;
               String rtName = dc.getSpotData(row).getName() + " Particle List";
               
               if (showTrack_) {
                  rt.show(rtName);
                  ImagePlus siPlus = ij.WindowManager.getImage(dc.getSpotData(row).title_);
                  Frame frame = WindowManager.getFrame(rtName);
                  if (frame != null && frame instanceof TextWindow && siPlus != null) {
                     win = (TextWindow) frame;
                     tp = win.getTextPanel();

                     // TODO: the following does not work, there is some voodoo going on here
                     for (MouseListener ms : tp.getMouseListeners()) {
                        tp.removeMouseListener(ms);
                     }
                     for (KeyListener ks : tp.getKeyListeners()) {
                        tp.removeKeyListener(ks);
                     }

                     ResultsTableListener myk = new ResultsTableListener(
                             dc.getSpotData(row).dw_, siPlus,
                             rt, win, dc.getSpotData(row).halfSize_);
                     tp.addKeyListener(myk);
                     tp.addMouseListener(myk);
                     frame.toFront();
                  }
               }
              
               ImagePlus siPlus = ij.WindowManager.getImage(dc.getSpotData(row).title_);
               if (showOverlay_) {
                  if (siPlus != null && siPlus.getOverlay() != null) {
                     siPlus.getOverlay().clear();
                  }
                  Arrow.setDefaultWidth(0.5);
               }

               itTracks = tracks.iterator();
               spotId = 0;
               List<Double> avgVectDistances = new ArrayList<Double>(tracks.size());
               List<Double> allDistances = new ArrayList<Double>(
                       tracks.size() * dc.getSpotData(row).nrFrames_);
               List<Double> allSigmas = new ArrayList<Double>(
                       tracks.size() * dc.getSpotData(row).nrFrames_);
               List<Double> sigmasFirstSpot  = new ArrayList<Double>(
                       tracks.size() * dc.getSpotData(row).nrFrames_);
               List<Double> sigmasSecondSpot = new ArrayList<Double>(
                       tracks.size() * dc.getSpotData(row).nrFrames_);
               while (itTracks.hasNext()) {
                  ArrayList<GsSpotPair> track = itTracks.next();
                  ArrayList<Double> distances = new ArrayList<Double>();
                  ArrayList<Double> orientations = new ArrayList<Double>();
                  ArrayList<Double> xDiff = new ArrayList<Double>();
                  ArrayList<Double> yDiff = new ArrayList<Double>();
                  ArrayList<Double> sigmas = new ArrayList<Double>();
                  ArrayList<Point2D.Double> firstPoints = new ArrayList<Point2D.Double>();
                  ArrayList<Point2D.Double> secondPoints = new ArrayList<Point2D.Double>();
                  for (GsSpotPair pair : track) {
                     double distance = Math.sqrt(
                             NearestPoint2D.distance2(pair.getFirstPoint(), pair.getSecondPoint()));
                     distances.add(distance);
                     allDistances.add(distance);
                     orientations.add(NearestPoint2D.orientation(pair.getFirstPoint(),
                             pair.getSecondPoint()));
                     xDiff.add(pair.getFirstPoint().getX() - pair.getSecondPoint().getX());
                     yDiff.add(pair.getFirstPoint().getY() - pair.getSecondPoint().getY());
                     double sigma = Math.sqrt(
                             pair.getFirstSpot().getSigma()
                             * pair.getFirstSpot().getSigma()
                             + pair.getSecondSpot().getSigma()
                             * pair.getSecondSpot().getSigma());
                     sigmas.add(sigma);
                     allSigmas.add(sigma);
                     sigmasFirstSpot.add(pair.getFirstSpot().getSigma());
                     sigmasSecondSpot.add(pair.getSecondSpot().getSigma());
                     firstPoints.add(pair.getFirstPoint());
                     secondPoints.add(pair.getSecondPoint());
                  }
                  GsSpotPair pair = track.get(0);
                  rt2.incrementCounter();
                  rt2.addValue("Row ID", dc.getSpotData(row).ID_);
                  rt2.addValue("Spot ID", spotId);
                  rt2.addValue(Terms.FRAME, pair.getFirstSpot().getFrame());
                  rt2.addValue(Terms.SLICE, pair.getFirstSpot().getSlice());
                  rt2.addValue(Terms.CHANNEL, pair.getFirstSpot().getSlice());
                  rt2.addValue(Terms.POSITION, pair.getFirstSpot().getPosition());
                  rt2.addValue(Terms.XPIX, pair.getFirstSpot().getX());
                  rt2.addValue(Terms.YPIX, pair.getFirstSpot().getY());
                  rt2.addValue("n", track.size());
                  
                  Point2D.Double avgPositionFirstSpot = ListUtils.avgXYList(firstPoints);
                  Point2D.Double avgPositionSecondSpot = ListUtils.avgXYList(secondPoints);
                  double stdDevFirstSpot = ListUtils.stdDevXYList(firstPoints, avgPositionFirstSpot);
                  double stdDevSecondSpot = ListUtils.stdDevXYList(secondPoints, avgPositionSecondSpot);
                  
                  rt2.addValue("StdDev-1", stdDevFirstSpot);
                  rt2.addValue("StdDev-2", stdDevSecondSpot);
                  
                  // Average of Euclidean distances in this strack
                  double avg = ListUtils.listAvg(distances);
                  rt2.addValue("Distance-Avg", avg);
                  // Standard Deviation of Euclidean distances in this track
                  double std = ListUtils.listStdDev(distances, avg);
                  rt2.addValue("Distance-StdDev", std);
                  // Average of weighted sigmas: Sqrt(sigma1(^2) + sigma2(^2) in this track
                  double avgSigma = ListUtils.listAvg(sigmas);
                  rt2.addValue("Sigma", avgSigma);
                  double oAvg = ListUtils.listAvg(orientations);
                  rt2.addValue("Orientation-Avg", oAvg);
                  rt2.addValue("Orientation-StdDev",
                          ListUtils.listStdDev(orientations, oAvg));

                  // average x position differential
                  double xDiffAvg = ListUtils.listAvg(xDiff);
                  // average y position differential
                  double yDiffAvg = ListUtils.listAvg(yDiff);
                  // Std Dev. in x position differentials
                  double xDiffAvgStdDev = ListUtils.listStdDev(xDiff, xDiffAvg);
                  // Std. Dev. in y position differentials
                  double yDiffAvgStdDev = ListUtils.listStdDev(yDiff, yDiffAvg);
                  // Distance based on x and y position differentials
                  double vectAvg = Math.sqrt(
                          (xDiffAvg * xDiffAvg) + (yDiffAvg * yDiffAvg));
                  avgVectDistances.add(vectAvg);
                  rt2.addValue("Dist.Vect.Avg", vectAvg);
                  // Std Dev. based on x and y differentials
                  rt2.addValue("Dist.Vect.StdDev", Math.sqrt(
                          (xDiffAvgStdDev * xDiffAvgStdDev)
                          + (yDiffAvgStdDev * yDiffAvgStdDev)));

                  if (showOverlay_) {
                     /* draw arrows in overlay */
                     double mag = 100.0;  // factor that sets magnification of the arrow
                     double factor = mag * 1 / dc.getSpotData(row).pixelSizeNm_;  // factor relating mad and pixelSize
                     int xStart = track.get(0).getFirstSpot().getX();
                     int yStart = track.get(0).getFirstSpot().getY();

                     Arrow arrow = new Arrow(xStart, yStart,
                             xStart + (factor * xDiffAvg),
                             yStart + (factor * yDiffAvg));
                     arrow.setHeadSize(3);
                     arrow.setOutline(false);
                     if (siPlus != null && siPlus.getOverlay() == null) {
                        siPlus.setOverlay(arrow, Color.yellow, 1, Color.yellow);
                     } else if (siPlus != null && siPlus.getOverlay() != null) {
                        siPlus.getOverlay().add(arrow);
                     }
                  }

                  spotId++;
               }
               if (showOverlay_) {
                  if (siPlus != null) {
                     siPlus.setHideOverlay(false);
                  }
               }
               
               if (showXYHistogram_) {
                  List<Double> xDiff = new ArrayList<Double>();
                  List<Double> yDiff = new ArrayList<Double>();
                  for (int pos : positions) {
                     for (ArrayList<GsSpotPair> pairList : spotPairsByFrame.get(pos)) {
                        for (GsSpotPair pair : pairList) {
                           xDiff.add(pair.getFirstPoint().getX() - 
                                   pair.getSecondPoint().getX());
                           yDiff.add(pair.getFirstPoint().getY() -
                                   pair.getSecondPoint().getY());
                        }
                     }
                  }
                   try {
                     double[] xDiffArray = ListUtils.toArray(xDiff);
                     double[] xGaussian = fitGaussianToData(xDiffArray, 
                             -maxDistanceNm_, 
                             maxDistanceNm_);
                        GaussianUtils.plotGaussian("Gaussian fit of: "
                             + dc.getSpotData(row).getName() + "-X distances",
                             xDiffArray, 
                             -5.0 * xGaussian[1], 
                             5.0 * xGaussian[1], 
                             xGaussian);
                     double[] yDiffArray = ListUtils.toArray(yDiff);
                     double[] yGaussian = fitGaussianToData(yDiffArray, 
                              -maxDistanceNm_, 
                             maxDistanceNm_);
                        GaussianUtils.plotGaussian("Gaussian fit of: "
                             + dc.getSpotData(row).getName() + "-Y distances",
                             yDiffArray, 
                             -5.0 * yGaussian[1], 
                             5.0 * yGaussian[1], 
                             yGaussian);
                     final double combinedError = Math.sqrt(
                             xGaussian[0] * xGaussian[0] + 
                             yGaussian[0] * yGaussian[0]);
                     ij.IJ.log(dc.getSpotData(row).getName() + " X-error: " +
                             NumberUtils.doubleToDisplayString(xGaussian[0], 3) + 
                             "nm, Y-error: " + 
                             NumberUtils.doubleToDisplayString(yGaussian[0],3) + 
                             "nm, combined error: " + 
                             NumberUtils.doubleToDisplayString(combinedError, 3) +
                             "nm.");
                  } catch (FittingException ex) {
                     ReportingUtils.showError("Failed to fit Gaussian, try decreasing the Maximum Distance value");
                  }
               }

               if (showSummary_) {
                  rtName = dc.getSpotData(row).getName() + " Particle Summary";
                  rt2.show(rtName);
                  siPlus = ij.WindowManager.getImage(dc.getSpotData(row).title_);
                  Frame frame = WindowManager.getFrame(rtName);
                  if (frame != null && frame instanceof TextWindow && siPlus != null) {
                     win = (TextWindow) frame;
                     tp = win.getTextPanel();

                     // TODO: the following does not work, there is some voodoo going on here
                     for (MouseListener ms : tp.getMouseListeners()) {
                        tp.removeMouseListener(ms);
                     }
                     for (KeyListener ks : tp.getKeyListeners()) {
                        tp.removeKeyListener(ks);
                     }

                     ResultsTableListener myk = new ResultsTableListener(
                             dc.getSpotData(row).dw_, siPlus,
                             rt2, win, dc.getSpotData(row).halfSize_);
                     tp.addKeyListener(myk);
                     tp.addMouseListener(myk);
                     frame.toFront();
                  }
               }

               double[] gResult = null;
               double[] avgVectDistancesAsDouble;
               if (doGaussianEstimate_ || (p2d_ && useVectorDistances_) ) {
                  // fit vector distances with gaussian function and plot
                  try {
                     avgVectDistancesAsDouble = ListUtils.toArray(avgVectDistances);
                     gResult = fitGaussianToData(avgVectDistancesAsDouble, 0.0, 
                             maxDistanceNm_);
                     if (doGaussianEstimate_ && showHistogram_) {
                        GaussianUtils.plotGaussian("Gaussian fit of: "
                             + dc.getSpotData(row).getName() + " distances",
                             avgVectDistancesAsDouble, 0.0, maxDistanceNm_, gResult);
                     }
                  } catch (FittingException ex) {
                     ReportingUtils.showError("Gaussian fit failed.  Try decresing the Maximum Distance value");
                  }

               }

               List<Double> distancesToUse = allDistances;
               if (useVectorDistances_) {
                  distancesToUse = avgVectDistances;
               }

               if (p2d_ && distancesToUse.size() > 0) {
                  double[] d = new double[distancesToUse.size()];
                  for (int j = 0; j < distancesToUse.size(); j++) {
                     d[j] = distancesToUse.get(j);
                  }
                  double[] sigmas = new double[distancesToUse.size()];
                  if (useIndividualSigmas_) {
                     for (int j = 0; j < allSigmas.size(); j++) {
                        sigmas[j] = allSigmas.get(j);
                     }                     
                  }
                 
                  P2DFitter p2df = new P2DFitter(d, sigmas,
                           fitSigmaInP2D_ || useVectorDistances_, 
                           maxDistanceNm_,
                           useIndividualSigmas_);
                  double distMean = ListUtils.listAvg(distancesToUse);
                  double distStd = sigmaUserGuess_;
                  if (fitSigmaInP2D_ || !useSigmaUserGuess_) {
                     // how do we best estimate sigma? Stefan thinks that the 
                     // localization errors themselves have an uncertainty that 
                     // we should account for. Simulations show this is slightly 
                     // better than just using the average of the quadratic 
                     // addition of sigmas of individual spot pairs. 
                     // See the upcoming manuscript:
                     double sfsAvg = ListUtils.listAvg(sigmasFirstSpot);
                     double sSsAvg = ListUtils.listAvg(sigmasSecondSpot);
                     double sfsStdDev = ListUtils.listStdDev(sigmasFirstSpot, sfsAvg);
                     double sSsStdDev = ListUtils.listStdDev(sigmasSecondSpot, sSsAvg);
                     distStd = Math.sqrt(sfsAvg * sfsAvg  +  sSsAvg * sSsAvg  + 
                             sfsStdDev * sfsStdDev  +  sSsStdDev * sSsStdDev);
                  }
                  if (gResult != null && gResult.length == 2 && useVectorDistances_){
                     p2df.setStartParams(gResult[0], gResult[1]);
                  } else {
                     p2df.setStartParams(distMean, distStd);
                  }

                  try {
                     double[] p2dfResult = p2df.solve();
                     // Confidence interval calculation as in matlab code by Stirling Churchman
                     double mu = p2dfResult[0];
                     double sigma = distStd;
                     if (fitSigmaInP2D_ || useVectorDistances_) {
                        sigma = p2dfResult[1];
                     }
                     double sigmaRange = 4.0 * sigma / Math.sqrt(d.length);
                     double resolution = 0.001 * sigma;
                     double[] distances;
                     distances = p2df.getDistances(mu - sigmaRange, resolution, mu + sigmaRange);
                     double[] logLikelihood = p2df.logLikelihood(p2dfResult, distances);

                     // Uncomment the following to plot loglikelihood
                     // XYSeries data = new XYSeries("distances(nm)");
                     // for (int i = 0; i < distances.length && i < logLikelihood.length; i++) {
                     //    data.add(distances[i], logLikelihood[i]);
                     // }
                     // GaussianUtils.plotData("Log Likelihood for " + dc.getSpotData(row).getName(), 
                     //                 data, "Distance (nm)", "Likelihood", 100, 100);
                     int indexOfMaxLogLikelihood = CalcUtils.maxIndex(logLikelihood);
                     int[] halfMax = CalcUtils.indicesToValuesClosest(logLikelihood,
                             logLikelihood[indexOfMaxLogLikelihood] - 0.5);
                     double dist1 = distances[halfMax[0]];
                     double dist2 = distances[halfMax[1]];
                     double lowConflim = mu - dist1;
                     double highConflim = dist2 - mu;
                     if (lowConflim < 0.0) {
                        lowConflim = mu - dist2;
                        highConflim = dist1 - mu;
                     }
                     String msg1 = "P2D fit for " + dc.getSpotData(row).getName();
                     String msg2 = "n = " + distancesToUse.size() + ", mu = "
                             + NumberUtils.doubleToDisplayString(mu, 2)
                             + " - "
                             + NumberUtils.doubleToDisplayString(lowConflim, 2)
                             + " + "
                             + NumberUtils.doubleToDisplayString(highConflim, 2)
                             + "  nm, sigma = "
                             + NumberUtils.doubleToDisplayString(sigma, 2)
                             + " nm, ";
                     MMStudio.getInstance().alerts().postAlert(msg1, null, msg2);

                     MMStudio.getInstance().alerts().postAlert("Gaussian distribution for "
                             + dc.getSpotData(row).getName(),
                             null,
                             "n = " + distancesToUse.size()
                             + ", avg = "
                             + NumberUtils.doubleToDisplayString(distMean, 2)
                             + " nm, std = "
                             + NumberUtils.doubleToDisplayString(distStd, 2) + " nm");

                     // plot function and histogram
                     double[] muSigma = {p2dfResult[0], sigma};
                     if (fitSigmaInP2D_) {
                        muSigma = p2dfResult;
                     }
                     if (showHistogram_) {
                        GaussianUtils.plotP2D("P2D fit of: " + 
                                 dc.getSpotData(row).getName() + " distances",
                                 d, maxDistanceNm_, muSigma);
                     }

                     // The following is used to output results in a machine readable fashion
                     // Uncomment when needed:
                     rt3.incrementCounter();
                     rt3.addValue("Max. Dist.", maxDistanceNm_);
                     rt3.addValue("File", dc.getSpotData(row).getName());
                     String useVect = useVectorDistances_ ? "yes" : "no";
                     rt3.addValue("Vect. Dist.", useVect);
                     String fittedSigma = fitSigmaInP2D_ ? "yes" : "no";
                     rt3.addValue("Fit Sigma", fittedSigma);
                     String sigmaFromData = useSigmaUserGuess_ || fitSigmaInP2D_ ? "no" : "yes";
                     rt3.addValue("Sigma from data", sigmaFromData);
                     rt3.addValue("n", distancesToUse.size());
                     rt3.addValue("Frames", dc.getSpotData(row).nrFrames_);
                     rt3.addValue("Positions", dc.getSpotData(row).nrPositions_);
                     rt3.addValue("mu", mu);
                     rt3.addValue("mu-lowConf", lowConflim);
                     rt3.addValue("mu-highConf", highConflim);
                     rt3.addValue("sigma", muSigma[1]);
                     rt3.addValue("mean", distMean);
                     rt3.addValue("std", distStd);
                     if (gResult != null) {
                        rt3.addValue("Gaussian-center", gResult[0]);
                        rt3.addValue("Gaussian-std", gResult[1]);
                     }
                     
                     
                     if (estimateP2DError_) {
                        // use bootstrapping to estimate the error
                        final int maxRepeats = 10000;
                        final double maxErrorFrac = 0.001;  // 0.1 % error allowed
                        final double checkEach = 50;  // check Each x runs 
                        
                        final int size = distancesToUse.size();
                        List<Double> bootsTrapMus = new ArrayList<Double>();
                        double[] s = new double[distancesToUse.size()];
                        boolean done = false;
                        double lastMu = -1.0;
                        double lastSem = -1.0;
                        int test = 0;
                        int randomIndex;
                        while (test < maxRepeats && !done) {
                           // create a new data set, same size as original
                           // by randomly drawing from original distances
                           for (int j = 0; j < size; j++) {
                              randomIndex = (int) (Math.random() * size);
                              d[j] = distancesToUse.get( randomIndex );
                              s[j] = allSigmas.get(randomIndex);
                           }
                           p2df = new P2DFitter(d, s,
                                   fitSigmaInP2D_ || useVectorDistances_, 
                                   maxDistanceNm_,
                                   useIndividualSigmas_ );
                           distMean = ListUtils.avg(d);
                           distStd = sigmaUserGuess_;
                           if (fitSigmaInP2D_ || !useSigmaUserGuess_) {
                              distStd = ListUtils.avg(s);
                           }
                           if (useVectorDistances_) {
                              gResult = fitGaussianToData(d, 0.0, maxDistanceNm_);
                              p2df.setStartParams(gResult[0], gResult[1]);
                           } else {
                              p2df.setStartParams(distMean, distStd);
                           }
                 
                           p2dfResult = p2df.solve();
                           
                           bootsTrapMus.add(p2dfResult[0]);
                           
                           if (test % checkEach == 0  && test != 0) {
                              double cmu = ListUtils.listAvg(bootsTrapMus);
                              double sem = ListUtils.listStdDev(bootsTrapMus, cmu);
                              if (lastMu > 0.0 && 
                                      Math.abs(cmu - lastMu) / cmu < maxErrorFrac &&
                                      Math.abs(sem - lastSem) / sem < maxErrorFrac) {
                                 done = true;
                              }
                              lastMu = cmu;
                              lastSem = sem;
                              ij.IJ.showProgress(test, maxRepeats);
                           }
                           test++;
                        }
                        
                        double realMu = ListUtils.listAvg(bootsTrapMus);
                        double finalSem = ListUtils.listStdDev(bootsTrapMus, realMu);
                        
                        rt3.addValue("BootsTrap mu", realMu);
                        rt3.addValue("BootsTrap sem", finalSem);
                        rt3.addValue("BootsTrap nrTries", --test);
                        
                     }
                     rt3.show("P2D Summary");

                  } catch (FittingException fe) {
                     String msg =  "ID: " + dc.getSpotData(row).ID_ + 
                             ", Failed to fit p2d function";
                     MMStudio.getInstance().alerts().postAlert("P2D fit error", 
                             null, msg);
                     if (row == rows_[rows_.length - 1]) {
                        ReportingUtils.showError(msg);
                     }
                  } catch (TooManyEvaluationsException tmee) {  
                     String msg = "ID: " + dc.getSpotData(row).ID_ + 
                             ", Too many evaluations while fitting";
                     MMStudio.getInstance().alerts().postAlert("P2D fit error", 
                             null, msg);
                     if (row == rows_[rows_.length - 1]) {
                        ReportingUtils.showError(msg);
                     }
                  }
               }
               ij.IJ.showProgress(100.0);
               ij.IJ.showStatus("Done listing pairs");

            }
         }
      };

      
      (new Thread(doWorkRunnable)).start();
      
   }

   /**
    * Fits a list of numbers to a Gaussian function using Maximum Likelihood
    *
    * @param input
    * @param max
    * @param min
    * @return fitresult, double[0] is mu, double[1] is sigma
    * @throws FittingException
    */
   public static double[] fitGaussianToData(final double[] input, 
           final double min, final double max) throws FittingException {
      // fit vector distances with gaussian function

      Gaussian1DFitter gf = new Gaussian1DFitter(input, max);
      gf.setLowerBound(min);
      double avg = ListUtils.avg(input);
      gf.setStartParams(avg, ListUtils.stdDev(input, avg));
      return gf.solve();
   }

}