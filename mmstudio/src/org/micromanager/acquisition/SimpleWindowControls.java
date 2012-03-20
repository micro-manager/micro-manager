///////////////////////////////////////////////////////////////////////////////
//FILE:          MetadataPanel.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Henry Pinkard, henry.pinkard@gmail.com, 2012
//
// COPYRIGHT:    University of California, San Francisco, 2012
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
package org.micromanager.acquisition;

import org.micromanager.internalinterfaces.DisplayControls;
import com.swtdesigner.SwingResourceManager;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import org.json.JSONObject;
import org.micromanager.MMStudioMainFrame;
import org.micromanager.api.ImageCache;
import org.micromanager.utils.ReportingUtils;


public class SimpleWindowControls extends DisplayControls {

   private VirtualAcquisitionDisplay virtAcq_;
   private JButton showFolderButton_;
   private JButton snapButton_;
   private JButton liveButton_;
   private JLabel statusLabel_;
   
   
   public SimpleWindowControls(VirtualAcquisitionDisplay virtAcq) {
     virtAcq_ = virtAcq;
      initComponents();
      showFolderButton_.setEnabled(false);
   }
   
   
   private void initComponents() {
      
      setPreferredSize(new java.awt.Dimension(512, 45));
      
      showFolderButton_ = new JButton();
      showFolderButton_.setBackground(new java.awt.Color(255, 255, 255));
      showFolderButton_.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/micromanager/icons/folder.png"))); // NOI18N
      showFolderButton_.setToolTipText("Show containing folder");
      showFolderButton_.setFocusable(false);
      showFolderButton_.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
      showFolderButton_.setMaximumSize(new java.awt.Dimension(30, 28));
      showFolderButton_.setMinimumSize(new java.awt.Dimension(30, 28));
      showFolderButton_.setPreferredSize(new java.awt.Dimension(30, 28));
      showFolderButton_.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
      showFolderButton_.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            showFolderButtonActionPerformed();
         }
      });
      
      JButton saveButton = new JButton();
      saveButton.setBackground(new java.awt.Color(255, 255, 255));
      saveButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/micromanager/icons/disk.png"))); // NOI18N
      saveButton.setToolTipText("Save as...");
      saveButton.setFocusable(false);
      saveButton.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
      saveButton.setMaximumSize(new Dimension(30, 28));
      saveButton.setMinimumSize(new Dimension(30, 28));
      saveButton.setPreferredSize(new Dimension(30, 28));
      saveButton.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
      saveButton.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent evt) {
            saveButtonActionPerformed();
         }
      });
      
      
      snapButton_ = new JButton();
      snapButton_.setFocusable(false);
      snapButton_.setIconTextGap(6);
      snapButton_.setText("Snap");
      snapButton_.setMinimumSize(new Dimension(99,28));
      snapButton_.setPreferredSize(new Dimension(99,28));
      snapButton_.setMaximumSize(new Dimension(99,28));
      snapButton_.setIcon(SwingResourceManager.getIcon(
            MMStudioMainFrame.class, "/org/micromanager/icons/camera.png"));
      snapButton_.setFont(new Font("Arial", Font.PLAIN, 10));
      snapButton_.setToolTipText("Snap single image");
      snapButton_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            MMStudioMainFrame.getInstance().doSnap();
         }
         
      });
      
      liveButton_ = new JButton();
      liveButton_.setIcon(SwingResourceManager.getIcon(
            MMStudioMainFrame.class,
            "/org/micromanager/icons/camera_go.png"));
      liveButton_.setIconTextGap(6);
      liveButton_.setText("Live");
      liveButton_.setMinimumSize(new Dimension(99,28));
      liveButton_.setPreferredSize(new Dimension(99,28));
      liveButton_.setMaximumSize(new Dimension(99,28));
      liveButton_.setFocusable(false);
      liveButton_.setToolTipText("Continuous live view");
      liveButton_.setFont(new Font("Arial", Font.PLAIN, 10));
      liveButton_.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {            
            liveButtonAction();
         }
      });
     


      JButton addToSeriesButton = new JButton("Album");
      addToSeriesButton.setIcon(SwingResourceManager.getIcon(MMStudioMainFrame.class,
              "/org/micromanager/icons/arrow_right.png"));
      addToSeriesButton.setToolTipText("Add current image to album");
      addToSeriesButton.setFocusable(false);
      addToSeriesButton.setMaximumSize(new Dimension(90, 25));
      addToSeriesButton.setMinimumSize(new Dimension(90, 25));
      addToSeriesButton.setPreferredSize(new Dimension(110, 25));
      addToSeriesButton.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent evt) {
            addToSeriesButtonActionPerformed();
         }});

      statusLabel_ = new JLabel("");
      statusLabel_.setFocusable(false);
      statusLabel_.setFont(new java.awt.Font("Lucida Grande", 0, 10));
      
      this.setLayout(new BorderLayout());

      JPanel buttonPanel = new JPanel();
      JPanel textPanel = new JPanel();
      buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.X_AXIS));

      textPanel.setLayout(new BorderLayout());
      this.add(buttonPanel, BorderLayout.CENTER);
      this.add(textPanel,BorderLayout.SOUTH); 
      
      buttonPanel.add(showFolderButton_);
      buttonPanel.add(new JLabel(" "));
      buttonPanel.add(saveButton);
      buttonPanel.add(new JLabel("   "));
      buttonPanel.add(snapButton_);
      buttonPanel.add(new JLabel("   "));
      buttonPanel.add(liveButton_);
      buttonPanel.add(new JLabel("    "));
      buttonPanel.add(addToSeriesButton);
      textPanel.add(statusLabel_, BorderLayout.CENTER);      
   }
   
   
   private void showFolderButtonActionPerformed() {
      virtAcq_.showFolder();
   }
   
    private void addToSeriesButtonActionPerformed() {
      try {
         MMStudioMainFrame gui = MMStudioMainFrame.getInstance();
         ImageCache ic = virtAcq_.getImageCache();
         int channels = ic.getSummaryMetadata().getInt("Channels");
         if (channels == 1) { //RGB or monchrome
            gui.addToAlbum( ic.getImage(0, 0, 0, 0), ic.getDisplayAndComments() );
         } else { //multicamera
            for (int i = 0; i < channels; i++)
               gui.addToAlbum(ic.getImage(i, 0, 0, 0), ic.getDisplayAndComments());
         }                
      } catch (Exception ex) {
         ReportingUtils.logError(ex);
      }
   }
   
    private void liveButtonAction() {
       MMStudioMainFrame.getInstance().enableLiveMode(!MMStudioMainFrame.getInstance().isLiveModeOn());
    }
    
     private void saveButtonActionPerformed() {
        virtAcq_.saveAs();
        showFolderButton_.setEnabled(true);
   }
     
   public void newImageUpdate(JSONObject tags) {
      showFolderButton_.setEnabled(false);
   }
   
   @Override
   public void imagesOnDiskUpdate(boolean onDisk) {

   }

   //called when live mode activated
   @Override
   public void acquiringImagesUpdate(boolean acquiring) {
      snapButton_.setEnabled(!acquiring);
      liveButton_.setIcon(acquiring ? SwingResourceManager.getIcon(MMStudioMainFrame.class,
              "/org/micromanager/icons/cancel.png")
              : SwingResourceManager.getIcon(MMStudioMainFrame.class,
              "/org/micromanager/icons/camera_go.png"));
      liveButton_.setSelected(acquiring);
      liveButton_.setText(acquiring ? "Stop Live" : "Live");

   }

   @Override
   public void setStatusLabel(String text) {
      statusLabel_.setText(text);
   }

   
}

