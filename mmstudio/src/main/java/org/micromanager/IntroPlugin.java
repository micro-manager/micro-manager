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

package org.micromanager;

import javax.swing.*;
import java.util.List;

/**
 * IntroPlugins are used to customize the intro dialog that prompts the user for which config file
 * and profile to use. Only one IntroPlugin can be effectively used at a time; the program will use
 * whichever one was loaded first.
 */
public interface IntroPlugin extends MMPlugin {
  /**
   * Provide a "splash" image to display at the top of the intro dialog. If this method returns
   * null, then the default µManager logo will be used.
   *
   * @return The image to use at the top of the intro dialog, or null.
   */
  public Icon getSplashImage();

  /**
   * Provide a list of paths to config files to include in the config file dropdown menu. These will
   * be provided in addition to any config files the user has used in the past. This list may be
   * null or empty.
   *
   * @return List of config files to show to the user, or null.
   */
  public List<String> getConfigFilePaths();
}
