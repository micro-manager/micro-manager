///////////////////////////////////////////////////////////////////////////////
//FILE:          SequenceSettings.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//               Definition of the Acquisition Protocol to be executed
//               by the acquisition engine
//
// AUTHOR:       Arthur Edelstein, Nenad Amodaj
//
// COPYRIGHT:    University of California, San Francisco, 2013
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

package org.micromanager.acquisition;

import java.util.ArrayList;

import org.micromanager.utils.ChannelSpec;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class SequenceSettings {
   
   // version ID for the sequence settings
   public static final double Version = 1.0;

   // acquisition protocol
   public int numFrames = 1;   
   public double intervalMs = 0.0;
   public ArrayList<Double> customIntervalsMs = null;
   public ArrayList<ChannelSpec> channels = new ArrayList<ChannelSpec>();
   public ArrayList<Double> slices = new ArrayList<Double>();
   public boolean relativeZSlice = false;
   public boolean slicesFirst = false;
   public boolean timeFirst = false;
   public boolean keepShutterOpenSlices = false;
   public boolean keepShutterOpenChannels = false;
   public boolean useAutofocus = false;
   public int skipAutofocusCount = 0;
   public boolean save = false;
   public String root = null;
   public String prefix = null;
   public double zReference = 0;
   public String comment = "";
   public String channelGroup = "";
   public boolean usePositionList = false;
      
   public static String toJSONStream(SequenceSettings settings) {
      Gson gson = new GsonBuilder().setPrettyPrinting().create();
      return gson.toJson(settings);      
   }
   
   public static SequenceSettings fromJSONStream(String stream) {
      Gson gson = new Gson();
      return gson.fromJson(stream, SequenceSettings.class);
   }
   
   // test serialization
   public synchronized static void main(String[] args) {
      
      // encode
      SequenceSettings s = new SequenceSettings();
      String channelGroup = "Channel";

      s.numFrames = 20;

      s.slices = new ArrayList<Double>();
      s.slices.add(-1.0);
      s.slices.add(0.0);
      s.slices.add(1.0);     
      s.relativeZSlice = true;

      s.channels = new ArrayList<ChannelSpec>();
      ChannelSpec ch1 = new ChannelSpec();
      ch1.config = "DAPI";
      ch1.exposure = 5.0;
      s.channels.add(ch1);
      ChannelSpec ch2 = new ChannelSpec();
      ch2.config = "FITC";
      ch2.exposure = 15.0;
      s.channels.add(ch2);

      s.prefix = "ACQ-TEST-B";
      s.root = "C:/AcquisitionData";
      s.channelGroup = channelGroup;

      String stream = SequenceSettings.toJSONStream(s);
      System.out.println("Encoded:\n" + stream);
      
      // decode
      SequenceSettings resultSs = SequenceSettings.fromJSONStream(stream);
      System.out.println("Decoded:\n" + SequenceSettings.toJSONStream(resultSs));
   }
   
}
