///////////////////////////////////////////////////////////////////////////////
// PROJECT:       Micro-Manager
// SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------

// COPYRIGHT:    University of California, San Francisco, 2008

// LICENSE:      This file is distributed under the BSD license.
// License text is included with the source distribution.

// This file is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty
// of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.

// IN NO EVENT SHALL THE COPYRIGHT OWNER OR
// CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
// INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.

package org.micromanager.internal.navigation;

import com.google.common.eventbus.Subscribe;
import ij.gui.ImageCanvas;
import mmcorej.CMMCore;
import mmcorej.MMCoreJ;
import org.micromanager.Studio;
import org.micromanager.display.internal.event.DisplayKeyPressEvent;
import org.micromanager.internal.dialogs.StageControlFrame;
import org.micromanager.internal.utils.ReportingUtils;
import org.micromanager.propertymap.MutablePropertyMapView;

import javax.swing.*;
import java.awt.event.KeyEvent;
import java.util.concurrent.ExecutorService;

import static org.micromanager.internal.dialogs.StageControlFrame.X_MOVEMENTS;
import static org.micromanager.internal.dialogs.StageControlFrame.Y_MOVEMENTS;

/**
 * @author OD
 *
 */
public final class XYZKeyListener  {
	private final CMMCore core_;
    private final Studio studio_;
	private final ExecutorService executorService_;
	private final MutablePropertyMapView settings_;
	double[] xStepSizes_;
	double[] yStepSizes_;

	private boolean mirrorX_;
	private boolean mirrorY_;
	private boolean transposeXY_;
	private boolean correction_;
	private static final double zmoveIncrement_ = 0.20;
	public static int ctrlZStep = 1;
	public static int normalZStep = 3;
	public static int shiftZStep = 10;

	public XYZKeyListener(Studio studio, ExecutorService executorService) {
       studio_ = studio;
       executorService_ = executorService;
       core_ = studio_.getCMMCore();

       xStepSizes_ = new double[] {1.0, core_.getImageWidth() / 4, core_.getImageWidth()};
       yStepSizes_ = new double[] {1.0, core_.getImageHeight() / 4, core_.getImageHeight()};

       settings_ = studio_.profile().getSettings(StageControlFrame.class);

       getOrientation();
	}

	@Subscribe
	public void keyPressed(DisplayKeyPressEvent dkpe) {
		KeyEvent e = dkpe.getKeyEvent();
		boolean consumed = false;
		for (int i = 0; i < xStepSizes_.length; ++i) {
			xStepSizes_[i] = settings_.getDouble(X_MOVEMENTS[i], xStepSizes_[i]);
		}
		for (int i = 0; i < yStepSizes_.length; ++i) {
			yStepSizes_[i] = settings_.getDouble(Y_MOVEMENTS[i], yStepSizes_[i]);
		}

		switch (e.getKeyCode()) {
		case KeyEvent.VK_LEFT:
		case KeyEvent.VK_RIGHT:
		case KeyEvent.VK_UP:
		case KeyEvent.VK_DOWN:
			//XY step
			double stepX = xStepSizes_[1];
			double stepY = yStepSizes_[1];
			if (e.isControlDown()) {
				stepX = xStepSizes_[0];
				stepY =yStepSizes_[0];
			} else if (e.isShiftDown()) {
				stepX = xStepSizes_[2];
				stepY = yStepSizes_[2];
			}
			switch (e.getKeyCode()) {
			case KeyEvent.VK_LEFT:
				IncrementXY(-stepX, 0);
				consumed = true;
				break;
			case KeyEvent.VK_RIGHT:
				IncrementXY(stepX, 0);
				consumed = true;
				break;
			case KeyEvent.VK_UP:
				IncrementXY(0, stepY);
				consumed = true;
				break;
			case KeyEvent.VK_DOWN:
				IncrementXY(0, -stepY);
				consumed = true;
			}
			break;
		case KeyEvent.VK_1:
		case KeyEvent.VK_U:
		case KeyEvent.VK_PAGE_UP:
		case KeyEvent.VK_2:
		case KeyEvent.VK_J:
		case KeyEvent.VK_PAGE_DOWN:
			int step = normalZStep;
			if (e.isControlDown())
				step = ctrlZStep;
			else if (e.isShiftDown())
				step = shiftZStep;
			switch (e.getKeyCode()) {
			case KeyEvent.VK_1:
			case KeyEvent.VK_U:
			case KeyEvent.VK_PAGE_UP:
				IncrementZ(-step);
				consumed = true;
				break;
			case KeyEvent.VK_2:
			case KeyEvent.VK_J:
			case KeyEvent.VK_PAGE_DOWN:
				IncrementZ(step);
				consumed = true;
			}
		}
		if (consumed) {
			dkpe.consume();
		}
	}


