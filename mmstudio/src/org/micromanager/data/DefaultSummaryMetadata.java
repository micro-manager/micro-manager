package org.micromanager.data;

import java.awt.Color;

import org.micromanager.api.data.SummaryMetadata;
import org.micromanager.api.MultiStagePosition;

public class DefaultSummaryMetadata implements SummaryMetadata {

   public class DefaultSummaryMetadataBuilder implements SummaryMetadata.SummaryMetadataBuilder {

      private String acquisitionName_ = null;
      private String fileName_ = null;
      private String prefix_ = null;
      private String userName_ = null;
      private String microManagerVersion_ = null;
      private String metadataVersion_ = null;
      private String computerName_ = null;
      private String directory_ = null;
      
      private String[] channelNames_ = null;
      private Color[] channelColors_ = null;
      private Integer[] channelContrastMins_ = null;
      private Integer[] channelContrastMaxes_ = null;
      
      private Double waitInterval_ = null;
      private Double[] customIntervalsMs_ = null;
      private String startDate_ = null;
      private Integer numComponents_ = null;
      private MultiStagePosition[] stagePositions_ = null;

      @Override
      public SummaryMetadata build() {
         return new DefaultSummaryMetadata(this);
      }
      
      @Override
      public SummaryMetadataBuilder acquisitionName(String name) {
         acquisitionName_ = name;
         return this;
      }

      @Override
      public SummaryMetadataBuilder fileName(String fileName) {
         fileName_ = fileName;
         return this;
      }

      @Override
      public SummaryMetadataBuilder prefix(String prefix) {
         prefix_ = prefix;
         return this;
      }

      @Override
      public SummaryMetadataBuilder userName(String userName) {
         userName_ = userName;
         return this;
      }

      @Override
      public SummaryMetadataBuilder microManagerVersion(String microManagerVersion) {
         microManagerVersion_ = microManagerVersion;
         return this;
      }

      @Override
      public SummaryMetadataBuilder metadataVersion(String metadataVersion) {
         metadataVersion_ = metadataVersion;
         return this;
      }

      @Override
      public SummaryMetadataBuilder computerName(String computerName) {
         computerName_ = computerName;
         return this;
      }

      @Override
      public SummaryMetadataBuilder directory(String directory) {
         directory_ = directory;
         return this;
      }

      @Override
      public SummaryMetadataBuilder channelNames(String[] channelNames) {
         channelNames_ = channelNames;
         return this;
      }

      @Override
      public SummaryMetadataBuilder channelColors(Color[] channelColors) {
         channelColors_ = channelColors;
         return this;
      }

      @Override
      public SummaryMetadataBuilder channelContrastMins(Integer[] channelContrastMins) {
         channelContrastMins_ = channelContrastMins;
         return this;
      }

      @Override
      public SummaryMetadataBuilder channelContrastMaxes(Integer[] channelContrastMaxes) {
         channelContrastMaxes_ = channelContrastMaxes;
         return this;
      }

      @Override
      public SummaryMetadataBuilder waitInterval(Double waitInterval) {
         waitInterval_ = waitInterval;
         return this;
      }

      @Override
      public SummaryMetadataBuilder customIntervalsMs(Double[] customIntervalsMs) {
         customIntervalsMs_ = customIntervalsMs;
         return this;
      }

      @Override
      public SummaryMetadataBuilder startDate(String startDate) {
         startDate_ = startDate;
         return this;
      }

      @Override
      public SummaryMetadataBuilder numComponents(Integer numComponents) {
         numComponents_ = numComponents;
         return this;
      }

      @Override
      public SummaryMetadataBuilder stagePositions(MultiStagePosition[] stagePositions) {
         stagePositions_ = stagePositions;
         return this;
      }
   }
   
   private String acquisitionName_ = null;
   private String fileName_ = null;
   private String prefix_ = null;
   private String userName_ = null;
   private String microManagerVersion_ = null;
   private String metadataVersion_ = null;
   private String computerName_ = null;
   private String directory_ = null;
   
   private String[] channelNames_ = null;
   private Color[] channelColors_ = null;
   private Integer[] channelContrastMins_ = null;
   private Integer[] channelContrastMaxes_ = null;

   private Double waitInterval_ = null;
   private Double[] customIntervalsMs_ = null;
   private String startDate_ = null;
   private Integer numComponents_ = null;
   private MultiStagePosition[] stagePositions_ = null;

   public DefaultSummaryMetadata(DefaultSummaryMetadataBuilder builder) {
      acquisitionName_ = builder.acquisitionName_;
      fileName_ = builder.fileName_;
      prefix_ = builder.prefix_;
      userName_ = builder.userName_;
      microManagerVersion_ = builder.microManagerVersion_;
      metadataVersion_ = builder.metadataVersion_;
      computerName_ = builder.computerName_;
      directory_ = builder.directory_;
      
      channelNames_ = builder.channelNames_;
      channelColors_ = builder.channelColors_;
      channelContrastMins_ = builder.channelContrastMins_;
      channelContrastMaxes_ = builder.channelContrastMaxes_;

      waitInterval_ = builder.waitInterval_;
      customIntervalsMs_ = builder.customIntervalsMs_;
      startDate_ = builder.startDate_;
      numComponents_ = builder.numComponents_;
      stagePositions_ = builder.stagePositions_;
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
   public String getPrefix() {
      return prefix_;
   }

   @Override
   public String getUserName() {
      return userName_;
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
   public String getComputerName() {
      return computerName_;
   }

   @Override
   public String getDirectory() {
      return directory_;
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
   public Double getWaitInterval() {
      return waitInterval_;
   }

   @Override
   public Double[] getCustomIntervalsMs() {
      return customIntervalsMs_;
   }

   @Override
   public String getStartDate() {
      return startDate_;
   }

   @Override
   public Integer getNumComponents() {
      return numComponents_;
   }

   @Override
   public MultiStagePosition[] getStagePositions() {
      return stagePositions_;
   }

   @Override
   public MetadataBuilder copy() {
      return new DefaultMetadataBuilder()
            .name(acquisitionName_)
            .fileName(fileName_)
            .prefix(prefix_)
            .userName(userName_)
            .microManagerVersion(microManagerVersion_)
            .metadataVersion(metadataVersion_)
            .computerName(computerName_)
            .directory(directory_)
            .channelNames(channelNames_)
            .channelColors(channelColors_)
            .channelContrastMins(channelContrastMins_)
            .channelContrastMaxes(channelContrastMaxes_)
            .waitInterval(waitInterval_)
            .customIntervalsMs(customIntervalsMs_)
            .startDate(startDate_)
            .numComponents(numComponents_)
            .stagePositions(stagePositions_);
   }
}
