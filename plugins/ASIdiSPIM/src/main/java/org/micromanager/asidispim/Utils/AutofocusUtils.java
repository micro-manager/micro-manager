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

package org.micromanager.asidispim.utils;

import ij.ImagePlus;
import ij.gui.ImageWindow;
import ij.gui.Roi;
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
import org.micromanager.Studio;
import org.micromanager.asidispim.data.AcquisitionModes;
import org.micromanager.asidispim.data.Cameras;
import org.micromanager.asidispim.data.Devices;
import org.micromanager.asidispim.data.Joystick.Directions;
import org.micromanager.asidispim.Data.MultichannelModes;
import org.micromanager.asidispim.data.MyStrings;
import org.micromanager.asidispim.data.Positions;
import org.micromanager.asidispim.data.Prefs;
import org.micromanager.asidispim.data.Properties;
import org.micromanager.asidispim.api.ASIdiSPIMException;
import org.micromanager.asidispim.fit.Fitter;
import org.micromanager.imagedisplay.VirtualAcquisitionDisplay;
import org.micromanager.utils.NumberUtils;
import org.micromanager.utils.ReportingUtils;

/**
 * @author nico
 */
public class AutofocusUtils {

   private final Studio gui_;
   private final Devices devices_;
   private final Properties props_;
   private final Prefs prefs_;
   private final Cameras cameras_;
   private final StagePositionUpdater posUpdater_;
   private final Positions positions_;
   private final ControllerUtils controller_;

