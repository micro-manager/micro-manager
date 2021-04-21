///////////////////////////////////////////////////////////////////////////////
// PROJECT:       Micro-Manager
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

package org.micromanager.quickaccess.internal.controls;

import com.bulenkov.iconloader.IconLoader;
import org.micromanager.Studio;
import org.micromanager.quickaccess.SimpleButtonPlugin;
import org.scijava.plugin.Plugin;
import org.scijava.plugin.SciJavaPlugin;

import javax.swing.*;

/** Implements the "Refresh" button logic. */
@Plugin(type = SimpleButtonPlugin.class)
public final class RefreshButton extends SimpleButtonPlugin implements SciJavaPlugin {
  private Studio studio_;

  @Override
  public void setContext(Studio studio) {
    studio_ = studio;
  }

  @Override
  public String getName() {
    return "Refresh GUI";
  }

  @Override
  public String getHelpText() {
    return "Refresh the GUI so it reflects the current state of the system.";
  }

  @Override
  public String getVersion() {
    return "1.0";
  }

  @Override
  public String getCopyright() {
    return "Copyright (c) 2015 Open Imaging, Inc.";
  }

  @Override
  public ImageIcon getIcon() {
    return new ImageIcon(IconLoader.loadFromResource("/org/micromanager/icons/arrow_refresh.png"));
  }

  @Override
  public String getTitle() {
    return "Refresh";
  }

  @Override
  public Icon getButtonIcon() {
    return IconLoader.getIcon("/org/micromanager/icons/arrow_refresh.png");
  }

  @Override
  public void activate() {
    studio_.app().refreshGUI();
  }
}
