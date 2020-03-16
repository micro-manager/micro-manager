/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.ndviewer.internal.gui;

import org.micromanager.ndviewer.internal.gui.TextIcon;
import org.micromanager.ndviewer.internal.gui.SubImageControls;
import org.micromanager.ndviewer.internal.gui.RotatedIcon;
import org.micromanager.ndviewer.internal.gui.AxisScroller;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Image;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.util.HashMap;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import org.json.JSONObject;
import org.micromanager.ndviewer.main.NDViewer;
import org.micromanager.ndviewer.overlay.Overlay;
import org.micromanager.ndviewer.api.CanvasMouseListenerInterface;
import org.micromanager.ndviewer.api.ControlsPanelInterface;

/**
 *
 * @author henrypinkard
 */
public class DisplayWindow implements WindowListener {

   //from other window
   private static final double ZOOM_FACTOR_KEYS = 2.0;

   private ViewerCanvas imageCanvas_;
   private SubImageControls subImageControls_;
   private DisplayWindowControls sideControls_;
   private JButton collapseExpandButton_;
   private JPanel leftPanel_, rightPanel_;

   private NDViewer display_;
   JFrame window_;
   private CanvasMouseListenerInterface listener_;

   public DisplayWindow(NDViewer display) {
      window_ = new JFrame();

      display_ = display;
      window_.setSize(1500, 800);
      window_.setVisible(true);
      window_.addWindowListener(this);
      buildInitialUI();
      setupMouseListeners();
      setupKeyListeners();
   }

   public void onDisplayClose() {
      removeKeyListenersRecursively(window_); //remove added key listeners

      //For some reason these two lines appear to be essential for preventing memory leaks after closing the display
      for (Component c : leftPanel_.getComponents()) {
         leftPanel_.remove(c);
      }
      for (Component c : rightPanel_.getComponents()) {
         rightPanel_.remove(c);
      }
      for (FocusListener l : window_.getFocusListeners()) {
         window_.removeFocusListener(l);
      }
      
      listener_ = null;

      subImageControls_.onDisplayClose();
      sideControls_.onDisplayClose();

      window_.removeWindowListener(this);
      display_ = null;
      collapseExpandButton_ = null;
      imageCanvas_ = null;
      subImageControls_ = null;
      sideControls_ = null;
      window_.dispose();
      window_.repaint();
      window_ = null;
      System.gc();

   }
   
   public void onScollPositionChanged(AxisScroller scroller, int value) {
      subImageControls_.onScollPositionChanged( scroller,  value);
   }

   public void onScrollersAdded() {
      subImageControls_.onScrollersAdded();

      //New scrollbars have been made visible
      window_.revalidate();
   }
   
   public void onCanvasResized(int w, int h) {
      imageCanvas_.onCanvasResize(w, h);
   }

   public void setTitle(String title) {
      window_.setTitle(title);
   }

