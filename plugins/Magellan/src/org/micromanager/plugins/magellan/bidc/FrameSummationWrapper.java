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

/**
 *
 * @author bidc
 */
public class FrameSummationWrapper extends FrameIntegrationMethod {
    
   public FrameSummationWrapper(int offset, int doubleWidth, int numFrames) {
      super(doubleWidth, offset, numFrames);
   }

   @Override
   public Object constructImage() {
      //sort all lists and construct final image
      short[] summedPixels = new short[width_ * height_];
      for (int i = 0; i < summedPixels.length; i++) {
         int sum = 0;
         for (int f = 0; f < numFrames_; f++) {
            summedPixels[i] += rawBuffers_.get(f).getUnwarpedImageValue(i % width_, i / width_);
         }
      }
      return summedPixels;
   }
    
}
