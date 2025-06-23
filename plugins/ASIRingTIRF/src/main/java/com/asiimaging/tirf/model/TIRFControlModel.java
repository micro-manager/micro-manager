/*
 * Project: ASI Ring TIRF Control
 * License: BSD 3-clause, see LICENSE.md
 * Author: Brandon Simpson (brandon@asiimaging.com)
 * Copyright (c) 2022, Applied Scientific Instrumentation
 */

package com.asiimaging.tirf.model;

import com.asiimaging.tirf.model.data.Settings;
import com.asiimaging.tirf.model.devices.Camera;
import com.asiimaging.tirf.model.devices.PLC;
import com.asiimaging.tirf.model.devices.Scanner;
import com.asiimaging.tirf.model.devices.XYStage;
import com.asiimaging.tirf.model.devices.ZStage;
import com.asiimaging.tirf.ui.TIRFControlFrame;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.Expose;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import mmcorej.CMMCore;
import mmcorej.StrVector;
import mmcorej.TaggedImage;
import org.apache.commons.lang3.StringUtils;
import org.micromanager.Studio;
import org.micromanager.data.Coordinates;
import org.micromanager.data.Coords;
import org.micromanager.data.Datastore;
import org.micromanager.data.Image;
import org.micromanager.data.Metadata;

public class TIRFControlModel {

   private static final String TIGER_DEVICE_LIBRARY = "ASITiger";

   private final Studio studio;
   private final CMMCore core;

   // hardware devices
   private final Scanner scanner;
   private final XYStage xyStage;
   private final ZStage zStage;
   private final Camera camera;
   private final PLC plc;

   private Datastore datastore;
   private final AtomicBoolean run;

   // use Gson to convert fields to JSON
   // all fields marked with @Expose are saved settings
   private static Gson GSON;

   @Expose
   private int numImages;
   @Expose
   private int numFastCircles;
   @Expose
   private int startupScriptDelay;
   @Expose
   private boolean useStartupScript;
   @Expose
   private boolean useShutdownScript;
   @Expose
   private String startupScriptPath;
   @Expose
   private String shutdownScriptPath;
   @Expose
   private String datastoreSavePath;
   @Expose
   private String datastoreSaveFileName;
   @Expose
   private Datastore.SaveMode datastoreSaveMode;

   private TIRFControlFrame frame;
   private final UserSettings settings;

   public TIRFControlModel(final Studio studio) {
      this.studio = Objects.requireNonNull(studio);
      core = this.studio.core();

      // used to save and load software settings
      settings = new UserSettings(studio);

      // true if the acquisition thread is running
      run = new AtomicBoolean(false);

      // hardware devices
      camera = new Camera(studio);
      scanner = new Scanner(studio);
      xyStage = new XYStage(studio);
      zStage = new ZStage(studio);
      plc = new PLC(studio);

      getDeviceNames();

      // return early if no Tiger controller or FAST_CIRCLES module
      if (!validate()) {
         return;
      }

      // update camera properties based on detected camera model
      if (!camera.setupProperties()) {
         studio.logs().showError("Your camera is not supported by the plugin.\n" +
               "Please send an email to info@asiimaging.com and we can add support.\n" +
               "You can also use a Beanshell script to set the camera trigger mode.\n" +
               "You are welcome to submit a pull request to GitHub as well.");
      }

      // used to serialize settings into JSON
      GSON = new GsonBuilder()
            .excludeFieldsWithoutExposeAnnotation()
            .create();
   }

   public Studio getStudio() {
      return studio;
   }

   /**
    * Returns true if the Hardware Device Configuration has an ASITiger device,
    * and a Scanner with the fast circles module.
    *
    * @return true if the plugin has what it requires to operate.
    */
   public boolean validate() {
      return isTigerDevice() && scanner.hasFastCirclesModule();
   }

   public void loadSettings() {
      // JSON is converted to an object and then model setters set values
      final Settings userData = Settings.fromJson(settings.load());
      userData.setModelSettings(this);
   }

   /**
    * Return the {@code UserSettings} object.
    *
    * <p>Used to save software settings.
    *
    * @return the {@code UserSettings} object
    */
   public UserSettings getUserSettings() {
      return settings;
   }

   /**
    * Returns true if the current hardware configuration has loaded the ASITiger device adapter.
    *
    * @return true if the Tiger device adapter is loaded in this configuration
    */
   public boolean isTigerDevice() {
      final StrVector devices = core.getLoadedDevices();
      for (final String device : devices) {
         try {
            if (core.getDeviceLibrary(device).equals(TIGER_DEVICE_LIBRARY)) {
               return true;
            }
         } catch (Exception e) {
            studio.logs().logError("isTigerDevice(): could not get the device library "
                  + "of the device with name => " + device);
         }
      }
      // if we made it here there were no Tiger devices
      return false;
   }

