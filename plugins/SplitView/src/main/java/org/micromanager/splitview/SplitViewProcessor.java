///////////////////////////////////////////////////////////////////////////////
// FILE:          SplitViewProcessor.java
// PROJECT:       Micro-Manager
// SUBSYSTEM:     mmstudio
// -----------------------------------------------------------------------------
//
// AUTHOR:       Nico Stuurman
//
// COPYRIGHT:    University of California, San Francisco, 2011, 2012
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

package org.micromanager.splitview;

import ij.process.ImageProcessor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.micromanager.data.Coords;
import org.micromanager.data.Image;
import org.micromanager.data.Processor;
import org.micromanager.data.ProcessorContext;
import org.micromanager.data.SummaryMetadata;

import org.micromanager.Studio;

/**
 * DataProcessor that splits images as instructed in SplitViewFrame
 *
 * @author nico, heavily updated by Chris Weisiger
 */
public class SplitViewProcessor implements Processor {

  private final Studio studio_;
  private String orientation_ = SplitViewFrame.LR;
  private final int numSplits_;
  private final ArrayList<String> channelSuffixes_;

  public SplitViewProcessor(Studio studio, String orientation, int numSplits) {
    studio_ = studio;
    orientation_ = orientation;
    numSplits_ = numSplits;

    if (orientation_.equals(SplitViewFrame.LR)) {
      channelSuffixes_ = new ArrayList<String>(Arrays.asList(new String[] {"Left", "Right"}));
    } else {
      channelSuffixes_ = new ArrayList<String>(Arrays.asList(new String[] {"Top", "Bottom"}));
    }
    // Insert "Middle" suffixes.
    if (numSplits_ == 3) {
      channelSuffixes_.add(1, "Middle");
    } else if (numSplits_ > 3) {
      // Just number things.
      channelSuffixes_.clear();
      for (int i = 0; i < numSplits_; ++i) {
        channelSuffixes_.add(Integer.toString(i + 1));
      }
    }
  }

  @Override
  public SummaryMetadata processSummaryMetadata(SummaryMetadata summary) {
    // Update channel names in summary metadata.
    List<String> chNames = summary.getChannelNameList();
    if (chNames == null || chNames.isEmpty()) {
      // Can't do anything as we don't know how many names there'll be.
      return summary;
    }
    String[] newNames = new String[chNames.size() * numSplits_];
    for (int i = 0; i < chNames.size(); ++i) {
      String base = summary.getSafeChannelName(i);
      for (int j = 0; j < numSplits_; ++j) {
        newNames[i * numSplits_ + j] = base + channelSuffixes_.get(j);
      }
    }
    return summary.copyBuilder().channelNames(newNames).build();
  }

  @Override
  public void processImage(Image image, ProcessorContext context) {
    ImageProcessor proc = studio_.data().ij().createProcessor(image);

    int width = image.getWidth();
    int height = image.getHeight();
    int xStep = 0;
    int yStep = 0;
    if (orientation_.equals(SplitViewFrame.TB)) {
      height /= numSplits_;
      yStep = height;
    } else {
      width /= numSplits_;
      xStep = width;
    }

    int channelIndex = image.getCoords().getChannel();
    for (int i = 0; i < numSplits_; ++i) {
      proc.setRoi(i * xStep, i * yStep, width, height);

      Coords coords = image.getCoords().copy().channel(channelIndex * numSplits_ + i).build();
      Image output =
          studio_
              .data()
              .createImage(
                  proc.crop().getPixels(),
                  width,
                  height,
                  image.getBytesPerPixel(),
                  image.getNumComponents(),
                  coords,
                  image.getMetadata());
      context.outputImage(output);
    }
  }
}
