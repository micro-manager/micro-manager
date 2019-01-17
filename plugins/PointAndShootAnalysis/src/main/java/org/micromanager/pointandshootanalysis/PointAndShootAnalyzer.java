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
import boofcv.alg.misc.GImageStatistics;
import boofcv.alg.misc.GPixelMath;
import boofcv.core.image.ConvertImage;
import boofcv.struct.ConnectRule;
import boofcv.struct.image.GrayF32;
import boofcv.struct.image.GrayS32;
import boofcv.struct.image.GrayU16;
import boofcv.struct.image.GrayU8;
import boofcv.struct.image.ImageGray;
import georegression.struct.point.Point2D_I32;
import georegression.struct.shapes.Rectangle2D_I32;
import ij.ImagePlus;
import ij.ImageStack;
import ij.plugin.ZProjector;
import ij.process.ImageProcessor;
import java.awt.Point;
import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
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
import org.micromanager.pointandshootanalysis.algorithm.BinaryListOps;
import org.micromanager.pointandshootanalysis.algorithm.CircleMask;
import org.micromanager.pointandshootanalysis.algorithm.ContourStats;
import org.micromanager.pointandshootanalysis.algorithm.MovementByCrossCorrelation;
import org.micromanager.pointandshootanalysis.algorithm.ThresholdImageOps;
import org.micromanager.pointandshootanalysis.algorithm.Utils;
import org.micromanager.pointandshootanalysis.data.BoofCVImageConverter;
import org.micromanager.pointandshootanalysis.data.PASData;
import org.micromanager.pointandshootanalysis.data.PASFrameSet;
import org.micromanager.pointandshootanalysis.data.ParticleData;
import org.micromanager.pointandshootanalysis.data.Terms;
import org.micromanager.pointandshootanalysis.display.Overlay;
import org.micromanager.pointandshootanalysis.plot.DataSeriesKey;
import org.micromanager.pointandshootanalysis.plot.PlotUtils;
import org.micromanager.pointandshootanalysis.utils.ListUtils;

/**
 *
 * @author nico
 */
public class PointAndShootAnalyzer implements Runnable {

   final private Studio studio_;
   final private PropertyMap settings_;
   ;
   final Map<String, Point> coordinates_;
   final private static int MAXDISTANCE = 10; // max distance in pixels from the expected position
   // if more, we will reject the bleach spot
   // may need to be changed buy the user
   final private int findMinFramesBefore_ = 5; // frames before user clicked PAS.  
   //Used to find actual bleach spot and to normalize
   final private int findMinFramesAfter_ = 20;  // frames after used clicked PAS.  
   //Used to find actual bleach spot
   final private int roiSize_ = 64;  // keep at factor of 2 to enable FFT 
   final private int halfROISize_ = roiSize_ / 2;
   final private int roiWidth_ = roiSize_;  // may need to changed by user
   final private int roiHeight_ = roiSize_;  // may need to changed by user

   public PointAndShootAnalyzer(Studio studio, PropertyMap settings) {
      studio_ = studio;
      settings_ = settings;
      coordinates_ = new HashMap<>();
   }

   @Override
   public void run() {

      final Map<Instant, Point> datedCoordinates = new TreeMap<>();
      final Map<Integer, Instant> frameTimeStamps = new TreeMap<>();

      // Read variables provided by UI from profile
      String fileName = settings_.getString(Terms.LOCATIONSFILENAME, "");
      int radius = settings_.getInteger(Terms.RADIUS, 3);
      int nrFramesBefore = settings_.getInteger(Terms.NRFRAMESBEFORE, 2);
      int nrFramesAfter = settings_.getInteger(Terms.NRFRAMESAFTER, 200);
      int maxDistance = settings_.getInteger(Terms.MAXDISTANCE, 3);

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
         Stream<String> fileLines = Files.lines(f.toPath());
         fileLines.forEach(action);
         fileLines.close();
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
         if (found) {
            PASData.Builder pasB = PASData.builder();
            pasB.pasClicked(entryInstant).
                    framePasClicked(frameNrAndTime.getKey()).
                    pasIntended(entry.getValue()).
                    tsOfFrameBeforePas(frameNrAndTime.getValue());
            pasData.add(pasB.build());
         }
      }

      // We have the bleach Coordinates as frame - x/y. Check with the actual images
      List<XYSeries> plotData = new ArrayList<>();
      List<Map<Integer, ParticleData>> tracks = new ArrayList<>();
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

