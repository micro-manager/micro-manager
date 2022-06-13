package de.embl.rieslab.emu.utils.exceptions;

/**
 * Exception thrown when the {@link de.embl.rieslab.emu.controller.SystemController} is asked
 * to reload the system with a UIPlugin and PluginConfiguration that are not compatible.
 *
 * @author Joran Deschamps
 */
public class IncompatiblePluginConfigurationException extends Exception {

   private static final long serialVersionUID = 1L;

   public IncompatiblePluginConfigurationException(String pluginName, String configurationName) {
      super("The configuration \"" + configurationName + "\" is incompatible with the "
            + pluginName + " plugin.");
   }
}
