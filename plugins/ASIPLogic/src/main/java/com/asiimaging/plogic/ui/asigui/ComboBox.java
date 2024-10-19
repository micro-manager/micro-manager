/*
 * Project: ASI PLogic Control
 * License: BSD 3-clause, see LICENSE.md
 * Author: Brandon Simpson (brandon@asiimaging.com)
 * Copyright (c) 2024, Applied Scientific Instrumentation
 */

package com.asiimaging.plogic.ui.asigui;

import java.awt.Dimension;
import java.util.Arrays;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;

public class ComboBox extends JComboBox<String> {

   private static int defaultWidth_ = 100;
   private static int defaultHeight_ = 20;

   private String selected_;
   private String[] labels_;

   public ComboBox(final String[] labels, final String selected) {
      super(labels);
      labels_ = labels;
      selected_ = selected;
      init(defaultWidth_, defaultHeight_);
   }

   public ComboBox(final String[] labels,
                   final String selected,
                   final int width,
                   final int height) {
      super(labels);
      labels_ = labels;
      selected_ = selected;
      init(width, height);
   }

   // used in the constructors
   private void init(final int width, final int height) {
      setAbsoluteSize(width, height);
      setSelectedIndex(getIndex(selected_));
      setFocusable(false); // removes the focus highlight
   }

   /**
    * Sets the default size for all {@code ComboBox} components.
    *
    * @param width the default width
    * @param height the default height
    */
   public static void setDefaultSize(final int width, final int height) {
      defaultWidth_ = width;
      defaultHeight_ = height;
   }

   // used in setSelected
   private int getIndex(final String label) {
      return Arrays.asList(labels_).indexOf(label);
   }

   /**
    * Return the currently selected item.
    * The cached value is updated in the actionListener.
    *
    * @return the currently selected item
    */
   public String getSelected() {
      return selected_;
   }

   /**
    * Set the currently selected item to {@code label}.
    *
    * @param label the {@code ComboBox} item to select.
    */
   public void setSelected(final String label) {
      setSelectedIndex(getIndex(label));
   }

   /**
    * Set the absolute size of the component.
    *
    * @param width the absolute width
    * @param height the absolute height
    */
   public void setAbsoluteSize(final int width, final int height) {
      final Dimension size = new Dimension(width, height);
      setPreferredSize(size);
      setMinimumSize(size);
      setMaximumSize(size);
   }

   /**
    * Updates the {@code ComboBox} with new labels.
    *
    * @param labels the new labels
    */
   public void updateItems(final String[] labels) {
      setModel(new DefaultComboBoxModel<>(labels));
      labels_ = labels;
   }

   /**
    * Add an actionListener to the {@code ComboBox}.
    *
    * @param method the method to run
    */
   public void registerListener(final Method method) {
      addActionListener(event -> {
         selected_ = (String) getSelectedItem();
         method.run(event);
      });
   }

}