         ListIterator<PASData> pasDataIt = pasData.listIterator();
         while (pasDataIt.hasNext()) {
            // define an ROI around the expected postion 
            PASData pasEntry = pasDataIt.next();
            int roiX = pasEntry.pasIntended().x - (int) (roiWidth_ / 2);
            roiX = (roiX < 0) ? 0 : roiX;
            roiX = (roiX + roiWidth_ > imgWidth) ? imgWidth - roiWidth_ : roiX;
            int roiY = pasEntry.pasIntended().y - (int) (roiWidth_ / 2);
            roiY = roiY < 0 ? 0 : roiY;
            roiY = (roiY + roiHeight_ > imgHeight) ? imgHeight - roiHeight_ : roiY;

            // Make a substack with this ROI, starting findMinFramesBefore_ and ending findMinFramesAfter_
            PASFrameSet findMinFrames = new PASFrameSet(
                    pasEntry.framePasClicked() - findMinFramesBefore_,
                    pasEntry.framePasClicked(),
                    pasEntry.framePasClicked() + findMinFramesAfter_,
                    dataProvider.getAxisLength(Coords.T));
            Coords.Builder cb = dataProvider.getAnyImage().getCoords().copyBuilder();
            ImageStack subStack = new ImageStack(roiWidth_, roiHeight_);
            for (int frame = findMinFrames.getStartFrame();
                    frame < findMinFrames.getEndFrame();
                    frame++) {
               Coords coord = cb.t(frame).build();
               Image img = dataProvider.getImage(coord);
               ImageProcessor iProc = studio_.data().getImageJConverter().createProcessor(img);
               iProc.setRoi(roiX, roiY, roiWidth_, roiHeight_);
               ImageProcessor crop = iProc.crop();
               subStack.addSlice(crop);
            }
            ImagePlus imp = new ImagePlus("f: " + pasEntry.framePasClicked(), subStack);

            // make an average projection of this substack
            ZProjector zp = new ZProjector(imp);
            zp.setMethod(ZProjector.AVG_METHOD);
            zp.setStartSlice(1);
            zp.setStopSlice(findMinFrames.getCentralFrame()
                    - findMinFrames.getStartFrame());  // TODO: make sure this is always correct
            zp.doProjection();
            ImagePlus before = zp.getProjection();

            // make a minimum projection of this substack
            zp.setMethod(ZProjector.MIN_METHOD);
            zp.setStartSlice(1);
            zp.setStopSlice(imp.getNSlices());
            zp.doProjection();
            ImagePlus min = zp.getProjection();
            
            // Normalize (divide) the minimum projection with the average projection 
            ImageGray minBCV = BoofCVImageConverter.convert(
                    min.getProcessor().convertToFloatProcessor(), false);
            ImageGray beforeCV = BoofCVImageConverter.convert(
                    before.getProcessor().convertToFloatProcessor(), false);
            GrayF32 dResult = new GrayF32(minBCV.width, minBCV.height);
            GPixelMath.divide(minBCV, beforeCV, dResult);
            GrayF32 gResult = new GrayF32(minBCV.width, minBCV.height);
            BlurImageOps.gaussian(dResult, gResult, 3, -1, null);
            
            // ImageProcessor tmp = BoofCVImageConverter.convert(gResult, false);
            // ImagePlus tTmp = new ImagePlus("BoofCV", tmp);
            //tTmp.show();

            // Normalize (divivde) the minimum projection with the average projection 
            // ImageCalculator ic = new ImageCalculator();
            // ImagePlus result = ic.run("Divide 32-bit", min, before);
            // IJ.run(result, "Gaussian Blur...", "sigma=3");
            //result.show();

            // Find the minimum and define this as the bleachPoint
            //Point minPoint = findMinPixel(result.getProcessor());
            Point2D_I32 minPoint = findMinPixel(gResult);
            
            System.out.println("Lowest Pixel position: " + minPoint.x + ", " + minPoint.y);
            // check if this is within expected range
            if (Utils.distance(minPoint, middle) > MAXDISTANCE) {
               pasDataIt.remove();
               continue;
            }
            // Store coordinates indicating where the bleach actually happened
            // (in pixel coordinates of the original data)
            Point pasActual = new Point(roiX + minPoint.x, roiY + minPoint.y);

            // Calculate intensity in the subStack of a spot with diameter "radius"
            // TODO: evalute background
            // TODO: track spot
            // TODO: analyze complete subStack, while tracking moving spots
            // Estimate when the bleach actually happened by looking for frames
            // that are much brighter than the average
            // This is not always accurate
            List<Double> subStackIntensityData = new ArrayList<>();
            for (int slice = 1; slice <= subStack.getSize(); slice++) {
               ImageProcessor iProc = subStack.getProcessor(slice);
               subStackIntensityData.add((double) Utils.GetIntensity(iProc.convertToFloatProcessor(),
                       minPoint.x, minPoint.y, radius));
            }
            double avg = ListUtils.listAvg(subStackIntensityData);
            double stdDev = ListUtils.listStdDev(subStackIntensityData, avg);
            List<Integer> bleachFrames = new ArrayList<>();
            for (int frame = 0; frame < subStackIntensityData.size(); frame++) {
               if (subStackIntensityData.get(frame) > avg + 2 * stdDev) {
                  bleachFrames.add(frame);
               }
            }
            int[] pasFrames = new int[bleachFrames.size()];
            for (int frame = 0; frame < bleachFrames.size(); frame++) {
               pasFrames[frame] = bleachFrames.get(frame) + findMinFrames.getStartFrame();
            }

            // find average intensity of frames before bleaching for later normalization
            double preBleachAverage = Utils.GetIntensity(
                    before.getProcessor().convertToFloatProcessor(),
                    minPoint.x, minPoint.y, radius);

            // Get intensity data from the complete desired range of the stack
            PASFrameSet intensityAnalysisFrames = new PASFrameSet(
                    pasEntry.framePasClicked() - nrFramesBefore,
                    pasEntry.framePasClicked(),
                    pasEntry.framePasClicked() + nrFramesAfter,
                    dataProvider.getAxisLength(Coords.T));

            List<Double> intensityData = new ArrayList<>();
            for (int frame = intensityAnalysisFrames.getStartFrame();
                    frame < intensityAnalysisFrames.getEndFrame(); frame++) {
               Coords coord = cb.t(frame).build();
               Image img = dataProvider.getImage(coord);
               ImageProcessor iProc = studio_.data().getImageJConverter().createProcessor(img);
               intensityData.add((double) Utils.GetIntensity(iProc.convertToFloatProcessor(),
                       pasActual.x, pasActual.y, radius));
            }

            XYSeries data = new XYSeries("" + findMinFrames.getCentralFrame(), false, false);
            for (int i = 0; i < intensityData.size(); i++) {
               data.add(frameTimeStamps.get(intensityAnalysisFrames.getStartFrame() + i).toEpochMilli()
                       - frameTimeStamps.get(intensityAnalysisFrames.getCentralFrame()).toEpochMilli(),
                       intensityData.get(i) / preBleachAverage);
            }
            data.setKey(new DataSeriesKey(intensityAnalysisFrames.getCentralFrame(),
                    pasActual.x, pasActual.y));
            plotData.add(data);

            subStackIntensityData.forEach((val) -> {
               System.out.print(" " + val / preBleachAverage);
            });
            System.out.println();

            pasDataIt.set(pasEntry.copyBuilder().
                    pasActual(pasActual).
                    pasFrames(pasFrames).
                    build());

         }

