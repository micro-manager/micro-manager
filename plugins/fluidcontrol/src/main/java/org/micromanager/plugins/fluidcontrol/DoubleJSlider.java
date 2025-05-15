package org.micromanager.plugins.fluidcontrol;

import java.util.Hashtable;
import javax.swing.JLabel;
import javax.swing.JSlider;

/**
 * Difference between min and max is the number of steps (recommended to set min
 * to zero), scale is the actual value at the maximum of the slider. *
 */
class DoubleJSlider extends JSlider {

   final int nSteps;
   final double min;
   final double max;
   final double div;
   Hashtable<Integer, JLabel> labelTable;

   public DoubleJSlider(double min, double max, double value, int nSteps) {
      super(0, nSteps, (int) ((value - min) * nSteps / (max - min)));
      this.div = (max - min) / nSteps;
      this.min = min;
      this.max = max;
      this.nSteps = nSteps;

      labelTable = new Hashtable<Integer, JLabel>();
      for (int i = 0; i < 11; i++) {
         labelTable.put(
               i * nSteps / 10,
               new JLabel(String.valueOf(Math.round(i * div * nSteps / 10 + min)))
         );
      }
      this.setLabelTable(labelTable);
   }

   public double getScaledValue() {
      return ((double) this.getValue()) * div + min;
   }

   public void setScaledValue(double value) {
      this.setValue((int) ((value - min) / div));
   }
}