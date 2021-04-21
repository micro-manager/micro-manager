///////////////////////////////////////////////////////////////////////////////
// PROJECT:       Micro-Manager
// SUBSYSTEM:     mmstudio
// -----------------------------------------------------------------------------
//
// AUTHOR:       Chris Weisiger
//
// COPYRIGHT:    University of California, San Francisco, 2011, 2015
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

package org.micromanager.imageflipper;

import org.micromanager.data.Processor;
import org.micromanager.data.ProcessorFactory;

import org.micromanager.PropertyMap;
import org.micromanager.Studio;

public class FlipperFactory implements ProcessorFactory {
  private final PropertyMap settings_;
  private final Studio studio_;

  public FlipperFactory(PropertyMap settings, Studio studio) {
    settings_ = settings;
    studio_ = studio;
  }

  @Override
  public Processor createProcessor() {
    return new FlipperProcessor(
        studio_,
        settings_.getString("camera", ""),
        settings_.getInteger("rotation", 0),
        settings_.getBoolean("shouldMirror", false));
  }
}
