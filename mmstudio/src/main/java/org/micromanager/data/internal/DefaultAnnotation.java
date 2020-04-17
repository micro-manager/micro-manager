///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     Data API implementation
//-----------------------------------------------------------------------------
//
// AUTHOR:       Chris Weisiger, 2016
//
// COPYRIGHT:    Open Imaging, Inc 2016
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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import org.micromanager.PropertyMap;
import org.micromanager.PropertyMaps;
import org.micromanager.data.Annotation;
import org.micromanager.data.Coords;
import org.micromanager.data.Datastore;
import org.micromanager.internal.utils.ReportingUtils;

/**
 * Fairly naive Annotation implementation that stores PropertyMaps as JSON in a
 * plaintext file.
 */
public final class DefaultAnnotation implements Annotation {
   private String filename_;

   private static final String GENERAL_KEY = "General annotation";
   private static final String COORDS_KEY = "Image coordinates";

   private Datastore store_;
   private HashMap<Coords, PropertyMap> imageAnnotations_ = new HashMap<Coords, PropertyMap>();
   private PropertyMap generalAnnotation_ = PropertyMaps.emptyPropertyMap();

   public DefaultAnnotation(Datastore store, String filename) throws IOException {
      store_ = store;
      filename_ = filename;
      File target = getFile(store, filename);
      if (target.exists()) {
         try {
            PropertyMap data = PropertyMaps.loadJSON(new File(target.getAbsolutePath()));
            // Load general annotations.
            if (data.containsKey(GENERAL_KEY)) {
               generalAnnotation_ = data.getPropertyMap(GENERAL_KEY,
                     PropertyMaps.builder().build());
            }
            // Load per-image annotations.
            for (String key : data.getKeys()) {
               if (key.equals(GENERAL_KEY)) {
                  continue;
               }
               // Convert key into Coords of image.
               if (!key.contains(COORDS_KEY)) {
                  ReportingUtils.logError("Unrecognized key " + key);
                  continue;
               }
               String def = key.substring(COORDS_KEY.length());
               try {
                  Coords coords = DefaultCoords.fromNormalizedString(def);
                  imageAnnotations_.put(coords, data.getPropertyMap(key));
               }
               catch (IllegalArgumentException e) {
                  ReportingUtils.logError("Malformatted coordinate key \"" + def + "\"");
               }
            }
         }
         catch (FileNotFoundException e) {
            // This should never happen.
            ReportingUtils.showError(e, "Unable to load annotation at " + target.getAbsolutePath() + " because it doesn't exist");
         }
      }
   }

   @Override
   public void save() throws IOException {
      if (store_.getSavePath() == null) {
         throw new RuntimeException("Asked to save Annotation when store has no save path");
      }
      File file = getFile(store_, filename_);
      PropertyMap.Builder builder = PropertyMaps.builder();
      if (generalAnnotation_ != null) {
         builder.putPropertyMap(GENERAL_KEY, generalAnnotation_);
      }
      for (Coords coords : imageAnnotations_.keySet()) {
         String coordsKey = COORDS_KEY + ((DefaultCoords) coords).toNormalizedString();
         builder.putPropertyMap(coordsKey, imageAnnotations_.get(coords));
      }
      builder.build().save(file.getAbsolutePath());
   }

   @Override
   public PropertyMap getImageAnnotation(Coords coords) {
      if (imageAnnotations_.containsKey(coords)) {
         return imageAnnotations_.get(coords);
      }
      return null;
   }

   @Override
   public void setImageAnnotation(Coords coords, PropertyMap newData) {
      imageAnnotations_.put(coords, newData);
   }

   @Override
   public PropertyMap getGeneralAnnotation() {
      return generalAnnotation_;
   }

   @Override
   public void setGeneralAnnotation(PropertyMap newData) {
      generalAnnotation_ = newData;
   }

   @Override
   @Deprecated
   public String getFilename() {
      return filename_;
   }

   /**
    * Utility function to get the path at which an Annotation's data should
    * reside, if it exists.
    * @param store
    * @param filename
    * @return 
    */
   public static File getFile(Datastore store, String filename) {
      return new File(store.getSavePath() + "/" + filename);
   }

   /**
    * Return true if there's an existing annotation for this datastore, on
    * disk where the datastore's own data is stored.
    * @param store
    * @param filename
    * @return 
    */
   public static boolean isAnnotationOnDisk(Datastore store, String filename) {
      return getFile(store, filename).exists();
   }

   @Override
   public String getTag() {
      return getFilename();
   }

   @Override
   public Datastore getDatastore() {
      return store_;
   }
}
