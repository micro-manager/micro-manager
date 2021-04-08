package org.micromanager.events.internal;

import org.micromanager.events.PropertyChangedEvent;

public class DefaultPropertyChangedEvent implements PropertyChangedEvent {

   private final String device_;
   private final String property_;
   private final String value_;

   public DefaultPropertyChangedEvent(String device, String property, String value) {
      device_ = device;
      property_ = property;
      value_ = value;
   }
   public String getValue() {
      return value_;
   }
   public String getProperty() {
      return property_;
   }
   public String getDevice() {
      return device_;
   }
}
