///////////////////////////////////////////////////////////////////////////////
//FILE:          ColorSpec.java
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
 * Representation of information in color table (akin to channel table) of 
 * diSPIM plugin.  Based on org.micromanager.utils.ChannelSpec.java. 
 */
public class ColorSpec {
   public static final String DEFAULT_CHANNEL_GROUP = "Channel";
   public static final double Version = 0.1;

   // fields that are used
   public boolean useChannel = false;
   public String config = ""; // Configuration setting name
   
   // not used yet but may be useful in future
//   public double exposure = 10.0; // ms
//   public double zOffset = 0.0; // um
//   public Color color = Color.gray;

   
   public ColorSpec(boolean useChannel, String config){
      this.useChannel = useChannel;
      this.config = config;
//      color = Color.WHITE;
   }
   
   
}

