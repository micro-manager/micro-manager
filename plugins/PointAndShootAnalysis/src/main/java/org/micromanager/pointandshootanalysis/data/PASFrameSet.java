///////////////////////////////////////////////////////////////////////////////
//FILE:          PASFrameSet.java
//PROJECT:       PointAndShootAnalysis
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nico Stuurman
//
// COPYRIGHT:    University of California, San Francisco 2018
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

package org.micromanager.pointandshootanalysis.data;

/**
 *
 * @author nico
 */
public class PASFrameSet {
   private final int startFrame_;
   private final int centralFrame_;
   private final int endFrame_;
   
   public PASFrameSet(final int startFrame, final int centralFrame, 
           final int endFrame, final int nrFrames) {
      startFrame_ = startFrame < 0 ? 0 : startFrame;
      centralFrame_ = centralFrame;
      endFrame_ = endFrame >= nrFrames
                    ? nrFrames - 1 : endFrame;
   }
   
   public int getStartFrame() {
      return startFrame_;
   }

   public int getCentralFrame() {
      return centralFrame_;
   }

   public int getEndFrame() {
      return endFrame_;
   }
   
}
