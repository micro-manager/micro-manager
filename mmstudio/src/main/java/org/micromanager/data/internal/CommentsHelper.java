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
import org.micromanager.PropertyMaps;
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
    * @param store Datastore from where to retrieve the comment
    * @return comment text
    * @throws java.io.IOException
    */
   public static String getSummaryComment(Datastore store) throws IOException {
      if (!store.hasAnnotation(COMMENTS_FILE)) {
         return "";
      }
      return store.getAnnotation(COMMENTS_FILE).getGeneralAnnotation().getString(COMMENTS_KEY, "");
   }

   /**
    * Write a new summary comment for the given Datastore.
    * @param store Datastore to whic to add the summary comment
    * @param comment text to be added to the metadata
    * @throws java.io.IOException
    */
   public static void setSummaryComment(Datastore store, String comment) throws IOException {
      Annotation annotation = store.getAnnotation(COMMENTS_FILE);
      PropertyMap prop = annotation.getGeneralAnnotation();
      if (prop == null) {
         prop = PropertyMaps.builder().build();
      }
      prop = prop.copyBuilder().putString(COMMENTS_KEY, comment).build();
      annotation.setGeneralAnnotation(prop);
   }

   /**
    * Returns the comment for the specified Image in the specified Datastore,
    * or empty string if it does not exist.
    * @param store Datastore 
    * @param coords
    * @return 
    * @throws java.io.IOException
    */
   public static String getImageComment(Datastore store, Coords coords) throws IOException {
      if (!store.hasAnnotation(COMMENTS_FILE)) {
         return "";
      }
      Annotation annotation = store.getAnnotation(COMMENTS_FILE);
      if (annotation.getImageAnnotation(coords) == null) {
         return "";
      }
      return annotation.getImageAnnotation(coords).getString(COMMENTS_KEY,
            "");
   }

   /**
    * Write a new image comment for the given Datastore.
    * @param store Datastore that will receive the comment
    * @param coords Specifies which image will receive the comment
    * @param comment Text to add to the image
    * @throws java.io.IOException
    */
   public static void setImageComment(Datastore store, Coords coords,
         String comment) throws IOException {
      Annotation annotation = store.getAnnotation(COMMENTS_FILE);
      PropertyMap prop = annotation.getImageAnnotation(coords);
      if (prop == null) {
         prop = PropertyMaps.builder().build();
      }
      prop = prop.copyBuilder().putString(COMMENTS_KEY, comment).build();
      annotation.setImageAnnotation(coords, prop);
   }

   public static void saveComments(Datastore store) throws IOException {
      Annotation annotation = store.getAnnotation(COMMENTS_FILE);
      annotation.save();
   }

   /**
    * Return true if there's a comments annotation.
    * @param store
    * @return true if there's a comments annotation
    * @throws java.io.IOException
    */
   public static boolean hasAnnotation(Datastore store) throws IOException {
      return store.hasAnnotation(COMMENTS_FILE);
   }

   /**
    * Create a new comments annotation.
    * @param store
    * @throws java.io.IOException
    */
   public static void createAnnotation(Datastore store) throws IOException {
      //store.createNewAnnotation(COMMENTS_FILE       // throw new UnsupportedOperationException("TODO");
      ReportingUtils.logError("TODO: Implement org.micromanager.data.internal.CommentsHelper.createAnnotation");
   }
}
