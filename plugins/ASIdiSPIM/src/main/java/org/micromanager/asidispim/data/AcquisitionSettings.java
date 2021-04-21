///////////////////////////////////////////////////////////////////////////////
// FILE:          AcquisitionSettings.java
// PROJECT:       Micro-Manager
// SUBSYSTEM:     ASIdiSPIM plugin
// -----------------------------------------------------------------------------
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

package org.micromanager.asidispim.data;

import java.util.ArrayList;
import org.micromanager.acquisition.SequenceSettings;
import org.micromanager.asidispim.utils.SliceTiming;

/**
 * Associative container or "plain old data structure" for acquisition settings. Public elements so
 * they can be get/set directly, like C/C++ struct Note that this container doesn't work with
 * collections (https://www.artima.com/lejava/articles/equality.html)
 *
 * @author Nico
 * @author Jon
 */
public class AcquisitionSettings {
  // piezo scanning, vibration, stage scanning, i.e. what is
  //                 moved between slices
  public AcquisitionModes.Keys spimMode;

  // true iff using stage scanning (for convenience b/c have this in spimMode)
  public boolean isStageScanning;

  // whether or not we use time points
  public boolean useTimepoints;

  // number of timepoints for this acquisition
  public int numTimepoints;

  // time between starts of timepoints in seconds
  public double timepointInterval;

  // if true, use multiple positions
  public boolean useMultiPositions;

  // if true, use channels is enabled (but may only have single channel)
  public boolean useChannels;

  // MultiChannel mode
  public MultichannelModes.Keys channelMode;

  // number of channels for this acquisition
  public int numChannels;

  // array of channels
  public ChannelSpec[] channels;

  // channel group
  public String channelGroup;

  // whether or not to use autofocus during acquisition
  public boolean useAutofocus;

  // whether or not to correct movement during acquisition
  public boolean useMovementCorrection;

  // whether or not to acquire from both cameras simultaneously (reflective imaging)
  public boolean acquireBothCamerasSimultaneously;

  // number of Sides from which we take data (diSPIM: 1 or 2)
  public int numSides;

  // firstSide to take data from (A or B)
  public boolean firstSideIsA;

  // wait in ms before starting each side
  public float delayBeforeSide;

  // number of slices for this acquisition
  public int numSlices;

  // spacing between slices in microns
  public float stepSizeUm;

  // if true, automatically minimize slice period
  public boolean minimizeSlicePeriod;

  // requested slice period (only matters if minimizeSlicePeriod_ is false
  public float desiredSlicePeriod;

  // requested time for laser to be on each slice
  public float desiredLightExposure;

  // true to use current Z position (e.g. autofocus),
  // false to use specified acquisition center
  public boolean centerAtCurrentZ;

  // low level controller timing parameters
  public SliceTiming sliceTiming;

  // camera in overlap, edge, etc. mode
  public CameraModes.Keys cameraMode;

  // if true, the tiger controller will coordinate multiple timepoints
  // instead of having plugin trigger each one
  // true/false determined by the interval between timpoints vs. overhead
  // to trigger each one separately
  public boolean hardwareTimepoints;

  // true if we are doing separate acquisition (viewer and file) for every timepoint
  public boolean separateTimepoints;

  /**
   * Fills in as many of the MM Sequence Settings parameters as possible so that we can give MM an
   * idea what we are up to
   *
   * @return MM Sequence Settings
   */
  public SequenceSettings getSequenceSettings() {
    SequenceSettings.Builder mmssb = new SequenceSettings.Builder();
    mmssb.numFrames(this.numTimepoints);
    mmssb.channelGroup(this.channelGroup);
    // mmss.channels = new ArrayList<ChannelSpec>(Arrays.asList(this.channels));

    ArrayList<Double> slices = new ArrayList<Double>(this.numSlices);
    for (int z = 0; z < this.numSlices; z++) {
      // todo: add the start z position
      slices.add(this.stepSizeUm * (double) z);
    }
    mmssb.slices(slices);

    mmssb.usePositionList(this.useMultiPositions);

    return mmssb.build();
  }
}
