///////////////////////////////////////////////////////////////////////////////
// FILE:          DragDroplistener.java
// PROJECT:       Micro-Manager
// SUBSYSTEM:     PointAndShoot plugin
// -----------------------------------------------------------------------------
//
// AUTHOR:       Nico Stuurman
//
// COPYRIGHT:    University of California, San Francisco 2018
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
package org.micromanager.pointandshootanalysis;

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
import javax.swing.JTextField;

/** @author nico */
public class DragDropListener implements DropTargetListener {
  private final JTextField textField_;

  public DragDropListener(JTextField textField) {
    textField_ = textField;
  }

  @Override
  public void dragEnter(DropTargetDragEvent dtde) {
    // throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public void dragOver(DropTargetDragEvent dtde) {
    // throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public void dropActionChanged(DropTargetDragEvent dtde) {
    // throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public void dragExit(DropTargetEvent dte) {
    // throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public void drop(DropTargetDropEvent dtde) {
    Transferable tr = dtde.getTransferable();
    DataFlavor[] flavors = tr.getTransferDataFlavors();
    try {
      for (DataFlavor flavor : flavors) {
        if (flavor.isFlavorJavaFileListType()) {
          dtde.acceptDrop(DnDConstants.ACTION_COPY_OR_MOVE);
          java.util.List list = (java.util.List) tr.getTransferData(flavor);
          // we expect only one file.  for now, just use the last one
          for (int j = 0; j < list.size(); j++) {
            File f = (File) list.get(j);
            if (f.isFile()) {
              textField_.setText(f.getPath());
            }
          }
          dtde.dropComplete(true);
          return;
        }
      }
    } catch (UnsupportedFlavorException | IOException usf) {
      // ignore
    }
  }
}
