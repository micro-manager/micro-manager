///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//AUTHOR:        Chris Weisiger, 2016
//COPYRIGHT:     (c) 2016 Open Imaging, Inc.
//LICENSE:       This file is distributed under the BSD license.
//               License text is included with the source distribution.
//               This file is distributed in the hope that it will be useful,
//               but WITHOUT ANY WARRANTY; without even the implied warranty
//               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.

package org.micromanager.internal.menus;

import javax.swing.JMenuBar;
import org.micromanager.internal.MMStudio;
import org.micromanager.internal.pluginmanagement.DefaultPluginManager;

/**
 * This class is the standard Micro-Manager menu bar.
 */
public final class MMMenuBar extends JMenuBar {
   private static FileMenu fileMenu_;
   public static MMMenuBar createMenuBar(MMStudio studio) {
      MMMenuBar result = new MMMenuBar();
      fileMenu_ = new FileMenu(studio, result);
      new ToolsMenu(studio, result);
      new ConfigMenu(studio, result);
      ((DefaultPluginManager) studio.plugins()).createPluginMenu(result);
      new HelpMenu(studio, result);
      return result;
   }
   public static FileMenu getFileMenu() {
      return fileMenu_;
   } 
}
