/*
 * Project: ASI Ring TIRF Control
 * License: BSD 3-clause, see LICENSE.md
 * Author: Brandon Simpson (brandon@asiimaging.com)
 * Copyright (c) 2022, Applied Scientific Instrumentation
 */

package com.asiimaging.tirf.ui.components;

import javax.swing.JComponent;
import javax.swing.JFormattedTextField;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

public class Spinner extends JSpinner {

   private static int defaultSize = 5;

   private Spinner(final Integer start, final Integer min, final Integer max, final Integer step) {
      super(new SpinnerNumberModel(start, min, max, step));
      setColumnSize(defaultSize);
   }

   private Spinner(final Float start, final Float min, final Float max, final Float step) {
      super(new SpinnerNumberModel(start, min, max, step));
      setColumnSize(defaultSize);
   }

   private Spinner(final Double start, final Double min, final Double max, final Double step) {
      super(new SpinnerNumberModel(start, min, max, step));
      setColumnSize(defaultSize);
   }

   public static Spinner createFloatSpinner(
         final Float start,
         final Float min,
         final Float max,
         final Float step) {
      return new Spinner(start, min, max, step);
   }

   public static Spinner createIntegerSpinner(
         final Integer start,
         final Integer min,
         final Integer max,
         final Integer step) {
      return new Spinner(start, min, max, step);
   }

   public static Spinner createDoubleSpinner(
         final Double start,
         final Double min,
         final Double max,
         final Double step) {
      return new Spinner(start, min, max, step);
   }

   public void setColumnSize(final int width) {
      final JComponent editor = getEditor();
      final JFormattedTextField textField = ((JSpinner.NumberEditor) editor).getTextField();
      textField.setColumns(width);
   }

   public void setDefaultSize(final int width) {
      defaultSize = width;
   }

   public int getInt() {
      return (Integer) getValue();
   }

   public float getFloat() {
      return (Float) getValue();
   }

   public double getDouble() {
      return (Double) getValue();
   }

   public void setInt(final int n) {
      setValue(n);
   }

   public void setFloat(final float n) {
      setValue(n);
   }

   public void setDouble(final double n) {
      setValue(n);
   }

   public void registerListener(final Method method) {
      addChangeListener(method::run);
   }
}
