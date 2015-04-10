///////////////////////////////////////////////////////////////////////////////
//FILE:          AutofocusUtils.java
//PROJECT:       Micro-Manager 
//SUBSYSTEM:     ASIdiSPIM plugin
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nico Stuurman, Jon Daniels
//
// COPYRIGHT:    University of California, San Francisco, & ASI, 2015
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

package org.micromanager.asidispim.Utils;

import ij.ImagePlus;
import ij.process.ByteProcessor;
import ij.process.ColorProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;
import java.awt.Cursor;
import java.util.concurrent.ExecutionException;
import javax.swing.SwingWorker;
import mmcorej.TaggedImage;
import org.jfree.data.xy.XYSeries;

import org.json.JSONException;
import org.json.JSONObject;
import org.micromanager.api.ScriptInterface;
import org.micromanager.asidispim.Data.AcquisitionModes;
import org.micromanager.asidispim.Data.Cameras;
import org.micromanager.asidispim.Data.Devices;
import org.micromanager.asidispim.Data.Joystick;
import org.micromanager.asidispim.Data.MultichannelModes;
import org.micromanager.asidispim.Data.MyStrings;
import org.micromanager.asidispim.Data.Positions;
import org.micromanager.asidispim.Data.Prefs;
import org.micromanager.asidispim.Data.Properties;
import org.micromanager.asidispim.api.ASIdiSPIMException;
import org.micromanager.utils.MMScriptException;
import org.micromanager.utils.ReportingUtils;

/**
 *
 * @author nico
 */
public class AutofocusUtils {

   private final ScriptInterface gui_;
   private final Devices devices_;
   private final Properties props_;
   private final Prefs prefs_;
   private final Cameras cameras_;
   private final StagePositionUpdater posUpdater_;
   private final Positions positions_;
   private final ControllerUtils controller_;

   public AutofocusUtils(ScriptInterface gui, Devices devices, Properties props,
           Prefs prefs, Cameras cameras, StagePositionUpdater stagePosUpdater,
           Positions positions, ControllerUtils controller) {
      gui_ = gui;
      devices_ = devices;
      props_ = props;
      prefs_ = prefs;
      cameras_ = cameras;
      posUpdater_ = stagePosUpdater;
      positions_ = positions;
      controller_ = controller;
   }

