package org.micromanager.data.internal;

import java.util.HashMap;

import org.micromanager.PropertyMap;

public class DefaultPropertyMap implements PropertyMap {
   // This class stores one value in the mapping.
   private static class PropertyValue {
      private Object val_;
      private Class<?> type_;

      public PropertyValue(String val) {
         val_ = val;
         type_ = String.class;
      }

      public PropertyValue(Integer val) {
         val_ = val;
         type_ = Integer.class;
      }

      public PropertyValue(Double val) {
         val_ = val;
         type_ = Double.class;
      }

      public String getAsString() {
         if (type_ != String.class) {
            return null;
         }
         return (String) val_;
      }

      public Integer getAsInteger() {
         if (type_ != Integer.class) {
            return null;
         }
         return (Integer) val_;
      }

      public Double getAsDouble() {
         if (type_ != Double.class) {
            return null;
         }
         return (Double) val_;
      }
   }
   public static class Builder implements PropertyMap.PropertyMapBuilder {
      private HashMap<String, PropertyValue> propMap_;

      public Builder() {
         propMap_ = new HashMap<String, PropertyValue>();
      }

      @Override
      public PropertyMap build() {
         return new DefaultPropertyMap(this);
      }

      @Override
      public PropertyMap.PropertyMapBuilder putString(String key, String value) {
         propMap_.put(key, new PropertyValue(value));
         return this;
      }
      
      @Override
      public PropertyMap.PropertyMapBuilder putInt(String key, Integer value) {
         propMap_.put(key, new PropertyValue(value));
         return this;
      }

      @Override
      public PropertyMap.PropertyMapBuilder putDouble(String key, Double value) {
         propMap_.put(key, new PropertyValue(value));
         return this;
      }

      /**
       * This method is used by PropertyMap.copy(), below.
       */
      public void putProperty(String key, PropertyValue val) {
         propMap_.put(key, val);
      }
   }

   private HashMap<String, PropertyValue> propMap_;

   public DefaultPropertyMap(Builder builder) {
      propMap_ = new HashMap<String, PropertyValue>(builder.propMap_);
   }

   @Override
   public PropertyMap.PropertyMapBuilder copy() {
      Builder builder = new Builder();
      for (String key : propMap_.keySet()) {
         builder.putProperty(key, propMap_.get(key));
      }
      return builder;
   }

   @Override
   public String getString(String key) {
      if (propMap_.containsKey(key)) {
         return propMap_.get(key).getAsString();
      }
      return null;
   }

   @Override
   public Integer getInt(String key) {
      if (propMap_.containsKey(key)) {
         return propMap_.get(key).getAsInteger();
      }
      return null;
   }

   @Override
   public Double getDouble(String key) {
      if (propMap_.containsKey(key)) {
         return propMap_.get(key).getAsDouble();
      }
      return null;
   }
}
