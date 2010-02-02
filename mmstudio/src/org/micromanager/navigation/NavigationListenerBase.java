/**
 * 
 */
package org.micromanager.navigation;

import ij.gui.ImageWindow;

import javax.swing.JOptionPane;

import mmcorej.CMMCore;
import mmcorej.MMCoreJ;
import org.micromanager.utils.ReportingUtils;

/**
 * @author OD
 *
 */
public abstract class NavigationListenerBase {
	private CMMCore core_;
	private static boolean isRunning_ = false;
	private boolean mirrorX_;
	private boolean mirrorY_;
	private boolean transposeXY_;
	private boolean correction_;

	public NavigationListenerBase(CMMCore core) {
		core_ = core;
	}

	public boolean isRunning() {
		return isRunning_;
	}
	public void start() {
		isRunning_=true;
	}
	public void stop() {
		isRunning_=false;
	}
	
	protected boolean needMirrorX(){return mirrorX_;}
	protected boolean needMirrorY(){return mirrorY_;}
	protected boolean needTransposeXY(){return transposeXY_;}
	protected boolean needCorrection(){return correction_;}
	
	public abstract void start(ImageWindow win);
	public abstract void attach(ImageWindow win);
	
	public void getOrientation() {
		String camera = core_.getCameraDevice();
		if (camera == null) {
			JOptionPane.showMessageDialog(null,
					"This function does not work without a camera");
			return;
		}
		try {
			String tmp = core_.getProperty(camera, "TransposeCorrection");
			if (tmp.equals("0"))
				correction_ = false;
			else
				correction_ = true;
			tmp = core_.getProperty(camera, MMCoreJ
					.getG_Keyword_Transpose_MirrorX());
			if (tmp.equals("0"))
				mirrorX_ = false;
			else
				mirrorX_ = true;
			tmp = core_.getProperty(camera, MMCoreJ
					.getG_Keyword_Transpose_MirrorY());
			if (tmp.equals("0"))
				mirrorY_ = false;
			else
				mirrorY_ = true;
			tmp = core_.getProperty(camera, MMCoreJ
					.getG_Keyword_Transpose_SwapXY());
			if (tmp.equals("0"))
				transposeXY_ = false;
			else
				transposeXY_ = true;
		} catch (Exception exc) {
			ReportingUtils.logError(exc);
			return;
		}

	}

}