	public void IncrementXY(double stepX, double stepY) {
		// Get needed info from core
		getOrientation();
		String xyStage = core_.getXYStageDevice();
		if (xyStage == null)
			return;
		try {
			if (core_.deviceBusy(xyStage))
				return;
		} catch (Exception ex) {
			ReportingUtils.showError(ex);
			return;
		}

		double pixSizeUm = core_.getPixelSizeUm();
		if (!(pixSizeUm > 0.0)) {
			JOptionPane
					.showMessageDialog(null,
							"Please provide pixel size calibration data before using this function");
			return;
		}

		// calculate needed relative movement
		double tmpXUm = stepX * pixSizeUm;
		double tmpYUm = stepY * pixSizeUm;

		double mXUm = tmpXUm;
		double mYUm = tmpYUm;
		// if camera does not correct image orientation, we'll correct for it here:
		if (!correction_) {
			// Order: swapxy, then mirror axis
			if (transposeXY_) {
				mXUm = tmpYUm;
				mYUm = tmpXUm;
			}
			if (mirrorX_) {
				mXUm = -mXUm;
			}
			if (mirrorY_) {
				mYUm = -mYUm;
			}
		}

		// Move the stage
		try {
			core_.setRelativeXYPosition(xyStage, mXUm, mYUm);
		} catch (Exception ex) {
			ReportingUtils.showError(ex);
			return;
		}

      // Cheap way to update XY position in GUI
      //studio_.updateXYPosRelative(mXUm, mYUm);
	}

	public void IncrementZ(int step) {
		// Get needed info from core
		String zStage = core_.getFocusDevice();
		if (zStage == null || zStage.length() == 0)
			return;

		double moveIncrement = zmoveIncrement_;
		double pixSizeUm = core_.getPixelSizeUm();
		if (pixSizeUm > 0.0) {
			moveIncrement = pixSizeUm;
		}

		// Move the stage
		try {
			core_.setRelativePosition(zStage, moveIncrement * step);
		} catch (Exception ex) {
			ReportingUtils.showError(ex);
		}
	}

	public void getOrientation() {
		String camera = core_.getCameraDevice();
		if (camera == null) {
			JOptionPane.showMessageDialog(null,
					"This function does not work without a camera");
			return;
		}
		try {
			String tmp = core_.getProperty(camera, "TransposeCorrection");
         correction_ = !tmp.equals("0");
			tmp = core_.getProperty(camera, MMCoreJ
					.getG_Keyword_Transpose_MirrorX());
         mirrorX_ = !tmp.equals("0");
			tmp = core_.getProperty(camera, MMCoreJ
					.getG_Keyword_Transpose_MirrorY());
         mirrorY_ = !tmp.equals("0");
			tmp = core_.getProperty(camera, MMCoreJ
					.getG_Keyword_Transpose_SwapXY());
         transposeXY_ = !tmp.equals("0");
		} catch (Exception exc) {
			ReportingUtils.showError(exc);
		}
	}
   

}
