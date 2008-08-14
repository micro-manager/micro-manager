package org.micromanager.api;

import mmcorej.CMMCore;

public interface Autofocus {
   public void showOptionsDialog();
   public void setMMCore(CMMCore core);
   public double fullFocus();
   public double incrementalFocus();
   public String getVerboseStatus();
   public void focus(double coarseStep, int numCoarse, double fineStep, int numFine);
}
