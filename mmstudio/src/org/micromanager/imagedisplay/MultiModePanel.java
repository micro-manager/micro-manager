package org.micromanager.imagedisplay;

import com.google.common.eventbus.EventBus;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import java.util.ArrayList;
import java.util.HashMap;

import javax.swing.JPanel;
import javax.swing.JScrollPane;

import net.miginfocom.swing.MigLayout;

import org.micromanager.imagedisplay.events.LayoutChangedEvent;

import org.micromanager.utils.ReportingUtils;

/**
 * This class is a JPanel that displays a set of vertically-oriented buttons
 * on the left, each of which is associated with a Component. When the button
 * is clicked, the corresponding component is shown/hidden to the right.
 */
class MultiModePanel extends JPanel {
   HashMap<Component, VerticalButton> widgetToButton_;
   ArrayList<Component> orderedWidgets_;
   JPanel buttonPanel_;
   JPanel modePanel_;
   EventBus bus_;

   public MultiModePanel(EventBus bus) {
      bus_ = bus;
      widgetToButton_ = new HashMap<Component, VerticalButton>();
      orderedWidgets_ = new ArrayList<Component>();
      // TODO: I can't figure out a way in MiG to specify grid gaps without
      // making assumptions about how many entries are in the layout. Except
      // I *can* specify gaps when adding components, if I use the "wrap"
      // command...which means the button panel technically has a horizontal
      // layout, but every single button added to it is added via "wrap 0px".
      buttonPanel_ = new JPanel(new MigLayout("insets 0"));
      modePanel_ = new JPanel(new MigLayout("insets 0, flowy"));
      setLayout(new MigLayout("insets 0"));
      add(buttonPanel_, "growy");
      JScrollPane scroller = new JScrollPane(modePanel_);
      scroller.setBorder(null);
      add(scroller, "grow");
   }

   public void addMode(String label, Component widget) {
      final VerticalButton button = new VerticalButton(label);
      widgetToButton_.put(widget, button);
      orderedWidgets_.add(widget);
      buttonPanel_.add(button, "wrap 0px");
      // Add an event to show/hide the appropriate widget.
      button.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent event) {
            redoLayout();
         }
      });
      validate();
   }

   /**
    * Change which widgets we are showing.
    */
   private void redoLayout() {
      modePanel_.removeAll();
      for (Component widget : orderedWidgets_) {
         if (widgetToButton_.get(widget).isSelected()) {
            modePanel_.add(widget);
         }
      }
      bus_.post(new LayoutChangedEvent());
   }

   /**
    * HACK: In addition to the usual validation work, we constrain our height
    * to the maximum height of our parent to ensure we don't make the window
    * grow when our contents change.
    */
   @Override
   public void validate() {
      Container parent = getParent();
      if (parent != null) {
         setMaximumSize(new Dimension(32767,
                  parent.getSize().height - 50));
      }
      super.validate();
   }
}
