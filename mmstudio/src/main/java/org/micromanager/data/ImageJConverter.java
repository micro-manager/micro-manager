///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     Data API
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

package org.micromanager.data;

import ij.process.ImageProcessor;

/**
 * This class provides access to utility methods for interoperating with
 * ImageJ. It can be accessed via the DataManager.ij() method or the
 * DataManager.getImageJConverter() method.
 */
public interface ImageJConverter {
   /**
    * Create an ImageProcessor whose image pixel data is derived from the
    * provided Image.
    * @param image Micro-Manager Image object
    * @return ImageJ ImageProcessor with reference to input Image pixel data
    */
   ImageProcessor createProcessor(Image image);

   /**
    * Create an ImageProcessor whose image pixel data is derived from a
    * single component of the provided Image. Use this to e.g. extract out
    * just the red component of an RGB image.
    * @param image Micro-Manager Image object
    * @param component Component number of the image to produce a processor for
    * @return ImageJ ImageProcessor whose pixels are based on a single
    *         component of the input Image.
    */
   ImageProcessor createProcessorFromComponent(Image image,
                                               int component);

   /**
    * Create a new Image based on the provided ImageProcessor and metadata.
    * @param processor ImageProcessor whose pixel data will form the data of
    *        the result image.
    * @param coords Coordinates for the new image.
    * @param metadata Metadata to use to create the new Image.
    * @return an Image based on the pixel data in the processor and the given
    *         coordinates and metadata.
    */
   Image createImage(ImageProcessor processor, Coords coords,
                     Metadata metadata);
}
