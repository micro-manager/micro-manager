///////////////////////////////////////////////////////////////////////////////
//FILE:          HistogramSettings.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Henry Pinkard, henry.pinkard@ucsf.edu, 2013
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
package mmcloneclasses.graph;

public class HistogramSettings {
   public int min_;
   public int max_;
   public double gamma_;
   public int histMax_;
   public int displayMode_;
   
   public HistogramSettings(int min, int max, double gamma, int histMax, int displayMode) {
      min_ = min;
      max_ = max;
      gamma_ = gamma;
      histMax_ = histMax;
      displayMode_ = displayMode;
   }
   
}
