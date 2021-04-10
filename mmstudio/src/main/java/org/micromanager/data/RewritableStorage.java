///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     Data API
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

package org.micromanager.data;

/**
 * This interface is for Storage entities that allow the overwriting and
 * deletion of Images and SummaryMetadata, as per RewritableDatastore.
 */
public interface RewritableStorage extends Storage {
   /**
    * Delete an image from the Storage. Posts an ImageDeletedEvent to the
    * event bus. Throws an IllegalArgumentException if the provided coordinates
    * do not correspond to any image in the Storage.
    * @param coords Coordinates of the image to remove.
    * @throws IllegalArgumentException if the coords do not match any image.
    */
   void deleteImage(Coords coords) throws IllegalArgumentException;
}
