///////////////////////////////////////////////////////////////////////////////
// FILE:          MultiChannelShading.java
// PROJECT:       Micro-Manager
// SUBSYSTEM:     MultiChannelShading plugin
// -----------------------------------------------------------------------------
//
// AUTHOR:       Kurt Thorn, Nico Stuurman
//
// COPYRIGHT:    University of California, San Francisco 2014
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

package org.micromanager.multichannelshading;

import org.micromanager.data.ProcessorConfigurator;
import org.micromanager.data.ProcessorFactory;
import org.micromanager.data.ProcessorPlugin;
import org.micromanager.PropertyMap;
import org.micromanager.Studio;

import org.scijava.plugin.Plugin;
import org.scijava.plugin.SciJavaPlugin;

/** @author kthorn */
@Plugin(type = ProcessorPlugin.class)
public class MultiChannelShading implements ProcessorPlugin, SciJavaPlugin {
  public static final String MENUNAME = "Flat-Field Correction";
  public static final String TOOLTIPDESCRIPTION =
      "Apply dark subtraction and flat-field correction";

  public static final String VERSIONNUMBER = "0.3";

  private Studio studio_;

  @Override
  public void setContext(Studio studio) {
    studio_ = studio;
  }

  @Override
  public ProcessorConfigurator createConfigurator(PropertyMap settings) {
    return new MultiChannelShadingMigForm(settings, studio_);
  }

  @Override
  public ProcessorFactory createFactory(PropertyMap settings) {
    return new MultiChannelShadingFactory(studio_, settings);
  }

  @Override
  public String getName() {
    return MENUNAME;
  }

  @Override
  public String getHelpText() {
    return TOOLTIPDESCRIPTION;
  }

  @Override
  public String getVersion() {
    return VERSIONNUMBER;
  }

  @Override
  public String getCopyright() {
    return "University of California, 2014-2016";
  }
}
