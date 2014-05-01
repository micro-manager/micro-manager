package org.micromanager.api.events;

// This class provides information when a specific property changes.
public class PropertyChangedEvent {
   private String device_;
   private String property_;
   private String value_;
   public PropertyChangedEvent(String device, String property, String value) {
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

