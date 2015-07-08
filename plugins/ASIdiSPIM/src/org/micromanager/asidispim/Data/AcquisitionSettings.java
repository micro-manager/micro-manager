///////////////////////////////////////////////////////////////////////////////
//FILE:          AcquisitionSettings.java
//PROJECT:       Micro-Manager 
//SUBSYSTEM:     ASIdiSPIM plugin
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nico Stuurman, Jon Daniels
//
// COPYRIGHT:    University of California, San Francisco, & ASI, 2015
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

package org.micromanager.asidispim.Data;

import org.micromanager.asidispim.Utils.SliceTiming;

/**
 * Associative container for slice timing information.
 * Public elements so they can be get/set directly, like C++ struct
 * @author nico
 */

public class AcquisitionSettings {
   // if true, the tiger controller will run multiple timepoints
   public boolean hardwareTimepoints_;
   // MultiChannel mode
   public MultichannelModes.Keys channelMode_;
   // if true, use channels
   public boolean useChannels_;
   // number of channels for this acquisition
   public int numChannels_;
   // number of slices for this acquisition
   public int numSlices_;
   // number of timepoints for this acquisition
   public int numTimepoints_;
   // time between starts of timepoints
   public double timePointInterval_;
   // number of Sides from which we take data (diSPIM: 1 or 2)
   public int numSides_;
   // firstSide to take data from (A or B)
   public String firstSide_;
   // whether or not we use time points
   public boolean useTimepoints_;
   // piezo scanning, vibration, stage scanning, i.e. what is 
   //                 moved between slices
   public AcquisitionModes.Keys spimMode_;
   // true to use current Z position (e.g. autofocus), 
   // false to use specified acquisition center
   public boolean centerAtCurrentZ_;
   // wait in ms before starting each side (piezo only)
   public float delayBeforeSide_;
   // spacing between slices in microns
   public float stepSizeUm_;
   // low level controller timing parameters
   public SliceTiming sliceTiming_;
}
