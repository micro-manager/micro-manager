package org.micromanager.conf2;

import javax.swing.tree.DefaultMutableTreeNode;

class DeviceTreeNode extends DefaultMutableTreeNode {
   private static final long serialVersionUID = 1L;
   boolean showLib_;

   public DeviceTreeNode(String value, boolean byLib) {
      super(value);
      showLib_ = byLib;
   }

   @Override
   public String toString() {
      String ret = "";
      Object uo = getUserObject();
      if (null != uo) {
         if (uo.getClass().isArray()) {
            Object[] userData = (Object[]) uo;
            if (2 < userData.length) {
               if (showLib_)
                  ret = userData[1].toString() + " | "
                        + userData[2].toString();
               else
                  ret = userData[0].toString() + " | "
                        + userData[2].toString();
            }
         } else {
            ret = uo.toString();
         }
      }
      return ret;
   }

   // if user clicks on a container node, just return a null array instead of
   // the user data

   public Object[] getUserDataArray() {
      Object[] ret = null;

      Object uo = getUserObject();
      if (null != uo) {
         if (uo.getClass().isArray()) {
            // retrieve the device info tuple
            Object[] userData = (Object[]) uo;
            if (1 < userData.length) {
               ret = userData;
            }
         }

      }
      return ret;
   }
}