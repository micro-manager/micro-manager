///////////////////////////////////////////////////////////////////////////////
//FILE:          filecelleditor.java
//PROJECT:       Micro-Manager 
//SUBSYSTEM:     asi gamepad plugin
//-----------------------------------------------------------------------------
//
// AUTHOR:       Vikram Kopuri
//
// COPYRIGHT:    Applied Scientific Instrumentation (ASI), 2018
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

package com.asiimaging.asigamepad;

import java.awt.Component;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import javax.swing.AbstractCellEditor;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.TableCellEditor;

/**
 * Custom file editor for the Button assignment table's 3rd column.
 * Presents user with a JFileChooser dialog
 *
 * @author Vikram Kopuri for ASI
 */

public class FileCellEditor extends AbstractCellEditor implements TableCellEditor {

   private static final long serialVersionUID = -4283475330269226460L;
   JFileChooser myJfc;
   File filepicked;
   JTextField jtfPick;
   FileFilter scriptfilter;

   /**
    * Ini the JTextField and JFileChooser that are to be supplied on cell edit
    */
   public FileCellEditor() {

      //filepicked=new File(".");
      filepicked = new File(System.getProperty("user.dir"));

      jtfPick = new JTextField("Click to Pick Script");

      scriptfilter = new FileNameExtensionFilter("BeanShell Script", "bsh");

      jtfPick.addMouseListener(new MouseAdapter() {

         @Override
         public void mouseClicked(MouseEvent e) {
            //my_jfc = new JFileChooser(new File("."));
            myJfc = new JFileChooser();
            myJfc.setCurrentDirectory(filepicked);
            myJfc.setFileFilter(scriptfilter);
            if (myJfc.showOpenDialog(new JDialog()) == JFileChooser.APPROVE_OPTION) {
               filepicked = myJfc.getSelectedFile();
               jtfPick.setText(filepicked.toString());
            }
         }

      });
   }

   @Override
   public Object getCellEditorValue() {
      return filepicked.toString();
   }


   /**
    * Implement the one method defined by TableCellEditor.
    * When cell is checked we replace the cellrenderer object with our
    * new components, in this case a JTextfield
    */
   public Component getTableCellEditorComponent(JTable table,
                                                Object value,
                                                boolean isSelected,
                                                int row,
                                                int column) {
      File temp = new File((String) value);

      if (temp.exists()) {
         filepicked = temp;
      }
      return jtfPick;
   }

} //end of class