/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package autofocus;

import acq.FixedAreaAcquisition;
import acq.MultiResMultipageTiffStorage;
import ij.ImagePlus;
import ij.ImageStack;
import ij3d.image3d.FHTImage3D;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import main.Magellan;
import misc.Log;
import org.apache.commons.math.ArgumentOutsideDomainException;
import org.apache.commons.math.analysis.interpolation.SplineInterpolator;
import org.apache.commons.math.analysis.polynomials.PolynomialSplineFunction;

/**
 *
 * @author Henry
 */
public class CrossCorrelationAutofocus {

   //number of interppolated points per z step
   private static final double SPLINE_PRECISION = 10;
   //5e6--13 s
   //1e7--37 s
   //4e7--92 s
   //1e8--92 s
   //8e8--682 s
   private static final int NUM_VOXEL_TARGET = 20000000; //this target shuld take 1-2 min to calculate, while maintaining images of a resonable size
   
   
   private final int channelIndex_;
   private final double maxDisplacement_;
   private double initialPosition_;
   private double currentPosition_;
   private FixedAreaAcquisition acq_;
   private int downsampleIndex_;
   private int downsampledWidth_;
   private int downsampledHeight_;
   
   
   public CrossCorrelationAutofocus(FixedAreaAcquisition acq, int channelIndex, double maxDisplacement , double initialPosition) {
      channelIndex_ = channelIndex;
      maxDisplacement_ = maxDisplacement;
      acq_ = acq;
      initialPosition_ = initialPosition;      
   }
 
//   public static void debug() {
//      ImagePlus tp0 = new ImagePlus("C:/Users/Henry/Desktop/tp0.tif");
//      ImagePlus tpc = new ImagePlus("C:/Users/Henry/Desktop/tp4.tif");
//      ImageStack stack1 = createAFStackFromDisk(tp0);
//      ImageStack stack2 = createAFStackFromDisk(tpc); 
//      double drift = calcFocusDrift("test", stack1, stack2, 4.5);
//      System.out.println(drift);
//   }
//
//   //for debug
//   private static ImageStack createAFStackFromDisk(ImagePlus ip) {
//      //pad by half of stack size in either direction so wrap around of xCorr doesnt give false values 
//      ImageStack stack = new ImageStack(ip.getWidth(), ip.getHeight());
//      int numSlices = (ip.getNSlices());
//      int frontPadding = 0;
//      //get background pixel value 
//      int backgroundVal = 9;
//      for (int slice = 0; slice < numSlices; slice++) {
//         int dataSlice = slice - frontPadding;
//         //add as int              
//         byte[] pix = (byte[]) ip.getStack().getPixels(dataSlice + 1);
//         float[] pix32 = new float[pix.length];
//         for (int i = 0; i < pix.length; i++) {
//            pix32[i] = pix[i] == 0 ? backgroundVal : pix[i] & 0xff;
//         }
//         stack.addSlice(null, pix32);
//      }
//      return stack;
//   }

   public double getAutofocusPosition() {
      return currentPosition_;
   }
   
   /**
    * Called by acquisitions at then end of time point
    * @param timeIndex 
     */
    public void run(int timeIndex) throws Exception {
        Log.log("________", true);
        Log.log("Autofocus for acq " + acq_.getName() + "  Time point " + timeIndex, true);
        if (timeIndex == 0) {
            //get initial position
            try {
                currentPosition_ = initialPosition_;
            } catch (Exception e) {
               Log.log("Couldn't get autofocus Z drive initial position", true);
            }
            //figure out which resolution level will be used for xCorr
            MultiResMultipageTiffStorage storage = acq_.getStorage();
            //do these calulations with BigIntegers to prevent overflow and Nan values
            BigInteger tileWidth = new BigInteger(storage.getTileWidth() + "");
            BigInteger tileHeight = new BigInteger(storage.getTileHeight() + "");
            BigInteger numCols = new BigInteger(((FixedAreaAcquisition) acq_).getNumColumns() + "");
            BigInteger numRows = new BigInteger(((FixedAreaAcquisition) acq_).getNumRows() + "");
            //figure out how much downsampling needed to run autofocus in a reasonable amount of time
            //factor of two is for z padding
            BigDecimal numPix2D = new BigDecimal(tileWidth.multiply(numCols).multiply(tileHeight).multiply(numRows));
            BigDecimal numXCorrSlices = new BigDecimal((acq_.getMaxSliceIndex() + 1) + "");
            double dsFactor = //ratio of number of voxels to voxel target
                    Math.sqrt(numPix2D.multiply(numXCorrSlices).divide(new BigDecimal((double) NUM_VOXEL_TARGET), 
                    RoundingMode.UP).doubleValue());
            downsampleIndex_ = (int) Math.max(0, Math.round(Math.log(dsFactor) / Math.log(2)));
            downsampledWidth_ = (int) (tileWidth.multiply(numCols).longValue() / Math.pow(2, downsampleIndex_));
            downsampledHeight_ = (int) (tileHeight.multiply(numRows).longValue() / Math.pow(2, downsampleIndex_));
            Log.log("Autofocus DS Index: " + downsampleIndex_, true);
            Log.log("Autofocus DS Width: " + downsampledWidth_, true);
            Log.log("Autofocus DS Height: " + downsampledHeight_, true);
            return;
        }

        ImageStack tp0Stack = createAFStack(acq_, 0, channelIndex_, downsampledWidth_, downsampledHeight_, downsampleIndex_);
        ImageStack currentTPStack = createAFStack(acq_, timeIndex, channelIndex_, downsampledWidth_, downsampledHeight_, downsampleIndex_);
        //run autofocus
        double drift = calcFocusDrift(acq_.getName(), tp0Stack, currentTPStack, acq_.getZStep());
        //check if outside max displacement
        if (Math.abs(currentPosition_ - drift - initialPosition_) > maxDisplacement_) {
            Log.log("Calculated focus drift of " + drift + " um exceeds tolerance. Leaving autofocus offset unchanged", true);
            return;
        } else {
           Log.log(acq_.getName() + " Autofocus: calculated drift of " + drift, true);
           Log.log( "New position: " + (currentPosition_ - drift), true);
        }
        currentPosition_ -= drift;
    }

