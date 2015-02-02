package org.micromanager.data.internal;

import java.util.HashMap;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import org.micromanager.PropertyMap;
import org.micromanager.internal.utils.ReportingUtils;

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

      public String serialize() {
         if (type_ == String.class) {
            return "String:" + ((String) val_);
         }
         else if (type_ == Integer.class) {
            return "Integer:" + Integer.toString((Integer) val_);
         }
         else if (type_ == Double.class) {
            return "Double:" + Double.toString((Double) val_);
         }
         else {
            throw new RuntimeException("Unexpected property value type " + type_);
         }
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

   @Override
   public JSONObject legacyToJSON() {
      JSONObject result = new JSONObject();
      for (String key : propMap_.keySet()) {
         try {
            result.put(key, propMap_.get(key).serialize());
         }
         catch (JSONException e) {
            ReportingUtils.logError(e, "Couldn't add property [" + key + "] to JSONified map");
         }
      }
      return result;
   }

   public static PropertyMap legacyFromJSON(JSONObject map) throws JSONException {
      Builder builder = new Builder();
      JSONArray keys = map.names();
      for (int i = 0; i < keys.length(); ++i) {
         String key = keys.getString(i);
         String val = map.getString(key);
         if (val.startsWith("String:")) {
            builder.putString(key, val.substring(6, val.length()));
         }
         else if (val.startsWith("Integer:")) {
            builder.putInt(key,
                  Integer.parseInt(val.substring(7, val.length())));
         }
         else if (val.startsWith("Double:")) {
            builder.putDouble(key,
                  Double.parseDouble(val.substring(6, val.length())));
         }
         else {
            throw new IllegalArgumentException(
                  "Illegal value for property: [" + val + "]");
         }
      }
      return builder.build();
   }
}
