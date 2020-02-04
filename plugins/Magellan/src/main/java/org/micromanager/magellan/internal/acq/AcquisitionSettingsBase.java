/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.magellan.internal.acq;

import org.micromanager.magellan.api.MagellanAcquisitionSettingsAPI;
import org.micromanager.magellan.internal.channels.MagellanChannelSpec;
import org.micromanager.magellan.internal.gui.GUI;
import org.micromanager.magellan.internal.surfacesandregions.SurfaceGridManager;
import org.micromanager.magellan.internal.surfacesandregions.SurfaceInterpolator;
import org.micromanager.magellan.internal.surfacesandregions.XYFootprint;

/**
 *
 * @author henrypinkard
 */
public abstract class AcquisitionSettingsBase implements MagellanAcquisitionSettingsAPI {

   public static final int NO_SPACE = 0;
   public static final int CUBOID_Z_STACK = 1;
   public static final int SURFACE_FIXED_DISTANCE_Z_STACK = 2;
   public static final int VOLUME_BETWEEN_SURFACES_Z_STACK = 3;
   public static final int REGION_2D = 4;
   public static final int REGION_2D_SURFACE_GUIDED = 5;

   public static final int TIME_MS = 0;
   public static final int TIME_S = 1;
   public static final int TIME_MIN = 2;

   //saving
   public String dir_, name_;

   //channels
   public MagellanChannelSpec channels_;
   public volatile String channelGroup_;

   //time
   public volatile boolean timeEnabled_;
   public volatile double timePointInterval_;
   public volatile int numTimePoints_;
   public volatile int timeIntervalUnit_;

   //space
   public volatile double zStep_, zStart_, zEnd_, distanceBelowFixedSurface_, distanceAboveFixedSurface_,
           distanceAboveTopSurface_, distanceBelowBottomSurface_;
   public volatile int spaceMode_;
   public volatile XYFootprint xyFootprint_;
   public volatile SurfaceInterpolator topSurface_, bottomSurface_, fixedSurface_, collectionPlane_;

   //space
   public volatile double tileOverlap_; //stored as percent * 100, i.e. 55 => 55%
   public volatile boolean channelsAtEverySlice_;

   @Override
   public void setChannelGroup(String channelGroup) {
      channelGroup_ = channelGroup;
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

   public void setTileOverlapPercent(double overlapPercent) {
      tileOverlap_ = overlapPercent;
      GUI.getInstance().refreshAcqControlsFromSettings();
   }

   public void setSavingDir(String dirPath) {
      dir_ = dirPath;
      GUI.getInstance().refreshAcqControlsFromSettings();
   }
   
   public void setAcquisitionName(String newName) {
      name_ = newName;
      GUI.getInstance().refreshAcqControlsFromSettings();
   }
   
   public void setAcquisitionOrder(String order) {
      if (order.equals("cz")) {
          channelsAtEverySlice_ = false;
      } else if  (order.equals("zc")) {
         channelsAtEverySlice_ = true;
      } else {
         throw new RuntimeException("Unrecognized acquisition order");
      }
   }

   
}
