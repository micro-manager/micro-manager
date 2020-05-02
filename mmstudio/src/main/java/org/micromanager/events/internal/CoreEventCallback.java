///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       
//
// COPYRIGHT:    University of California, San Francisco, 2014
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
//

package org.micromanager.events.internal;

import java.awt.geom.AffineTransform;
import javax.swing.SwingUtilities;
import mmcorej.CMMCore;
import mmcorej.MMEventCallback;
import org.micromanager.Studio;
import org.micromanager.acquisition.internal.AcquisitionWrapperEngine;
import org.micromanager.events.ChannelGroupChangedEvent;
import org.micromanager.events.ConfigGroupChangedEvent;
import org.micromanager.events.ExposureChangedEvent;
import org.micromanager.events.PixelSizeAffineChangedEvent;
import org.micromanager.events.PixelSizeChangedEvent;
import org.micromanager.events.PropertiesChangedEvent;
import org.micromanager.events.PropertyChangedEvent;
import org.micromanager.events.SLMExposureChangedEvent;
import org.micromanager.events.StagePositionChangedEvent;
import org.micromanager.events.SystemConfigurationLoadedEvent;
import org.micromanager.events.XYStagePositionChangedEvent;

/**
 * Callback to update Java layer when a change happens in the MMCore. This
 * posts events on the EventManager's event bus.
 * Callbacks are all issued on the EDT to avoid deadlock
 */
public final class CoreEventCallback extends MMEventCallback {

   private final CMMCore core_;
   private final Studio studio_;
   private final AcquisitionWrapperEngine engine_;
   private volatile boolean ignorePropertyChanges_;

   @SuppressWarnings("LeakingThisInConstructor")
   public CoreEventCallback(Studio studio, AcquisitionWrapperEngine engine) {
      super();
      studio_ = studio;
      core_ = studio.core();
      engine_ = engine;
      core_.registerCallback(this);
   }

   @Override
   public void onPropertiesChanged() {
      // TODO: remove test once acquisition engine is fully multithreaded
      if (engine_ != null && engine_.isAcquisitionRunning()) {
         core_.logMessage("Notification from MMCore ignored because acquisition is running!", true);
      } else if (ignorePropertyChanges_) {
         core_.logMessage("Notification from MMCore ignored since the system is still loading", true);
      } else {
         core_.logMessage("Notification from MMCore!", true);
         core_.updateSystemStateCache();
         // see OnPropertyChanged for reasons to run this on the EDT
         SwingUtilities.invokeLater(() ->  studio_.events().post(
                    new PropertiesChangedEvent()));
      }
   }

   @Override
   public void onPropertyChanged(String deviceName, String propName, String propValue) {
      core_.logMessage("Notification for Device: " + deviceName + " Property: "
              + propName + " changed to value: " + propValue, true);
      // Not running this on the EDT causes rare deadlocks, for instance:
      // user stops or starts live mode while a callback is received will
      // result in deadlock.  Hopefully, always running this on the EDT
      // will fix this, as its main purpose is providing user feedback.
      // To avoid a callback on the EDT calling back into the Core, resulting
      // in further callbacks, always run this through invokeLater,
      // (see https://github.com/micro-manager/micro-manager/issues/498)
      SwingUtilities.invokeLater(() -> studio_.events().post(
              new PropertyChangedEvent(deviceName, propName, propValue)));
   }

   @Override
   public void onChannelGroupChanged(String newChannelGroupName) {
      SwingUtilities.invokeLater(() -> studio_.events().post(
              new DefaultChannelGroupChangedEvent(newChannelGroupName)));
   }

   @Override
   public void onConfigGroupChanged(String groupName, String newConfig) {
      // see OnPropertyChanged for reasons to run this on the EDT
      SwingUtilities.invokeLater(() -> studio_.events().post(
              new ConfigGroupChangedEvent(groupName, newConfig)));
   }

   @Override
   public void onSystemConfigurationLoaded() {
      // see OnPropertyChanged for reasons to run this on the EDT
      SwingUtilities.invokeLater(() -> studio_.events().post(
              new SystemConfigurationLoadedEvent()));
   }

   @Override
   public void onPixelSizeChanged(double newPixelSizeUm) {
      // see OnPropertyChanged for reasons to run this on the EDT
      SwingUtilities.invokeLater(() -> studio_.events().post(
              new PixelSizeChangedEvent(newPixelSizeUm)));
   }
   
   @Override
   public void onPixelSizeAffineChanged(double npa0, double npa1, double npa2,
           double npa3, double npa4, double npa5) {
      double[] flatMatrix = {npa0, npa1, npa2, npa3, npa4, npa5};
      AffineTransform newPixelSizeAffine = new AffineTransform(flatMatrix);
      // see OnPropertyChanged for reasons to run this on the EDT
      SwingUtilities.invokeLater(() -> studio_.events().post(
              new PixelSizeAffineChangedEvent(newPixelSizeAffine)));
   }

   @Override
   public void onStagePositionChanged(String deviceName, double pos) {
      // see OnPropertyChanged for reasons to run this on the EDT
      SwingUtilities.invokeLater(() -> studio_.events().post(
              new StagePositionChangedEvent(deviceName, pos)));
   }

   @Override
   public void onXYStagePositionChanged(String deviceName, double xPos, double yPos) {
      // see OnPropertyChanged for reasons to run this on the EDT
      SwingUtilities.invokeLater(() -> studio_.events().post(
              new XYStagePositionChangedEvent(deviceName, xPos, yPos)));
   }

   @Override
   public void onExposureChanged(String deviceName, double exposure) {
      // see OnPropertyChanged for reasons to run this on the EDT
      SwingUtilities.invokeLater(() -> studio_.events().post(
              new ExposureChangedEvent(deviceName, exposure)));
   }

   @Override
   public void onSLMExposureChanged(String deviceName, double exposure) {
      // see OnPropertyChanged for reasons to run this on the EDT
      SwingUtilities.invokeLater(() -> studio_.events().post(
              new SLMExposureChangedEvent(deviceName, exposure)));
   }

   public void setIgnoring(boolean isIgnoring) {
      ignorePropertyChanges_ = isIgnoring;
   }
}
