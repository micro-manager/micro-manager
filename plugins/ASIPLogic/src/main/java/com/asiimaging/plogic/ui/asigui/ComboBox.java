/*
 * Project: ASI PLogic Control
 * License: BSD 3-clause, see LICENSE.md
 * Author: Brandon Simpson (brandon@asiimaging.com)
 * Copyright (c) 2024, Applied Scientific Instrumentation
 */

package com.asiimaging.plogic.ui.asigui;

import java.awt.Dimension;
import java.util.Arrays;
import javax.swing.JComboBox;

public class ComboBox extends JComboBox<String> {

   private static int defaultWidth = 100;
   private static int defaultHeight = 20;

   private String selected;
   private final String[] labels;

   public ComboBox(final String[] labels, final String selected) {
      super(labels);
      this.labels = labels;
      this.selected = selected;
      init(defaultWidth, defaultHeight);
   }

   public ComboBox(final String[] labels,
                   final String selected,
                   final int width,
                   final int height) {
      super(labels);
      this.labels = labels;
      this.selected = selected;
      init(width, height);
   }

   private void init(final int width, final int height) {
      setAbsoluteSize(width, height);
      setSelectedIndex(getIndex(selected));
      setFocusable(false); // removes the focus highlight
   }

   public static void setDefaultSize(final int width, final int height) {
      defaultWidth = width;
      defaultHeight = height;
   }

   private int getIndex(final String label) {
      return Arrays.asList(labels).indexOf(label);
   }

   public String getSelected() {
      return selected;
   }

   public void setSelected(final String label) {
      setSelectedIndex(getIndex(label));
   }

   public void setAbsoluteSize(final int width, final int height) {
      final Dimension size = new Dimension(width, height);
      setPreferredSize(size);
      setMinimumSize(size);
      setMaximumSize(size);
   }

   public void registerListener(final Method method) {
      addActionListener(event -> {
         selected = (String)getSelectedItem();
         method.run(event);
      });
   }

}