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
package org.micromanager.multiresviewer;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.json.JSONException;
import org.json.JSONObject;

public class DisplaySettings {

   public final static int NUM_DISPLAY_HIST_BINS = 256;

   private static final String ALL_CHANNELS_SETTINGS_KEY = "All channel settings";
   private static final String AUTOSCALE = "Autoscale all channels";
   private static final String LOG_HIST = "Log histogram";
   private static final String COMPOSITE = "Display all channels";
   private static final String SYNC_CHANNELS = "Sync all channels";
   private static final String IGNORE_OUTLIERS = "Ignore outliers";
   private static final String IGNORE_PERCENTAGE = "gnore outlier percentage";

   private final JSONObject json_;

   //for reading from disk
   public DisplaySettings(JSONObject json) {
      json_ = json;
   }
   
   public JSONObject toJSON() {
      try {
         //make copy
         return new JSONObject(json_.toString());
      } catch (JSONException ex) {
         throw new RuntimeException();
      }
   }

   public DisplaySettings(JSONObject displaySettingsJSON, JSONObject summaryMD) {
      int bitDepth = 16;
      if (summaryMD.has("BitDepth")) {
         
         bitDepth = DisplayMetadata.getBitDepth(summaryMD);
      } else if (summaryMD.has("PixelType")) {
         if (DisplayMetadata.isGRAY8(summaryMD) || DisplayMetadata.isRGB32(summaryMD)) {
            bitDepth = 8;
         }
      }

      json_ = displaySettingsJSON;
   }

   @Override
   public String toString() {
      return json_.toString();
   }

   public Color getColor(String channelName) {
      synchronized (this) {

         try {
            return new Color(json_.getJSONObject(channelName).getInt("Color"));
         } catch (Exception ex) {
            System.err.println("Color missing from display settings");
         }
         return Color.white;
      }
   }

   public int getBitDepth(String channelName) {
      synchronized (this) {
         try {
            return json_.getJSONObject(channelName).optInt("BitDepth", 16);
         } catch (Exception ex) {
            System.err.println("bitdepth missing from display settings");
         }
         return 16;
      }
   }

   public double getContrastGamma(String channelName) {
      synchronized (this) {
         try {
            return json_.getJSONObject(channelName).optDouble("Gamma", 1.0);
         } catch (Exception ex) {
            System.err.println("gamma missing from display settings");
         }
         return 1.0;
      }
   }

   public int getContrastMin(String channelName) {
      synchronized (this) {
         try {
            return json_.getJSONObject(channelName).optInt("Min", 0);
         } catch (Exception ex) {
            System.err.println("min missing from display settings");
         }
         return 0;
      }
   }

   public int getContrastMax(String channelName) {
      synchronized (this) {
         try {
            return json_.getJSONObject(channelName).getInt("Max");
         } catch (Exception ex) {
            System.err.println("max missing from display settings");
         }
         return (int) (Math.pow(2, this.getBitDepth(channelName)) - 1);
      }
   }

   public boolean isActive(String channelName) {
      synchronized (this) {
         try {
            return json_.getJSONObject(channelName).getBoolean("Active");
         } catch (Exception ex) {
            System.err.println("Channel active missing in settings");
            return true;
         }
      }
   }

   public void setActive(String channelName, boolean selected) {
      synchronized (this) {
         try {
            json_.getJSONObject(channelName).put("Active", selected);
         } catch (Exception ex) {
            System.err.println("Couldnt set display setting");
         }
      }
   }

   public void setColor(String channelName, Color color) {
      synchronized (this) {
         try {
            json_.getJSONObject(channelName).put("Color", color.getRGB());
         } catch (Exception ex) {
            System.err.println("Couldnt set display setting");
         }
      }
   }

   public void setGamma(String channelName, double gamma) {
      synchronized (this) {
         try {
            if (isSyncChannels()) {
               json_.keys().forEachRemaining((String t) -> {
                  if (!t.equals(ALL_CHANNELS_SETTINGS_KEY)) {
                     try {
                        json_.getJSONObject(t).put("Gamma", gamma);
                     } catch (JSONException ex) {
                        System.err.println("Couldnt set display setting");
                     }
                  }
               });
            }
            json_.getJSONObject(channelName).put("Gamma", gamma);
         } catch (Exception ex) {
            System.err.println("Couldnt set display setting");
         }
      }
   }

   public void setContrastMin(String channelName, int contrastMin) {
      synchronized (this) {
         try {
            if (isSyncChannels()) {
               json_.keys().forEachRemaining((String t) -> {
                  if (!t.equals(ALL_CHANNELS_SETTINGS_KEY)) {
                     try {
                        json_.getJSONObject(t).put("Min", contrastMin);
                        json_.getJSONObject(t).put("Max", Math.max(contrastMin, getContrastMax(t)));
                     } catch (JSONException ex) {
                        System.err.println("Couldnt set display setting");
                     }
                  }
               });
            }
            json_.getJSONObject(channelName).put("Min", contrastMin);
            json_.getJSONObject(channelName).put("Max", Math.max(contrastMin, getContrastMax(channelName)));
         } catch (Exception ex) {
            System.err.println("Couldnt set display setting");
         }
      }
   }

