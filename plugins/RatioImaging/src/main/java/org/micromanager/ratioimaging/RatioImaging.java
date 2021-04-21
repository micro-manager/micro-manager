///////////////////////////////////////////////////////////////////////////////
// FILE:          RatioImaging.java
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

import org.micromanager.data.ProcessorConfigurator;
import org.micromanager.data.ProcessorFactory;
import org.micromanager.data.ProcessorPlugin;
import org.micromanager.PropertyMap;

import org.scijava.plugin.Plugin;
import org.scijava.plugin.SciJavaPlugin;

import org.micromanager.Studio;

/**
 * Micro-Manager plugin to create ratio image
 *
 * @author nico
 */
@Plugin(type = ProcessorPlugin.class)
public class RatioImaging implements ProcessorPlugin, SciJavaPlugin {
  public static final String MENU_NAME = "RatioImaging";
  public static final String TOOL_TIP_DESCRIPTION = "Generates a ratio image from two channels";
  private Studio studio_;

  @Override
  public void setContext(Studio studio) {
    studio_ = studio;
  }

  @Override
  public ProcessorConfigurator createConfigurator(PropertyMap settings) {
    RatioImagingFrame ratioImagingFrame = new RatioImagingFrame(settings, studio_);
    studio_.events().registerForEvents(ratioImagingFrame);
    return ratioImagingFrame;
  }

  @Override
  public ProcessorFactory createFactory(PropertyMap settings) {
    return new RatioImagingFactory(studio_, settings);
  }

  @Override
  public String getName() {
    return "Ratio Imaging";
  }

  @Override
  public String getHelpText() {
    return TOOL_TIP_DESCRIPTION;
  }

  @Override
  public String getVersion() {
    return "0.1";
  }

  @Override
  public String getCopyright() {
    return "Regents of the University of California, 2018";
  }
}
