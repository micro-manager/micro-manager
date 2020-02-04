///////////////////////////////////////////////////////////////////////////////
// AUTHOR:       Henry Pinkard, henry.pinkard@gmail.com
//
// COPYRIGHT:    University of California, San Francisco, 2015
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
//
package org.micromanager.magellan.internal.acq;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.prefs.Preferences;
import org.apache.commons.math3.analysis.interpolation.LinearInterpolator;
import org.apache.commons.math3.analysis.polynomials.PolynomialSplineFunction;
import org.micromanager.magellan.internal.coordinates.XYStagePosition;
import org.micromanager.magellan.internal.gui.GUI;
import org.micromanager.magellan.internal.main.Magellan;
import org.micromanager.magellan.internal.misc.GlobalSettings;
import org.micromanager.magellan.internal.misc.Log;

public class AcqDurationEstimator {

   private static int MAX_DATA_POINTS = 10;
   private static final String EXPOSURE_KEY = "Exposure_Time_Map";
   private static final String XY_KEY = "XY_Time_Map";
   private static final String Z_KEY = "ZMove_Time_Map";
   private static final String CHANNEL_KEY = "Channel_Switch_Time_Map";

   private ExecutorService executor_;
   private Future<?> currentTask_;
   private TreeMap<Double, LinkedList<Double>> exposureMap_; // hold entries for interpolation
   private LinkedList<Double> xyMoveTimeList_, zStepMoveTimeList_, channelSwitchTimeList_;

   public AcqDurationEstimator() {
      executor_ = Executors.newSingleThreadExecutor(new ThreadFactory() {
         @Override
         public Thread newThread(Runnable r) {
            return new Thread(r, "Acquisition duration estimation Thread");
         }
      });

      Preferences prefs = Preferences.userNodeForPackage(AcqDurationEstimator.class);
      //populate with one from preferences
      exposureMap_ = GlobalSettings.getObjectFromPrefs(prefs, EXPOSURE_KEY, new TreeMap<Double, LinkedList<Double>>());
      xyMoveTimeList_ = GlobalSettings.getObjectFromPrefs(prefs, XY_KEY, new LinkedList<Double>());
      zStepMoveTimeList_ = GlobalSettings.getObjectFromPrefs(prefs, Z_KEY, new LinkedList<Double>());
      channelSwitchTimeList_ = GlobalSettings.getObjectFromPrefs(prefs, CHANNEL_KEY, new LinkedList<Double>());

   }

   public void storeChannelSwitchTime(double time) {
      channelSwitchTimeList_.add(time);
      if (channelSwitchTimeList_.size() > MAX_DATA_POINTS) {
         channelSwitchTimeList_.removeFirst();
      }
   }

   public void storeZMoveTime(double time) {
      zStepMoveTimeList_.add(time);
      if (zStepMoveTimeList_.size() > MAX_DATA_POINTS) {
         zStepMoveTimeList_.removeFirst();
      }
   }

   public void storeXYMoveTime(double time) {
      xyMoveTimeList_.add(time);
      if (xyMoveTimeList_.size() > MAX_DATA_POINTS) {
         xyMoveTimeList_.removeFirst();
      }
   }

   public void storeImageAcquisitionTime(double exposure, double time) {
      if (!exposureMap_.containsKey(exposure)) {
         exposureMap_.put(exposure, new LinkedList<Double>());
      }
      exposureMap_.get(exposure).add(time);
      //limit size
      if (exposureMap_.get(exposure).size() > MAX_DATA_POINTS) {
         exposureMap_.get(exposure).removeFirst();
      }
   }

   public double averageList(LinkedList<Double> list) {
      double avgTime = 0;
      for (double t : list) {
         avgTime += t;
      }
      avgTime /= list.size();
      return avgTime;
   }

   public double estimateImageAcquisitionTime(double exposure) {
      double[] exposures = new double[exposureMap_.keySet().size()];
      double[] avgTimes = new double[exposureMap_.keySet().size()];
      int i = 0;
      for (double e : exposureMap_.keySet()) {
         exposures[i] = e;
         LinkedList<Double> times = exposureMap_.get(e);
         double avgTime = 0;
         for (double t : times) {
            avgTime += t;
         }
         avgTime /= times.size();
         avgTimes[i] = avgTime;
         i++;
      }
      return interpolateOrExtrapolate(exposures, avgTimes, exposure);
   }

   private double interpolateOrExtrapolate(double[] x, double[] y, double xVal) {
      if (x.length == 1) {
         return y[0];
      }
      LinearInterpolator interpolator = new LinearInterpolator();
      PolynomialSplineFunction interpolant = interpolator.interpolate(x, y);
      if (xVal < interpolant.getKnots()[0]) {
         return interpolant.getKnots()[0];
      } else if (xVal > interpolant.getKnots()[interpolant.getN() - 1]) {
         return interpolant.getKnots()[interpolant.getN() - 1];
      } else {
         return interpolant.value(xVal);
      }
   }

   public synchronized void calcAcqDuration(MagellanGUIAcquisitionSettings settings) {
      if (currentTask_ != null && !currentTask_.isDone()) {
         currentTask_.cancel(true);
      }
      currentTask_ = executor_.submit(estimateDuration(settings));
   }

   private void checkForInterrupt() throws InterruptedException {
      if (Thread.interrupted()) {
         throw new InterruptedException();
      }
   }

