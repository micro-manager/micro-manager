///////////////////////////////////////////////////////////////////////////////
//FILE:          PlaybackPanel.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nenad Amodaj, nenad@amodaj.com, March 20, 2006
//
// COPYRIGHT:    University of California, San Francisco, 2006
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
//
// CVS:          $Id$

package org.micromanager;

import ij.IJ;

import java.awt.Font;
import java.awt.Panel;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.JToggleButton;

import org.micromanager.image5d.Image5DWindow;

import com.swtdesigner.SwingResourceManager;

/**
 * Add-on panel for the modified Image5D class.
 * Displays basic palyback, contrast and file I/O controls at the bottom of the frame.
 */
public class PlaybackPanel extends Panel {

   private JTextField framesField_;
   private Image5DWindow wnd_;
   private JLabel elapsedLabel_;
   
   /**
    * Create the panel
    */
   public PlaybackPanel(Image5DWindow wnd) {
      super();
      wnd_ = wnd;
      setSize(590, 37);
      setLayout(null);

      final JButton saveAsButton_ = new JButton();
      saveAsButton_.addActionListener(new ActionListener() {
         public void actionPerformed(final ActionEvent e) {
            wnd_.saveAs();
         }
      });
      saveAsButton_.setToolTipText("Save 5D image");
      saveAsButton_.setIcon(SwingResourceManager.getIcon(PlaybackPanel.class, "/org/micromanager/icons/disk.png"));
      saveAsButton_.setBounds(6, 7, 37, 24);
      add(saveAsButton_);

      final JButton abortButton_ = new JButton();
      abortButton_.addActionListener(new ActionListener() {
         public void actionPerformed(final ActionEvent e) {
            wnd_.abortAcquisition();
         }
      });
      abortButton_.setToolTipText("Abort current acquistion");
      abortButton_.setIcon(SwingResourceManager.getIcon(PlaybackPanel.class, "/org/micromanager/icons/cancel.png"));
      abortButton_.setBounds(44, 7, 37, 24);
      add(abortButton_);

      final JLabel framessecLabel = new JLabel();
      framessecLabel.setFont(new Font("Arial", Font.PLAIN, 10));
      framessecLabel.setText("Frames/sec");
      framessecLabel.setBounds(226, 4, 60, 30);
      add(framessecLabel);

      framesField_ = new JTextField();
      framesField_.setFont(new Font("Arial", Font.PLAIN, 10));
      framesField_.setToolTipText("Playback speed in frames per second");
      framesField_.setBounds(288, 8, 46, 22);
      add(framesField_);

      final JToggleButton togglePlayButton_ = new JToggleButton();
      togglePlayButton_.setIcon(SwingResourceManager.getIcon(PlaybackPanel.class, "/org/micromanager/icons/control_play_blue.png"));
      togglePlayButton_.setToolTipText("Play");
      togglePlayButton_.addActionListener(new ActionListener() {
         public void actionPerformed(final ActionEvent e) {
            if (togglePlayButton_.isSelected()){
               wnd_.playBack();
               togglePlayButton_.setIcon(SwingResourceManager.getIcon(PlaybackPanel.class, "/org/micromanager/icons/control_stop_blue.png"));
               togglePlayButton_.setToolTipText("Stop");
            } else {
               wnd_.stopPlayBack();
               togglePlayButton_.setIcon(SwingResourceManager.getIcon(PlaybackPanel.class, "/org/micromanager/icons/control_play_blue.png"));
               togglePlayButton_.setToolTipText("Play");
            }
         }
      });
      togglePlayButton_.setBounds(178, 7, 42, 24);
      add(togglePlayButton_);

      elapsedLabel_ = new JLabel();
      elapsedLabel_.setFont(new Font("Arial", Font.PLAIN, 10));
      elapsedLabel_.setBounds(344, 9, 229, 21);
      add(elapsedLabel_);

      final JButton metaButton_ = new JButton();
      metaButton_.addActionListener(new ActionListener() {
         public void actionPerformed(final ActionEvent e) {
            wnd_.displayMetadata();
         }
      });
      metaButton_.setIcon(SwingResourceManager.getIcon(PlaybackPanel.class, "/org/micromanager/icons/application_view_list.png"));
      metaButton_.setToolTipText("Display metadata");
      metaButton_.setBounds(82, 7, 37, 24);
      add(metaButton_);

      JButton contrastButton_ = new JButton();
      contrastButton_.setIcon(SwingResourceManager.getIcon(PlaybackPanel.class, "/org/micromanager/icons/contrast.png"));
      contrastButton_.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            IJ.runPlugIn("ij.plugin.frame.ContrastAdjuster", "");
            wnd_.getImagePlus().changes = true;
          }
      });
      contrastButton_.setToolTipText("Contrast settings dialog");
      contrastButton_.setBounds(120, 7, 37, 24);
      add(contrastButton_);

      //
   }
   
   public String getIntervalText() {
      return framesField_.getText();
   }
   
   public void setIntervalText(String txt) {
      framesField_.setText(txt);
   }

   public void setImageInfo(String txt) {
      elapsedLabel_.setText(txt);
   }
}
