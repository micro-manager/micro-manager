package org.micromanager.magellan.api;

import java.util.ArrayList;
import java.util.List;
import org.micromanager.magellan.acq.MagellanAcquisitionsManager;
import org.micromanager.magellan.gui.GUI;

/**
 *
 * @author henrypinkard
 */
public class MagellanAPI {

   private static MagellanAPI singleton_;
   private static GUI gui_;

   public MagellanAPI() {
      singleton_ = this;
   }

   public MagellanAcquisitionAPI createAcquisition() {
      return MagellanAcquisitionsManager.getInstance().addNew();
   }
   
   public void deleteAcquisition(int index) {
      MagellanAcquisitionsManager.getInstance().remove(index);
   }

   public List<MagellanAcquisitionAPI> getAcquisitions() {
      ArrayList<MagellanAcquisitionAPI> acqList = new ArrayList<MagellanAcquisitionAPI>();
      for (int i = 0; i < MagellanAcquisitionsManager.getInstance().getNumberOfAcquisitions(); i++) {
         acqList.add(MagellanAcquisitionsManager.getInstance().getAcquisition(i));
      }
      return acqList;
   }
   

}
