///////////////////////////////////////////////////////////////////////////////
//FILE:          PointAndShootAnalyzer.java
//PROJECT:       Micro-Manager  
//SUBSYSTEM:     PointAndShootAnalyzer plugin
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nico Stuurman
//
// COPYRIGHT:    University of California, San Francisco 2018
//
// LICENSE:      This file is distributed under the BSD license.
//               License text is included with the source distribution.
//
//               This file is distributed in the hope that it will be useful,
//               but WITHOUT ANY WARRANTY; without even the implied warranty
//               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.

package org.micromanager.pointandshootanalysis;

import boofcv.alg.filter.binary.BinaryImageOps;
import boofcv.alg.filter.binary.Contour;
import boofcv.alg.filter.binary.GThresholdImageOps;
import boofcv.alg.filter.blur.BlurImageOps;
import boofcv.alg.misc.GImageBandMath;
import boofcv.alg.misc.GPixelMath;
import boofcv.concurrency.BoofConcurrency;
import boofcv.core.image.ConvertImage;
import boofcv.struct.ConnectRule;
import boofcv.struct.image.GrayF32;
import boofcv.struct.image.GrayS32;
import boofcv.struct.image.GrayU16;
import boofcv.struct.image.GrayU8;
import boofcv.struct.image.ImageGray;
import boofcv.struct.image.Planar;
import georegression.struct.point.Point2D_I32;
import georegression.struct.shapes.Rectangle2D_I32;
import java.awt.Point;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.jfree.data.xy.XYSeries;
import org.micromanager.PropertyMap;
import org.micromanager.Studio;
import org.micromanager.data.Coords;
import org.micromanager.data.DataProvider;
import org.micromanager.data.Image;
import org.micromanager.data.Metadata;
import org.micromanager.display.DataViewer;
import org.micromanager.display.DisplayWindow;
import org.micromanager.imageprocessing.BoofCVUtils;
import org.micromanager.pointandshootanalysis.algorithm.ContourStats;
import org.micromanager.pointandshootanalysis.data.PASData;
import org.micromanager.pointandshootanalysis.data.PASFrameSet;
import org.micromanager.pointandshootanalysis.data.ParticleData;
import org.micromanager.pointandshootanalysis.data.Terms;
import org.micromanager.pointandshootanalysis.display.Overlay;
import org.micromanager.pointandshootanalysis.display.WidgetSettings;
import org.micromanager.pointandshootanalysis.plot.PlotUtils;
import org.micromanager.pointandshootanalysis.utils.ListUtils;

// Imports for MMStudio internal packages
// Plugins should not access internal packages, to ensure modularity and
// maintainability. However, this plugin code is older than the current
// MMStudio API, so it still uses internal classes and interfaces. New code
// should not imitate this practice.
import org.micromanager.internal.utils.imageanalysis.BoofCVImageConverter;

/**
 *
 * @author nico
 */
public class PointAndShootAnalyzer implements Runnable {

   final static double DIST_UNCERTAINTY = 5.0; // in pixels
   
   final private Studio studio_;
   final private PointAndShootDialog psd_;
   final private PropertyMap settings_;
   final Map<String, Point> coordinates_;
   /* max distance in pixels from the expected position
    * if more, we will reject the bleach spot.
    * May need to be changed by the user
   */
   final private static int MAXDISTANCE = 10; 
   // final private int findMinFramesBefore_ = 5; // frames before user clicked PAS. 
   /*
     Do a minimum projection from the frame where we expect the bleach up to 
     this number of frames after.  The bleach is located from the ratio of the 
     minimum and average projection.
     If too large, the particle may move causing abberant minima to be detected.
     May need to be changed by the user 
   */
   final private int findMinFramesAfter_ = 10;  
   final private int roiSize_ = 64;  // keep at factor of 2 to enable FFT 
   final private int halfROISize_ = roiSize_ / 2;
   final private int roiWidth_ = roiSize_;  // may need to changed by user
   final private int roiHeight_ = roiSize_;  // may need to changed by user
   
   // final private boolean continueBleachSpotTracking_ = true; 
   //final private int nrFramesToMeasureBleachToParticleVector_ = 20; 

   public PointAndShootAnalyzer(Studio studio, PropertyMap settings, PointAndShootDialog psd) {
      studio_ = studio;
      settings_ = settings;
      psd_ = psd;
      coordinates_ = new HashMap<>();
   }

