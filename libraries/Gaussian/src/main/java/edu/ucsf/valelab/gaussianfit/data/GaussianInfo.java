/*
 * Contains information about the gaussian fit to be performed
 * This class is not meant to be used by itself but should be inherited from
 * 
Copyright (c) 2010-2017, Regents of the University of California
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
   list of conditions and the following disclaimer.
2. Redistributions in binary form must reproduce the above copyright notice,
   this list of conditions and the following disclaimer in the documentation
   and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

The views and conclusions contained in the software and documentation are those
of the authors and should not be interpreted as representing official policies,
either expressed or implied, of the FreeBSD Project.
 */

package edu.ucsf.valelab.gaussianfit.data;

import ij.ImagePlus;
import ij.process.ImageProcessor;
import java.util.List;
import java.util.concurrent.BlockingQueue;

/**
 * @author nico
 */
public class GaussianInfo {

   protected static final Object GFSLOCK = new Object();
   protected ImagePlus siPlus_;
   protected ImageProcessor siProc_;
   protected BlockingQueue<SpotData> sourceList_;
   protected List<SpotData> resultList_;

   // half the size (in pixels) of the square used for Gaussian fitting
   private int halfSize_ = 8;
   protected double baseLevel_ = 100; // base level (bias) of the camera in counts
   protected double readNoise_ = 0.0; // Effective Read Noise of the camera in electrons
   // Will not be corrected for EM gain

   // settings for maximum finder
   protected int noiseTolerance_ = 100;

   // Needed to calculate # of photons and estimate error
   // the real PCF is calculated as photonConversionFactor_ * gain_
   protected double photonConversionFactor_ = 10.41;
   protected double gain_ = 50;      // linear (EM) gain
   protected float pixelSize_ = 107; // nm/pixel
   protected float zStackStepSize_ = 50;  // step size of Z-stack in nm
   protected double timeIntervalMs_ = 100;

   // Filters for results of Gaussian fit
   protected double widthMax_ = 200;
   protected double widthMin_ = 100;
   protected boolean useWidthFilter_ = false;

   protected double nrPhotonsMin_ = 100;
   protected double nrPhotonsMax_ = 100000;
   protected boolean useNrPhotonsFilter_;


   // Settings affecting Gaussian fitting
   protected int maxIterations_ = 200;
   //protected int mode_;       // ?
   private int shape_;      // 1. symmetric, 2. asymetric, 3. asymetric with theta 
   private int fitMode_;    // Algorithm to be used (Simplex, LM, SImplex-MLE,

   // Setting determinig tracking behavior
   protected boolean endTrackAfterBadFrames_;
   protected int endTrackAfterNBadFrames_;

   // Special setting for Stefan
   protected boolean skipChannels_ = false;  // whether or not to skip channels
   protected int[] channelsToSkip_;   // the channels that we should not analyze

   protected boolean fixWidth_ = false; // when true, do not fit the width of the peak but use given number
   protected double widthNm_ = 250.0;   // Width of Gaussian to use in fit

   protected boolean stop_ = false;


   protected void print(String myText) {
      ij.IJ.log(myText);
   }


   public void setHalfBoxSize(int hs) {
      halfSize_ = hs;
   }

   public int getHalfBoxSize() {
      return halfSize_;
   }

   public void setNoiseTolerance(int n) {
      noiseTolerance_ = n;
   }

   public int getNoiseTolerance() {
      return noiseTolerance_;
   }

   public void setPhotonConversionFactor(double f) {
      photonConversionFactor_ = f;
   }

   public double getPhotonConversionFactor() {
      return photonConversionFactor_;
   }

   public void setGain(double f) {
      gain_ = f;
   }

   public double getGain() {
      return gain_;
   }

   public void setPixelSize(float f) {
      pixelSize_ = f;
   }

   public float getPixelSize() {
      return pixelSize_;
   }

   public void setZStackStepSize(float f) {
      zStackStepSize_ = f;
   }

   public float getZStackStepSize() {
      return zStackStepSize_;
   }

   public void setTimeIntervalMs(double f) {
      timeIntervalMs_ = f;
   }

   public double getTimeIntervalMs() {
      return timeIntervalMs_;
   }

   public void setSigmaMin(double f) {
      widthMin_ = f;
   }

