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

import mmcorej.CMMCore;
import mmcorej.MMEventCallback;
import org.micromanager.acquisition.internal.AcquisitionWrapperEngine;
import org.micromanager.events.ConfigGroupChangedEvent;
import org.micromanager.events.ExposureChangedEvent;
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
 */
public final class CoreEventCallback extends MMEventCallback {

   private final CMMCore core_;
   private final AcquisitionWrapperEngine engine_;
   private volatile boolean ignorePropertyChanges_;

   @SuppressWarnings("LeakingThisInConstructor")
   public CoreEventCallback(CMMCore core, AcquisitionWrapperEngine engine) {
      super();
      core_ = core;
      engine_ = engine;
      core_.registerCallback(this);
   }

   @Override
   public void onPropertiesChanged() {
      // TODO: remove test once acquisition engine is fully multithreaded
      if (engine_ != null && engine_.isAcquisitionRunning()) {
         core_.logMessage("Notification from MMCore ignored because acquisition is running!", true);
      } else {
         if (ignorePropertyChanges_) {
            core_.logMessage("Notification from MMCore ignored since the system is still loading", true);
         } else {
            core_.updateSystemStateCache();
            DefaultEventManager.getInstance().post(
                  new PropertiesChangedEvent());
            core_.logMessage("Notification from MMCore!", true);
         }
      }
   }

   @Override
   public void onPropertyChanged(String deviceName, String propName, String propValue) {
      core_.logMessage("Notification for Device: " + deviceName + " Property: " +
            propName + " changed to value: " + propValue, true);
      DefaultEventManager.getInstance().post(
            new PropertyChangedEvent(deviceName, propName, propValue));
   }

   @Override
   public void onConfigGroupChanged(String groupName, String newConfig) {
      DefaultEventManager.getInstance().post(
            new ConfigGroupChangedEvent(groupName, newConfig));
   }
   
   @Override
   public void onSystemConfigurationLoaded() {
      DefaultEventManager.getInstance().post(
            new SystemConfigurationLoadedEvent());
   }

   @Override
   public void onPixelSizeChanged(double newPixelSizeUm) {
      DefaultEventManager.getInstance().post(
            new PixelSizeChangedEvent(newPixelSizeUm));
   }

   @Override
   public void onStagePositionChanged(String deviceName, double pos) {
      // TODO: this check should be in the core, not the java layer!
      if (deviceName.equals(core_.getFocusDevice())) {
         DefaultEventManager.getInstance().post(
               new StagePositionChangedEvent(deviceName, pos));
      }
   }

   @Override
   public void onXYStagePositionChanged(String deviceName, double xPos, double yPos) {
      // TODO: this check should be in the core, not the java layer!
      if (deviceName.equals(core_.getXYStageDevice())) {
         DefaultEventManager.getInstance().post(
               new XYStagePositionChangedEvent(deviceName, xPos, yPos));
      }
   }

   @Override
   public void onExposureChanged(String deviceName, double exposure) {
      DefaultEventManager.getInstance().post(
            new ExposureChangedEvent(deviceName, exposure));
   }

   @Override
   public void onSLMExposureChanged(String deviceName, double exposure) {
      DefaultEventManager.getInstance().post(
            new SLMExposureChangedEvent(deviceName, exposure));
   }

   public void setIgnoring(boolean isIgnoring) {
      ignorePropertyChanges_ = isIgnoring;
   }
}