   private void buildInitialUI() {
      window_.setLayout(new BorderLayout());

      imageCanvas_ = new ViewerCanvas(display_);
      subImageControls_ = new SubImageControls(display_);

      leftPanel_ = new JPanel(new BorderLayout());
      leftPanel_.add(imageCanvas_.getCanvas(), BorderLayout.CENTER);
      leftPanel_.add(subImageControls_, BorderLayout.PAGE_END);
      window_.add(leftPanel_, BorderLayout.CENTER);

      sideControls_ = new DisplayWindowControls(display_, null);
      JPanel buttonPanel = new FixedWidthJPanel();
      collapseExpandButton_ = new JButton();

      TextIcon t1 = new TextIcon(collapseExpandButton_, "Hide controls", TextIcon.Layout.HORIZONTAL);
      t1.setFont(t1.getFont().deriveFont(15.0f));
      RotatedIcon r1 = new RotatedIcon(t1, RotatedIcon.Rotate.DOWN);
      collapseExpandButton_.setIcon(r1);
      collapseExpandButton_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            boolean controlsExpanded = !sideControls_.isVisible();
//            "\u25c4" : "\u25ba" //left and right arrow
            t1.setText(controlsExpanded ? "Hide controls" : "Show controls");
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

   public void addContrastControls( String channelName) {
      sideControls_.addContrastControls( channelName);
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
   public void displayImage(Image image, HashMap<String, int[]> hists, HashMap<String, Integer> mins, HashMap<String, Integer> maxs, DataViewCoords view) {
      //Make scrollbars reflect image
      subImageControls_.updateScrollerPositions(view);
      imageCanvas_.updateDisplayImage(image, view.getMagnificationFromResLevel());
      sideControls_.updateHistogramData(hists, mins, maxs);
   }

   public void displayOverlay(Overlay overlay) {
      imageCanvas_.updateOverlay(overlay);
      imageCanvas_.getCanvas().repaint();
   }

   public void repaintCanvas() {
      imageCanvas_.getCanvas().repaint();
   }

   public void setImageMetadata(JSONObject imageMD) {
      sideControls_.setImageMetadata(imageMD);
   }

   public void expandDisplayedRangeToInclude(List<HashMap<String, Integer>> newIamgeEvents,
           List<String> channels) {
      subImageControls_.expandDisplayedRangeToInclude(newIamgeEvents, channels);
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

   private void setupKeyListeners() {
      window_.addFocusListener(new FocusListener() {
         @Override
         public void focusGained(FocusEvent e) {
            imageCanvas_.getCanvas().requestFocus(); //give focus to canvas so keylistener active
         }

         @Override
         public void focusLost(FocusEvent e) {
         }
      });
      KeyListener kl = new KeyListener() {

         @Override
         public void keyTyped(KeyEvent ke) {
            if (ke.getKeyChar() == '=') {
               display_.zoom(1 / ZOOM_FACTOR_KEYS, null);
            } else if (ke.getKeyChar() == '-') {
               display_.zoom(ZOOM_FACTOR_KEYS, null);
            }
         }

         @Override
         public void keyPressed(KeyEvent ke) {
         }

         @Override
         public void keyReleased(KeyEvent ke) {
         }
      };

      //add keylistener to window and all subscomponenets so it will fire whenever
      //focus in anywhere in the window
      window_.addKeyListener(kl);
      addRecursively(window_, kl);

//      recursiveRemoveFocus(window_);
   }

   private void addRecursively(Component c, KeyListener kl) {
      c.addKeyListener(kl);
      if (c instanceof Container) {
         for (Component subC : ((Container) c).getComponents()) {
            addRecursively(subC, kl);
         }
      }
   }

   private static void removeKeyListenersRecursively(Component c) {
      for (KeyListener kl : c.getKeyListeners()) {
         c.removeKeyListener(kl);
      }
      if (c instanceof Container) {
         for (Component subC : ((Container) c).getComponents()) {
            removeKeyListenersRecursively(subC);
         }
      }
   }

   private void setupMouseListeners() {
      listener_ = new CanvasMouseListener(display_);
      imageCanvas_.getCanvas().addMouseWheelListener(listener_);
      imageCanvas_.getCanvas().addMouseMotionListener(listener_);
      imageCanvas_.getCanvas().addMouseListener(listener_);
   }

   public ViewerCanvas getCanvas() {
      return imageCanvas_;
   }

   public void superlockAllScrollers() {
      subImageControls_.superLockAllScroller();
   }

   public void unlockAllScrollers() {
      subImageControls_.unlockAllScrollers();
   }

   public boolean isScrollerAxisLocked(String axis) {
      return subImageControls_.isScrollerLocked(axis);
   }

   void repaintSideControls() {
      sideControls_.repaint();
   }

   public void displaySettingsChanged() {
      sideControls_.displaySettingsChanged();
   }

   public void setCustomCanvasMouseListener(CanvasMouseListenerInterface m) {
      //out with the old
      imageCanvas_.getCanvas().removeMouseListener(listener_);
      imageCanvas_.getCanvas().removeMouseMotionListener(listener_);
      imageCanvas_.getCanvas().removeMouseWheelListener(listener_);
      //in with the new
      imageCanvas_.getCanvas().addMouseWheelListener(m);
      imageCanvas_.getCanvas().addMouseMotionListener(m);
      imageCanvas_.getCanvas().addMouseListener(m);
   }

   public void addControlPanel(ControlsPanelInterface panel) {
      sideControls_.addControlPanel(panel) ;
   }

}
