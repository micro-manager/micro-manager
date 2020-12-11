package org.micromanager.internal;

import org.micromanager.PositionList;

import javax.swing.SwingUtilities;

public final class PositionListManager implements org.micromanager.PositionListManager {

   private PositionList posList_;

   public PositionListManager() {
      posList_ = new PositionList();
   }

   /**
    * Makes this the 'current' PositionList, i.e., the one used by the
    * Acquisition Protocol.
    * Replaces the list in the PositionList Window
    * It will open a position list dialog if it was not already open.
    * @param pl PosiionLIst to be made the current one
    */
   public void setPositionList(PositionList pl) { // use serialization to clone the PositionList object
      posList_ = pl; // PositionList.newInstance(pl);
      SwingUtilities.invokeLater(() -> {
         if (posListDlg_ != null) {
            posListDlg_.setPositionList(posList_);
         }
         if (acqEngine_ != null) {
            acqEngine_.setPositionList(posList_);
         }
         if (acqControlWin_ != null) {
            acqControlWin_.updateGUIContents();
         }
      });
   }

   /**
    * Returns a copy of the current PositionList, the one used by the
    * Acquisition Protocol
    * @return copy of the current PositionList
    */
   public PositionList getPositionList()  {
      return posList_;
   }

   /**
    * Opens the XYPositionList when it is not opened.
    * Adds the current position to the list (same as pressing the "Mark" button
    * in the XYPositionList)
    */
   public void markCurrentPosition() {
      if (posListDlg_ == null) {
         showPositionList();
      }
      if (posListDlg_ != null) {
         posListDlg_.markPosition(false);
      }
   }
}
