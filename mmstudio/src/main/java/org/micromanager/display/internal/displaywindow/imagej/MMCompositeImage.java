// Copyright (C) 2015-2017 Open Imaging, Inc.
//           (C) 2015 Regents of the University of California
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

package org.micromanager.display.internal.displaywindow.imagej;

import ij.CompositeImage;
import ij.ImagePlus;

/**
 * Wrapped version of ImageJ's {@code CompositeImage}.
 *
 * @see MMImagePlus
 * @author Mark A. Tsuchida, based on older version by Chris Weisiger
 */
public final class MMCompositeImage extends CompositeImage implements IMMImagePlus {
  static MMCompositeImage create(ImageJBridge parent, ImagePlus predecessor) {
    return new MMCompositeImage(parent, predecessor);
  }

  private MMCompositeImage(ImageJBridge parent, ImagePlus predecessor) {
    super(predecessor, COMPOSITE);
    // So far we don't use parent reference
  }

  @Override
  public void setDimensionsWithoutUpdate(int nChannels, int nSlices, int nFrames) {
    super.nSlices = nSlices;
    super.nFrames = nFrames;
    super.nChannels = nChannels;
    super.dimensionsSet = true;
  }

  @Override
  public int getNChannelsWithoutSideEffect() {
    return super.nChannels;
  }

  @Override
  public int getNSlicesWithoutSideEffect() {
    return super.nSlices;
  }

  @Override
  public int getNFramesWithoutSideEffect() {
    return super.nFrames;
  }
}
