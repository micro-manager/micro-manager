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
import java.awt.geom.AffineTransform;
import java.util.UUID;
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
   interface Builder extends MetadataBuilder {
      @Override Builder binning(Integer binning);
      @Override Builder bitDepth(Integer bitDepth);
      @Override Builder camera(String camera);
      @Override Builder elapsedTimeMs(Double elapsedTimeMs);
      @Override Builder exposureMs(Double exposureMs);
      @Override Builder imageNumber(Long imageNumber);
      @Override Builder pixelAspect(Double pixelAspect);
      @Override Builder pixelSizeUm(Double pixelSizeUm);
      @Override Builder pixelSizeAffine(AffineTransform aff);
      @Override Builder positionName(String positionName);
      @Override Builder receivedTime(String receivedTime);
      /** Same as {@link #roi}. Use {@code roi} in new code. */
      @Override Builder ROI(Rectangle roi);
      Builder roi(Rectangle roi);
      /**
       * Add device property data.
       * This method will remove all previously added scope data from this
       * builder.
       * @param scopeData device properties; keys should be in
       * "DeviceLabel-PropertyName" format, and values should be strings
       * (numbers will be converted to strings)
       */
      @Override Builder scopeData(PropertyMap scopeData);
      /**
       * Add user-defined data.
       * This method will remove all previously added user data from this
       * builder.
       * @param userData any valid (potentially nested) property map containing
       * user data
       */
      @Override Builder userData(PropertyMap userData);
      /** @deprecated Use {@link #generateUUID}. */
      @Deprecated @Override Builder uuid();
      Builder generateUUID();
      @Override Builder uuid(UUID uuid);
      @Override Builder xPositionUm(Double xPositionUm);
      @Override Builder yPositionUm(Double yPositionUm);
      @Override Builder zPositionUm(Double zPositionUm);
      Builder fileName(String filename);
   }

   /**
    * @deprecated Use {@link Metadata.Builder} instead
    */
   @Deprecated
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
      MetadataBuilder elapsedTimeMs(Double elapsedTimeMs);
      MetadataBuilder exposureMs(Double exposureMs);
      MetadataBuilder imageNumber(Long imageNumber);
      MetadataBuilder pixelAspect(Double pixelAspect);
      MetadataBuilder pixelSizeUm(Double pixelSizeUm);
      MetadataBuilder pixelSizeAffine(AffineTransform aff);
      MetadataBuilder positionName(String positionName);
      MetadataBuilder receivedTime(String receivedTime);
      MetadataBuilder ROI(Rectangle ROI);
      MetadataBuilder scopeData(PropertyMap scopeData);
      MetadataBuilder userData(PropertyMap userData);
      @Deprecated
      MetadataBuilder uuid();
      MetadataBuilder uuid(UUID uuid);
      MetadataBuilder xPositionUm(Double xPositionUm);
      MetadataBuilder yPositionUm(Double yPositionUm);
      MetadataBuilder zPositionUm(Double zPositionUm);
   }

   /** 
    * Return a builder with the same content, preserving the image UUID. 
    * The UUID is a unique identifier for the image. Micro-Manager uses this
    * field to determine if two images are truly different, so if you copy this
    * Metadata instance to apply to a new image, use another builder
    * @return Builder, useful for command chaining
    */
   Builder copyBuilderPreservingUUID();
   
   /** Return a builder with the same content, assigning a new image UUID. 
    * The UUID is a unique identifier for the image. Micro-Manager uses this
    * field to determine if two images are truly different, so if you copy this
    * Metadata instance to apply to a new image, use this builder
    * @return Builder, useful for command chaining
    */
   Builder copyBuilderWithNewUUID();
   
   /** Return a builder with the same content but removing the image UUID. 
    * The UUID is a unique identifier for the image. Micro-Manager uses this
    * field to determine if two images are truly different.
    * @return Builder, useful for command chaining
    */
   Builder copyBuilderRemovingUUID();

   /**
    * @deprecated Use the appropriate of the following:
    * {@link #copyBuilderPreservingUUID}, {@link #copyBuilderWithNewUUID},
    * {@link #copyBuilderRemovingUUID}. This method is equivalent to
    * {@code copyBuilderPreservingUUID}.
    */
   @Deprecated
   MetadataBuilder copy();

   /** 
    * The time at which Micro-Manager received this image, in milliseconds.
    * There can be substantial jitter in this value; as a rule of thumb it
    * should not be assumed to be accurate to better than 20ms or so.
    * @return Milliseconds since the start of the acquisition up to the moment
    * this image was received by Micro-Manager
    
    * @deprecated - use Double {@link #getElapsedTimeMs(double) } instead
   */
   @Deprecated
   Double getElapsedTimeMs();
   
   /**
    * Whether or not this image has metadata indicating the time elapsed
    * since the start of the acquisition
    * @return true if the image metadata has a field indicating the elapsed time
    */
   boolean hasElapsedTimeMs();
   
   /**
    * Time in milliseconds since the start of the given data acquisition
    * at which the image was received by Micro-Manager.  This is a proxy 
    * for the time at which the exposure happened, but there is considerable
    * jitter so it should not be assumed to be accurate to more than ~20 ms. 
    * @param defaultValue - value returned of the image metadata did not contain
    * information about the elapsed time
    * @return Milliseconds since the start of the acquisition up to the moment
    * this image was received by Micro-Manager
    */
   double getElapsedTimeMs(double defaultValue);

   
   /** 
    * How long of an exposure was used to collect this image 
    * @return Camera exposure (in ms) of this image
    */
   Double getExposureMs();
   
   /** 
    * The aspect ratio of the pixels in this image, as a Y/X ratio (e.g.
    * 2.0 means that the pixels are twice as tall as they are wide).
    * @return  Pixels aspect ratio
    */
   Double getPixelAspect();
   
   /** 
    * How much of the sample, in microns, a single pixel of the camera sees
    * @return Sample pixel size in microns 
    */
   Double getPixelSizeUm();
   
   /**
    * Geometric relation between stage movement (in microns) and pixels
    * @return Affine transform describing geometric relation between stage
    * movement (in microns) and camera (in pixels)
    */
   AffineTransform getPixelSizeAffine();
   
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
   PropertyMap getScopeData();

   /** 
    * Arbitrary additional metadata added by third-party code 
    * @return arbitrary additional metadata added by third-party code 
    */
   PropertyMap getUserData();
   
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
    * Any name attached to the stage position at which this image was
    * acquired 
    * @return Name of the position at which this image was acquired
    * @deprecated Use {@link #getPositionName(java.lang.String) } instead
    */
   @Deprecated
   String getPositionName();
   
   boolean hasPositionName();
   
   /** 
    * Any name attached to the stage position at which this image was
    * acquired 
    * @param defaultPosName Will be returned if no name is provided by the metadata
    * @return Name of the position at which this image was acquired
    */
   String getPositionName(String defaultPosName);
   
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
    * A unique identifier for this specific image. Micro-Manager uses this
    * field to determine if two images are truly different, so if you copy this
    * Metadata instance to apply to a new image, you should make certain to
    * set a new UUID for it (which you can conveniently do using
    * MetadataBuilder.uuid() with no parameter).
    * @return Unique identifier
    */
   UUID getUUID();

   /**
    * The name of the file from which the image was loaded, if applicable.
    * @return filename.
    */
   String getFileName();
}