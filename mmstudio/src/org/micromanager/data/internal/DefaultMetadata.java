package org.micromanager.data.internal;

import java.awt.Color;
import java.awt.Rectangle;
import java.util.UUID;

import org.json.JSONException;
import org.json.JSONObject;

import org.micromanager.data.Metadata;
import org.micromanager.data.SummaryMetadata;
import org.micromanager.MultiStagePosition;

import org.micromanager.internal.utils.MDUtils;
import org.micromanager.internal.utils.ReportingUtils;

/**
 * This class holds the metadata for ImagePlanes. It is intended to be 
 * immutable; construct new Metadatas using a MetadataBuilder, or by using
 * the copy() method (which provides a MetadataBuilder). Any fields that are
 * not explicitly set will default to null.
 */
public class DefaultMetadata implements Metadata {

   /**
    * This class constructs Metadata objects. Use the build() method to 
    * generate a Metadata.
    */
   public static class Builder implements Metadata.MetadataBuilder {
      private UUID uuid_ = null;
      private String source_ = null;
      
      private MultiStagePosition initialPositionList_ = null;

      private Boolean keepShutterOpenSlices_ = null;
      private Boolean keepShutterOpenChannels_ = null;

      private String pixelType_ = null;
      private Integer bitDepth_ = null;
      private Integer numComponents_ = null;
      private Integer ijType_ = null;
      private String channelName_ = null;
      private Double exposureMs_ = null;
      private Double elapsedTimeMs_ = null;
      private Double startTimeMs_ = null;
      private Integer binning_ = null;
      
      private Integer imageNumber_ = null;
      private Integer gridRow_ = null;
      private Integer gridColumn_ = null;
      private String positionName_ = null;
      private Double xPositionUm_ = null;
      private Double yPositionUm_ = null;
      private Double zPositionUm_ = null;

      private Double pixelSizeUm_ = null;
      private String camera_ = null;
      private String receivedTime_ = null;
      private String excitationLabel_ = null;
      private String emissionLabel_ = null;
      private Rectangle ROI_ = null;
      private String comments_ = null;

      private Integer color_ = null;
      private Double pixelAspect_ = null;

      private JSONObject userMetadata_ = null;
      private SummaryMetadata summaryMetadata_ = null;

      @Override
      public DefaultMetadata build() {
         return new DefaultMetadata(this);
      }

      @Override
      public MetadataBuilder uuid(UUID uuid) {
         uuid_ = uuid;
         return this;
      }

      @Override
      public MetadataBuilder source(String source) {
         source_ = source;
         return this;
      }

      @Override
      public MetadataBuilder initialPositionList(MultiStagePosition initialPositionList) {
         initialPositionList_ = initialPositionList;
         return this;
      }

      @Override
      public MetadataBuilder keepShutterOpenSlices(Boolean keepShutterOpenSlices) {
         keepShutterOpenSlices_ = keepShutterOpenSlices;
         return this;
      }

      @Override
      public MetadataBuilder keepShutterOpenChannels(Boolean keepShutterOpenChannels) {
         keepShutterOpenChannels_ = keepShutterOpenChannels;
         return this;
      }

      @Override
      public MetadataBuilder pixelType(String pixelType) {
         pixelType_ = pixelType;
         return this;
      }

      @Override
      public MetadataBuilder bitDepth(Integer bitDepth) {
         bitDepth_ = bitDepth;
         return this;
      }

      @Override
      public MetadataBuilder numComponents(Integer numComponents) {
         numComponents_ = numComponents;
         return this;
      }

      @Override
      public MetadataBuilder ijType(Integer ijType) {
         ijType_ = ijType;
         return this;
      }

