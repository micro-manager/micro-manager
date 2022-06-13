package org.micromanager.internal;

import org.micromanager.PositionList;
import org.micromanager.PositionListManager;
import org.micromanager.Studio;
import org.micromanager.events.internal.DefaultNewPositionListEvent;


/**
 * Default implementation of the PositionListManager.
 */
public final class DefaultPositionListManager implements PositionListManager {

   private PositionList posList_;
   private final Studio studio_;

   public DefaultPositionListManager(Studio studio) {
      studio_ = studio;
      posList_ = new PositionList();
   }

   /**
    * Makes this the 'current' PositionList, i.e., the one used by the
    * Acquisition Protocol.
    * Replaces the list in the PositionList Window
    * It will open a position list dialog if it was not already open.
    *
    * @param pl PosiionLIst to be made the current one
    */
   @Override
   public void setPositionList(PositionList pl) {
      // use serialization to clone the PositionList object
      posList_ = pl;
      studio_.events().post(new DefaultNewPositionListEvent(posList_));
   }

   /**
    * Returns a copy of the current PositionList, the one used by the
    * Acquisition Protocol.
    *
    * @return copy of the current PositionList
    */
   @Override
   public PositionList getPositionList() {
      return PositionList.newInstance(posList_);
   }

   /**
    * Adds the current position to the list (same as pressing the "Mark" button
    * in the XYPositionList with no position selected).
    */
   @Override
   public void markCurrentPosition() {
      MMStudio mm = (MMStudio) studio_;
      mm.uiManager().markCurrentPosition();
   }
}
