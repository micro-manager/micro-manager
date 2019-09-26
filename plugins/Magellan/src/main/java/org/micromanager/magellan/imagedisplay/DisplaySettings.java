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
package org.micromanager.magellan.imagedisplay;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.json.JSONException;
import org.json.JSONObject;
import org.micromanager.magellan.channels.MagellanChannelSpec;
import org.micromanager.magellan.misc.Log;
import org.micromanager.magellan.misc.MD;

public class DisplaySettings {

   private final JSONObject json_;

   //for reading from disk
   public DisplaySettings(JSONObject json) {
      json_ = json;
   }

   public DisplaySettings(MagellanChannelSpec channels, JSONObject summaryMD) {
      int bitDepth = 16;
      if (summaryMD.has("BitDepth")) {
         bitDepth = MD.getBitDepth(summaryMD);
      } else if (summaryMD.has("PixelType")) {
         if (MD.isGRAY8(summaryMD) || MD.isRGB32(summaryMD)) {
            bitDepth = 8;
         }
      }

      json_ = new JSONObject();
      List<String> channelNames = new ArrayList<String>();
      if (channels == null || channels.getNumChannels() == 0) {

         channelNames.add("");

      } else {
         channelNames.addAll(channels.getChannelNames());
      }
      for (String cName : channelNames) {
         try {
            JSONObject channelDisp = new JSONObject();
            channelDisp.put("Color", cName.equals("") ? Color.white : channels.getChannelSetting(cName).color_.getRGB());
            channelDisp.put("BitDepth", bitDepth);
//         channelObject.put("Name", name);
            channelDisp.put("Gamma", 1.0);
         channelDisp.put("Min", 0);
         channelDisp.put("Max", (int) Math.pow(2, bitDepth) -1);
         channelDisp.put("Active", true);
         

            json_.put(cName, channelDisp);
         } catch (JSONException ex) {
            //this wont happen
            throw new RuntimeException(ex);
         }
      }
   }

   @Override
   public String toString() {
      return json_.toString();
   }

   public Color getColor(String channelName) {
      synchronized (this) {

         try {
            return new Color(json_.getJSONObject(channelName).getInt("Color"));
         } catch (JSONException ex) {
            Log.log("Color missing from display settings", false);
         }
         return Color.white;
      }
   }

   public int getBitDepth(String channelName) {
      synchronized (this) {
         try {
            return json_.getJSONObject(channelName).optInt("BitDepth", 16);
         } catch (JSONException ex) {
            Log.log("bitdepth missing from display settings", false);
         }
         return 16;
      }
   }

   public double getGamma(String channelName) {
      synchronized (this) {
         try {
            return json_.getJSONObject(channelName).optDouble("Gamma", 1.0);
         } catch (JSONException ex) {
            Log.log("gamma missing from display settings", false);
         }
         return 1.0;
      }
   }

   public int getContrastMin(String channelName) {
      synchronized (this) {
         try {
            return json_.getJSONObject(channelName).optInt("Min", 0);
         } catch (JSONException ex) {
            Log.log("min missing from display settings", false);
         }
         return 0;
      }
   }

   public int getContrastMax(String channelName) {
      synchronized (this) {
         try {
            return json_.getJSONObject(channelName).getInt("Max");
         } catch (JSONException ex) {
            Log.log("max missing from display settings", false);
         }
         return (int) (Math.pow(2, this.getBitDepth(channelName)) - 1);
      }
   }

   public boolean isActive(String channelName) {
      synchronized (this) {
         try {
            return json_.getJSONObject(channelName).getBoolean("Active");
         } catch (JSONException ex) {
            Log.log("Channel active missing in settings");
            return true;
         }
      }
   }

   public void setActive(String channelName, boolean selected) {
      synchronized (this) {
         try {
            json_.getJSONObject(channelName).put("Active", selected);
         } catch (JSONException ex) {
            throw new RuntimeException("Couldnt set display setting");
         }
      }
   }

   public void setColor(String channelName, Color color) {
      synchronized (this) {
         try {
            json_.getJSONObject(channelName).put("Color", color.getRGB());
         } catch (JSONException ex) {
            throw new RuntimeException("Couldnt set display setting");
         }
      }
   }

   public void setGamma(String channelName, double gamma) {
      synchronized (this) {
         try {
            json_.getJSONObject(channelName).put("Gamma", gamma);
         } catch (JSONException ex) {
            throw new RuntimeException("Couldnt set display setting");
         }
      }
   }

   public void setContrastMin(String channelName, int contrastMin) {
      synchronized (this) {
         try {
            json_.getJSONObject(channelName).put("Min", contrastMin);
         } catch (JSONException ex) {
            throw new RuntimeException("Couldnt set display setting");
         }
      }
   }

   public void setContrastMax(String channelName, int contrastMax) {
      synchronized (this) {
         try {
            json_.getJSONObject(channelName).put("Max", contrastMax);
         } catch (JSONException ex) {
            throw new RuntimeException("Couldnt set display setting");
         }
      }
   }

}
