///////////////////////////////////////////////////////////////////////////////
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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.ArrayList;


/**
 * SequenceSettings objects contain the parameters describing how to run a
 * single acquisition. Various methods of the AcquisitionManager will consume
 * or generate SequenceSettings, and you can create your own to configure your
 * custom acquisitions.
 */
public class SequenceSettings {
   // version ID for the sequence settings
   public static final double Version = 1.3;

   public static class Builder {
      private int numFrames = 1;
      private double intervalMs = 0.0;
      private int displayTimeUnit = 1;
      private boolean useCustomIntervals = false;
      private ArrayList<Double> customIntervalsMs;
      private ArrayList<ChannelSpec> channels = new ArrayList<>();
      private ArrayList<Double> slices = new ArrayList<>();
      private boolean relativeZSlice = false;
      private boolean slicesFirst = false;
      private boolean timeFirst = false;
      private boolean keepShutterOpenSlices = false;
      private boolean keepShutterOpenChannels = false;
      private boolean useAutofocus = false;
      private int skipAutofocusCount = 0;
      private boolean save = false;
      private String root = null;
      private String prefix = null;
      private double zReference = 0.0;
      private String comment = "";
      private String channelGroup = "";
      private boolean usePositionList = false;
      private int cameraTimeout = 20000;
      private boolean shouldDisplayImages = true;
      private boolean useSlices = false;
      private boolean useFrames = false;
      private boolean useChannels = false;
      private double sliceZStepUm = 1.0;
      private double sliceZBottomUm = 0.0;
      private double sliceZTopUm = 0.0;
      private int acqOrderMode; // defined in org.micromanager.internal.utils.AcqOrderMode

      public Builder numFrames(int nFrames) { numFrames = nFrames; return this;}
      public Builder intervalMs(double d) { intervalMs = d; return this;}
      public Builder displayTimeUnit(int d) { displayTimeUnit = d; return this; }
      public Builder useCustomIntervals (boolean use) {
         useCustomIntervals = use; return this; }
      public Builder customIntervalsMs(ArrayList<Double> c) {
         customIntervalsMs = c; return this; }
      public Builder channels(ArrayList<ChannelSpec> c) {channels = c; return this;}
      public Builder slices (ArrayList<Double> s) { slices = s; return this;}
      public Builder relativeZSlice(boolean r) {relativeZSlice = r; return this;}
      public Builder slicesFirst(boolean s) {slicesFirst = s; return this;}
      public Builder timeFirst(boolean t) {timeFirst = t; return this;}
      public Builder keepShutterOpenSlices(boolean k) {keepShutterOpenSlices = k; return this;}
      public Builder keepShutterOpenChannels (boolean k) {keepShutterOpenChannels = k; return this;}
      public Builder useAutofocus(boolean u) {useAutofocus = u; return this;}
      public Builder skipAutofocusCount(int s) {skipAutofocusCount = s; return this;}
      public Builder save(boolean s) {save = s; return this;}
      public Builder root(String r) {root = r; return this;}
      public Builder prefix (String p) {prefix = p; return this;}
      public Builder zReference(double z) {zReference = z; return this;}
      public Builder comment(String c) {comment = c; return this;}
      public Builder channelGroup (String c) {channelGroup = c; return this;}
      public Builder usePositionList (boolean u) {usePositionList = u; return this;}
      public Builder cameraTimeout(int ct) {cameraTimeout = ct; return this;}
      public Builder shouldDisplayImages(boolean s) {shouldDisplayImages = s; return this;}

      public Builder useSlices(boolean u) { useSlices = u; return this; }
      public Builder useFrames(boolean u) {useFrames = u; return this;}
      public Builder useChannels(boolean u) {useChannels = u; return this;}
      public Builder sliceZStepUm(double s) {sliceZStepUm = s; return this;}
      public Builder sliceZBottomUm (double s) {sliceZBottomUm = s; return this;}
      public Builder sliceZTopUm(double s) {sliceZTopUm = s; return this;}
      public Builder acqOrderMode(int a) {acqOrderMode = a; return this;}

      public Builder() {}

