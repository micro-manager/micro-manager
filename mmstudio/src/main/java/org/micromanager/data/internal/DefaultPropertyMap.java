///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     Data API implementation
//-----------------------------------------------------------------------------
//
// AUTHOR:       Chris Weisiger, 2015
//
// COPYRIGHT:    University of California, San Francisco, 2015
//
// LICENSE:      This file is distributed under the BSD license.
//               License text is included with the source distribution.
//
//               This file is distributed in the hope that it will be useful,
//               but WITHOUT ANY WARRANTY; without even the implied warranty
//               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.

package org.micromanager.data.internal;

import com.google.common.io.BaseEncoding;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import org.micromanager.PropertyMap;
import org.micromanager.internal.utils.ReportingUtils;

// TODO: there is an awful lot of duplicated code here!
public class DefaultPropertyMap implements PropertyMap {
   private static final String TYPE = "PropType";
   private static final String VALUE = "PropVal";
   private static final String STRING = "String";
   private static final String STRING_ARRAY = "String array";
   private static final String INTEGER = "Integer";
   private static final String INTEGER_ARRAY = "Integer array";
   private static final String LONG = "Long";
   private static final String LONG_ARRAY = "Long array";
   private static final String DOUBLE = "Double";
   private static final String DOUBLE_ARRAY = "Double array";
   private static final String BOOLEAN = "Boolean";
   private static final String BOOLEAN_ARRAY = "Boolean array";
   private static final String OBJECT = "Object";

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

