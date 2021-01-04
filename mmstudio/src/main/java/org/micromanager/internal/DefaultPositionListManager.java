package org.micromanager.internal;

import org.micromanager.PositionList;
import org.micromanager.PositionListManager;
import org.micromanager.Studio;
import org.micromanager.events.internal.DefaultNewPositionListEvent;


public final class DefaultPositionListManager implements PositionListManager {

   private PositionList posList_;
   private Studio studio_;

   public DefaultPositionListManager(Studio studio) {
      studio_ = studio;
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
      studio_.events().post(new DefaultNewPositionListEvent(posList_));
   }

   /**
    * Returns a copy of the current PositionList, the one used by the
    * Acquisition Protocol
    * @return copy of the current PositionList
    */
   public PositionList getPositionList()  {
      return PositionList.newInstance(posList_);
   }

   /**
    * Adds the current position to the list (same as pressing the "Mark" button
    * in the XYPositionList with no position selected)
    */
   public void markCurrentPosition() {
      MMStudio mm = (MMStudio) studio_;
      mm.uiManager().markCurrentPosition();
   }
}
