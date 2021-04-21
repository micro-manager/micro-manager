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

/**
 * Common interface of {@code MMImagePlus} and {@code MMCompositeImage}, providing access to
 * protected members of {@code ij.ImagePlus}.
 *
 * @author Mark A. Tsuchida
 */
interface IMMImagePlus {
  /**
   * Set the axis extents of this {@code ImagePlus} without triggering a repaint or window creation.
   *
   * <p>This is something that is missing from {@code ImagePlus}'s interface.
   *
   * @param nChannels new number of channels
   * @param nSlices new number of Z slices
   * @param nFrames new number of time points
   *     <p>see ij.ImagePlus.setDimensions
   */
  public void setDimensionsWithoutUpdate(int nChannels, int nSlices, int nFrames);

  /**
   * Get the number of channels of this {@code ImagePlus} without altering the axis extents of this
   * {@code ImagePlus}.
   *
   * <p>The provided {@code ImagePlus.getNChannels} method will alter the number of
   * channels/slices/frames if called under certain conditions.
   *
   * @return the extent of the channel axis
   *     <p>see ij.ImagePlus.getNChannels
   */
  public int getNChannelsWithoutSideEffect();

  /**
   * Get the number of Z slices of this {@code ImagePlus} without altering the axis extents of this
   * {@code ImagePlus}.
   *
   * <p>The provided {@code ImagePlus.getNSlices} method will alter the number of
   * channels/slices/frames if called under certain conditions.
   *
   * @return the extent of the Z slice axis
   *     <p>see ij.ImagePlus.getNSlices
   */
  public int getNSlicesWithoutSideEffect();

  /**
   * Get the number of time points of this {@code ImagePlus} without altering the axis extents of
   * this {@code ImagePlus}.
   *
   * <p>The provided {@code ImagePlus.getNFrames} method will alter the number of
   * channels/slices/frames if called under certain conditions.
   *
   * @return the extent of the time axis
   *     <p>see ij.ImagePlus.getNFrames
   */
  public int getNFramesWithoutSideEffect();
}
