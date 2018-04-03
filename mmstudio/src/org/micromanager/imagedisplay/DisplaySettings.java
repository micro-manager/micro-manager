package org.micromanager.imagedisplay;

import java.awt.Color;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import org.micromanager.utils.MDUtils;

public class DisplaySettings {

   public static JSONObject getDisplaySettingsFromSummary(JSONObject summaryMetadata) throws Exception {
      JSONObject displaySettings = new JSONObject();

      //create empty display and comments object  
      JSONArray channels = new JSONArray();            
      JSONObject comments = new JSONObject();
      displaySettings.put("Channels", channels);  
      String summary = "";
      try {
         summary = summaryMetadata.getString("Comment");
      } catch (JSONException ex) {}
      comments.put("Summary", summary);
      displaySettings.put("Comments", comments);
      
      int numDisplayChannels;
      JSONArray chColors = null, chMaxes = null, chMins = null, chNames = null;
      if (summaryMetadata.has("ChNames")) {
         chNames = MDUtils.getJSONArrayMember(summaryMetadata, "ChNames");
         // HACK: derive the number of channels from the number of channel
         // names. 
         numDisplayChannels = chNames.length();
      } else {
         numDisplayChannels = MDUtils.getNumChannels(summaryMetadata);
         if (MDUtils.isRGB(summaryMetadata)) {
            numDisplayChannels *= 3;
         }
      }
      if (summaryMetadata.has("ChColors")) {
          chColors = MDUtils.getJSONArrayMember(summaryMetadata, "ChColors");
      } 
      if (summaryMetadata.has("ChContrastMin")) {
         chMins = MDUtils.getJSONArrayMember(summaryMetadata, "ChContrastMin");
      } 
      if ( summaryMetadata.has("ChContrastMax")) {
         chMaxes = MDUtils.getJSONArrayMember(summaryMetadata, "ChContrastMax");
      }      
      
      for (int k = 0; k < numDisplayChannels; ++k) {
         String name = chNames != null ? chNames.getString(k) :"channel " + k;
         int color = (chColors != null && k < chColors.length()) ? 
                 chColors.getInt(k) : Color.white.getRGB();
         int min = (chMins != null && chMins.length() > k) ? chMins.getInt(k) : 0;
         int bitDepth = 16;
         if (summaryMetadata.has("BitDepth")) {
            bitDepth = MDUtils.getBitDepth(summaryMetadata);
         } else if (summaryMetadata.has("PixelType")) {
            if (MDUtils.isGRAY8(summaryMetadata) || MDUtils.isRGB32(summaryMetadata)) {
               bitDepth = 8;
            }
            else if (MDUtils.isGRAY32(summaryMetadata)) {
               bitDepth = 32;
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
