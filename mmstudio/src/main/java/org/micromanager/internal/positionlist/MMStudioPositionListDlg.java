/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.internal.positionlist;

import mmcorej.CMMCore;
import org.micromanager.PositionList;
import org.micromanager.Studio;
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
    }
    
    @Override
    protected void updatePositionData() {
        super.updatePositionData();
        acd_.updateGUIContents();
    }
}
