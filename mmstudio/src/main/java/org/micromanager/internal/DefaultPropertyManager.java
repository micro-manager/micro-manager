package org.micromanager.internal;

import org.micromanager.PropertyManager;
import org.micromanager.PropertyMap;
import org.micromanager.PropertyMaps;
import org.micromanager.internal.propertymap.DefaultPropertyMap;

import java.io.File;
import java.io.IOException;


public class DefaultPropertyManager implements PropertyManager {
   /**
    * Provides an easy way for a script to obtain a PropertyMap Builder
    *
    * @return PropertyMap Builder
    */
   @Override
   public PropertyMap.Builder propertyMapBuilder() {
      return DefaultPropertyMap.builder();
   }

   /**
    * Returns an empty property map.
    *
    * @return empty property map
    */
   @Override
   public PropertyMap emptyPropertyMap() {
      return PropertyMaps.emptyPropertyMap();
   }

   /**
    * Creates a property map from its JSON-serialized form.
    * JSON format is described in {@link org.micromanager.internal.propertymap.PropertyMapJSONSerializer}
    * @param json json formatted String to be converted into a PropertyMap
    * @return PropertyMap result from conversion
    * @throws IOException if {@code json} is invalid JSON or if it does not
    * represent a valid property map
    */
   @Override
   public PropertyMap fromJSON(String json) throws IOException {
      return PropertyMaps.fromJSON(json);
   }

   /**
    * Create a property map from its JSON-serialized form stored in a file.
    *
    * JSON format is described in {@link org.micromanager.internal.propertymap.PropertyMapJSONSerializer}
    * @param file File containing JSON serialized Properties.
    * @return PropertyMap resulting from conversion fo JSON in file.
    * @throws IOException if there was a problem reading {@code file} or if the
    * file contained invalid JSON or if the JSON did not represent a valid
    * property map
    * @throws java.io.FileNotFoundException this subclass of {@code IOException}
    * is thrown if {@code file} does not exist
    */
   @Override
   public PropertyMap loadJSON(File file) throws IOException {
      return PropertyMaps.loadJSON(file);
   }

}