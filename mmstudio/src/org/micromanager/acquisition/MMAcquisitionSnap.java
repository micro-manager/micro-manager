///////////////////////////////////////////////////////////////////////////////
//FILE:          MMAcquisitionSnap.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------

//AUTHOR:       Arthur Edelstein, arthuredelstein@gmail.com, January 16, 2009

//COPYRIGHT:    University of California, San Francisco, 2009

//LICENSE:      This file is distributed under the BSD license.
//License text is included with the source distribution.

//This file is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty
//of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.

//IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.

package org.micromanager.acquisition;

import mmcorej.CMMCore;

import org.micromanager.MMStudioMainFrame;
import org.micromanager.image5d.Image5D;
import org.micromanager.image5d.Image5DWindow;
import org.micromanager.image5d.Image5DWindowSnap;
import org.micromanager.utils.MMScriptException;
import org.micromanager.utils.ReportingUtils;



public class MMAcquisitionSnap extends MMAcquisition {
	MMStudioMainFrame gui_;
	
	public MMAcquisitionSnap(String name, String dir) {
		super(name, dir);
	}

	public MMAcquisitionSnap(String name, String dir, MMStudioMainFrame gui, boolean show) {
		super(name, dir, show);
		gui_ = gui;
	}
	
	protected Image5DWindow createImage5DWindow(Image5D img5d) {
		return new Image5DWindowSnap(img5d, this);
	}
	
	public void increaseFrameCount() {
		numFrames_++;
	}
	
	public Boolean isCompatibleWithCameraSettings() {
		CMMCore core = gui_.getCore();
		Boolean compatible = 
			(core.getImageWidth() == width_)
		 && (core.getImageHeight() == height_ )
		 && (core.getBytesPerPixel() == depth_ );		 
		return compatible;
	}
	
	public void doSnapReplace() {
		try {

			if (isCompatibleWithCameraSettings()) {
				Image5D i5d = imgWin_.getImage5D();
				int n = i5d.getCurrentFrame();
				gui_.snapAndAddImage(name_ ,n-1,0,0);
			} else {
				gui_.snapAndAddToImage5D(null);
			}
		} catch (MMScriptException e) {
			ReportingUtils.showError(e);
		}

	}
	
	public void doSnapAppend() {
		gui_.snapAndAddToImage5D(name_);


	}
	

	public void appendImage(Object pixels) throws MMScriptException {
		numFrames_++;
		Image5D i5d = imgWin_.getImage5D();
		int n = i5d.getDimensionSize(4);
		if (n < numFrames_)
			i5d.expandDimension(4,numFrames_,false);
		insertImage(pixels, numFrames_-1 , 0, 0);
	}
	


}