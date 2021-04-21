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

/** Implements the "CloseAll" button logic. */
@Plugin(type = SimpleButtonPlugin.class)
public final class CloseAllButton extends SimpleButtonPlugin implements SciJavaPlugin {
  private Studio studio_;

  @Override
  public void setContext(Studio studio) {
    studio_ = studio;
  }

  @Override
  public String getName() {
    return "Close All";
  }

  @Override
  public String getHelpText() {
    return "Close all open image windows, optionally prompting to save unsaved data.";
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
    return new ImageIcon(
        IconLoader.loadFromResource("/org/micromanager/icons/close_windows@2x.png"));
  }

  @Override
  public String getTitle() {
    return "Close All";
  }

  @Override
  public Icon getButtonIcon() {
    return IconLoader.getIcon("/org/micromanager/icons/close_windows.png");
  }

  @Override
  public void activate() {
    studio_.displays().promptToCloseWindows();
  }
}