   private Runnable estimateDuration(final MagellanGUIAcquisitionSettings settings) {
      return new Runnable() {
         @Override
         public void run() {
            try {

               double imageTime = estimateImageAcquisitionTime(Magellan.getCore().getExposure());
               checkForInterrupt();
               double xyMoveTime = averageList(xyMoveTimeList_);
               checkForInterrupt();
               double zMoveTime = averageList(zStepMoveTimeList_);
               checkForInterrupt();
               double channelMoveTime = averageList(channelSwitchTimeList_);
               checkForInterrupt();

               List<XYStagePosition> positions = getXYPositions(settings);
               long numImagesAcquired = 0, xyMoves = 0, zMoves = 0, channelSwitches = -1, numImages = 0;
               double zOrigin = MagellanGUIAcquisition.getZTopCoordinate(settings.spaceMode_,
                       settings, false, 0, 0, Magellan.getCore().getFocusDevice());
               for (XYStagePosition pos : positions) {
                  int sliceIndex = 0;
                  if (!MagellanGUIAcquisition.isImagingVolumeUndefinedAtPosition(settings.spaceMode_, settings, pos)) {
                     xyMoves++;
                  }
                  while (true) {
                     checkForInterrupt();
                     double zPos = zOrigin + sliceIndex * settings.zStep_;
                     if ((settings.spaceMode_ == MagellanGUIAcquisitionSettings.REGION_2D || 
                             settings.spaceMode_ == MagellanGUIAcquisitionSettings.REGION_2D_SURFACE_GUIDED ||
                             settings.spaceMode_ == MagellanGUIAcquisitionSettings.NO_SPACE)
                             && sliceIndex > 0) {
                        numImagesAcquired++;
                        numImages++;
                        xyMoves++;
                        break; //2D regions only have 1 slice
                     }

                     if (MagellanGUIAcquisition.isImagingVolumeUndefinedAtPosition(settings.spaceMode_, settings, pos)) {
                        break;
                     }

                     if (MagellanGUIAcquisition.isZBelowImagingVolume(settings.spaceMode_, settings, pos, zPos, zOrigin)) {
                        //position is below z stack or limit of focus device, z stack finished
                        break;
                     }
                     //3D region
                     if (MagellanGUIAcquisition.isZAboveImagingVolume(settings.spaceMode_, settings, pos, zPos, zOrigin)) {
                        sliceIndex++;
                        continue; //position is above imaging volume or range of focus device
                     }

                     numImagesAcquired++;
                     String channelName = settings.channels_.nextActiveChannel(null);
                     while (channelName != null) {
                        channelName = settings.channels_.nextActiveChannel(channelName);
                        if (!settings.channels_.getChannelSetting(channelName).uniqueEvent_) {
                              continue;
                           }
                        channelSwitches++;
                        numImagesAcquired++;
                     }
        
                     
                     sliceIndex++;
                     zMoves++;
                  } //slice loop finish

               }

               double estimatedTime = numImagesAcquired * imageTime + xyMoveTime * xyMoves + zMoveTime * zMoves + channelMoveTime * channelSwitches;
               if (settings.timeEnabled_) {
                  estimatedTime = settings.numTimePoints_ * Math.max(estimatedTime,
                          settings.timePointInterval_ * (settings.timeIntervalUnit_ == 1 ? 1000 : (settings.timeIntervalUnit_ == 2 ? 60000 : 1)));
               }
               long hours = (long) (estimatedTime / 60 / 60 / 1000),
                       minutes = (long) (estimatedTime / 60 / 1000), seconds = (long) (estimatedTime / 1000);

               minutes = minutes % 60;
               seconds = seconds % 60;
               String h = ("0" + hours).substring(("0" + hours).length() - 2);
               String m = ("0" + (minutes)).substring(("0" + minutes).length() - 2);
               String s = ("0" + (seconds)).substring(("0" + seconds).length() - 2);

               //estiamte amount of data
               numImages *= (settings.timeEnabled_ ? settings.numTimePoints_ : 1);
               numImages *= Magellan.getCore().getBytesPerPixel() * Magellan.getCore().getImageWidth() * Magellan.getCore().getImageHeight();
               long kb = numImages / 1024;
               long mb = kb / 1024;
               double gb = mb / 1024.0;
               String sizeLabel = "Estimated size: " + (gb > 1 ? String.format("%.2f GB", gb) : mb + " MB");
               
               GUI.updateEstiamtedDurationLabel("Estimated duration: " + h + ":" + m + ":" + s + " (H:M:S)");
               GUI.updateEstiamtedSizeLabel(sizeLabel);

               Preferences prefs = Preferences.userNodeForPackage(AcqDurationEstimator.class);

               //store
               GlobalSettings.putObjectInPrefs(prefs, EXPOSURE_KEY, exposureMap_);
               GlobalSettings.putObjectInPrefs(prefs, XY_KEY, xyMoveTimeList_);
               GlobalSettings.putObjectInPrefs(prefs, Z_KEY, zStepMoveTimeList_);
               GlobalSettings.putObjectInPrefs(prefs, CHANNEL_KEY, channelSwitchTimeList_);

            } catch (InterruptedException ex) {
               return; //Interrupted, return with no result
            } catch (Exception e) {
               GUI.updateEstiamtedDurationLabel("Error estimting acquisiton duration");
               return;
            }
         }
      };
   }

   private List<XYStagePosition> getXYPositions(MagellanGUIAcquisitionSettings settings) throws Exception, InterruptedException {
      List<XYStagePosition> list;
      if(settings.xyFootprint_ == null) {
         list = new ArrayList<XYStagePosition>();
         list.add(new XYStagePosition(new Point2D.Double(), 0, 0, ""));
      } else {              
         list = settings.xyFootprint_.getXYPositionsNoUpdate();
      }
      return list;
   }

}
