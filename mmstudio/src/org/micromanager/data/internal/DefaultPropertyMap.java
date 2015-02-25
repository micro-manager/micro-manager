package org.micromanager.data.internal;

import java.util.HashMap;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import org.micromanager.PropertyMap;
import org.micromanager.internal.utils.ReportingUtils;

// TODO: there is an awful lot of duplicated code here!
public class DefaultPropertyMap implements PropertyMap {
   // This gets thrown when someone tries to retrieve a value as the wrong type
   public static class PropertyValueMismatchException extends RuntimeException {
      public PropertyValueMismatchException(String message) {
         super(message);
      }
   }

   // This class stores one value in the mapping.
   private static class PropertyValue {
      private Object val_;
      private Class<?> type_;

      public PropertyValue(String val) {
         val_ = val;
         type_ = String.class;
      }
      public PropertyValue(String[] val) {
         val_ = val;
         type_ = String[].class;
      }

      public PropertyValue(Integer val) {
         val_ = val;
         type_ = Integer.class;
      }
      public PropertyValue(Integer[] val) {
         val_ = val;
         type_ = Integer[].class;
      }

      public PropertyValue(Double val) {
         val_ = val;
         type_ = Double.class;
      }
      public PropertyValue(Double[] val) {
         val_ = val;
         type_ = Double[].class;
      }

      public PropertyValue(Boolean val) {
         val_ = val;
         type_ = Boolean.class;
      }
      public PropertyValue(Boolean[] val) {
         val_ = val;
         type_ = Boolean[].class;
      }

      public String getAsString() {
         if (type_ != String.class) {
            throw new PropertyValueMismatchException("Type of value is not String");
         }
         return (String) val_;
      }
      public String[] getAsStringArray() {
         if (type_ != String[].class) {
            throw new PropertyValueMismatchException("Type of value is not String[]");
         }
         return (String[]) val_;
      }

      public Integer getAsInteger() {
         if (type_ != Integer.class) {
            throw new PropertyValueMismatchException("Type of value is not Integer");
         }
         return (Integer) val_;
      }
      public Integer[] getAsIntegerArray() {
         if (type_ != Integer[].class) {
            throw new PropertyValueMismatchException("Type of value is not Integer[]");
         }
         return (Integer[]) val_;
      }

      public Double getAsDouble() {
         if (type_ != Double.class) {
            throw new PropertyValueMismatchException("Type of value is not Double");
         }
         return (Double) val_;
      }
      public Double[] getAsDoubleArray() {
         if (type_ != Double[].class) {
            throw new PropertyValueMismatchException("Type of value is not Double[]");
         }
         return (Double[]) val_;
      }

      public Boolean getAsBoolean() {
         if (type_ != Boolean.class) {
            throw new PropertyValueMismatchException("Type of value is not Boolean");
         }
         return (Boolean) val_;
      }
      public Boolean[] getAsBooleanArray() {
         if (type_ != Boolean[].class) {
            throw new PropertyValueMismatchException("Type of value is not Boolean[]");
         }
         return (Boolean[]) val_;
      }

