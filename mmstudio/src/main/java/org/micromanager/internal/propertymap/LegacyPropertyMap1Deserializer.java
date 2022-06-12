/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.micromanager.internal.propertymap;

import com.google.common.io.BaseEncoding;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.micromanager.PropertyMap;
import org.micromanager.PropertyMaps;

/**
 * Deserialize MM2-beta style property maps (version "1.0").
 *
 * @author Mark A. Tsuchida
 */
class LegacyPropertyMap1Deserializer {
   // Note: This does not include support for the first-line version ("1.0\n")
   public static PropertyMap fromJSON(String json) {
      JsonParser parser = new JsonParser();
      return parsePM1JSON(parser.parse(json));
   }

   private static PropertyMap parsePM1JSON(JsonElement element) {
      PropertyMap.Builder builder = PropertyMaps.builder();
      JsonObject map = element.getAsJsonObject();
      for (Map.Entry<String, JsonElement> e : map.entrySet()) {
         String key = e.getKey();
         JsonObject typeAndValue = e.getValue().getAsJsonObject();
         JsonElement typeElement = typeAndValue.get("PropType");
         JsonElement valueElement = typeAndValue.get("PropVal");
         if (typeElement == null || valueElement == null) {
            continue;
         }
         String typeName = typeElement.getAsString();

         constructPropertyMap1Property(builder, key, typeName, valueElement);
      }
      return builder.build();
   }

   static void constructPropertyMap1Property(PropertyMap.Builder builder,
                                             String key, String typeName,
                                             JsonElement valueElement) {
      if ("String".equals(typeName)) {
         builder.putString(key, valueElement.getAsString());
      }
      else if ("Integer".equals(typeName)) {
         builder.putInteger(key, valueElement.getAsInt());
      }
      else if ("Long".equals(typeName)) {
         builder.putLong(key, valueElement.getAsLong());
      }
      else if ("Double".equals(typeName)) {
         builder.putDouble(key, valueElement.getAsDouble());
      }
      else if ("Boolean".equals(typeName)) {
         builder.putBoolean(key, valueElement.getAsBoolean());
      }
      else if ("String array".equals(typeName)) {
         List<String> values = new ArrayList<>();
         for (JsonElement ae : valueElement.getAsJsonArray()) {
            values.add(ae.getAsString());
         }
         builder.putStringList(key, values);
      }
      else if ("Integer array".equals(typeName)) {
         List<Integer> values = new ArrayList<>();
         for (JsonElement ae : valueElement.getAsJsonArray()) {
            values.add(ae.getAsInt());
         }
         builder.putIntegerList(key, values);
      }
      else if ("Long array".equals(typeName)) {
         List<Long> values = new ArrayList<>();
         for (JsonElement ae : valueElement.getAsJsonArray()) {
            values.add(ae.getAsLong());
         }
         builder.putLongList(key, values);
      }
      else if ("Double array".equals(typeName)) {
         List<Double> values = new ArrayList<>();
         for (JsonElement ae : valueElement.getAsJsonArray()) {
            values.add(ae.getAsDouble());
         }
         builder.putDoubleList(key, values);
      }
      else if ("Boolean array".equals(typeName)) {
         List<Boolean> values = new ArrayList<>();
         for (JsonElement ae : valueElement.getAsJsonArray()) {
            values.add(ae.getAsBoolean());
         }
         builder.putBooleanList(key, values);
      }
      else if ("Property map".equals(typeName)) {
         JsonObject submap = valueElement.getAsJsonObject();
         builder.putPropertyMap(key, parsePM1JSON(submap));
      }
      else if ("Object".equals(typeName)) {
         // Hidden support for backward-compat
         String base64EncodedSerializedObject = valueElement.getAsString();
         byte[] serializedObject = BaseEncoding.base64().decode(
               base64EncodedSerializedObject);
         ((DefaultPropertyMap.Builder) builder)
               .putLegacySerializedObject(key, serializedObject);
      }
      else {
         throw new JsonParseException("Valid JSON but not valid property map (unknown propType)");
      }
   }
}