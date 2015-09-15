///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     Data API
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

package org.micromanager.data;

import java.awt.Rectangle;
import java.util.UUID;

import org.micromanager.MultiStagePosition;
import org.micromanager.PropertyMap;


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
 *
 * This class uses a Builder pattern. Please see
 * https://micro-manager.org/wiki/Using_Builders
 * for more information.
 */
public interface Metadata {

   interface MetadataBuilder {
      /**
       * Construct a Metadata from the MetadataBuilder. Call this once you are
       * finished setting all Metadata parameters.
       * @return a Metadata instance based on the state of the MetadataBuilder.
       */
      Metadata build();

      // The following functions each set the relevant value for the Metadata.
      // See the getter methods of Metadata, below, for information on these
      // properties.
      MetadataBuilder binning(Integer binning);
      MetadataBuilder bitDepth(Integer bitDepth);
      MetadataBuilder camera(String camera);
      MetadataBuilder comments(String comments);
      MetadataBuilder elapsedTimeMs(Double elapsedTimeMs);
      MetadataBuilder emissionLabel(String emissionLabel);
      MetadataBuilder excitationLabel(String excitationLabel);
      MetadataBuilder exposureMs(Double exposureMs);
      MetadataBuilder gridColumn(Integer gridColumn);
      MetadataBuilder gridRow(Integer gridRow);
      MetadataBuilder ijType(Integer ijType);
      MetadataBuilder imageNumber(Long imageNumber);
      MetadataBuilder initialPositionList(MultiStagePosition initialPositionList);
      MetadataBuilder keepShutterOpenChannels(Boolean keepShutterOpenChannels);
      MetadataBuilder keepShutterOpenSlices(Boolean keepShutterOpenSlices);
      MetadataBuilder pixelAspect(Double pixelAspect);
      MetadataBuilder pixelSizeUm(Double pixelSizeUm);
      MetadataBuilder pixelType(String pixelType);
      MetadataBuilder positionName(String positionName);
      MetadataBuilder receivedTime(String receivedTime);
      MetadataBuilder ROI(Rectangle ROI);
      MetadataBuilder source(String source);
      MetadataBuilder startTimeMs(Double startTimeMs);
      MetadataBuilder scopeData(PropertyMap scopeData);
      MetadataBuilder userData(PropertyMap userData);
      MetadataBuilder uuid(UUID uuid);
      MetadataBuilder xPositionUm(Double xPositionUm);
      MetadataBuilder yPositionUm(Double yPositionUm);
      MetadataBuilder zPositionUm(Double zPositionUm);
   }

   /**
    * Generate a new MetadataBuilder whose values are initialized to be
    * the values of this Metadata.
    * @return a MetadataBuilder based on this Metadata.
    */
   MetadataBuilder copy();

   Boolean getKeepShutterOpenChannels();
   Boolean getKeepShutterOpenSlices();
   
   /** 
    * The time at which Micro-Manager received this image, in milliseconds.
    * There can be substantial jitter in this value; as a rule of thumb it
    * should not be assumed to be accurate to better than 20ms or so.
    * @return Milliseconds since the start of the acquisition up to the moment
    * this image was received by Micro-Manager
    */
   Double getElapsedTimeMs();
   
   /** 
    * How long of an exposure was used to collect this image 
    * @return Camera exposure (in ms) of this image
    */
   Double getExposureMs();
   
   /** 
    * The aspect ratio of the pixels in this image.
    * TODO: is this X/Y or Y/X?
    * @return  Pixels aspect ratio
    */
   Double getPixelAspect();
   
   /** 
    * How much of the sample, in microns, a single pixel of the camera sees
    * @return Sample pixel size in microns 
    */
   Double getPixelSizeUm();
   
   /** 
    * TODO: what is this? 
    * @return 
    */
   Double getStartTimeMs();
   
   /** 
    * The X stage position of the sample for this image 
    * @return X position of the default XY stage for this image
    */
   Double getXPositionUm();
   
   /** 
    * The Y stage position of the sample for this image
    * @return Y position of the default XY stage for this image
    */
   Double getYPositionUm();
   
   /** 
    * The Z stage position of the sample for this image 
    * @return Position of the default focus stage for this image
    */
   Double getZPositionUm();
   
   /** 
    * The binning mode of the camera for this image 
    * @return binning mode of the camera for this image 
    */
   Integer getBinning();
   
   /** 
    * The number of bits used to represent each pixel (e.g. 12-bit means that
     * pixel values range from 0 to 4095) 
    * @return Number of bits used to represent each pixel
     */
   Integer getBitDepth();
   
   /** 
    * When acquiring a grid of stage positions, the Y position in the grid 
    * @return Y position in the grid
    */
   Integer getGridColumn();
   
   /** 
    * When acquiring a grid of stage positions, the X position in the grid 
    * @return X position in the grid
    */
   Integer getGridRow();
   
   /** 
    * The ImageJ pixel type, e.g. ImagePlus.GRAY8, ImagePlus.RGB32 
    * @return ImageJ pixel type
    */
   Integer getIjType();
   
   /** 
    * The sequence number of this image, for sequence acquisitions 
    * @return sequence number of this image
    */
   Long getImageNumber();

   /**
    * Any information provided by Micro-Manager or its device adapters that
    * is relevant to this image. This includes a copy of the Core's
    * knowledge of every device property at the time of image acquisition.
    * It is possible that the values in this object are not up to date, as
    * devices are allowed to change their settings without notifying the Core,
    * and the Core does not manually update its copy of device settings after
    * each image, for performance reasons.
    * @return Miscellaneous medatada provided by the microscope.
    */
   public PropertyMap getScopeData();

   /** 
    * Arbitrary additional metadata added by third-party code 
    * @return rbitrary additional metadata added by third-party code 
    */
   PropertyMap getUserData();
   
   /** 
    * List of stage positions at the start of the acquisition (e.g. not taking
    * into account changes caused by autofocus). TODO: should be part of
    * SummaryMetadata?
    * @return stage positions at the start of the acquisition
    */
   MultiStagePosition getInitialPositionList();
   
   /** 
    * The ROI of the camera when acquiring this image 
    * @return Camera ROI
    */
   Rectangle getROI();
   
   /** 
    * The name of the camera for this image 
    * @return Camera name
    */
   String getCamera();
   
   /** 
    * Any user-supplied comments for this specific image 
    * @return user-supplied image comments
    */
   String getComments();
   
   /** 
    * The emission filter for this image
    * TODO: Is this used? Remove?
    * @return  Emission Label
    */
   String getEmissionLabel();
   
   /** 
    * The excitation filter for this image 
    * TODO: Is this used? Remove?
    * @return Excitation label
    */
   String getExcitationLabel();
   
   /** 
    * Seems to be a string version of the "IjType" field (TODO: remove?) 
    * @return string version of the "IjType" field
    */
   String getPixelType();
   
   /** 
    * Any name attached to the stage position at which this image was
    * acquired 
    * @return Name of the position at which this image was acquired
    */
   String getPositionName();
   
   /**
    * The time at which the Java layer of Micro-Manager receives the image from
    * the Core, expressed as a date-time string. Depending on the parameters
    * for your acquisition, there may be significant and/or variable delay
    * between the time the image was actually acquired by the camera and the
    * time in this field. It will always be at least a little behind.
    * @return Time at which this image was received by Micro-Manager
    */
   String getReceivedTime();
   
   /** 
    * TODO: what is this? 
    * @return ???
    */
   String getSource();
   
   /** 
    * A unique identifier for this specific image
    * @return Unique identifier 
    */
   UUID getUUID();
}
