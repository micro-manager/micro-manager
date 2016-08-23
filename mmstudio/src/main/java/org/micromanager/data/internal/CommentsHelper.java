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

import java.io.IOException;
import org.micromanager.PropertyMap;
import org.micromanager.data.Annotation;
import org.micromanager.data.Coords;
import org.micromanager.data.Datastore;
import org.micromanager.internal.utils.ReportingUtils;

/**
 * A simple helper class to handle access of comments.
 */
public final class CommentsHelper {
   /** File that comments are saved in. */
   private static final String COMMENTS_FILE = "comments.txt";
   /** String key used to access comments in annotations. */
   private static final String COMMENTS_KEY = "comments";

   /**
    * Returns the summary comment for the specified Datastore, or "" if it
    * does not exist.
    */
   public static String getSummaryComment(Datastore store) {
      if (!store.hasAnnotation(COMMENTS_FILE)) {
         return "";      }
      try {
         Annotation annotation = store.loadAnnotation(COMMENTS_FILE);
         if (annotation.getGeneralAnnotation() == null) {
            return "";
         }
         return annotation.getGeneralAnnotation().getString(COMMENTS_KEY, "");
      }
      catch (IOException e) {
         ReportingUtils.logError(e, "Error accessing comments annotation");
         return "";
      }
   }

   /**
    * Write a new summary comment for the given Datastore.
    */
   public static void setSummaryComment(Datastore store, String comment) {
      try {
         Annotation annotation = store.loadAnnotation(COMMENTS_FILE);
         PropertyMap prop = annotation.getGeneralAnnotation();
         if (prop == null) {
            prop = new DefaultPropertyMap.Builder().build();
         }
         prop = prop.copy().putString(COMMENTS_KEY, comment).build();
         annotation.setGeneralAnnotation(prop);
      }
      catch (IOException e) {
         ReportingUtils.logError(e, "Error setting summary comment");
      }
   }

   /**
    * Returns the comment for the specified Image in the specified Datastore,
    * or "" if it does not exist.
    */
   public static String getImageComment(Datastore store, Coords coords) {
      if (!store.hasAnnotation(COMMENTS_FILE)) {
         return "";
      }
      try {
         Annotation annotation = store.loadAnnotation(COMMENTS_FILE);
         if (annotation.getImageAnnotation(coords) == null) {
            return "";
         }
         return annotation.getImageAnnotation(coords).getString(COMMENTS_KEY,
               "");
      }
      catch (IOException e) {
         ReportingUtils.logError(e, "Error accessing comments annotation");
         return "";
      }
   }

   /**
    * Write a new image comment for the given Datastore.
    */
   public static void setImageComment(Datastore store, Coords coords,
         String comment) {
      try {
         Annotation annotation = store.loadAnnotation(COMMENTS_FILE);
         PropertyMap prop = annotation.getImageAnnotation(coords);
         if (prop == null) {
            prop = new DefaultPropertyMap.Builder().build();
         }
         prop = prop.copy().putString(COMMENTS_KEY, comment).build();
         annotation.setImageAnnotation(coords, prop);
      }
      catch (IOException e) {
         ReportingUtils.logError(e, "Error setting image comment");
      }
   }

   public static void saveComments(Datastore store) throws IOException {
      Annotation annotation = store.loadAnnotation(COMMENTS_FILE);
      annotation.save();
   }

   /**
    * Return true if there's a comments annotation.
    */
   public static boolean hasAnnotation(Datastore store) {
      return store.hasAnnotation(COMMENTS_FILE);
   }

   /**
    * Create a new comments annotation.
    */
   public static void createAnnotation(Datastore store) {
      store.createNewAnnotation(COMMENTS_FILE);
   }
}
