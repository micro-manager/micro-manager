package org.micromanager.api.data;

import org.json.JSONObject;

import org.micromanager.api.MultiStagePosition;

/**
 * This class defines the summary metadata that applies to all images in a 
 * dataset. It is immutable; construct it with a SummaryMetadataBuilder.
 * All fields of the SummaryMetadata that are not explicitly initialized will
 * default to null.
 * You are not expected to implement this interface; it is here to describe how
 * you can interact with SummaryMetadata created by Micro-Manager itself. If
 * you need to create a new SummaryMetadat, either use the
 * getSummaryMetadataBuilder() method of the DataManager class, or call the
 * copy() method of an existing SummaryMetadata instance.
 */
public interface SummaryMetadata {

   interface SummaryMetadataBuilder {
      /**
       * Construct a SummaryMetadata from the SummaryMetadataBuilder. Call 
       * this once you are finished setting all SummaryMetadata parameters.
       */
      SummaryMetadata build();

      // The following functions each set the relevant value for the 
      // SummaryMetadata.
      SummaryMetadataBuilder acquisitionName(String acquisitionName);
      SummaryMetadataBuilder fileName(String fileName);
      SummaryMetadataBuilder prefix(String prefix);
      SummaryMetadataBuilder userName(String userName);
      SummaryMetadataBuilder microManagerVersion(String microManagerVersion);
      SummaryMetadataBuilder metadataVersion(String metadataVersion);
      SummaryMetadataBuilder computerName(String computerName);
      SummaryMetadataBuilder directory(String directory);
      SummaryMetadataBuilder comments(String comments);
      
      SummaryMetadataBuilder waitInterval(Double waitInterval);
      SummaryMetadataBuilder customIntervalsMs(Double[] customIntervalsMs);
      SummaryMetadataBuilder startDate(String startDate);
      SummaryMetadataBuilder numComponents(Integer numComponents);
      SummaryMetadataBuilder stagePositions(MultiStagePosition[] stagePositions);
   }

   /**
    * Generate a new SummaryMetadataBuilder whose values are initialized to be
    * the values of this SummaryMetadata.
    */
   SummaryMetadataBuilder copy();

   public String getAcquisitionName();
   public String getFileName();
   public String getPrefix();
   public String getUserName();
   public String getMicroManagerVersion();
   public String getMetadataVersion();
   public String getComputerName();
   public String getDirectory();
   public String getComments();
   public Double getWaitInterval();
   public Double[] getCustomIntervalsMs();
   public String getStartDate();
   public Integer getNumComponents();
   public MultiStagePosition[] getStagePositions();

   /**
    * For legacy support only: convert to JSONObject.
    */
   public JSONObject legacyToJSON();
}