   /**
    * Detect the loaded device names in the hardware configuration and
    * set the deviceName field for all devices.
    */
   public void getDeviceNames() {
      scanner.setDeviceName(core.getGalvoDevice());
      xyStage.setDeviceName(core.getXYStageDevice());
      zStage.setDeviceName(core.getFocusDevice());
      plc.setDeviceName(core.getShutterDevice());
      scanner.getScanAxisNames();
   }

   /**
    * Select the PLogic program based on firmware version.
    */
   public void setupPLogic() {
      // "FastCirclesRate(Hz)" needs to be current for setupPLogicNew()
      // Used when setting the "one shot (NRT)" config value
      scanner.setFastCirclesRateHz(computeFastCirclesHz());
      frame.getTabPanel().getScannerTab().updateFastCirclesHzLabel();
      if (scanner.getFirmwareVersion() >= 3.51) {
         setupPLogicNew();
      } else {
         setupPLogicOld();
      }
   }

   /**
    * Set up the PLogic program for the latest firmware.
    *
    * <p>Firmware version >= 3.51 required.
    */
   private void setupPLogicNew() {
      int addrOutputBNC = 33; // BNC1
      if (plc.getPLogicMode().equals("Seven-channel shutter")
            || plc.getPLogicMode().equals("Seven-channel TTL shutter")) {
         addrOutputBNC = 40; // BNC8
      }

      final int addrInvert = 64;
      final int addrOneShotNRT = 1; // use logic cell 1

      // always use the Micro-mirror card as a clock source
      plc.setTriggerSource(PLC.Values.TriggerSource.MICRO_MIRROR_CARD);

      // connect to output BNC1 or BNC8
      plc.setPointerPosition(addrOutputBNC);
      // invert the signal because "one shot (NRT)" stays high by default
      plc.setCellConfig(addrOneShotNRT + addrInvert);

      // setup "one shot NRT" cell
      plc.setPointerPosition(addrOneShotNRT);
      plc.setCellType(PLC.Values.EditCellType.ONE_SHOT_NRT);
      plc.setCellInput1(192);
      plc.setCellInput2(192);

      // trigger timing
      plc.setCellConfig(computeOneShotTiming());
   }

   /**
    * Compute the configuration value for the "one shot (NRT)" cell in the PLogic program.
    * This cell controls the timing of the BNC output signal.
    *
    * <p>Firmware >= 3.51 required.
    *
    * @return the configuration value for logic cell 1
    */
   public int computeOneShotTiming() {
      final int periodMs = 1000 / (int) (scanner.getFastCirclesRateHz() / getNumFastCircles());
      return (periodMs * 4) - 1;
   }

   /**
    * Set up the PLogic program to divide the clock for FAST_CIRCLES scanner firmware.
    *
    * <p>This is to ensure backwards compatibility with older firmware versions.
    */
   private void setupPLogicOld() {
      int addrOutputBNC = 33; // BNC1
      if (plc.getPLogicMode().equals("Seven-channel shutter")
            || plc.getPLogicMode().equals("Seven-channel TTL shutter")) {
         addrOutputBNC = 40; // BNC8
      }

      final int addrDFlop = 1; // use logic cell 1

      // always use the Micro-mirror card as a clock source
      plc.setTriggerSource(PLC.Values.TriggerSource.MICRO_MIRROR_CARD);

      // connect to output BNC1
      plc.setPointerPosition(addrOutputBNC);
      plc.setCellConfig(addrDFlop);

      // setup one shot cell
      plc.setPointerPosition(addrDFlop);
      plc.setCellType(PLC.Values.EditCellType.D_FLOP);
      plc.setCellInput1(65);
      plc.setCellInput2(192); // trigger every clock pulse
   }

