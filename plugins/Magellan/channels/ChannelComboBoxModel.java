/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package channels;

import java.util.ArrayList;
import javax.swing.DefaultComboBoxModel;
import misc.Log;
import mmcorej.Configuration;
import mmcorej.PropertySetting;
import mmcorej.StrVector;
import org.micromanager.MMStudio;
import org.micromanager.utils.ReportingUtils;

/**
 *
 * @author henrypinkard
 */
public class ChannelComboBoxModel  extends DefaultComboBoxModel {
  
   private int selected_ = 0; 
   
   private String[] getChannelGroups() {

      StrVector groups;
      try {
         groups = MMStudio.getInstance().getCore().getAllowedPropertyValues("Core", "ChannelGroup");
      } catch (Exception ex) {
         Log.log(ex.toString());
         return null;
      }
      ArrayList<String> strGroups = new ArrayList<String>();
      for (String group : groups) {
         if (groupIsEligibleChannel(group)) {
            strGroups.add(group);
         }
      }

      return strGroups.toArray(new String[0]);
   }

   private boolean groupIsEligibleChannel(String group) {
      StrVector cfgs = MMStudio.getInstance().getCore().getAvailableConfigs(group);
      if (cfgs.size() == 1) {
         Configuration presetData;
         try {
            presetData = MMStudio.getInstance().getCore().getConfigData(group, cfgs.get(0));
            if (presetData.size() == 1) {
               PropertySetting setting = presetData.getSetting(0);
               String devLabel = setting.getDeviceLabel();
               String propName = setting.getPropertyName();
               if (MMStudio.getInstance().getCore().hasPropertyLimits(devLabel, propName)) {
                  return false;
               }
            }
         } catch (Exception ex) {
            Log.log(ex.toString());
            return false;
         }
      }
      return true;
   }

    @Override
   public void setSelectedItem(Object anItem) {
      String[] groups = getChannelGroups();
      for (int i = 0; i < groups.length; i++ ) {
         if (groups[i].equals((String)anItem)) {
            selected_ = i;
         }         
      }   
   }
   
   @Override
   public Object getSelectedItem() {
      return getElementAt(selected_);
   }

   @Override
   public int getSize() {
      return getChannelGroups().length;
   }

   @Override
   public Object getElementAt(int index) {
      return getChannelGroups()[index];
   }

}
