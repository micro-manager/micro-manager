///////////////////////////////////////////////////////////////////////////////
//FILE:          TaggedImageStore.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// COPYRIGHT:    University of California, San Francisco, 2014
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

package org.micromanager.acquisition;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import mmcorej.TaggedImage;

import org.json.JSONException;
import org.json.JSONObject;

import org.micromanager.api.TaggedImageStorage;
import org.micromanager.utils.MDUtils;
import org.micromanager.utils.ReportingUtils;

/**
 * This simple storage class holds one image per channel, and is intended
 * solely for the Snap/Live view. We use it because TaggedImageStorageRamFast
 * is slightly too heavyweight for Snap/Live view -- it has memory issues when
 * run in a 32-bit environment.
 */
public class TaggedImageStorageLive implements TaggedImageStorage {
   private HashMap<Integer, TaggedImage> channelToImage_;
   private JSONObject summaryMetadata_;
   private JSONObject displayAndComments_;

   public TaggedImageStorageLive() {
      channelToImage_ = new HashMap<Integer, TaggedImage>();
      summaryMetadata_ = new JSONObject();
      displayAndComments_ = new JSONObject();
   }

   @Override
   public TaggedImage getImage(int channel, int slice, int frame, int position) {
      if (channelToImage_.containsKey(channel)) {
         // Fix the image's metadata to hold the desired coordinates.
         TaggedImage result = channelToImage_.get(channel);
         try {
            MDUtils.setSliceIndex(result.tags, slice);
            MDUtils.setFrameIndex(result.tags, frame);
            MDUtils.setPositionIndex(result.tags, position);
         }
         catch (JSONException e) {
            ReportingUtils.logError(e, "Couldn't adjust image's location data");
         }
         return result;
      }
      return null;
   }

   @Override
   public JSONObject getImageTags(int channel, int slice, int frame, int position) {
      TaggedImage image = getImage(channel, slice, frame, position);
      if (image != null) {
         return image.tags;
      }
      return null;
   }

   @Override
   public void putImage(TaggedImage image) {
      try {
         int channel = MDUtils.getChannelIndex(image.tags);
         channelToImage_.put(channel, image);
      }
      catch (JSONException e) {
         ReportingUtils.logError(e, "Couldn't extract channel from tags");
      }
   }

   @Override
   public Set<String> imageKeys() {
      HashSet<String> result = new HashSet<String>();
      for (int channel : channelToImage_.keySet()) {
         result.add(String.format("%d_0_0_0", channel));
      }
      return result;
   }

   /**
    * The Snap/Live storage can never be locked.
    */
   @Override
   public void finished() {}

   @Override
   public boolean isFinished() {
      return false;
   }

   @Override
   public void setSummaryMetadata(JSONObject summary) {
      summaryMetadata_ = summary;
   }

   @Override
   public JSONObject getSummaryMetadata() {
      return summaryMetadata_;
   }

   @Override
   public void setDisplayAndComments(JSONObject settings) {
      displayAndComments_ = settings;
   }

   @Override
   public JSONObject getDisplayAndComments() {
      return displayAndComments_;
   }

   @Override
   public void close() {}

   @Override
   public String getDiskLocation() {
      return null;
   }

   @Override
   public int lastAcquiredFrame() {
      return 0;
   }

   @Override
   public long getDataSetSize() {
      return 0;
   }

   @Override
   public void writeDisplaySettings() {}
}
