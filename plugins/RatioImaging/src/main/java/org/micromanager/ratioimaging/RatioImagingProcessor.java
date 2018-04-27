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

import ij.process.Blitter;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;
import java.text.ParseException;

import java.util.ArrayList;
import java.util.List;
import org.micromanager.PropertyMap;

import org.micromanager.data.Coords;
import org.micromanager.data.Image;
import org.micromanager.data.Processor;
import org.micromanager.data.ProcessorContext;
import org.micromanager.data.SummaryMetadata;

import org.micromanager.Studio;
import org.micromanager.internal.utils.NumberUtils;

/**
 * DataProcessor that splits images as instructed in SplitViewFrame
 *
 * @author nico, heavily updated by Chris Weisiger
 */
public class RatioImagingProcessor extends Processor {

   private final Studio studio_;
   private final PropertyMap settings_;
   private final int factor_;
   private final int bc1Constant_;
   private final int bc2Constant_;
   private final List<Image> images_;
   private boolean process_;
   private int ch1Index_;
   private int ch2Index_;
   private int ratioIndex_;

   public RatioImagingProcessor(Studio studio, PropertyMap settings) {
      studio_ = studio;
      settings_ = settings;
      images_ = new ArrayList<Image>();
      int factor = 1; int bc1Constant = 0; int bc2Constant = 0;
      try {
         factor = NumberUtils.displayStringToInt(
              settings_.getString(RatioImagingFrame.FACTOR, "1"));
         bc1Constant = NumberUtils.displayStringToInt(
              settings_.getString(RatioImagingFrame.BACKGROUND1CONSTANT, "0"));
         bc2Constant = NumberUtils.displayStringToInt(
              settings_.getString(RatioImagingFrame.BACKGROUND2CONSTANT, "0"));
      } catch (ParseException pe) { // What to do? 
      }
      factor_ = factor;
      bc1Constant_ = bc1Constant;
      bc2Constant_ = bc2Constant;
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
      
      process_ = true;
      
      ch1Index_ = -1;
      ch2Index_ = -1;
      for (int i = 0; i < chNames.size(); i++) {
         if (chNames.get(i).equals(ch1Name)) {
            ch1Index_ = i;
         }
         if (chNames.get(i).equals(ch2Name)) {
            ch2Index_ = i;
         }
      }
      if (ch1Index_ < 0 || ch2Index_ < 0) {
         process_ = false;
         return summary;
      }
      
      String[] newNames = new String[chNames.size() + 1];
      for (int i = 0; i < chNames.size(); i++) {
         newNames[i] = (String) chNames.get(i);
      }
      newNames[chNames.size() ] = "ratio " + ch1Name + " / " + ch2Name;
      ratioIndex_ = chNames.size();
      
      return summary.copyBuilder().channelNames(newNames).build();
   }

   @Override
   public void processImage(Image newImage, ProcessorContext context) {
      
      context.outputImage(newImage);
      
      if (newImage.getNumComponents() > 1) {
         return;
      }
      if (! (newImage.getBytesPerPixel() == 1 || newImage.getBytesPerPixel() == 2) ) {
         return;
      }
      
      if (!process_) {
         return;
      }

      Coords newCoords = newImage.getCoords();
      int c = newImage.getCoords().getC();
      if (!(c == ch1Index_ || c == ch2Index_)) {
         return;
      }

      for (Image oldImage : images_) {
         Coords oldCoords = oldImage.getCoords();
         if (newCoords.copyRemovingAxes(Coords.C).equals(oldCoords.copyRemovingAxes(Coords.C))) {
            if (newCoords.getC() == ch1Index_ && oldCoords.getC() == ch2Index_) {
               process(newImage, oldImage, context);
               images_.remove(oldImage);
               return;
            }
         
            if (oldCoords.getC() == ch1Index_ && newCoords.getC() == ch2Index_) {
               process(oldImage, newImage, context);
               images_.remove(oldImage);
               return;
            }
         }
      }
      
      // if we are still here, there was no match, so add this image to our list
      images_.add(newImage);

   }
      
   private void process(Image ch1Image, Image ch2Image, ProcessorContext context) {
      Coords ratioCoords = ch1Image.getCoords().copyBuilder().c(ratioIndex_).build();
      
      ImageProcessor ch1Proc = studio_.data().ij().createProcessor(ch1Image);
      ImageProcessor ch2Proc = studio_.data().ij().createProcessor(ch2Image);
      ch1Proc = ch1Proc.convertToFloat();
      ch2Proc = ch2Proc.convertToFloat();
      ch1Proc.subtract(bc1Constant_);
      ch2Proc.subtract(bc2Constant_);
      ImageProcessor ch3Proc = ch1Proc.createProcessor(ch1Proc.getWidth(), 
              ch1Proc.getHeight());
      ch3Proc.insert(ch1Proc, 0, 0);
      ch3Proc.copyBits(ch2Proc, 0, 0, Blitter.DIVIDE);
      ch3Proc.multiply(factor_);
      
      if (ch1Image.getBytesPerPixel() == 1) {
         // check this actually works....
         ch3Proc = ch3Proc.convertToByteProcessor();
      } else if (ch1Image.getBytesPerPixel() == 2) {
         // ImageJ method seems to be broken. Copied code from ImageJ1 here
         ch3Proc = convertFloatToShort( (FloatProcessor) ch3Proc);
      }
      int max = (int) ch3Proc.getMax();
      int bitDepth = 1;
      while ( (1 << bitDepth) < max && bitDepth <= ch1Image.getBytesPerPixel() * 8) {
         bitDepth += 1;
      }
      
      Image ratioImage = studio_.data().ij().createImage(ch3Proc, ratioCoords, 
              ch1Image.getMetadata().copyBuilderWithNewUUID().bitDepth(bitDepth).
                      build());
      
      context.outputImage(ratioImage);
   }
   
   /**
    * Copied from https://github.com/imagej/imagej1/blob/master/ij/process/TypeConverter.java
    * 
    * The call to chrProc.convertToShortProcess results in pixel values of -1
    * Not sure why (but in included ImageJ version?) 
    * Copying the relevant code from the ImageJ1 source fixes it
    *  
    * @param ip FloatProcessor to be converted
    * @return Shortprocessor
    */
   ShortProcessor convertFloatToShort(FloatProcessor ip) {
		float[] pixels32 = (float[])ip.getPixels();
		short[] pixels16 = new short[ip.getWidth()*ip.getHeight()];
		double value;
		for (int i=0,j=0; i< (ip.getWidth() * ip.getHeight()); i++) {
			value = pixels32[i];
			if (value<0.0) {
            value = 0.0;
         }
			if (value>65535.0) {
            value = 65535.0;
         }
			pixels16[i] = (short)(value+0.5);
		}
	    return new ShortProcessor(ip.getWidth(), ip.getHeight(), pixels16, 
               ip.getColorModel());
   }
}