   public void calibrateFastCircles(final int nImages, final double startSize,
                                    final double radiusIncrement) {
      final SwingWorker<Void, Void> worker = new SwingWorker<>() {
         
         final double originalRadius = scanner.getFastCirclesRadius();
         
         @Override
         protected Void doInBackground() {
            studio.logs().logMessage("Calibration Started");

            Image image;

            // make sure values are current
            scanner.setFastCirclesRateHz(computeFastCirclesHz());
            if (scanner.getFirmwareVersion() >= 3.51) {
               plc.setPointerPosition(1); // logic cell 1
               plc.setCellConfig(computeOneShotTiming());
            }

            SwingUtilities.invokeLater(() ->
                  frame.getTabPanel().getScannerTab().updateFastCirclesHzLabel());

            if (camera.isSupported()) {
               camera.setTriggerModeExternal();
            }

            scanner.setBeamEnabled(true);

            datastore = studio.data().createRAMDatastore();
            studio.displays().createDisplay(datastore);
            Coords.CoordsBuilder builder = Coordinates.builder();

            int time = 0;
            while (time < nImages) {
               //System.out.println("Time " + time + " running...");

               builder = builder.time(time).channel(0).stagePosition(0).z(0);

               scanner.setFastCirclesRadius(startSize + time * radiusIncrement);
               // System.out.println(startSize + time * radiusIncrement);

               scanner.setFastCirclesState(Scanner.Values.FastCirclesState.ON);

               image = studio.live().snap(false).get(0);
               image = image.copyAtCoords(builder.build());
               try {
                  datastore.putImage(image);
               } catch (IOException e) {
                  studio.logs().logError("error putting the image into the datastore!");
               }
               time++;

               scanner.setFastCirclesState(Scanner.Values.FastCirclesState.OFF);
            }
            
            return null;
         }

         @Override
         protected void done() {
            // turn off fast circles and the scanner beam
            scanner.setFastCirclesState(Scanner.Values.FastCirclesState.OFF);
            scanner.setBeamEnabled(false);

            if (camera.isSupported()) {
               camera.setTriggerModeInternal();
            }

            scanner.setFastCirclesRadius(originalRadius); // restore radius
            studio.logs().logMessage("Calibration Finished");
         }

      };

      // start thread
      worker.execute();
   }

   /**
    * Run an acquisition.
    */
   public void burstAcq() {
      final SwingWorker<Void, Void> worker = new SwingWorker<>() {

         @Override
         protected Void doInBackground() throws Exception {
            studio.logs().logMessage("Acquisition Started");
            //datastore.close();
            run.set(true);

            int time = 0;

            Image image;
            TaggedImage taggedImage;

            // validate file name
            if (StringUtils.containsAny(datastoreSaveFileName, "<>:\"/\\|?*")) {
               studio.logs().showError("Invalid characters in the save file name.");
               return null;
            }

            // create the datastore
            final String filePath = studio.data().getUniqueSaveDirectory(
                  Paths.get(datastoreSavePath, datastoreSaveFileName).toString());

            if (datastoreSaveMode == Datastore.SaveMode.SINGLEPLANE_TIFF_SERIES) {
               datastore = studio.data().createSinglePlaneTIFFSeriesDatastore(filePath);
            } else if (datastoreSaveMode == Datastore.SaveMode.MULTIPAGE_TIFF) {
               datastore = studio.data().createMultipageTIFFDatastore(filePath, false, false);
            } else if (datastoreSaveMode == Datastore.SaveMode.ND_TIFF) {
               datastore = studio.data().createNDTIFFDatastore(filePath);
            }

            // create the display window
            studio.displays().createDisplay(datastore);
            studio.displays().manage(datastore);
            Coords.CoordsBuilder builder = Coordinates.builder();

            // metadata
            Metadata.Builder metadataBuilder = studio.data().metadataBuilder();
            metadataBuilder.fileName(getDatastoreSaveFileName());
            metadataBuilder.positionName("0");
            Metadata metadata = metadataBuilder.build();

            if (useStartupScript) {
               runBeanshellScript(startupScriptPath);
               Thread.sleep(startupScriptDelay);
               studio.logs().logMessage("Startup Script Finished");
            }

            if (camera.isSupported()) {
               camera.setTriggerModeExternal();
            }

            scanner.setBeamEnabled(true);

            // make sure values are current
            scanner.setFastCirclesRateHz(computeFastCirclesHz());
            if (scanner.getFirmwareVersion() >= 3.51) {
               plc.setPointerPosition(1); // logic cell 1
               plc.setCellConfig(computeOneShotTiming());
            }

            // update ui
            SwingUtilities.invokeLater(() -> {
               frame.getTabPanel().getScannerTab().updateFastCirclesHzLabel();
               frame.getTabPanel().getScannerTab().setBeamEnabledState(true);
               frame.getTabPanel().getScannerTab().setFastCirclesState(true);

               frame.getTabPanel().getScannerTab().getSpinnerScannerX()
                     .setValue(scanner.getPositionX());
               frame.getTabPanel().getScannerTab().getSpinnerScannerY()
                     .setValue(scanner.getPositionY());
            });

            scanner.setFastCirclesStateRestart();
            camera.startSequenceAcquisition(numImages);

            while (core.getRemainingImageCount() > 0 || camera.isSequenceRunning()) {
               if (core.getRemainingImageCount() > 0) {
                  // check if stop requested
                  if (!run.get()) {
                     studio.logs().logMessage("Acquisition Canceled");
                     break;
                  }

                  // we captured all the images
                  if (time >= numImages) {
                     break;
                  }

                  // get the next image and put it in the datastore
                  taggedImage = core.popNextTaggedImage();
                  image = studio.data()
                        .convertTaggedImage(taggedImage, builder.time(time).build(), metadata);
                  datastore.putImage(image);
                  time++;

               } else {
                  Thread.sleep(10);
               }
            }

            camera.stopSequenceAcquisition();

            return null;
         }

         @Override
         protected void done() {
            // turn off fast circles and the scanner beam
            scanner.setFastCirclesState(Scanner.Values.FastCirclesState.OFF);
            scanner.setBeamEnabled(false);

            try {
               datastore.freeze();
            } catch (IOException e) {
               studio.logs().showError("could not freeze the datastore!");
            }

            // set panels to reflect their current state
            frame.getButtonPanel().getStartButton().setState(false);
            frame.getTabPanel().getScannerTab().setFastCirclesState(false);
            frame.getTabPanel().getScannerTab().setBeamEnabledState(false);

            // update the scanner position spinners
            frame.getTabPanel().getScannerTab().getSpinnerScannerX()
                  .setValue(scanner.getPositionX());
            frame.getTabPanel().getScannerTab().getSpinnerScannerY()
                  .setValue(scanner.getPositionY());

            if (camera.isSupported()) {
               camera.setTriggerModeInternal();
            }

            if (useShutdownScript) {
               runBeanshellScript(shutdownScriptPath);
            }

            studio.logs().logMessage("Acquisition Finished");
            run.set(false);
         }

      };

      // start thread
      worker.execute();
   }

