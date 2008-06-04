package org.micromanager.api;

import mmcorej.CMMCore;

public interface Autofocus {
   public void showOptionsDialog();
   public void setMMCore(CMMCore core);
   public double fullFocus();
   public double incrementalFocus();
   public String getVerboseStatus();
}