   @Override
   public void run() {

      final Map<Instant, Point> datedCoordinates = new TreeMap<>();
      final Map<Integer, Instant> frameTimeStamps = new TreeMap<>();

      // Read variables provided by UI from profile
      String fileName = settings_.getString(Terms.LOCATIONSFILENAME, "");
      final int bleachSpotRadius = settings_.getInteger(Terms.RADIUS, 3);
      final int findMinFramesBefore = settings_.getInteger(Terms.NRFRAMESBEFORE, 2);
      // final int nrFramesAfter = settings_.getInteger(Terms.NRFRAMESAFTER, 200);
      final int maxDistance = settings_.getInteger(Terms.MAXDISTANCE, 3);
      final int cameraOffset = settings_.getInteger(Terms.CAMERAOFFSET, 100);
      // Even when we no longer
      // can measure a bleach spot, continue tracking it based on previous position relative
      // to particle centroid
      final boolean continueBleachSpotTracking = settings_.getBoolean(Terms.FIXBLEACHINPARTICLE, true);
      final int nrFramesToMeasureBleachToParticleVector = settings_.getInteger(Terms.NRFRAMESTOFIXBLEACH, 20);

      File f = new File(fileName);
      if (!f.exists()) {
         studio_.logs().showError("File " + f.getName() + " does not exist");
         return;
      }
      if (!f.canRead()) {
         studio_.logs().showError("File " + f.getName() + " is not readable");
         return;
      }

      try {
         // this thing is ugly.  It depends on side effects...
         Consumer<String> action = new pointAndShootParser();
         try (Stream<String> fileLines = Files.lines(f.toPath())) {
            fileLines.forEach(action);
         }
      } catch (IOException ex) {
         studio_.logs().showError("Error while parsing file: " + f.getName());
         return;
      }

      // Store timeStamp and x/y coordinates from file into datedCoordinates
      for (Map.Entry<String, Point> entry : coordinates_.entrySet()) {
         datedCoordinates.put(dateStringToInstant(entry.getKey()),
                 entry.getValue());
      }

      final DataViewer activeDataViewer = studio_.displays().getActiveDataViewer();
      if (activeDataViewer == null) {
         studio_.logs().showError("Please open image data first");
         return;
      }

      final List<PASData> pasData = new ArrayList<>(datedCoordinates.size());

      // Store TimeStamps from images into frameTimeStamps
      final DataProvider dataProvider = activeDataViewer.getDataProvider();
      Iterable<Coords> unorderedImageCoords = dataProvider.getUnorderedImageCoords();
      for (Coords c : unorderedImageCoords) {
         int frame = c.getT();
         try {
            Metadata metadata = dataProvider.getImage(c).getMetadata();
            PropertyMap userData = metadata.getUserData();

            // for backward coimpatabilityh with erronous file type
            if (userData.containsPropertyMap("UserData")) {
               userData = userData.getPropertyMap("UserData", null);
            }
            if (userData.containsString(Terms.CORERECEIVEDTIMEKEY)) {
               String timeStampString = userData.getString(Terms.CORERECEIVEDTIMEKEY, null);
               if (timeStampString != null) {
                  frameTimeStamps.put(frame, dateStringToInstant(timeStampString));
               }
            }

         } catch (IOException ex) {
            studio_.logs().logError(ex);
         }
      }

      // for each Point and Shoot timeStamp, find the frame where it happened
      // and store in pasData
      for (Map.Entry<Instant, Point> entry : datedCoordinates.entrySet()) {
         Instant entryInstant = entry.getKey();
         Iterator<Map.Entry<Integer, Instant>> it = frameTimeStamps.entrySet().iterator();
         boolean found = false;
         Map.Entry<Integer, Instant> frameNrAndTime = null;
         while (it.hasNext() && !found) {
            frameNrAndTime = it.next();
            if (entryInstant.isBefore(frameNrAndTime.getValue())) {
               found = true;
            }
         }
         if (found && frameNrAndTime != null) {
            PASData.Builder pasB = PASData.builder();
            pasB.pasClicked(entryInstant).
                    framePasClicked(frameNrAndTime.getKey()).
                    pasIntended(entry.getValue()).
                    tsOfFrameBeforePas(frameNrAndTime.getValue()).
                    dataSetName(dataProvider.getName());
            pasData.add(pasB.build());
         }
      }

      // We have the bleach Coordinates as frame - x/y. Check with the actual images
      List<Map<Integer, ParticleData>> tracks = new ArrayList<>();
      // Use multiple threads in BoofCV code:
      BoofConcurrency.USE_CONCURRENT = true;
      try {
         int imgWidth = dataProvider.getAnyImage().getWidth();
         int imgHeight = dataProvider.getAnyImage().getHeight();
         if (roiWidth_ > imgWidth) {
            studio_.logs().showError("ROI width is greater than image width.  Aborting");
         }
         if (roiHeight_ > imgHeight) {
            studio_.logs().showError("ROI height is greater than image height.  Aborting");
         }
         final int xMiddle = roiWidth_ / 2;
         final int yMiddle = roiHeight_ / 2;
         final Point2D_I32 middle = new Point2D_I32(xMiddle, yMiddle);
         
         // create a boofCV Planar that contains all of the MM data (no copy, backed by MM)
         Coords.Builder cbb = dataProvider.getAnyImage().getCoords().copyBuilder();
         Planar bCVStack = new Planar(GrayU16.class, dataProvider.getAxisLength(Coords.T));
         bCVStack.setWidth(imgWidth);
         bCVStack.setHeight(imgHeight);
         bCVStack.setStride(imgWidth);
         for (int frame = 0; frame < dataProvider.getAxisLength(Coords.T); frame++) {
            bCVStack.setBand(frame, BoofCVImageConverter.mmToBoofCV(
                    dataProvider.getImage(cbb.t(frame).build()), false) );
         }
         ListIterator<PASData> pasDataIt = pasData.listIterator();
         while (pasDataIt.hasNext()) {
            // define an ROI around the expected postion 
            PASData pasEntry = pasDataIt.next();
            int x0 = pasEntry.pasIntended().x - (int) (roiWidth_ / 2);
            x0 = (x0 < 0) ? 0 : x0;
            int x1 = (x0 + roiWidth_ > imgWidth) ? imgWidth : x0 + roiWidth_;
            int y0 = pasEntry.pasIntended().y - (int) (roiWidth_ / 2);
            y0 = y0 < 0 ? 0 : y0;
            int y1 = (y0 + roiHeight_ > imgHeight) ? imgHeight: y0 + roiHeight_;
            Planar<GrayU16> subImage = bCVStack.subimage(x0, y0, x1, y1, null);
            PASFrameSet findMinFrames = new PASFrameSet(
                    pasEntry.framePasClicked() - findMinFramesBefore,
                    pasEntry.framePasClicked(),
                    pasEntry.framePasClicked() + findMinFramesAfter_,
                    dataProvider.getAxisLength(Coords.T));
            /* Locate the bleach spot by dividing a minimum and average projection
              of n frames started at the recorded bleach time and reporting the 
              position of the minimum.  This fails when the particle moves too much
              This algorithm can probably be improved by tracking the complete particle
            */
            GrayU16 beforeCV = new GrayU16(x1 - x0, y1 - y0);
            GImageBandMath.average(subImage, beforeCV, 
                    findMinFrames.getStartFrame(), findMinFrames.getCentralFrame() + 1);
            GrayF32 beforeCVF = new GrayF32(x1 - x0, y1 - y0);
            ConvertImage.convert(beforeCV, beforeCVF);
            GrayU16 minBCV = new GrayU16(x1 - x0, y1 - y0);
            GImageBandMath.minimum(subImage, minBCV, 
                    findMinFrames.getCentralFrame(), findMinFrames.getEndFrame());
            GrayF32 minBCVF = new GrayF32(x1 - x0, y1 - y0);
            ConvertImage.convert(minBCV, minBCVF);
            GrayF32 dResult = new GrayF32(minBCVF.width, minBCVF.height);
            GPixelMath.divide(minBCVF, beforeCVF, dResult);
            GrayF32 gResult = new GrayF32(minBCVF.width, minBCVF.height);
            BlurImageOps.gaussian(dResult, gResult, 3, -1, null);
            
             // Find the minimum and define this as the bleachPoint
            Point2D_I32 minPoint = findMinPixel(gResult);
            
            // System.out.println("Lowest Pixel position: " + minPoint.x + ", " + minPoint.y);
            // check if this is within expected range
            
            if (minPoint.distance(middle) > MAXDISTANCE) {
               pasDataIt.remove();
               continue;
            }
            // Store coordinates indicating where the bleach actually happened
            // (in pixel coordinates of the original data)
            Point pasActual = new Point(x0 + minPoint.x, y0 + minPoint.y);
            String id = "" + pasEntry.framePasClicked() + ": "
                    + pasActual.x + ", " + pasActual.y;
            pasDataIt.set(pasEntry.copyBuilder().
                    id(id).
                    pasActual(pasActual).
                    pasFrames(null).
                    build());         
         }
         psd_.setStatus("Found " + pasData.size()  + " bleach events, now tracking...");
         
         // Track particle that received the bleach by local thresholding
         // First go backwards in time, then forward
         pasDataIt = pasData.listIterator();
         Coords.Builder cb = dataProvider.getAnyImage().getCoords().copyBuilder();
         int count = 0;
         while (pasDataIt.hasNext()) {
            PASData pasEntry = pasDataIt.next();
            Map<Integer, ParticleData> track = new TreeMap<>();
            Point2D_I32 bleachPoint = new Point2D_I32(pasEntry.pasActual().x, pasEntry.pasActual().y);
            ParticleData firstParticle = ParticleData.centralParticle(dataProvider, cb, 
                    pasEntry.framePasClicked() + 1, bleachPoint, halfROISize_);
            if (firstParticle == null) {
               continue;
            }
            Point2D_I32 currentPoint = firstParticle.getCentroid().copy();
            for (int frame = pasEntry.framePasClicked() + 1;
                    frame >= 0; frame--) {
               ParticleData nextParticle = ParticleData.centralParticle(dataProvider, 
                       cb, frame, currentPoint, halfROISize_);
               if (nextParticle != null && ( 
                       currentPoint.distance(nextParticle.getCentroid()) < maxDistance )) {
                  currentPoint = nextParticle.getCentroid();
                  track.put(frame, nextParticle);
               } else {
                  track.put(frame, null);
                  // TODO: increase counter, give up when too high
               }
            }

            // now go forward in time
            currentPoint = firstParticle.getCentroid().copy();
            GrayU16 preBleach = (GrayU16) BoofCVImageConverter.subImage(dataProvider, cb, 
                    pasEntry.framePasClicked() + 1, currentPoint, halfROISize_);
            if (preBleach == null) {
               continue;
            }
            GrayF32 fPreBleach = new GrayF32(preBleach.getWidth(), preBleach.getHeight());
            ConvertImage.convert(preBleach, fPreBleach);
            ParticleData previousParticle = null;
            for (int frame = pasEntry.framePasClicked() + 2;
                    frame < dataProvider.getAxisLength(Coords.T); frame++) {
               ParticleData nextParticle = ParticleData.centralParticle(dataProvider, 
                       cb, frame, currentPoint, halfROISize_);
               if (nextParticle == null || ( 
                       currentPoint.distance(nextParticle.getCentroid()) > maxDistance )) {
                  track.put(frame, null);
                  if (previousParticle != null) {
                     nextParticle = previousParticle.copy();
                  }
                  // TODO: increase counter, give up when too high
               } 

               previousParticle = nextParticle;
               if (nextParticle != null) {
                  currentPoint = nextParticle.getCentroid();
               }
               track.put(frame, nextParticle);
            }
            
            // Locate the bleachspots in the particle data 
            int bleachSpotsMissed = 0;
            currentPoint = track.get(pasEntry.framePasClicked() + 2).getCentroid();
            final int getCalculateVectorFrame = pasEntry.framePasClicked() + 5 + 
                    nrFramesToMeasureBleachToParticleVector;
            Point2D_I32 offsetVector = null;
            for (int frame = pasEntry.framePasClicked() + 2;
                    frame < dataProvider.getAxisLength(Coords.T); frame++) {
               if (bleachSpotsMissed < 5 || continueBleachSpotTracking) {
                  ParticleData particle = track.get(frame);
                  ImageGray current = BoofCVImageConverter.subImage(dataProvider,
                          cb, frame, currentPoint, halfROISize_);
                  if (current != null) {
                     Point2D_I32 offset = new Point2D_I32(currentPoint.x - halfROISize_, 
                             currentPoint.y - halfROISize_);
                     if (offsetVector != null) {
                        Point2D_I32 centroid = particle.getCentroid();
                        Point2D_I32 bp = new Point2D_I32(centroid.x - offsetVector.x,
                                 centroid.y - offsetVector.y);
                        particle = ParticleData.addBleachSpotToParticle(particle, 
                                (GrayU16)current, offset, bp,
                        bleachSpotRadius);                                                
                     } else {
                     particle = ParticleData.addBleachSpotToParticle(
                             fPreBleach,
                             (GrayU16) current,
                             track,
                             frame,
                             particle,
                             offset,
                              bleachSpotRadius,
                             MAXDISTANCE);
                     }
                     currentPoint = particle.getCentroid();
                     track.put(frame, particle);
                  }
                  if (particle.getBleachSpot() == null) {
                     bleachSpotsMissed += 1;
                  } else {
                     bleachSpotsMissed = 0;
                  }
               }
               if (continueBleachSpotTracking && frame == getCalculateVectorFrame ) {
                  List<Point2D_I32> vectors = new ArrayList<>
                                 (nrFramesToMeasureBleachToParticleVector);
                  int startFrame = pasEntry.framePasClicked() + 5;
                  int endFrame = startFrame + nrFramesToMeasureBleachToParticleVector;
                  for (int vFrame = startFrame; vFrame < endFrame; vFrame++) {
                     ParticleData pd = track.get(vFrame);
                     if (pd != null) {
                        Point2D_I32 c = pd.getCentroid();
                        Point2D_I32 b = pd.getBleachSpot();
                        if (c != null && b != null) {
                           Point2D_I32 vector = new Point2D_I32(c.x - b.x,
                                 c.y - b.y);
                           vectors.add(vector);
                        }
                     }                     
                  }
                  if (vectors.size() > 0.8 * nrFramesToMeasureBleachToParticleVector) {
                     offsetVector = ListUtils.avgPoint2DList(vectors);
                     // System.out.println("offset: " + offsetVector.x + ", " + offsetVector.y);
                  }
                  
               }
            }

            tracks.add(track);
            pasDataIt.set(pasEntry.copyBuilder().particleDataTrack(track).
                    build());
            psd_.setProgress((double) ++count / (double) pasData.size());
         }
         
         // Find duplicate tracks (i.e. the same particle was bleached twice
         // Algorithm: find the centroid of the first particle in the track.  
         // If within a certain distance from the centroid of the first particle
         // from another track, we'll assume this is one and the same and remove the track.
         final double identityDistance = 5.0;
         List<Map<Integer, ParticleData>> doubleTracks = new ArrayList<>();
         for (int i = 0; i < tracks.size(); i++) {
            Map<Integer, ParticleData> track = tracks.get(i);
            if (!doubleTracks.contains(track)) {
               ParticleData firstParticle = track.get(0);
               if (firstParticle != null) {
                  for (int j = 0; j < tracks.size(); j++) {
                     if (j != i) {
                        Map<Integer, ParticleData> otherTrack = tracks.get(j);
                        ParticleData otherFirstParticle = otherTrack.get(0);
                        if (otherFirstParticle != null
                                && firstParticle.getCentroid().distance(
                                        otherFirstParticle.getCentroid()) < identityDistance) {
                           doubleTracks.add(otherTrack);
                        }
                     }
                  }
               }
            }
         }
         // Remove the duplicates that were found
         for (Map<Integer, ParticleData> track : doubleTracks) {
            tracks.remove(track);
            // also remove pasData that contain this track
            pasDataIt = pasData.listIterator();
            PASData dataToBeRemoved = null;
            while (pasDataIt.hasNext() && dataToBeRemoved == null) {
               PASData next = pasDataIt.next();
               if (next.particleDataTrack() ==  track) {
                  dataToBeRemoved = next;
               }
            }
            if (dataToBeRemoved != null) {
               pasData.remove(dataToBeRemoved);
            }
         }
         
         if (tracks.isEmpty()) {
            psd_.setStatus("No bleached particles found");
            psd_.setProgress(1.0);
            return;
         }

         // index tracks by frame:
         Map<Integer, List<ParticleData>> tracksIndexedByFrame = new TreeMap<>();
         tracks.forEach((track) -> {
            track.entrySet().forEach((entry) -> {
               List<ParticleData> particlesInFrame = tracksIndexedByFrame.get(entry.getKey());
               if (particlesInFrame == null) {
                  particlesInFrame = new ArrayList<>();
               }
               particlesInFrame.add(entry.getValue());
               tracksIndexedByFrame.put(entry.getKey(), particlesInFrame);
            });
         });

         // Remove PASData that have no particleDataTrack 
         List<PASData> cleanedPASData = new ArrayList<>();
         for (PASData d : pasData) {
            if (d.particleDataTrack() != null) {
               cleanedPASData.add(d);
            }
         }


         // Find "control particle", i.e. particles that were not bleached
         // and that serve as intensity controls
         psd_.setStatus("Looking for control particles...");
         psd_.setProgress(0.0);
         List<Map<Integer, ParticleData>> controlTracks = new ArrayList<>();
         try {
            cb = dataProvider.getAnyImage().getCoords().copyBuilder();
            Coords ct0 = cb.t(0).build();
            GrayU16 img0 = (GrayU16) BoofCVImageConverter.mmToBoofCV(
                    dataProvider.getImage(ct0), false);
            //GrayU16 img0Gauss = new GrayU16(img0.getWidth(), img0.getHeight());
            //BlurImageOps.gaussian(img0, img0Gauss, 3, -1, null);
            
            //int otsuThreshold = (int) GThresholdImageOps.computeOtsu(
            //        img0, 0, img0.getDataType().getMaxValue());
            int entropyThreshold =  BoofCVUtils.compressedMaxEntropyThreshold(img0, 256);
            GrayU8 mask = new GrayU8(img0.getWidth(), img0.getHeight());
      
            GThresholdImageOps.threshold(img0, mask, entropyThreshold, false);
            // Remove small particles
            mask = BinaryImageOps.erode4(mask, 1, null);
            mask = BinaryImageOps.dilate4(mask, 1, null);
            GrayS32 contourImg = new GrayS32(img0.getWidth(), img0.getHeight());
            List<Contour> contours = 
                    BinaryImageOps.contour(mask, ConnectRule.FOUR, contourImg);
            List<List<Point2D_I32>> clusters = 
                    BinaryImageOps.labelToClusters(contourImg, contours.size(), null);
            // Remove particles that were bleached
            List<List<Point2D_I32>> controlClusters = new ArrayList<>();
            for (List<Point2D_I32> particle : clusters) {
               Point2D_I32 centroid = ContourStats.centroid(particle);
               boolean isBleachedParticle = false;
               for (Map<Integer, ParticleData> track : tracks) {
                  if (track.get(0) != null) {
                     if (centroid.distance(track.get(0).getCentroid()) < DIST_UNCERTAINTY) {
                        isBleachedParticle = true;
                     }
                  }
               }
               if (!isBleachedParticle) {
                  controlClusters.add(particle);
               }
            }
            
            // only analyze the n largest clusters
            // TODO: make n an input variable
            final int nrLargestClusters = 15;
            count = 0;
            controlClusters = ListUtils.getNLargestLists(controlClusters, nrLargestClusters);
            for (List<Point2D_I32> particle : controlClusters) {
               Point2D_I32 centroid = ContourStats.centroid(particle);
               Map<Integer, ParticleData> track = new TreeMap<>();
               Point2D_I32 currentPoint = centroid;
               int missing = 0;
               boolean bail = false;
               for (int frame = 0; frame < dataProvider.getAxisLength(Coords.T) && !bail; frame++) {
                  ParticleData nextParticle = ParticleData.centralParticle(dataProvider,
                          cb, frame, currentPoint, halfROISize_);
                  if (nextParticle != null && (currentPoint.distance(nextParticle.getCentroid()) < maxDistance)) {
                     currentPoint = nextParticle.getCentroid();
                     track.put(frame, nextParticle);
                     missing = 0;
                     // TODO: Check whether it is now the same as one of the bleached particles
                     // and bail if so...
                     for (ParticleData p : tracksIndexedByFrame.get(frame)) {
                        if (p != null && p.getCentroid() != null && 
                                p.getCentroid().distance(currentPoint) < maxDistance) {
                           bail = true;
                        }
                     }
                  } else {
                     // increase counter, give up when too high
                     missing++;
                     if (missing > 10) {
                        bail = true;
                     }
                  }
               }
               if (!bail) {
                  controlTracks.add(track);
               }
               psd_.setProgress( (double) ++count / (double) nrLargestClusters);
            }

         } catch (IOException ioe) {
         }
         
         
         if (controlTracks.size() > 0) {
            // get average intensity of control particles, indexed by frame 
            Map<Integer, Double> controlAvgIntensity = new HashMap<>();
            for (int frame = 0; frame < dataProvider.getAxisLength(Coords.T); frame++) {
               double sum = 0.0;
               int n = 0;
               for (Map<Integer, ParticleData> track : controlTracks) {
                  // TODO: normalize by size??
                  if (track.get(frame) != null && track.get(frame).getMaskAvg() != null) {
                     sum += track.get(frame).getMaskAvg();
                     n += 1;
                  }
               } 
               controlAvgIntensity.put(frame, (sum / n) - cameraOffset ); 
            }
            
            //TODO: filter the control intensities
            // Either Kalman filter or moving average or median

            // normalize the bleach spot intensities and store with the PASData->ParticleData
            for (PASData d : cleanedPASData) {
               d.normalizeBleachSpotIntensities(findMinFramesBefore, 
                       cameraOffset, controlAvgIntensity);
               d.normalizeParticleIncludingBleachIntensities(findMinFramesBefore, 
                       cameraOffset, controlAvgIntensity);
            }
            
            cleanedPASData = removeEmptryParticleTracks(cleanedPASData);
            
                        
            if (cleanedPASData.isEmpty()) {
               psd_.setStatus("No bleaching events found");
               psd_.setProgress(0.0);
               return;
            }
                          
            // get intensities of bleach Spots and total particles
            // out of the data structures, and plot them
            List<XYSeries> bleachPlotData = plottableNormalizedBleachSpotIntensities(
                    cleanedPASData, frameTimeStamps) ;
            XYSeries[] plots = bleachPlotData.toArray(new XYSeries[bleachPlotData.size()]);
            PlotUtils pu = new PlotUtils(studio_.profile().getSettings(this.getClass()));
            DataExporter bleachExporter = new DataExporter(studio_, cleanedPASData, 
                    frameTimeStamps, DataExporter.Type.BLEACH);
            pu.plotData("Bleach Intensity Profile", plots, "Time (ms)",
                    "Normalized Intensity", "", 1.3, WidgetSettings.COLORS, bleachExporter);
      
            List<XYSeries> particlePlotData = plottableNormalizedParticleIntensities(
                  cleanedPASData, frameTimeStamps);
            XYSeries[] particlePlots = particlePlotData.toArray(new XYSeries[particlePlotData.size()]);            
            PlotUtils pu2 = new PlotUtils(studio_.profile().getSettings(this.getClass()));
            DataExporter particleABExporter = new DataExporter(studio_, cleanedPASData, 
                    frameTimeStamps, DataExporter.Type.PARTICLE_AND_BLEACH);
            pu2.plotData("Particle Intensity Profile", particlePlots, "Time (ms)",
                    "Normalized Intensity", "", 1.3, WidgetSettings.COLORS, particleABExporter);

            plotControlParticleIntensities(controlAvgIntensity, frameTimeStamps);
            
         } else {
            psd_.setStatus("No usable control particles found");
            psd_.setProgress(0.0);
         }
         
         
         if (activeDataViewer instanceof DisplayWindow) {
            DisplayWindow dw = (DisplayWindow) activeDataViewer;
            dw.addOverlay(new Overlay(tracksIndexedByFrame, controlTracks));
         }
         
         psd_.setStatus("Inactive...");
         psd_.setProgress(0.0);

      } catch (IOException ioe) {
         studio_.logs().showError("Error while reading image data");
      }

   }

