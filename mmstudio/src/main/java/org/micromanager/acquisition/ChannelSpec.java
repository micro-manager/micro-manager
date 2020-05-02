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
   public static class Builder{

      /** Channel group this channel config belongs to **/
      private String channelGroup_ = "";
      /** Name of the channel */
      private String config_ = "";
      /** Exposure time, in milliseconds */
      private double exposure_ = 10.0;
      /** Z-offset, in microns */
      private double zOffset_ = 0.0;
      /** Whether this channel should be imaged in each Z slice of the stack */
      private Boolean doZStack_ = true;
      /** Color to use when displaying this channel */
      private Color color_ = Color.gray;
      /** Number of frames to skip between each time this channel is imaged. */
      private int skipFactorFrame_ = 0;
      /** Whether the channel is enabled for imaging at all. */
      private boolean useChannel_ = true;
      /** Name of the camera to use. */
      private String camera_ = "";

      public Builder() {}

      public Builder channelGroup (String channelGroup) { channelGroup_ = channelGroup;
         return this;
      }
      public Builder config (String config) { config_ = config; return this; }
      public Builder exposure (double exposure) { exposure_ = exposure; return this; }
      public Builder zOffset (double zOffset) { zOffset_ = zOffset; return this; }
      public Builder doZStack (Boolean doZStack) { doZStack_ = doZStack; return this; }
      public Builder color (Color color) { color_ = color; return this; }
      public Builder skipFactorFrame (int skipFactorFrame) { skipFactorFrame_ = skipFactorFrame; return this; }
      public Builder useChannel (boolean useChannel) { useChannel_ = useChannel; return this; }
      public Builder camera (String camera) {camera_ = camera; return this; }

      private Builder(String channelGroup, String config, double exposure,
                      double zOffset, Boolean doZStack, Color color,
                      int skipFactorFrame, boolean useChannel, String camera) {
         channelGroup_ = channelGroup;
         config_ = config;
         exposure_ = exposure;
         zOffset_ = zOffset;
         doZStack_ = doZStack;
         color_ = color;
         skipFactorFrame_ = skipFactorFrame;
         useChannel_ = useChannel;
         camera_ = camera;
      }

      public ChannelSpec build() {
         return new ChannelSpec(channelGroup_, config_, exposure_, zOffset_,
                 doZStack_, color_, skipFactorFrame_, useChannel_,  camera_);
      }
   }


   /** Channel group this channel config belongs to
    * @deprecated Use Builder and getters instead **/
   @Deprecated
   public String channelGroup = "";
   /** Name of the channel
    * @deprecated Use Builder and getters instead **/
   @Deprecated
   public String config = "";
   /** Exposure time, in milliseconds
    * @deprecated Use Builder and getters instead **/
   @Deprecated
   public double exposure = 10.0;
   /** Z-offset, in microns
    * @deprecated Use Builder and getters instead **/
   @Deprecated
   public double zOffset = 0.0;
   /** Whether this channel should be imaged in each Z slice of the stack
    * @deprecated Use Builder and getters instead **/
   @Deprecated
   public Boolean doZStack = true;
   /** Color to use when displaying this channel
    * @deprecated Use Builder and getters instead **/
   @Deprecated
   public Color color = Color.gray;
   /** Number of frames to skip between each time this channel is imaged.
    * @deprecated Use Builder and getters instead **/
   @Deprecated
   public int skipFactorFrame = 0;
   /** Whether the channel is enabled for imaging at all.
    * @deprecated Use Builder and getters instead **/
   @Deprecated
   public boolean useChannel = true;
   /** Name of the camera to use.
    * @deprecated Use Builder and getters instead **/
   @Deprecated
   public String camera = "";

   /**
    * @deprecated Use Builder.build() instead for a default ChannelSpec
    */
   @Deprecated
   public ChannelSpec(){
      color = Color.WHITE;
   }

   private ChannelSpec(String mChannelGroup, String mConfig, double mExposure,
                      double mZOffset, Boolean mDoZStack, Color mColor,
                      int mSkipFactorFrame, boolean mUseChannel, String mCamera) {
      channelGroup = mChannelGroup;
      config = mConfig;
      exposure = mExposure;
      zOffset = mZOffset;
      doZStack = mDoZStack;
      color = mColor;
      skipFactorFrame = mSkipFactorFrame;
      useChannel = mUseChannel;
      camera = mCamera;
   }

   public Builder copyBuilder() {
      return new Builder(channelGroup, config, exposure, zOffset, doZStack,
              color, skipFactorFrame, useChannel, camera);
   }

   public String channelGroup () { return channelGroup; }
   public String config() { return config; }
   public double exposure() { return exposure; }
   public double zOffset() { return zOffset; }
   public boolean doZStack() { return doZStack; }
   public Color color() { return color; }
   public int skipFactorFrame() { return skipFactorFrame; }
   public boolean useChannel() { return useChannel; }
   public String camera() { return camera; }

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
      return gson.fromJson(stream, ChannelSpec.class);
   }
}
