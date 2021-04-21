///////////////////////////////////////////////////////////////////////////////
// PROJECT:       Micro-Manager
// SUBSYSTEM:     Data API implementation
// -----------------------------------------------------------------------------
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

package org.micromanager.data.internal;

import org.micromanager.data.Datastore;
import org.micromanager.data.Image;

/** This class signifies that an image has been deleted from a Datastore. */
public final class DefaultImageDeletedEvent implements org.micromanager.data.ImageDeletedEvent {
  private Image image_;
  private Datastore store_;

  public DefaultImageDeletedEvent(Image image, Datastore store) {
    image_ = image;
    store_ = store;
  }

  @Override
  public Image getImage() {
    return image_;
  }

  @Override
  public Datastore getDatastore() {
    return store_;
  }
}