   /**
    * Convenience methods to convert a string with expected format to an Instant
    *
    * @param timeStampString String should be formatted as: 2018-10-22
    * 17:05:12.241 yyyy-mm-dd hh:mm:ss.ms Part after the "." can be 3 digits
    * (ms) or 6 digits (microseconds)
    * @return Instant representing the provided timeStamp in the current
    * timezone
    */
   public static Instant dateStringToInstant(final String timeStampString) {
      String subSeconds = timeStampString.substring(timeStampString.lastIndexOf(".") + 1);
      String tmpTimeStampString = timeStampString.substring(0, timeStampString.lastIndexOf("."));
      tmpTimeStampString = tmpTimeStampString.replace(" ", "T");
      LocalDateTime ldt = LocalDateTime.parse(tmpTimeStampString);
      ZonedDateTime zdt = ldt.atZone(ZoneId.systemDefault());
      Instant instant = zdt.toInstant();
      if (subSeconds.length() == 3) {
         instant = instant.plusMillis(Integer.parseInt(subSeconds));
      } else if (subSeconds.length() == 6) {
         instant = instant.plusNanos(1000 * Integer.parseInt(subSeconds));
      }
      return instant;
   }

   /**
    * Convenience method to find the minimum pixel value in the given Processor
    *
    * @param img
    * @param ip
    * @return
    *
   public static Point findMinPixel(ImageProcessor ip) {
      Point p = new Point(0, 0);
      Float val = ip.getPixelValue(0, 0);
      for (int x = 0; x < ip.getWidth(); x++) {
         for (int y = 0; y < ip.getHeight(); y++) {
            if (ip.getPixelValue(x, y) < val) {
               p.x = x;
               p.y = y;
               val = ip.getPixelValue(x, y);
            }
         }
      }
      return p;
   }
   */
   
