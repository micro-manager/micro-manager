/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.multiresviewer;

import com.google.common.eventbus.Subscribe;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Image;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.json.JSONObject;
import org.micromanager.multiresviewer.events.DisplayClosingEvent;
import org.micromanager.multiresviewer.events.MagellanScrollbarPosition;
import org.micromanager.multiresviewer.events.ScrollersAddedEvent;
import org.micromanager.multiresviewer.overlay.Overlay;

/**
 *
 * @author henrypinkard
 */
class DisplayWindow implements WindowListener {

   //from other window
   public static final int NONE = 0, EXPLORE = 1, SURFACE_AND_GRID = 2;
   private static final double ZOOM_FACTOR_KEYS = 2.0;



   private MagellanCanvas imageCanvas_;
   private SubImageControls subImageControls_;
   private DisplayWindowControls sideControls_;
   private JButton collapseExpandButton_;
   private JPanel leftPanel_, rightPanel_;

   private MagellanDisplayController display_;
   JFrame window_;

   public DisplayWindow(MagellanDisplayController display) {
      window_ = new JFrame();

      display_ = display;
      display_.registerForEvents(this);
      window_.setSize(1500, 800);
      window_.setVisible(true);
      window_.addWindowListener(this);
      buildInitialUI();
      setupMouseListeners();
      setupKeyListeners();
   }

   @Subscribe
   public void onDisplayClose(DisplayClosingEvent e) {
      removeKeyListenersRecursively(window_); //remove added key listeners
      System.out.println("\n");

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

      subImageControls_.onDisplayClose();
      sideControls_.onDisplayClose();

      window_.removeWindowListener(this);
      display_.unregisterForEvents(this);
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

   @Subscribe
   public void onScrollersAdded(final ScrollersAddedEvent e
   ) {
      //New scrollbars have been made visible
      window_.revalidate();
   }

   public void setTitle(String title) {
      window_.setTitle(title);
   }

   private void buildInitialUI() {
      window_.setLayout(new BorderLayout());

      imageCanvas_ = new MagellanCanvas(display_);
      subImageControls_ = new SubImageControls(display_);

      leftPanel_ = new JPanel(new BorderLayout());
      leftPanel_.add(imageCanvas_.getCanvas(), BorderLayout.CENTER);
      leftPanel_.add(subImageControls_, BorderLayout.PAGE_END);
      window_.add(leftPanel_, BorderLayout.CENTER);

      //TODO: add in plugin panels
      DisplayWindowControls sideControls = new DisplayWindowControls(display_, null);
      sideControls_ = sideControls;
      JPanel buttonPanel = new FixedWidthJPanel();
      collapseExpandButton_ = new JButton();

      TextIcon t1 = new TextIcon(collapseExpandButton_, "Hide controls", TextIcon.Layout.HORIZONTAL);
      t1.setFont(t1.getFont().deriveFont(15.0f));
      RotatedIcon r1 = new RotatedIcon(t1, RotatedIcon.Rotate.DOWN);
      collapseExpandButton_.setIcon(r1);
      collapseExpandButton_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            boolean controlsExpanded = !sideControls.isVisible();
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

   public void addContrastControls(int channelIndex, String channelName) {
      sideControls_.addContrastControls(channelIndex, channelName);
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
   void displayImage(Image image, HashMap<Integer, int[]> hists, HashMap<Integer, Integer> mins, HashMap<Integer, Integer> maxs, DataViewCoords view) {
      //Make scrollbars reflect image
      subImageControls_.updateScrollerPositions(view);
      imageCanvas_.updateDisplayImage(image, view.getDisplayScaleFactor());
      sideControls_.updateHistogramData(hists, mins, maxs);
   }

   void displayOverlay(Overlay overlay) {
      imageCanvas_.updateOverlay(overlay);
      imageCanvas_.getCanvas().repaint();
   }

   public void onNewImage() {
      sideControls_.onNewImage();
   }

   void setImageMetadata(JSONObject imageMD) {
      sideControls_.setImageMetadata(imageMD);
   }

   public void expandDisplayedRangeToInclude(List<MagellanScrollbarPosition> newIamgeEvents) {
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
      CanvasMouseListener listener = new CanvasMouseListener(display_);
      imageCanvas_.getCanvas().addMouseWheelListener(listener);
      imageCanvas_.getCanvas().addMouseMotionListener(listener);
      imageCanvas_.getCanvas().addMouseListener(listener);
   }

   MagellanCanvas getCanvas() {
      return imageCanvas_;
   }

   void superlockAllScrollers() {
      subImageControls_.superLockAllScroller();
   }

   void unlockAllScrollers() {
      subImageControls_.unlockAllScrollers();
   }

   boolean isScrollerAxisLocked(String axis) {
      return subImageControls_.isScrollerLocked(axis);
   }

   void repaintSideControls() {
      sideControls_.repaint();
   }

   void displaySettingsChanged() {
      sideControls_.displaySettingsChanged();
   }

}
