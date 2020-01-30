///////////////////////////////////////////////////////////////////////////////
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
// CVS:          $Id$
//
package org.micromanager.acquisition;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.awt.Color;

/**
 * A ChannelSpec is a description of how a specific channel will be used in an
 * MDA (multi-dimensional acquisition). It contains fields corresponding to
 * each of the columns in the Channels section of the MDA dialog. ChannelSpecs
 * are used by the SequenceSettings object to set up channels for acquisitions.
 */
@SuppressWarnings("unused")
public class ChannelSpec {

   /** Whether this channel should be imaged in each Z slice of the stack */
   public Boolean doZStack = true;
   /** Name of the channel */
   public String config = "";
   /** Exposure time, in milliseconds */
   public double exposure = 10.0;
   /** Z-offset, in microns */
   public double zOffset = 0.0;
   /** Color to use when displaying this channel */
   public Color color = Color.gray;
   /** Number of frames to skip between each time this channel is imaged. */
   public int skipFactorFrame = 0;
   /** Whether the channel is enabled for imaging at all. */
   public boolean useChannel = true;
   /** Name of the camera to use. */
   public String camera = "";

   public ChannelSpec(){
      color = Color.WHITE;
   }

   /**
    * Serialize to JSON encoded string
    */
   public static String toJSONStream(ChannelSpec cs) {
      Gson gson = new GsonBuilder().setPrettyPrinting().create();
      return gson.toJson(cs);
   }

   /**
    * De-serialize from JSON encoded string
    */
   public static ChannelSpec fromJSONStream(String stream) {
      Gson gson = new Gson();
      ChannelSpec cs = gson.fromJson(stream, ChannelSpec.class);
      return cs;
   }
}

