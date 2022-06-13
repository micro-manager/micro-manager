///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Chris Weisiger, 2016
//
// COPYRIGHT:    Open Imaging, Inc 2016
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

import java.util.List;

/**
 * This entity provides access to methods for controlling autofocus of the
 * microscope. You can access it via Studio.getAutofocusManager().
 */
public interface AutofocusManager {
   /**
    * Set the current AutofocusPlugin to use for performing autofocus actions.
    *
    * @param plugin AutofocusPlugin to use for autofocus.
    */
   void setAutofocusMethod(AutofocusPlugin plugin);

   /**
    * Set the current AutofocusPlugin by name. This will throw an
    * IllegalArgumentException if there is no AutofocusPlugin with the
    * specified name (as per the value returned by its getName() method).
    *
    * @param name Name of autofocus method to use.
    */
   void setAutofocusMethodByName(String name);

   /**
    * Return the current AutofocusPlugin being used to run autofocus. It is
    * recommended that callers invoke this method whenever the current
    * autofocus plugin is required, rather than storing its results.
    *
    * @return AutofocusPlugin currently selected.
    */
   AutofocusPlugin getAutofocusMethod();

   /**
    * Return a list of the current valid autofocus names, suitable for use in
    * setAutofocusMethodByName(). This includes both software AutofocusPlugins
    * (which are accessible via the PluginManager) and hardware autofocus
    * devices (which are not).
    *
    * @return List of valid autofocus names.
    */
   List<String> getAllAutofocusMethods();

   /**
    * Update the list of available autofocus devices by scanning the system
    * for autofocus device adapters and AutofocusPlugins.
    */
   void refresh();

   /**
    * Initializes all known autofocus plugins.
    */
   void initialize();
}
