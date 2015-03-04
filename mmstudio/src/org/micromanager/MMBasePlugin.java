///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       ???
// COPYRIGHT:    University of California, San Francisco
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

/** 
 * In practice no user code should directly implement this interface; instead
 * they should implement MMPlugin or MMProcessorPlugin.
 */
public interface MMBasePlugin {
   
   /**
    * The menu name is stored in a static string, so Micro-Manager
    * can obtain it without instantiating the plugin
    * Implement this member in your plugin
	 
      public static String menuName;
     
   */
      
   /**
    * A tool-tip description can also be in a static string. This tool-tip
    * will appear on the Micro-Manager plugin menu item.
    * Implement this member in your plugin
   
        public static String tooltipDescription = null;
   */

   /**
    * Returns a very short (few words) description of the module.
    * 
    * @return very short (few words) description of the module
    */
   public String getDescription();
   
   /**
    * Returns verbose information about the module.
    * This may even include a short help instructions.
    * 
    * @return verbose information about the module
    */
   public String getInfo();
   
   /**
    * Returns version string for the module.
    * There is no specific required format for the version
    * 
    * @return version string for the module
    */
   public String getVersion();
   
   /**
    * Returns copyright information
    * 
    * @return copyright information
    */
   public String getCopyright();
   
}