      public Builder(SequenceSettings s) {
         numFrames =  s.numFrames;
         intervalMs = s.intervalMs;
         displayTimeUnit = s.displayTimeUnit;
         useCustomIntervals = s.useCustomIntervals;
         customIntervalsMs = s.customIntervalsMs;
         channels = s.channels;
         slices = s.slices;
         relativeZSlice = s.relativeZSlice;
         slicesFirst = s.slicesFirst;
         timeFirst = s.timeFirst;
         keepShutterOpenSlices = s.keepShutterOpenSlices;
         keepShutterOpenChannels = s.keepShutterOpenChannels;
         useAutofocus = s.useAutofocus;
         skipAutofocusCount = s.skipAutofocusCount;
         save = s.save;
         root = s.root;
         prefix = s.prefix;
         zReference = s.zReference;
         comment = s.comment;
         channelGroup = s.channelGroup;
         usePositionList = s.usePositionList;
         cameraTimeout = s.cameraTimeout;
         shouldDisplayImages = s.shouldDisplayImages;
         useSlices = s.useSlices;
         useFrames = s.useFrames;
         useChannels = s.useChannels;
         sliceZStepUm = s.sliceZStepUm;
         sliceZBottomUm = s.sliceZBottomUm;
         sliceZTopUm = s.sliceZTopUm;
         acqOrderMode = s.acqOrderMode;
      }

      public SequenceSettings build() {
         SequenceSettings s = new SequenceSettings();
         s.numFrames =  numFrames;
         s.intervalMs = intervalMs;
         s.displayTimeUnit = displayTimeUnit;
         s.useCustomIntervals = useCustomIntervals;
         s.customIntervalsMs = customIntervalsMs;
         s.channels = channels;
         s.slices = slices;
         s.relativeZSlice = relativeZSlice;
         s.slicesFirst = slicesFirst;
         s.timeFirst = timeFirst;
         s.keepShutterOpenSlices = keepShutterOpenSlices;
         s.keepShutterOpenChannels = keepShutterOpenChannels;
         s.useAutofocus = useAutofocus;
         s.skipAutofocusCount = skipAutofocusCount;
         s.save = save;
         s.root = root;
         s.prefix = prefix;
         s.zReference = zReference;
         s.comment = comment;
         s.channelGroup = channelGroup;
         s.usePositionList = usePositionList;
         s.cameraTimeout = cameraTimeout;
         s.shouldDisplayImages = shouldDisplayImages;
         s.useSlices = useSlices;
         s.useFrames = useFrames;
         s.useChannels = useChannels;
         s.sliceZStepUm = sliceZStepUm;
         s.sliceZBottomUm = sliceZBottomUm;
         s.sliceZTopUm = sliceZTopUm;
         s.acqOrderMode = acqOrderMode;

         return s;
      }

   }

   // acquisition protocol
   /**
    * number of frames
    * @deprecated use Builder and numFrames() instead
    */
   @Deprecated
   public int numFrames = 1;
   /**
    * frame interval
    * @deprecated use Builder and intervalMs() instead
    */
   @Deprecated
   public double intervalMs = 0.0;

