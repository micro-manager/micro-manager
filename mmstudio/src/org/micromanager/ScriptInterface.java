///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nenad Amodaj, nenad@amodaj.com, December 3, 2006
//               Chris Weisiger, 2015
//
// COPYRIGHT:    University of California, San Francisco, 2006-2015
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

package org.micromanager;


import mmcorej.CMMCore;

import org.micromanager.data.DataManager;
import org.micromanager.display.DisplayManager;
import org.micromanager.internal.utils.AutofocusManager;


/**
 * Interface to execute commands in the main panel. Implemented by
 * MMStudio and available as the "mm" object in the Beanshell
 * scripting panel.
 */
public interface ScriptInterface {
   /**
    * Provides access to the compatibility layer that exposes some old 1.4
    * API methods.
    * @return An implementation of the compatibility API.
    */
   public CompatibilityInterface compat();

   /**
    * Provides access to the compatibility layer that exposes some old 1.4
    * API methods. Identical to compat() in all but name.
    * @return An implementation of the compatibility API.
    */
   public CompatibilityInterface getCompatibilityInterface();

   /**
    * Provides access to Micro-Manager's logging functions, like logError(),
    * showMessage(), etc.
    * @return Access to Micro-Manager's logging interface.
    */
   public LogManager logs();

   /**
    * Provides access to Micro-Manager's logging functions, like logError(),
    * showMessage(), etc. Identical to logs() except in name.
    * @return Access to Micro-Manager's logging interface.
    */
   public LogManager getLogManager();

   /**
    * @return the currently selected AutoFocusManger object
    */
   public AutofocusManager getAutofocusManager();
   
   /**
    * Provides access to the Core and its functionality.
    * @return Micro-Manager core object. 
    */
   public CMMCore core();

   /**
    * Provides access to the Core and its functionality. Identical to core()
    * except in name.
    * @return Micro-Manager core object. 
    */
   public CMMCore getCMMCore();

   /**
    * Provide access to the DataManager instance for accessing Micro-Manager
    * data constructs.
    * @return DataManager instance
    */
   public DataManager data();

   /**
    * Provide access to the DataManager instance for accessing Micro-Manager
    * data constructs. Identical to data() except in name.
    * @return DataManager instance
    */
   public DataManager getDataManager();

   /**
    * Provides access to the DisplayManager instance for accessing
    * Micro-Manager display constructs.
    * @return DisplayManager instance
    */
   public DisplayManager displays();

   /**
    * Provides access to the DisplayManager instance for accessing
    * Micro-Manager display constructs. Identical to displays() except in name.
    * @return DisplayManager instance
    */
   public DisplayManager getDisplayManager();

   /**
    * Provides access to some utility methods for use in the Beanshell
    * scripting panel.
    * @return ScriptController instance.
    */
   public ScriptController scripter();

   /**
    * Provides access to some utility methods for use in the Beanshell
    * scripting panel. Identical to scripter() except in name.
    * @return ScriptController instance.
    */
   public ScriptController getScriptController();

   /**
    * Provides access to the UserProfile instance for accessing per-user
    * profiles.
    * @return UserProfile instance
    */
   public UserProfile profile();

   /**
    * Provides access to the UserProfile instance for accessing per-user
    * profiles. Identical to profile() except in name.
    * @return UserProfile instance
    */
   public UserProfile getUserProfile();
}
