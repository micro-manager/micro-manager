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

/** Class implements the button actions 
 * @author Vikram Kopuri for ASI
 */


package com.asiimaging.asi_gamepad;

import java.io.File;

import org.micromanager.utils.ReportingUtils;

public class ButtonActions {

	public enum ActionItems{
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

		org.micromanager.MMStudio studio_ =
				org.micromanager.MMStudio.getInstance();
		org.micromanager.SnapLiveManager snapLiveManager_ =
				studio_.getSnapLiveManager();


		switch(ai) {

		case Snap:
			studio_.snapSingleImage();
			break;
		case Toggle_Live:
			snapLiveManager_.setLiveMode(!snapLiveManager_.getIsLiveModeOn());
			break;
		case Toggle_Shutter:
			studio_.toggleShutter();
			break;
		case Add_to_Album:
			snapLiveManager_.snapAndAddToImage5D();
			break;
		case Mark_Position:
			studio_.markCurrentPosition();
			break;
		case Autostretch_histograms:
			studio_.autostretchCurrentWindow();
			break;
		case Run_Beanshell_script:
			File script2run = new File(arg);
			if(script2run.exists()) {
				org.micromanager.script.ScriptPanel.runFile(script2run);
			}else {
				ReportingUtils.logMessage("ASIGamepad> Script not found");
			}
			break;
		case Undefined:
		default:
			break;


		}

	}

}


