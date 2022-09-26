
package org.micromanager.magellan.api;

/**
 *
 * @author henrypinkard
 */
public interface MagellanAcquisitionSettingsAPI {
   
   void setNumTimePoints(int nTimePoints);
   
   void setTimeInterval(double interval, String unit);
   
   void setTimeEnabled(boolean enable);
   
   void setZStep(double zStepUm);
   
   void setZStart(double zStartUm);
   
   void setZEnd(double zEndUm);
      
   /**
    *
    * @param type One of: "3d_cuboid", "3d_between_surfaces", "3d_distance_from_surface",
    *             "2d_flat", "2d_surface"
    */
   void setAcquisitionSpaceType(String type);
   
   void setAcquisitionOrder(String order);
   
   void setXYPositionSource(String surfaceOrGridName);
   
   void setTopSurface(String topSurfaceName);
   
   void setBottomSurface(String bottomSurfaceName);
   
   void setSurface(String withinDistanceSurfaceName);
   
   void setChannelGroup(String channelGroup);

   void setUseChannel(String channelName, boolean use);

   void setChannelExposure(String channelName, double exposure);

   void setChannelZOffset(String channelName, double offset);
   
   void setSavingDir(String dirPath);
   
   void setAcquisitionName(String newName);
   
   
}