   /**
    * Acquires image stack by scanning the mirror, calculates focus scores
    * Acquires around the current piezo position. As a side effect, will
    * temporarily set the center position to the current position (but restore
    * the center position on exit).
    *
    * @param caller - calling panel, used to restore settings after focussing
    * @param side - A or B
    * @param centerAtCurrentZ - whether to focus around the current position, or
    *                            around the middle set in the setup panel
    * @param sliceTiming - Data structure with current device timing setting
    * @param runAsynchronously - whether or not to run the function asynchronously
    *
    * @return position of the galvo device associated with highest focus score
    */
   public double runFocus(
           final ListeningJPanel caller,
           final Devices.Sides side,
           final boolean centerAtCurrentZ,
           final SliceTiming sliceTiming,
           final boolean runAsynchronously) {

      class FocusWorker extends SwingWorker<Double, Object> {

         @Override
         protected Double doInBackground() throws Exception {

            double bestScore = 0;

            if (gui_.getAutofocus() == null) {
               throw new ASIdiSPIMException("Please define an autofocus methods first");
            }
            gui_.getAutofocus().applySettings();

            String camera = devices_.getMMDevice(Devices.Keys.CAMERAA);
            if (side.equals(Devices.Sides.B)) {
               camera = devices_.getMMDevice(Devices.Keys.CAMERAB);
            }

            final int nrImages = props_.getPropValueInteger(
                    Devices.Keys.PLUGIN, Properties.Keys.PLUGIN_AUTOFOCUS_NRIMAGES);
            final boolean debug = prefs_.getBoolean(
                    MyStrings.PanelNames.AUTOFOCUS.toString(),
                    Properties.Keys.PLUGIN_AUTOFOCUS_DEBUG, false);
            final float piezoStepSize = props_.getPropValueFloat(Devices.Keys.PLUGIN,
                    Properties.Keys.PLUGIN_AUTOFOCUS_STEPSIZE);
            final float originalCenter = prefs_.getFloat(
                    MyStrings.PanelNames.SETUP.toString() + side.toString(),
                    Properties.Keys.PLUGIN_PIEZO_CENTER_POS, 0);
            final double piezoPosition = positions_.getUpdatedPosition(
                    Devices.getSideSpecificKey(Devices.Keys.PIEZOA, side),
                    Joystick.Directions.NONE);
            final double center = centerAtCurrentZ ? piezoPosition : originalCenter;

            final double start = center - (0.5 * (nrImages - 1) * piezoStepSize);


            posUpdater_.pauseUpdates(true);

            // TODO: run this on its own thread
            controller_.prepareControllerForAquisition(side,
                    false, // no hardware timepoints
                    MultichannelModes.Keys.NONE,
                    false, // do not use channels
                    1, // numChannels
                    nrImages, // numSlices
                    1, // numTimepoints
                    1, // timeInterval
                    1, // numSides
                    side.toString(), // firstside
                    false, // useTimepoints
                    AcquisitionModes.Keys.SLICE_SCAN_ONLY, // scan only the mirror
                    centerAtCurrentZ,  // center around the current Z or the middle set in the setup panel
                    100.0f, // delay before side (can go to 0?)
                    piezoStepSize, // piezoStepSize in microns
                    sliceTiming);

            final float galvoRate = prefs_.getFloat(
                     MyStrings.PanelNames.SETUP.toString() + side.toString(), 
                     Properties.Keys.PLUGIN_RATE_PIEZO_SHEET, -80);
            final float galvoOffset = prefs_.getFloat(
                     MyStrings.PanelNames.SETUP.toString() + side.toString(), 
                     Properties.Keys.PLUGIN_OFFSET_PIEZO_SHEET, 0);
            final double galvoStepSize = piezoStepSize / galvoRate;
            final double galvoStart = (start - galvoOffset) / galvoRate;
            
            double[] focusScores = new double[nrImages];
            TaggedImage[] imageStore = new TaggedImage[nrImages];
            // Use array to store data so that we can expand to plotting multiple
            // data sets.  For now, use only 1
            XYSeries[] scoresToPlot = new XYSeries[1];
            scoresToPlot[0] = new XYSeries(nrImages);

            boolean liveModeOriginally = false;
            String originalCamera = gui_.getMMCore().getCameraDevice();
            String acqName = "";

            try {
               liveModeOriginally = gui_.isLiveModeOn();
               if (liveModeOriginally) {
                  gui_.enableLiveMode(false);
                  gui_.getMMCore().waitForDevice(originalCamera);
               }
               gui_.getMMCore().setCameraDevice(camera);
               if (debug) {
                  acqName = gui_.getUniqueAcquisitionName("diSPIM Autofocus");
                  gui_.openAcquisition(acqName, "", nrImages, 1, 1, 1, true, false);
                  gui_.initializeAcquisition(acqName,
                          (int) gui_.getMMCore().getImageWidth(),
                          (int) gui_.getMMCore().getImageHeight(),
                          (int) gui_.getMMCore().getBytesPerPixel(),
                          (int) gui_.getMMCore().getImageBitDepth());
               }
               gui_.getMMCore().clearCircularBuffer();
               cameras_.setSPIMCamerasForAcquisition(true);
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
                       && (now - startTime < timeout)) {
                  now = System.currentTimeMillis();
                  Thread.sleep(5);
               }
               if (now - startTime >= timeout) {
                  throw new ASIdiSPIMException(
                          "Camera did not send first image within a reasonable time");
               }

               // calculate focus scores of the acquired images, using the scoring
               // algorithm of the active autofocus device
               // Store the scores in an array
               boolean done = false;
               int counter = 0;
               startTime = System.currentTimeMillis();
               while ((gui_.getMMCore().getRemainingImageCount() > 0
                       || gui_.getMMCore().isSequenceRunning(camera))
                       && !done) {
                  now = System.currentTimeMillis();
                  if (gui_.getMMCore().getRemainingImageCount() > 0) {  // we have an image to grab
                     TaggedImage timg = gui_.getMMCore().popNextTaggedImage();
                     ImageProcessor ip = makeProcessor(timg);
                     focusScores[counter] = gui_.getAutofocus().computeScore(ip);
                     imageStore[counter] = timg;
                     ReportingUtils.logDebugMessage("Autofocus, image: " + counter
                             + ", score: " + focusScores[counter]);
                     if (debug) {
                        // we are using the slow way to insert images, should be OK
                        // as long as the circular buffer is big enough
                        double zPos = start + counter * piezoStepSize;
                        double galvoPos = galvoStart + counter * galvoStepSize;
                        timg.tags.put("SlicePosition", zPos);
                        timg.tags.put("ZPositionUm", zPos);
                        gui_.addImageToAcquisition(acqName, 0, 0, counter, 0, timg);
                        scoresToPlot[0].add(galvoPos, focusScores[counter]);
                     }
                     counter++;
                     if (counter >= nrImages) {
                        done = true;
                     }
                  }
                  if (now - startTime > timeout) {
                     // no images within a reasonable amount of time => exit
                     throw new ASIdiSPIMException("No image arrived in 5 seconds");
                  }
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
               // display the best scoring image in the snap/live window
               ImageProcessor bestIP = makeProcessor(imageStore[highestIndex]);
               ImagePlus bestIPlus = new ImagePlus();
               bestIPlus.setProcessor(bestIP);
               if (gui_.getSnapLiveWin() != null) {
                  try {
                     gui_.addImageToAcquisition("Snap/Live Window", 0, 0, 0, 0,
                             imageStore[highestIndex]);
                  } catch (MMScriptException ex) {
                     ReportingUtils.logError(ex, "Failed to add image to Snap/Live Window");
                  }
               }

               if (debug) {
                  PlotUtils plotter = new PlotUtils(prefs_, "AutofocusUtils");
                  plotter.plotDataN("Focus curve", scoresToPlot, 
                          "Galvo position (degree)", "Score", true, false);
               }

               // return the position of the galvo device associated with the 
               // highest focus score
               bestScore = galvoStart + galvoStepSize * highestIndex;

            } catch (ASIdiSPIMException ex) {
               throw ex;
            } catch (Exception ex) {
               throw new ASIdiSPIMException(ex);
            } finally {
               try {
                  caller.setCursor(Cursor.getDefaultCursor());

                  gui_.getMMCore().stopSequenceAcquisition(camera);
                  gui_.getMMCore().setCameraDevice(originalCamera);
                  if (debug) {
                     gui_.promptToSaveAcquisition(acqName, false);
                     gui_.closeAcquisition(acqName);
                  }

                  controller_.cleanUpAfterAcquisition(false, null, 0.0f, 0.0f);

                  cameras_.setSPIMCamerasForAcquisition(false);

                  // Let the calling panel restore the settings
                  caller.gotSelected();

                  // move piezo  to original position
                  Devices.Keys piezo = Devices.getSideSpecificKey(Devices.Keys.PIEZOA, side);
                  if (devices_.isValidMMDevice(piezo)) {
                     positions_.setPosition(piezo, Joystick.Directions.NONE, center);
                  }
                  // move Galvo to its focussed position
                  Devices.Keys galvo = Devices.getSideSpecificKey(Devices.Keys.GALVOA, side);
                  if (devices_.isValidMMDevice(galvo)) {
                     positions_.setPosition(galvo, Joystick.Directions.Y, bestScore);
                  }

                  posUpdater_.pauseUpdates(false);

                  if (liveModeOriginally) {
                     gui_.getMMCore().waitForDevice(camera);
                     gui_.getMMCore().waitForDevice(originalCamera);
                     gui_.enableLiveMode(true);
                  }

               } catch (Exception ex) {
                  throw new ASIdiSPIMException(ex, "Error while restoring hardware state");
               }
            }
            return bestScore;
         }

         @Override
         protected void done() {
            try {
               get();
            } catch (InterruptedException ex) {
               MyDialogUtils.showError(ex);
            } catch (ExecutionException ex) {
               MyDialogUtils.showError(ex);
            }
         }
      }

      if (runAsynchronously) {
         caller.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
         (new FocusWorker()).execute();
      } else {
         FocusWorker fw = new FocusWorker();
         fw.execute();
         try {
            double result = fw.get();
            return result;
         } catch (InterruptedException ex) {
            MyDialogUtils.showError(ex);
         } catch (ExecutionException ex) {
            MyDialogUtils.showError(ex);
         }
      }
      
      // we can only return a bogus score 
      return 0;
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
