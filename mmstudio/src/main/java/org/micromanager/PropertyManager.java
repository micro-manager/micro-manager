package org.micromanager;

import java.io.File;
import java.io.IOException;

/**
 * Provides access to PropertyMaps.  And instance of this interface is obtained
 * through Studio.properties() and Studio.propertyManager();
 */
public interface PropertyManager {

   /**
    * Provides an easy way for a script to obtain a PropertyMap Builder
    * @return PropertyMap Builder
    */
   PropertyMap.Builder propertyMapBuilder();

   /**
    * Returns an empty property map.
    * @return empty property map
    */
   PropertyMap emptyPropertyMap();

   /**
    * Creates a property map from its JSON-serialized form.
    * JSON format is described in {@link org.micromanager.internal.propertymap.PropertyMapJSONSerializer}
    * @param json json formatted String to be converted into a PropertyMap
    * @return PropertyMap result from conversion
    * @throws IOException if {@code json} is invalid JSON or if it does not
    * represent a valid property map
    */
   PropertyMap fromJSON(String json) throws IOException;

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
   PropertyMap loadJSON(File file) throws IOException;
}
