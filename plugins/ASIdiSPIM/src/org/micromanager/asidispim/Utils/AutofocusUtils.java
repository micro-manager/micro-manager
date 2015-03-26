package org.micromanager.asidispim.Utils;

import ij.ImagePlus;
import ij.process.ByteProcessor;
import ij.process.ColorProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;
import mmcorej.TaggedImage;
import org.json.JSONException;
import org.json.JSONObject;
import org.micromanager.api.ScriptInterface;
import org.micromanager.asidispim.Data.AcquisitionModes;
import org.micromanager.asidispim.Data.Devices;
import org.micromanager.asidispim.Data.MultichannelModes;
import org.micromanager.asidispim.api.ASIdiSPIMException;

/**
 *
 * @author nico
 */
public class AutofocusUtils {

   private final ScriptInterface gui_;
   private final Devices devices_;
   private final ControllerUtils controller_;

   public AutofocusUtils(ScriptInterface gui,
           Devices devices,
           ControllerUtils controller) {
      gui_ = gui;
      devices_ = devices;
      controller_ = controller;
   }

   public String getAutofocusMethod() {
      return gui_.getAutofocus().getDeviceName();
   }

   /**
    * Acquires image stack by scanning the mirror, calculates focus scores
    *
    * @param side
    * @param nrImages
    * @param start
    * @param stepSize
    * @param sliceTiming
    * @param debug debug mode will display the image sequence
    * 
    * @return position of the moving device associated with highest focus score
    * @throws org.micromanager.asidispim.api.ASIdiSPIMException
    */
   public double runFocus(
           final Devices.Sides side,
           final int nrImages,
           final double start,
           final float stepSize,
           final SliceTiming sliceTiming,
           boolean debug) throws ASIdiSPIMException {

      String camera = devices_.getMMDevice(Devices.Keys.CAMERAA);
      if (side.equals(Devices.Sides.B)) {
         camera = devices_.getMMDevice(Devices.Keys.CAMERAB);
      }

      // TODO: run this on its own thread
      controller_.prepareControllerForAquisition(
              side,
              false, // no hardware timepoints
              MultichannelModes.Keys.NONE,
              false, // do not use channels
              1, // numChannels
              nrImages, // numSlices
              1, // numTimepoints
              0, // timeInterval
              1, // numSides
              side.toString(), // firstside
              false, // useTimepoints
              AcquisitionModes.Keys.SLICE_SCAN_ONLY, // scan only the mirror
              100.0f, // delay before side (can go to 0?)
              stepSize, // stepSize of whom?
              sliceTiming);

      double[] focusScores = new double[nrImages];
      
      try {
         String acqName = "";
         if (debug) {
            acqName = gui_.getUniqueAcquisitionName("diSPIM Autofocus");
            gui_.openAcquisition(acqName, "", nrImages, 1, 1, 1, true, false);
            // initialize acquisition
            gui_.initializeAcquisition(acqName, 
                    (int) gui_.getMMCore().getImageWidth(),
                    (int) gui_.getMMCore().getImageHeight(), 
                    (int) gui_.getMMCore().getBytesPerPixel(),
                    (int) gui_.getMMCore().getImageBitDepth());
         }
         gui_.getMMCore().setCameraDevice(camera);
         gui_.getMMCore().setExposure((double) sliceTiming.cameraExposure);
         gui_.getMMCore().startSequenceAcquisition(camera, nrImages, 0, true);

         boolean success = controller_.triggerControllerStartAcquisition(
                 AcquisitionModes.Keys.SLICE_SCAN_ONLY,
                 side.equals(Devices.Sides.A));
         if (!success) {
            throw new ASIdiSPIMException("Failed to trigger controller");
         }

         long startTime = System.currentTimeMillis();
         long now = startTime;
         long timeout = 5000;  // wait 5 seconds for first image to come
         //timeout = Math.max(5000, Math.round(1.2*controller_.computeActualVolumeDuration(sliceTiming)));
         while (gui_.getMMCore().getRemainingImageCount() == 0
                 && (now - start < timeout)) {
            now = System.currentTimeMillis();
            Thread.sleep(5);
         }
         if (now - start >= timeout) {
            throw new ASIdiSPIMException(
                    "Camera did not send first image within a reasonable time");
         }

         // calculate focus scores of the acquired images, using the scoring
         // algorithm of the active autofocus device
         // Store the scores in an array
         boolean done = false;
         int counter = 0;
         while ((gui_.getMMCore().getRemainingImageCount() > 0
                 || gui_.getMMCore().isSequenceRunning(camera))
                 && !done) {
            now = System.currentTimeMillis();
            if (gui_.getMMCore().getRemainingImageCount() > 0) {  // we have an image to grab
               TaggedImage timg = gui_.getMMCore().popNextTaggedImage();
               ImageProcessor ip = makeProcessor(timg);
               focusScores[counter] = gui_.getAutofocus().computeScore(ip);
               counter++;
               if (counter >= nrImages) {
                  done = true;
               }
               if (debug) {
                  // we are using the slow way to insert images, should be OK
                  // as long as the circulat buffer is big enough
                  gui_.addImageToAcquisition(acqName, counter, 0, 0, 0, timg);
               }
            }
         }
      } catch (Exception ex) {
         throw new ASIdiSPIMException("Hardware Error while executing Autofocus");
      }
      // now find the position in the focus Score array with the highest score
      // TODO: use more sophisticated analysis here
      double highestScore = focusScores[0];
      int highestIndex = 0;
      for (int i = 1; i < focusScores.length; i++) {
         if (focusScores[i] > highestScore) {
            highestIndex = i;
            highestScore = focusScores[i];
         }
      }

      // return the position of the scanning device associated with the highest
      // focus score
      double bestScore = start + stepSize * highestIndex;
      
      return bestScore;
   }
   
   

