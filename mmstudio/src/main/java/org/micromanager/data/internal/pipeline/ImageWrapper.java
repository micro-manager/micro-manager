///////////////////////////////////////////////////////////////////////////////
// PROJECT:       Micro-Manager
// SUBSYSTEM:     Data API implementation
// -----------------------------------------------------------------------------
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

package org.micromanager.data.internal.pipeline;

import org.micromanager.data.Image;

/**
 * This class serves as a simple wrapper around Images for passing through the pipeline. It exists
 * predominantly so we can tell when we're being told to flush the pipeline (by creating an
 * ImageWrapper with a null image).
 */
public final class ImageWrapper {
  private Image image_;

  public ImageWrapper(Image image) {
    image_ = image;
  }

  public Image getImage() {
    return image_;
  }
}
