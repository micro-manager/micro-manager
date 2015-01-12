package org.micromanager.data;

import org.json.JSONObject;

import java.awt.Color;
import java.awt.Rectangle;
import java.util.UUID;

import org.micromanager.MultiStagePosition;


/**
 * This interface defines the metadata for Images. Note that Metadatas are 
 * immutable; if you need to modify one, create a new one using a 
 * MetadataBuilder.
 * All fields of the Metadata that are not explicitly initialized will default
 * to null.
 * You are not expected to implement this interface; it is here to describe how
 * you can interact with Metadata created by Micro-Manager itself. If you need
 * to get a MetadataBuilder, call the getMetadataBuilder() method of the
 * DataManager class, or use the copy() method of an existing Metadata.
 */
public interface Metadata {

   interface MetadataBuilder {
      /**
       * Construct a Metadata from the MetadataBuilder. Call this once you are
       * finished setting all Metadata parameters.
       */
      Metadata build();

      // The following functions each set the relevant value for the Metadata.
      // See the getter methods of Metadat, below, for information on these
      // properties.
      MetadataBuilder binning(Integer binning);
      MetadataBuilder bitDepth(Integer bitDepth);
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
   }

   /**
    * Generate a new MetadataBuilder whose values are initialized to be
    * the values of this Metadata.
    */
   MetadataBuilder copy();

   Boolean getKeepShutterOpenChannels();
   Boolean getKeepShutterOpenSlices();
   /** The time at which Micro-Manager received this image, in milliseconds.
     * There can be substantial jitter in this value; as a rule of thumb it
     * should not be assumed to be accurate to better than 20ms or so. */
   Double getElapsedTimeMs();
   /** How long of an exposure was used to collect this image */
   Double getExposureMs();
   /** The aspect ratio of the pixels in this image.
     * TODO: is this X/Y or Y/X? */
   Double getPixelAspect();
   /** How much of the sample, in microns, a single pixel of the camera sees */
   Double getPixelSizeUm();
   /** TODO: what is this? */
   Double getStartTimeMs();
   /** The X stage position of the sample for this image */
   Double getXPositionUm();
   /** The Y stage position of the sample for this image */
   Double getYPositionUm();
   /** The Z stage position of the sample for this image */
   Double getZPositionUm();
   /** The binning mode of the camera for this image */
   Integer getBinning();
   /** The number of bits used to represent each pixel (e.g. 12-bit means that
     * pixel values range from 0 to 4095) */
   Integer getBitDepth();
   /** The color of this image (TODO: now properly part of DisplaySettings) */
   Integer getColor();
   /** When acquiring a grid of stage positions, the X position in the grid */
   Integer getGridColumn();
   /** When acquiring a grid of stage positions, the Y position in the grid */
   Integer getGridRow();
   /** The ImageJ pixel type, e.g. ImagePlus.GRAY8, ImagePlus.RGB32 */
   Integer getIjType();
   /** The sequence number of this image, for sequence acquisitions */
   Integer getImageNumber();
   /** How many sub-pixel components are in this image, for multi-component
     * images (TODO: now properly part of Image class) */
   Integer getNumComponents();
   /** Arbitrary additional metadata */
   JSONObject getUserMetadata();
   /** List of stage positions at the start of the acquisition (e.g. not taking
     * into account changes caused by autofocus). TODO: should be part of
     * SummaryMetadata?
     */
   MultiStagePosition getInitialPositionList();
   /** The ROI of the camera when acquiring this image */
   Rectangle getROI();
   /** The name of the camera for this image */
   String getCamera();
   /** The name of the channel for this image (e.g. DAPI or GFP) */
   String getChannelName();
   /** Any user-supplied comments for this specific image */
   String getComments();
   /** The emission filter for this image */
   String getEmissionLabel();
   /** The excitation filter for this image */
   String getExcitationLabel();
   /** Seems to be a string version of the "IjType" field (TODO: remove?) */
   String getPixelType();
   /** Any name attached to the stage position at which this image was
     * acquired */
   String getPositionName();
   /** The time at which this image was received by Micro-Manager (TODO:
     * difference from ElapsedTimeMs?) */
   String getReceivedTime();
   /** TODO: what is this? */
   String getSource();
   /** A link to the SummaryMetadata instance for the acquisition this image
     * was part of */
   SummaryMetadata getSummaryMetadata();
   /** A unique identifier for this specific image */
   UUID getUUID();

   /**
    * For legacy support only: convert to JSONObject.
    */
   public JSONObject legacyToJSON();
}
