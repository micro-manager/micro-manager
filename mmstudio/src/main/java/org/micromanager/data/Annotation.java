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
 * a Datastore but cannot be stored within that Datastore itself, nor written
 * to the same file that the image data is stored in. Annotations are used for
 * attributes like image comments and display settings that may be changed
 * after the Datastore has been frozen.
 * You can create and access Annotations using the DataManager.
 */
public interface Annotation {
   /**
    * Return a PropertyMap of information stored by this Annotation related to
    * the image at the specified coordinates.
    * @param coords Coordinates of image to get data for.
    * @return PropertyMap of data related to the image, or null if none exists.
    */
   public PropertyMap getImageAnnotation(Coords coords);

   /**
    * Return a PropertyMap of information stored by the Annotation that is not
    * specific to any one image.
    * @return PropertyMap of data related to the entire dataset, or null if
    *         none exists.
    */
   public PropertyMap getGeneralAnnotation();

   /**
    * Replace the data this Annotation has for the specified Image with the
    * provided PropertyMap.
    * @param coords Coordinates of image whose data is to be updated.
    * @param newData Updated PropertyMap of data to be stored in the Annotation
    *        that pertains to the image
    */
   public void setImageAnnotation(Coords coords, PropertyMap newData);

   /**
    * Replace the data this Annotation has for the Datastore as a whole with the
    * provided PropertyMap.
    * @param newData Updated PropertyMap of data to be stored in the Annotation
    *        that is not specific to any one Image.
    */
   public void setGeneralAnnotation(PropertyMap newData);

   /**
    * Return the filename this Annotation stores its data under. This will not
    * include the complete save path of the Datastore (which may not exist
    * yet). For example, "comments.txt" or "displaySettings.txt".
    * @return String describing the filename this Annotation stores its
    *         information in.
    */
   public String getFilename();

   /**
    * Save the annotation to disk, to a file adjacent to the Datastore's data,
    * based on this Annotation's filename. This method is automatically called
    * when Datastore.save() is called; however, you may want to call it
    * manually after making changes to an annotation after having saved the
    * Datastore's data.
    * @throws IOException if there were any problems writing the annotation
    *         data.
    */
   public void save() throws IOException;
}
