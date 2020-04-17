///////////////////////////////////////////////////////////////////////////////
// PROJECT:       Micro-Manager
// SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------

// COPYRIGHT:    University of California, San Francisco, 2008-2020

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
import mmcorej.CMMCore;
import org.micromanager.Studio;
import org.micromanager.display.internal.event.DisplayKeyPressEvent;
import org.micromanager.events.internal.MouseMovesStageStateChangeEvent;
import org.micromanager.propertymap.MutablePropertyMapView;

import java.awt.event.KeyEvent;

import static org.micromanager.internal.dialogs.StageControlFrame.MEDIUM_MOVEMENT_Z;
import static org.micromanager.internal.dialogs.StageControlFrame.SELECTED_Z_DRIVE;
import static org.micromanager.internal.dialogs.StageControlFrame.SMALL_MOVEMENT_Z;
import static org.micromanager.internal.dialogs.StageControlFrame.X_MOVEMENTS;
import static org.micromanager.internal.dialogs.StageControlFrame.Y_MOVEMENTS;

/**
 * @author Nico
 *
 */
public final class XYZKeyListener  {
	private final CMMCore core_;
	private final MutablePropertyMapView settings_;
	private final ZNavigator zNavigator_;
	private final XYNavigator xyNavigator_;
	double[] xMovesMicron_;
	double[] yMovesMicron_;
	double[] zMovesMicron_;
	private boolean active_;

	/**
	 * The XYZKeyListener receives settings from the StageControl plugin
	 * by means of the user profile.
	 *
	 * Array sizes, etc. match those of the StageControl plugin, so changes
	 * there may break things here.
	 *
	 * Movement requests funnel to the single instance of the
	 * XYNavigator.  The XYNavigator send the actual movement comman to the
	 * XY stage using its own executor. Movements can be send even while
	 * the stage is moving.  Once the stage stops moving, all (added)
	 * movement requests will be combined into a single stage movement.
	 *
	 * @param studio Our beloved Micro-Manager Studio object
	 * @param xyNavigator Object that manages communication to XY Stages
	 * @param zNavigator Object that manages communication to Z Stages.
	 */
	public XYZKeyListener(Studio studio, XYNavigator xyNavigator, ZNavigator zNavigator) {
       core_ = studio.getCMMCore();
       xyNavigator_ = xyNavigator;
       zNavigator_ = zNavigator;

       xMovesMicron_ = new double[] {1.0, core_.getImageWidth() / 4, core_.getImageWidth()};
       yMovesMicron_ = new double[] {1.0, core_.getImageHeight() / 4, core_.getImageHeight()};
       zMovesMicron_ = new double[] {1.0, 10.0};

       active_ = false;

       settings_ = studio.profile().getSettings(
       		org.micromanager.internal.dialogs.StageControlFrame.class);
	}

	@Subscribe
	public void keyPressed(DisplayKeyPressEvent dkpe) {
		if (!active_) {
			return;
		}
		KeyEvent e = dkpe.getKeyEvent();
		boolean consumed = false;
		for (int i = 0; i < xMovesMicron_.length; ++i) {
			xMovesMicron_[i] = settings_.getDouble(X_MOVEMENTS[i], xMovesMicron_[i]);
		}
		for (int i = 0; i < yMovesMicron_.length; ++i) {
			yMovesMicron_[i] = settings_.getDouble(Y_MOVEMENTS[i], yMovesMicron_[i]);
		}
		zMovesMicron_[0] = settings_.getDouble(SMALL_MOVEMENT_Z, 1.1);
		zMovesMicron_[1] = settings_.getDouble(MEDIUM_MOVEMENT_Z, 11.1);

		switch (e.getKeyCode()) {
			case KeyEvent.VK_LEFT:
			case KeyEvent.VK_RIGHT:
			case KeyEvent.VK_UP:
			case KeyEvent.VK_DOWN:
				//XY step
				double xMicron = xMovesMicron_[1];
				double yMicron = yMovesMicron_[1];
				if (e.isControlDown()) {
					xMicron = xMovesMicron_[0];
					yMicron = yMovesMicron_[0];
				} else if (e.isShiftDown()) {
					xMicron = xMovesMicron_[2];
					yMicron = yMovesMicron_[2];
				}
				switch (e.getKeyCode()) {
					case KeyEvent.VK_LEFT:
						xyNavigator_.moveSampleOnDisplayUm(-xMicron, 0);
						consumed = true;
						break;
					case KeyEvent.VK_RIGHT:
						xyNavigator_.moveSampleOnDisplayUm(xMicron, 0);
						consumed = true;
						break;
					case KeyEvent.VK_UP:
						xyNavigator_.moveSampleOnDisplayUm(0, yMicron);
						consumed = true;
						break;
					case KeyEvent.VK_DOWN:
						xyNavigator_.moveSampleOnDisplayUm(0, -yMicron);
						consumed = true;
				}
				break;
			case KeyEvent.VK_1:
			case KeyEvent.VK_U:
			case KeyEvent.VK_PAGE_UP:
			case KeyEvent.VK_2:
			case KeyEvent.VK_J:
			case KeyEvent.VK_PAGE_DOWN:
				double zMicron = zMovesMicron_[0];
				if (e.isShiftDown()) {
					zMicron = zMovesMicron_[1];
		    }
			switch (e.getKeyCode()) {
			case KeyEvent.VK_1:
			case KeyEvent.VK_U:
			case KeyEvent.VK_PAGE_UP:
				IncrementZ(-zMicron);
				consumed = true;
				break;
			case KeyEvent.VK_2:
			case KeyEvent.VK_J:
			case KeyEvent.VK_PAGE_DOWN:
				IncrementZ(zMicron);
				consumed = true;
			}
		}
		if (consumed) {
			dkpe.consume();
		}
	}

	public void IncrementZ(double micron) {
		// Get needed info from core
		String zStage = settings_.getString(SELECTED_Z_DRIVE, core_.getFocusDevice());
		if (zStage == null || zStage.length() == 0)
			return;

		zNavigator_.setPosition(zStage, micron);
	}

	@Subscribe
	public void onActiveChange (MouseMovesStageStateChangeEvent mouseMovesStageStateChangeEvent) {
		if (mouseMovesStageStateChangeEvent.getIsEnabled()) {
			active_ = true;
		} else {
			active_ = false;
		}
	}

}