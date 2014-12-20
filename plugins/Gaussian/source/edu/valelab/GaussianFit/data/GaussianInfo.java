/*
 * Contains information about the gaussian fit to be performed
 * This class is not meant to be used by itself but should be inherited from
 * 
 */

package edu.valelab.GaussianFit.data;

import ij.ImagePlus;
import ij.process.ImageProcessor;
import java.util.List;
import java.util.concurrent.BlockingQueue;

/**
 *
 * @author nico
 */
public class GaussianInfo {
   protected static final Object gfsLock_ = new Object();
   protected ImagePlus siPlus_;
   protected ImageProcessor siProc_;
   protected BlockingQueue<GaussianSpotData> sourceList_;
   protected List<GaussianSpotData> resultList_;

   // half the size (in pixels) of the square used for Gaussian fitting
   protected int halfSize_ = 8;
   protected double baseLevel_ = 100; // base level of the camera in counts
   
   // settings for maximum finder
   protected int noiseTolerance_ = 100;

   // Needed to calculate # of photons and estimate error
   // the real PCF is calculated as photonConversionFactor_ * gain_
   protected double photonConversionFactor_ = 10.41;
   protected double gain_ = 50;
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
   protected int mode_;
   protected int shape_;
   protected int fitMode_;

   // Setting determinig tracking behavior
   protected boolean endTrackAfterBadFrames_;
   protected int endTrackAfterNBadFrames_;

   protected boolean stop_ = false;


   protected void print(String myText) {
      ij.IJ.log(myText);
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
   public void setPixelSize (float f) {
      pixelSize_ = f;
   }
   public double getPixelSize() {
      return pixelSize_;
   }
   public void setZStackStepSize(float f) {
      zStackStepSize_ = f;
   }
   public double getZStackStepSize() {
      return zStackStepSize_;
   }
   public void setTimeIntervalMs (double f) {
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
   public void setBoxSize(int boxSize) {
      halfSize_ = boxSize / 2;
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
   
}
