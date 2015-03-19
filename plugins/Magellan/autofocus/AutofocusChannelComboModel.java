/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package autofocus;

import javax.swing.DefaultComboBoxModel;

/**
 *
 * @author Henry
 */
public class AutofocusChannelComboModel extends DefaultComboBoxModel{
  
   private String selectedItem_;


 
   @Override
   public void setSelectedItem(Object anItem) {
     selectedItem_ = (String) anItem;
   }

   @Override
   public Object getSelectedItem() {
      return selectedItem_;
   }

//   @Override
//   public int getSize() {
//      
//   }
//
//   @Override
//   public Object getElementAt(int index) {
//      
//   }

   
}