      public PropertyValue(Long val) {
         val_ = val;
         type_ = Long.class;
      }
      public PropertyValue(Long[] val) {
         val_ = val;
         type_ = Long[].class;
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

      // Used for storing generic objects.
      public PropertyValue(byte[] val) {
         val_ = val;
         type_ = byte[].class;
      }

      public String getAsString() {
         if (type_ != String.class) {
            throw new PropertyMap.TypeMismatchException("Type of value is not String");
         }
         return (String) val_;
      }
      public String[] getAsStringArray() {
         if (type_ != String[].class) {
            throw new PropertyMap.TypeMismatchException("Type of value is not String[]");
         }
         return (String[]) val_;
      }

      public Integer getAsInteger() {
         if (type_ != Integer.class) {
            throw new PropertyMap.TypeMismatchException("Type of value is not Integer");
         }
         return (Integer) val_;
      }
      public Integer[] getAsIntegerArray() {
         if (type_ != Integer[].class) {
            throw new PropertyMap.TypeMismatchException("Type of value is not Integer[]");
         }
         return (Integer[]) val_;
      }

      public Long getAsLong() {
         if (type_ != Long.class) {
            throw new PropertyMap.TypeMismatchException("Type of value is not Long");
         }
         return (Long) val_;
      }
      public Long[] getAsLongArray() {
         if (type_ != Long[].class) {
            throw new PropertyMap.TypeMismatchException("Type of value is not Long[]");
         }
         return (Long[]) val_;
      }

      public Double getAsDouble() {
         if (type_ != Double.class) {
            throw new PropertyMap.TypeMismatchException("Type of value is not Double");
         }
         return (Double) val_;
      }
      public Double[] getAsDoubleArray() {
         if (type_ != Double[].class) {
            throw new PropertyMap.TypeMismatchException("Type of value is not Double[]");
         }
         return (Double[]) val_;
      }

      public Boolean getAsBoolean() {
         if (type_ != Boolean.class) {
            throw new PropertyMap.TypeMismatchException("Type of value is not Boolean");
         }
         return (Boolean) val_;
      }
      public Boolean[] getAsBooleanArray() {
         if (type_ != Boolean[].class) {
            throw new PropertyMap.TypeMismatchException("Type of value is not Boolean[]");
         }
         return (Boolean[]) val_;
      }

      public byte[] getAsByteArray() {
         if (type_ != byte[].class) {
            throw new PropertyMap.TypeMismatchException("Type of value is not byte[]");
         }
         return (byte[]) val_;
      }

      public JSONObject serialize() {
         if (val_ == null) {
            return null;
         }
         JSONObject result = new JSONObject();
         try {
            if (type_ == String.class) {
               result.put(TYPE, STRING);
               result.put(VALUE, (String) val_);
            }
            else if (type_ == String[].class) {
               result.put(TYPE, STRING_ARRAY);
               JSONArray tmp = new JSONArray();
               String[] vals = (String[]) val_;
               for (int i = 0; i < vals.length; ++i) {
                  tmp.put(vals[i]);
               }
               result.put(VALUE, tmp);
            }
            else if (type_ == Integer.class) {
               result.put(TYPE, INTEGER);
               result.put(VALUE, (Integer) val_);
            }
            else if (type_ == Integer[].class) {
               result.put(TYPE, INTEGER_ARRAY);
               JSONArray tmp = new JSONArray();
               Integer[] vals = (Integer[]) val_;
               for (int i = 0; i < vals.length; ++i) {
                  tmp.put(vals[i]);
               }
               result.put(VALUE, tmp);
            }
            else if (type_ == Long.class) {
               result.put(TYPE, LONG);
               result.put(VALUE, (Long) val_);
            }
            else if (type_ == Long[].class) {
               result.put(TYPE, LONG_ARRAY);
               JSONArray tmp = new JSONArray();
               Long[] vals = (Long[]) val_;
               for (int i = 0; i < vals.length; ++i) {
                  tmp.put(vals[i]);
               }
               result.put(VALUE, tmp);
            }
            else if (type_ == Double.class) {
               result.put(TYPE, DOUBLE);
               result.put(VALUE, (Double) val_);
            }
            else if (type_ == Double[].class) {
               result.put(TYPE, DOUBLE_ARRAY);
               JSONArray tmp = new JSONArray();
               Double[] vals = (Double[]) val_;
               for (int i = 0; i < vals.length; ++i) {
                  tmp.put(vals[i]);
               }
               result.put(VALUE, tmp);
            }
            else if (type_ == Boolean.class) {
               result.put(TYPE, BOOLEAN);
               result.put(VALUE, (Boolean) val_);
            }
            else if (type_ == Boolean[].class) {
               result.put(TYPE, BOOLEAN_ARRAY);
               JSONArray tmp = new JSONArray();
               Boolean[] vals = (Boolean[]) val_;
               for (int i = 0; i < vals.length; ++i) {
                  tmp.put(vals[i]);
               }
               result.put(VALUE, tmp);
            }
            else if (type_ == byte[].class) {
               result.put(TYPE, OBJECT);
               // Store as base64-encoded string.
               String val = BaseEncoding.base64().encode((byte[]) val_);
               result.put(VALUE, val);
            }
            else {
               throw new PropertyMap.TypeMismatchException("Unexpected property value type " + type_);
            }
         }
         catch (JSONException e) {
            ReportingUtils.logError(e, "Error generating JSON for a property");
         }
         return result;
      }

      public Object getVal() {
         return val_;
      }
      public Class getType() {
         return type_;
      }

      @Override
      public boolean equals(Object obj) {
         if (!(obj instanceof PropertyValue)) {
            return false;
         }
         PropertyValue alt = (PropertyValue) obj;
         if (!alt.getType().equals(type_)) {
            return false;
         }
         if (type_.isArray()) {
            return Arrays.deepEquals((Object[]) val_,
                  (Object[]) (alt.getVal()));
         }
         return alt.getVal().equals(val_);
      }

      @Override
      public String toString() {
         return String.format("<PropertyValue with type %s and value %s>",
               type_, val_);
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
      public PropertyMap.PropertyMapBuilder putLong(String key, Long value) {
         propMap_.put(key, new PropertyValue(value));
         return this;
      }
      @Override
      public PropertyMap.PropertyMapBuilder putLongArray(String key, Long[] values) {
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

      @Override
      public PropertyMap.PropertyMapBuilder putObject(String key, Object val) {
         // Convert the object to a byte array.
         ObjectOutputStream objectStream = null;
         try {
            ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
            objectStream = new ObjectOutputStream(byteStream);
            objectStream.writeObject(val);
            byte[] bytes = byteStream.toByteArray();
            objectStream.close();
            propMap_.put(key, new PropertyValue(bytes));
         }
         catch (IOException e) {
            ReportingUtils.logError(e, "Error converting object " + val +
                  " to byte array");
         }
         finally {
            try {
               if (objectStream != null) {
                  objectStream.close();
               }
            }
            catch (IOException e) {
               ReportingUtils.logError(e, "Failed to close outputStream");
            }
         }
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
   public String getString(String key, String defaultVal) {
      if (propMap_.containsKey(key)) {
         return propMap_.get(key).getAsString();
      }
      return defaultVal;
   }
   @Override
   public String[] getStringArray(String key) {
      if (propMap_.containsKey(key)) {
         return propMap_.get(key).getAsStringArray();
      }
      return null;
   }
   @Override
   public String[] getStringArray(String key, String[] defaultVal) {
      if (propMap_.containsKey(key)) {
         return propMap_.get(key).getAsStringArray();
      }
      return defaultVal;
   }

   @Override
   public Integer getInt(String key) {
      if (propMap_.containsKey(key)) {
         return propMap_.get(key).getAsInteger();
      }
      return null;
   }
   @Override
   public Integer getInt(String key, Integer defaultVal) {
      if (propMap_.containsKey(key)) {
         return propMap_.get(key).getAsInteger();
      }
      return defaultVal;
   }
   @Override
   public Integer[] getIntArray(String key) {
      if (propMap_.containsKey(key)) {
         return propMap_.get(key).getAsIntegerArray();
      }
      return null;
   }
   @Override
   public Integer[] getIntArray(String key, Integer[] defaultVal) {
      if (propMap_.containsKey(key)) {
         return propMap_.get(key).getAsIntegerArray();
      }
      return defaultVal;
   }

   @Override
   public Long getLong(String key) {
      if (propMap_.containsKey(key)) {
         return propMap_.get(key).getAsLong();
      }
      return null;
   }
   @Override
   public Long getLong(String key, Long defaultVal) {
      if (propMap_.containsKey(key)) {
         return propMap_.get(key).getAsLong();
      }
      return defaultVal;
   }
   @Override
   public Long[] getLongArray(String key) {
      if (propMap_.containsKey(key)) {
         return propMap_.get(key).getAsLongArray();
      }
      return null;
   }
   @Override
   public Long[] getLongArray(String key, Long[] defaultVal) {
      if (propMap_.containsKey(key)) {
         return propMap_.get(key).getAsLongArray();
      }
      return defaultVal;
   }

   @Override
   public Double getDouble(String key) {
      if (propMap_.containsKey(key)) {
         return propMap_.get(key).getAsDouble();
      }
      return null;
   }
   @Override
   public Double getDouble(String key, Double defaultVal) {
      if (propMap_.containsKey(key)) {
         return propMap_.get(key).getAsDouble();
      }
      return defaultVal;
   }
   @Override
   public Double[] getDoubleArray(String key) {
      if (propMap_.containsKey(key)) {
         return propMap_.get(key).getAsDoubleArray();
      }
      return null;
   }
   @Override
   public Double[] getDoubleArray(String key, Double[] defaultVal) {
      if (propMap_.containsKey(key)) {
         return propMap_.get(key).getAsDoubleArray();
      }
      return defaultVal;
   }

   @Override
   public Boolean getBoolean(String key) {
      if (propMap_.containsKey(key)) {
         return propMap_.get(key).getAsBoolean();
      }
      return null;
   }
   @Override
   public Boolean getBoolean(String key, Boolean defaultVal) {
      if (propMap_.containsKey(key)) {
         return propMap_.get(key).getAsBoolean();
      }
      return defaultVal;
   }
   @Override
   public Boolean[] getBooleanArray(String key) {
      if (propMap_.containsKey(key)) {
         return propMap_.get(key).getAsBooleanArray();
      }
      return null;
   }
   @Override
   public Boolean[] getBooleanArray(String key, Boolean[] defaultVal) {
      if (propMap_.containsKey(key)) {
         return propMap_.get(key).getAsBooleanArray();
      }
      return defaultVal;
   }

   @Override
   public <T> T getObject(String key, T defaultVal) {
      if (propMap_.containsKey(key)) {
         byte[] bytes = propMap_.get(key).getAsByteArray();
         ObjectInputStream oInputStream = null;
         try {
            ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
            oInputStream = new ObjectInputStream(bis);
            @SuppressWarnings("unchecked")
            T result = (T) oInputStream.readObject();
            oInputStream.close();
            return result;
         }
         catch (IOException e) {
            ReportingUtils.logError(e, "Error loading object of type " +
                  defaultVal.getClass() + " from PropertyMap");
         }
         catch (ClassNotFoundException e) {
            ReportingUtils.logError(e,
                  "Object with key " + key + " does not have type " +
                  defaultVal.getClass());
         }
         finally {
            try {
               if (oInputStream != null) {
                  oInputStream.close();
               }
            }
            catch (IOException e) {
               ReportingUtils.logError(e, "Failed to close inputStream");
            }
         }
      }
      return defaultVal;
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

   @Override
   public Set<String> getKeys() {
      return propMap_.keySet();
   }

   @Override
   public boolean containsKey(String key) {
      return propMap_.containsKey(key);
   }

   @Override
   public Class getPropertyType(String key) {
      if (propMap_.containsKey(key)) {
         return propMap_.get(key).getType();
      }
      return null;
   }

   public boolean hasProperty(String key) {
      return propMap_.containsKey(key);
   }

   public PropertyValue getProperty(String key) {
      return propMap_.get(key);
   }

   public JSONObject toJSON() {
      JSONObject result = new JSONObject();
      for (String key : propMap_.keySet()) {
         try {
            JSONObject val = propMap_.get(key).serialize();
            if (val != null) {
               result.put(key, val);
            }
         }
         catch (JSONException e) {
            ReportingUtils.logError(e, "Couldn't add property [" + key + "] to JSONified map");
         }
      }
      return result;
   }

   /**
    * A convenience function mostly for the MetadataPanel that discards all
    * of the typing information in a serialization of our contents. As a
    * result, our serialization will look more like this:
    * {
    *    "key1": value1,
    *    ...
    * }
    * instead of this:
    * {
    *    "key1": {"PropType": "String", "PropVal": "value1"}
    *    ...
    * }
    */
   public void flattenJSONSerialization(JSONObject serialization) {
      for (String key : getKeys()) {
         try {
            serialization.put(key,
                  serialization.getJSONObject(key).get(VALUE));
         }
         catch (JSONException e) {
            ReportingUtils.logError(e,
                  "Error in flattening PropertyMap serialization");
         }
      }
   }

   public static DefaultPropertyMap fromJSON(JSONObject map) throws JSONException {
      Builder builder = new Builder();
      if (map.length() == 0) {
         // Nothing here!
         return (DefaultPropertyMap) builder.build();
      }
      JSONArray keys = map.names();
      for (int i = 0; i < keys.length(); ++i) {
         String key = keys.getString(i);
         try {
            JSONObject property = map.getJSONObject(key);
            if (!property.has(VALUE)) {
               // Value must be null, because putting a null into a JSONObject
               // results in deleting the key.
               // TODO: for now we refuse to load null values. Should we allow
               // nulls?
               continue;
            }
            String type = property.getString(TYPE);
            if (type.contentEquals(STRING)) {
               builder.putString(key, property.getString(VALUE));
            }
            else if (type.contentEquals(STRING_ARRAY)) {
               JSONArray tmp = property.getJSONArray(VALUE);
               String[] valArr = new String[tmp.length()];
               for (int j = 0; j < tmp.length(); ++j) {
                  valArr[j] = tmp.getString(j);
               }
               builder.putStringArray(key, valArr);
            }
            else if (type.contentEquals(INTEGER)) {
               builder.putInt(key, property.getInt(VALUE));
            }
            else if (type.contentEquals(INTEGER_ARRAY)) {
               JSONArray tmp = property.getJSONArray(VALUE);
               Integer[] valArr = new Integer[tmp.length()];
               for (int j = 0; j < tmp.length(); ++j) {
                  valArr[j] = tmp.getInt(j);
               }
               builder.putIntArray(key, valArr);
            }
            else if (type.contentEquals(LONG)) {
               builder.putLong(key, property.getLong(VALUE));
            }
            else if (type.contentEquals(LONG_ARRAY)) {
               JSONArray tmp = property.getJSONArray(VALUE);
               Long[] valArr = new Long[tmp.length()];
               for (int j = 0; j < tmp.length(); ++j) {
                  valArr[j] = tmp.getLong(j);
               }
               builder.putLongArray(key, valArr);
            }
            else if (type.contentEquals(DOUBLE)) {
               builder.putDouble(key, property.getDouble(VALUE));
            }
            else if (type.contentEquals(DOUBLE_ARRAY)) {
               JSONArray tmp = property.getJSONArray(VALUE);
               Double[] valArr = new Double[tmp.length()];
               for (int j = 0; j < tmp.length(); ++j) {
                  valArr[j] = tmp.getDouble(j);
               }
               builder.putDoubleArray(key, valArr);
            }
            else if (type.contentEquals(BOOLEAN)) {
               builder.putBoolean(key, property.getBoolean(VALUE));
            }
            else if (type.contentEquals(BOOLEAN_ARRAY)) {
               JSONArray tmp = property.getJSONArray(VALUE);
               Boolean[] valArr = new Boolean[tmp.length()];
               for (int j = 0; j < tmp.length(); ++j) {
                  valArr[j] = tmp.getBoolean(j);
               }
               builder.putBooleanArray(key, valArr);
            }
            else if (type.contentEquals(OBJECT)) {
               String tmp = property.getString(VALUE);
               byte[] bytes = BaseEncoding.base64().decode(tmp);
               builder.putProperty(key, new PropertyValue(bytes));
            }
            else {
               throw new PropertyMap.TypeMismatchException(
                     "Illegal property type " + type + " for property " +
                     property);
            }
         }
         catch (JSONException e) {
            throw new RuntimeException("Error converting key " + key + " from JSON: " + e);
         }
      }
      return (DefaultPropertyMap) builder.build();
   }

   @Override
   public String toString() {
      try {
         return toJSON().toString(2);
      }
      catch (JSONException e) {
         ReportingUtils.logError(e, "Error converting PropertyMap to String");
         return "<DefaultPropertyMap with unknown contents>";
      }
   }

   @Override
   public boolean equals(Object obj) {
      if (!(obj instanceof DefaultPropertyMap)) {
         return false;
      }
      DefaultPropertyMap alt = (DefaultPropertyMap) obj;
      for (String key : getKeys()) {
         if (!alt.hasProperty(key) ||
               !alt.getProperty(key).equals(getProperty(key))) {
            return false;
         }
      }
      return true;
   }
}
