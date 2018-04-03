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
import org.micromanager.api.ScriptInterface;
import org.micromanager.api.Autofocus;
import org.micromanager.asidispim.ASIdiSPIM;
import org.micromanager.asidispim.Data.AcquisitionModes;
import org.micromanager.asidispim.Data.AcquisitionSettings;
import org.micromanager.asidispim.Data.Cameras;
import org.micromanager.asidispim.Data.Devices;
import org.micromanager.asidispim.Data.Devices.Sides;
import org.micromanager.asidispim.Data.Joystick.Directions;
import org.micromanager.asidispim.Data.MultichannelModes;
import org.micromanager.asidispim.Data.MyStrings;
import org.micromanager.asidispim.Data.Positions;
import org.micromanager.asidispim.Data.Prefs;
import org.micromanager.asidispim.Data.Properties;
import org.micromanager.asidispim.api.ASIdiSPIMException;
import org.micromanager.asidispim.fit.Fitter;
import org.micromanager.imagedisplay.VirtualAcquisitionDisplay;
import org.micromanager.utils.AutofocusManager;
import org.micromanager.utils.NumberUtils;
import org.micromanager.utils.ReportingUtils;

/**
 * @author nico
 * @author Jon
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
   private FocusResult lastFocusResult_;

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
      lastFocusResult_ = new FocusResult(false, 0.0, 0.0, 0.0, 0.0);
      
   }

   public class FocusResult {
      public final boolean focusSuccess_;
      public final double galvoPosition_;
      public final double piezoPosition_;
      public final double offsetDelta_;
      public final double rSquared_;
      
      public FocusResult(boolean focusSuccess, double galvoPosition, double piezoPosition, double offsetDelta, double rSquared) {
         focusSuccess_ = focusSuccess;
         galvoPosition_ = galvoPosition;
         piezoPosition_ = piezoPosition;
         offsetDelta_ = offsetDelta;  // amount in um that the offset will shift, could be positive or negative
         rSquared_ = rSquared;
      }
   }
   
   public FocusResult getLastFocusResult() {
      return lastFocusResult_;
   }
   
   /**
    * Acquires image stack by scanning the mirror, calculates focus scores
    * Acquires around the current piezo position. As a side effect, will
    * temporarily set the center position to the current position.  If the 
    * autofocus is successful (based on the goodness of fit of the focus curve),
    * either the scanner slice position will  be set to the in-focus position
    * (for option "fix piezo, sweep slice") or else the piezo position will be 
    * set to the in-focus position (for option "fix slice, sweep piezo").
    * Immediately following if the user calls updateCalibrationOffset() in the 
    * setup panel then the calibration offset will be changed to be in-focus.
    * If the goodness of fit is insufficient or else the best fit position is
    * at the extreme of the sweep range then the piezo and sheet will be returned
    * to their original positions. 
    *
    * @param caller - calling panel, used to restore settings after focusing
    * @param side - A or B
    * @param centerAtCurrentZ - whether to focus around the current position (true), or
    *                            around the imaging center set in the setup panel (false)
    * @param sliceTiming - Data structure with current device timing setting
    * @param runAsynchronously - whether or not to run the function asynchronously (in own thread)
    *          (true when run from Setup panel and false when run during acquisition)
    * @return FocusResult object.  Will be bogus if runAsynchronously is true, but in that case the
    *          actual result can be accessed via getLastFocusResult() in the caller's refreshSelected() method
    *
    */
   public FocusResult runFocus(
           final ListeningJPanel caller,
           final Devices.Sides side,
           final boolean centerAtCurrentZ,
           final SliceTiming sliceTiming,
           final boolean runAsynchronously) {

      class FocusWorker extends SwingWorker<FocusResult, Object> {

         @Override
         protected FocusResult doInBackground() throws Exception {
            
            if (ASIdiSPIM.getFrame().getHardwareInUse()) {
               throw new ASIdiSPIMException("Cannot run autofocus while another"
                     + " autofocus or acquisition is ongoing.");
            }

            // Score indicating goodness of the fit
            double r2 = 0;
            double bestGalvoPosition = 0;
            double bestPiezoPosition = 0;
            
            AutofocusManager afManager = gui_.getAutofocusManager();
            afManager.selectDevice("OughtaFocus");
            
            Autofocus afDevice = gui_.getAutofocus();

            if (afDevice == null) {
               throw new ASIdiSPIMException("Please define autofocus methods first");
            }
            
            // select the appropriate algorithm
            final String algorithmName = Fitter.getAlgorithmFromPrefCode(
                  prefs_.getInt(MyStrings.PanelNames.AUTOFOCUS.toString(),
                  Properties.Keys.AUTOFOCUS_SCORING_ALGORITHM,
                  Fitter.Algorithm.VOLATH.getPrefCode())).toString();
            afDevice.setPropertyValue("Maximize", algorithmName);
            
            // make sure that the currently selected MM autofocus device uses the 
            // settings in its dialog
            afDevice.applySettings();
            
            // if the Snap/Live window has an ROI set, we will use the same 
            // ROI for our focus calculations
            // TODO: should this be an option?
            ImageWindow iw = gui_.getSnapLiveWin();
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
            
            final String acqModeString = props_.getPropValueString(Devices.Keys.PLUGIN, Properties.Keys.AUTOFOCUS_ACQUSITION_MODE);
            final boolean isPiezoScan = acqModeString.equals("Fix slice, sweep piezo");
            
            ReportingUtils.logDebugMessage("Autofocus getting ready using " + algorithmName + " algorithm, mode \"" + acqModeString + "\"");

            String camera = devices_.getMMDevice(Devices.Keys.CAMERAA);
            Devices.Keys cameraDevice = Devices.Keys.CAMERAA;
            boolean usingDemoCam = devices_.getMMDeviceLibrary(Devices.Keys.CAMERAA).equals(Devices.Libraries.DEMOCAM);
            if (side.equals(Devices.Sides.B)) {
               camera = devices_.getMMDevice(Devices.Keys.CAMERAB);
               cameraDevice = Devices.Keys.CAMERAB;
               usingDemoCam = devices_.getMMDeviceLibrary(Devices.Keys.CAMERAB).equals(Devices.Libraries.DEMOCAM);
            }
            Devices.Keys galvoDevice = Devices.getSideSpecificKey(Devices.Keys.GALVOA, side);
            Devices.Keys piezoDevice = Devices.getSideSpecificKey(Devices.Keys.PIEZOA, side);

            final int nrImages = props_.getPropValueInteger(
                    Devices.Keys.PLUGIN, Properties.Keys.PLUGIN_AUTOFOCUS_NRIMAGES);
            final boolean showImages = prefs_.getBoolean(MyStrings.PanelNames.AUTOFOCUS.toString(),
                    Properties.Keys.PLUGIN_AUTOFOCUS_SHOWIMAGES, true);
            final boolean showPlot = prefs_.getBoolean(MyStrings.PanelNames.AUTOFOCUS.toString(),
                    Properties.Keys.PLUGIN_AUTOFOCUS_SHOWPLOT, true);
            float piezoStepSize = props_.getPropValueFloat(Devices.Keys.PLUGIN,
                    Properties.Keys.PLUGIN_AUTOFOCUS_STEPSIZE);  // user specifies step size in um, we translate to galvo and move only galvo
            final float imagingCenter = prefs_.getFloat(
                    MyStrings.PanelNames.SETUP.toString() + side.toString(),
                    Properties.Keys.PLUGIN_PIEZO_CENTER_POS, 0);
            final float minimumRSquare =  props_.getPropValueFloat(Devices.Keys.PLUGIN,
                     Properties.Keys.PLUGIN_AUTOFOCUS_MINIMUMR2);
            
            // make sure we start with the beam on, but we will restore its state later
            // if the beam is off then we will get an erroneous value for the slice position
            final boolean beamOff = props_.getPropValueString(galvoDevice, Properties.Keys.BEAM_ENABLED)
                  .equals(Properties.Values.NO.toString());
            props_.setPropValue(galvoDevice, Properties.Keys.BEAM_ENABLED, Properties.Values.YES);
            
            final double originalPiezoPosition = positions_.getUpdatedPosition(piezoDevice);
            final double originalGalvoPosition = positions_.getUpdatedPosition(galvoDevice, Directions.Y);
            final double piezoCenter = centerAtCurrentZ ? originalPiezoPosition : imagingCenter;
            
            posUpdater_.pauseUpdates(true);
            
            // start with current acquisition settings, then modify a few of them for the focus acquisition
            AcquisitionSettings acqSettings = ASIdiSPIM.getFrame().getAcquisitionPanel().getCurrentAcquisitionSettings();
            acqSettings.isStageScanning = false;
            acqSettings.hardwareTimepoints = false;
            acqSettings.channelMode = MultichannelModes.Keys.NONE;
            acqSettings.useChannels = false;
            acqSettings.numChannels = 1;
            acqSettings.numSlices = nrImages;
            acqSettings.numTimepoints = 1;
            acqSettings.timepointInterval = 1;
            acqSettings.numSides = 1;
            acqSettings.firstSideIsA = side.equals(Sides.A);
            acqSettings.useTimepoints = false;
            acqSettings.useMultiPositions = false;
            acqSettings.spimMode = isPiezoScan ? AcquisitionModes.Keys.PIEZO_SCAN_ONLY : AcquisitionModes.Keys.SLICE_SCAN_ONLY;
            acqSettings.centerAtCurrentZ = centerAtCurrentZ;
            acqSettings.stepSizeUm = piezoStepSize;

            controller_.prepareControllerForAquisition(acqSettings);
            
            final float calibrationRate = prefs_.getFloat(
                     MyStrings.PanelNames.SETUP.toString() + side.toString(), 
                     Properties.Keys.PLUGIN_RATE_PIEZO_SHEET, 100);
            final float calibrationOffset = prefs_.getFloat(
                     MyStrings.PanelNames.SETUP.toString() + side.toString(),
                     Properties.Keys.PLUGIN_OFFSET_PIEZO_SHEET, 0);
            final double galvoStepSize = isPiezoScan ? 0 : piezoStepSize / calibrationRate;
            final double galvoCenter = (piezoCenter - calibrationOffset) / calibrationRate;
            final double galvoRange = (nrImages - 1) * galvoStepSize;
            final double piezoRange = (nrImages - 1) * piezoStepSize;
            final double galvoStart = galvoCenter - 0.5 * galvoRange;
            final double piezoStart = piezoCenter - 0.5 * piezoRange;
            if (!isPiezoScan) {
               // change this to 0 for calculating actual position if we aren't moving piezo
               piezoStepSize = 0;
            }
            
            double piezoPosition = originalPiezoPosition;
            double galvoPosition = originalGalvoPosition;
            if (!centerAtCurrentZ) {
               positions_.setPosition(piezoDevice, piezoCenter);
               piezoPosition = piezoCenter;
               positions_.setPosition(galvoDevice, Directions.Y, galvoCenter);
               galvoPosition = galvoCenter;
            }
            
            double[] focusScores = new double[nrImages];
            TaggedImage[] imageStore = new TaggedImage[nrImages];
            
            // Use array to store data so that we can expand to plotting multiple
            // data sets.  
            XYSeries[] scoresToPlot = new XYSeries[2];
            scoresToPlot[0] = new XYSeries(nrImages);

            boolean liveModeOriginally = false;
            String originalCamera = gui_.getMMCore().getCameraDevice();
            String acqName = "diSPIM Autofocus";
            
            int highestIndex = -1;

            try {
               liveModeOriginally = gui_.isLiveModeOn();
               if (liveModeOriginally) {
                  gui_.enableLiveMode(false);
                  gui_.getMMCore().waitForDevice(originalCamera);
               }
               
               ASIdiSPIM.getFrame().setHardwareInUse(true);
               
               // deal with shutter before starting acquisition
               // needed despite core's handling because of DemoCamera
               boolean autoShutter = gui_.getMMCore().getAutoShutter();
               boolean shutterOpen = gui_.getMMCore().getShutterOpen();
               if (autoShutter) {
                  gui_.getMMCore().setAutoShutter(false);
                  if (!shutterOpen) {
                     gui_.getMMCore().setShutterOpen(true);
                  }
               }
               
               gui_.getMMCore().setCameraDevice(camera);
               if (showImages) {
                  if (gui_.acquisitionExists(acqName)) {
                     gui_.closeAcquisitionWindow(acqName);
                  }
                  acqName = gui_.getUniqueAcquisitionName(acqName);
                  gui_.openAcquisition(acqName, "", 1, 1, nrImages, 1, true, false);
                  gui_.initializeAcquisition(acqName,
                          (int) gui_.getMMCore().getImageWidth(),
                          (int) gui_.getMMCore().getImageHeight(),
                          (int) gui_.getMMCore().getBytesPerPixel(),
                          (int) gui_.getMMCore().getImageBitDepth());
               }
               gui_.getMMCore().clearCircularBuffer();
               gui_.getMMCore().initializeCircularBuffer();
               cameras_.setCameraForAcquisition(cameraDevice, true);
               prefs_.putFloat(MyStrings.PanelNames.SETTINGS.toString(),
                     Properties.Keys.PLUGIN_CAMERA_LIVE_EXPOSURE_FIRST.toString(),
                     (float)gui_.getMMCore().getExposure());
               gui_.getMMCore().setExposure((double) sliceTiming.cameraExposure);
               gui_.refreshGUIFromCache();
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
                  throw new ASIdiSPIMException("Camera did not send first image within a reasonable time");
               }

               // calculate focus scores of the acquired images, using the scoring
               // algorithm of the active autofocus device
               // Store the scores in an array for fitting analysis
               boolean done = false;
               int counter = 0;
               startTime = System.currentTimeMillis();
               while ((gui_.getMMCore().getRemainingImageCount() > 0
                       || gui_.getMMCore().isSequenceRunning(camera))
                       && !done) {
                  now = System.currentTimeMillis();
                  if (gui_.getMMCore().getRemainingImageCount() > 0) {  // we have an image to grab
                     TaggedImage timg = gui_.getMMCore().popNextTaggedImage();
                     // reset our wait timer since we got an image
                     startTime = System.currentTimeMillis();
                     ImageProcessor ip = makeProcessor(timg);
                     if (roi != null) {
                        ip.setRoi(roi);
                        ip = ip.crop();
                     }
                     try {
                        focusScores[counter] = afDevice.computeScore(ip);
                     } catch (Exception ex) {
                        done = true;
                        throw new ASIdiSPIMException("Selected autofocus device didn't return a focus score.");
                     }
                     imageStore[counter] = timg;
                     ReportingUtils.logDebugMessage("Autofocus, image: " + counter
                             + ", score: " + focusScores[counter]);
                     
                     double galvoPos = galvoStart + counter * galvoStepSize;
                     double piezoPos = piezoStart + counter * piezoStepSize;
                     scoresToPlot[0].add(isPiezoScan ? piezoPos : galvoPos, focusScores[counter]);
                     if (showImages) {
                        // we are using the slow way to insert images, should be OK
                        // as long as the circular buffer is big enough
                        timg.tags.put("SlicePosition", galvoPos);
                        timg.tags.put("ZPositionUm", piezoPos);
                        gui_.addImageToAcquisition(acqName, 0, 0, counter, 0, timg);
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
               gui_.getMMCore().setShutterOpen(shutterOpen);
               gui_.getMMCore().setAutoShutter(autoShutter);
               
               // fit the focus scores
               // limit the best position to the range of galvo range we used
               double[] fitParms = Fitter.fit(scoresToPlot[0], function, null);
               if (isPiezoScan) {
                  bestPiezoPosition = Fitter.getXofMaxY(scoresToPlot[0], function, fitParms);
                  highestIndex = Fitter.getIndex(scoresToPlot[0], bestPiezoPosition);
                  
               } else {
                  bestGalvoPosition = Fitter.getXofMaxY(scoresToPlot[0], function, fitParms);
                  highestIndex = Fitter.getIndex(scoresToPlot[0], bestGalvoPosition);
               }

               scoresToPlot[1] = Fitter.getFittedSeries(scoresToPlot[0], function, fitParms);
               if (function == Fitter.FunctionType.NoFit) {
                  r2 = 1;
               } else {
                  r2 = Fitter.getRSquare(scoresToPlot[0], function, fitParms);
               }
               
               // display the best scoring image in the debug stack if it exists
               // or if not then in the snap/live window if it exists
               if (showImages) {
                  // the following does not work.  Delete?
                  VirtualAcquisitionDisplay vad = gui_.getAcquisition(acqName).getAcquisitionWindow();
                  vad.setSliceIndex(highestIndex);
                  vad.updateDisplay(null);
                  // TODO figure out why slice slider isn't updated, probably a bug in the core
               } else if (gui_.getSnapLiveWin() != null) {
                  // gui_.addImageToAcquisition("Snap/Live Window", 0, 0, 0, 0,
                  //         imageStore[highestIndex]);
                  gui_.displayImage(imageStore[highestIndex]);
               }
               
               if (showPlot) {
                  PlotUtils plotter = new PlotUtils(prefs_, "AutofocusUtils");
                  boolean[] showSymbols = {true, false};
                  plotter.plotDataN("Focus curve", 
                        scoresToPlot, 
                        isPiezoScan ? "Piezo position [\u00B5m]" : "Galvo position [\u00B0]", 
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
               
               ASIdiSPIM.getFrame().setHardwareInUse(false);

               // set result to be a dummy value for now; we will overwrite it later
               //  unless we encounter an exception in the meantime
               lastFocusResult_ = new FocusResult(false, galvoPosition, piezoPosition, 0.0, 0.0);
               
               try {
                  caller.setCursor(Cursor.getDefaultCursor());

                  gui_.getMMCore().stopSequenceAcquisition(camera);
                  gui_.getMMCore().setCameraDevice(originalCamera);
                  if (showImages) {
                     gui_.promptToSaveAcquisition(acqName, false);
                     gui_.closeAcquisition(acqName);
                  }

                  controller_.cleanUpControllerAfterAcquisition(1, acqSettings.firstSideIsA, false);
                  
                  if (runAsynchronously) {
                     // when run from Setup panels then put things back to live mode settings, but not if run during acquisition
                     cameras_.setCameraForAcquisition(cameraDevice, false);
                     gui_.getMMCore().setExposure(camera, prefs_.getFloat(MyStrings.PanelNames.SETTINGS.toString(),
                           Properties.Keys.PLUGIN_CAMERA_LIVE_EXPOSURE_FIRST.toString(), 10f));
                     gui_.refreshGUIFromCache();
                  }
                  
                  // turn the beam off it started out that way
                  if (beamOff) {
                     props_.setPropValue(galvoDevice, Properties.Keys.BEAM_ENABLED, Properties.Values.NO);
                  }

                  // move back to original position if needed
                  if (!centerAtCurrentZ) {
                     positions_.setPosition(piezoDevice, originalPiezoPosition);
                     positions_.setPosition(galvoDevice, Directions.Y, originalGalvoPosition);
                  }
                  
                  // determine if it was "successful" which for now we define as
                  //   - "maximal focus" position is inside the center 80% of the search range
                  //   - curve fit has sufficient R^2 value
                  // if we are successful then stay at that position, otherwise restore the original position
                  //   (don't bother moving anything if we are doing this during acquisition)
                  // the caller may (and usually will) apply further logic to decide whether or not this was a
                  //   successful autofocus, e.g. before automatically updating the offset
                  boolean focusSuccess = false; 
                  if (isPiezoScan) {
                     final double end1 = piezoStart + 0.1*piezoRange;
                     final double end2 = piezoStart + 0.9*piezoRange;
                     focusSuccess = (r2 > minimumRSquare
                           && bestPiezoPosition > Math.min(end1, end2)
                           && bestPiezoPosition < Math.max(end1, end2));
                     double focusDelta = piezoCenter-bestPiezoPosition;
                     lastFocusResult_ = new FocusResult(focusSuccess, galvoPosition, bestPiezoPosition, focusDelta, r2);
                  } else { // slice scan
                     final double end1 = galvoStart + 0.1*galvoRange;
                     final double end2 = galvoStart + 0.9*galvoRange;
                     focusSuccess = (r2 > minimumRSquare
                           && bestGalvoPosition > Math.min(end1, end2)
                           && bestGalvoPosition < Math.max(end1, end2));
                     double focusDelta = (galvoCenter-bestGalvoPosition) * calibrationRate;
                     lastFocusResult_ = new FocusResult(focusSuccess, bestGalvoPosition, piezoPosition, focusDelta, r2);
                  }
                  
                  // if we are in Setup panel, move to either the best-focus position (if found)
                  //   or else back to the original position.  If we are doing autofocus during
                  //   acquisition this is an unnecessary step.
                  if (runAsynchronously) {  // proxy for "running from setup"
                     if (isPiezoScan) {
                        positions_.setPosition(piezoDevice, 
                              focusSuccess ? bestPiezoPosition : originalPiezoPosition);
                     } else {
                        positions_.setPosition(galvoDevice, Directions.Y,
                              focusSuccess ? bestGalvoPosition : originalGalvoPosition);
                     }
                  }
                  
                  // let the calling panel restore appropriate settings
                  // currently only used by setup panels
                  caller.refreshSelected();
                     
                  if (liveModeOriginally) {
                     gui_.getMMCore().waitForDevice(camera);
                     gui_.getMMCore().waitForDevice(originalCamera);
                     gui_.enableLiveMode(true);
                  }

               } catch (Exception ex) {
                  throw new ASIdiSPIMException(ex, "Error while restoring hardware state after autofocus.");
               }
               finally {
                  posUpdater_.pauseUpdates(false);
               }
               
               if (showImages) {
                  if (gui_.acquisitionExists(acqName)) {
                     try {
                        gui_.closeAcquisition(acqName);
                     } catch (Exception ex) {
                        // NS: I don't yet know why, but this call can throw an exception
                        // It is pivotal to catch it, or we will not stop the hardware
                        gui_.logError(ex, "While closing acquisition in diSPIM-AutofocusUtils");
                     }
                  }
               }
            }
            ReportingUtils.logMessage("finished autofocus: " + (lastFocusResult_.focusSuccess_ ? "successful" : "not successful")
               + " with galvo position " + lastFocusResult_.galvoPosition_ + " and piezo position " + lastFocusResult_.piezoPosition_
               + " and R^2 value of " + lastFocusResult_.rSquared_);
            return lastFocusResult_;
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
            FocusResult result = fw.get();
            return result;
         } catch (InterruptedException ex) {
            MyDialogUtils.showError(ex);
         } catch (ExecutionException ex) {
            MyDialogUtils.showError(ex);
         }
      }
      
      // we can only return a bogus score 
      return new FocusResult(false, 0.0, 0.0, 0.0, 0.0);
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