   public double getSigmaMin() {
      return widthMin_;
   }

   public void setSigmaMax(double f) {
      widthMax_ = f;
   }

   public double getSigmaMax() {
      return widthMax_;
   }

   public void setUseWidthFilter(boolean filter) {
      useWidthFilter_ = filter;
   }

   public boolean getUseWidthFilter() {
      return useWidthFilter_;
   }

   public void setNrPhotonsMin(double min) {
      nrPhotonsMin_ = min;
   }

   public double getNrPhotonsMin() {
      return nrPhotonsMin_;
   }

   public void setNrPhotonsMax(double max) {
      nrPhotonsMax_ = max;
   }

   public double getNrPhotonsMax() {
      return nrPhotonsMax_;
   }

   public void setUseNrPhotonsFilter(boolean filter) {
      useNrPhotonsFilter_ = filter;
   }

   public boolean getUseNrPhotonsFilter() {
      return useNrPhotonsFilter_;
   }

   public void setMaxIterations(int maxIter) {
      maxIterations_ = maxIter;
   }

   public int getMaxIterations() {
      return maxIterations_;
   }

   public void setShape(int shape) {
      shape_ = shape;
   }

   public int getShape() {
      return shape_;
   }

   public void setFitMode(int fitMode) {
      fitMode_ = fitMode;
   }

   public int getFitMode() {
      return fitMode_;
   }

   public void setBaseLevel(double baseLevel) {
      baseLevel_ = baseLevel;
   }

   public double getBaseLevel() {
      return baseLevel_;
   }

   public void setReadNoise(double readNoise) {
      readNoise_ = readNoise;
   }

   public double getReadNoise() {
      return readNoise_;
   }

   public void setEndTrackBool(boolean endTrack) {
      endTrackAfterBadFrames_ = endTrack;
   }

   public boolean getEndTrackBool() {
      return endTrackAfterBadFrames_;
   }

   public void setEndTrackAfterNFrames(int nFrames) {
      endTrackAfterNBadFrames_ = nFrames;
   }

   public int getEndTrackAfterNFrames() {
      return endTrackAfterNBadFrames_;
   }

   public void setSkipChannels(boolean skip) {
      skipChannels_ = skip;
   }

   public boolean getSkipChannels() {
      return skipChannels_;
   }

   public void setChannelsToSkip(int[] c2s) {
      channelsToSkip_ = c2s;
   }

   public int[] getChannelsToSkip() {
      return channelsToSkip_;
   }

   public void setUseFixedWidth(boolean fixWidth) {
      fixWidth_ = fixWidth;
   }

   public boolean getUseFixedWidth() {
      return fixWidth_;
   }

   public void setFixedWidthNm(double width) {
      widthNm_ = width;
   }

   public double getFixedWidthNm() {
      return widthNm_;
   }

   public void copy(GaussianInfo source) {
      setBaseLevel(source.getBaseLevel());
      setChannelsToSkip(source.getChannelsToSkip());
      setEndTrackAfterNFrames(source.getEndTrackAfterNFrames());
      setEndTrackBool(source.getEndTrackBool());
      setFitMode(source.getFitMode());
      setFixedWidthNm(source.getFixedWidthNm());
      setGain(source.getGain());
      setHalfBoxSize(source.getHalfBoxSize());
      setNoiseTolerance(source.getNoiseTolerance());
      setNrPhotonsMax(source.getNrPhotonsMax());
      setNrPhotonsMin(source.getNrPhotonsMin());
      setPhotonConversionFactor(source.getPhotonConversionFactor());
      setPixelSize(source.getPixelSize());
      setReadNoise(source.getReadNoise());
      setShape(source.getShape());
      setSigmaMax(source.getSigmaMax());
      setSigmaMin(source.getSigmaMin());
      setSkipChannels(source.getSkipChannels());
      setTimeIntervalMs(source.getTimeIntervalMs());
      setUseFixedWidth(source.getUseFixedWidth());
      setUseNrPhotonsFilter(source.getUseNrPhotonsFilter());
      setUseWidthFilter(source.getUseWidthFilter());
      setZStackStepSize(source.getZStackStepSize());
      setMaxIterations(source.getMaxIterations());
   }

}
