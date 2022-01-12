///////////////////////////////////////////////////////////////////////////////
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


package org.micromanager.internal.utils;

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
import java.util.List;
import org.micromanager.Studio;
import org.micromanager.data.Datastore;
import org.micromanager.internal.MMStudio;
import org.micromanager.internal.menus.FileMenu;

/**
 * Handler for drop events in Micro-Manager.
 * Checks if files or folders are dropped onto Micro-Manager, and 
 * tries to open them.
 *
 * @author Nico Stuurman
 * 
 */
public final class DragDropUtil implements DropTargetListener {
   private final Studio studio_;

   public DragDropUtil(Studio studio) {
      studio_ = studio;
   }

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
    * This function does the actual work.
    *
    * @param dropTargetDropEvent Event signalling that a Drop occurred
    */
   @Override
   public void drop(final DropTargetDropEvent dropTargetDropEvent) {
      try {
         Transferable tr = dropTargetDropEvent.getTransferable();
         DataFlavor[] flavors = tr.getTransferDataFlavors();
         for (DataFlavor flavor : flavors) {
            if (flavor.isFlavorJavaFileListType()) {

               dropTargetDropEvent.acceptDrop(DnDConstants.ACTION_COPY_OR_MOVE);

               List<Object> list = (List<Object>) tr.getTransferData(flavor);
               for (Object o : list) {
                  File f = (File) o;
                  String dirtmp = f.getPath();
                  if (f.isFile()) {
                     dirtmp = f.getParent();
                  }
                  final String dir = dirtmp;

                  // to not block the UI of the OS, open in a separate thread          
                  new Thread(() -> loadData(dir)).start();

               }
               dropTargetDropEvent.dropComplete(true);
               return;
            }
         }
      } catch (UnsupportedFlavorException ex) {
      } catch (IOException ex) {
      }
   }

   private void loadData(String dir) {
      try {
         Datastore store = studio_.data().loadData(dir, false);
         studio_.displays().manage(store);
         studio_.displays().loadDisplays(store);
         FileMenu fm = ((MMStudio) studio_).uiManager().menubar().getFileMenu();
         if (fm != null) {
            fm.updateFileHistory(store.getSavePath());
         }
      } catch (IOException e) {
         ReportingUtils.showError(e, "There was an error while opening the file at " + dir);
      }
   }
}
