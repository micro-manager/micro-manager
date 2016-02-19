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

import org.micromanager.acquisition.AcquisitionManager;
import org.micromanager.data.DataManager;
import org.micromanager.display.DisplayManager;
import org.micromanager.events.EventManager;
import org.micromanager.quickaccess.QuickAccessManager;


/**
 * Interface to execute commands in the main panel. Implemented by
 * MMStudio and available as the "mm" object in the Beanshell
 * scripting panel.
 */
public interface Studio {
   /**
    * Provides access to and control of the contents of the Album, the
    * implicit, temporary image storage datastore.
    * @return An implementation of the Album API.
    */
   public Album album();

   /**
    * Provides access to and control of the contents of the Album, the
    * implicit, temporary image storage datastore. Identical to album() except
    * in name.
    * @return An implementation of the Album API.
    */
   public Album getAlbum();

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
    * Provide access to the AcquisitionManager, for running data acquisition
    * using the Micro-Manager acquisition engine.
    */
   public AcquisitionManager acquisitions();

   /**
    * Provide access to the AcquisitionManager, for running data acquisition
    * using the Micro-Manager acquisition engine. Identical to acquisitions()
    * except in name.
    */
   public AcquisitionManager getAcquisitionManager();

   /**
    * Provide access to the AutofocusManager, for performing autofocus
    * operations.
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
    * Provides access to the EventManager instance for subscribing to and
    * posting events on the application-wide EventBus.
    * @return EventManager instance
    */
   public EventManager events();

   /**
    * Provides access to the EventManager instance for subscribing to and
    * posting events on the application-wide EventBus. Identical to events()
    * except in name.
    * @return EventManager instance
    */
   public EventManager getEventManager();

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
    * Provides access to the Snap/Live display and associated logic.
    * @return SnapLiveManager instance.
    */
   public SnapLiveManager live();

   /**
    * Provides access to the Snap/Live display and associated logic. Identical
    * to live() except in name.
    * @return SnapLiveManager instance. 
    */
   public SnapLiveManager getSnapLiveManager();

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

   /**
    * Provides access to the PluginManager for accessing plugin instances.
    * @return PluginManager instance.
    */
   public PluginManager plugins();

   /**
    * Provides access to the PluginManager for accessing plugin instances.
    * Identical to plugins() except in name.
    * @return PluginManager instance.
    */
   public PluginManager getPluginManager();

   /**
    * Provides access to the PositionListManager for interacting with the
    * Stage Position List.
    * @return PositionListManager instance.
    */
   public PositionListManager positions();

   /**
    * Provides access to the PositionListManager for interacting with the
    * Stage Position List. Identical to positions() except in name.
    * @return PositionListManager instance.
    */
   public PositionListManager getPositionListManager();

   /**
    * Provides access to the QuickAccessManager for accessing the Quick-Access
    * Panel system.
    * @return QuickAccessManager instance.
    */
   public QuickAccessManager quickAccess();

   /**
    * Provides access to the QuickAccessManager for accessing the Quick-Access
    * Panel system. Identical to quickAccess() except in name.
    * @return QuickAccessManager instance.
    */
   public QuickAccessManager getQuickAccessManager();

   /**
    * Provides access to the ShutterManager for controlling the shutter state.
    * @return ShutterManager instance.
    */
   public ShutterManager shutter();

   /**
    * Provides access to the ShutterManager for controlling the shutter state.
    * Identical to shutter() except in name.
    * @return ShutterManager instance.
    */
   public ShutterManager getShutterManager();
}
