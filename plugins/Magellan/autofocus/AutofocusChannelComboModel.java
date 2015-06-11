///////////////////////////////////////////////////////////////////////////////
// AUTHOR:       Henry Pinkard, henry.pinkard@gmail.com
//
// COPYRIGHT:    University of California, San Francisco, 2015
//
// LICENSE:      This file is distributed under the BSD license.
//               License text is included with the source distribution.
//
//               This file is distributed in the hope that it will be useful,
//               but WITHOUT ANY WARRANTY; without even the implied warranty
//               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//

package autofocus;

import channels.SimpleChannelTableModel;
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
