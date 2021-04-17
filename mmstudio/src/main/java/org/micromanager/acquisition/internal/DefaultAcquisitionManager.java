///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//AUTHOR:        Nenad Amodaj, nenad@amodaj.com, Jul 18, 2005
//               Modifications by Arthur Edelstein, Nico Stuurman, Henry Pinkard
//COPYRIGHT:     University of California, San Francisco, 2006-2013
//               100X Imaging Inc, www.100ximaging.com, 2008
//LICENSE:       This file is distributed under the BSD license.
//               License text is included with the source distribution.
//               This file is distributed in the hope that it will be useful,
//               but WITHOUT ANY WARRANTY; without even the implied warranty
//               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//CVS:          $Id$
//
package org.micromanager.acquisition.internal;

import com.google.common.base.Charsets;
import com.google.common.io.Files;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import javax.swing.SwingUtilities;
import mmcorej.CMMCore;
import mmcorej.Configuration;
import mmcorej.PropertySetting;
import mmcorej.TaggedImage;
import org.micromanager.PropertyMap;
import org.micromanager.PropertyMaps;
import org.micromanager.Studio;
import org.micromanager.acquisition.AcquisitionManager;
import org.micromanager.acquisition.ChannelSpec;
import org.micromanager.acquisition.SequenceSettings;
import org.micromanager.data.Coords;
import org.micromanager.data.Datastore;
import org.micromanager.data.Image;
import org.micromanager.data.Metadata;
import org.micromanager.data.SummaryMetadata;
import org.micromanager.data.internal.DefaultImage;
import org.micromanager.data.internal.DefaultSummaryMetadata;
import org.micromanager.internal.MMStudio;
import org.micromanager.internal.dialogs.AcqControlDlg;
import org.micromanager.internal.utils.MMException;

/**
 * TODO: this class still depends on MMStudio for access to its cache.
 */
public final class DefaultAcquisitionManager implements AcquisitionManager {
   // NOTE: should match the format used by the acquisition engine.
   private static final SimpleDateFormat DATE_FORMATTER = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS Z");

   private final Studio studio_;
   private final AcquisitionWrapperEngine engine_;
   private final AcqControlDlg mdaDialog_;

   public DefaultAcquisitionManager(Studio studio,
         AcquisitionWrapperEngine engine, AcqControlDlg mdaDialog) {
      studio_ = studio;
      engine_ = engine;
      mdaDialog_ = mdaDialog;
   }

   @Override
   public SequenceSettings.Builder sequenceSettingsBuilder() {
      return new SequenceSettings.Builder();
   }

   @Override
   public ChannelSpec.Builder channelSpecBuilder() {
      return new ChannelSpec.Builder();
   }

   @Override
   public Datastore runAcquisition() throws IllegalThreadStateException {
      return executeAcquisition(null, true);
   }

   @Override
   public Datastore runAcquisitionNonblocking() throws IllegalThreadStateException {
      return executeAcquisition(null, false);
   }

   @Override
   public Datastore runAcquisitionWithSettings(SequenceSettings settings,
         boolean shouldBlock) throws IllegalThreadStateException {
      return executeAcquisition(settings, shouldBlock);
   }

   private Datastore executeAcquisition(SequenceSettings settings, boolean isBlocking) throws IllegalThreadStateException {
      if (SwingUtilities.isEventDispatchThread()) {
         throw new IllegalThreadStateException("Acquisition can not be run from this (EDT) thread");
      }
      Datastore store = null;
      if (settings == null) {
         // Use the MDA dialog's runAcquisition logic.
         if (mdaDialog_ != null) {
            store = mdaDialog_.runAcquisition();
         }
         else {
            // I'm not sure how this could ever happen, but we have null
            // checks for mdaDialog_ everywhere in this code, with no
            // explanation.
            studio_.logs().showError("Unable to run acquisition as MDA dialog is null");
         }
      }
      else {
         // Use the provided settings.
         engine_.setSequenceSettings(settings);
         try {
            store = engine_.acquire();
         }
         catch (MMException e) {
            throw new RuntimeException(e);
         }
      }
      if (isBlocking) {
         try {
            while (engine_.isAcquisitionRunning()) {
               Thread.sleep(50);
            }
         }
         catch (InterruptedException e) {
            studio_.logs().showError(e);
         }
      }
      return store;
   }

   @Override
   public void haltAcquisition() {
      engine_.abortRequest();
   }

   @Override
   public Datastore runAcquisition(String name, String root)
         throws IllegalThreadStateException {
      if (mdaDialog_ != null) {
         Datastore store = mdaDialog_.runAcquisition(name, root);
         try {
            while (!store.isFrozen()) {
               Thread.sleep(100);
            }
         } catch (InterruptedException e) {
            studio_.logs().showError(e);
         }
         return store;
      } else {
         throw new IllegalThreadStateException(
               "Acquisition setup window must be open for this command to work.");
      }
   }

   /**
    * Loads acquisition settings from file
    * @param path file containing previously saved acquisition settings
    * @throws java.io.IOException
    */
   @Override
   public void loadAcquisition(String path) throws IOException {
      engine_.shutdown();

      // load protocol
      if (mdaDialog_ != null) {
         mdaDialog_.loadAcqSettingsFromFile(path);
      }
   }

