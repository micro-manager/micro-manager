///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     Data API
//-----------------------------------------------------------------------------
//
// AUTHOR:       Chris Weisiger, 2015
//
// COPYRIGHT:    University of California, San Francisco, 2015
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

package org.micromanager.data;

import org.micromanager.PropertyMap;

/**
 * ProcessorPluginConfigurators are used to generate the PropertyMaps needed
 * to set up a ProcessorFactory. In practical use, Micro-Manager will request
 * a Configurator from the DataProcessorPlugin, the user will interact with
 * the Configurator by setting values, and the Configurator will provide a
 * PropertyMap representing the values the user has set.
 */
public interface ProcessorConfigurator {
   /**
    * Display any GUI needed for performing configuration.
    */
   void showGUI();

   /**
    * Remove any GUI resources currently in use.
    */
   void cleanup();

   /**
    * Provide a PropertyMap fully encapsulating the settings needed to set up
    * a new DataProcessor.
    * @return PropertyMap fully encapsulating the settings needed to set up
    * a new DataProcessor
    */
   PropertyMap getSettings();
}
