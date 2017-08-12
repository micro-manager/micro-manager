
package org.micromanager.asidispim.Utils;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.measure.Calibration;
import ij.measure.ResultsTable;
import ij.plugin.ZProjector;
import ij.plugin.filter.Analyzer;
import ij.process.FHT;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import java.awt.Point;
import java.awt.geom.Point2D;

import mmcorej.TaggedImage;
import org.micromanager.acquisition.MMAcquisition;
import org.micromanager.utils.ImageUtils;

import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;


/**
 *
 * @author Nico
 */
public class SamplePositionDetector {
   
   
   private enum Axis {X, Y, Z}
   

   /**
    * Calculates the sample of mass (using pixel coordinates) of the 3D volume of
    * the given channel and position in this acquisition.
    * Returns the center of mass in pixel coordinates
    * @param acq  - Micro-Manager acquisition for which we want to know the center
    *                of mass
    * @param ch   - Channel to be used
    * @param pos  - position to be used
    * @return     - Center of mass in pixel coordinates
    */
   public static Vector3D getCenter(final MMAcquisition acq, final int ch, final int pos) {
      ImageStack tmpStack = getStack(acq, acq.getLastAcquiredFrame(), ch, pos);
      return centerOfMass(tmpStack, 450);
   }
   
   /**
    * Calculates the displacement (in microns) between object in frame and
    * frame -1 by projecting the stacks on the X, Y, and Z axis, Phase
    * Correlation of these 3 sets of 2D images (see 
    * https://en.wikipedia.org/wiki/Phase_correlation), using the ImageJ 
    * FD Math method, calculating the position of the maxima, and averaging
    * the two measurements.  
    * Projections are sized to be a square of a power of 2 (i.e. 256x256, 1024, 1024).
    * For best and fastest results, all sizes in the stack should be a power of 2.
    * Returns a vector indicating the displacement (in microns) between the two frames.
    * @param acq - Micro-Manager acquisition to be analyzed
    * @param currentFrame - Frame number to be analyzed.  
    * @param previousFrame - Frame to compare to
    * @param ch - Channel in the acquisition to be used
    * @param pos - Position in the acquistion to be used
    * @param pixelSize - PixelSize in the XY plane
    * @param zStep - Stepsize between Z planes
    * @return - Vector showing the object displacement in microns
    */
   public static Vector3D getDisplacementUsingIJPhaseCorrelation(
           MMAcquisition acq, final int currentFrame, final int previousFrame, 
           final int ch, final int pos, final double pixelSize, 
           final double zStep) {
      
      Vector3D zeroVector = new Vector3D (0.0, 0.0, 0.0);
      
      // input sanitation
      if (currentFrame <= previousFrame || previousFrame < 0 || 
              currentFrame > acq.getFrames()) {
         return zeroVector; // TODO: throw an exception instead?
      }
      ImageStack stackCurrent = getStack(acq, currentFrame, ch, pos);
      ImageStack stackPrevious = getStack(acq, currentFrame - 1, ch, pos);
      
      Calibration xyCal = new Calibration();
      xyCal.pixelWidth = pixelSize;
      xyCal.pixelHeight = pixelSize;
      xyCal.setUnit("micron");
      ImagePlus XYProjectionCurrent = project(stackCurrent, Axis.Z);
      XYProjectionCurrent.setCalibration(xyCal);
      ImagePlus XYProjectionPrevious = project(stackPrevious, Axis.Z);
      XYProjectionPrevious.setCalibration(xyCal);
      
      Calibration xzCal = new Calibration();
      xzCal.pixelWidth = pixelSize;
      xzCal.pixelHeight = zStep;
      xzCal.setUnit("micron");
      ImagePlus XZProjectionCurrent = project(stackCurrent, Axis.Y);
      XZProjectionCurrent.setCalibration(xzCal);
      ImagePlus XZProjectionPrevious = project(stackPrevious, Axis.Y);
      XZProjectionPrevious.setCalibration(xzCal);
      
      Calibration yzCal = new Calibration();
      yzCal.pixelWidth = zStep;
      yzCal.pixelHeight = pixelSize;
      yzCal.setUnit("micron");
      ImagePlus YZProjectionCurrent = project(stackCurrent, Axis.X);
      YZProjectionCurrent.setCalibration(yzCal);
      ImagePlus YZProjectionPrevious = project(stackPrevious, Axis.X);
      YZProjectionPrevious.setCalibration(yzCal);

      ImagePlus[] ips = {XYProjectionCurrent, XYProjectionPrevious, 
                        XZProjectionCurrent, XZProjectionPrevious,
                        YZProjectionCurrent, YZProjectionPrevious};
      for (ImagePlus ip : ips) {
         IJ.setAutoThreshold(ip, "Default dark");
         IJ.run(ip, "Convert to Mask", "");
         if (!isPowerOf2Size(ip)) {
            int newSize = nextPowerOf2Size(ip);
            IJ.run(ip, "Size...", "width=" + newSize + " height=" + newSize + 
                    " average interpolation=Bilinear");
         }
      }          
      
      ImagePlus XYCorr = doCorrelation(XYProjectionCurrent, XYProjectionPrevious);
      //XYCorr.show();
      ImagePlus XZCorr = doCorrelation(XZProjectionCurrent, XZProjectionPrevious);
      //XZCorr.show();
      ImagePlus YZCorr = doCorrelation(YZProjectionCurrent, YZProjectionPrevious);
      //YZCorr.show();
     
      Point xyMov = coordinateOfMax(XYCorr);
      Point2D xyMovM = new Point2D.Double( 
              (xyMov.x - XYCorr.getWidth() / 2) * XYCorr.getCalibration().pixelWidth,
              (xyMov.y - XYCorr.getHeight() / 2) * XYCorr.getCalibration().pixelHeight );
      Point xzMov = coordinateOfMax(XZCorr);
      Point2D xzMovM = new Point2D.Double (
              (xzMov.x - XZCorr.getWidth() / 2) * XZCorr.getCalibration().pixelWidth,
              (xzMov.y - XZCorr.getHeight() / 2) * XZCorr.getCalibration().pixelHeight);
      Point yzMov = coordinateOfMax(YZCorr);
      Point2D yzMovM = new Point2D.Double (
              (yzMov.x - YZCorr.getWidth() / 2) * YZCorr.getCalibration().pixelWidth,
              (yzMov.y - YZCorr.getHeight() / 2) * YZCorr.getCalibration().pixelHeight);
      
      System.out.println("X: " + xyMovM.getX() + ", Y: " + xyMovM.getY());
      System.out.println("X: " + xzMovM.getX() + ", Z: " + xzMovM.getY());
      System.out.println("Z: " + yzMovM.getX() + ", Y: " + yzMovM.getY());
      
      // Average the information from the different planes
      // TODO: evaluate weighing XY plane more than Z projections
      Vector3D movement = new Vector3D( 
              (xyMovM.getX() + xzMovM.getX()) / 2.0,
              (xyMovM.getY() + yzMovM.getY()) / 2.0,
              (xzMovM.getY() + yzMovM.getX()) / 2.0);
      
      return movement;
      
   }
   
   
   /**
    * Returns an ImageJ stack for the given frame, channel and position in this
    * Micro-manager acquisition
    * @param acq   - Micro-Manager dataset
    * @param ch    - Channel for which we want the stack
    * @param pos   - Position for which we want the stack
    * @return      - ImageJ stack containing a volume in one channel at one position
    */
   private static ImageStack getStack (final MMAcquisition acq, final int frame, 
           final int ch, final int pos) {
      final ImageStack stack = new ImageStack (acq.getWidth(), acq.getHeight());
      for (int z=0; z < acq.getSlices(); z++) {
         TaggedImage tImg = acq.getImageCache().getImage(ch, z, frame, pos);
         ImageProcessor processor = ImageUtils.makeProcessor(tImg);
         stack.addSlice(processor);
      }
      return stack;
   }
   
