///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//-----------------------------------------------------------------------------
//AUTHOR:        Mark Tsuchida, Chris Weisiger
//COPYRIGHT:     University of California, San Francisco, 2006-2015
//               100X Imaging Inc, www.100ximaging.com, 2008
//LICENSE:       This file is distributed under the BSD license.
//               License text is included with the source distribution.
//               This file is distributed in the hope that it will be useful,
//               but WITHOUT ANY WARRANTY; without even the implied warranty
//               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
package org.micromanager.internal.pipelineinterface;

import org.micromanager.data.ProcessorConfigurator;
import org.micromanager.data.ProcessorPlugin;

/**
 * This class wraps around a ProcessorConfigurator, allowing it to be easily
 * enabled or disabled without having any logic in the plugin side of things.
 * It also contains some backreferences to the plugin that created the
 * Configurator, for ease of use.
 */
public class ConfiguratorWrapper {
   private ProcessorPlugin plugin_;
   private ProcessorConfigurator configurator_;
   private String name_;
   private boolean isEnabled_;

   public ConfiguratorWrapper(ProcessorPlugin plugin,
         ProcessorConfigurator configurator, String name) {
      plugin_ = plugin;
      configurator_ = configurator;
      name_ = name;
      isEnabled_ = true;
   }

   public ProcessorPlugin getPlugin() {
      return plugin_;
   }

   public ProcessorConfigurator getConfigurator() {
      return configurator_;
   }

   public String getName() {
      return name_;
   }

   public boolean getIsEnabled() {
      return isEnabled_;
   }

   public void setIsEnabled(boolean isEnabled) {
      isEnabled_ = isEnabled;
   }
}
