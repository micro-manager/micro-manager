/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package acq;

import acq.FixedAreaAcquisition;
import acq.MultiResMultipageTiffStorage;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ByteProcessor;
import ij.process.ImageConverter;
import ij.process.ShortProcessor;
import ij3d.image3d.FHTImage3D;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.math.ArgumentOutsideDomainException;
import org.apache.commons.math.analysis.interpolation.SplineInterpolator;
import org.apache.commons.math.analysis.polynomials.PolynomialSplineFunction;
import org.micromanager.MMStudio;
import org.micromanager.utils.ReportingUtils;

/**
 *
 * @author Henry
 */
public class CrossCorrelationAutofocus {

   //number of interppolated points per z step
   private static final double SPLINE_PRECISION = 100;
   //5e6--13 s
   //1e7--37 s
   //4e7--92 s
   //1e8--92 s
   //8e8--682 s
   private static final int NUM_VOXEL_TARGET = 100000000;
   
   
   private final int channelIndex_;
   private final double maxDisplacement_;
   private double initialPosition_;
   private double currentPosition_;
   private FixedAreaAcquisition acq_;
   private String zDevice_;
   private int downsampleIndex_;
   private int downsampledWidth_;
   private int downsampledHeight_;
   
   
   public CrossCorrelationAutofocus(FixedAreaAcquisition acq, int channelIndex, double maxDisplacement, String zDevice) {
      channelIndex_ = channelIndex;
      maxDisplacement_ = maxDisplacement;
      zDevice_ = zDevice;
      acq_ = acq;
   }
   

   public double getAutofocusPosition() {
      return currentPosition_;
   }
    
   public static void runSecretSerialAutofocus(FixedAreaAcquisition refAcq, FixedAreaAcquisition currentAcq, double initalPos) {    
      ImageStack tp0Stack = createAFStack(refAcq, 0, 0, refAcq.getStorage().getTileWidth(), refAcq.getStorage().getTileHeight(), 0);
      ImageStack currentTPStack = createAFStack(currentAcq,  0, 0, refAcq.getStorage().getTileWidth(), refAcq.getStorage().getTileHeight(), 0);
      //run autofocus
      double drift = calcFocusDrift(tp0Stack, currentTPStack, refAcq.getZStep());
      
      if (Math.abs(drift) > refAcq.getSettings().autofocusMaxDisplacemnet_um_) {
         ReportingUtils.showError("Focus drift exceeds max displacement");
         return;
      }
      try {
         //move autofocus focus
         MMStudio.getInstance().getCore().setPosition(refAcq.getSettings().autoFocusZDevice_, initalPos - drift);
      } catch (Exception ex) {
         ReportingUtils.showError("Coulndt move secret autofocus drive");
      }
   }
   
   /**
    * Called by acquisitions at then end of time point
    * @param timeIndex 
    */
   public void run(int timeIndex) throws Exception {
      if (timeIndex == 0) {
         //get initial position
         try {
            initialPosition_ = MMStudio.getInstance().getCore().getPosition(zDevice_);
            currentPosition_ = initialPosition_;
         } catch (Exception e) {
            ReportingUtils.showError("Couldn't get autofocus Z drive initial position");
         }
         //figure out which resolution level will be used for xCorr
         MultiResMultipageTiffStorage storage = acq_.getStorage();
         int fullResPixelWidth = ((FixedAreaAcquisition) acq_).getNumColumns() * storage.getTileWidth();
         int fullResPixelHeight = ((FixedAreaAcquisition) acq_).getNumRows() * storage.getTileHeight();
         //figure out how much downsampling needed to run autofocus in a reasonable amount of time
         //factor of two is for z padding
         double dsFactor = Math.sqrt(fullResPixelWidth * fullResPixelHeight * 2*(acq_.getMaxSliceIndex() + 1) / (double) NUM_VOXEL_TARGET);
         downsampleIndex_ = (int) Math.max(0, Math.ceil(Math.log10(dsFactor) / Math.log(2)));
         downsampledWidth_ = (int) (fullResPixelWidth / Math.pow(2, downsampleIndex_));
         downsampledHeight_ = (int) (fullResPixelHeight / Math.pow(2, downsampleIndex_));
         return;
      }      
      
      ImageStack tp0Stack = createAFStack(acq_, 0, channelIndex_, downsampledWidth_, downsampledHeight_, downsampleIndex_);
      ImageStack currentTPStack = createAFStack(acq_, timeIndex, channelIndex_, downsampledWidth_, downsampledHeight_, downsampleIndex_);
      //run autofocus
      double drift = calcFocusDrift(tp0Stack, currentTPStack, acq_.getZStep());

      //check if outside max displacement
      if (Math.abs(currentPosition_ - drift - initialPosition_) > maxDisplacement_) {
         IJ.log("Calculated focus drift of " + drift + " um exceeds tolerance. Leaving autofocus offset unchanged");
         return;
      }
      currentPosition_ -= drift;
   }
   
