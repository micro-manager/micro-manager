///////////////////////////////////////////////////////////////////////////////
// FILE:          DragDropUtil.java
// PROJECT:       Micro-Manager
// SUBSYSTEM:     mmstudio/utils
//-----------------------------------------------------------------------------
//
// AUTHOR:        Nico Stuurman, nico.stuurman@ucsf.edu, March 9, 2013
//
// COPYRIGHT:     University of California, San Francisco, 2013
//
// LICENSE:       This file is distributed under the BSD license.
//                License text is included with the source distribution.
//
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.


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
import org.micromanager.MMStudio;

/**
 * DragDropUtil
 * Handler for drop events in Micro-Manager
 * Checks if files or folders are dropped onto Micro-Manager, and 
 * tries to open them.
 * 
 * @author nico
 * 
 */
public class DragDropUtil implements DropTargetListener {

   @Override
   public void dragEnter(DropTargetDragEvent dtde) {
      //throw new UnsupportedOperationException("Not supported yet.");
   }

   @Override
   public void dragOver(DropTargetDragEvent dtde) {
      //throw new UnsupportedOperationException("Not supported yet.");
   }

   @Override
   public void dropActionChanged(DropTargetDragEvent dtde) {
      //throw new UnsupportedOperationException("Not supported yet.");
   }

   @Override
   public void dragExit(DropTargetEvent dte) {
      //throw new UnsupportedOperationException("Not supported yet.");
   }

   /**
    * This function does the actual work
    */
   @Override
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

                  // to not block the UI of the OS, open in a separate thread          
                  new Thread() {
                     @Override
                     public void run() {
                        try {
                           MMStudio.getInstance().openAcquisitionData(dir, true);
                        } catch (MMScriptException ex) {
                           ReportingUtils.showError(ex);
                        }
                     }
                  }.start();

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
