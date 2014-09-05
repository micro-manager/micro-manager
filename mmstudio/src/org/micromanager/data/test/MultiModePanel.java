package org.micromanager.data.test;

import com.google.common.eventbus.EventBus;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import java.util.ArrayList;
import java.util.HashMap;

import javax.swing.JPanel;

import net.miginfocom.swing.MigLayout;

/**
 * This class is a JPanel that displays a set of vertically-oriented buttons
 * on the left, each of which is associated with a JPanel. When the button is
 * clicked, the corresponding panel is shown/hidden to the right.
 */
class MultiModePanel extends JPanel {
   HashMap<JPanel, VerticalButton> panelToButton_;
   ArrayList<JPanel> orderedPanels_;
   JPanel buttonPanel_;
   JPanel modePanel_;
   EventBus bus_;
   public MultiModePanel(EventBus bus) {
      bus_ = bus;
      panelToButton_ = new HashMap<JPanel, VerticalButton>();
      orderedPanels_ = new ArrayList<JPanel>();
      buttonPanel_ = new JPanel(new MigLayout("insets 0, flowy"));
      modePanel_ = new JPanel(new MigLayout("insets 0, flowy"));
      setLayout(new MigLayout("insets 0"));
      add(buttonPanel_, "growy");
      add(modePanel_, "growy");
   }

   public void addMode(String label, JPanel panel) {
      final VerticalButton button = new VerticalButton(label);
      panelToButton_.put(panel, button);
      orderedPanels_.add(panel);
      buttonPanel_.add(button);
      // Add an event to show/hide the appropriate panel.
      button.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent event) {
            redoLayout();
         }
      });
      validate();
   }

   /**
    * Change which panels we are showing.
    */
   private void redoLayout() {
      modePanel_.removeAll();
      for (JPanel panel : orderedPanels_) {
         if (panelToButton_.get(panel).isSelected()) {
            modePanel_.add(panel);
         }
      }
      bus_.post(new LayoutChangedEvent());
   }
}
