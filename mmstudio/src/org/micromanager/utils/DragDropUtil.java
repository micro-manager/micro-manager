package org.micromanager.utils;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.dnd.DropTargetListener;
import java.io.File;
import java.io.IOException;
import javax.swing.SwingUtilities;
import org.micromanager.MMStudioMainFrame;

/**
 * DragDropUtil
 * Handler for drop events in Micro-Manager
 * Checks if files or folders are dropped onto Micro-Manager, and 
 * tries to open them.
 * 
 * @author nico
 * BSD license
 * Copyright University of California, 2013
 * 
 */
public class DragDropUtil implements DropTargetListener {

   public void dragEnter(DropTargetDragEvent dtde) {
      //throw new UnsupportedOperationException("Not supported yet.");
   }

   public void dragOver(DropTargetDragEvent dtde) {
      //throw new UnsupportedOperationException("Not supported yet.");
   }

   public void dropActionChanged(DropTargetDragEvent dtde) {
      //throw new UnsupportedOperationException("Not supported yet.");
   }

   public void dragExit(DropTargetEvent dte) {
      //throw new UnsupportedOperationException("Not supported yet.");
   }

   /**
    * This function does the actual work
    */
   public void drop(final DropTargetDropEvent dtde) {

      try {
         Transferable tr = dtde.getTransferable();
         DataFlavor[] flavors = tr.getTransferDataFlavors();
         for (int i = 0; i < flavors.length; i++) {
            
            if (flavors[i].isFlavorJavaFileListType()) {
               
               dtde.acceptDrop(DnDConstants.ACTION_COPY_OR_MOVE);

               java.util.List list = (java.util.List) tr.getTransferData(flavors[i]);
               for (int j = 0; j < list.size(); j++) {
                  File f = (File) list.get(j);
                  String dirtmp = f.getPath();
                  if (f.isFile()) {
                     dirtmp = f.getParent();
                  }
                  final String dir = dirtmp;
                  // to not block the UI of the OS, open in a seperate thread
                  SwingUtilities.invokeLater(new Runnable() {

                     @Override
                     public void run() {
                        try {
                           MMStudioMainFrame.getInstance().openAcquisitionData(dir, true);
                        } catch (MMScriptException ex) {
                           ReportingUtils.showError(ex);
                        }
                     }
                  });
                 
               }
               dtde.dropComplete(true);
               return;
            }
         }
      } catch (UnsupportedFlavorException ex) {
      } catch (IOException ex) {
      } 

   }
}
