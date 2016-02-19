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

import ij.ImagePlus;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import javax.swing.SwingUtilities;

import mmcorej.CMMCore;
import mmcorej.Configuration;
import mmcorej.PropertySetting;
import mmcorej.TaggedImage;

import org.micromanager.acquisition.AcquisitionManager;
import org.micromanager.data.Coords;
import org.micromanager.data.Datastore;
import org.micromanager.data.Image;
import org.micromanager.data.internal.DefaultImage;
import org.micromanager.data.internal.DefaultMetadata;
import org.micromanager.data.internal.DefaultMetadata;
import org.micromanager.data.internal.DefaultSummaryMetadata;
import org.micromanager.data.Metadata;
import org.micromanager.data.SummaryMetadata;
import org.micromanager.PropertyMap;
import org.micromanager.SequenceSettings;
import org.micromanager.Studio;

import org.micromanager.internal.dialogs.AcqControlDlg;
import org.micromanager.internal.MMStudio;
import org.micromanager.internal.utils.MMException;
import org.micromanager.internal.utils.MMScriptException;

/**
 * TODO: this class still depends on MMStudio for many things.
 */
public class DefaultAcquisitionManager implements AcquisitionManager {
   // NOTE: should match the format used by the acquisition engine.
   private static final SimpleDateFormat formatter_ = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z");

   private Studio studio_;
   private AcquisitionWrapperEngine engine_;
   private AcqControlDlg mdaDialog_;

   public DefaultAcquisitionManager(Studio studio,
         AcquisitionWrapperEngine engine, AcqControlDlg mdaDialog) {
      studio_ = studio;
      engine_ = engine;
      mdaDialog_ = mdaDialog;
   }

   @Override
   public Datastore runAcquisition() throws MMScriptException {
      return executeAcquisition(null, true);
   }

   @Override
   public Datastore runAcquisitionNonblocking() throws MMScriptException {
      return executeAcquisition(null, false);
   }

   @Override
   public Datastore runAcquisitionWithSettings(SequenceSettings settings,
         boolean shouldBlock) throws MMScriptException {
      return executeAcquisition(settings, shouldBlock);
   }

   private Datastore executeAcquisition(SequenceSettings settings, boolean isBlocking) throws MMScriptException {
      if (SwingUtilities.isEventDispatchThread()) {
         throw new MMScriptException("Acquisition can not be run from this (EDT) thread");
      }
      ((MMStudio) studio_).testForAbortRequests();
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
            throw new MMScriptException(e.toString());
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
         throws MMScriptException {
      ((MMStudio) studio_).testForAbortRequests();
      if (mdaDialog_ != null) {
         Datastore store = mdaDialog_.runAcquisition(name, root);
         try {
            while (!store.getIsFrozen()) {
               Thread.sleep(100);
            }
         } catch (InterruptedException e) {
            studio_.logs().showError(e);
         }
         return store;
      } else {
         throw new MMScriptException(
               "Acquisition setup window must be open for this command to work.");
      }
   }

   /**
    * Loads acquisition settings from file
    * @param path file containing previously saved acquisition settings
    * @throws MMScriptException 
    */
   @Override
   public void loadAcquisition(String path) throws MMScriptException {
      ((MMStudio) studio_).testForAbortRequests();
      try {
         engine_.shutdown();

         // load protocol
         if (mdaDialog_ != null) {
            mdaDialog_.loadAcqSettingsFromFile(path);
         }
      } catch (MMScriptException ex) {
         throw new MMScriptException(ex.getMessage());
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
      if (engine_ == null)
         return new SequenceSettings();
      return engine_.getSequenceSettings();
   }

   @Override
   public void setAcquisitionSettings(SequenceSettings ss) {
      if (engine_ == null)
         return;

      engine_.setSequenceSettings(ss);
      mdaDialog_.updateGUIContents();
   }

   @Override
   public List<Image> snap() throws Exception {
      CMMCore core = studio_.core();
      if (core.getCameraDevice().length() == 0) {
         throw new RuntimeException("No camera configured.");
      }
      core.snapImage();
      ArrayList<Image> result = new ArrayList<Image>();
      for (int c = 0; c < core.getNumberOfCameraChannels(); ++c) {
         TaggedImage tagged = core.getTaggedImage(c);
         Image temp = new DefaultImage(tagged);
         Coords newCoords = temp.getCoords().copy().channel(c).build();
         Metadata newMetadata = temp.getMetadata().copy()
            .uuid(UUID.randomUUID()).build();
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
         .microManagerVersion(studio_.compat().getVersion())
         .metadataVersion(DefaultSummaryMetadata.METADATA_VERSION)
         .computerName(computerName)
         .build();
   }

   @Override
   public Metadata generateMetadata(Image image, boolean includeHardwareState) throws Exception {
      String camera = studio_.core().getCameraDevice();
      int ijType = -1;
      String pixelType = null;
      if (image.getNumComponents() == 1) {
         if (image.getBytesPerPixel() == 1) {
            ijType = ImagePlus.GRAY8;
            pixelType = "GRAY8";
         }
         else if (image.getBytesPerPixel() == 2) {
            ijType = ImagePlus.GRAY16;
            pixelType = "GRAY16";
         }
         else {
            throw new IllegalArgumentException("Unrecognized pixel type");
         }
      }
      else {
         if (image.getBytesPerPixel() == 4) {
            ijType = ImagePlus.COLOR_RGB;
            pixelType = "RGB32";
         }
         else {
            throw new IllegalArgumentException("Unrecognized pixel type");
         }
      }
      Metadata.MetadataBuilder result = new DefaultMetadata.Builder()
         // TODO: do we have a better way to get integer property values?
         .binning(Integer.parseInt(studio_.core().getProperty(camera, "Binning")))
         .bitDepth((int) studio_.core().getImageBitDepth())
         .camera(camera)
         .ijType(ijType)
         .pixelType(pixelType)
         .receivedTime(formatter_.format(new Date()))
         .uuid(UUID.randomUUID())
         .xPositionUm(studio_.core().getXYStagePosition().x)
         .yPositionUm(studio_.core().getXYStagePosition().y)
         .zPositionUm(studio_.core().getPosition());
      if (includeHardwareState) {
         PropertyMap.PropertyMapBuilder scopeBuilder = studio_.data().getPropertyMapBuilder();
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
}
