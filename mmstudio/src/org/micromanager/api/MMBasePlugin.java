
package org.micromanager.api;

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