   public void setContrastMax(String channelName, int contrastMax) {
      synchronized (this) {
         try {
            if (isSyncChannels()) {
               json_.keys().forEachRemaining((String t) -> {
                  if (!t.equals(ALL_CHANNELS_SETTINGS_KEY)) {
                     try {
                        json_.getJSONObject(t).put("Max", contrastMax);
                        json_.getJSONObject(t).put("Min", Math.min(contrastMax, getContrastMin(t)));

                     } catch (JSONException ex) {
                        System.err.println("Couldnt set display setting");
                     }
                  }
               });
            }
            json_.getJSONObject(channelName).put("Max", contrastMax);
            json_.getJSONObject(channelName).put("Min", Math.min(contrastMax, getContrastMin(channelName)));

         } catch (JSONException ex) {
            System.err.println("Couldnt set display setting");
         }
      }
   }

   boolean isSyncChannels() {
      synchronized (this) {
         try {
            return json_.getJSONObject(ALL_CHANNELS_SETTINGS_KEY).optBoolean(SYNC_CHANNELS, false);
         } catch (JSONException ex) {
            System.err.println(ex);
            return true;
         }
      }
   }

   boolean isLogHistogram() {
      synchronized (this) {
         try {
            return json_.getJSONObject(ALL_CHANNELS_SETTINGS_KEY).optBoolean(LOG_HIST, true);
         } catch (JSONException ex) {
            System.err.println(ex);
            return true;
         }
      }
   }

   boolean isCompositeMode() {
      synchronized (this) {
         try {
            return json_.getJSONObject(ALL_CHANNELS_SETTINGS_KEY).optBoolean(COMPOSITE, true);
         } catch (JSONException ex) {
            System.err.println(ex);
            return true;
         }
      }
   }

   double percentToIgnore() {
      synchronized (this) {
         try {
            return json_.getJSONObject(ALL_CHANNELS_SETTINGS_KEY).optDouble(IGNORE_PERCENTAGE, 0.1);
         } catch (JSONException ex) {
            System.err.println(ex);
            return 0;
         }
      }
   }

   boolean ignoreFractionOn() {
      synchronized (this) {
         try {
            return json_.getJSONObject(ALL_CHANNELS_SETTINGS_KEY).optBoolean(IGNORE_OUTLIERS, false);
         } catch (JSONException ex) {
            System.err.println(ex);
            return false;
         }
      }
   }

   boolean getAutoscale() {
      synchronized (this) {
         try {
            return json_.getJSONObject(ALL_CHANNELS_SETTINGS_KEY).optBoolean(AUTOSCALE, true);
         } catch (JSONException ex) {
            System.err.println(ex);
            return true;
         }
      }
   }

   void setChannelContrastFromFirst() {
      try {
         String firstChannel = json_.keys().next();
         JSONObject first = json_.getJSONObject(firstChannel);
         int max = first.getInt("Max");
         int min = first.getInt("Min");
         double gamma = first.getInt("Gamma");

         json_.keys().forEachRemaining((String t) -> {
            if (!t.equals(ALL_CHANNELS_SETTINGS_KEY)) {
               try {
                  json_.getJSONObject(t).put("Min", min);
                  json_.getJSONObject(t).put("Max", max);
                  json_.getJSONObject(t).put("Gamma", gamma);
               } catch (JSONException ex) {
                  System.err.println("Couldnt set display setting");
               }
            }
         });
      } catch (JSONException ex) {
         System.err.println(ex);
      }
   }

   void setIgnoreOutliersPercentage(double percent) {
      synchronized (this) {
         try {
            json_.getJSONObject(ALL_CHANNELS_SETTINGS_KEY).put(IGNORE_PERCENTAGE, percent);
         } catch (JSONException ex) {
            System.err.println("Couldnt set autoscale");
         }
      }
   }

   void setIgnoreOutliers(boolean b) {
      synchronized (this) {
         try {
            json_.getJSONObject(ALL_CHANNELS_SETTINGS_KEY).put(IGNORE_OUTLIERS, b);
         } catch (JSONException ex) {
            System.err.println("Couldnt set autoscale");
         }
      }
   }

   void setLogHist(boolean b) {
      synchronized (this) {
         try {
            json_.getJSONObject(ALL_CHANNELS_SETTINGS_KEY).put(LOG_HIST, b);
         } catch (JSONException ex) {
            System.err.println("Couldnt set autoscale");
         }
      }
   }

   void setAutoscale(boolean b) {
      synchronized (this) {
         try {
            json_.getJSONObject(ALL_CHANNELS_SETTINGS_KEY).put(AUTOSCALE, b);
         } catch (JSONException ex) {
            System.err.println("Couldnt set autoscale");
         }
      }
   }

   void setSyncChannels(boolean b) {
      synchronized (this) {
         try {
            json_.getJSONObject(ALL_CHANNELS_SETTINGS_KEY).put(SYNC_CHANNELS, b);
         } catch (JSONException ex) {
            System.err.println("Couldnt set autoscale");
         }
      }
   }

   void setCompositeMode(boolean b) {
      synchronized (this) {
         try {
            json_.getJSONObject(ALL_CHANNELS_SETTINGS_KEY).put(COMPOSITE, b);
         } catch (JSONException ex) {
            System.err.println("Couldnt set autoscale");
         }
      }
   }

}
