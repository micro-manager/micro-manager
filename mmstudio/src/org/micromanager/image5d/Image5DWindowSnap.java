///////////////////////////////////////////////////////////////////////////////
//FILE:          Image5DWindowSnap.java
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


package org.micromanager.image5d;

import java.awt.Scrollbar;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.prefs.Preferences;

import javax.swing.JButton;

import org.micromanager.PlaybackPanel;
import org.micromanager.acquisition.MMAcquisitionSnap;

import com.swtdesigner.SwingResourceManager;

public class Image5DWindowSnap extends Image5DWindow {
	private static final long serialVersionUID = 6593148168923699921L;
	private MMAcquisitionSnap acq_;
	private Preferences image5dSnapPrefs_;

	public Image5DWindowSnap(Image5D imp, MMAcquisitionSnap acq) {
		super(imp);
		image5dSnapPrefs_ = Preferences.userNodeForPackage(this.getClass());
		rootDir_ = image5dSnapPrefs_.get("rootDir_", "/");

		acq_ = acq;
		addSnapAppendButton();
		addSnapReplaceButton();
		channelControl.scrollbarWL.setVisible(false);

	}

	public void addSnapReplaceButton() {
		JButton snapButton = new JButton();
		snapButton.setToolTipText("Snap a new image and overwrite current image in this sequence");
		snapButton.setIcon(SwingResourceManager.getIcon(PlaybackPanel.class, "/org/micromanager/icons/camera.png"));
		snapButton.setBounds(44, 5, 37, 24);
		this.pb_.add(snapButton);
		snapButton.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				doSnapReplace();
			}
		});
	}

	public void saveAs() {
		super.saveAs();
		image5dSnapPrefs_.put("rootDir_", rootDir_);
	}

	public void addSnapAppendButton() {
		JButton snapButton = new JButton();
		snapButton.setToolTipText("Snap and append a new image to this sequence");
		snapButton.setIcon(SwingResourceManager.getIcon(PlaybackPanel.class, "/org/micromanager/icons/snapAppend.png"));
		snapButton.setBounds(83, 5, 37, 24);
		this.pb_.add(snapButton);
		snapButton.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				doSnapAppend();
			}
		});
	}





	private void doSnapReplace() {
		acq_.doSnapReplace();
	}


	private void doSnapAppend() {
		acq_.doSnapAppend();
	}



	// Override createPlaybackPanel to prevent drawing Pause and Abort buttons:
	public PlaybackPanel createPlaybackPanel() {
		return new PlaybackPanel(this, true);
	}

	// Override addHorizontalScrollbars to use "n" label instead of "t" and hide z scrollbar:
	protected void addHorizontalScrollbars(Image5D imp) {      
		int size;

		// Add slice selector
		ScrollbarWithLabel bar;   

		// Add frame selector
		size = imp.getNFrames();	
		bar = new ScrollbarWithLabel(Scrollbar.HORIZONTAL, 1, 1, 1, size+1, "n");
		Scrollbars[4] = bar.getScrollbar();		
		add(bar, Image5DLayout.FRAME_SELECTOR);
		if (ij!=null) bar.getScrollbar().addKeyListener(ij);
	}
}

