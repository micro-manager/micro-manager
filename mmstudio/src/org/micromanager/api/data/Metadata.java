package org.micromanager.api.data;

import org.json.JSONObject;

import java.awt.Color;
import java.awt.Rectangle;
import java.util.UUID;

import org.micromanager.api.MultiStagePosition;


/**
 * This interface defines the metadata for Images. Note that Metadatas are 
 * immutable; if you need to modify one, create a new one using a 
 * MetadataBuilder.
 * All fields of the Metadata that are not explicitly initialized will default
 * to null.
 */
public interface Metadata {

   interface MetadataBuilder {
      /**
       * Construct a Metadata from the MetadataBuilder. Call this once you are
       * finished setting all Metadata parameters.
       */
      Metadata build();

      // The following functions each set the relevant value for the Metadata.
      MetadataBuilder ROI(Rectangle ROI);
      MetadataBuilder binning(Integer binning);
      MetadataBuilder camera(String camera);
      MetadataBuilder channelName(String channelName);
      MetadataBuilder color(Integer color);
      MetadataBuilder comments(String comments);
      MetadataBuilder elapsedTimeMs(Double elapsedTimeMs);
      MetadataBuilder emissionLabel(String emissionLabel);
      MetadataBuilder excitationLabel(String excitationLabel);
      MetadataBuilder exposureMs(Double exposureMs);
      MetadataBuilder gridColumn(Integer gridColumn);
      MetadataBuilder gridRow(Integer gridRow);
      MetadataBuilder ijType(Integer ijType);
      MetadataBuilder imageNumber(Integer imageNumber);
      MetadataBuilder initialPositionList(MultiStagePosition initialPositionList);
      MetadataBuilder keepShutterOpenChannels(Boolean keepShutterOpenChannels);
      MetadataBuilder keepShutterOpenSlices(Boolean keepShutterOpenSlices);
      MetadataBuilder numComponents(Integer numComponents);
      MetadataBuilder pixelAspect(Double pixelAspect);
      MetadataBuilder pixelSizeUm(Double pixelSizeUm);
      MetadataBuilder pixelType(String pixelType);
      MetadataBuilder positionName(String positionName);
      MetadataBuilder receivedTime(String receivedTime);
      MetadataBuilder ROI(Rectangle ROI);
      MetadataBuilder source(String source);
      MetadataBuilder startTimeMs(Double startTimeMs);
      MetadataBuilder summaryMetadata(SummaryMetadata summaryMetadata);
      MetadataBuilder userMetadata(JSONObject userMetadata);
      MetadataBuilder uuid(UUID uuid);
      MetadataBuilder xPositionUm(Double xPositionUm);
      MetadataBuilder yPositionUm(Double yPositionUm);
      MetadataBuilder zPositionUm(Double zPositionUm);
      MetadataBuilder zStepUm(Double zStepUm);
   }

   /**
    * Generate a new MetadataBuilder whose values are initialized to be
    * the values of this Metadata.
    */
   MetadataBuilder copy();

   Boolean getKeepShutterOpenChannels();
   Boolean getKeepShutterOpenSlices();
   Double getElapsedTimeMs();
   Double getExposureMs();
   Double getPixelAspect();
   Double getPixelSizeUm();
   Double getStartTimeMs();
   Double getXPositionUm();
   Double getYPositionUm();
   Double getZPositionUm();
   Double getZStepUm();
   Integer getBinning();
   Integer getColor();
   Integer getGridColumn();
   Integer getGridRow();
   Integer getIjType();
   Integer getImageNumber();
   Integer getNumComponents();
   JSONObject getUserMetadata();
   MultiStagePosition getInitialPositionList();
   Rectangle getROI();
   String getCamera();
   String getChannelName();
   String getComments();
   String getEmissionLabel();
   String getExcitationLabel();
   String getPixelType();
   String getPositionName();
   String getReceivedTime();
   String getSource();
   SummaryMetadata getSummaryMetadata();
   UUID getUUID();

   /**
    * For legacy support only: convert to JSONObject.
    */
   public JSONObject legacyToJSON();
}
