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
import java.util.UUID;
import org.micromanager.PropertyMap;
import org.micromanager.PropertyMaps;
import org.micromanager.data.Metadata;

/**
 * This class holds the metadata for ImagePlanes. It is intended to be 
 * immutable; construct new Metadatas using a MetadataBuilder, or by using
 * the copy() method (which provides a MetadataBuilder). Any fields that are
 * not explicitly set will default to null.
 */
public final class DefaultMetadata implements Metadata {
   public static class Builder implements Metadata.MetadataBuilder {
      private UUID uuid_;
      private String camera_;
      private Integer binning_;
      private Rectangle ROI_;
      private Integer bitDepth_;
      private Double exposureMs_;
      private Double elapsedTimeMs_;
      private Long imageNumber_;
      private String receivedTime_;
      private Double pixelSizeUm_;
      private Double pixelAspect_;
      private String positionName_;
      private Double xPositionUm_;
      private Double yPositionUm_;
      private Double zPositionUm_;
      private PropertyMap scopeData_;
      private PropertyMap userData_;

      @Override
      public DefaultMetadata build() {
         return new DefaultMetadata(this);
      }

      @Override
      public MetadataBuilder uuid() {
         uuid_ = UUID.randomUUID();
         return this;
      }

      @Override
      public MetadataBuilder uuid(UUID uuid) {
         uuid_ = uuid;
         return this;
      }

      @Override
      public MetadataBuilder bitDepth(Integer bitDepth) {
         bitDepth_ = bitDepth;
         return this;
      }

      @Override
      public MetadataBuilder exposureMs(Double exposureMs) {
         exposureMs_ = exposureMs;
         return this;
      }

      @Override
      public MetadataBuilder elapsedTimeMs(Double elapsedTimeMs) {
         elapsedTimeMs_ = elapsedTimeMs;
         return this;
      }

      @Override
      public MetadataBuilder binning(Integer binning) {
         binning_ = binning;
         return this;
      }

      @Override
      public MetadataBuilder imageNumber(Long imageNumber) {
         imageNumber_ = imageNumber;
         return this;
      }

      @Override
      public MetadataBuilder positionName(String positionName) {
         positionName_ = positionName;
         return this;
      }

      @Override
      public MetadataBuilder xPositionUm(Double xPositionUm) {
         xPositionUm_ = xPositionUm;
         return this;
      }

      @Override
      public MetadataBuilder yPositionUm(Double yPositionUm) {
         yPositionUm_ = yPositionUm;
         return this;
      }

      @Override
      public MetadataBuilder zPositionUm(Double zPositionUm) {
         zPositionUm_ = zPositionUm;
         return this;
      }

      @Override
      public MetadataBuilder pixelSizeUm(Double pixelSizeUm) {
         pixelSizeUm_ = pixelSizeUm;
         return this;
      }

      @Override
      public MetadataBuilder camera(String camera) {
         camera_ = camera;
         return this;
      }

      @Override
      public MetadataBuilder receivedTime(String receivedTime) {
         receivedTime_ = receivedTime;
         return this;
      }

      @Override
      public MetadataBuilder ROI(Rectangle ROI) {
         ROI_ = ROI;
         return this;
      }

      @Override
      public MetadataBuilder pixelAspect(Double pixelAspect) {
         pixelAspect_ = pixelAspect;
         return this;
      }

      @Override
      public MetadataBuilder scopeData(PropertyMap scopeData) {
         for (String key : scopeData.keySet()) {
            if (scopeData.getValueTypeForKey(key) != String.class) {
               throw new ClassCastException("ScopeData property map values must be Strings");
            }
         }
         scopeData_ = scopeData;
         return this;
      }

      @Override
      public MetadataBuilder userData(PropertyMap userData) {
         // This is not read by MM1 so an arbitrary property map is allowed.
         userData_ = userData;
         return this;
      }
   }

   private final UUID uuid_;
   private final String camera_;
   private final Integer binning_;
   private final Rectangle ROI_;
   private final Integer bitDepth_;
   private final Double exposureMs_;
   private final Double elapsedTimeMs_;
   private final Long imageNumber_;
   private final String receivedTime_;
   private final Double pixelSizeUm_;
   private final Double pixelAspect_;
   private final String positionName_;
   private final Double xPositionUm_;
   private final Double yPositionUm_;
   private final Double zPositionUm_;
   private final PropertyMap scopeData_;
   private final PropertyMap userData_;

   public DefaultMetadata(Builder builder) {
      uuid_ = builder.uuid_;
      camera_ = builder.camera_;
      binning_ = builder.binning_;
      ROI_ = builder.ROI_;
      bitDepth_ = builder.bitDepth_;
      exposureMs_ = builder.exposureMs_;
      elapsedTimeMs_ = builder.elapsedTimeMs_;
      imageNumber_ = builder.imageNumber_;
      receivedTime_ = builder.receivedTime_;
      pixelSizeUm_ = builder.pixelSizeUm_;
      pixelAspect_ = builder.pixelAspect_;
      positionName_ = builder.positionName_;
      xPositionUm_ = builder.xPositionUm_;
      yPositionUm_ = builder.yPositionUm_;
      zPositionUm_ = builder.zPositionUm_;
      scopeData_ = builder.scopeData_;
      userData_ = builder.userData_;
   }

