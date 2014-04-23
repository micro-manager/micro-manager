package ch.epfl.leb.autolase;

import org.micromanager.api.ScriptInterface;

/**
 * The plugin class for MicroManager which works as a bridge between AutoLase
 * and MicroManager. See Autolase for more information.
 * 
 * @author Thomas Pengo
 * 
 * @see AutoLase
 */
public class AutoLasePlugin implements org.micromanager.api.MMPlugin {
   public static final String menuName = "AutoLase";
   public static final String tooltipDescription =
      "Closed-loop imaged-based photoactivation control for PALM";

    @Override
    public void dispose() {
        AutoLase.INSTANCE.dispose();
    }

    @Override
    public void setApp(ScriptInterface app) {
        AutoLase.INSTANCE.setup(app);
    }

    @Override
    public void show() {
        AutoLase.INSTANCE.show();
    }

    @Override
    public String getDescription() {
        return "Automated single-molecule density control via activation laser power";
    }

    @Override
    public String getInfo() {
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
