////////////////////////////////////////////////////////////////////////////////
// FILE:
// PROJECT:
// -----------------------------------------------------------------------------
//
// AUTHOR:       Nico Stuurman
//
// COPYRIGHT:    University of California, San Francisco 2015
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

package org.micromanager.asidispim.utils;

import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;
import org.micromanager.data.Image;

/** @author nico */
public class ImageUtils {
  public static ImageProcessor getImageProcessor(Image img) throws NotImplementedException {
    ImageProcessor ip = null;
    if (img.getNumComponents() > 1)
      throw new NotImplementedException("Conversion of RGB images is not yet implemented");
    if (img.getBytesPerPixel() == 1) {
      ip = new ByteProcessor(img.getWidth(), img.getHeight(), (byte[]) img.getRawPixels());
    } else if (img.getBytesPerPixel() == 2) {
      ip = new ShortProcessor(img.getWidth(), img.getHeight());
      ip.setPixels((short[]) img.getRawPixels());
    }

    return ip;
  }
}