   /**
    * Does a sum of slices projection in the requested orientation
    * Returns an Imageplus with a FloatProcessor
    * Only works reliably with 16-bit (short) input images.
    * @param stack - Input stack to be projected
    * @param axis - Axis along which the desired projection should executed,
    *       Z -returns an XY projection,
    *       X - return a YZ projection
    *       Y - returns a XZ projection
    * @return 
    */
   private static ImagePlus project(ImageStack stack, final Axis axis) {

      if (null != axis) {
         switch (axis) {
            case Z:
               ZProjector zp = new ZProjector(new ImagePlus("tmp", stack));
               zp.doHyperStackProjection(false);
               zp.setMethod(ZProjector.MAX_METHOD);
               zp.doProjection();
               return zp.getProjection();
            case X: {
               // TODO: this only work for 16-bit (short) input images

               // do a sideways sum
               IJ.showStatus("XProjection");
               ImageProcessor projProc = new FloatProcessor(stack.getSize(), stack.getHeight());
               float[] fpixels = (float[]) projProc.getPixels();
               for (int slice = 1; slice <= stack.getSize(); slice++) {
                  IJ.showProgress(slice, stack.getSize());
                  IJ.showStatus("XProjection " + slice + "/" + stack.getSize());
                  short[] pixels = (short[]) stack.getProcessor(slice).getPixels();
                  for (int y = 0; y < stack.getHeight(); y++) {
                     int index = y * stack.getHeight();
                     int fIndex = y * stack.getSize() + (slice - 1);
                     for (int x = 0; x < stack.getWidth(); x++) {
                        fpixels[fIndex] += pixels[index + x] & 0xffff;
                     }
                  }
               }
               // not needed, but avoids Java warnings about fpixels being unused
               projProc.setPixels(fpixels);
               return new ImagePlus("YZProjection", projProc);
            }
            case Y: {
               // TODO: this only work for 16-bit (short) input images
               // do a sideways sum
               IJ.showStatus("YProjection");
               ImageProcessor projProc = new FloatProcessor(stack.getWidth(), stack.getSize());
               float[] fpixels = (float[]) projProc.getPixels();
               for (int slice = 1; slice <= stack.getSize(); slice++) {
                  IJ.showProgress(slice, stack.getSize());
                  IJ.showStatus("YProjection " + slice + "/" + stack.getSize());
                  short[] pixels = (short[]) stack.getProcessor(slice).getPixels();
                  for (int x = 0; x < stack.getWidth(); x++) {
                     int fIndex = (slice - 1) * stack.getWidth() + x;
                     for (int y = 0; y < stack.getHeight(); y++) {
                        fpixels[fIndex] += pixels[x + y * stack.getWidth()] & 0xffff;
                     }
                  }
               }
               // not needed, but avoids Java warnings about fpixels being unused
               projProc.setPixels(fpixels);
               return new ImagePlus("XZProjection", projProc);
            }
            default:
               break;
         }
      }
      // else null axis (todo?)
      return null;

   }
   
   
   /**
    * Find the pixel coordinates of the pixel with the highest intensity in this image
    * Only works with images of type Float
    * @param img - input image (better has a FLoatprocessor
    * @return - x, y coordinates (in pixels) of pixel with highest intensity.
    *          If input image did not have floatprocessor, the zero coordinates 
    *          will be returned.
    */
   private static Point coordinateOfMax(ImagePlus img) {
      Point result = new Point(0, 0);
      
      ImageProcessor iProc = img.getProcessor();
      if (iProc instanceof FloatProcessor) {
         float[] pixels = (float[]) iProc.getPixels();
         float max = pixels[0];
         int maxIndex = 0;
         for (int i = 1; i < pixels.length; i++) {
            if (pixels[i] > max) {
               max = pixels[i];
               maxIndex = i;
            }
         }
         result.x = maxIndex % img.getWidth();
         result.y = maxIndex / img.getWidth();
      }
      
      return result;
   }
   
