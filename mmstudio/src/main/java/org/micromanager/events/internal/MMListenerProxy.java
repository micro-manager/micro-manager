package org.micromanager.events.internal;

import com.google.common.eventbus.EventBus;

import org.micromanager.MMListenerInterface;

import org.micromanager.events.ConfigGroupChangedEvent;
import org.micromanager.events.ExposureChangedEvent;
import org.micromanager.events.PixelSizeChangedEvent;
import org.micromanager.events.PropertiesChangedEvent;
import org.micromanager.events.PropertyChangedEvent;
import org.micromanager.events.SLMExposureChangedEvent;
import org.micromanager.events.StagePositionChangedEvent;
import org.micromanager.events.SystemConfigurationLoadedEvent;
import org.micromanager.events.XYStagePositionChangedEvent;

import org.micromanager.events.internal.DefaultEventManager;

/**
 * This class wraps around each of the functions specified in 
 * MMListenerInterface, publishing an event for each, so that classes that are
 * only interested in some of the functions are not required to provide stub
 * implementations for all of them.
 */
public class MMListenerProxy implements MMListenerInterface {
   @Override
   public void propertiesChangedAlert() {
      DefaultEventManager.getInstance().post(
            new PropertiesChangedEvent());
   }

   @Override
   public void propertyChangedAlert(String device, String property,
         String value) {
      DefaultEventManager.getInstance().post(
            new PropertyChangedEvent(device, property, value));
   }

   @Override
   public void configGroupChangedAlert(String groupName, String newConfig) {
      DefaultEventManager.getInstance().post(
            new ConfigGroupChangedEvent(groupName, newConfig));
   }

   @Override
   public void systemConfigurationLoaded() {
      DefaultEventManager.getInstance().post(
            new SystemConfigurationLoadedEvent());
   }

   @Override
   public void pixelSizeChangedAlert(double newPixelSizeUm) {
      DefaultEventManager.getInstance().post(
            new PixelSizeChangedEvent(newPixelSizeUm));
   }

   @Override
   public void stagePositionChangedAlert(String deviceName, double pos) {
      DefaultEventManager.getInstance().post(
            new StagePositionChangedEvent(deviceName, pos));
   }

   @Override
   public void xyStagePositionChanged(String deviceName,
         double xPos, double yPos) {
      DefaultEventManager.getInstance().post(
            new XYStagePositionChangedEvent(deviceName, xPos, yPos));
   }

   @Override
   public void exposureChanged(String cameraName, double newExposureTime) {
      DefaultEventManager.getInstance().post(
            new ExposureChangedEvent(cameraName, newExposureTime));
   }
   
   @Override
   public void slmExposureChanged(String cameraName, double newExposureTime) {
      DefaultEventManager.getInstance().post(
            new SLMExposureChangedEvent(cameraName, newExposureTime));
   }
}

