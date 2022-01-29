///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nico Stuurman, 2018
//
// COPYRIGHT:    Regents of the University of California, 2018
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

package org.micromanager.internal.dialogs;

import java.awt.geom.AffineTransform;

/**
 * PixelSize Providers are thought to be GUI elements that allow the user
 * to edit pixel size and associated affine transforms.
 * Various other code may want to get and set the values displayed to the user,
 * by means of this interface.
 *
 * @author nico
 */
public interface PixelSizeProvider {
   
   /**
    * Provides the current (for instance, the value just entered by the user)
    * pixel size (in microns).
    *
    * @return Pixel size (in microns)
    */
   Double getPixelSize();
   
   /**
    * Sets the pixel size as displayed in the PixelSizeProvider.
    *
    * @param pixelSizeUm - pixel size in microns that will be set in the PixelSizeProvier
    */
   void setPixelSize(double pixelSizeUm);
   
   /**
    * Returns the affine transform as currently known by the PixelSizeProvider.
    *
    * @return - affine transform as currently known by the PixelSizeProvider
    */
   AffineTransform getAffineTransform();
   
   /**
    * Sets the affine transform as known by the provider.
    *
    * @param aft - new affine transform
    */
   void setAffineTransform(AffineTransform aft);
}