   public static ImageProcessor makeProcessor(TaggedImage taggedImage)
           throws ASIdiSPIMException {
      final JSONObject tags = taggedImage.tags;
      try {
         return makeProcessor(getIJType(tags), tags.getInt("Width"),
                 tags.getInt("Height"), taggedImage.pix);
      } catch (JSONException e) {
         throw new ASIdiSPIMException("Error while parsing image tags");
      } 
   }

   public static ImageProcessor makeProcessor(int type, int w, int h,
           Object imgArray) throws ASIdiSPIMException {
      if (imgArray == null) {
         return makeProcessor(type, w, h);
      } else {
         switch (type) {
            case ImagePlus.GRAY8:
               return new ByteProcessor(w, h, (byte[]) imgArray, null);
            case ImagePlus.GRAY16:
               return new ShortProcessor(w, h, (short[]) imgArray, null);
            case ImagePlus.GRAY32:
               return new FloatProcessor(w, h, (float[]) imgArray, null);
            case ImagePlus.COLOR_RGB:
               // Micro-Manager RGB32 images are generally composed of byte
               // arrays, but ImageJ only takes int arrays.
               throw new ASIdiSPIMException("Color images are not supported");
            default:
               return null;
         }
      }
   }

   public static ImageProcessor makeProcessor(int type, int w, int h) {
      if (type == ImagePlus.GRAY8) {
         return new ByteProcessor(w, h);
      } else if (type == ImagePlus.GRAY16) {
         return new ShortProcessor(w, h);
      } else if (type == ImagePlus.GRAY32) {
         return new FloatProcessor(w, h);
      } else if (type == ImagePlus.COLOR_RGB) {
         return new ColorProcessor(w, h);
      } else {
         return null;
      }
   }

   public static int getIJType(JSONObject map) throws ASIdiSPIMException {
      try {
         return map.getInt("IJType");
      } catch (JSONException e) {
         try {
            String pixelType = map.getString("PixelType");
            if (pixelType.contentEquals("GRAY8")) {
               return ImagePlus.GRAY8;
            } else if (pixelType.contentEquals("GRAY16")) {
               return ImagePlus.GRAY16;
            } else if (pixelType.contentEquals("GRAY32")) {
               return ImagePlus.GRAY32;
            } else if (pixelType.contentEquals("RGB32")) {
               return ImagePlus.COLOR_RGB;
            } else {
               throw new ASIdiSPIMException("Can't figure out IJ type.");
            }
         } catch (JSONException e2) {
            throw new ASIdiSPIMException("Can't figure out IJ type");
         }
      }
   }
   
}
