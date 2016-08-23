///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     Display implementation
//-----------------------------------------------------------------------------
//
// AUTHOR:       Chris Weisiger, 2015
//
// COPYRIGHT:    University of California, San Francisco, 2015
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

package org.micromanager.display.internal;


import java.awt.Component;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.Hashtable;
import javax.swing.JLabel;
import javax.swing.JPopupMenu;
import javax.swing.JSlider;
import javax.swing.JTextField;
import javax.swing.MenuElement;
import javax.swing.MenuSelectionManager;
import javax.swing.border.TitledBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import org.micromanager.display.DisplaySettings;
import org.micromanager.display.DisplayWindow;

/**
 * This class provides an interface for selecting the FPS at which to display
 * animations for the ScrollerPanel.
 */
public final class FPSPopupMenu extends JPopupMenu {
   /**
    * Maps integer values from the slider to float values for the actual FPS.
    */
   private static final double FPS_RATIO = 2.0;
   /**
    * Implementation adapted from
    * http://www.onjava.com/pub/a/onjava/excerpt/swing_14/index6.html?page=2
    * Note that we have a 2:1 scaling factor applied to the values this slider
    * outputs (e.g. when the slider is at the "20" indicator, calling
    * getValue() will return 40 instead).
    */
   private class FPSSlider extends JSlider implements MenuElement {
      public FPSSlider() {
         super(1, (int) (100 * FPS_RATIO));
         setBorder(new TitledBorder("Animation FPS"));
         setMajorTickSpacing(20);
         // Set up custom labels, because otherwise we end up with ticks at
         // 21, 41, etc.
         Hashtable<Integer, JLabel> labels = new Hashtable<Integer, JLabel>();
         labels.put(1, new JLabel("1"));
         for (int i = 20; i < 101; i += 20) {
            labels.put((int) (i * FPS_RATIO), new JLabel(String.valueOf(i)));
         }
         setLabelTable(labels);
         setPaintLabels(true);
      }
      
      @Override
      public MenuElement[] getSubElements() {
         return new MenuElement[0];
      }

      @Override
      public Component getComponent() {
         return this;
      }

      /**
       * Forward mouse motion events to our slider. Well actually we forward
       * *all* mouse events to our slider, but it seems to work okay.
       */
      @Override
      public void processMouseEvent(MouseEvent e, MenuElement[] path,
            MenuSelectionManager manager) {
         super.processMouseMotionEvent(e);
      }
      @Override
      public void processKeyEvent(KeyEvent e, MenuElement[] path,
            MenuSelectionManager manager) {}
      @Override
      public void menuSelectionChanged(boolean isIncluded) {}
   }

   public FPSPopupMenu(final DisplayWindow display, double initialVal) {
      final FPSSlider slider = new FPSSlider();
      final JTextField field = new JTextField(3);
      slider.setValue((int) (initialVal * FPS_RATIO));
      slider.addChangeListener(new ChangeListener() {
         @Override
         public void stateChanged(ChangeEvent event) {
            double newRate = slider.getValue() / FPS_RATIO;
            field.setText(String.format("%.2f", newRate));
            DisplaySettings settings = display.getDisplaySettings();
            settings = settings.copy().animationFPS(newRate).build();
            display.setDisplaySettings(settings);
         }
      });
      add(slider);

      field.addKeyListener(new KeyAdapter() {
         @Override
         public void keyReleased(KeyEvent event) {
            try {
               double newRate = Double.parseDouble(field.getText());
               slider.setValue((int) (newRate * FPS_RATIO));
               slider.repaint();
               DisplaySettings settings = display.getDisplaySettings();
               settings = settings.copy().animationFPS(newRate).build();
               display.setDisplaySettings(settings);
            }
            catch (NumberFormatException e) {
               // Ignore it
            }
         }
      });
      add(field);
   }
}
