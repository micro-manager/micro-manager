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

import org.micromanager.data.ProcessorConfigurator;
import org.micromanager.data.ProcessorPlugin;
import org.micromanager.data.ProcessorFactory;

import org.micromanager.PropertyMap;
import org.micromanager.Studio;

import org.scijava.plugin.Plugin;
import org.scijava.plugin.SciJavaPlugin;

@Plugin(type = ProcessorPlugin.class)
public class FlipperPlugin implements ProcessorPlugin, SciJavaPlugin {
  private Studio studio_;

  @Override
  public void setContext(Studio studio) {
    studio_ = studio;
  }

  @Override
  public ProcessorConfigurator createConfigurator(PropertyMap settings) {
    return new FlipperConfigurator(studio_, settings);
  }

  @Override
  public ProcessorFactory createFactory(PropertyMap settings) {
    return new FlipperFactory(settings, studio_);
  }

  @Override
  public String getName() {
    return "Image Flipper";
  }

  @Override
  public String getHelpText() {
    return "Rotates and/or mirrors images coming from the selected camera";
  }

  @Override
  public String getVersion() {
    return "Version 1.0";
  }

  @Override
  public String getCopyright() {
    return "Copyright University of California San Francisco, 2015";
  }
}
