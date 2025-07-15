package org.micromanager.events.internal;

import org.micromanager.events.ShutterOpenChangedEvent;

public class DefaultShutterOpenChangedEvent implements ShutterOpenChangedEvent {
   String deviceName_;
   boolean open_;

   public DefaultShutterOpenChangedEvent(String deviceName, boolean open) {
      deviceName_ = deviceName;
      open_ = open;
   }

   @Override
   public String getDeviceName() {
      return deviceName_;
   }

   @Override
   public boolean isShutterOpened() {
      return open_;
   }

}