      public String serialize() {
         if (type_ == String.class) {
            return "String:" + ((String) val_);
         }
         else if (type_ == String[].class) {
            JSONArray tmp = new JSONArray();
            String[] vals = (String[]) val_;
            for (int i = 0; i < vals.length; ++i) {
               tmp.put(vals[i]);
            }
            return "StringArr:" + tmp.toString();
         }
         else if (type_ == Integer.class) {
            return "Integer:" + Integer.toString((Integer) val_);
         }
         else if (type_ == Integer[].class) {
            JSONArray tmp = new JSONArray();
            Integer[] vals = (Integer[]) val_;
            for (int i = 0; i < vals.length; ++i) {
               tmp.put(vals[i]);
            }
            return "IntegerArr:" + tmp.toString();
         }
         else if (type_ == Double.class) {
            return "Double:" + Double.toString((Double) val_);
         }
         else if (type_ == Double[].class) {
            JSONArray tmp = new JSONArray();
            Double[] vals = (Double[]) val_;
            for (int i = 0; i < vals.length; ++i) {
               tmp.put(vals[i]);
            }
            return "DoubleArr:" + tmp.toString();
         }
         else if (type_ == Boolean.class) {
            return "Boolean:" + Boolean.toString((Boolean) val_);
         }
         else if (type_ == Boolean[].class) {
            JSONArray tmp = new JSONArray();
            Boolean[] vals = (Boolean[]) val_;
            for (int i = 0; i < vals.length; ++i) {
               tmp.put(vals[i]);
            }
            return "BooleanArr:" + tmp.toString();
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
      public PropertyMap.PropertyMapBuilder putStringArray(String key, String[] values) {
         propMap_.put(key, new PropertyValue(values));
         return this;
      }
      
      @Override
      public PropertyMap.PropertyMapBuilder putInt(String key, Integer value) {
         propMap_.put(key, new PropertyValue(value));
         return this;
      }
      @Override
      public PropertyMap.PropertyMapBuilder putIntArray(String key, Integer[] values) {
         propMap_.put(key, new PropertyValue(values));
         return this;
      }

      @Override
      public PropertyMap.PropertyMapBuilder putDouble(String key, Double value) {
         propMap_.put(key, new PropertyValue(value));
         return this;
      }
      @Override
      public PropertyMap.PropertyMapBuilder putDoubleArray(String key, Double[] values) {
         propMap_.put(key, new PropertyValue(values));
         return this;
      }

      @Override
      public PropertyMap.PropertyMapBuilder putBoolean(String key, Boolean value) {
         propMap_.put(key, new PropertyValue(value));
         return this;
      }
      @Override
      public PropertyMap.PropertyMapBuilder putBooleanArray(String key, Boolean[] values) {
         propMap_.put(key, new PropertyValue(values));
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
   public String[] getStringArray(String key) {
      if (propMap_.containsKey(key)) {
         return propMap_.get(key).getAsStringArray();
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
   public Integer[] getIntArray(String key) {
      if (propMap_.containsKey(key)) {
         return propMap_.get(key).getAsIntegerArray();
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
   public Double[] getDoubleArray(String key) {
      if (propMap_.containsKey(key)) {
         return propMap_.get(key).getAsDoubleArray();
      }
      return null;
   }

   @Override
   public Boolean getBoolean(String key) {
      if (propMap_.containsKey(key)) {
         return propMap_.get(key).getAsBoolean();
      }
      return null;
   }
   @Override
   public Boolean[] getBooleanArray(String key) {
      if (propMap_.containsKey(key)) {
         return propMap_.get(key).getAsBooleanArray();
      }
      return null;
   }

   @Override
   public PropertyMap merge(PropertyMap alt) {
      // We need access to "internal" methods that aren't exposed in the API.
      DefaultPropertyMap defaultAlt = (DefaultPropertyMap) alt;
      Builder builder = (Builder) copy();
      for (String key : defaultAlt.getKeys()) {
         builder.putProperty(key, defaultAlt.getProperty(key));
      }
      return builder.build();
   }

   public Set<String> getKeys() {
      return propMap_.keySet();
   }

   public PropertyValue getProperty(String key) {
      return propMap_.get(key);
   }

   public JSONObject toJSON() {
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

   public static DefaultPropertyMap fromJSON(JSONObject map) throws JSONException {
      Builder builder = new Builder();
      JSONArray keys = map.names();
      for (int i = 0; i < keys.length(); ++i) {
         String key = keys.getString(i);
         String val = map.getString(key);
         if (val.startsWith("String:")) {
            builder.putString(key, val.substring(7, val.length()));
         }
         else if (val.startsWith("StringArr:")) {
            JSONArray tmp = new JSONArray(val.substring(10, val.length()));
            String[] valArr = new String[tmp.length()];
            for (int j = 0; j < tmp.length(); ++j) {
               valArr[j] = tmp.getString(j);
            }
            builder.putStringArray(key, valArr);
         }
         else if (val.startsWith("Integer:")) {
            builder.putInt(key,
                  Integer.parseInt(val.substring(8, val.length())));
         }
         else if (val.startsWith("IntegerArr:")) {
            JSONArray tmp = new JSONArray(val.substring(11, val.length()));
            Integer[] valArr = new Integer[tmp.length()];
            for (int j = 0; j < tmp.length(); ++j) {
               valArr[j] = tmp.getInt(j);
            }
            builder.putIntArray(key, valArr);
         }
         else if (val.startsWith("Double:")) {
            builder.putDouble(key,
                  Double.parseDouble(val.substring(7, val.length())));
         }
         else if (val.startsWith("DoubleArr:")) {
            JSONArray tmp = new JSONArray(val.substring(10, val.length()));
            Double[] valArr = new Double[tmp.length()];
            for (int j = 0; j < tmp.length(); ++j) {
               valArr[j] = tmp.getDouble(j);
            }
            builder.putDoubleArray(key, valArr);
         }
         else if (val.startsWith("Boolean:")) {
            builder.putBoolean(key,
                  Boolean.parseBoolean(val.substring(8, val.length())));
         }
         else if (val.startsWith("BooleanArr:")) {
            JSONArray tmp = new JSONArray(val.substring(11, val.length()));
            Boolean[] valArr = new Boolean[tmp.length()];
            for (int j = 0; j < tmp.length(); ++j) {
               valArr[j] = tmp.getBoolean(j);
            }
            builder.putBooleanArray(key, valArr);
         }
         else {
            throw new IllegalArgumentException(
                  "Illegal value for property: [" + val + "]");
         }
      }
      return (DefaultPropertyMap) builder.build();
   }
}
