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

package org.micromanager.acquisition.internal;

import javax.swing.ImageIcon;
import org.micromanager.Studio;
import org.micromanager.internal.MMStudio;
import org.scijava.plugin.Plugin;
import org.scijava.plugin.SciJavaPlugin;

/** This built-in plugin provides access to our standard MDA dialog. */
@Plugin(type = AcquisitionDialogPlugin.class)
public final class DefaultMDAPlugin implements AcquisitionDialogPlugin, SciJavaPlugin {
  private Studio studio_;

  @Override
  public ImageIcon getIcon() {
    // This icon based on the public-domain icon at
    // https://openclipart.org/detail/2757/movie-tape
    return new ImageIcon(
        getClass().getClassLoader().getResource("org/micromanager/icons/film.png"));
  }

  @Override
  public void showAcquisitionDialog() {
    ((MMStudio) studio_).uiManager().openAcqControlDialog();
  }

  @Override
  public void setContext(Studio studio) {
    studio_ = studio;
  }

  @Override
  public String getName() {
    return "Multi-D Acq.";
  }

  @Override
  public String getHelpText() {
    return "Open the data acquisition dialog.";
  }

  @Override
  public String getVersion() {
    return "1.0";
  }

  @Override
  public String getCopyright() {
    return "Copyright (c) 2016 Open Imaging, Inc. and the Regents of the University of California";
  }
}
