package org.micromanager.slideexplorer;

import mmcorej.CMMCore;

import org.micromanager.api.MMPlugin;
import org.micromanager.api.ScriptInterface;
import org.micromanager.utils.ReportingUtils;

public class SlideExplorerPlugin implements MMPlugin {
	public static final String menuName = "Slide Explorer";
	public static final String tooltipDescription =
		"Automatically acquire images as you pan and zoom, " +
		"constructing a tiled map of the sample";

	private CMMCore core_;
	private Hub hub_;
    private ScriptInterface app_;
	
	
	public void configurationChanged() {
		// TODO Auto-generated method stub
		
	}

	public void dispose() {
		hub_.shutdown();
	}

	public String getCopyright() {
		// TODO Auto-generated method stub
		return "University of California, San Francisco, 2009. Author: Arthur Edelstein";
	}

	public String getDescription() {
		// TODO Auto-generated method stub
		return tooltipDescription;
	}

	public String getInfo() {
		// TODO Auto-generated method stub
		return null;
	}

	public String getVersion() {
		// TODO Auto-generated method stub
		return null;
	}

	public void setApp(ScriptInterface app) {
		app_ = app;
	}

	public void show() {
      ReportingUtils.showMessage("Warning: the Slide Explorer plugin can move the XY-stage\n" +
              "long distances. Please be careful not to pan far from the slide\n" +
              "and make sure the objectives don't hit any other hardware.\n" +
              "Use at your own risk! ");
		hub_ = new Hub(app_);
	}
	
	
}
