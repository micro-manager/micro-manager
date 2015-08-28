package ch.epfl.leb.autolase;

import org.micromanager.MenuPlugin;
import org.micromanager.Studio;

import org.scijava.plugin.Plugin;
import org.scijava.plugin.SciJavaPlugin;

/**
 * The plugin class for MicroManager which works as a bridge between AutoLase
 * and MicroManager. See Autolase for more information.
 * 
 * @author Thomas Pengo
 * 
 * @see AutoLase
 */
@Plugin(type = MenuPlugin.class)
public class AutoLasePlugin implements org.micromanager.MenuPlugin, SciJavaPlugin {
   public static final String menuName = "AutoLase";
   public static final String tooltipDescription =
      "Closed-loop imaged-based photoactivation control for PALM";
   private Studio studio_;

    @Override
    public void setContext(Studio app) {
       studio_ = app;
        AutoLase.INSTANCE.setup(app);
    }

    @Override
    public void onPluginSelected() {
        AutoLase.INSTANCE.show();
    }

    @Override
    public String getName() {
        return menuName;
    }

    @Override
    public String getSubMenu() {
        return "Device Control";
    }

    @Override
    public String getHelpText() {
        return "Automated single-molecule density control via activation laser power";
    }

    @Override
    public String getVersion() {
        return "0.1";
    }

    @Override
    public String getCopyright() {
        return "Thomas Pengo, Seamus Holden, 2012";
    }
}
