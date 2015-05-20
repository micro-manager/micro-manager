///////////////////////////////////////////////////////////////////////////////
//FILE:          ImagePlusInfo.java
//PROJECT:       Micro-Manager  
//SUBSYSTEM:     MultiChannelShading plugin
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nico Stuurman
//
// COPYRIGHT:    University of California, San Francisco 2014
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

package org.micromanager.multichannelshading;

import ij.ImagePlus;
import ij.process.ImageProcessor;
import java.awt.Rectangle;

/**
 * Utility class that bundles an ImagePlus with the binning setting and the roi 
 * (of the full frame, binned image) of the ImagePlus.  
 * @author nico
 */
public class ImagePlusInfo extends ImagePlus{
   private final int binning_;
   private final Rectangle roi_;
    
   
   public ImagePlusInfo(ImagePlus ip, int binning, Rectangle roi) {
      super(ip.getTitle(), ip.getProcessor());
      binning_ = binning;
      roi_ = roi;
   }
   
   public ImagePlusInfo(ImagePlus ip) {
      this(ip, 1, new Rectangle(0, 0, ip.getWidth(), ip.getHeight()));
   }
   
   public ImagePlusInfo(ImageProcessor ip) {
      super("", ip);
      binning_ = 1;
      roi_ = new Rectangle(0, 0, ip.getWidth(), ip.getHeight());
   }
   
   public int getBinning() {
      return binning_;
   }
   
   public Rectangle getOriginalRoi() {
      return roi_;
   }
}
