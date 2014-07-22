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
      MetadataBuilder uuid(UUID uuid);
      MetadataBuilder source(String source);
      MetadataBuilder initialPositionList(MultiStagePosition initialPositionList);
      MetadataBuilder zStepUm(Double zStepUm);
      MetadataBuilder keepShutterOpenSlices(Boolean keepShutterOpenSlices);
      MetadataBuilder keepShutterOpenChannels(Boolean keepShutterOpenChannels);
      MetadataBuilder pixelType(String pixelType);
      MetadataBuilder numComponents(Integer numComponents);
      MetadataBuilder ijType(Integer ijType);
      MetadataBuilder channelName(String channelName);
      MetadataBuilder exposureMs(Double exposureMs);
      MetadataBuilder elapsedTimeMs(Double elapsedTimeMs);
      MetadataBuilder startTimeMs(Double startTimeMs);
      MetadataBuilder binning(Integer binning);
      MetadataBuilder imageNumber(Integer imageNumber);
      MetadataBuilder gridRow(Integer gridRow);
      MetadataBuilder gridColumn(Integer gridColumn);
      MetadataBuilder positionName(String positionName);
      MetadataBuilder xPositionUm(Double xPositionUm);
      MetadataBuilder yPositionUm(Double yPositionUm);
      MetadataBuilder zPositionUm(Double zPositionUm);
      MetadataBuilder pixelSizeUm(Double pixelSizeUm);
      MetadataBuilder camera(String camera);
      MetadataBuilder receivedTime(String receivedTime);
      MetadataBuilder excitationLabel(String excitationLabel);
      MetadataBuilder emissionLabel(String emissionLabel);
      MetadataBuilder ROI(Rectangle ROI);
      MetadataBuilder comments(String comments);
      MetadataBuilder color(Integer color);
      MetadataBuilder pixelAspect(Double pixelAspect);
      MetadataBuilder userMetadata(JSONObject userMetadata);
      MetadataBuilder summaryMetadata(SummaryMetadata summaryMetadata);
   }

   /**
    * Generate a new MetadataBuilder whose values are initialized to be
    * the values of this Metadata.
    */
   MetadataBuilder copy();

   UUID getUuid();
   String getSource();
   MultiStagePosition getInitialPositionList();
   Double getZStepUm();
   Boolean getKeepShutterOpenSlices();
   Boolean getKeepShutterOpenChannels();
   String getPixelType();
   Integer getNumComponents();
   Integer getIjType();
   String getChannelName();
   Double getExposureMs();
   Double getElapsedTimeMs();
   Double getStartTimeMs();
   Integer getBinning();
   Integer getImageNumber();
   Integer getGridRow();
   Integer getGridColumn();
   String getPositionName();
   Double getXPositionUm();
   Double getYPositionUm();
   Double getZPositionUm();
   Double getPixelSizeUm();
   String getCamera();
   String getReceivedTime();
   String getExcitationLabel();
   String getEmissionLabel();
   Rectangle getROI();
   String getComments();
   Integer getColor();
   Double getPixelAspect();
   JSONObject getUserMetadata();
   SummaryMetadata getSummaryMetadata();
}