   public AutofocusUtils(Studio gui, Devices devices, Properties props,
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
    * temporarily set the center position to the current position.  If the 
    * autofocus is successful (based on the goodness of fit of the focus curve),
    * the scanner slice position will  be set to the in-focus position, otherwise
    * it will be returned to the original position
    *
    * @param caller - calling panel, used to restore settings after focusing
    * @param side - A or B
    * @param centerAtCurrentZ - whether to focus around the current position (true), or
    *                            around the imaging center set in the setup panel (false)
    * @param sliceTiming - Data structure with current device timing setting
    * @param runAsynchronously - whether or not to run the function asynchronously
    * @return R-square for Gaussian fit to the focus data
    *
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

            double bestGalvoPosition = 0;
            
            double r2 = 0;

            if (gui_.getAutofocusManager().getDevice() == null) {
               throw new ASIdiSPIMException("Please define an autofocus methods first");
            }
            gui_.getAutofocusManager().getDevice().applySettings();
            
            // if the Snap/Live window has an ROI set, we will use the same 
            // ROI for our focus calculations
            // TODO: should this be an option?
            ImageWindow iw = gui_.getSnapLiveManager().getDisplay().getImageWindow();
            Roi roi = null;
            if (iw != null)
               roi = iw.getImagePlus().getRoi();
            
            // TODO implement this pref as numeric code like other pull-downs
            final Fitter.FunctionType function = Fitter.getFunctionTypeAsType(
                    prefs_.getString (
                        MyStrings.PanelNames.AUTOFOCUS.toString(), 
                        Prefs.Keys.AUTOFOCUSFITFUNCTION, 
                        Fitter.getFunctionTypeAsString(
                        Fitter.FunctionType.Gaussian) 
                    ) 
            );

            String camera = devices_.getMMDevice(Devices.Keys.CAMERAA);
            boolean usingDemoCam = devices_.getMMDeviceLibrary(Devices.Keys.CAMERAA).equals(Devices.Libraries.DEMOCAM);
            if (side.equals(Devices.Sides.B)) {
               camera = devices_.getMMDevice(Devices.Keys.CAMERAB);
               usingDemoCam = devices_.getMMDeviceLibrary(Devices.Keys.CAMERAB).equals(Devices.Libraries.DEMOCAM);
            }
            Devices.Keys galvoDevice = Devices.getSideSpecificKey(Devices.Keys.GALVOA, side);
            Devices.Keys piezoDevice = Devices.getSideSpecificKey(Devices.Keys.PIEZOA, side);

            final int nrImages = props_.getPropValueInteger(
                    Devices.Keys.PLUGIN, Properties.Keys.PLUGIN_AUTOFOCUS_NRIMAGES);
            final boolean debug = prefs_.getBoolean(
                    MyStrings.PanelNames.AUTOFOCUS.toString(),
                    Properties.Keys.PLUGIN_AUTOFOCUS_DEBUG, false);
            final float piezoStepSize = props_.getPropValueFloat(Devices.Keys.PLUGIN,
                    Properties.Keys.PLUGIN_AUTOFOCUS_STEPSIZE);  // user specifies step size in um, we translate to galvo and move only galvo
            final float imagingCenter = prefs_.getFloat(
                    MyStrings.PanelNames.SETUP.toString() + side.toString(),
                    Properties.Keys.PLUGIN_PIEZO_CENTER_POS, 0);
            final float minimumRSquare =  props_.getPropValueFloat(Devices.Keys.PLUGIN,
                     Properties.Keys.PLUGIN_AUTOFOCUS_MINIMUMR2, null);
            final double originalPiezoPosition = positions_.getUpdatedPosition(piezoDevice);
            final double originalGalvoPosition = positions_.getUpdatedPosition(galvoDevice);
            final double piezoCenter = centerAtCurrentZ ? originalPiezoPosition : imagingCenter;
            
            if (!centerAtCurrentZ) {
               positions_.setPosition(piezoDevice, piezoCenter);
            }
            
            posUpdater_.pauseUpdates(true);

            // TODO: run this on its own thread
            controller_.prepareControllerForAquisition(
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
                    0.0f, // delay before side (move piezo ahead of time so 0 delay should be OK with >100ms comm time) 
                    piezoStepSize, // piezoStepSize in microns, used in SLICE_SCAN_ONLY to compute slice movement
                    sliceTiming);

            final float galvoRate = prefs_.getFloat(
                     MyStrings.PanelNames.SETUP.toString() + side.toString(), 
                     Properties.Keys.PLUGIN_RATE_PIEZO_SHEET, 100);
            // TODO remove below comments when fixed
            // galvoOffset is named inappropriately for now
            // will be changing with change in calibration representation later on
            final float galvoOffset = prefs_.getFloat(
                     MyStrings.PanelNames.SETUP.toString() + side.toString(),
                     Properties.Keys.PLUGIN_OFFSET_PIEZO_SHEET, 0);
            final double galvoStepSize = piezoStepSize / galvoRate;
            final double galvoCenter = (piezoCenter - galvoOffset) / galvoRate;
            final double galvoStart = galvoCenter - 0.5 * (nrImages - 1) * galvoStepSize;
            
            double[] focusScores = new double[nrImages];
            TaggedImage[] imageStore = new TaggedImage[nrImages];
            
            // Use array to store data so that we can expand to plotting multiple
            // data sets.  
            XYSeries[] scoresToPlot = new XYSeries[2];
            scoresToPlot[0] = new XYSeries(nrImages);

            boolean liveModeOriginally = false;
            String originalCamera = gui_.core().getCameraDevice();
            String acqName = "diSPIM Autofocus";
            
            int highestIndex = -1;

            try {
               liveModeOriginally = gui_.live().getIsLiveModeOn();
               if (liveModeOriginally) {
                  gui_.live().setLiveMode(false);
                  gui_.core().waitForDevice(originalCamera);
               }
               
               // deal with shutter before starting acquisition
               // needed despite core's handling because of DemoCamera
               boolean autoShutter = gui_.core().getAutoShutter();
               boolean shutterOpen = gui_.core().getShutterOpen();
               if (autoShutter) {
                  gui_.core().setAutoShutter(false);
                  if (!shutterOpen) {
                     gui_.core().setShutterOpen(true);
                  }
               }
               
               gui_.core().setCameraDevice(camera);
               if (debug) {
                  if (gui_.displays().acquisitionExists(acqName)) {
                     gui_.closeAcquisitionWindow(acqName);
                  }
                  acqName = gui_.getUniqueAcquisitionName(acqName);
                  gui_.openAcquisition(acqName, "", 1, 1, nrImages, 1, true, false);
                  gui_.initializeAcquisition(acqName,
                          (int) gui_.core().getImageWidth(),
                          (int) gui_.core().getImageHeight(),
                          (int) gui_.core().getBytesPerPixel(),
                          (int) gui_.core().getImageBitDepth());
               }
               gui_.core().clearCircularBuffer();
               gui_.core().initializeCircularBuffer();
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
               while (gui_.core().getRemainingImageCount() == 0
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
               // Store the scores in an array for fitting analysis
               boolean done = false;
               int counter = 0;
               startTime = System.currentTimeMillis();
               while ((gui_.core().getRemainingImageCount() > 0
                       || gui_.core().isSequenceRunning(camera))
                       && !done) {
                  now = System.currentTimeMillis();
                  if (gui_.core().getRemainingImageCount() > 0) {  // we have an image to grab
                     TaggedImage timg = gui_.core().popNextTaggedImage();
                     // reset our wait timer since we got an image
                     startTime = System.currentTimeMillis();
                     ImageProcessor ip = makeProcessor(timg);
                     if (roi != null) {
                        ip.setRoi(roi);
                        ip = ip.crop();
                     }
                     try {
                        focusScores[counter] = gui_.getAutofocusManager().getDevice().computeScore(ip);
                     } catch (Exception ex) {
                        done = true;
                        throw new ASIdiSPIMException("Selected autofocus device didn't return a focus score.");
                     }
                     imageStore[counter] = timg;
                     ReportingUtils.logDebugMessage("Autofocus, image: " + counter
                             + ", score: " + focusScores[counter]);
                     if (debug) {
                        // we are using the slow way to insert images, should be OK
                        // as long as the circular buffer is big enough
                        double galvoPos = galvoStart + counter * galvoStepSize;
                        timg.tags.put("SlicePosition", galvoPos);
                        timg.tags.put("ZPositionUm", piezoCenter);
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
               
               // if we are using demo camera then add some extra time to let controller finish
               // since we got images without waiting for controller to actually send triggers
               if (usingDemoCam) {
                  Thread.sleep(1000);
               }
               
               // clean up shutter
               gui_.core().setShutterOpen(shutterOpen);
               gui_.core().setAutoShutter(autoShutter);
               
               // fit the focus scores
               // limit the best position to the range of galvo range we used
               double[] fitParms = Fitter.fit(scoresToPlot[0], function, null);
               bestGalvoPosition = Fitter.getXofMaxY(scoresToPlot[0], function, fitParms);
               highestIndex = Fitter.getIndex(scoresToPlot[0], bestGalvoPosition);
               scoresToPlot[1] = Fitter.getFittedSeries(scoresToPlot[0], 
                       function, fitParms);
               r2 = Fitter.getRSquare(scoresToPlot[0], function, fitParms);
               
               // display the best scoring image in the debug stack if it exists
               // or if not then in the snap/live window if it exists
               if (debug) {
                  // the following does not work.  Delete?
                  VirtualAcquisitionDisplay vad = gui_.getAcquisition(acqName).getAcquisitionWindow();
                  vad.setSliceIndex(highestIndex);
                  vad.updateDisplay(null);
                  // TODO figure out why slice slider isn't updated, probably a bug in the core
               } else if (gui_.live().getDisplay() != null) {
                  // gui_.addImageToAcquisition("Snap/Live Window", 0, 0, 0, 0,
                  //         imageStore[highestIndex]);
                  gui_.live().displayImage(imageStore[highestIndex]);
               }
               
               if (debug) {
                  PlotUtils plotter = new PlotUtils(prefs_, "AutofocusUtils");
                  boolean[] showSymbols = {true, false};
                  plotter.plotDataN("Focus curve", 
                        scoresToPlot, 
                        "Galvo position [\u00B0]", 
                        "Focus Score", 
                        showSymbols, 
                        "R^2 = " + NumberUtils.doubleToDisplayString(r2));
                  // TODO add annotations with piezo position, bestGalvoPosition, etc.
               }

            } catch (ASIdiSPIMException ex) {
               throw ex;
            } catch (Exception ex) {
               throw new ASIdiSPIMException(ex);
            } finally {
               try {
                  caller.setCursor(Cursor.getDefaultCursor());

                  gui_.core().stopSequenceAcquisition(camera);
                  gui_.core().setCameraDevice(originalCamera);
                  if (debug) {
                     gui_.promptToSaveAcquisition(acqName, false);
                  }

                  controller_.cleanUpControllerAfterAcquisition(1, side.toString(), false);

                  cameras_.setSPIMCamerasForAcquisition(false);

                  // move piezo back to original position if needed
                  if (!centerAtCurrentZ) {
                     positions_.setPosition(piezoDevice, originalPiezoPosition);
                  }
                  
                  // move galvo to maximal focus position if we found one
                  // if for some reason we were unsuccessful then restore to original position
                  // TODO: 
                  if (highestIndex >= 0 && r2 > minimumRSquare) {
                     positions_.setPosition(galvoDevice, Directions.Y, bestGalvoPosition);
                  } else {
                     positions_.setPosition(galvoDevice, Directions.Y, originalGalvoPosition);
                  }
                  
                  // let the calling panel restore appropriate settings
                  caller.refreshSelected();
                     
                  posUpdater_.pauseUpdates(false);

                  if (liveModeOriginally) {
                     gui_.core().waitForDevice(camera);
                     gui_.core().waitForDevice(originalCamera);
                     gui_.live().setLiveMode(true);
                  }

               } catch (Exception ex) {
                  throw new ASIdiSPIMException(ex, "Error while restoring hardware state after autofocus.");
               }
            }
            return r2;
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
