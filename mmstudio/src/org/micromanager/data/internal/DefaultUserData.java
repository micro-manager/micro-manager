package org.micromanager.data.internal;

import java.util.HashMap;

import org.micromanager.UserData;

public class DefaultUserData implements UserData {
   public static class Builder implements UserData.UserDataBuilder {
      private HashMap<String, String> stringMap_;
      private HashMap<String, Integer> intMap_;
      private HashMap<String, Double> doubleMap_;

      public Builder() {
         stringMap_ = new HashMap<String, String>();
         intMap_ = new HashMap<String, Integer>();
         doubleMap_ = new HashMap<String, Double>();
      }

      @Override
      public UserData build() {
         return new DefaultUserData(this);
      }

      @Override
      public UserData.UserDataBuilder putString(String key, String value) {
         stringMap_.put(key, value);
         return this;
      }
      
      @Override
      public UserData.UserDataBuilder putInt(String key, Integer value) {
         intMap_.put(key, value);
         return this;
      }

      @Override
      public UserData.UserDataBuilder putDouble(String key, Double value) {
         doubleMap_.put(key, value);
         return this;
      }
   }

   private HashMap<String, String> stringMap_;
   private HashMap<String, Integer> intMap_;
   private HashMap<String, Double> doubleMap_;

   public DefaultUserData(Builder builder) {
      stringMap_ = builder.stringMap_;
      intMap_ = builder.intMap_;
      doubleMap_ = builder.doubleMap_;
   }

   @Override
   public UserData.UserDataBuilder copy() {
      Builder builder = new Builder();
      for (String key : stringMap_.keySet()) {
         builder.putString(key, stringMap_.get(key));
      }
      for (String key : intMap_.keySet()) {
         builder.putInt(key, intMap_.get(key));
      }
      for (String key : doubleMap_.keySet()) {
         builder.putDouble(key, doubleMap_.get(key));
      }
      return builder;
   }

   @Override
   public String getString(String key) {
      if (stringMap_.containsKey(key)) {
         return stringMap_.get(key);
      }
      return null;
   }

   @Override
   public Integer getInt(String key) {
      if (intMap_.containsKey(key)) {
         return intMap_.get(key);
      }
      return null;
   }

   @Override
   public Double getDouble(String key) {
      if (doubleMap_.containsKey(key)) {
         return doubleMap_.get(key);
      }
      return null;
   }
}
