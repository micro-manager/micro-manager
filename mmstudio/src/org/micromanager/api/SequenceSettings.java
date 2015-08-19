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

package org.micromanager.api;

import java.util.ArrayList;

import org.micromanager.utils.ChannelSpec;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class SequenceSettings {
   
   // version ID for the sequence settings
   public static final double Version = 1.0;

   // acquisition protocol
   public int numFrames = 1;                                                 // number of frames
   public double intervalMs = 0.0;                                           // frame interval
   public ArrayList<Double> customIntervalsMs = null;                        // sequence of custom intervals or null
   public ArrayList<ChannelSpec> channels = new ArrayList<ChannelSpec>();    // an array of ChannelSpec settings (one for each channel)
   public ArrayList<Double> slices = new ArrayList<Double>();                // slice Z coordinates
   public boolean relativeZSlice = false;                                    // are Z coordinates relative or absolute
   public boolean slicesFirst = false;                                       // slice coordinate changes first
   public boolean timeFirst = false;                                         // frame coordinate changes first
   public boolean keepShutterOpenSlices = false;                             // do we keep shutter open during slice changes
   public boolean keepShutterOpenChannels = false;                           // do we keep shutter open channel changes
   public boolean useAutofocus = false;                                      // are we going to run autofocus before acquiring each position/frame
   public int skipAutofocusCount = 0;                                        // how many af opportunities to skip                          
   public boolean save = false;                                              // save to disk?
   public String root = null;                                                // root directory name
   public String prefix = null;                                              // acquisition name
   public double zReference = 0.0;                                           // referent z position for relative moves                                            
   public String comment = "";                                               // comment text
   public String channelGroup = "";                                          // which group is used to define fluorescence channels
   public boolean usePositionList = false;                                   // true if we want to have multiple positions
   public int cameraTimeout = 20000; // Minimum camera timeout, in ms, for sequence acquisitions (actual timeout depends on exposure time and other factors)
      
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
