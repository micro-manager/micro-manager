// Copyright (C) 2015-2017 Open Imaging, Inc.
//           (C) 2015 Regents of the University of California
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

package org.micromanager.display.internal.displaywindow;

import org.micromanager.display.AbstractDataViewer;
import org.micromanager.display.DisplaySettings;
import org.micromanager.display.DisplayWindow;

import java.awt.*;

/**
 * This is to {@code DisplayWindow} what {@code AbstractDataViewer} is to {@code DataViewer}.
 *
 * <p>Segregate support of deprecated methods from the main implementation.
 *
 * @author Mark A. Tsuchida
 */
public abstract class DisplayWindowAPIAdapter extends AbstractDataViewer implements DisplayWindow {
  protected DisplayWindowAPIAdapter(DisplaySettings initialDisplaySettings) {
    super(initialDisplaySettings);
  }

  @Override
  @Deprecated
  public double getMagnification() {
    return getZoom();
  }

  @Override
  @Deprecated
  public void setMagnification(double ratio) {
    setZoom(ratio);
  }

  @Override
  @Deprecated
  public Window getAsWindow() {
    try {
      return getWindow();
    } catch (IllegalStateException e) {
      return null;
    }
  }
}
