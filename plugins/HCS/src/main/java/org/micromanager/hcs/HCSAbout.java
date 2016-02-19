package org.micromanager.hcs;

import java.awt.BorderLayout;
import java.awt.FlowLayout;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import javax.swing.JTextPane;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;

public class HCSAbout extends JDialog {
   private static final long serialVersionUID = 1L;
   
   private final JPanel contentPanel = new JPanel();

   /**
    * Create the dialog.
    */
   public HCSAbout(SiteGenerator parent) {
      setModal(true);
      setTitle("About HCS Site Generator " + HCSPlugin.VERSION_INFO);
      setBounds(200, 200, 462, 273);
      getContentPane().setLayout(new BorderLayout());
      contentPanel.setBorder(new EmptyBorder(5, 5, 5, 5));
      getContentPane().add(contentPanel, BorderLayout.CENTER);
      contentPanel.setLayout(new BorderLayout(0, 0));
      {
         JTextPane txtpnHcsModuleCopyright = new JTextPane();
         txtpnHcsModuleCopyright.setEditable(false);
         txtpnHcsModuleCopyright.setText("HCS Site Generator\r\n\r\n" +
         "Derived from HCS plugin by 100X Imaging Inc.\r\n" +
         "THIS SOFTWARE IS PROVIDED IN THE HOPE THAT IT MAY BE USEFUL, WITHOUT ANY REPRESENTATIONS OR WARRANTIES, INCLUDING WITHOUT LIMITATION THE WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE. IN NO EVENT SHALL AUTHOR BE LIABLE FOR INDIRECT, EXEMPLARY, PUNITIVE, INCIDENTAL, OR CONSEQUENTIAL DAMAGES ARISING FROM USE OF THIS SOFTWARE, REGARDLESS OF THE FORM OF ACTION, AND WHETHER OR NOT THE AUTHOR HAS BEEN INFORMED OF, OR OTHERWISE MIGHT HAVE ANTICIPATED, THE POSSIBILITY OF SUCH DAMAGES.\r\n\r\n");
         contentPanel.add(txtpnHcsModuleCopyright);
      }
      {
         JPanel buttonPane = new JPanel();
         buttonPane.setLayout(new FlowLayout(FlowLayout.RIGHT));
         getContentPane().add(buttonPane, BorderLayout.SOUTH);
         {
            JButton okButton = new JButton("OK");
            okButton.addActionListener(new ActionListener() {
               public void actionPerformed(ActionEvent e) {
                  dispose();
               }
            });
            okButton.setActionCommand("OK");
            buttonPane.add(okButton);
            getRootPane().setDefaultButton(okButton);
         }
      }
   }

}