   private int displayTimeUnit;
   /**
    * Whether or not to use custom time intervals. Do not set this to true
    * if customIntervalsMs is null!
    * @deprecated use Builder and useCustomIntervals() instead
    */
   @Deprecated
   public boolean useCustomIntervals;
   /**
    * sequence of custom intervals or null
    * @deprecated use Builder and customIntervalsMs() instead
    */
   @Deprecated
   public ArrayList<Double> customIntervalsMs = null;
   /**
    * an array of ChannelSpec settings (one for each channel)
    * @deprecated use Builder and channels() instead
    */
   @Deprecated
   public ArrayList<ChannelSpec> channels = new ArrayList<>();
   /**
    * slice Z coordinates
    * @deprecated use Builder and slices() instead
    */
   @Deprecated
   public ArrayList<Double> slices = new ArrayList<>();
   /**
    * are Z coordinates relative or absolute
    * @deprecated use Builder and relativeZSlice() instead
    */
   @Deprecated
   public boolean relativeZSlice = false;
   /**
    * slice coordinate changes first
    * @deprecated use Builder and slicesFirst() instead
    */
   @Deprecated
   public boolean slicesFirst = false;
   /**
    * frame coordinate changes first
    * @deprecated use Builder and timeFirst() instead
    */
   @Deprecated
   public boolean timeFirst = false;
   /**
    * do we keep shutter open during slice changes
    * @deprecated use Builder and keepShutterOpenSlices() instead
    */
   @Deprecated
   public boolean keepShutterOpenSlices = false;
   /**
    * do we keep shutter open channel changes
    * @deprecated use Builder and keepShutterOpenChannels() instead
    */
   @Deprecated
   public boolean keepShutterOpenChannels = false;
   /**
    * are we going to run autofocus before acquiring each position/frame
    * @deprecated use Builder and useAutofocus() instead
    */
   @Deprecated
   public boolean useAutofocus = false;
   /**
    * how many autofocus opportunities to skip
    * @deprecated use Builder and skipAutofocusCount() instead
    */
   @Deprecated
   public int skipAutofocusCount = 0;
   /**
    * save to disk?
    * @deprecated use Builder and save() instead
    */
   @Deprecated
   public boolean save = false;
   /**
    * root directory name
    * @deprecated use Builder and root() instead
    */
   @Deprecated
   public String root = null;
   /**
    * acquisition name
    * @deprecated use Builder and prefix() instead
    */
   @Deprecated
   public String prefix = null;
   /**
    * referent z position for relative moves
    * @deprecated use Builder and zReference() instead
    */
   @Deprecated
   public double zReference = 0.0;
   /**
    * comment text
    * @deprecated use Builder and comment() instead
    */
   @Deprecated
   public String comment = "";
   /**
    * which configuration group is used to define channels
    * @deprecated use Builder and channelGroup() instead
    */
   @Deprecated
   public String channelGroup = "";
   /**
    * true if we want to have multiple positions
    * @deprecated use Builder and usePositionList() instead
    */
   @Deprecated
   public boolean usePositionList = false;
   /**
    * Minimum camera timeout, in ms, for sequence acquisitions
    * (actual timeout depends on exposure time and other factors)
    * @deprecated use Builder and cameraTimeout() instead
    */
   @Deprecated
   public int cameraTimeout = 20000;
   /**
    * Whether or not to display images generated by the acquisition.
    * @deprecated use Builder and shouldDisplayImages() instead
    */
   @Deprecated
   public boolean shouldDisplayImages = true;

   /**
    * Whether or not to acquire a z-stack during the acquisition.
    * @deprecated use Builder and useSlices() instead
    */
   @Deprecated
   public boolean useSlices = false;
   /**
    * Whether or not to acquire multiple time points during the acquisition.
    * @deprecated use Builder and useFrames() instead
    */
   @Deprecated
   public boolean useFrames = false;
   /**
    * Whether or not to acquire multiple channels during the acquisition.
    * @deprecated use Builder and useChannels() instead
    */
   @Deprecated
   public boolean useChannels = false;
   /**
    * Distance the z-drive should travel between steps when acquiring a z-stack
    * @deprecated use Builder and sliceZStepUm() instead
    */
   @Deprecated
   public double sliceZStepUm;
   /**
    * Start position of the z-stack in microns.  Can be absolute or relative to current position.
    * @deprecated use Builder and sliceZBottomUm() instead
    */
   @Deprecated
   public double sliceZBottomUm;
   /**
    * End position of the z-stack in microns.  Can be absolute or relative to current position.
    * @deprecated use Builder and sliceZTopUm() instead
    */
   @Deprecated
   public double sliceZTopUm;
   /**
    * Order of the various axes during acquisition
    * @deprecated use Builder and acqOrderMode() instead
    */
   @Deprecated
   public int acqOrderMode; // defined in org.micromanager.internal.utils.AcqOrderMode


   /**
    * Create a copy of this SequenceSettings. All parameters will be copied,
    * with new objects being created as necessary (i.e. this is a deep copy).
    * @return Copy of this SequenceSettings.
    */
   public SequenceSettings copy() {
      return new SequenceSettings(this);
   }

