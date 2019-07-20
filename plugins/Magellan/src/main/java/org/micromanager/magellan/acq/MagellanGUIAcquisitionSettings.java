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
package org.micromanager.magellan.acq;

import java.util.prefs.Preferences;
import org.micromanager.magellan.channels.MagellanChannelSpec;
import org.micromanager.magellan.gui.GUI;
import org.micromanager.magellan.main.Magellan;
import org.micromanager.magellan.surfacesandregions.SurfaceInterpolator;
import org.micromanager.magellan.surfacesandregions.XYFootprint;
import org.micromanager.propertymap.MutablePropertyMapView;

/**
 *
 * @author Henry
 */
public class MagellanGUIAcquisitionSettings {
   
   public static final String PREF_PREFIX = "Fixed area acquisition ";

   public static final int NO_SPACE = 0;
   public static final int CUBOID_Z_STACK = 1;
   public static final int SURFACE_FIXED_DISTANCE_Z_STACK = 2;
   public static final int VOLUME_BETWEEN_SURFACES_Z_STACK = 3;
   public static final int REGION_2D = 4;
   public static final int TIME_MS = 0;
   public static final int TIME_S = 1;
   public static final int TIME_MIN = 2;
   public static int FOOTPRINT_FROM_TOP = 0, FOOTPRINT_FROM_BOTTOM = 1;
   
   //saving
   public String dir_, name_;
   //time
   public volatile boolean timeEnabled_;
   public volatile double timePointInterval_;
   public volatile int numTimePoints_;
   public volatile int timeIntervalUnit_; 

   //space
   public volatile double zStep_, zStart_, zEnd_, distanceBelowFixedSurface_, distanceAboveFixedSurface_,
           distanceAboveTopSurface_, distanceBelowBottomSurface_;
   public volatile int spaceMode_;
   public volatile boolean useCollectionPlane_ = false;
   public volatile SurfaceInterpolator topSurface_, bottomSurface_, fixedSurface_, collectionPlane_;
   public volatile XYFootprint footprint_;
   public volatile int useTopOrBottomFootprint_;
   public volatile double tileOverlap_; //stored as percent * 100, i.e. 55 => 55%
   public volatile boolean channelsAtEverySlice_;
   
   //channels
   public volatile String channelGroup_;
   public volatile MagellanChannelSpec channels_ ;

   public MagellanGUIAcquisitionSettings() {
      MutablePropertyMapView prefs = Magellan.getStudio().profile().getSettings(MagellanGUIAcquisitionSettings.class);
      name_ = prefs.getString(PREF_PREFIX + "NAME", "Untitled");
      timeEnabled_ = prefs.getBoolean(PREF_PREFIX + "TE", false);
      timePointInterval_ = prefs.getDouble(PREF_PREFIX + "TPI", 0);
      numTimePoints_ = prefs.getInteger(PREF_PREFIX + "NTP", 1);
      timeIntervalUnit_ = prefs.getInteger(PREF_PREFIX + "TPIU", 0);
      //space
      useCollectionPlane_ = prefs.getBoolean(PREF_PREFIX +"USECOLLECTIONPLANE", false);
      channelsAtEverySlice_ = prefs.getBoolean(PREF_PREFIX +"ACQORDER", true);
      zStep_ = prefs.getDouble(PREF_PREFIX + "ZSTEP", 1);
      zStart_ = prefs.getDouble(PREF_PREFIX + "ZSTART", 0);
      zEnd_ = prefs.getDouble(PREF_PREFIX + "ZEND", 0);
      distanceBelowFixedSurface_ = prefs.getDouble(PREF_PREFIX + "ZDISTBELOWFIXED", 0);
      distanceAboveFixedSurface_ = prefs.getDouble(PREF_PREFIX + "ZDISTABOVEFIXED", 0);
      distanceBelowBottomSurface_ = prefs.getDouble(PREF_PREFIX + "ZDISTBELOWBOTTOM", 0);
      distanceAboveTopSurface_ = prefs.getDouble(PREF_PREFIX + "ZDISTABOVETOP", 0);
      spaceMode_ = prefs.getInteger(PREF_PREFIX + "SPACEMODE", 0);
      tileOverlap_ = prefs.getDouble(PREF_PREFIX + "TILEOVERLAP", 5);
      //channels
      channelGroup_ = prefs.getString(PREF_PREFIX + "CHANNELGROUP", "");
      //This creates a new Object of channelSpecs that is "Owned" by the accquisition
      channels_ = new MagellanChannelSpec(channelGroup_); 
   }
   
   public static double getStoredTileOverlapPercentage() {
      MutablePropertyMapView prefs = Magellan.getStudio().profile().getSettings(MagellanGUIAcquisitionSettings.class);
      return prefs.getDouble(PREF_PREFIX + "TILEOVERLAP", 5);
   }
   
   public void storePreferedValues() {
      MutablePropertyMapView prefs = Magellan.getStudio().profile().getSettings(MagellanGUIAcquisitionSettings.class);
      prefs.putString(PREF_PREFIX + "NAME", name_);
      prefs.putBoolean(PREF_PREFIX + "TE", timeEnabled_);
      prefs.putDouble(PREF_PREFIX + "TPI", timePointInterval_);
      prefs.putInteger(PREF_PREFIX + "NTP", numTimePoints_);
      prefs.putInteger(PREF_PREFIX + "TPIU", timeIntervalUnit_);
      //space
      prefs.putBoolean(PREF_PREFIX +"USECOLLECTIONPLANE", useCollectionPlane_);
      prefs.putDouble(PREF_PREFIX + "ZSTEP", zStep_);
      prefs.putDouble(PREF_PREFIX + "ZSTART", zStart_);
      prefs.putDouble(PREF_PREFIX + "ZEND", zEnd_);
      prefs.putDouble(PREF_PREFIX + "ZDISTBELOWFIXED", distanceBelowFixedSurface_);
      prefs.putDouble(PREF_PREFIX + "ZDISTABOVEFIXED", distanceAboveFixedSurface_);
      prefs.putDouble(PREF_PREFIX + "ZDISTBELOWBOTTOM", distanceBelowBottomSurface_);
      prefs.putDouble(PREF_PREFIX + "ZDISTABOVETOP", distanceAboveTopSurface_);
      prefs.putInteger(PREF_PREFIX + "SPACEMODE", spaceMode_);
      prefs.putDouble(PREF_PREFIX + "TILEOVERLAP", tileOverlap_);
      prefs.putBoolean(PREF_PREFIX + "ACQORDER", channelsAtEverySlice_);
      //channels
      prefs.putString(PREF_PREFIX + "CHANNELGROUP", channelGroup_);
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
      
      int nChannels = 1;
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

}