         if (plotData.size() < 1) {
            studio_.logs().showMessage("No Point and Shoot events found");
            return;
         }

         XYSeries[] plots = plotData.toArray(new XYSeries[plotData.size()]);
         boolean[] showShapes = new boolean[plotData.size()];
         for (int i = 0; i < showShapes.length; i++) {
            showShapes[i] = true;
         }

         PlotUtils pu = new PlotUtils(studio_.profile().getSettings(this.getClass()));
         pu.plotDataN("Bleach Intensity Profile", plots, "Time (ms)",
                 "Normalized Intensity", showShapes, "", 1.3);

         // Track particle that received the bleach by local thresholding
         // First go backwards in time, then forward
         pasDataIt = pasData.listIterator();
         Coords.Builder cb = dataProvider.getAnyImage().getCoords().copyBuilder();
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
                       Utils.distance(currentPoint, nextParticle.getCentroid()) < maxDistance )) {
                  currentPoint = nextParticle.getCentroid();
                  track.put(frame, nextParticle);
               } else {
                  System.out.println("Before Missing particle");
                  // TODO: increase counter, give up when too high
               }
            }

            // now go forward in time
            currentPoint = firstParticle.getCentroid().copy();
            GrayU16 preBleach = (GrayU16) BoofCVImageConverter.subImage(dataProvider, cb, 
                    pasEntry.framePasClicked() + 1, currentPoint, halfROISize_);
            GrayF32 fPreBleach = new GrayF32(preBleach.getWidth(), preBleach.getHeight());
            ConvertImage.convert(preBleach, fPreBleach);
            ParticleData previousParticle = null;
            for (int frame = pasEntry.framePasClicked() + 2;
                    frame < dataProvider.getAxisLength(Coords.T); frame++) {
               ParticleData nextParticle = ParticleData.centralParticle(dataProvider, 
                       cb, frame, currentPoint, halfROISize_);
               if (nextParticle == null || ( 
                       Utils.distance(currentPoint, nextParticle.getCentroid()) > maxDistance )) {
                  if (previousParticle != null) {
                     nextParticle = previousParticle.copy();
                  }
                  System.out.println("After Missing particle");
                  // TODO: increase counter, give up when too high
               } //else {
               
               ImageGray current = BoofCVImageConverter.subImage(dataProvider, 
                        cb, frame, currentPoint, halfROISize_);
               nextParticle = ParticleData.addBleachSpotToParticle(fPreBleach, (GrayU16) current, nextParticle, 
                        new Point2D_I32(currentPoint.x - halfROISize_, currentPoint.y - halfROISize_), MAXDISTANCE );
               //}
               previousParticle = nextParticle;
               currentPoint = nextParticle.getCentroid();
               track.put(frame, nextParticle);
            }
            tracks.add(track);
         }

         if (activeDataViewer instanceof DisplayWindow) {
            DisplayWindow dw = (DisplayWindow) activeDataViewer;
            dw.addOverlay(new Overlay(tracks));
         }

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
    * @param ip
    * @return
    */
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
      /*
     NormalizedCrossCorrelation ncc = new NormalizedCrossCorrelation( 
             (ShortProcessor)  iProc);
     return ncc.correlate((ShortProcessor) iProc2, p, new Point(2,2));
*/
   
   }
   
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
