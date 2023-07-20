package org.micromanager.acquisition.internal.acqengjcompat.multimda;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import org.micromanager.internal.dialogs.AcqControlDlg;
import org.micromanager.internal.dialogs.ComponentTitledBorder;
import org.micromanager.internal.utils.MMException;

/**
 * Utility class to make a checkboxPanel with a border with a title.
 */
public class CheckBoxPanel extends AcqControlDlg.ComponentTitledPanel {

   JCheckBox checkBox;

   public CheckBoxPanel(String title) {
      super();
      titleComponent = new JCheckBox(title);
      checkBox = (JCheckBox) titleComponent;

      compTitledBorder = new ComponentTitledBorder(checkBox, this,
            BorderFactory.createEtchedBorder());
      super.setBorder(compTitledBorder);
      borderSet_ = true;

      final CheckBoxPanel thisPanel = this;

      checkBox.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            boolean enable = checkBox.isSelected();
            thisPanel.setChildrenEnabled(enable);
         }

         public void writeObject(java.io.ObjectOutputStream stream) throws MMException {
            throw new MMException("Do not serialize this class");
         }
      });
   }

   /**
    * Sets enable/disable for all children of this component.
    *
    * @param enabled selects enable or disable.
    */
   public void setChildrenEnabled(boolean enabled) {
      Component[] comp = this.getComponents();
      for (Component comp1 : comp) {
         if (comp1.getClass() == JPanel.class) {
            Component[] subComp = ((JPanel) comp1).getComponents();
            for (Component subComp1 : subComp) {
               subComp1.setEnabled(enabled);
            }
         } else {
            comp1.setEnabled(enabled);
         }
      }
   }

   public boolean isSelected() {
      return checkBox.isSelected();
   }

   public void setSelected(boolean selected) {
      checkBox.setSelected(selected);
      setChildrenEnabled(selected);
   }

   public void addActionListener(ActionListener actionListener) {
      checkBox.addActionListener(actionListener);
   }

   /**
    * Removes all action listeners.
    */
   public void removeActionListeners() {
      for (ActionListener l : checkBox.getActionListeners()) {
         checkBox.removeActionListener(l);
      }
   }
}
