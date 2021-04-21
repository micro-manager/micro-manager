///////////////////////////////////////////////////////////////////////////////
// FILE:          RatioImagingProcessor.java
// PROJECT:       Micro-Manager
// SUBSYSTEM:     mmstudio
// -----------------------------------------------------------------------------
//
// AUTHOR:       Nico Stuurman
//
// COPYRIGHT:    University of California, San Francisco, 2018
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

package org.micromanager.ratioimaging;

import org.micromanager.data.Processor;
import org.micromanager.data.ProcessorFactory;
import org.micromanager.PropertyMap;
import org.micromanager.Studio;

public class RatioImagingFactory implements ProcessorFactory {
  private final Studio studio_;
  private final PropertyMap settings_;

  public RatioImagingFactory(Studio studio, PropertyMap settings) {
    studio_ = studio;
    settings_ = settings;
  }

  @Override
  public Processor createProcessor() {
    return new RatioImagingProcessor(studio_, settings_);
  }
}
