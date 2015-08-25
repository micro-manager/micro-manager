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

import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;
import mmcorej.TaggedImage;
import org.json.JSONException;
import org.json.JSONObject;
import org.micromanager.acquisition.internal.TaggedImageQueue;
import org.micromanager.data.Coords;
import org.micromanager.data.Image;
import org.micromanager.data.Metadata;
import org.micromanager.data.Processor;
import org.micromanager.data.ProcessorContext;
import org.micromanager.Studio;
import org.micromanager.internal.utils.MDUtils;
import org.micromanager.internal.utils.MMScriptException;
import org.micromanager.internal.utils.ReportingUtils;

/**
 * DataProcessor that splits images as instructed in SplitViewFrame
 *
 * @author nico
 */
public class SplitViewProcessor implements Processor {

   private Studio studio_;
   private String orientation_ = SplitViewFrame.LR;

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

      proc.setPixels(image.getRawPixels());

      int height = image.getHeight();
      int width = image.getWidth();
      height = calculateHeight(height, orientation_);
      width = calculateWidth(width, orientation_);

      proc.setRoi(0, 0, width, height);

      // first channel
      Coords firstCoords = image.getCoords().copy().channel(channelIndex * 2).build();
      Metadata firstMetadata = image.getMetadata();
      firstMetadata = firstMetadata.copy().channelName(
            firstMetadata.getChannelName() + getChannelSuffix(channelIndex * 2)).build();
      Image first = studio_.data().createImage(proc.crop().getPixels(),
            width, height, image.getBytesPerPixel(), image.getNumComponents(),
            firstCoords, firstMetadata);
      context.outputImage(first);

      // second channel
      if (orientation_.equals(SplitViewFrame.LR)) {
         proc.setRoi(width, 0, width, height);
      } else if (orientation_.equals(SplitViewFrame.TB)) {
         proc.setRoi(0, height, width, height);
      }
      Coords secondCoords = image.getCoords().copy().channel(channelIndex * 2 + 1).build();
      Metadata secondMetadata = image.getMetadata();
      secondMetadata = secondMetadata.copy().channelName(
            secondMetadata.getChannelName() + getChannelSuffix(channelIndex * 2 + 1)).build();
      Image second = studio_.data().createImage(proc.crop().getPixels(),
            width, height, image.getBytesPerPixel(), image.getNumComponents(),
            secondCoords, secondMetadata);
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