      @Override
      public MetadataBuilder channelName(String channelName) {
         channelName_ = channelName;
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
      public MetadataBuilder startTimeMs(Double startTimeMs) {
         startTimeMs_ = startTimeMs;
         return this;
      }

      @Override
      public MetadataBuilder binning(Integer binning) {
         binning_ = binning;
         return this;
      }

      @Override
      public MetadataBuilder imageNumber(Integer imageNumber) {
         imageNumber_ = imageNumber;
         return this;
      }

      @Override
      public MetadataBuilder gridRow(Integer gridRow) {
         gridRow_ = gridRow;
         return this;
      }

      @Override
      public MetadataBuilder gridColumn(Integer gridColumn) {
         gridColumn_ = gridColumn;
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
      public MetadataBuilder excitationLabel(String excitationLabel) {
         excitationLabel_ = excitationLabel;
         return this;
      }

      @Override
      public MetadataBuilder emissionLabel(String emissionLabel) {
         emissionLabel_ = emissionLabel;
         return this;
      }

      @Override
      public MetadataBuilder ROI(Rectangle ROI) {
         ROI_ = ROI;
         return this;
      }

      @Override
      public MetadataBuilder comments(String comments) {
         comments_ = comments;
         return this;
      }

      @Override
      public MetadataBuilder color(Integer color) {
         color_ = color;
         return this;
      }

      @Override
      public MetadataBuilder pixelAspect(Double pixelAspect) {
         pixelAspect_ = pixelAspect;
         return this;
      }

      @Override
      public MetadataBuilder userMetadata(JSONObject userMetadata) {
         userMetadata_ = userMetadata;
         return this;
      }

      @Override
      public MetadataBuilder summaryMetadata(SummaryMetadata summaryMetadata) {
         summaryMetadata_ = summaryMetadata;
         return this;
      }
   }

   private UUID uuid_ = null;
   private String source_ = null;
   
   private MultiStagePosition initialPositionList_ = null;

   private Boolean keepShutterOpenSlices_ = null;
   private Boolean keepShutterOpenChannels_ = null;

   private String pixelType_ = null;
   private Integer bitDepth_ = null;
   private Integer numComponents_ = null;
   private Integer ijType_ = null;
   private String channelName_ = null;
   private Double exposureMs_ = null;
   private Double elapsedTimeMs_ = null;
   private Double startTimeMs_ = null;
   private Integer binning_ = null;
   
   private Integer imageNumber_ = null;
   private Integer gridRow_ = null;
   private Integer gridColumn_ = null;
   private String positionName_ = null;
   private Double xPositionUm_ = null;
   private Double yPositionUm_ = null;
   private Double zPositionUm_ = null;

   private Double pixelSizeUm_ = null;
   private String camera_ = null;
   private String receivedTime_ = null;
   private String excitationLabel_ = null;
   private String emissionLabel_ = null;
   private Rectangle ROI_ = null;
   private String comments_ = null;

   private Integer color_ = null;
   private Double pixelAspect_ = null;

   private JSONObject userMetadata_ = null;
   private SummaryMetadata summaryMetadata_ = null;

   public DefaultMetadata(Builder builder) {
      uuid_ = builder.uuid_;
      source_ = builder.source_;
      
      initialPositionList_ = builder.initialPositionList_;

      keepShutterOpenSlices_ = builder.keepShutterOpenSlices_;
      keepShutterOpenChannels_ = builder.keepShutterOpenChannels_;

      pixelType_ = builder.pixelType_;
      bitDepth_ = builder.bitDepth_;
      numComponents_ = builder.numComponents_;
      ijType_ = builder.ijType_;
      channelName_ = builder.channelName_;
      exposureMs_ = builder.exposureMs_;
      elapsedTimeMs_ = builder.elapsedTimeMs_;
      startTimeMs_ = builder.startTimeMs_;
      binning_ = builder.binning_;
      
      imageNumber_ = builder.imageNumber_;
      gridRow_ = builder.gridRow_;
      gridColumn_ = builder.gridColumn_;
      positionName_ = builder.positionName_;
      xPositionUm_ = builder.xPositionUm_;
      yPositionUm_ = builder.yPositionUm_;
      zPositionUm_ = builder.zPositionUm_;

      pixelSizeUm_ = builder.pixelSizeUm_;
      camera_ = builder.camera_;
      receivedTime_ = builder.receivedTime_;
      excitationLabel_ = builder.excitationLabel_;
      emissionLabel_ = builder.emissionLabel_;
      ROI_ = builder.ROI_;
      comments_ = builder.comments_;

      color_ = builder.color_;
      pixelAspect_ = builder.pixelAspect_;

      userMetadata_ = builder.userMetadata_;
      summaryMetadata_ = builder.summaryMetadata_;
   }
   
   @Override
   public MetadataBuilder copy() {
      return new Builder()
            .uuid(uuid_)
            .source(source_)
            .initialPositionList(initialPositionList_)
            .keepShutterOpenSlices(keepShutterOpenSlices_)
            .keepShutterOpenChannels(keepShutterOpenChannels_)
            .pixelType(pixelType_)
            .bitDepth(bitDepth_)
            .numComponents(numComponents_)
            .ijType(ijType_)
            .channelName(channelName_)
            .exposureMs(exposureMs_)
            .elapsedTimeMs(elapsedTimeMs_)
            .startTimeMs(startTimeMs_)
            .binning(binning_)
            .imageNumber(imageNumber_)
            .gridRow(gridRow_)
            .gridColumn(gridColumn_)
            .positionName(positionName_)
            .xPositionUm(xPositionUm_)
            .yPositionUm(yPositionUm_)
            .zPositionUm(zPositionUm_)
            .pixelSizeUm(pixelSizeUm_)
            .camera(camera_)
            .receivedTime(receivedTime_)
            .excitationLabel(excitationLabel_)
            .emissionLabel(emissionLabel_)
            .ROI(ROI_)
            .comments(comments_)
            .color(color_)
            .pixelAspect(pixelAspect_);
   }

   @Override
   public UUID getUUID() {
      return uuid_;
   }

   @Override
   public String getSource() {
      return source_;
   }

   @Override
   public MultiStagePosition getInitialPositionList() {
      return MultiStagePosition.newInstance(initialPositionList_);
   }

   @Override
   public Boolean getKeepShutterOpenSlices() {
      return keepShutterOpenSlices_;
   }

   @Override
   public Boolean getKeepShutterOpenChannels() {
      return keepShutterOpenChannels_;
   }

   @Override
   public String getPixelType() {
      return pixelType_;
   }

   @Override
   public Integer getBitDepth() {
      return bitDepth_;
   }

   @Override
   public Integer getNumComponents() {
      return numComponents_;
   }

   @Override
   public Integer getIjType() {
      return ijType_;
   }

   @Override
   public String getChannelName() {
      return channelName_;
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
   public Double getStartTimeMs() {
      return startTimeMs_;
   }

   @Override
   public Integer getBinning() {
      return binning_;
   }

   @Override
   public Integer getImageNumber() {
      return imageNumber_;
   }

   @Override
   public Integer getGridRow() {
      return gridRow_;
   }

   @Override
   public Integer getGridColumn() {
      return gridColumn_;
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
   public String getExcitationLabel() {
      return excitationLabel_;
   }

   @Override
   public String getEmissionLabel() {
      return emissionLabel_;
   }

   @Override
   public Rectangle getROI() {
      return new Rectangle(ROI_);
   }

   @Override
   public String getComments() {
      return comments_;
   }

   @Override
   public Integer getColor() {
      return color_;
   }

   @Override
   public Double getPixelAspect() {
      return pixelAspect_;
   }

   @Override
   public JSONObject getUserMetadata() {
      return userMetadata_;
   }

   @Override
   public SummaryMetadata getSummaryMetadata() {
      return summaryMetadata_;
   }

   /**
    * For backwards compatibility, convert our data into a JSONObject.
    */
   @Override
   public JSONObject legacyToJSON() {
      try {
         JSONObject result = new JSONObject();
         MDUtils.setChannelName(result, getCamera());
         MDUtils.setROI(result, getROI());
         MDUtils.setBinning(result, getBinning());
         MDUtils.setBitDepth(result, getBitDepth());
         MDUtils.setPixelSizeUm(result, getPixelSizeUm());
         MDUtils.setUUID(result, getUUID());
         // If we don't do these manual conversions, we get null pointer
         // exceptions because the argument type for MDUtils here is a
         // lowercase-d double.
         MDUtils.setElapsedTimeMs(result, 
               (getElapsedTimeMs() == null) ? 0 : getElapsedTimeMs());
         MDUtils.setComments(result, getComments());
         return result;
      }
      catch (JSONException e) {
         ReportingUtils.logError(e, "Couldn't convert DefaultMetadata to JSON.");
         return null;
      }
   }
}
