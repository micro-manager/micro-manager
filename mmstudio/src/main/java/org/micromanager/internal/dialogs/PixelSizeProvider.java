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

   /**
    * Returns the dx/dy value as currently known by the PixelSizeProvider.
    *
    * @return - dx/dy value as currently known by the PixelSizeProvider
    */
   Double getdxdz();

   /**
    * Sets the dx/dy value as known by the provider.
    *
    * @param dxdz angle between camera and Z stage
    */
   void setdxdz(double dxdz);

   /**
    * Returns the dy/dz value as currently known by the PixelSizeProvider.
    *
    * @return - dy/dz value as currently known by the PixelSizeProvider
    */
   Double getdydz();

   /**
    * Sets the dy/dz value as known by the provider.
    *
    * @param dydz angle between camera and Z stage
    */
   void setdydz(double dydz);

   /**
    * Returns the preferred step size in Z as currently known by the PixelSizeProvider.
    *
    * @return - preferred step size in Z as currently known by the PixelSizeProvider
    */
   Double getPreferredZStepUm();

   /**
    * Sets the preferred step size in Z as known by the provider.
    *
    * @param stepSizeUm preferred step size in Z
    */
   void setPreferredZStepUm(double stepSizeUm);
}