   @Override
   public MetadataBuilder copy() {
      return new Builder()
            .uuid(uuid_)
            .camera(camera_)
            .binning(binning_)
            .ROI(ROI_)
            .bitDepth(bitDepth_)
            .exposureMs(exposureMs_)
            .elapsedTimeMs(elapsedTimeMs_)
            .imageNumber(imageNumber_)
            .receivedTime(receivedTime_)
            .pixelSizeUm(pixelSizeUm_)
            .pixelAspect(pixelAspect_)
            .positionName(positionName_)
            .xPositionUm(xPositionUm_)
            .yPositionUm(yPositionUm_)
            .zPositionUm(zPositionUm_)
            .scopeData(scopeData_)
            .userData(userData_);
   }

   private DefaultMetadata(PropertyMap map) {
      uuid_ = map.containsString(PropertyKey.UUID.key()) ?
            UUID.fromString(map.getString(PropertyKey.UUID.key(), null)) : null;
      camera_ = map.getString(PropertyKey.CAMERA.key(), null);
      binning_ = (Integer) map.getAsNumber(PropertyKey.BINNING.key(), null);
      ROI_ = map.getRectangle(PropertyKey.ROI.key(), null);
      bitDepth_ = (Integer) map.getAsNumber(PropertyKey.BIT_DEPTH.key(), null);
      exposureMs_ = (Double) map.getAsNumber(PropertyKey.EXPOSURE_MS.key(), null);
      elapsedTimeMs_ = (Double) map.getAsNumber(PropertyKey.ELAPSED_TIME_MS.key(), null);
      imageNumber_ = (Long) map.getAsNumber(PropertyKey.IMAGE_NUMBER.key(), null);
      receivedTime_ = map.getString(PropertyKey.RECEIVED_TIME.key(), null);
      pixelSizeUm_ = (Double) map.getAsNumber(PropertyKey.PIXEL_SIZE_UM.key(), null);
      pixelAspect_ = (Double) map.getAsNumber(PropertyKey.PIXEL_ASPECT.key(), null);
      positionName_ = map.getString(PropertyKey.POSITION_NAME.key(), null);
      xPositionUm_ = (Double) map.getAsNumber(PropertyKey.X_POSITION_UM.key(), null);
      yPositionUm_ = (Double) map.getAsNumber(PropertyKey.Y_POSITION_UM.key(), null);
      zPositionUm_ = (Double) map.getAsNumber(PropertyKey.Z_POSITION_UM.key(), null);
      scopeData_ = map.getPropertyMap(PropertyKey.SCOPE_DATA.key(), null);
      userData_ = map.getPropertyMap(PropertyKey.USER_DATA.key(), null);
   }

   // Could throw if map has wrong value types
   public static Metadata fromPropertyMap(PropertyMap map) {
      return new DefaultMetadata(map);
   }

   public PropertyMap toPropertyMap() {
      return PropertyMaps.builder().
            putString(PropertyKey.UUID.key(), getUUID() == null ? null : getUUID().toString()).
            putString(PropertyKey.CAMERA.key(), getCamera()).
            putInteger(PropertyKey.BINNING.key(), getBinning()).
            putRectangle(PropertyKey.ROI.key(), getROI()).
            putInteger(PropertyKey.BIT_DEPTH.key(), getBitDepth()).
            putDouble(PropertyKey.EXPOSURE_MS.key(), getExposureMs()).
            putDouble(PropertyKey.ELAPSED_TIME_MS.key(), getElapsedTimeMs()).
            putLong(PropertyKey.IMAGE_NUMBER.key(), getImageNumber()).
            putString(PropertyKey.RECEIVED_TIME.key(), getReceivedTime()).
            putDouble(PropertyKey.PIXEL_SIZE_UM.key(), getPixelSizeUm()).
            putDouble(PropertyKey.PIXEL_ASPECT.key(), getPixelAspect()).
            putString(PropertyKey.POSITION_NAME.key(), getPositionName()).
            putDouble(PropertyKey.X_POSITION_UM.key(), getXPositionUm()).
            putDouble(PropertyKey.Y_POSITION_UM.key(), getYPositionUm()).
            putDouble(PropertyKey.Z_POSITION_UM.key(), getZPositionUm()).
            putPropertyMap(PropertyKey.SCOPE_DATA.key(), scopeData_).
            putPropertyMap(PropertyKey.USER_DATA.key(), userData_).
            build();
   }

   @Override
   public UUID getUUID() {
      return uuid_;
   }

   @Override
   public Integer getBitDepth() {
      return bitDepth_;
   }

   @Override
   public Double getExposureMs() {
      return exposureMs_;
   }

   @Override
   public Double getElapsedTimeMs() {
      return elapsedTimeMs_;
   }

   @Override
   public Integer getBinning() {
      return binning_;
   }

   @Override
   public Long getImageNumber() {
      return imageNumber_;
   }

   @Override
   public String getPositionName() {
      return positionName_;
   }

   @Override
   public Double getXPositionUm() {
      return xPositionUm_;
   }

   @Override
   public Double getYPositionUm() {
      return yPositionUm_;
   }

   @Override
   public Double getZPositionUm() {
      return zPositionUm_;
   }

   @Override
   public Double getPixelSizeUm() {
      return pixelSizeUm_;
   }

   @Override
   public String getCamera() {
      return camera_;
   }

   @Override
   public String getReceivedTime() {
      return receivedTime_;
   }

   @Override
   public Rectangle getROI() {
      return ROI_ == null ? null : new Rectangle(ROI_);
   }

   @Override
   public Double getPixelAspect() {
      return pixelAspect_;
   }

   @Override
   public PropertyMap getScopeData() {
      return scopeData_ == null ? PropertyMaps.emptyPropertyMap() : scopeData_;
   }

   @Override
   public PropertyMap getUserData() {
      return userData_ == null ? PropertyMaps.emptyPropertyMap() : userData_;
   }

   @Override
   public String toString() {
      return toPropertyMap().toString();
   }
}