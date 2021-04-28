package org.micromanager.events.internal;

import org.micromanager.events.PropertyChangedEvent;

/**
 * This class provides information when a specific property changes.
 *
 * This event posts on the Studio event bus,
 * so subscribe using {@link org.micromanager.events.EventManager}.
 */
public class DefaultPropertyChangedEvent implements PropertyChangedEvent {

   private final String device_;
   private final String property_;
   private final String value_;

   public DefaultPropertyChangedEvent(String device, String property, String value) {
      device_ = device;
      property_ = property;
      value_ = value;
   }
   /**
    * Device to which the changed property belongs
    * @return Device to which the changed property belongs
    */
   public String getValue() {
      return value_;
   }

   /**
    * @return new value of the property
    */
   public String getProperty() {
      return property_;
   }

   /**
    * @return Property name (key)
    */
   public String getDevice() {
      return device_;
   }
}
