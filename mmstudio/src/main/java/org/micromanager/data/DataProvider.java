// Copyright (C) 2015-2017 Open Imaging, Inc.
//           (C) 2015 Regents of the University of California
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

import java.io.Closeable;
import java.io.IOException;
import java.util.List;

/**
 * Read-only access to multi-dimensional image data.
 *
 * @author Chris Weisiger and Mark A. Tsuchida
 */
public interface DataProvider extends Closeable {
   @Override
   void close() throws IOException;

   /**
    * Retrieve any image from this DataProvider.
    * 
    * @return a random image from the collection kept by the DataProvider
    * @throws IOException when error occurs loading the data from storage
    */
   Image getAnyImage() throws IOException;

   /**
    * Retrieve the axis currently in use by this DataProvider
    * 
    * @return List with Axis being used in no particular order
    */
   List<String> getAxes();

   /**
    * Returns the maximum index plus one along the given index.
    * There is no guarantee that images can be found at every position of this
    * axis, i.e. empty positions are possible
    * 
    * @param axis Axis that we are enquiring about
    * @return Maximum index plus 1 along this axis
    */
   int getAxisLength(String axis);

   /**
    * Returns the image at the given postion 
    * @param coords Coords specifying the multi-dimensional index to the image
    * @return desired Image
    * @throws IOException when error occurs loading the data from storage
    */
   Image getImage(Coords coords) throws IOException;

   /**
    * Returns a list of images that have coords matching the given one
    * @param coords specification of multi-dimensional index that needs to be
    *               present in the Coords of the images that will be returned
    * @return Matching Images
    * @throws IOException when error occurs loading the data from storage
    * @deprecated - instead use getImagesIgnoringAxes
    */
   @Deprecated
   List<Image> getImagesMatching(Coords coords) throws IOException;


   /**
    * Returns a list of image in the DataProvider's collection that have
    * identical coords after removing the given axes.
    * @param coords Coords to look for in the provider's collection
    * @param ignoreTheseAxes Axes that will be removed from copy of Coords in the
    *                        collection before checking for identity
    * @return images in the dataProvider's collection with the desired Coords
    * @throws IOException when error occurs loading the data from storage
    */
   List<Image> getImagesIgnoringAxes(Coords coords, String... ignoreTheseAxes) throws IOException;

   /**
    * A dataProvider is frozen when no more images can be added
    * 
    * @return True if no more images can be added
    */
   boolean isFrozen();

   /**
    * Coords with highest possible index along each axis
    * @return Coords with highest possible index along each axis
    */
   Coords getMaxIndices();

   /**
    * Provides total number of images that can be accessed through this DataProvider
    * TODO: does this include blank images/empty Coords?
    * @return total number of images that can be accessed through this DataProvider
    */
   int getNumImages();

   /**
    * 
    * @return summarymetadata of this dataProvides
    */
   SummaryMetadata getSummaryMetadata();

   Iterable<Coords> getUnorderedImageCoords();

   boolean hasImage(Coords coords);
   
   /**
    * A dataProvider has a name (not guaranteed to be unique)
    * 
    * @return name of this dataProvider
    */
   String getName();
   
   void registerForEvents(Object obj);

   void unregisterForEvents(Object obj);
}