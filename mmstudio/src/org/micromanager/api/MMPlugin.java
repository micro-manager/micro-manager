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

   public interface MMPlugin {
   
   /**
	*  The menu name is stored in a static string, so Micro-Manager
	*  can obtain it without instantiating the plugin
	*/
   public static String menuName = null;
   public static String tooltipDescription = null;
	
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
   
   /**
    * The main app calls this method when hardware settings change.
    * This call signals to the module that it needs to update whatever
    * information it needs from the MMCore.
    */
   public void configurationChanged();
   
   /**
    * Returns a very short (few words) description of the module.
    */
   public String getDescription();
   
   /**
    * Returns verbose information about the module.
    * This may even include a short help instructions.
    */
   public String getInfo();
   
   /**
    * Returns version string for the module.
    * There is no specific required format for the version
    */
   public String getVersion();
   
   /**
    * Returns copyright information
    */
   public String getCopyright();
   
}
