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
import org.json.JSONException;
import org.json.JSONObject;
import org.micromanager.magellan.channels.MagellanChannelSpec;
import org.micromanager.magellan.misc.MD;

public class DisplaySettings {

   public static JSONObject getDefaultDisplaySettings(MagellanChannelSpec channels, JSONObject summaryMD) {
      int bitDepth = 16;
      if (summaryMD.has("BitDepth")) {
         bitDepth = MD.getBitDepth(summaryMD);
      } else if (summaryMD.has("PixelType")) {
         if (MD.isGRAY8(summaryMD) || MD.isRGB32(summaryMD)) {
            bitDepth = 8;
         }
      }

      JSONObject dispSettings = new JSONObject();
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
//         channelObject.put("Gamma", 1.0);
//         channelObject.put("Min", min);
//         channelObject.put("Max", max);

            dispSettings.put(cName, channelDisp);
         } catch (JSONException ex) {
            //this wont happen
            throw new RuntimeException(ex);
         }
      }
      return dispSettings;
   }

}
