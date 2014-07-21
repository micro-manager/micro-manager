package org.micromanager.data;

import java.awt.Color;
import java.awt.Rectangle;
import java.util.UUID;

import org.json.JSONObject;

import org.micromanager.api.data.Metadata;
import org.micromanager.api.MultiStagePosition;


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
   public static class DefaultMetadataBuilder implements Metadata.MetadataBuilder {
      private UUID uuid_ = null;
      private String source_ = null;
      private String microManagerVersion_ = null;
      private String metadataVersion_ = null;
      private String acquisitionName_ = null;
      private String fileName_ = null;
      private String userName_ = null;
      private String computerName_ = null;
      private String prefix_ = null;
      private String directory_ = null;
      private String startTime_ = null;
      
      private String[] channelNames_ = null;
      private Color[] channelColors_ = null;
      private Integer[] channelContrastMins_ = null;
      private Integer[] channelContrastMaxes_ = null;
      private MultiStagePosition initialPositionList_ = null;

      private Double zStepUm_ = null;
      private Double waitIntervalMs_ = null;
      private Double[] customIntervalsMs_ = null;
      private Boolean timeFirst_ = null;
      private Boolean slicesFirst_ = null;
      private Boolean keepShutterOpenSlices_ = null;
      private Boolean keepShutterOpenChannels_ = null;

      private String pixelType_ = null;
      private Integer numComponents_ = null;
      private Integer ijType_ = null;
      private Integer frame_ = null;
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

      @Override
      public Metadata build() {
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
      public MetadataBuilder microManagerVersion(String microManagerVersion) {
         microManagerVersion_ = microManagerVersion;
         return this;
      }

      @Override
      public MetadataBuilder metadataVersion(String metadataVersion) {
         metadataVersion_ = metadataVersion;
         return this;
      }

      @Override
      public MetadataBuilder acquisitionName(String acquisitionName) {
         acquisitionName_ = acquisitionName;
         return this;
      }

      @Override
      public MetadataBuilder fileName(String fileName) {
         fileName_ = fileName;
         return this;
      }

      @Override
      public MetadataBuilder userName(String userName) {
         userName_ = userName;
         return this;
      }

      @Override
      public MetadataBuilder computerName(String computerName) {
         computerName_ = computerName;
         return this;
      }

      @Override
      public MetadataBuilder prefix(String prefix) {
         prefix_ = prefix;
         return this;
      }

      @Override
      public MetadataBuilder directory(String directory) {
         directory_ = directory;
         return this;
      }

      @Override
      public MetadataBuilder startTime(String startTime) {
         startTime_ = startTime;
         return this;
      }

      @Override
      public MetadataBuilder channelNames(String[] channelNames) {
         channelNames_ = channelNames;
         return this;
      }

      @Override
      public MetadataBuilder channelColors(Color[] channelColors) {
         channelColors_ = channelColors;
         return this;
      }

      @Override
      public MetadataBuilder channelContrastMins(Integer[] channelContrastMins) {
         channelContrastMins_ = channelContrastMins;
         return this;
      }

      @Override
      public MetadataBuilder channelContrastMaxes(Integer[] channelContrastMaxes) {
         channelContrastMaxes_ = channelContrastMaxes;
         return this;
      }

      @Override
      public MetadataBuilder initialPositionList(MultiStagePosition initialPositionList) {
         initialPositionList_ = initialPositionList;
         return this;
      }

      @Override
      public MetadataBuilder zStepUm(Double zStepUm) {
         zStepUm_ = zStepUm;
         return this;
      }

      @Override
      public MetadataBuilder waitIntervalMs(Double waitIntervalMs) {
         waitIntervalMs_ = waitIntervalMs;
         return this;
      }

      @Override
      public MetadataBuilder customIntervalsMs(Double[] customIntervalsMs) {
         customIntervalsMs_ = customIntervalsMs;
         return this;
      }

      @Override
      public MetadataBuilder timeFirst(Boolean timeFirst) {
         timeFirst_ = timeFirst;
         return this;
      }

      @Override
      public MetadataBuilder slicesFirst(Boolean slicesFirst) {
         slicesFirst_ = slicesFirst;
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
      public MetadataBuilder frame(Integer frame) {
         frame_ = frame;
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
   }

   private UUID uuid_ = null;
   private String source_ = null;
   private String microManagerVersion_ = null;
   private String metadataVersion_ = null;
   private String acquisitionName_ = null;
   private String fileName_ = null;
   private String userName_ = null;
   private String computerName_ = null;
   private String prefix_ = null;
   private String directory_ = null;
   private String startTime_ = null;
   
   private String[] channelNames_ = null;
   private Color[] channelColors_ = null;
   private Integer[] channelContrastMins_ = null;
   private Integer[] channelContrastMaxes_ = null;
   private MultiStagePosition initialPositionList_ = null;

   private Double zStepUm_ = null;
   private Double waitIntervalMs_ = null;
   private Double[] customIntervalsMs_ = null;
   private Boolean timeFirst_ = null;
   private Boolean slicesFirst_ = null;
   private Boolean keepShutterOpenSlices_ = null;
   private Boolean keepShutterOpenChannels_ = null;

   private String pixelType_ = null;
   private Integer numComponents_ = null;
   private Integer ijType_ = null;
   private Integer frame_ = null;
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

   public DefaultMetadata(DefaultMetadataBuilder builder) {
      uuid_ = builder.uuid_;
      source_ = builder.source_;
      microManagerVersion_ = builder.microManagerVersion_;
      metadataVersion_ = builder.metadataVersion_;
      acquisitionName_ = builder.acquisitionName_;
      fileName_ = builder.fileName_;
      userName_ = builder.userName_;
      computerName_ = builder.computerName_;
      prefix_ = builder.prefix_;
      directory_ = builder.directory_;
      startTime_ = builder.startTime_;
      
      channelNames_ = builder.channelNames_;
      channelColors_ = builder.channelColors_;
      channelContrastMins_ = builder.channelContrastMins_;
      channelContrastMaxes_ = builder.channelContrastMaxes_;
      initialPositionList_ = builder.initialPositionList_;

      zStepUm_ = builder.zStepUm_;
      waitIntervalMs_ = builder.waitIntervalMs_;
      customIntervalsMs_ = builder.customIntervalsMs_;
      timeFirst_ = builder.timeFirst_;
      slicesFirst_ = builder.slicesFirst_;
      keepShutterOpenSlices_ = builder.keepShutterOpenSlices_;
      keepShutterOpenChannels_ = builder.keepShutterOpenChannels_;

      pixelType_ = builder.pixelType_;
      numComponents_ = builder.numComponents_;
      ijType_ = builder.ijType_;
      frame_ = builder.frame_;
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
   }
   
   @Override
   public MetadataBuilder copy() {
      return new DefaultMetadataBuilder()
            .uuid(uuid_)
            .source(source_)
            .microManagerVersion(microManagerVersion_)
            .metadataVersion(metadataVersion_)
            .acquisitionName(acquisitionName_)
            .fileName(fileName_)
            .userName(userName_)
            .computerName(computerName_)
            .prefix(prefix_)
            .directory(directory_)
            .startTime(startTime_)
            .channelNames(channelNames_)
            .channelColors(channelColors_)
            .channelContrastMins(channelContrastMins_)
            .channelContrastMaxes(channelContrastMaxes_)
            .initialPositionList(initialPositionList_)
            .zStepUm(zStepUm_)
            .waitIntervalMs(waitIntervalMs_)
            .customIntervalsMs(customIntervalsMs_)
            .timeFirst(timeFirst_)
            .slicesFirst(slicesFirst_)
            .keepShutterOpenSlices(keepShutterOpenSlices_)
            .keepShutterOpenChannels(keepShutterOpenChannels_)
            .pixelType(pixelType_)
            .numComponents(numComponents_)
            .ijType(ijType_)
            .frame(frame_)
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
   public UUID getUuid() {
      return uuid_;
   }

   @Override
   public String getSource() {
      return source_;
   }

   @Override
   public String getMicroManagerVersion() {
      return microManagerVersion_;
   }

   @Override
   public String getMetadataVersion() {
      return metadataVersion_;
   }

   @Override
   public String getAcquisitionName() {
      return acquisitionName_;
   }

   @Override
   public String getFileName() {
      return fileName_;
   }

   @Override
   public String getUserName() {
      return userName_;
   }

   @Override
   public String getComputerName() {
      return computerName_;
   }

   @Override
   public String getPrefix() {
      return prefix_;
   }

   @Override
   public String getDirectory() {
      return directory_;
   }

   @Override
   public String getStartTime() {
      return startTime_;
   }

   @Override
   public String[] getChannelNames() {
      return channelNames_;
   }

   @Override
   public Color[] getChannelColors() {
      return channelColors_;
   }

   @Override
   public Integer[] getChannelContrastMins() {
      return channelContrastMins_;
   }

   @Override
   public Integer[] getChannelContrastMaxes() {
      return channelContrastMaxes_;
   }

   @Override
   public MultiStagePosition getInitialPositionList() {
      return initialPositionList_;
   }

   @Override
   public Double getZStepUm() {
      return zStepUm_;
   }

   @Override
   public Double getWaitIntervalMs() {
      return waitIntervalMs_;
   }

   @Override
   public Double[] getCustomIntervalsMs() {
      return customIntervalsMs_;
   }

   @Override
   public Boolean getTimeFirst() {
      return timeFirst_;
   }

   @Override
   public Boolean getSlicesFirst() {
      return slicesFirst_;
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
   public Integer getNumComponents() {
      return numComponents_;
   }

   @Override
   public Integer getIjType() {
      return ijType_;
   }

   @Override
   public Integer getFrame() {
      return frame_;
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
      return ROI_;
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
}
