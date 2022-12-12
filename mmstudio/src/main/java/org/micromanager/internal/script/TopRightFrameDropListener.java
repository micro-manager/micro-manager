package org.micromanager.internal.script;

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

public class TopRightFrameDropListener implements DropTargetListener {

   private final ScriptPanel scriptPanel_;

   public TopRightFrameDropListener(ScriptPanel scriptPanel) {
      scriptPanel_ = scriptPanel;
   }

   @Override
   public void dragEnter(DropTargetDragEvent dtde) {

   }

   @Override
   public void dragOver(DropTargetDragEvent dtde) {

   }

   @Override
   public void dropActionChanged(DropTargetDragEvent dtde) {

   }

   @Override
   public void dragExit(DropTargetEvent dte) {

   }

   @Override
   public void drop(DropTargetDropEvent dropTargetDropEvent) {
      try {
         Transferable tr = dropTargetDropEvent.getTransferable();
         DataFlavor[] flavors = tr.getTransferDataFlavors();
         for (DataFlavor flavor : flavors) {
            if (flavor.isFlavorJavaFileListType()) {

               dropTargetDropEvent.acceptDrop(DnDConstants.ACTION_COPY_OR_MOVE);

               List<Object> list = (List<Object>) tr.getTransferData(flavor);
               for (Object o : list) {
                  File f = (File) o;
                  scriptPanel_.openScriptInPane(f);
               }
               dropTargetDropEvent.dropComplete(true);
               return;
            }
         }
      } catch (UnsupportedFlavorException | IOException ex) {
         scriptPanel_.handleException(ex);
      }
   }
}