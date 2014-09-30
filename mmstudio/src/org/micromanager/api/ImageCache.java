///////////////////////////////////////////////////////////////////////////////
//FILE:          ImageCache.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Arthur Edelstein
//
// COPYRIGHT:    University of California, San Francisco, 2012
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


package org.micromanager.api;

import java.awt.Color;
import java.util.Set;

import mmcorej.TaggedImage;

import org.json.JSONObject;

/**
 * An interface, implemented by MMImageCache. See also TaggedImageStorage.
 * The ImageCache is expected to use another TaggedImageStorage for actual
 * storage. It contains various helper functions for interrogating the
 * stored data set.
 */
public interface ImageCache extends TaggedImageStorage {

   /**
    * Adds the provided cache listener. The listener will be notified
    * whenever an image is added or finished() has been called.
    */
   void addImageCacheListener(ImageCacheListener l);

   /**
    * Get a list of keys that are not identical for every TaggedImage.
    * The Set returns changes over time as new images are loaded
    * or received.
    */
   Set<String> getChangingKeys();

   /**
    * Gets the overall comment string for this data set.
    */
   String getComment();

   /**
    * Gets a list of image cache listeners.
    */
   ImageCacheListener[] getImageCacheListeners();

   /**
    * Returns the image tags for the last received image.
    */
   JSONObject getLastImageTags();

   /**
    * Removes an imageCacheListener so that it will no longer be notified
    * of relevant events.
    */
   void removeImageCacheListener(ImageCacheListener l);

   /**
    * Save a new copy of a TaggedImage data set, stored in a TaggedImageStorage
    * object. The new data set will be used by this cache in the future.
    */
   void saveAs(TaggedImageStorage newImageFileManager);

   /**
    * Save a new copy of a TaggedImage data set, stored in a TaggedImageStorage
    * object. The data set will be use by this cache in the future if
    * moveToNewStorage is true.
    */
   void saveAs(TaggedImageStorage newImageFileManager, boolean moveToNewStorage);

   /**
    * Return true if the ImageCache is still valid, false otherwise.
    */
   boolean getIsOpen();

   /**
    * Set the data set's comment string for this image cache.
    */
   void setComment(String text);

   /**
    * Set the comment string for an individual image. The image is specified
    * by indices given in tags.
    */
   void setImageComment(String comment, JSONObject tags);

   /**
    * Returns the image comment for a particular image. The image is specified
    * by indices given in tags.
    */
   String getImageComment(JSONObject tags);

   /**
    * Store the display settings for a particular channel.
    * @param channelIndex - The channel index for which settings are being specified
    * @param min - The minimum display intensity value (shown as black)
    * @param max - The maximum display intensity value (shown as full-intensity color)
    * @param gamma - The gamma value (curvature of the value-to-display curve)
    * @param histMax - The preferred maximum value at which the histogram is displayed.
    * @param displayMode - Composite, Color, or Gray-scale
    */
   public void storeChannelDisplaySettings(int channelIndex, int min, int max, 
           double gamma, int histMax, int displayMode);

   /**
    * Returns a JSONObject with channel settings for the channel whose index
    * is the argument.
    */
   public JSONObject getChannelSetting(int channel);

   /**
    * Returns the bit depth of all images in the image cache.
    */
   public int getBitDepth();

   /**
    * Returns the display mode used by the CompositeImage (composite, color, or grayscale)
    */
   public int getDisplayMode(); 
   
   /**
    * Returns the preferred display color for a channel, specified by channelIndex.
    */
   public Color getChannelColor(int channelIndex);

   /**
    * Sets the preferred display color for a channel
    * @param channel - The channel index
    * @param rgb - A 6-byte integer specifying the color: e.g., 0xFFFFFF is white.
    */
   public void setChannelColor(int channel, int rgb);

   /**
    * Returns the name of a channel specified by channelIndex.
    */
   public String getChannelName(int channelIndex);

   /**
    * Gets the minimum intensity value for a channel display (typically shown
    * as black).
    */
   public int getChannelMin(int channelIndex);

   /**
    * Gets the maximum intensity value for a channel display (typically shown
    * as full intensity color).
    */
   public int getChannelMax(int channelIndex) ;

   /**
    * Returns the gamma for the channel display.
    */
   public double getChannelGamma(int channelIndex);

   /**
    * Returns the preferred maximum value for the channel's histogram.
    */
   public int getChannelHistogramMax(int channelIndex);

   /**
    * Returns the number of channels in the ImageCache. More channels
    * may appear if more images are received with new channel indices.
    */
   public int getNumDisplayChannels();

   /**
    * Returns the pixel type for images in this image cache.
    */
   public String getPixelType();
   
   /**
    * Return the TaggedImage at the specified coordinates, or null if that
    * image does not exist.
    */
   public TaggedImage getImage(int channel, int slice, int frame, int position);
}
