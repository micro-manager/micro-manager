/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.magellan.api;

/**
 *
 * @author henrypinkard
 */
public interface MagellanAcquisitionSettingsAPI {
   
   public void setNumTimePoints(int nTimePoints);
   
   public void setTimeInterval(double interval, String unit);
   
   public void setTimeEnabled(boolean enable);
   
   public void setZStep(double zStep_um);
   
   public void setZStart(double zStart_um);
   
   public void setZEnd(double zEnd_um);
      
   /**
    *
    * @param type One of: "3d_cuboid", "3d_between_surfaces", "3d_distance_from_surface", "2d_flat", "2d_surface"
    */
   public void setAcquisitionSpaceType(String type);
   
   public void setAcquisitionOrder(String order);
   
   public void setXYPositionSource(String surfaceOrGridName);
   
   public void setTopSurface(String topSurfaceName);
   
   public void setBottomSurface(String bottomSurfaceName);
   
   public void setSurface(String withinDistanceSurfaceName);
   
   public void setTileOverlapPercent(double overlapPercent);

   public void setChannelGroup(String channelGroup);

   public void setUseChannel(String channelName, boolean use);

   public void setChannelExposure(String channelName, double exposure);

   public void setChannelZOffset(String channelName, double offset);
   
   public void setSavingDir(String dirPath);
   
   public void setAcquisitionName(String newName);
   
   
}
