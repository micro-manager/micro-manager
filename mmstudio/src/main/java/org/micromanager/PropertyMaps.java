/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager;

import com.google.common.base.Charsets;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import org.micromanager.internal.propertymap.DefaultPropertyMap;
import org.micromanager.internal.propertymap.PropertyMapJSONSerializer;

/**
 * Static methods to create PropertyMap instances.
 *
 * @author Mark A. Tsuchida
 */
public final class PropertyMaps {
   private PropertyMaps() { }
   private static final PropertyMap EMPTY_MAP = builder().build();

   /**
    * Returns a builder object for creating {@code PropertyMap} instances.
    *
    * Create property maps like this:
    * <pre><code>
    * PropertyMap myPropertyMap = PropertyMaps.builder().
    *       putString("first name", "Jane").
    *       putString("last name", "Smith").
    *       putInteger("age", 32).
    *       build();
    * </code></pre>
    * @return a property map builder
    */
   public static PropertyMap.Builder builder() {
      return DefaultPropertyMap.builder();
   }

   /**
    * Return the empty property map.
    * @return empty property map
    */
   public static PropertyMap emptyPropertyMap() {
      return EMPTY_MAP;
   }

   /**
    * Create a property map from its JSON-serialized form.
    * @param json json formatted String to be converted into a PropertyMap
    * @return PropertyMap result from conversion
    * @throws IOException if {@code json} is invalid JSON or if it does not
    * represent a valid property map
    */
   public static PropertyMap fromJSON(String json) throws IOException {
      return PropertyMapJSONSerializer.fromJSON(json);
   }

   /**
    * Create a property map from its JSON-serialized form stored in a file.
    * @param file
    * @return
    * @throws IOException if there was a problem reading {@code file} or if the
    * file contained invalid JSON or if the JSON did not represent a valid
    * property map
    * @throws java.io.FileNotFoundException this subclass of {@code IOException}
    * is thrown if {@code file} does not exist
    */
   public static PropertyMap loadJSON(File file) throws IOException {
      return PropertyMapJSONSerializer.fromJSON(Files.toString(file, Charsets.UTF_8));
   }
}