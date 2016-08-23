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
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.micromanager.PropertyMap;
import org.micromanager.data.Metadata;
import org.micromanager.internal.utils.MDUtils;
import org.micromanager.internal.utils.ReportingUtils;

/**
 * This class holds the metadata for ImagePlanes. It is intended to be 
 * immutable; construct new Metadatas using a MetadataBuilder, or by using
 * the copy() method (which provides a MetadataBuilder). Any fields that are
 * not explicitly set will default to null.
 */
public final class DefaultMetadata implements Metadata {

   /**
    * This class constructs Metadata objects. Use the build() method to 
    * generate a Metadata.
    */
   public static class Builder implements Metadata.MetadataBuilder {
      private UUID uuid_ = null;
      
      private Integer bitDepth_ = null;
      private Double exposureMs_ = null;
      private Double elapsedTimeMs_ = null;
      private Integer binning_ = null;
      
      private Long imageNumber_ = null;
      private String positionName_ = null;
      private Double xPositionUm_ = null;
      private Double yPositionUm_ = null;
      private Double zPositionUm_ = null;

      private Double pixelSizeUm_ = null;
      private String camera_ = null;
      private String receivedTime_ = null;
      private Rectangle ROI_ = null;

      private Double pixelAspect_ = null;

      private DefaultPropertyMap scopeData_ = null;
      private DefaultPropertyMap userData_ = null;

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
         scopeData_ = (DefaultPropertyMap) scopeData;
         return this;
      }

