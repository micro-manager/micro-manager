///////////////////////////////////////////////////////////////////////////////
//FILE:          ChannelSpec.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nenad Amodaj, nenad@amodaj.com, November 10, 2005
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
// CVS:          $Id: ChannelSpec.java 12081 2013-11-06 21:27:25Z nico $
//
package channels;
import java.awt.Color; 

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Channel acquisition protocol. 
 */
public class ChannelSettings {
   public static final String DEFAULT_CHANNEL_GROUP = "Channel";
   public static final double Version = 1.0;

   public Boolean doZStack = true;
   public String config = ""; // Configuration setting name
   public double exposure = 10.0; // ms
   public double zOffset = 0.0; // um
   public Color color = Color.gray;
   public int skipFactorFrame = 0;
   public boolean useChannel = true;
   public String camera = "";
   
   public ChannelSettings(){
      color = Color.WHITE;
   }
   
   /**
    * Serialize to JSON encoded string
    */
   public static String toJSONStream(ChannelSettings cs) {
      Gson gson = new GsonBuilder().setPrettyPrinting().create();
      return gson.toJson(cs);
   }
   
   /**
    * De-serialize from JSON encoded string
    */
   public static ChannelSettings fromJSONStream(String stream) {
      Gson gson = new Gson();
      ChannelSettings cs = gson.fromJson(stream, ChannelSettings.class);
      return cs;
   }
   
//   // test serialization
//   public synchronized static void main(String[] args) {
//      
//      // encode
//      ChannelSettings cs = new ChannelSettings();
//      String stream = ChannelSettings.toJSONStream(cs);
//      System.out.println("Encoded:\n" + stream);
//      
//      // decode
//      ChannelSettings resultCs = ChannelSettings.fromJSONStream(stream);
//      System.out.println("Decoded:\n" + ChannelSettings.toJSONStream(resultCs));
//   }
//   
}