   @Override
   public SequenceSettings loadSequenceSettings(String path) throws IOException {
      return SequenceSettings.fromJSONStream(
            Files.toString(new File(path), Charsets.UTF_8));
   }

   @Override
   public void saveSequenceSettings(SequenceSettings settings, String path) throws IOException {
      File file = new File(path);
      try (FileWriter writer = new FileWriter(file)) {
         writer.write(SequenceSettings.toJSONStream(settings));
         writer.close();
      }
   }

   @Override
   public boolean isAcquisitionRunning() {
      if (engine_ == null)
         return false;
      return engine_.isAcquisitionRunning();
   }

   public AcquisitionWrapperEngine getAcquisitionEngine() {
      return engine_;
   }

   @Override
   public void setPause(boolean state) {
      getAcquisitionEngine().setPause(state);
   }

   @Override
   public boolean isPaused() {
      return getAcquisitionEngine().isPaused();
   }

   @Override
   public void attachRunnable(int frame, int position, int channel, int slice, Runnable runnable) {
      getAcquisitionEngine().attachRunnable(frame, position, channel, slice, runnable);
   }

   @Override
   public void clearRunnables() {
      getAcquisitionEngine().clearRunnables();
   }

   @Override
   public SequenceSettings getAcquisitionSettings() {
      if (engine_ == null) {
         return new SequenceSettings.Builder().build();
      }
      return engine_.getSequenceSettings();
   }

   @Override
   public void setAcquisitionSettings(SequenceSettings ss) {
      if (engine_ == null) {
         return;
      }
      engine_.setSequenceSettings(ss);
      mdaDialog_.updateGUIBlocking();
   }

   @Override
   public List<Image> snap() throws Exception {
      CMMCore core = studio_.core();
      if (core.getCameraDevice().length() == 0) {
         throw new RuntimeException("No camera configured.");
      }
      core.snapImage();
      ArrayList<Image> result = new ArrayList<>();
      for (int c = 0; c < core.getNumberOfCameraChannels(); ++c) {
         TaggedImage tagged = core.getTaggedImage(c);
         Image temp = new DefaultImage(tagged);
         Coords newCoords = temp.getCoords().copyBuilder().channel(c).build();
         Metadata newMetadata = temp.getMetadata().copyBuilderWithNewUUID().build();
         temp = temp.copyWith(newCoords, newMetadata);
         result.add(temp);
      }
      return result;
   }

   @Override
   public SummaryMetadata generateSummaryMetadata() {
      String computerName = null;
      try {
         computerName = InetAddress.getLocalHost().getHostName();
      }
      catch (UnknownHostException e) {}
      return new DefaultSummaryMetadata.Builder()
         .userName(System.getProperty("user.name"))
         .profileName(studio_.profile().getProfileName())
         .computerName(computerName)
         .build();
   }

   @Override
   public Metadata generateMetadata(Image image, boolean includeHardwareState) throws Exception {
      String camera = image.getMetadata().getCamera();
      if (camera == null) {
         camera = studio_.core().getCameraDevice();
      }

      MMStudio mmstudio = (MMStudio) studio_;
      Metadata.Builder result = image.getMetadata().copyBuilderWithNewUUID()
         .camera(camera)
         .receivedTime(DATE_FORMATTER.format(new Date()))
         .pixelSizeUm(mmstudio.cache().getPixelSizeUm())
         .pixelSizeAffine(mmstudio.cache().getPixelSizeAffine())
         .xPositionUm(mmstudio.cache().getStageX())
         .yPositionUm(mmstudio.cache().getStageY())
         .zPositionUm(mmstudio.cache().getStageZ())
         .bitDepth(mmstudio.cache().getImageBitDepth());

      try {
         String binning = studio_.core().getPropertyFromCache(
               camera, "Binning");
         if (binning.contains("x")) {
            // HACK: assume the binning parameter is e.g. "1x1" or "2x2" and
            // just take the first number.
            try {
               result.binning(Integer.parseInt(binning.split("x", 2)[0]));
            }
            catch (NumberFormatException e) {
               studio_.logs().logError("Unable to determine binning from " + binning);
            }
         }
         else {
            try {
               result.binning(Integer.parseInt(binning));
            }
            catch (NumberFormatException e) {
               studio_.logs().logError("Unable to determine binning from " + binning);
            }
         }
      }
      catch (Exception ignored) {
         // Again, this can fail if there is no camera.
      }
      if (includeHardwareState) {
         PropertyMap.Builder scopeBuilder = PropertyMaps.builder();
         Configuration config = studio_.core().getSystemStateCache();
         for (long i = 0; i < config.size(); ++i) {
            PropertySetting setting = config.getSetting(i);
            // NOTE: this key format chosen to match that used by the current
            // acquisition engine.
            scopeBuilder.putString(
                  setting.getDeviceLabel() + "-" + setting.getPropertyName(),
                  setting.getPropertyValue());
         }
         result.scopeData(scopeBuilder.build());
      }
      return result.build();
   }

   @Override
   public boolean isOurAcquisition(Object source) {
      return source == engine_;
   }
}
