///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     Data API
//-----------------------------------------------------------------------------
//
// AUTHOR:       Chris Weisiger, 2016
//
// COPYRIGHT:    Open Imaging, Inc. 2016
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

package org.micromanager.data;

import java.io.IOException;
import org.micromanager.PropertyMap;

/**
 * A Annotation is a container for mutable information that is associated with
 * a Datastore. *
 * Annotations are used for attributes like image comments and display settings
 * that may be changed after the Datastore has been frozen.
 */
public interface Annotation {
   /**
    * Return a PropertyMap of information stored by this Annotation related to
    * the image at the specified coordinates.
    *
    * @param coords Coordinates of image to get data for.
    * @return PropertyMap of data related to the image, or null if none exists.
    */
   PropertyMap getImageAnnotation(Coords coords);

   /**
    * Return a PropertyMap of information stored by the Annotation that is not
    * specific to any one image.
    *
    * @return PropertyMap of data related to the entire dataset, or null if
    *     none exists.
    */
   PropertyMap getGeneralAnnotation();

   /**
    * Replace the data this Annotation has for the specified Image with the
    * provided PropertyMap.
    *
    * @param coords  Coordinates of image whose data is to be updated.
    * @param newData Updated PropertyMap of data to be stored in the Annotation
    *                that pertains to the image
    */
   void setImageAnnotation(Coords coords, PropertyMap newData);

   /**
    * Replace the data this Annotation has for the Datastore as a whole with the
    * provided PropertyMap.
    *
    * @param newData Updated PropertyMap of data to be stored in the Annotation
    *                that is not specific to any one Image.
    */
   void setGeneralAnnotation(PropertyMap newData);

   /**
    * Deprecated function returning tag (which doubles as filename) for this Annotation.
    *
    * @return the tag for this annotation
    * @deprecated this is an old name for {@link #getTag}
    */
   @Deprecated
   String getFilename();

   /**
    * Return this annotation's tag.
    *
    * @return the tag for this annotation
    */
   String getTag();

   /**
    * Return the datastore to which this annotation is associated.
    *
    * @return Datastore associated with this annotation.
    */
   Datastore getDatastore();

   /**
    * Commit the changes to this annotation to disk.
    */
   void save() throws IOException;
}