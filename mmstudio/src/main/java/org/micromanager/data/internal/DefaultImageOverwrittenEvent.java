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

/** This class signifies that an image in a Datastore has been overwritten by a new image. */
public final class DefaultImageOverwrittenEvent
    implements org.micromanager.data.ImageOverwrittenEvent {
  private Image newImage_;
  private Image oldImage_;
  private Datastore store_;

  public DefaultImageOverwrittenEvent(Image newImage, Image oldImage, Datastore store) {
    newImage_ = newImage;
    oldImage_ = oldImage;
    store_ = store;
  }

  @Override
  public Image getNewImage() {
    return newImage_;
  }

  @Override
  public Image getOldImage() {
    return oldImage_;
  }

  @Override
  public Datastore getDatastore() {
    return store_;
  }
}
