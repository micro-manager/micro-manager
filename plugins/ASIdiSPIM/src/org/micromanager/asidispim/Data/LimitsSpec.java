///////////////////////////////////////////////////////////////////////////////
//FILE:          LimitsSpec.java
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
 * Representation of information in limits table of 
 * diSPIM plugin.  Based on org.micromanager.utils.ChannelSpec.java. 
 */
public class LimitsSpec {
   public static final double Version = 0.1;

   // fields that are used
   public boolean use_; // whether or not to use this group
   public double xCoeff_;
   public double yCoeff_;
   public double zCoeff_;
   public double sum_;
   public boolean invert_;
   
   // not used yet but may be useful in future
   //   public double exposure = 10.0; // ms
   //   public double zOffset = 0.0; // um
   //   public Color color = Color.gray;

   
   public LimitsSpec(boolean use, double xCoeff, double yCoeff, double zCoeff, double sum, boolean invert) {
      this.use_ = use;
      this.xCoeff_ = xCoeff;
      this.yCoeff_ = yCoeff;
      this.zCoeff_ = zCoeff;
      this.sum_ = sum;
      this.invert_ = invert;
   }
   
   public LimitsSpec(LimitsSpec orig) {
      this.use_ = orig.use_;
      this.xCoeff_ = orig.xCoeff_;
      this.yCoeff_ = orig.yCoeff_;
      this.zCoeff_ = orig.zCoeff_;
      this.sum_ = orig.sum_;
      this.invert_ = orig.invert_;
   }
   
}