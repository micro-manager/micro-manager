///////////////////////////////////////////////////////////////////////////////
// AUTHOR:       Henry Pinkard, henry.pinkard@gmail.com
//
// COPYRIGHT:    University of California, San Francisco, 2015
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

package org.micromanager.plugins.magellan.bidc;

import java.util.ArrayList;

/**
 *
 * @author Henry
 */
public abstract class FrameIntegrationMethod {

   public static final int FRAME_AVERAGE = 0, RANK_FILTER = 1, FRAME_SUMMATION = 2;
   protected int width_, height_;
   protected ArrayList<RawBufferWrapper> rawBuffers_;
   protected int doubleWidth_, numFrames_;
   private int offset_;

   
   public FrameIntegrationMethod(int doubleWidth, int offset, int numFrames) {
      offset_ = offset;
      numFrames_ = numFrames;
      doubleWidth_ = doubleWidth;
      rawBuffers_ = new ArrayList<RawBufferWrapper>();
      width_ = RawBufferWrapper.getWidth();
      height_ = RawBufferWrapper.getHeight();
   }

   
   /**
    * Add a single 
    * @param buffer 
    */
   public void addBuffer(byte[] buffer) {
      rawBuffers_.add(new RawBufferWrapper(buffer, offset_, doubleWidth_));
   }

   public int getConstructedImageWidth() {
      return width_;
   }
   
   public int getConstructedImageHeight() {
      return height_;
   }

   public abstract Object constructImage();
}
