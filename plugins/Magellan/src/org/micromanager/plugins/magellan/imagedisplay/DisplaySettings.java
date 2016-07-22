///////////////////////////////////////////////////////////////////////////////
// AUTHOR:       Henry Pinkard, henry.pinkard@gmail.com
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
//
package org.micromanager.plugins.magellan.imagedisplay;

import java.awt.Color;
import org.micromanager.plugins.magellan.json.JSONArray;
import org.micromanager.plugins.magellan.json.JSONObject;
import org.micromanager.plugins.magellan.misc.MD;


public class DisplaySettings {

   public static JSONObject getDisplaySettingsFromSummary(JSONObject summaryMetadata) throws Exception {
      JSONObject displaySettings = new JSONObject();

      //create empty display and comments object  
      JSONArray channels = new JSONArray();            
      JSONObject comments = new JSONObject();
      displaySettings.put("Channels", channels);  
      String summary = "";
      comments.put("Summary", summary);
      displaySettings.put("Comments", comments);
      
      int numDisplayChannels;
      JSONArray chColors = null, chMaxes = null, chMins = null, chNames = null;
      if (summaryMetadata.has("ChNames")) {
         chNames = MD.getJSONArrayMember(summaryMetadata, "ChNames");
         // HACK: derive the number of channels from the number of channel
         // names. 
         numDisplayChannels = chNames.length();
      } else {
         numDisplayChannels = MD.getNumChannels(summaryMetadata);
         if (MD.isRGB(summaryMetadata)) {
            numDisplayChannels *= 3;
         }
      }
      if (summaryMetadata.has("ChColors")) {
          chColors = MD.getJSONArrayMember(summaryMetadata, "ChColors");
      } 
      if (summaryMetadata.has("ChContrastMin")) {
         chMins = MD.getJSONArrayMember(summaryMetadata, "ChContrastMin");
      } 
      if ( summaryMetadata.has("ChContrastMax")) {
         chMaxes = MD.getJSONArrayMember(summaryMetadata, "ChContrastMax");
      }      
      
      for (int k = 0; k < numDisplayChannels; ++k) {
         String name = chNames != null ? chNames.getString(k) :"channel " + k;
         int color = (chColors != null && k < chColors.length()) ? 
                 chColors.getInt(k) : Color.white.getRGB();
         int min = (chMins != null && chMins.length() > k) ? chMins.getInt(k) : 0;
         int bitDepth = 16;
         if (summaryMetadata.has("BitDepth")) {
            bitDepth = MD.getBitDepth(summaryMetadata);
         } else if (summaryMetadata.has("PixelType")) {
            if (MD.isGRAY8(summaryMetadata) ) { 
//                    || MD.isRGB32(summaryMetadata)) {
               bitDepth = 8;
            }
         }
         int max = (chMaxes != null && chMaxes.length() > k) ? chMaxes.getInt(k) : (int) (Math.pow(2, bitDepth) - 1);
         JSONObject channelObject = new JSONObject();
         channelObject.put("Color", color);
         channelObject.put("Name", name);
         channelObject.put("Gamma", 1.0);
         channelObject.put("Min", min);
         channelObject.put("Max", max);
         channels.put(channelObject);
      }
      return displaySettings;
   }
}

