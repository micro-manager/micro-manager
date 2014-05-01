package org.micromanager.api.events;

// This class provides information when a specific property changes.
public class PropertyChangedEvent {
   public String device_;
   public String property_;
   public String value_;
   public PropertyChangedEvent(String device, String property, String value) {
      device_ = device;
      property_ = property;
      value_ = value;
   }
}

