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

import com.google.common.base.Charsets;
import com.google.common.io.Files;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;

import org.json.JSONException;
import org.json.JSONObject;

import org.micromanager.data.Coords;
import org.micromanager.data.Datastore;
import org.micromanager.data.Annotation;

import org.micromanager.PropertyMap;

import org.micromanager.internal.utils.JavaUtils;
import org.micromanager.internal.utils.ReportingUtils;

/**
 * Fairly naive Annotation implementation that stores PropertyMaps as JSON in a
 * plaintext file. We have a "header" that contains the version string.
 */
public class DefaultAnnotation implements Annotation {
   private PropertyMap data_;
   private String filename_;

   private static final Double CURRENT_VERSION = 1.0;
   private static final String GENERAL_KEY = "General annotation";
   private static final String COORDS_KEY = "Image coordinates";

   private Datastore store_;
   private HashMap<Coords, PropertyMap> imageAnnotations_ = new HashMap<Coords, PropertyMap>();
   private PropertyMap generalAnnotation_ = null;

   public DefaultAnnotation(Datastore store, String filename) throws IOException {
      store_ = store;
      filename_ = filename;
      File target = getFile(store, filename);
      if (target.exists()) {
         try {
            String contents = Files.toString(target, Charsets.UTF_8);
            // Split the contents into the first line, which has our version
            // info, and the rest.
            String[] splitContents = contents.split("\n", 2);
            String firstLine = splitContents[0];
            try {
               Double version = Double.parseDouble(firstLine);
               if (version > CURRENT_VERSION) {
                  ReportingUtils.logError("Warning: trying to load annotation from a future version (version " + version + " > our loader " + CURRENT_VERSION);
               }
            }
            catch (NumberFormatException e) {
               ReportingUtils.logError("Warning: unable to determine version of annotation file from line [" + firstLine + "]");
            }
            PropertyMap data = null;
            try {
               data = DefaultPropertyMap.fromJSON(new JSONObject(splitContents[1]));
            }
            catch (JSONException e) {
               throw new IOException(e);
            }
            // Load general annotations.
            if (data.containsKey(GENERAL_KEY)) {
               generalAnnotation_ = data.getPropertyMap(GENERAL_KEY,
                     new DefaultPropertyMap.Builder().build());
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

   public void save() throws IOException {
      if (store_.getSavePath() == null) {
         throw new RuntimeException("Asked to save Annotation when store has no save path");
      }
      DefaultPropertyMap.Builder builder = new DefaultPropertyMap.Builder();
      if (generalAnnotation_ != null) {
         builder.putPropertyMap(GENERAL_KEY, generalAnnotation_);
      }
      for (Coords coords : imageAnnotations_.keySet()) {
         String coordsKey = COORDS_KEY + ((DefaultCoords) coords).toNormalizedString();
         builder.putPropertyMap(coordsKey, imageAnnotations_.get(coords));
      }
      /**
       * Annoyingly, since we have to include the header at the top of the
       * file, we can't just use PropertyMap.save() here.
       * TODO: much of the logic of PropertyMap.save() is replicated here.
       */
      File tmpFile = getFile(store_, filename_ + ".tmp");
      File destFile = getFile(store_, filename_);
      FileWriter writer = null;
      try {
         writer = new FileWriter(tmpFile, true);
         writer.write(Double.toString(CURRENT_VERSION) + "\n");
         writer.write(
               ((DefaultPropertyMap) builder.build()).toJSON().toString(1));
         writer.close();
         if (JavaUtils.isWindows() && destFile.exists()) {
            // Must delete the destination before copying over it.
            destFile.delete();
         }
         tmpFile.renameTo(destFile);
      }
      catch (JSONException e) {
         throw new IOException(e);
      }
      finally {
         if (writer != null) {
            writer.close();
         }
      }
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
   public String getFilename() {
      return filename_;
   }

   /**
    * Utility function to get the path at which an Annotation's data should
    * reside, if it exists.
    */
   public static File getFile(Datastore store, String filename) {
      return new File(store.getSavePath() + "/" + filename);
   }

   /**
    * Return true if there's an existing annotation for this datastore, on
    * disk where the datastore's own data is stored.
    */
   public static boolean isAnnotationOnDisk(Datastore store,
         String filename) {
      return getFile(store, filename).exists();
   }
}
