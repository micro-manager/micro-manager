///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nico Stuurman, nico@cmp.ucsf.edu, July, 2007
//
// COPYRIGHT:    University of California, San Francisco, 2007
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
//
// CVS:          $Id: ProgressBar.java 2 2007-02-27 23:33:17Z nenad $
//
package org.micromanager.internal.utils;

import java.awt.Color;
import java.util.HashMap;

import javax.swing.plaf.ColorUIResource;
import javax.swing.UIManager;

/*
 * This class controls the colors of the user interface
 * Note we use ColorUIResources instead of Colors because Colors don't
 * interact well with Look and Feel; see
 * http://stackoverflow.com/questions/27933017/cant-update-look-and-feel-on-the-fly
 */
public class GUIColors {
   final public String STYLE_DAY = "Day";
   final public String STYLE_NIGHT = "Night";
   // List of display options
   final public String[] styleOptions = {STYLE_DAY, STYLE_NIGHT};

   // background color of the UI
   public HashMap<String, ColorUIResource> background_;
   // background color of pads in the UI
   public HashMap<String, ColorUIResource> padBackground_;
   // Color of text.
   public HashMap<String, ColorUIResource> textColor_;

   public GUIColors() {
      // Possible: make UI to let user set these colors
      background_ = new HashMap<String, ColorUIResource>();
      background_.put(STYLE_DAY, new ColorUIResource(java.awt.SystemColor.control));
      background_.put(STYLE_NIGHT, new ColorUIResource(new Color(64, 64, 64)));

      padBackground_ = new HashMap<String, ColorUIResource>();
      padBackground_.put(STYLE_DAY, new ColorUIResource(Color.white));
      padBackground_.put(STYLE_NIGHT, new ColorUIResource(java.awt.SystemColor.control));

      textColor_ = new HashMap<String, ColorUIResource>();
      textColor_.put(STYLE_DAY, new ColorUIResource(UIManager.getColor("TextField.foreground")));
      textColor_.put(STYLE_NIGHT, new ColorUIResource(200, 200, 200));
   }

}