   private static ImageStack createAFStack(FixedAreaAcquisition acq, int timeIndex, int channelIndex, int width, int height, int dsIndex) {
      //pad by half of stack size in either direction so wrap around of xCorr doesnt give false values
      int numSlices = 2*(acq.getMaxSliceIndex() + 1);
      ImageStack stack = new ImageStack(width, height);
      int frontPadding = (acq.getMaxSliceIndex() + 1) / 2;
      for (int slice = 0; slice < numSlices; slice++) {
         if (slice >= frontPadding && slice < frontPadding + acq.getMaxSliceIndex() + 1) {
            int dataSlice = slice - frontPadding;
            stack.addSlice(null, acq.getStorage().getImageForDisplay(channelIndex, dataSlice, timeIndex, dsIndex, 0, 0, width, height).pix);
         } else {
            //add dummy slices
            if (MMStudio.getInstance().getCore().getBytesPerPixel() == 1) {
               stack.addSlice(new ByteProcessor(width, height));
            } else {
               stack.addSlice(new ShortProcessor(width, height));
            }
         }
      }
      return stack;
   }

   /**
    *
    * @param original
   * @param current
   * @param pixelSizeZ
   * @return double representing the focus position of current relative to original (i.e. 4 means
   * that current is focused 4 um deeper than current)
   */
   private static double calcFocusDrift(ImageStack tp0Stack, ImageStack currentTPStack, double pixelSizeZ) {    
      ImagePlus original = new ImagePlus("Timepoint0",tp0Stack);
      ImagePlus current = new ImagePlus("Current timepoint" ,currentTPStack);
//      long start = System.currentTimeMillis();
      //convert to 32 bit floats
      new ImageConverter(original).convertToGray32();
      new ImageConverter(current).convertToGray32();
      ImageStack xCorrStack = FHTImage3D.crossCorrelation(original.getStack(), current.getStack());
      ImagePlus xCorr = new ImagePlus("XCorr", xCorrStack);      
//      System.out.println("Time to generate xCorr: " + ((System.currentTimeMillis() - start)/1000) + " s");       
      
      //find the maximum cross correlation intensity at each z slice
      double[] ccIntensity = new double[xCorr.getNSlices()], interpolatedCCMax = new double[xCorr.getNSlices()];
      for (int i = 1; i <= ccIntensity.length; i++) {
         xCorr.setSlice(i);
         ccIntensity[i - 1] = xCorr.getStatistics(ImagePlus.MIN_MAX).max;        
         interpolatedCCMax[i - 1] = i - 1;
      }
      //find maximum value of interpolated spline function
      PolynomialSplineFunction func = new SplineInterpolator().interpolate(interpolatedCCMax, ccIntensity);
      double[] sliceIndexInterpolationPoints = new double[(int) (SPLINE_PRECISION * (interpolatedCCMax.length - 1))];
      int maxIndex = 0;
      for (int i = 0; i < sliceIndexInterpolationPoints.length; i++) {
         sliceIndexInterpolationPoints[i] = i / SPLINE_PRECISION;
         try {
            if (func.value(sliceIndexInterpolationPoints[i]) > func.value(sliceIndexInterpolationPoints[maxIndex])) {
               maxIndex = i;
            }
         } catch (ArgumentOutsideDomainException ex) {
            ReportingUtils.showError("Spline value calculation outside range");
         }
      }
      //get maximum value of xCorr in slice index units
      double ccMaxSliceIndex = sliceIndexInterpolationPoints[maxIndex];
      //convert to um
      double drift_um = (ccMaxSliceIndex - (((double) xCorr.getNSlices()) / 2.0)) * pixelSizeZ;

      original.close();
      current.close();
      xCorr.close();
      
      return drift_um;
   }


 
   
}