   /**
    * Returns the fast circles rate in Hz based on the
    * exposure of the camera.
    *
    * @return the fast circles rate in Hz
    */
   public double computeFastCirclesHz() {
      return (1.0 / camera.getExposure()) * (1000.0 * numFastCircles);
   }

   /**
    * Returns the camera exposure based on the "FastCirclesRate(Hz)".
    *
    * @param rateHz the scanner fast circles rate
    * @return the camera exposure
    */
   public double computeExposureFromFastCirclesHz(final double rateHz) {
      return 1000.0 / rateHz;
   }

   /**
    * When the plugin closes, this method is called to
    * save the settings with the @Expose annotation.
    *
    * @return the settings as a JSON String
    */
   public String toJson() {
      return GSON.toJson(this);
   }

   // Getters/Setters

   public void setNumFastCircles(final int num) {
      numFastCircles = num;
   }

   public int getNumFastCircles() {
      return numFastCircles;
   }

   public void setFrame(final TIRFControlFrame frame) {
      this.frame = frame;
   }

   public void setScriptDelay(final int ms) {
      startupScriptDelay = ms;
   }

   public int getScriptDelay() {
      return startupScriptDelay;
   }

   public void setNumImages(final int n) {
      numImages = n;
   }

   public int getNumImages() {
      return numImages;
   }

   public void setDatastoreSaveMode(final Datastore.SaveMode mode) {
      datastoreSaveMode = mode;
   }

   public String getDatastoreSavePath() {
      return datastoreSavePath;
   }

   public String getDatastoreSaveFileName() {
      return datastoreSaveFileName;
   }

   public void setDatastoreSaveFileName(final String name) {
      datastoreSaveFileName = name;
   }

   public void setDatastoreSavePath(final String filePath) {
      datastoreSavePath = filePath;
   }

   public Datastore.SaveMode getDatastoreSaveMode() {
      return datastoreSaveMode;
   }

   // Beanshell Scripts

   public void runBeanshellScript(final String filePath) {
      studio.scripter().runFile(new File(filePath));
   }

   public void setStartupScriptPath(final String filePath) {
      startupScriptPath = filePath;
   }

   public void setShutdownScriptPath(final String filePath) {
      shutdownScriptPath = filePath;
   }

   public void setUseStartupScript(final boolean state) {
      useStartupScript = state;
   }

   public void setUseShutdownScript(final boolean state) {
      useShutdownScript = state;
   }

   public String getStartupScriptPath() {
      return startupScriptPath;
   }

   public String getShutdownScriptPath() {
      return shutdownScriptPath;
   }

   public boolean getUseStartupScript() {
      return useStartupScript;
   }

   public boolean getUseShutdownScript() {
      return useShutdownScript;
   }

   // Hardware Devices

   public Scanner getScanner() {
      return scanner;
   }

   public XYStage getXYStage() {
      return xyStage;
   }

   public ZStage getZStage() {
      return zStage;
   }

   public Camera getCamera() {
      return camera;
   }

   public PLC getPLogic() {
      return plc;
   }

   // Acquisition

   public void setRunning(final boolean state) {
      run.set(state);
   }

   public boolean isRunning() {
      return run.get();
   }
}
