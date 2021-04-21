///////////////////////////////////////////////////////////////////////////////
// FILE:          SplitView.java
// PROJECT:       Micro-Manager
// SUBSYSTEM:     mmstudio
// -----------------------------------------------------------------------------
//
// AUTHOR:       Nico Stuurman
//
// COPYRIGHT:    University of California, San Francisco, 2011, 2012
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

package org.micromanager.splitview;

import org.micromanager.data.ProcessorConfigurator;
import org.micromanager.data.ProcessorFactory;
import org.micromanager.data.ProcessorPlugin;
import org.micromanager.PropertyMap;

import org.scijava.plugin.Plugin;
import org.scijava.plugin.SciJavaPlugin;

import org.micromanager.Studio;

/**
 * Micro-Manager plugin that can split the acquired image top-down or left-right and display the
 * split image as a two channel image.
 *
 * @author nico
 */
@Plugin(type = ProcessorPlugin.class)
public class SplitView implements ProcessorPlugin, SciJavaPlugin {
  public static final String MENU_NAME = "Split View";
  public static final String TOOL_TIP_DESCRIPTION =
      "Split images vertically or horizontally into two channels";
  private Studio studio_;

  @Override
  public void setContext(Studio studio) {
    studio_ = studio;
  }

  @Override
  public ProcessorConfigurator createConfigurator(PropertyMap settings) {
    return new SplitViewFrame(settings, studio_);
  }

  @Override
  public ProcessorFactory createFactory(PropertyMap settings) {
    return new SplitViewFactory(studio_, settings);
  }

  @Override
  public String getName() {
    return "Split View";
  }

  @Override
  public String getHelpText() {
    return TOOL_TIP_DESCRIPTION;
  }

  @Override
  public String getVersion() {
    return "0.2";
  }

  @Override
  public String getCopyright() {
    return "University of California, 2011-2017";
  }
}