   /**
    * 
    * @param img image in which to look for minimum pixel
    * @return 
    */
   public static Point2D_I32 findMinPixel(GrayF32 img) {
      Point2D_I32 p = new Point2D_I32(0, 0);
      Float val = img.unsafe_get(0, 0);
      for (int x = 0; x < img.getWidth(); x++) {
         for (int y = 0; y < img.getHeight(); y++) {
            if (img.unsafe_get(x, y) < val) {
               p.x = x;
               p.y = y;
               val = img.unsafe_get(x, y);
            }
         }
      }
      return p;
   }

 



   private class pointAndShootParser implements Consumer<String> {
      @Override
      public void accept(String t) {
         String[] parts = t.split("\t");
         if (parts.length == 3) {
            coordinates_.put(
                    parts[0],
                    new Point(
                            Integer.parseInt(parts[1]),
                            Integer.parseInt(parts[2])));
         }
      }
   }
   
   private void plotControlParticleIntensities(final Map<Integer, Double> controlAvgIntensity,
           final Map<Integer, Instant> frameTimeStamps) {
      List<XYSeries> plotData = new ArrayList<>();
      XYSeries data = new XYSeries("Control Particles", false, false);
      
      for (Map.Entry<Integer, Double> frameControlData : controlAvgIntensity.entrySet()) {
         data.add((frameTimeStamps.get(frameControlData.getKey()).toEpochMilli() - 
                     frameTimeStamps.get(0).toEpochMilli()) / 1000.0,
                 frameControlData.getValue());
      }

      plotData.add(data);
      XYSeries[] plots = plotData.toArray(new XYSeries[plotData.size()]);
      PlotUtils pu = new PlotUtils(studio_.profile().getSettings(this.getClass()));
      pu.plotData("Control Intensity Profile", plots, "Time (s)",
              "Avg. Intensity", "", null, WidgetSettings.COLORS, null);
   }
   
