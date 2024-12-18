package com.asiimaging.plogic.ui.asigui;

import java.awt.Dimension;
import javax.swing.JProgressBar;

public class ProgressBar extends JProgressBar {

   public ProgressBar() {
      super();
   }

   public void setRange(final int min, final int max) {
      setMinimum(min);
      setMaximum(max);
   }

   public void setAbsoluteSize(final int width, final int height) {
      final Dimension size = new Dimension(width, height);
      setPreferredSize(size);
      setMinimumSize(size);
      setMaximumSize(size);
   }

}
