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
package imagedisplay;

/**
 * This interface allows us to manipulate the dimensions
 * in an ImagePlus without it throwing conniptions.
 */
public interface IMMImagePlus {
   public int getNChannelsUnverified();
   public int getNSlicesUnverified();
   public int getNFramesUnverified();
   public void setNChannelsUnverified(int nChannels);
   public void setNSlicesUnverified(int nSlices);
   public void setNFramesUnverified(int nFrames);
   public void drawWithoutUpdate();

   public int[] getPixelIntensities(int x, int y);
}