    /**
     * return a 32 bit for use with cross corr autofocus
     *

    * @return
    */
   private static ImageStack createAFStack(FixedAreaAcquisition acq, int timeIndex, int channelIndex, int width, int height, int dsIndex) {
      ImageStack stack = new ImageStack(width, height);
      //get background pixel value
      for (int slice = 0; slice < acq.getMaxSliceIndex() + 1; slice++) {
         //add as int
         float[] pix32;
         if (Magellan.getCore().getBytesPerPixel() == 1) {
            byte[] pix = (byte[]) acq.getStorage().getImageForDisplay(channelIndex, slice, timeIndex, dsIndex, 0, 0, width, height).pix;
            pix32 = new float[pix.length];
            for (int i = 0; i < pix.length; i++) {
               pix32[i] = pix[i] & 0xff;
            }
         } else {
            short[] pix = (short[]) acq.getStorage().getImageForDisplay(channelIndex, slice, timeIndex, dsIndex, 0, 0, width, height).pix;
            pix32 = new float[pix.length];
            for (int i = 0; i < pix.length; i++) {
               pix32[i] = pix[i] & 0xffff;
            }
         }
         stack.addSlice(null, pix32);
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
   private static double calcFocusDrift(String acqName, ImageStack tp0Stack, ImageStack currentTPStack, double pixelSizeZ) {    
      Log.log( acqName + " Autofocus: cross correlating", true);      
      ImageStack xCorrStack = FHTImage3D.crossCorrelation(tp0Stack, currentTPStack);
      Log.log( acqName + " Autofocus: finished cross correlating..calculating drift", true);      
      ImagePlus xCorr = new ImagePlus("XCorr", xCorrStack);      
      xCorr.show();
      //find the maximum cross correlation intensity at each z slice
      double[] ccIntensity = new double[xCorr.getNSlices()], interpolatedCCMax = new double[xCorr.getNSlices()];
      for (int i = 1; i <= ccIntensity.length; i++) {
         xCorr.setSlice(i);      
         ccIntensity[i - 1] = findMaxPixelVal((float[]) xCorr.getProcessor().getPixels(), xCorr.getWidth(), xCorr.getHeight());
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
            Log.log("Spline value calculation outside range");
         }
      }
      //get maximum value of xCorr in slice index units
      double ccMaxSliceIndex = sliceIndexInterpolationPoints[maxIndex];
      //convert to um
      double drift_um = (ccMaxSliceIndex - (((double) xCorr.getNSlices()) / 2.0)) * pixelSizeZ;
      xCorr.close();
      
      return drift_um;
   }
   
   private static float findMaxPixelVal(float[] pix, int width, int height) {
      //only use central square for calulating max, because weird large values can occur on the edges of the xCorr
      float max = 0;
      for (int i = 0; i < width*height; i++) {
         if (i / width < height / 4 || i / width > 3*height / 4 ||
                 i % width  < width / 4 || i % width > 3*width/4) {
            continue; // use only central square of pixels
         }
         max = Math.max(max, pix[i]);
      }
      return max;
   }
   
}
