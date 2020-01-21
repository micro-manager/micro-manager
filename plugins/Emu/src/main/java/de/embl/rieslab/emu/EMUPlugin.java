package de.embl.rieslab.emu;

import java.io.File;

import javax.swing.SwingUtilities;

import org.micromanager.MenuPlugin;
import org.micromanager.Studio;
import org.scijava.plugin.Plugin;
import org.scijava.plugin.SciJavaPlugin;

import de.embl.rieslab.emu.controller.SystemController;
import de.embl.rieslab.emu.controller.utils.GlobalSettings;


@Plugin(type = MenuPlugin.class)
public class EMUPlugin implements MenuPlugin, SciJavaPlugin {

	private SystemController controller_;
	private static Studio studio_;
	
	private static String name = "EMU";
	private static String description = "Easier Micro-manager User interface: loads its own UI plugins and interfaces them with Micro-manager device properties.";
	private static String copyright = "Joran Deschamps, EMBL, 2016-2019.";
	private static String version = "v1.0-alpha-release";

	@Override
	public String getCopyright() {
		return copyright;
	}

	@Override
	public String getHelpText() {
		return description;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public void setContext(Studio mmAPI) {
		studio_ = mmAPI;
	}

	@Override
	public String getSubMenu() {
		return "Interface";
	}

	@Override
	public void onPluginSelected() {
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				// make sure the directory EMU exist
				if(!(new File(GlobalSettings.HOME)).exists()){
					new File(GlobalSettings.HOME).mkdirs();
				}
				
				controller_ = new SystemController(studio_);
				controller_.start();
			}
		});
	}

	@Override
	public String getVersion() {
		return version;
	}

}