   private List<PASData> removeEmptryParticleTracks(final List<PASData> input) {
      final List<PASData> output = new ArrayList<>();
      for (PASData d : input) {
         if (d.particleDataTrack() != null && d.id() != null) {
            boolean add = false;
            int bleachCounter = 0;
            int particleCounter = 0;
            for (int frame = 0; frame < d.particleDataTrack().size(); frame++) {
               if (d.particleDataTrack().get(frame) != null) {
                  Double normalizedBleachMaskAvg = d.particleDataTrack().get(frame).getNormalizedBleachMaskAvg();
                  if (normalizedBleachMaskAvg != null && normalizedBleachMaskAvg != Double.NaN) {
                     bleachCounter++;
                  }
                  Double normalizedMaskIncludingBleachAvg
                          = d.particleDataTrack().get(frame).getNormalizedMaskIncludingBleachAvg();
                  if (normalizedMaskIncludingBleachAvg != null && normalizedMaskIncludingBleachAvg != Double.NaN) {
                     particleCounter++;
                  }
               }
               if (bleachCounter > 10 && particleCounter > 10) {
                  add = true;
               }
            }
            if (add) {
               output.add(d);
            }
         }
      }
      return output;
   }
   
   /**
    * Should only be called after normalized Bleach Values have been calculated
    * @param pasData
    * @param cameraOffset
    * @param controlAvgIntensity
    * @param frameTimeStamps
    * @return 
    */
   private List<XYSeries> plottableNormalizedBleachSpotIntensities(
           final List<PASData> pasData,
           final Map<Integer, Instant> frameTimeStamps) {
      List<XYSeries> plotData = new ArrayList<>();
      for (PASData d : pasData) {
         if (d.particleDataTrack() != null && d.id() != null) {
            XYSeries data = new XYSeries(d.id(), false, false);
            for (int frame = 0; frame < d.particleDataTrack().size(); frame++) {
               if (d.particleDataTrack().get(frame) != null) {
                  Double normalizedBleachMaskAvg = d.particleDataTrack().get(frame).getNormalizedBleachMaskAvg();
                  if (normalizedBleachMaskAvg != null && normalizedBleachMaskAvg != Double.NaN) {
                     data.add(frameTimeStamps.get(frame).toEpochMilli()
                             - frameTimeStamps.get(d.framePasClicked()).toEpochMilli(),
                             normalizedBleachMaskAvg);
                  }
               }
            }
            if (data.getItemCount() > 10) {
               plotData.add(data);
            }
         }
      }
      return plotData;
   }
     
   
   private List<XYSeries> plottableNormalizedParticleIntensities(final List<PASData> pasData,
           final Map<Integer, Instant> frameTimeStamps) {
      List<XYSeries> plotData = new ArrayList<>();
      for (PASData d : pasData) {
         if (d.particleDataTrack() != null) {
            XYSeries data = new XYSeries("" + d.framePasClicked() + ": "
                    + d.pasActual().x + ", " + d.pasActual().y,
                    false, false);
            for (int frame = 0; frame < d.particleDataTrack().size(); frame++) {
               if (d.particleDataTrack().get(frame) != null) {
                  Double normalizedMaskIncludingBleachAvg
                          = d.particleDataTrack().get(frame).getNormalizedMaskIncludingBleachAvg();
                  if (normalizedMaskIncludingBleachAvg != null && normalizedMaskIncludingBleachAvg != Double.NaN) {
                     data.add(frameTimeStamps.get(frame).toEpochMilli()
                             - frameTimeStamps.get(d.framePasClicked()).toEpochMilli(),
                             normalizedMaskIncludingBleachAvg);
                  }
               }
            }
            if (data.getItemCount() > 10) {
               plotData.add(data);
            }
         }
      }
      return plotData;
   }
 
   
   /*
   private Point ccParticle(DataProvider dp, Coords.Builder cb,
           int frame1, int frame2, Point p) throws IOException {
      Coords coord = cb.t(frame1).build();
      Image img = dp.getImage(coord);
      ImageProcessor iProc = studio_.data().getImageJConverter().createProcessor(img);
      iProc.setRoi((int) p.getX() - halfROISize_, (int) p.getY() - halfROISize_, roiSize_, roiSize_);
      iProc = iProc.crop();
      // check ROI out of bounds!
      if (iProc.getWidth() != roiSize_ || iProc.getHeight() != roiSize_) {
         return p;  // TODO: log/show this problem?
      }
      Coords coord2 = cb.t(frame2).build();
      Image img2 = dp.getImage(coord2);
      ImageProcessor iProc2 = studio_.data().getImageJConverter().createProcessor(img2);
      iProc2.setRoi((int) p.getX() - halfROISize_, (int) p.getY() - halfROISize_, roiSize_, roiSize_);
      iProc2 = iProc2.crop();
      // check ROI out of bounds
      if (iProc2.getWidth() != roiSize_ || iProc2.getHeight() != roiSize_) {
         return p;  // TODO: log/show this problem?
      }
      MovementByCrossCorrelation mbdd = new MovementByCrossCorrelation(iProc);
      Point2D.Double p2 = new Point2D.Double();
      mbdd.getJitter(iProc2, p2);
      return new Point(p.x + (int) p2.x - halfROISize_, p.y + (int) p2.y - halfROISize_);
      /
     NormalizedCrossCorrelation ncc = new NormalizedCrossCorrelation( 
             (ShortProcessor)  iProc);
     return ncc.correlate((ShortProcessor) iProc2, p, new Point(2,2));
/
   
   }
*/
   
