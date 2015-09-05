///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nenad Amodaj, nenad@amodaj.com, December 3, 2006
//               Chris Weisiger, 2015
//
// COPYRIGHT:    University of California, San Francisco, 2006-2015
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
//

package org.micromanager;

import java.util.Collection;

import org.micromanager.data.Datastore;
import org.micromanager.data.Image;

/**
 * Interface for interacting with the Album, an implicit Datastore that
 * Micro-Manager uses as a "scratch pad" for image data. You can access the
 * album via Studio.album() or Studio.getAlbum().
 */
public interface Album {
   /**
    * Return the Datastore that currently backs the Album. If no images have
    * ever been added to the Album, then this will be null. Otherwise, the
    * Datastore may potentially be frozen, if the user has saved the Album and
    * then not created a new one yet.
    *
    * @return The current Datastore for the Album.
    */
   public Datastore getDatastore();

   /**
    * Add the specified Image to the Album's datastore. If no Datastore exists
    * for the Album yet, or if the current Datastore is frozen, then a new one
    * will be created, as well as a new DisplayWindow to go with it.
    *
    * @param image The Image to add to the album
    * @return True if a new Datastore and DisplayWindow were created as a
    *         side-effect of adding the image.
    */
   public boolean addImage(Image image);

   /**
    * Add the specified Images to the Album's datastore. If no Datastore exists
    * for the Album yet, or if the current Datastore is frozen, then a new one
    * will be created, as well as a new DisplayWindow to go with it. Equivalent
    * to repeatedly calling addImage().
    *
    * @param images The Images to add to the album
    * @return True if a new Datastore and DisplayWindow were created as a
    *         side-effect of adding the images.
    */
   public boolean addImages(Collection<Image> images);
}
