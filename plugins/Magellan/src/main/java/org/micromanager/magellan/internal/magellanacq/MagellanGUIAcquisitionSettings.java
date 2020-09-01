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
package org.micromanager.magellan.internal.magellanacq;

import org.micromanager.magellan.api.MagellanAcquisitionSettingsAPI;
import org.micromanager.magellan.internal.channels.ChannelGroupSettings;
import org.micromanager.magellan.internal.gui.GUI;
import org.micromanager.magellan.internal.main.Magellan;
import org.micromanager.magellan.internal.surfacesandregions.SurfaceGridManager;
import org.micromanager.magellan.internal.surfacesandregions.SurfaceInterpolator;
import org.micromanager.magellan.internal.surfacesandregions.XYFootprint;
import org.micromanager.propertymap.MutablePropertyMapView;

/**
 *
 * @author Henry
 */
public class MagellanGUIAcquisitionSettings extends MagellanGenericAcquisitionSettings
        implements MagellanAcquisitionSettingsAPI {

   public static final int TIME_MS = 0;
   public static final int TIME_S = 1;
   public static final int TIME_MIN = 2;

   public static final int NO_SPACE = 0;
   public static final int CUBOID_Z_STACK = 1;
   public static final int SURFACE_FIXED_DISTANCE_Z_STACK = 2;
   public static final int VOLUME_BETWEEN_SURFACES_Z_STACK = 3;
   public static final int REGION_2D = 4;
   public static final int REGION_2D_SURFACE_GUIDED = 5;

   public static final String PREF_PREFIX = "Fixed area acquisition ";

   public static int FOOTPRINT_FROM_TOP = 0, FOOTPRINT_FROM_BOTTOM = 1;

   //space
   public volatile double zStart_, zEnd_, distanceBelowFixedSurface_, distanceAboveFixedSurface_,
           distanceAboveTopSurface_, distanceBelowBottomSurface_;
   public volatile int spaceMode_;
   public volatile XYFootprint xyFootprint_;
   public volatile SurfaceInterpolator topSurface_, bottomSurface_, fixedSurface_, collectionPlane_;

   //time
   public volatile boolean timeEnabled_;
   public volatile double timePointInterval_;
   public volatile int numTimePoints_;
   public volatile int timeIntervalUnit_;

   public MagellanGUIAcquisitionSettings() {
      super();
      //now replace everything with values gotten from preferences
      MutablePropertyMapView prefs = Magellan.getStudio().profile().getSettings(MagellanGUIAcquisitionSettings.class);
      if (GUI.getInstance() != null && GUI.getInstance().getSavingDir() != null) { //To avoid error on init
         dir_ = GUI.getInstance().getSavingDir();
      }
      name_ = prefs.getString(PREF_PREFIX + "NAME", "Untitled");
      timeEnabled_ = prefs.getBoolean(PREF_PREFIX + "TE", false);
      timePointInterval_ = prefs.getDouble(PREF_PREFIX + "TPI", 0);
      numTimePoints_ = prefs.getInteger(PREF_PREFIX + "NTP", 1);
      timeIntervalUnit_ = prefs.getInteger(PREF_PREFIX + "TPIU", 0);
      //space
      channelsAtEverySlice_ = prefs.getBoolean(PREF_PREFIX + "ACQORDER", true);
      zStep_ = prefs.getDouble(PREF_PREFIX + "ZSTEP", 1);
      zStart_ = prefs.getDouble(PREF_PREFIX + "ZSTART", 0);
      zEnd_ = prefs.getDouble(PREF_PREFIX + "ZEND", 0);
      distanceBelowFixedSurface_ = prefs.getDouble(PREF_PREFIX + "ZDISTBELOWFIXED", 0);
      distanceAboveFixedSurface_ = prefs.getDouble(PREF_PREFIX + "ZDISTABOVEFIXED", 0);
      distanceBelowBottomSurface_ = prefs.getDouble(PREF_PREFIX + "ZDISTBELOWBOTTOM", 0);
      distanceAboveTopSurface_ = prefs.getDouble(PREF_PREFIX + "ZDISTABOVETOP", 0);
      spaceMode_ = prefs.getInteger(PREF_PREFIX + "SPACEMODE", 0);
      //channels
      String channelGroup = prefs.getString(PREF_PREFIX + "CHANNELGROUP", "");
      //This creates a new Object of channelSpecs that is "Owned" by the accquisition
      channels_ = new ChannelGroupSettings(channelGroup);
   }

   public void storePreferedValues() {
      MutablePropertyMapView prefs = Magellan.getStudio().profile().getSettings(MagellanGUIAcquisitionSettings.class);
      prefs.putString(PREF_PREFIX + "NAME", name_);
      prefs.putBoolean(PREF_PREFIX + "TE", timeEnabled_);
      prefs.putDouble(PREF_PREFIX + "TPI", timePointInterval_);
      prefs.putInteger(PREF_PREFIX + "NTP", numTimePoints_);
      prefs.putInteger(PREF_PREFIX + "TPIU", timeIntervalUnit_);
      //space
      prefs.putDouble(PREF_PREFIX + "ZSTEP", zStep_);
      prefs.putDouble(PREF_PREFIX + "ZSTART", zStart_);
      prefs.putDouble(PREF_PREFIX + "ZEND", zEnd_);
      prefs.putDouble(PREF_PREFIX + "ZDISTBELOWFIXED", distanceBelowFixedSurface_);
      prefs.putDouble(PREF_PREFIX + "ZDISTABOVEFIXED", distanceAboveFixedSurface_);
      prefs.putDouble(PREF_PREFIX + "ZDISTBELOWBOTTOM", distanceBelowBottomSurface_);
      prefs.putDouble(PREF_PREFIX + "ZDISTABOVETOP", distanceAboveTopSurface_);
      prefs.putInteger(PREF_PREFIX + "SPACEMODE", spaceMode_);
      prefs.putBoolean(PREF_PREFIX + "ACQORDER", channelsAtEverySlice_);
      //channels
      prefs.putString(PREF_PREFIX + "CHANNELGROUP", channels_.getChannelGroup());
      //Individual channel settings sotred in ChannelUtils
   }

   public String toString() {
      String s = "";
      if (spaceMode_ == CUBOID_Z_STACK) {
         s += "Cuboid volume";
      } else if (spaceMode_ == SURFACE_FIXED_DISTANCE_Z_STACK) {
         s += "Volume within distance from surface";
      } else if (spaceMode_ == VOLUME_BETWEEN_SURFACES_Z_STACK) {
         s += "Volume bounded by surfaces";
      } else if (spaceMode_ == REGION_2D && collectionPlane_ == null) {
         s += "2D plane";
      } else {
         s += "2D along surface";
      }

      int nChannels = 0;
      String chName = channels_.nextActiveChannel(null);
      while (chName != null) {
         nChannels++;
         chName = channels_.nextActiveChannel(chName);
      }
      if (nChannels > 1) {
         s += " " + nChannels + " channels";
      }
      if (timeEnabled_) {
         s += " " + numTimePoints_ + " time points";
      }
      return s;
   }

   @Override
   public void setSavingDir(String dirPath) {
      dir_ = dirPath;
   }

   @Override
   public void setAcquisitionName(String newName) {
      name_ = newName;
      GUI.getInstance().refreshAcqControlsFromSettings();
   }

   @Override
   public void setChannelGroup(String channelGroup) {
      channels_.updateChannelGroup(channelGroup);
      GUI.getInstance().refreshAcqControlsFromSettings();
   }

   @Override
   public void setUseChannel(String channelName, boolean use) {
      channels_.getChannelSetting(channelName).use_ = use;
      GUI.getInstance().refreshAcqControlsFromSettings();
   }

   @Override
   public void setChannelExposure(String channelName, double exposure) {
      channels_.getChannelSetting(channelName).exposure_ = exposure;
      GUI.getInstance().refreshAcqControlsFromSettings();
   }

   @Override
   public void setChannelZOffset(String channelName, double offset) {
      channels_.getChannelSetting(channelName).offset_ = offset;
      GUI.getInstance().refreshAcqControlsFromSettings();
   }

   public void setZStep(double zStep_um) {
      zStep_ = zStep_um;
      GUI.getInstance().refreshAcqControlsFromSettings();
   }

   public void setZStart(double zStart_um) {
      zStart_ = zStart_um;
      distanceAboveFixedSurface_ = zStart_um;
      distanceAboveTopSurface_ = zStart_um;
      GUI.getInstance().refreshAcqControlsFromSettings();
   }

   public void setZEnd(double zEnd_um) {
      zEnd_ = zEnd_um;
      distanceBelowFixedSurface_ = zEnd_um;
      distanceBelowBottomSurface_ = zEnd_um;
      GUI.getInstance().refreshAcqControlsFromSettings();
   }

   public void setAcquisitionSpaceType(String type) {
      if (type.equals("3d_cuboid")) {
         spaceMode_ = CUBOID_Z_STACK;
      } else if (type.equals("3d_between_surfaces")) {
         spaceMode_ = VOLUME_BETWEEN_SURFACES_Z_STACK;
      } else if (type.equals("3d_distance_from_surface")) {
         spaceMode_ = SURFACE_FIXED_DISTANCE_Z_STACK;
      } else if (type.equals("2d_flat")) {
         spaceMode_ = REGION_2D;
      } else if (type.equals("2d_surface")) {
         spaceMode_ = REGION_2D_SURFACE_GUIDED;
      } else {
         throw new RuntimeException("Unrecognized acquisition space type");
      }
      GUI.getInstance().refreshAcqControlsFromSettings();
   }

   public void setXYPositionSource(String surfaceOrGridName) {
      XYFootprint xy = SurfaceGridManager.getInstance().getSurfaceNamed(surfaceOrGridName);
      if (xy == null) {
         xy = SurfaceGridManager.getInstance().getGridNamed(surfaceOrGridName);
      }
      if (xy == null) {
         throw new RuntimeException("No surface or grid named: " + surfaceOrGridName);
      }
      xyFootprint_ = xy;
      GUI.getInstance().refreshAcqControlsFromSettings();
   }

   public void setTopSurface(String topSurfaceName) {
      SurfaceInterpolator s = SurfaceGridManager.getInstance().getSurfaceNamed(topSurfaceName);
      if (s == null) {
         throw new RuntimeException("No surface named: " + topSurfaceName);
      }
      topSurface_ = s;
      GUI.getInstance().refreshAcqControlsFromSettings();
   }

   public void setBottomSurface(String bottomSurfaceName) {
      SurfaceInterpolator s = SurfaceGridManager.getInstance().getSurfaceNamed(bottomSurfaceName);
      if (s == null) {
         throw new RuntimeException("No surface named: " + bottomSurfaceName);
      }
      bottomSurface_ = s;
      GUI.getInstance().refreshAcqControlsFromSettings();
   }

   public void setSurface(String withinDistanceSurfaceName) {
      SurfaceInterpolator s = SurfaceGridManager.getInstance().getSurfaceNamed(withinDistanceSurfaceName);
      if (s == null) {
         throw new RuntimeException("No surface named: " + withinDistanceSurfaceName);
      }
      fixedSurface_ = s;
      collectionPlane_ = s;
      GUI.getInstance().refreshAcqControlsFromSettings();
   }

   @Override
   public void setTimeEnabled(boolean enable) {
      timeEnabled_ = enable;
      GUI.getInstance().refreshAcqControlsFromSettings();
   }

   @Override
   public void setNumTimePoints(int nTimePoints) {
      numTimePoints_ = nTimePoints;
      GUI.getInstance().refreshAcqControlsFromSettings();
   }

   @Override
   public void setTimeInterval(double interval, String unit) {
      //0 is ms, 1 is s, 2 is min
      if (!unit.equals("ms") && !unit.equals("s") && !unit.equals("min")) {
         throw new RuntimeException("Time unit must equal \"ms\", \"s\", or \"min\"");
      }
      timePointInterval_ = interval;
      timeIntervalUnit_ = (unit.equals("ms") ? TIME_MS : (unit.equals("s") ? TIME_S : TIME_MS));
      GUI.getInstance().refreshAcqControlsFromSettings();
   }

}
