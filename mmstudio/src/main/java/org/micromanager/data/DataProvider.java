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

   Image getAnyImage() throws IOException;

   List<String> getAxes();

   int getAxisLength(String axis);

   Image getImage(Coords coords) throws IOException;

   List<Image> getImagesMatching(Coords coords) throws IOException;

   boolean isFrozen();

   Coords getMaxIndices();

   int getNumImages();

   SummaryMetadata getSummaryMetadata();

   Iterable<Coords> getUnorderedImageCoords();

   boolean hasImage(Coords coords);
   
   void registerForEvents(Object obj);

   void unregisterForEvents(Object obj);
}