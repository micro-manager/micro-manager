/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.magellan.imagedisplaynew;

import org.micromanager.magellan.imagedisplaynew.events.MagellanNewImageEvent;
import org.micromanager.magellan.imagedisplaynew.events.ScrollersAddedEvent;
import com.google.common.eventbus.Subscribe;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentListener;
import java.awt.event.KeyListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.awt.image.BufferedImage;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeListener;
import org.micromanager.magellan.imagedisplaynew.events.DisplayClosingEvent;

/**
 *
 * @author henrypinkard
 */
class DisplayWindowNew implements WindowListener {

   private MagellanCanvas imageCanvas_;
   private NewSubImageControls subImageControls_;
   private DisplayWindowControlsNew sideControls_;
   private JButton collapseExpandButton_;
   private JPanel leftPanel_, rightPanel_;

   private MagellanDisplayController display_;
   JFrame window_;

   public DisplayWindowNew(MagellanDisplayController display) {
      window_ = new JFrame();

      display_ = display;
      display_.registerForEvents(this);
      window_.setSize(1500, 700);
      window_.setVisible(true);
      window_.addWindowListener(this);
      buildInitialUI();
   }

   @Subscribe
   public void onDisplayClose(DisplayClosingEvent e) {
      //For some reason these two lines appear to be essential for preventing memory leaks after closing the display
      for (Component c : leftPanel_.getComponents()) {
         leftPanel_.remove(c);
      }
      for (Component c : rightPanel_.getComponents()) {
         rightPanel_.remove(c);
      }
      
      window_.removeWindowListener(this);
      display_.unregisterForEvents(this);
      display_ = null;
      collapseExpandButton_ = null;
      imageCanvas_ = null;
      subImageControls_ = null;
      sideControls_ = null;
      SwingUtilities.invokeLater(new Runnable() {
         @Override
         public void run() {
            window_.dispose();
            window_ = null;
         }
      });
   }

   @Subscribe
   public void onScrollersAdded(final ScrollersAddedEvent e
   ) {
      //New scrollbars have been made visible
      window_.revalidate();
   }

   public void updateExploreZControls(int zIndex) {
      subImageControls_.updateExploreZControls(zIndex);
   }

   private void buildInitialUI() {
      window_.setLayout(new BorderLayout());


      imageCanvas_ = new MagellanCanvas(display_);
      subImageControls_ = new NewSubImageControls(display_, display_.getZStep(), display_.isExploreAcquisiton());
      //TODO add channels for explore acquisitions

      leftPanel_ = new JPanel(new BorderLayout());
      leftPanel_.add(imageCanvas_.getCanvas(), BorderLayout.CENTER);
      leftPanel_.add(subImageControls_, BorderLayout.PAGE_END);
      window_.add(leftPanel_, BorderLayout.CENTER);


      DisplayWindowControlsNew sideControls = new DisplayWindowControlsNew(display_, null);
      sideControls_ = sideControls;
      JPanel buttonPanel = new FixedWidthJPanel();
      collapseExpandButton_ = new JButton();

//      TextIcon t1 = new TextIcon(collapseExpandButton_, "Hide controls", TextIcon.Layout.HORIZONTAL);
//      t1.setFont(t1.getFont().deriveFont(15.0f));

//      RotatedIcon r1 = new RotatedIcon(t1, RotatedIcon.Rotate.DOWN);
//      collapseExpandButton_.setIcon(r1);

      collapseExpandButton_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            boolean controlsExpanded = !sideControls.isVisible();
//            "\u25c4" : "\u25ba" //left and right arrow
//            t1.setText(controlsExpanded ? "Hide controls" : "Show controls");
            collapseOrExpandSideControls(controlsExpanded);
         }
      });
      buttonPanel.add(collapseExpandButton_, BorderLayout.CENTER);
      rightPanel_ = new JPanel(new BorderLayout());
      rightPanel_.add(buttonPanel, BorderLayout.CENTER);
      rightPanel_.add(sideControls_, BorderLayout.LINE_END);
      window_.add(rightPanel_, BorderLayout.LINE_END);
      
      window_.revalidate();

   }

   public void applyDisplaySettings(org.micromanager.display.DisplaySettings adjustedSettings) {

   }

   public void collapseOrExpandSideControls(boolean expand) {
      sideControls_.setVisible(expand);
      window_.revalidate();
   }

   /**
    * CAlled on EDT. Update image and make sure scrollers are in right positions
    *
    * @param images
    */
   void displayImage(BufferedImage image, MagellanDataViewCoords view) {
      //Make scrollbars reflect image
      subImageControls_.updateScrollerPositions(view);
      imageCanvas_.updateDisplayImage(image);

      imageCanvas_.getCanvas().repaint();
   }

   public void expandDisplayedRangeToInclude(List<MagellanNewImageEvent> newIamgeEvents) {
      subImageControls_.expandDisplayedRangeToInclude(newIamgeEvents);
   }

   @Override
   public void windowOpened(WindowEvent e) {
   }

   @Override
   public void windowIconified(WindowEvent e) {
   }

   @Override
   public void windowDeiconified(WindowEvent e) {
   }

   @Override
   public void windowActivated(WindowEvent e) {
   }

   @Override
   public void windowDeactivated(WindowEvent e) {
   }

   @Override
   //Invoked when the user attempts to close the window from the window's system menu.
   public void windowClosing(WindowEvent e) {
      display_.requestToClose();
   }

   @Override
   public void windowClosed(WindowEvent e) {
   }

}
