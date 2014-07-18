package org.micromanager.data;

import java.awt.Color;
import java.util.UUID;

import org.micromanager.api.MultiStagePosition;


/**
 * This interface defines the metadata for Images. Note that Metadatas are 
 * immutable; if you need to modify one, create a new one using a 
 * MetadataBuilder.
 * All fields of the Metadata that are not explicitly initialized will default
 * to null.
 */
interface Metadata {

   interface MetadataBuilder {
      /**
       * Construct a Metadata from the MetadataBuilder. Call this once you are
       * finished setting all Metadata parameters.
       */
      Metadata build();

      // The following functions each set the relevant value for the Metadata.
      MetadataBuilder uuid(UUID uuid);
      MetadataBuilder source(String source);
      MetadataBuilder microManagerVersion(String microManagerVersion);
      MetadataBuilder metadataVersion(String metadataVersion);
      MetadataBuilder acquisitionName(String acquisitionName);
      MetadataBuilder fileName(String fileName);
      MetadataBuilder userName(String userName);
      MetadataBuilder computerName(String computerName);
      MetadataBuilder prefix(String prefix);
      MetadataBuilder directory(String directory);
      MetadataBuilder startTime(String startTime);
      MetadataBuilder channelNames(String[] channelNames);
      MetadataBuilder channelColors(Color[] channelColors);
      MetadataBuilder channelContrastMins(int[] channelContrastMins);
      MetadataBuilder channelContrastMaxes(int[] channelContrastMaxes);
      MetadataBuilder initialPositionList(MultiStagePosition initialPositionList);
      MetadataBuilder zStepUm(double zStepUm);
      MetadataBuilder waitIntervalMs(double waitIntervalMs);
      MetadataBuilder customIntervalsMs(double[] customIntervalsMs);
      MetadataBuilder timeFirst(boolean timeFirst);
      MetadataBuilder slicesFirst(boolean slicesFirst);
      MetadataBuilder keepShutterOpenSlices(boolean keepShutterOpenSlices);
      MetadataBuilder keepShutterOpenChannels(boolean keepShutterOpenChannels);
      MetadataBuilder pixelType(String pixelType);
      MetadataBuilder numComponents(int numComponents);
      MetadataBuilder ijType(int ijType);
      MetadataBuilder frame(int frame);
      MetadataBuilder channelName(String channelName);
      MetadataBuilder exposureMs(double exposureMs);
      MetadataBuilder elapsedTimeMs(double elapsedTimeMs);
      MetadataBuilder startTimeMs(double startTimeMs);
      MetadataBuilder binning(int binning);
      MetadataBuilder imageNumber(int imageNumber);
      MetadataBuilder gridRow(int gridRow);
      MetadataBuilder gridColumn(int gridColumn);
      MetadataBuilder positionName(String positionName);
      MetadataBuilder xPositionUm(double xPositionUm);
      MetadataBuilder yPositionUm(double yPositionUm);
      MetadataBuilder zPositionUm(double zPositionUm);
      MetadataBuilder pixelSizeUm(double pixelSizeUm);
      MetadataBuilder camera(String camera);
      MetadataBuilder receivedTime(String receivedTime);
      MetadataBuilder excitationLabel(String excitationLabel);
      MetadataBuilder emissionLabel(String emissionLabel);
      MetadataBuilder ROI(String ROI);
      MetadataBuilder comments(String comments);
      MetadataBuilder color(int color);
      MetadataBuilder pixelAspect(double pixelAspect);
   }

   Metadata(MetadataBuilder builder);
  
   /**
    * Generate a new MetadataBuilder whose values are initialized to be
    * the values of this Metadata.
    */
   MetadataBuilder copy();

   UUID getUuid();
   String getSource();
   String getMicroManagerVersion();
   String getMetadataVersion();
   String getAcquisitionName();
   String getFileName();
   String getUserName();
   String getComputerName();
   String getPrefix();
   String getDirectory();
   String getStartTime();
   String[] getChannelNames();
   Color[] getChannelColors();
   int[] getChannelContrastMins();
   int[] getChannelContrastMaxes();
   MultiStagePosition getInitialPositionList();
   double getZStepUm();
   double getWaitIntervalMs();
   double[] getCustomIntervalsMs();
   boolean getTimeFirst();
   boolean getSlicesFirst();
   boolean getKeepShutterOpenSlices();
   boolean getKeepShutterOpenChannels();
   String getPixelType();
   int getNumComponents();
   int getIjType();
   int getFrame();
   String getChannelName();
   double getExposureMs();
   double getElapsedTimeMs();
   double getStartTimeMs();
   int getBinning();
   int getImageNumber();
   int getGridRow();
   int getGridColumn();
   String getPositionName();
   double getXPositionUm();
   double getYPositionUm();
   double getZPositionUm();
   double getPixelSizeUm();
   String getCamera();
   String getReceivedTime();
   String getExcitationLabel();
   String getEmissionLabel();
   String getROI();
   String getComments();
   int getColor();
   double getPixelAspect();
}
