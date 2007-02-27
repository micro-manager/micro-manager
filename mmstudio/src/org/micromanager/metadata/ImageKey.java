///////////////////////////////////////////////////////////////////////////////
//FILE:          ImageKey.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nenad Amodaj, nenad@amodaj.com, May 29, 2006
//
// COPYRIGHT:    University of California, San Francisco, 2006
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
//
// CVS:          $Id$
//
package org.micromanager.metadata;

import java.text.DecimalFormat;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Generates filenames and image keys for JSON metadata indexing.
 */
public class ImageKey {
   public static final String IMAGE_PREFIX = "img";
   public static final String METADATA_FILE_NAME = "metadata.txt";
   
   private static final DecimalFormat fmt2dec = new DecimalFormat("#0.00");

   public static String generateFrameKey(int frame, int channel, int slice) {
      String key = "FrameKey-" + frame + "-" + channel + "-" + slice;
      return key;      
   }
   
   public static String generateFileName(int frame, String channel, int slice) {
      String name = IMAGE_PREFIX + "_" + frame + "_" + channel + "_" + slice + ".tif";
      return name;
   }
   
   public static String getImageInfo(JSONObject metadata, int frame, int channel, int slice) {
      String key = ImageKey.generateFrameKey(frame, channel, slice);
      try {
         JSONObject imgData = metadata.getJSONObject(key);
         String txt = new String();
         txt = imgData.getInt(ImagePropertyKeys.ELAPSED_TIME_MS) + "ms, " +
               imgData.getString(ImagePropertyKeys.CHANNEL) + ", " +
               fmt2dec.format(imgData.getDouble(ImagePropertyKeys.Z_UM)) + "um";
         return txt;
      } catch (JSONException e) {
         return "Metadata not available";
      }      
   }
   
   public static String getChannelName(JSONObject metadata, int channel) {
      String key = ImageKey.generateFrameKey(0, channel, 0);
      try {
         JSONObject imgData = metadata.getJSONObject(key);
         return imgData.getString(ImagePropertyKeys.CHANNEL);
      } catch (JSONException e) {
         return Integer.toString(channel);
      }            
   }
}
