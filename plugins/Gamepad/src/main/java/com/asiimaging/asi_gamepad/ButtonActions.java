///////////////////////////////////////////////////////////////////////////////
//FILE:          ButtonActions.java
//PROJECT:       Micro-Manager 
//SUBSYSTEM:     asi gamepad plugin
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


package com.asiimaging.asi_gamepad;

import org.micromanager.SnapLiveManager;
import org.micromanager.display.DataViewer;
import org.micromanager.internal.MMStudio;

import java.io.File;
import java.io.IOException;

public class ButtonActions  {

	public enum ActionItems {
		Snap,
		Toggle_Live,
		Toggle_Shutter,
		Add_to_Album,
		Mark_Position,
		Autostretch_histograms,
		Run_Beanshell_script,
		Undefined
	} //end of ActionItem


/**
 * execute action based on enum supplied
 * @param ai actionitems enum 
 * @param arg string with the beanshell scripts absolute path. can use null if enum to be executed isn't Run_Beanshell_script
 */
	public void ExecuteAction(ActionItems ai,String arg) {

		MMStudio mmStudio = MMStudio.getInstance();
		SnapLiveManager snapLiveManager_ = mmStudio.getSnapLiveManager();

		switch(ai) {

		case Snap:
			snapLiveManager_.snap(true);
			break;
		case Toggle_Live:
			snapLiveManager_.setLiveModeOn(!snapLiveManager_.isLiveModeOn());
			break;
		case Toggle_Shutter:
			try {
				mmStudio.shutter().setShutter(mmStudio.shutter().getShutter());
			} catch (Exception ex) {
				mmStudio.logs().logError(ex, "Error discovering a shutter");
			}
			break;
		case Add_to_Album:
			try {
				mmStudio.album().addImage(mmStudio.live().snap(false).get(0));
			} catch (IOException ioe) {
				mmStudio.logs().logError("IOException accessing ALbum");
			}
			break;
		case Mark_Position:
			// TODO: once Stage manager is created
			// mmStudio.positions().markCurrentPosition();
			break;
		case Autostretch_histograms:
			DataViewer dv = mmStudio.displays().getActiveDataViewer();
			if (dv != null) {
				dv.setDisplaySettings(dv.getDisplaySettings().copyBuilder().autostretch(true).build());
				dv.setDisplaySettings(dv.getDisplaySettings().copyBuilder().autostretch(false).build());
			}
			break;
		case Run_Beanshell_script:
			File script2run = new File(arg);
			if (script2run.exists()) {
				mmStudio.scripter().runFile(script2run);
			} else {
				mmStudio.logs().logMessage("ASIGamepad> Script not found");
			}
			break;
		case Undefined:
		default:
			break;

		}

	}

}