///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//AUTHOR:        Nick Anthony 2020
//LICENSE:       This file is distributed under the BSD license.
//               License text is included with the source distribution.
//               This file is distributed in the hope that it will be useful,
//               but WITHOUT ANY WARRANTY; without even the implied warranty
//               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//CVS:          $Id$
//

package org.micromanager.internal.positionlist;

import com.google.common.eventbus.Subscribe;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import org.micromanager.PositionList;
import org.micromanager.Studio;
import org.micromanager.events.NewPositionListEvent;
import org.micromanager.events.internal.InternalShutdownCommencingEvent;

/**
 * The MMPositionListDlg class extends PositionListDlg to be used as the singleton
 * PositionListDlg used in the MMStudio API. In addition to the normal behavior of
 * a PositionListDlg, this object will:
 * 1: Update an AcqControlDlg window each time the position list is modified.
 * 2: Post a DefaultNewPositionListEvent to the MMStudio EventManager each time
 * the position list is modified.
 * 3: Save preferences to the MMStudio UserProfile.
 */
public final class MMPositionListDlg extends PositionListDlg {

   /**
    * PositionList Dialog constructor.
    *
    * @param studio The always present Studio object
    * @param posList Position List to be displayed
    */
   public MMPositionListDlg(Studio studio, PositionList posList) {
      super(studio, posList);

      addWindowListener(new WindowAdapter() {
         @Override
         public void windowClosing(WindowEvent arg0) {
            saveDims();
         }
      });
   }

   @Override
   protected void updatePositionData() {
      studio_.positions().setPositionList(getPositionList());
      super.updatePositionData();
   }

   private void saveDims() {
      int posCol0Width = posTable_.getColumnModel().getColumn(0).getWidth();
      studio_.profile().getSettings(PositionListDlg.class).putInteger(POS_COL0_WIDTH,
            posCol0Width);
      int axisCol0Width = axisTable_.getColumnModel().getColumn(0).getWidth();
      studio_.profile().getSettings(PositionListDlg.class).putInteger(AXIS_COL0_WIDTH,
            axisCol0Width);
   }

   /**
    * Handles event signalling that the application starts shut down.
    *
    * @param event The event that signals shut down is starting
    */
   @Subscribe
   public void onShutdownCommencing(InternalShutdownCommencingEvent event) {
      if (!event.isCanceled()) {
         saveDims();
         dispose();
      }
   }

   @Subscribe
   public void onNewPositionList(NewPositionListEvent nple) {
      PositionList pl = nple.getPositionList();
      if (this.getPositionList() != pl) { // Without this check we will enter an infinite loop.
         this.setPositionList(nple.getPositionList());
      }
   }
}