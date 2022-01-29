package org.micromanager.events.internal;

import org.micromanager.events.PropertyChangedEvent;

/**
 * This class provides information when a specific property changes.
 *
 * <p>This event posts on the Studio event bus,
 * so subscribe using {@link org.micromanager.events.EventManager}.</p>
 */
public class DefaultPropertyChangedEvent implements PropertyChangedEvent {

   private final String device_;
   private final String property_;
   private final String value_;

   /**
    * Event signalling that a Property changed its value.
    *
    * @param device Device to which this property belongs.
    * @param property Name of the Property that changed.
    * @param value New value of the Property.
    */
   public DefaultPropertyChangedEvent(String device, String property, String value) {
      device_ = device;
      property_ = property;
      value_ = value;
   }

   /**
    * Device to which the changed property belongs.
    *
    * @return Device to which the changed property belongs
    */
   public String getValue() {
      return value_;
   }

   /**
    * New value of the Property that changed.
    *
    * @return new value of the property
    */
   public String getProperty() {
      return property_;
   }

   /**
    * Name of the Property that changed.
    *
    * @return Property name (key)
    */
   public String getDevice() {
      return device_;
   }
}
