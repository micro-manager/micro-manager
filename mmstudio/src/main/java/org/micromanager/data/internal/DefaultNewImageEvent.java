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

package org.micromanager.data.internal;

import org.micromanager.data.Coords;
import org.micromanager.data.DataProvider;
import org.micromanager.data.DataProviderHasNewImageEvent;
import org.micromanager.data.Image;

/** This class signifies that an image has been added to a Datastore. */
public final class DefaultNewImageEvent implements DataProviderHasNewImageEvent {
  private final Image image_;
  private final DataProvider provider_;

  public DefaultNewImageEvent(Image image, DataProvider provider) {
    image_ = image;
    provider_ = provider;
  }

  @Override
  public Image getImage() {
    return image_;
  }

  @Override
  public Coords getCoords() {
    return image_.getCoords();
  }

  @Override
  public DataProvider getDataProvider() {
    return provider_;
  }
}
