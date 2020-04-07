///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     Data API implementation
//-----------------------------------------------------------------------------
//
// AUTHOR:       Chris Weisiger, 2015
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

package org.micromanager.data.internal;

import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.util.UUID;
import org.micromanager.PropertyMap;
import org.micromanager.PropertyMaps;
import org.micromanager.data.Metadata;
import static org.micromanager.data.internal.PropertyKey.*;

/**
 * This class holds the metadata for ImagePlanes. It is intended to be 
 * immutable; construct new Metadatas using a MetadataBuilder, or by using
 * the copy() method (which provides a MetadataBuilder). Any fields that are
 * not explicitly set will default to null.
 */
public final class DefaultMetadata implements Metadata {
   public static class Builder implements Metadata.Builder {
      private final PropertyMap.Builder b_;

      public Builder() {
         b_ = PropertyMaps.builder();
      }

      private Builder(PropertyMap toCopy) {
         b_ = toCopy.copyBuilder();
      }

      @Override
      public DefaultMetadata build() {
         return new DefaultMetadata(b_.build());
      }

      @Override
      public Builder generateUUID() {
         return uuid(java.util.UUID.randomUUID());
      }

      @Override
      @Deprecated
      public Builder uuid() {
         return generateUUID();
      }

      @Override
      public Builder uuid(UUID uuid) {
         b_.putUUID(PropertyKey.UUID.key(), uuid);
         return this;
      }

      @Override
      public Builder bitDepth(Integer bitDepth) {
         b_.putInteger(BIT_DEPTH.key(), bitDepth);
         return this;
      }

      @Override
      public Builder exposureMs(Double exposureMs) {
         b_.putDouble(EXPOSURE_MS.key(), exposureMs);
         return this;
      }

      @Override
      public Builder elapsedTimeMs(Double elapsedTimeMs) {
         b_.putDouble(ELAPSED_TIME_MS.key(), elapsedTimeMs);
         return this;
      }

      @Override
      public Builder binning(Integer binning) {
         b_.putInteger(BINNING.key(), binning);
         return this;
      }

      @Override
      public Builder imageNumber(Long imageNumber) {
         b_.putLong(IMAGE_NUMBER.key(), imageNumber);
         return this;
      }

      @Override
      public Builder positionName(String positionName) {
         b_.putString(POSITION_NAME.key(), positionName);
         return this;
      }

      @Override
      public Builder xPositionUm(Double xPositionUm) {
         b_.putDouble(X_POSITION_UM.key(), xPositionUm);
         return this;
      }

      @Override
      public Builder yPositionUm(Double yPositionUm) {
         b_.putDouble(Y_POSITION_UM.key(), yPositionUm);
         return this;
      }

      @Override
      public Builder zPositionUm(Double zPositionUm) {
         b_.putDouble(Z_POSITION_UM.key(), zPositionUm);
         return this;
      }

      @Override
      public Builder pixelSizeUm(Double pixelSizeUm) {
         b_.putDouble(PIXEL_SIZE_UM.key(), pixelSizeUm);
         return this;
      }
      
      @Override 
      public Builder pixelSizeAffine(AffineTransform aff) {
         b_.putAffineTransform(PIXEL_SIZE_AFFINE.key(), aff);
         return this;
      }

      @Override
      public Builder camera(String camera) {
         b_.putString(CAMERA.key(), camera);
         return this;
      }

      @Override
      public Builder receivedTime(String receivedTime) {
         b_.putString(RECEIVED_TIME.key(), receivedTime);
         return this;
      }

      @Override
      public Builder roi(Rectangle roi) {
         b_.putRectangle(ROI.key(), roi);
         return this;
      }

      @Override
      public Builder ROI(Rectangle roi) {
         return roi(roi);
      }

      @Override
      public Builder pixelAspect(Double pixelAspect) {
         b_.putDouble(PIXEL_ASPECT.key(), pixelAspect);
         return this;
      }

      @Override
      public Builder scopeData(PropertyMap scopeData) {
         for (String key : scopeData.keySet()) {
            if (scopeData.getValueTypeForKey(key) != String.class) {
               throw new ClassCastException("ScopeData property map values must be Strings");
            }
         }
         b_.putPropertyMap(SCOPE_DATA.key(), scopeData);
         return this;
      }

      @Override
      public Builder userData(PropertyMap userData) {
         // This is not read by MM1 so an arbitrary property map is allowed.
         b_.putPropertyMap(USER_DATA.key(), userData);
         return this;
      }

      @Override
      public Builder fileName(String filename) {
         b_.putString(FILE_NAME.key(), filename);
         return this;
      }
   }


   private final PropertyMap pmap_;

   public DefaultMetadata(PropertyMap pmap) {
      pmap_ = pmap;

      // Check map format
      getUUID();
      getCamera();
      getBinning();
      getROI();
      getBitDepth();
      getExposureMs();
      getElapsedTimeMs(0.0);
      getImageNumber();
      getReceivedTime();
      getPixelSizeUm();
      getPixelAspect();
      getPositionName("");
      getXPositionUm();
      getYPositionUm();
      getZPositionUm();
      PropertyMap scopeData = getScopeData();
      for (String key : scopeData.keySet()) {
         if (scopeData.getValueTypeForKey(key) != String.class) {
            throw new ClassCastException("ScopeData property map values must be Strings");
         }
      }
      getUserData();
      getFileName();
   }

