///////////////////////////////////////////////////////////////////////////////
//FILE:          SliderPanel.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------

//AUTHOR:       Nenad Amodaj, nenad@amodaj.com, Dec 20, 2007

//COPYRIGHT:    University of California, San Francisco, 2007

//LICENSE:      This file is distributed under the BSD license.
//              License text is included with the source distribution.

//              This file is distributed in the hope that it will be useful,
//              but WITHOUT ANY WARRANTY; without even the implied warranty
//              of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.

//              IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//              CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//              INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.

//CVS:          $Id: ConfigGroupPad.java 747 2008-01-04 01:08:50Z nenad $

package mmcloneclasses.utils;


import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.text.ParseException;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JTextField;
import javax.swing.SpringLayout;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import misc.Log;
import org.micromanager.utils.JavaUtils;
import org.micromanager.utils.NumberUtils;


public class SliderPanel extends JPanel {
   private static final long serialVersionUID = -6039226355990936685L;
   private SpringLayout springLayout_;
   private JTextField textField_;
   private double lowerLimit_ = 0.0;
   private double upperLimit_ = 10.0;
   private final int STEPS = 1000;
   private double factor_ = 1.0;
   private boolean integer_ = false;
   private ChangeListener sliderChangeListener_;

   private JScrollBar slider_;
   
   /**
    * Create the panel
    */
   public SliderPanel() {
      super();
      springLayout_ = new SpringLayout();
      setLayout(springLayout_);
      setPreferredSize(new Dimension(304, 18));

      textField_ = new JTextField();
      textField_.setFont(new Font("", Font.PLAIN, 10));
      textField_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(final ActionEvent arg0) {
            onEditChange();
         }
      });
      add(textField_);
      springLayout_.putConstraint(SpringLayout.EAST, textField_, 40, SpringLayout.WEST, this);
      springLayout_.putConstraint(SpringLayout.WEST, textField_, 0, SpringLayout.WEST, this);
      springLayout_.putConstraint(SpringLayout.SOUTH, textField_, 0, SpringLayout.SOUTH, this);
      springLayout_.putConstraint(SpringLayout.NORTH, textField_, 0, SpringLayout.NORTH, this);

      sliderChangeListener_ = new ChangeListener() {
         @Override
         public void stateChanged(final ChangeEvent arg0) {
            onSliderMove();
         }
      };

      slider_ = new JScrollBar(JScrollBar.HORIZONTAL);
      slider_.getModel().addChangeListener(sliderChangeListener_);
      add(slider_);
      springLayout_.putConstraint(SpringLayout.EAST, slider_, 0, SpringLayout.EAST, this);
      springLayout_.putConstraint(SpringLayout.WEST, slider_, 41, SpringLayout.WEST, this);
      springLayout_.putConstraint(SpringLayout.SOUTH, slider_, 0, SpringLayout.SOUTH, this);
      springLayout_.putConstraint(SpringLayout.NORTH, slider_, 0, SpringLayout.NORTH, this);
   }


   public String getText() {
      try {
         // implicitly enforce limits
         setText(textField_.getText());
      } catch (ParseException ex) {
            Log.log(
                    "Asked to convert an empty variable into a number in SliderPanel.java.  " 
                    + "This indicates a faulty Device Adapter.",true);
      }
      return textField_.getText();
   }

   public void enableEdit(boolean state) {
      textField_.setEditable(state);
   }

   public void setLimits(double lowerLimit, double upperLimit) {
      integer_ = false;
      factor_ = (upperLimit - lowerLimit) / (STEPS);
      upperLimit_ = upperLimit;
      lowerLimit_ = lowerLimit;
      slider_.setMinimum(0);
      slider_.setMaximum(STEPS);
      slider_.setVisibleAmount(0);

   }

   public void setLimits(int lowerLimit, int upperLimit) {
      integer_ = true;
      upperLimit_ = upperLimit;
      lowerLimit_ = lowerLimit;
      slider_.setMinimum(0);
      factor_ = 1.0;
      int maximum = upperLimit - lowerLimit + 1;
      if (upperLimit > STEPS) {
           factor_ = ((double) upperLimit - (double) lowerLimit) / (double) (STEPS);
           maximum = STEPS + 1;
      }
      slider_.setMaximum(maximum);
      slider_.setVisibleAmount(1);
   }


   private void setSliderValue(double val) {
      slider_.setValue((int)Math.round((val - lowerLimit_) / factor_));
   }

   private void onSliderMove() {
      double value = slider_.getValue() * factor_ + lowerLimit_;

      if (integer_) {
         textField_.setText(NumberUtils.intToDisplayString((int)(value)));
      } else { 
         textField_.setText(NumberUtils.doubleToDisplayString(value));
      }   
   }


   protected void onEditChange() {
      double val;
      try {
         val = enforceLimits(NumberUtils.displayStringToDouble(textField_.getText()));
         slider_.getModel().removeChangeListener(sliderChangeListener_);
         setSliderValue(val);
         slider_.getModel().addChangeListener(sliderChangeListener_);
      } catch (ParseException p) {
         handleException (p);
      }
   }

   public void setText(String txt) throws ParseException {
      double val;
      if (integer_) {
         val = enforceLimits(NumberUtils.displayStringToInt(txt));
         textField_.setText(NumberUtils.intToDisplayString((int) val));
      } else {
         val = enforceLimits(NumberUtils.displayStringToDouble(txt));
         textField_.setText(NumberUtils.doubleToDisplayString(val));
      }
      slider_.getModel().removeChangeListener(sliderChangeListener_);
      setSliderValue(val);
      slider_.getModel().addChangeListener(sliderChangeListener_);


   }

   private double enforceLimits(double value) {
      double val = value;
      if (val < lowerLimit_)
         val = lowerLimit_;
      if (val > upperLimit_)
         val = upperLimit_;
      return val;
   }

   private int enforceLimits(int value) {
      int val = value;
      if (val < lowerLimit_)
         val = (int)(lowerLimit_);
      if (val > upperLimit_)
         val = (int)(upperLimit_);
      return val;
   }

   public void addEditActionListener(ActionListener al) {
      textField_.addActionListener(al);
   }

   public void addSliderMouseListener(MouseAdapter md) {
      slider_.addMouseListener(md);
      // The following code adds listeners to the arrow buttons
      // It is not needed on Mac, not sure about other platforms, so stick to Windows
      if (JavaUtils.isWindows()) {
         Component comp = (Component) slider_;
         if (comp instanceof Container) {
            Container cm = (Container) comp;
            for (int i = 0; i < cm.getComponentCount(); i++) {
               Component cont = cm.getComponent(i);
               if (cont instanceof JButton) {
                  cont.addMouseListener(md);
               }
            }
         }
      }
   }

   @Override
   public void setBackground(Color bg) {
      super.setBackground(bg);
      if (slider_ != null)
         slider_.setBackground(bg);
      if (textField_ != null)
         textField_.setBackground(bg);
   }

   @Override
   public void setEnabled(boolean enabled) {
      textField_.setEnabled(enabled);
      slider_.setEnabled(enabled);
   }

   private void handleException (Exception e) {
      Log.log(e);
   }

}