      @Override
      public MetadataBuilder userData(PropertyMap userData) {
         userData_ = (DefaultPropertyMap) userData;
         return this;
      }
   }

   private UUID uuid_ = null;
   
   private Integer bitDepth_ = null;
   private Double exposureMs_ = null;
   private Double elapsedTimeMs_ = null;
   private Integer binning_ = null;
   
   private Long imageNumber_ = null;
   private String positionName_ = null;
   private Double xPositionUm_ = null;
   private Double yPositionUm_ = null;
   private Double zPositionUm_ = null;

   private Double pixelSizeUm_ = null;
   private String camera_ = null;
   private String receivedTime_ = null;
   private Rectangle ROI_ = null;

   private Double pixelAspect_ = null;

   private DefaultPropertyMap scopeData_ = null;
   private DefaultPropertyMap userData_ = null;

   public DefaultMetadata(Builder builder) {
      uuid_ = builder.uuid_;

      bitDepth_ = builder.bitDepth_;
      exposureMs_ = builder.exposureMs_;
      elapsedTimeMs_ = builder.elapsedTimeMs_;
      binning_ = builder.binning_;
      
      imageNumber_ = builder.imageNumber_;
      positionName_ = builder.positionName_;
      xPositionUm_ = builder.xPositionUm_;
      yPositionUm_ = builder.yPositionUm_;
      zPositionUm_ = builder.zPositionUm_;

      pixelSizeUm_ = builder.pixelSizeUm_;
      camera_ = builder.camera_;
      receivedTime_ = builder.receivedTime_;
      ROI_ = builder.ROI_;

      pixelAspect_ = builder.pixelAspect_;

      scopeData_ = builder.scopeData_;
      userData_ = builder.userData_;
   }
   
   @Override
   public MetadataBuilder copy() {
      return new Builder()
            .uuid(uuid_)
            .bitDepth(bitDepth_)
            .exposureMs(exposureMs_)
            .elapsedTimeMs(elapsedTimeMs_)
            .binning(binning_)
            .imageNumber(imageNumber_)
            .positionName(positionName_)
            .xPositionUm(xPositionUm_)
            .yPositionUm(yPositionUm_)
            .zPositionUm(zPositionUm_)
            .pixelSizeUm(pixelSizeUm_)
            .camera(camera_)
            .receivedTime(receivedTime_)
            .ROI(ROI_)
            .pixelAspect(pixelAspect_)
            .scopeData(scopeData_)
            .userData(userData_);
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
      if (ROI_ != null) {
         return new Rectangle(ROI_);
      }
      return null;
   }

   @Override
   public Double getPixelAspect() {
      return pixelAspect_;
   }

   @Override
   public PropertyMap getScopeData() {
      return scopeData_;
   }

   @Override
   public PropertyMap getUserData() {
      return userData_;
   }

   /**
    * For backwards compatibility, convert our data into a JSONObject.
    */
   public JSONObject toJSON() {
      try {
         // If we don't do these manual conversions for various MDUtils
         // methods, we get null pointer exceptions because the argument type
         // for MDUtils here is a double or int, not Double or Integer.
         JSONObject result = new JSONObject();
         if (getBinning() != null) {
            MDUtils.setBinning(result, getBinning());
         }
         if (getBitDepth() != null) {
            MDUtils.setBitDepth(result, getBitDepth());
         }
         if (getCamera() != null) {
            result.put("Camera", getCamera());
         }
         if (getElapsedTimeMs() != null) {
            MDUtils.setElapsedTimeMs(result, getElapsedTimeMs());
         }
         if (getExposureMs() != null) {
            MDUtils.setExposureMs(result, getExposureMs());
         }
         if (getImageNumber() != null) {
            MDUtils.setSequenceNumber(result, getImageNumber());
         }
         if (getPixelAspect() != null) {
            result.put("pixelAspect", getPixelAspect());
         }
         if (getPixelSizeUm() != null) {
            MDUtils.setPixelSizeUm(result, getPixelSizeUm());
         }
         if (getPositionName() != null) {
            MDUtils.setPositionName(result, getPositionName());
         }
         if (getReceivedTime() != null) {
            result.put("receivedTime", getReceivedTime());
         }
         if (ROI_ != null) {
            MDUtils.setROI(result, getROI());
         }
         if (getUUID() != null) {
            MDUtils.setUUID(result, getUUID());
         }
         if (getXPositionUm() != null) {
            MDUtils.setXPositionUm(result, getXPositionUm());
         }
         if (getYPositionUm() != null) {
            MDUtils.setYPositionUm(result, getYPositionUm());
         }
         if (getZPositionUm() != null) {
            MDUtils.setZPositionUm(result, getZPositionUm());
         }

         if (scopeData_ != null) {
            JSONArray keys = new JSONArray();
            for (String key : scopeData_.getKeys()) {
               keys.put(key);
            }
            // Scope data properties are stored "flat" in the result JSON,
            // to maintain backwards compatibility with MM1.4.
            result.put("scopeDataKeys", keys);
            JSONObject scopeJSON = scopeData_.toJSON();
            for (String key : MDUtils.getKeys(scopeJSON)) {
               result.put(key, scopeJSON.get(key));
            }
         }
         if (userData_ != null) {
            result.put("userData", userData_.toJSON());
         }
         return result;
      }
      catch (JSONException e) {
         ReportingUtils.logError(e, "Couldn't convert DefaultMetadata to JSON.");
         return null;
      }
   }

   /**
    * For backwards compatibility and save/loading, generate a new Metadata
    * from JSON.
    * Note that we silently ignore all JSONExceptions caused by failing to
    * find a given metadata tag, as those tags may not be available.
    */
   public static Metadata legacyFromJSON(JSONObject tags) {
      Builder builder = new Builder();

      try {
         builder.binning(MDUtils.getBinning(tags));
      }
      catch (JSONException e) {}

      try {
         builder.bitDepth(MDUtils.getBitDepth(tags));
      }
      catch (JSONException e) {}

      // There's three possible sources for this value:
      // - When loading a previously-serialized DefaultMetadata, it stores any
      //   camera property under "Camera"
      // - The Acquisition Engine in multicamera setups stores the camera name
      //   under "Camera"
      // - The Acquisition Engine in single-camera setups stores "" under
      //   "Camera", so we have to resort to the Core-Camera property instead.
      // The Acquisition Engine function that does this is
      // get-camera-channel-names.
      // Note that our logic here makes it impossible to deserialize a
      // DefaultMetadata which has a non-null Camera with value ""; it will
      // end up being null.
      try {
         String camera;
         if (tags.has("Camera") &&
               !tags.getString("Camera").contentEquals("")) {
            camera = tags.getString("Camera");
         }
         else {
            camera = tags.getString("Core-Camera");
         }
         builder.camera(camera);
      }
      catch (JSONException e) {}

      try {
         builder.elapsedTimeMs(MDUtils.getElapsedTimeMs(tags));
      }
      catch (JSONException e) {}

      try {
         builder.exposureMs(MDUtils.getExposureMs(tags));
      }
      catch (JSONException e) {}

      try {
         builder.imageNumber(MDUtils.getSequenceNumber(tags));
      }
      catch (JSONException e) {}

      try {
         builder.pixelAspect(tags.getDouble("pixelAspect"));
      }
      catch (JSONException e) {}

      try {
         builder.pixelSizeUm(MDUtils.getPixelSizeUm(tags));
      }
      catch (JSONException e) {}

      try {
         builder.positionName(MDUtils.getPositionName(tags));
      }
      catch (JSONException e) {}

      // 1.4 puts this value under "Time".
      try {
         builder.receivedTime(tags.getString("receivedTime"));
      }
      catch (JSONException e) {
         try {
            builder.receivedTime(tags.getString("Time"));
         }
         catch (JSONException e2) {}
      }

      try {
         builder.ROI(MDUtils.getROI(tags));
      }
      catch (Exception e) {} // JSONException or MMScriptException

      try {
         builder.uuid(MDUtils.getUUID(tags));
      }
      catch (JSONException e) {}

      try {
         builder.xPositionUm(MDUtils.getXPositionUm(tags));
      }
      catch (JSONException e) {}
      try {
         builder.yPositionUm(MDUtils.getYPositionUm(tags));
      }
      catch (JSONException e) {}
      try {
         builder.zPositionUm(MDUtils.getZPositionUm(tags));
      }
      catch (JSONException e) {}

      if (tags.has("scopeDataKeys")) {
         try {
            // Generate a separated JSONObject out of the "flat" scope data
            // fields
            JSONArray scopeKeys = tags.getJSONArray("scopeDataKeys");
            JSONObject tmp = new JSONObject();
            for (int i = 0; i < scopeKeys.length(); ++i) {
               String key = scopeKeys.getString(i);
               tmp.put(key, tags.getJSONObject(key));
            }
            builder.scopeData(DefaultPropertyMap.fromJSON(tmp));
         }
         catch (JSONException e) {
            ReportingUtils.logError("Metadata failed to reconstruct field scopeData that we expected to be able to recover");
         }
      }

      try {
         builder.userData(DefaultPropertyMap.fromJSON(
                  tags.getJSONObject("userData")));
      }
      catch (JSONException e) {}
      return builder.build();
   }

   @Override
   public String toString() {
      try {
         return toJSON().toString(2);
      }
      catch (JSONException e) {
         ReportingUtils.logError("Failed to convert JSONized metadata to string: " + e);
         return "<Unknown metadata>";
      }
   }
}
