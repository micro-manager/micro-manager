/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package channels;

import channels.SimpleChannelTableModel;
import javax.swing.DefaultComboBoxModel;

/** 
 *
 * @author Henry
 */
public class AutofocusChannelComboModel extends DefaultComboBoxModel{
  
   private String selectedItem_;
   private SimpleChannelTableModel channelModel_;
   
   public AutofocusChannelComboModel(SimpleChannelTableModel channelModel) {
      channelModel_ = channelModel;
   }
 
   @Override
   public void setSelectedItem(Object anItem) {
     selectedItem_ = (String) anItem;
   }

   @Override
   public Object getSelectedItem() {
      return selectedItem_;
   }

   @Override
   public int getSize() {
      return channelModel_.getActiveChannelNames().length;
   }

   @Override
   public Object getElementAt(int index) {
      return channelModel_.getActiveChannelNames()[index];
   }

   
}
