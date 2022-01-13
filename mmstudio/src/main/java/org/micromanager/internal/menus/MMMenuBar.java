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
   private FileMenu fileMenu_;
   private ToolsMenu toolsMenu_;
   private final MMStudio mmStudio_;

   /**
    * Creates the standard Micro-Manager menu bar.
    */
   public static MMMenuBar createMenuBar(MMStudio mmStudio) {
      MMMenuBar result = new MMMenuBar(mmStudio);
      result.createSubMenus();
      return result;      
   }
   
   private MMMenuBar(MMStudio mmStudio) {
      mmStudio_ = mmStudio;
   }
   
   private void createSubMenus() {
      fileMenu_ = new FileMenu(mmStudio_, this);
      toolsMenu_ = new ToolsMenu(mmStudio_, this);
      new ConfigMenu(mmStudio_, this);
      ((DefaultPluginManager) mmStudio_.plugins()).createPluginMenu(this);
      new WindowMenu(mmStudio_, this);
      new HelpMenu(mmStudio_, this);
   }
   
   public FileMenu getFileMenu() {
      return fileMenu_;
   } 
   
   public ToolsMenu getToolsMenu() {
      return toolsMenu_;
   }
}