   /**
    * Default constructor needed since we have a copy constructor
    * @deprecated use Builder instead
    */
   @Deprecated
   public SequenceSettings() {
   }

   /**
    * copy Constructor provides a deep copy
    * @param input input that will b deep copied
    */
   private SequenceSettings(SequenceSettings input) {
      cameraTimeout = input.cameraTimeout;
      channelGroup = input.channelGroup;
      channels = input.channels == null ? null : new ArrayList<>(input.channels);
      comment = input.comment;
      customIntervalsMs = input.customIntervalsMs == null ? null : new ArrayList<>(input.customIntervalsMs);
      intervalMs = input.intervalMs;
      displayTimeUnit = input.displayTimeUnit;
      keepShutterOpenSlices = input.keepShutterOpenSlices;
      keepShutterOpenChannels = input.keepShutterOpenChannels;
      numFrames = input.numFrames;
      prefix = input.prefix;
      relativeZSlice = input.relativeZSlice;
      root = input.root;
      save = input.save;
      shouldDisplayImages = input.shouldDisplayImages;
      skipAutofocusCount = input.skipAutofocusCount;
      slices = input.slices == null ? null : new ArrayList<>(input.slices);
      slicesFirst = input.slicesFirst;
      timeFirst = input.timeFirst;
      useAutofocus = input.useAutofocus;
      useCustomIntervals = input.useCustomIntervals;
      usePositionList = input.usePositionList;
      zReference = input.zReference;
      useSlices = input.useSlices;
      useFrames = input.useFrames;
      useChannels = input.useChannels;
      sliceZStepUm = input.sliceZStepUm;
      sliceZBottomUm = input.sliceZBottomUm;
      sliceZTopUm = input.sliceZTopUm;
      acqOrderMode = input.acqOrderMode;
   }

   public SequenceSettings.Builder copyBuilder() {return new Builder(this);}

   public int numFrames() {return numFrames;}
   public double intervalMs() {return  intervalMs; }
   /**
    * Time unit, only used to store preferred way to display the time
    * @return 0-milliseconds, 1-seconds, 2-minutes
    */
   public int displayTimeUnit() { return displayTimeUnit;}
   public boolean useCustomIntervals() { return useCustomIntervals; }
   public ArrayList<Double> customIntervalsMs() { return customIntervalsMs; };
   public ArrayList<ChannelSpec> channels() {return channels; }
   public ArrayList<Double> slices() { return slices; }
   public boolean relativeZSlice() { return relativeZSlice; }
   public boolean slicesFirst() { return slicesFirst; }
   public boolean timeFirst() { return timeFirst; }
   public boolean keepShutterOpenSlices() { return keepShutterOpenSlices; }
   public boolean keepShutterOpenChannels() { return keepShutterOpenChannels; }
   public boolean useAutofocus() { return useAutofocus; }
   public int skipAutofocusCount() { return skipAutofocusCount; }
   public boolean save() { return save; }
   public String root() { return root; }
   public String prefix() { return prefix; }
   public double zReference() { return zReference; }
   public String comment() { return comment; }
   public String channelGroup() { return channelGroup; }
   public boolean usePositionList() { return usePositionList; }
   public int cameraTimeout() { return cameraTimeout; }
   public boolean shouldDisplayImages() { return shouldDisplayImages; }
   public boolean useSlices() { return useSlices; }
   public boolean useFrames() { return useFrames; }
   public boolean useChannels() { return  useChannels; }
   public double sliceZStepUm() { return sliceZStepUm ; }
   public double sliceZBottomUm() { return sliceZBottomUm; }
   public double sliceZTopUm () { return sliceZTopUm; }
   public int acqOrderMode() { return acqOrderMode; }

   public static String toJSONStream(SequenceSettings settings) {
      Gson gson = new GsonBuilder().setPrettyPrinting().create();
      return gson.toJson(settings);
   }

   public static SequenceSettings fromJSONStream(String stream) {
      Gson gson = new Gson();
      return gson.fromJson(stream, SequenceSettings.class);
   }
}
