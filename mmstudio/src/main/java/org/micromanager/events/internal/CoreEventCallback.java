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

/**
 * Callback to update Java layer when a change happens in the MMCore. This
 * posts events on the EventManager's event bus.
 * Callbacks are all issued on the EDT to avoid deadlock
 */
public final class CoreEventCallback extends MMEventCallback {

   private final CMMCore core_;
   private final Studio studio_;
   private final AcquisitionWrapperEngine engine_;
   private volatile boolean ignoreCoreEvents_;

   /**
    * Receives Callbacks from the core and translates them into events posted
    * on the Studio's eventbus. Event posting can be interrupted using the
    * ignoreEvents_ flag.
    *
    * @param studio Our main Studio object (usually a singleton)
    * @param engine Acquisition engine object
    */
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
      } else if (ignoreCoreEvents_) {
         core_.logMessage("Notification from MMCore ignored", true);
      } else {
         core_.logMessage("Notification from MMCore!", true);
         core_.updateSystemStateCache();
         // see OnPropertyChanged for reasons to run this on the EDT
         SwingUtilities.invokeLater(() ->  studio_.events().post(
                    new DefaultPropertiesChangedEvent()));
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
      if (ignoreCoreEvents_) {
         core_.logMessage("Notification from MMCore ignored", true);
      } else {
         SwingUtilities.invokeLater(() -> studio_.events().post(
               new DefaultPropertyChangedEvent(deviceName, propName, propValue)));
      }
   }

   @Override
   public void onChannelGroupChanged(String newChannelGroupName) {
      if (ignoreCoreEvents_) {
         core_.logMessage("Notification from MMCore ignored", true);
      } else {
         SwingUtilities.invokeLater(() -> studio_.events().post(
               new DefaultChannelGroupChangedEvent(newChannelGroupName)));
      }
   }

   @Override
   public void onConfigGroupChanged(String groupName, String newConfig) {
      if (ignoreCoreEvents_) {
         core_.logMessage("Notification from MMCore ignored", true);
      } else {
         // see OnPropertyChanged for reasons to run this on the EDT
         SwingUtilities.invokeLater(() -> studio_.events().post(
               new DefaultConfigGroupChangedEvent(groupName, newConfig)));
      }
   }

   @Override
   public void onSystemConfigurationLoaded() {
      if (ignoreCoreEvents_) {
         core_.logMessage("Notification from MMCore ignored", true);
      } else {
         // see OnPropertyChanged for reasons to run this on the EDT
         SwingUtilities.invokeLater(() -> studio_.events().post(
               new DefaultSystemConfigurationLoadedEvent()));
      }
   }

   @Override
   public void onPixelSizeChanged(double newPixelSizeUm) {
      if (ignoreCoreEvents_) {
         core_.logMessage("Notification from MMCore ignored", true);
      } else {
         // see OnPropertyChanged for reasons to run this on the EDT
         SwingUtilities.invokeLater(() -> studio_.events().post(
               new DefaultPixelSizeChangedEvent(newPixelSizeUm)));
      }
   }
   
   @Override
   public void onPixelSizeAffineChanged(double npa0, double npa1, double npa2,
           double npa3, double npa4, double npa5) {
      if (ignoreCoreEvents_) {
         core_.logMessage("Notification from MMCore ignored", true);
      } else {
         double[] flatMatrix = {npa0, npa1, npa2, npa3, npa4, npa5};
         AffineTransform newPixelSizeAffine = new AffineTransform(flatMatrix);
         // see OnPropertyChanged for reasons to run this on the EDT
         SwingUtilities.invokeLater(() -> studio_.events().post(
               new DefaultPixelSizeAffineChangedEvent(newPixelSizeAffine)));
      }
   }

   @Override
   public void onStagePositionChanged(String deviceName, double pos) {
      if (ignoreCoreEvents_) {
         core_.logMessage("Notification from MMCore ignored", true);
      } else {
         // see OnPropertyChanged for reasons to run this on the EDT
         SwingUtilities.invokeLater(() -> studio_.events().post(
               new DefaultStagePositionChangedEvent(deviceName, pos)));
      }
   }

   @Override
   public void onXYStagePositionChanged(String deviceName, double xPos, double yPos) {
      if (ignoreCoreEvents_) {
         core_.logMessage("Notification from MMCore ignored", true);
      } else {
         // see OnPropertyChanged for reasons to run this on the EDT
         SwingUtilities.invokeLater(() -> studio_.events().post(
               new DefaultXYStagePositionChangedEvent(deviceName, xPos, yPos)));
      }
   }

   @Override
   public void onExposureChanged(String deviceName, double exposure) {
      if (ignoreCoreEvents_) {
         core_.logMessage("Notification from MMCore ignored", true);
      } else {
         // see OnPropertyChanged for reasons to run this on the EDT
         SwingUtilities.invokeLater(() -> studio_.events().post(
               new DefaultExposureChangedEvent(deviceName, exposure)));
      }
   }

   @Override
   public void onSLMExposureChanged(String deviceName, double exposure) {
      if (ignoreCoreEvents_) {
         core_.logMessage("Notification from MMCore ignored", true);
      } else {
         // see OnPropertyChanged for reasons to run this on the EDT
         SwingUtilities.invokeLater(() -> studio_.events().post(
               new DefaultSLMExposureChangedEvent(deviceName, exposure)));
      }
   }

   public void setIgnoring(boolean isIgnoring) {
      ignoreCoreEvents_ = isIgnoring;
   }
}