   /**
    * Find the averaged centroid xy position of the large objects in the image
    * Thresholds the image, closes gaps, and find objects larger than 50 micron^2
    * Averages the position of their centroids
    * @param projection
    * @return 
    */
   private static Point2D centralXYPos (ImagePlus projection) {
      IJ.setAutoThreshold(projection, "Li dark");
      IJ.run(projection, "Convert to Mask", "method=Li background=Dark black");
      IJ.run(projection, "Options...", "iterations=2 count=3 black pad edm=Overwrite do=Close");

      IJ.run("Set Measurements...", "center redirect=None decimal=2");
      // NS: I don't know why, but it seems that ImageJ uses pixel units rather than 
      // micron^2, as it is supposed to.  Take no chances, and force it to use
      // pixels^2 and convert ourselves.
      double minSize = 50.0;
      minSize /= 0.165;  // TODO: get pixel Width correctly
      minSize /= 0.165;  
      IJ.run(projection, "Measure", "");
      
      projection.show();
      
      ResultsTable rt = Analyzer.getResultsTable();
      
      double x = 0.0;
      double y = 0.0;
      int numRows = rt.getCounter();
      for (int i = 0; i < numRows; i++) {
         x += rt.getValue("X", i);
         y += rt.getValue("Y", i);
      }
      x /= numRows;
      y /= numRows;
   
      return new Point2D.Double(x, y);
   }
   
   private static Vector3D centerOfMass(ImageStack stack, int offset) {
      int depth = stack.getSize();
      ImageProcessor[] imgProcs = new ImageProcessor[stack.getSize()];
      for (int i = 0; i < stack.getSize(); i++) {
         imgProcs[i] = stack.getProcessor(i+1);
      }
      
      double totalMass = 0.0;
      double totalX = 0.0;
      double totalY = 0.0;
      double totalZ = 0.0;
      for (int z = 0; z < depth; z++) {
         for (int y = 0; y < stack.getHeight(); y++) {
            for (int x = 0; x < stack.getWidth(); x++) {
               int mass = imgProcs[z].get(x, y) - offset;
               if (mass < 0) {
                  mass = 0;
               }
               totalMass += mass;
               totalX += x * mass;
               totalY += y * mass;
               totalZ += z * mass;
            }
         }
      }
      Vector3D centerOfMass = new Vector3D(
            totalX / totalMass,
            totalY / totalMass,
            totalZ / totalMass);
      
      return centerOfMass;
   }
   
