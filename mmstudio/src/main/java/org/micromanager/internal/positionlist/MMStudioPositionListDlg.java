/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.internal.positionlist;

import com.google.common.eventbus.Subscribe;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import mmcorej.CMMCore;
import org.micromanager.PositionList;
import org.micromanager.Studio;
import org.micromanager.events.internal.InternalShutdownCommencingEvent;
import org.micromanager.internal.dialogs.AcqControlDlg;

/**
 *
 * @author nick
 */
public final class MMStudioPositionListDlg extends PositionListDlg {
    AcqControlDlg acd_;
    
    public MMStudioPositionListDlg(Studio studio, PositionList posList, AcqControlDlg acd) {
        super(studio, posList);
        acd_ = acd;
        
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent arg0) {
               saveDims();
            }
        });
    }
    
    @Override
    protected void updatePositionData() {
        super.updatePositionData();
        acd_.updateGUIContents();
    }
    
    private void saveDims() {
      int posCol0Width = posTable_.getColumnModel().getColumn(0).getWidth();
      studio_.profile().getSettings(PositionListDlg.class).putInteger(POS_COL0_WIDTH,
            posCol0Width);
      int axisCol0Width = axisTable_.getColumnModel().getColumn(0).getWidth();
      studio_.profile().getSettings(PositionListDlg.class).putInteger(AXIS_COL0_WIDTH,
            axisCol0Width);
   }
    
    @Subscribe
   public void onShutdownCommencing(InternalShutdownCommencingEvent event) {
      if (!event.getIsCancelled()) {
         saveDims();
         dispose();
      }
   }
}