   @Override
   public Builder copyBuilderPreservingUUID() {
      return new Builder(pmap_);
   }

   @Override
   public Builder copyBuilderWithNewUUID() {
      return new Builder(pmap_).generateUUID();
   }

   @Override
   public Builder copyBuilderRemovingUUID() {
      return new Builder(pmap_).uuid(null);
   }

   @Override
   @Deprecated
   public Builder copy() {
      return copyBuilderPreservingUUID();
   }

   // Could throw if map has wrong value types
   public static Metadata fromPropertyMap(PropertyMap map) {
      return new DefaultMetadata(map);
   }

   public PropertyMap toPropertyMap() {
      return pmap_;
   }

   @Override
   public UUID getUUID() {
      return pmap_.getUUID(PropertyKey.UUID.key(), null);
   }

   @Override
   public Integer getBitDepth() {
      return pmap_.containsKey(BIT_DEPTH.key()) ?
            pmap_.getInteger(BIT_DEPTH.key(), 0) : null;
   }

   @Override
   public Double getExposureMs() {
      return pmap_.containsKey(EXPOSURE_MS.key()) ?
            pmap_.getDouble(EXPOSURE_MS.key(), Double.NaN) : null;
   }
      
   @Override
   @Deprecated
   public Double getElapsedTimeMs() {
      return pmap_.containsKey(ELAPSED_TIME_MS.key()) ?
            pmap_.getDouble(ELAPSED_TIME_MS.key(), Double.NaN) : null;
   }
   
   @Override
   public boolean hasElapsedTimeMs() {
      return pmap_.containsKey(ELAPSED_TIME_MS.key());
   }
   
   @Override
   public double getElapsedTimeMs(double exposureMs) {
      return pmap_.getDouble(ELAPSED_TIME_MS.key(), exposureMs);
   }


   @Override
   public Integer getBinning() {
      return pmap_.containsKey(BINNING.key()) ?
            pmap_.getInteger(BINNING.key(), 0) : null;
   }

   @Override
   public Long getImageNumber() {
      return pmap_.containsKey(IMAGE_NUMBER.key()) ?
            pmap_.getLong(IMAGE_NUMBER.key(), 0L) : null;
   }

   /**
    * @return
    * @deprecated use {@link #getPositionName(java.lang.String) } instead
    */
   @Override
   @Deprecated
   public String getPositionName() {
      return pmap_.getString(POSITION_NAME.key(), null);
   }
   
   @Override
   public boolean hasPositionName() {
      return pmap_.containsKey(POSITION_NAME.key());
   }
   
   @Override
   public String getPositionName(String defaultPosName) {
      return pmap_.getString(POSITION_NAME.key(), defaultPosName);
   }
   

   @Override
   public Double getXPositionUm() {
      return pmap_.containsKey(X_POSITION_UM.key()) ?
            pmap_.getDouble(X_POSITION_UM.key(), Double.NaN) : null;
   }

   @Override
   public Double getYPositionUm() {
      return pmap_.containsKey(Y_POSITION_UM.key()) ?
            pmap_.getDouble(Y_POSITION_UM.key(), Double.NaN) : null;
   }

   @Override
   public Double getZPositionUm() {
      return pmap_.containsKey(Z_POSITION_UM.key()) ?
            pmap_.getDouble(Z_POSITION_UM.key(), Double.NaN) : null;
   }

   @Override
   public Double getPixelSizeUm() {
      return pmap_.containsKey(PIXEL_SIZE_UM.key()) ?
            pmap_.getDouble(PIXEL_SIZE_UM.key(), Double.NaN) : null;
   }
   
   @Override
   public AffineTransform getPixelSizeAffine() {
      return pmap_.getAffineTransform(PIXEL_SIZE_AFFINE.key(), null);
   }

   @Override
   public String getCamera() {
      return pmap_.getString(CAMERA.key(), null);
   }

   @Override
   public String getReceivedTime() {
      return pmap_.getString(RECEIVED_TIME.key(), null);
   }

   @Override
   public Rectangle getROI() {
      return pmap_.getRectangle(ROI.key(), null);
   }

   @Override
   public Double getPixelAspect() {
      return pmap_.containsKey(PIXEL_ASPECT.key()) ?
            pmap_.getDouble(PIXEL_ASPECT.key(), Double.NaN) : null;
   }

   @Override
   public PropertyMap getScopeData() {
      return pmap_.getPropertyMap(SCOPE_DATA.key(), PropertyMaps.emptyPropertyMap());
   }

   @Override
   public PropertyMap getUserData() {
      return pmap_.getPropertyMap(USER_DATA.key(), PropertyMaps.emptyPropertyMap());
   }

   @Override
   public String getFileName() {
      return pmap_.getString(FILE_NAME.key(), null);
   }

   @Override
   public String toString() {
      return toPropertyMap().toString();
   }
}