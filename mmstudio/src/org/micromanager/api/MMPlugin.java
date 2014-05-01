///////////////////////////////////////////////////////////////////////////////
//FILE:          MMPlugin.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
// DESCRIPTION:  API for MM plugins. Each module has to implement this interface.
//
// AUTHOR:       Nenad Amodaj, nenad@amodaj.com, 2008
//
// COPYRIGHT:    100X Imaging Inc, www.100ximaging.com, 2008
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

package org.micromanager.api;

/**
 * Implement this interface to create Micro-Manager plugins. Compiled jars
 * may be dropped into Micro-Manager's mmplugin directory, and if correctly
 * implemented, will appear in the Micro-Manager plugins menu.
 * You should look at the MMBasePlugin.java file as well for other functions
 * and member fields that should be implemented.
 */
public interface MMPlugin extends MMBasePlugin {
   /**
    * The main app calls this method to remove the module window
    */
   public void dispose();
   
   /**
    * The main app passes its ScriptInterface to the module. This
    * method is typically called after the module is instantiated.
    * @param app - ScriptInterface implementation
    */
   public void setApp(ScriptInterface app);
   
   /**
    * Open the module window
    */
   public void show();
      
}
