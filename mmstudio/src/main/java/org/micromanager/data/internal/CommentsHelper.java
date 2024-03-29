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

/**
 * Comments are handled separately from SummaryMetadata and Imagemetadata since the
 * used may change them (add to them) at any point in time.
 * This class facilitates supporting Comments in the various storage methods.
 * Note that the design of this class is not final, Annotation should be
 * part of the API.
 */
public final class CommentsHelper {
   /**
    * File that comments are saved in.
    */
   private static final String COMMENTS_FILE = "comments.txt";
   /**
    * String key used to access comments in annotations.
    */
   private static final String COMMENTS_KEY = "comments";

   /**
    * Returns the summary comment for the specified Datastore, or en empty string
    * if no summary comment exists.
    *
    * @param store Datastore from where to retrieve the comment
    * @return comment text
    * @throws java.io.IOException Can happen with disk based stores.
    */
   public static String getSummaryComment(Datastore store) throws IOException {
      if (!store.hasAnnotation(COMMENTS_FILE)) {
         return "";
      }
      return store.getAnnotation(COMMENTS_FILE).getGeneralAnnotation().getString(COMMENTS_KEY, "");
   }

   /**
    * Write a new summary comment for the given Datastore.
    *
    * @param store   Datastore to which to add the summary comment
    * @param comment text to be added to the metadata
    * @throws java.io.IOException Can happen with disk based Datastores.
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
    *
    * @param store  Datastore
    * @param coords Specifies the image for which to get the comment
    * @return ImageComment for specified image
    * @throws java.io.IOException Can happen with disk based Datastores.
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
    *
    * @param store   Datastore that will receive the comment
    * @param coords  Specifies which image will receive the comment
    * @param comment Text to add to the image
    * @throws java.io.IOException Can happen with Disk based Storage
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
    * Copies comments form one Datastore to another.
    *
    * @param source Source Datastores
    * @param target Datastore to copy comments to.
    * @throws IOException Can happen with Disk based storage.
    */
   public static void copyComments(Datastore source, Datastore target) throws IOException {
      Annotation annotation = source.getAnnotation(COMMENTS_FILE);
      if (target instanceof DefaultDatastore) {
         DefaultDatastore dTarget = (DefaultDatastore) target;
         dTarget.setAnnotation(COMMENTS_FILE, annotation);
      }
   }

   /**
    * Return true if there's a comments annotation.
    *
    * @param store Datastore to be queried
    * @return true if there's a comments annotation
    * @throws java.io.IOException Can happen with Disk based Storage
    */
   public static boolean hasAnnotation(Datastore store) throws IOException {
      return store.hasAnnotation(COMMENTS_FILE);
   }

}
