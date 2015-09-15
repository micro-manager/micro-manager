///////////////////////////////////////////////////////////////////////////////
//FILE:          SplitViewProcessor.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
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

import org.micromanager.data.Coords;
import org.micromanager.data.DatastoreFrozenException;
import org.micromanager.data.Image;
import org.micromanager.data.Processor;
import org.micromanager.data.ProcessorContext;
import org.micromanager.data.SummaryMetadata;

import org.micromanager.Studio;

/**
 * DataProcessor that splits images as instructed in SplitViewFrame
 *
 * @author nico
 */
public class SplitViewProcessor extends Processor {

   private Studio studio_;
   private String orientation_ = SplitViewFrame.LR;
   private boolean haveUpdatedMetadata_ = false;

   public SplitViewProcessor(Studio studio, String orientation) {
      studio_ = studio;
      orientation_ = orientation;
   }

   private String getChannelSuffix(int channelIndex) {
      String token;
      if (orientation_.equals(SplitViewFrame.LR)) {

         if ((channelIndex % 2) == 0) {
            token = "Left";
         } else {
            token = "Right";
         }
      } else { // orientation is "TB"
         if ((channelIndex % 2) == 0) {
            token = "Top";
         } else {
            token = "Bottom";
         }
      }
      return token;
   }

   @Override
   public void processImage(Image image, ProcessorContext context) {
      ImageProcessor proc = studio_.data().ij().createProcessor(image);
      int channelIndex = image.getCoords().getChannel();

      // Update channel names in summary metadata.
      if (!haveUpdatedMetadata_) {
         SummaryMetadata summary = context.getSummaryMetadata();
         String[] names = summary.getChannelNames();
         if (names == null) {
            names = new String[image.getCoords().getChannel() + 1];
         }
         String[] newNames = new String[names.length * 2];
         for (int i = 0; i < names.length; ++i) {
            newNames[i] = names[i];
         }
         String base = summary.getSafeChannelName(channelIndex);
         newNames[channelIndex * 2] = base + getChannelSuffix(channelIndex * 2);
         newNames[channelIndex * 2 + 1] = base + getChannelSuffix(channelIndex * 2 + 1);
         summary = summary.copy().channelNames(newNames).build();
         try {
            context.setSummaryMetadata(summary);
         }
         catch (DatastoreFrozenException e) {
            studio_.logs().logError("Unable to update channel names because datastore is frozen.");
         }
         haveUpdatedMetadata_ = true;
      }

      proc.setPixels(image.getRawPixels());

      int height = image.getHeight();
      int width = image.getWidth();
      height = calculateHeight(height, orientation_);
      width = calculateWidth(width, orientation_);

      proc.setRoi(0, 0, width, height);

      // first channel
      Coords firstCoords = image.getCoords().copy().channel(channelIndex * 2).build();
      Image first = studio_.data().createImage(proc.crop().getPixels(),
            width, height, image.getBytesPerPixel(), image.getNumComponents(),
            firstCoords, image.getMetadata());
      context.outputImage(first);

      // second channel
      if (orientation_.equals(SplitViewFrame.LR)) {
         proc.setRoi(width, 0, width, height);
      } else if (orientation_.equals(SplitViewFrame.TB)) {
         proc.setRoi(0, height, width, height);
      }
      Coords secondCoords = image.getCoords().copy().channel(channelIndex * 2 + 1).build();
      Image second = studio_.data().createImage(proc.crop().getPixels(),
            width, height, image.getBytesPerPixel(), image.getNumComponents(),
            secondCoords, image.getMetadata());
      context.outputImage(second);
   }

   public static int calculateWidth(int width, String orientation) {
      int newWidth = width;
      if (orientation.equals(SplitViewFrame.LR)) {
         newWidth = width / 2;
      }
      return newWidth;
   }

   public static int calculateHeight(int height, String orientation) {
      int newHeight = height;
      if (orientation.equals(SplitViewFrame.TB)) {
         newHeight = height / 2;
      }
      return newHeight;
   }
}
