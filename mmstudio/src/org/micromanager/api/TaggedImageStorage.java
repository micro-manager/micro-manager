///////////////////////////////////////////////////////////////////////////////
//FILE:          TaggedImageStore.java
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

import java.io.IOException;
import java.util.Set;
import mmcorej.TaggedImage;
import org.json.JSONObject;
import org.micromanager.utils.MMException;

/**
 * This interface is implemented by TaggedImageStorage classes. Implementing
 * classes will store images (in RAM, on disk, or otherwise) whenever they
 * arrive via putImage, and will return stored images when they are
 * requested by getImage. Each image is expected to be mapped to four indices:
 * channel, slice, frame, and position.
 */
public interface TaggedImageStorage {
   public static String storagePluginName = null;

   /*
    * Get the pixels and metadata for a particular image.
    */
   public TaggedImage getImage(int channelIndex, int sliceIndex,
                               int frameIndex, int positionIndex);

   /*
    * Get the image tags (without necessarily reading the pixels) for
    * a particular image.
    */
   public JSONObject getImageTags(int channelIndex, int sliceIndex,
                               int frameIndex, int positionIndex);

   /*
    * Call this method to add a TaggedImage to the storage.
    */
   public void putImage(TaggedImage taggedImage) throws MMException, IOException;

   /*
    * Returns the set of image keys available in this image store. An
    * image key is a String contained the four indices, separated by
    * underscores.
    */
   public Set<String> imageKeys();

   /**
    * Call this function when no more images are expected
    * Finishes writing the metadata file and closes it.
    * After calling this function, the imagestorage is read-only
    */
   public void finished();
   
   /*
    * Returns true if finished() has been called, or if this is an
    * old data set that has been loaded from disk, etc.
    */
   public boolean isFinished();

   /*
    * Sets the summary metadata associated with this image set. Should
    * be called before images are supplied.
    */
   public void setSummaryMetadata(JSONObject md);

   /*
    * Returns the summary metadata associated with this image set.
    */
   public JSONObject getSummaryMetadata();

   /*
    * Update the display settings and (summary and per-image) comments.
    */
   public void setDisplayAndComments(JSONObject settings);

   /*
    * Returns a map of display settings (such as channel colors)
    * and any stored comments.
    */
   public JSONObject getDisplayAndComments();

   /**
    * Disposes of the tagged images from memory -- images will only
    * be stored in disk.
    */
   public void close();

   /*
    * Provides the location of the data set, if it is stored on disk
    * (null otherwise).
    */
   public String getDiskLocation();

   /*
    * Gives the frameIndex for the most recent TaggedImage that has been
    * received.
    */
   public int lastAcquiredFrame();

   /*
    * Returns an estimate of the size of stored data, in bytes.
    */
   public long getDataSetSize();

   /*
    * Writes the display settings to disk, if appropriate.
    */
   public void writeDisplaySettings();
}
