///////////////////////////////////////////////////////////////////////////////
//FILE:          RatioImagingProcessor.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nico Stuurman
//
// COPYRIGHT:    University of California, San Francisco, 2018
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



package org.micromanager.ratioimaging;

import ij.process.ImageProcessor;

import java.util.ArrayList;
import java.util.List;
import org.micromanager.PropertyMap;

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
public class RatioImagingProcessor extends Processor {

   private final Studio studio_;
   private final PropertyMap settings_;
   private Image img_;
   private boolean process_;

   public RatioImagingProcessor(Studio studio, PropertyMap settings) {
      studio_ = studio;
      settings_ = settings;
   }

   @Override
   public SummaryMetadata processSummaryMetadata(SummaryMetadata summary) {
      // Update channel names in summary metadata.
      List<String> chNames = summary.getChannelNameList();
      if (chNames == null || chNames.isEmpty() || chNames.size() < 2) {
         // Can't do anything as we don't know how many names there'll be.
         return summary;
      }
      String ch1Name = settings_.getString(RatioImagingFrame.CHANNEL1, "");
      String ch2Name = settings_.getString(RatioImagingFrame.CHANNEL2, "");
      if (! (chNames.contains(ch1Name) && chNames.contains(ch2Name))) {
         process_ = false;
         return summary;
      }
      
      process_ = true;
      String[] newNames = new String[chNames.size() + 1];
      for (int i = 0; i < chNames.size(); i++) {
         newNames[i] = (String) chNames.get(i);
      }
      newNames[chNames.size() ] = "ratio " + ch1Name + " / " + ch2Name;
      
      return summary.copyBuilder().channelNames(newNames).build();
   }

   @Override
   public void processImage(Image image, ProcessorContext context) {
      
      if (img_ == null || !process_) {
         img_ = image;
         context.outputImage(image);
         return;
      }
      Coords newCoords = image.getCoords();
      Coords oldCoords = img_.getCoords();
      if (newCoords.copyRemovingAxes(Coords.C) == oldCoords.copyRemovingAxes(Coords.C)) {
         // may have found it.
      }
      
      ImageProcessor proc = studio_.data().ij().createProcessor(image);

      int width = image.getWidth();
      int height = image.getHeight();
      int xStep = 0;
      int yStep = 0;

     
   }
}
