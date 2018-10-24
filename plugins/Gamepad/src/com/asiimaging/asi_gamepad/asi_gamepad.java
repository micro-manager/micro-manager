///////////////////////////////////////////////////////////////////////////////
//FILE:          asi_gamepad.java
//PROJECT:       Micro-Manager 
//SUBSYSTEM:      asi gamepad plugin
//-----------------------------------------------------------------------------
//
// AUTHOR:       Vikram Kopuri
//
// COPYRIGHT:    Applied Scientific Instrumentation (ASI), 2018
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

/**The main class of the plugin 
 * @author Vikram Kopuri for ASI
 */

package com.asiimaging.asi_gamepad;

import java.awt.event.WindowEvent;

import org.micromanager.api.MMPlugin;
import org.micromanager.api.ScriptInterface;

public class asi_gamepad implements MMPlugin {

	public final static String menuName = "ASI Gamepad";
	public final static String tooltipDescription = "XBox Controller for MicroManager";
	public final static String versionString = "0.0";
	public final static String copyrightString = "Applied Scientific Instrumentation (ASI), 2018";

	private ScriptInterface gui_;
	private static asi_gamepad_frame agf_frame = null;

	@Override
	public void setApp(ScriptInterface arg0) {

		gui_=arg0;
		// close frame before re-load if already open
		dispose();
		// create brand new instance of plugin frame every time
		try {
			agf_frame = new asi_gamepad_frame(gui_);
			gui_.addMMListener(agf_frame);
			gui_.addMMBackgroundListener(agf_frame);
		} catch (Exception e) {
			gui_.showError(e);
		}
	}

	public static asi_gamepad_frame getFrame() {
		return agf_frame;
	}

	@Override
	public String getCopyright() {

		return copyrightString;
	}

	@Override
	public String getDescription() {

		return tooltipDescription;
	}

	@Override
	public String getInfo() {

		return menuName;
	}

	@Override
	public String getVersion() {
		if(agf_frame!=null) {
		return Float.toString(agf_frame.plugin_ver);	
		}else {
		return versionString;
		}
	}

	@Override
	public void dispose() {
		if (agf_frame != null && agf_frame.isDisplayable()) {
			WindowEvent wev = new WindowEvent(agf_frame, WindowEvent.WINDOW_CLOSING);
			//ReportingUtils.logMessage("!!!!closed from main gamepad class!!!!");
			agf_frame.dispatchEvent(wev);
		}

	}
	@Override
	public void show() {
		@SuppressWarnings("unused")
		String ig = menuName;

	}

}