   /**
    * Copied from ImageJ1 source code (FFMath) so that we can return an ImagePlus
    * and not show it.
    * Now hard coded to do a conjugate multiplication and inverse
    * Note that both input images should have identical height and width 
    * that both should be a power of 2.
    * @param imp1 - First ImagePlus
    * @param imp2 - Second input ImagePlus
    * @return - Inverse FFT of conjugate multiplication
    */
   private static ImagePlus doCorrelation(ImagePlus imp1, ImagePlus imp2) {
      final int CONJUGATE_MULTIPLY = 0, MULTIPLY = 1, DIVIDE = 2;
      final int operation = 0;
      final boolean doInverse = true;

      FHT h1, h2 = null;
      ImageProcessor fht1, fht2;
      fht1 = (ImageProcessor) imp1.getProperty("FHT");
      if (fht1 != null) {
         h1 = new FHT(fht1);
      } else {
         IJ.showStatus("Converting to float");
         ImageProcessor ip1 = imp1.getProcessor();
         h1 = new FHT(ip1);
      }
      fht2 = (ImageProcessor) imp2.getProperty("FHT");
      if (fht2 != null) {
         h2 = new FHT(fht2);
      } else {
         ImageProcessor ip2 = imp2.getProcessor();
         if (imp2 != imp1) {
            h2 = new FHT(ip2);
         }
      }
      if (!h1.powerOf2Size()) {
         IJ.error("FFT Math", "Images must be a power of 2 size (256x256, 512x512, etc.)");
         return null;
      }
      if (imp1.getWidth() != imp2.getWidth()) {
         IJ.error("FFT Math", "Images must be the same size");
         return null;
      }
      if (fht1 == null) {
         IJ.showStatus("Transform image1");
         h1.transform();
      }
      if (fht2 == null) {
         if (h2 == null) {
            h2 = new FHT(h1.duplicate());
         } else {
            IJ.showStatus("Transform image2");
            h2.transform();
         }
      }
      FHT result = null;
      switch (operation) {
         case CONJUGATE_MULTIPLY:
            IJ.showStatus("Complex conjugate multiply");
            result = h1.conjugateMultiply(h2);
            break;
         case MULTIPLY:
            IJ.showStatus("Fourier domain multiply");
            result = h1.multiply(h2);
            break;
         case DIVIDE:
            IJ.showStatus("Fourier domain divide");
            result = h1.divide(h2);
            break;
      }
      ImagePlus imp3 = null;
      if (doInverse && result != null) {
         IJ.showStatus("Inverse transform");
         result.inverseTransform();
         IJ.showStatus("Swap quadrants");
         result.swapQuadrants();
         IJ.showStatus("Display image");
         result.resetMinAndMax();
         imp3 = new ImagePlus("Corr", result);
      } else if (result != null) {
         IJ.showStatus("Power spectrum");
         ImageProcessor ps = result.getPowerSpectrum();
         imp3 = new ImagePlus("Power Spectrum", ps.convertToFloat());
         result.quadrantSwapNeeded = true;
         imp3.setProperty("FHT", result);
      }
      if (imp3 != null) {
         Calibration cal1 = imp1.getCalibration();
         Calibration cal2 = imp2.getCalibration();
         Calibration cal3 = cal1.scaled() ? cal1 : cal2;
         if (cal1.scaled() && cal2.scaled() && !cal1.equals(cal2)) {
            cal3 = null;                //can't decide between different calibrations
         }
         imp3.setCalibration(cal3);
         cal3 = imp3.getCalibration();   //imp3 has a copy, which we may modify
         cal3.disableDensityCalibration();
      }
      IJ.showProgress(1.0);

      return imp3;
   }

   private static boolean isPowerOf2Size(ImagePlus img) {
		int i=2;
		while(i<img.getWidth()) {
         i *= 2;
      }
      return i==img.getWidth() && img.getWidth()==img.getHeight();
	}
   
   private static int nextPowerOf2Size(ImagePlus img) {
      int w = 2;
      while (w < img.getWidth()) {
         w *= 2;
      }
      while (w < img.getHeight()) {
         w *=2;
      }
      return w;
   }

   
}
