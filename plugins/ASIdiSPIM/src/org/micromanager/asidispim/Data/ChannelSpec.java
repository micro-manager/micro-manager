///////////////////////////////////////////////////////////////////////////////
//FILE:          ChannelSpec.java
//PROJECT:       Micro-Manager 
//SUBSYSTEM:     ASIdiSPIM plugin
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nico Stuurman, Jon Daniels
//
// COPYRIGHT:    University of California, San Francisco, & ASI, 2013
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
package org.micromanager.asidispim.Data;


/**
 * Representation of information in channel table of 
 * diSPIM plugin.  Based on org.micromanager.utils.ChannelSpec.java. 
 */
public class ChannelSpec {
   public static final String DEFAULT_CHANNEL_GROUP = "Channel";
   public static final double Version = 0.1;

   // fields that are used
   public boolean useChannel_; // whether or not to use this group
   public String group_; // configuration group
   public String config_; // Configuration setting name
   
   // not used yet but may be useful in future
   //   public double exposure = 10.0; // ms
   //   public double zOffset = 0.0; // um
   //   public Color color = Color.gray;

   
   public ChannelSpec(boolean useChannel, String group, String config) {
      this.useChannel_ = useChannel;
      this.group_ = group;
      this.config_ = config;
   }
   
   public ChannelSpec(ChannelSpec orig) {
      this.useChannel_ = orig.useChannel_;
      this.group_ = orig.group_;
      this.config_ = orig.config_;
   }
   
}