   private Rectangle2D_I32 boundingBoxSize(DataProvider dp, Coords.Builder cb,
           int frame, Point2D_I32 p) throws IOException
   {
      Coords coord = cb.t(frame).build();
      Image img = dp.getImage(coord);
      
      ImageGray ig = BoofCVImageConverter.mmToBoofCV(img, false);
      if (p.getX() - halfROISize_ < 0 ||
              p.getY() - halfROISize_ < 0 ||
              p.getX() + halfROISize_ >= ig.getWidth() ||
              p.getY() + halfROISize_ >= ig.getHeight()) {
         return null; // TODO: we'll get stuck at the edge
      }
      ImageGray sub = (ImageGray) ig.subimage((int) p.getX() - halfROISize_, 
              (int) p.getY() - halfROISize_, (int) p.getX() + halfROISize_, 
              (int) p.getY() + halfROISize_);
      int threshold =  GThresholdImageOps.computeOtsu2(sub, 
              0, (int)sub.getImageType().getDataType().getMaxValue());
      GrayU8 mask = new GrayU8(sub.width, sub.height);
      GThresholdImageOps.threshold(sub, mask, threshold, false);
      GrayS32 contourImg = new GrayS32(sub.width, sub.height);
      List<Contour> contours = BinaryImageOps.contour(mask, ConnectRule.FOUR, contourImg);
      
      List<List<Point2D_I32>> clusters = BinaryImageOps.labelToClusters(contourImg, contours.size(), null);

      Map<Point2D_I32, List<Point2D_I32>> centroidedClusters = 
              new HashMap<>();
      clusters.forEach((cluster) -> {
         centroidedClusters.put(ContourStats.centroid(cluster), cluster);
      });
      Point2D_I32 nearestPoint = ContourStats.nearestPoint(p, centroidedClusters.keySet());
      Rectangle2D_I32 boundingBox = null;
      if (nearestPoint != null) {
         boundingBox = ContourStats.boundingBox(centroidedClusters.get(nearestPoint));
      }
      return boundingBox;      
      
   }

}