///////////////////////////////////////////////////////////////////////////////
//FILE:          MosaicSequencingFrame.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     Projector plugin
//-----------------------------------------------------------------------------
//COPYRIGHT:     Andor Technology, Inc., 2014
//               Contributed as open source to the Micro-Manager code base.
//AUTHOR:        Code written by Arthur Edelstein.
//LICENSE:       This file is distributed under the BSD license.
//               License text is included with the source distribution.
//               This file is distributed in the hope that it will be useful,
//               but WITHOUT ANY WARRANTY; without even the implied warranty
//               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.

// The functions in this file are written in call
// stack order: functions declared earlier in the file are
// called by those declared later.

// This source file is formatted to be processed
// with [docco](http://jashkenas.github.io/docco/),
// which generates nice HTML documentation side-by-side with the
// source code.

package org.micromanager.projector.internal;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.PolygonRoi;
import ij.gui.Roi;
import ij.plugin.frame.RoiManager;
import ij.process.FloatPolygon;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.swing.BorderFactory;
import javax.swing.DefaultCellEditor;
import javax.swing.DefaultComboBoxModel;
import javax.swing.GroupLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.LayoutStyle;
import javax.swing.ListModel;
import javax.swing.border.TitledBorder;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableModel;
import mmcorej.CMMCore;
import mmcorej.DeviceType;
import mmcorej.StrVector;
import mmcorej.org.json.JSONArray;
import mmcorej.org.json.JSONException;
import mmcorej.org.json.JSONObject;
import org.micromanager.Studio;
import org.micromanager.internal.utils.FileDialogs;
import org.micromanager.internal.utils.GUIUtils;
import org.micromanager.internal.utils.ReportingUtils;
import org.micromanager.internal.utils.TextUtils;
import org.micromanager.internal.utils.WindowPositioning;
import org.micromanager.projector.Mapping;
import org.micromanager.projector.ProjectorActions;
import org.micromanager.projector.internal.devices.SLM;

// The Mosaic Sequencing Window is for use with Andor's Mosaic3 device adapter.
// It allows the creation of complex phototargeting sequences, for use with
// Micro-Manager's multi-dimensional acquisition.
public class MosaicSequencingFrame extends JFrame {

   private final CMMCore core_;
   private final Studio gui_;
   private final String mosaicName_;
   private final SLM mosaicDevice_;
   private int mosaicWidth_ = 0;
   private int mosaicHeight_ = 0;
   private final ProjectorControlForm projectorControlForm_;
   private final DefaultTableModel sequenceTableModel_;
   private final Vector<String> headerNames = new Vector<String>(Arrays.asList(
         new String[]{"Time Slot", "Roi Indices", "On Duration (ms)",
               "Off Duration (ms)", "Loop count"}));
   private final List<String> intensityNames_;
   private final ExecutorService mosaicExecutor_;

   // ## Utility functions.

   // The triggerProperties_ allow us to convert GUI names for triggering
   // to the Mosaic3 device adapter names for triggering.
   final Map<String, String> triggerProperties_ = new HashMap<String, String>() {{
         put("External Frame Bulb", "ExternalBulb");
         put("Sequence Start", "InternalExpose");
         put("External Advance", "ExternalExpose");
         put("External Start", "ExternalSequenceStart");
      }};

   // Returns all active Mosaic devices reported by the MMCore.
   public static ArrayList<String> getMosaicDevices(CMMCore core) {
      StrVector slmDevices = core.getLoadedDevicesOfType(DeviceType.SLMDevice);
      final ArrayList<String> mosaicDevices = new ArrayList<String>();
      for (final String slmDevice : slmDevices) {
         try {
            if (core.getDeviceLibrary(slmDevice).contentEquals("Mosaic3")) {
               mosaicDevices.add(slmDevice);
            }
         } catch (Exception ex) {
            ReportingUtils.logError(ex);
         }
      }
      return mosaicDevices;
   }

   // Returns the current Rois on the live window.
   private Roi[] getRois() {
      ImagePlus plus;
      try {
         plus = gui_.live().getDisplay().getImagePlus();
      } catch (Exception e) {
         return new Roi[0]; // empty array
      }
      return ProjectorControlForm.getRois(plus, false);
   }

   // Get the total number of ROIs available in the Snap/Live window.
   private int getRoiCount() {
      Roi[] rois = getRois();
      return rois.length;
   }

   // ## The ROI List
   // This table should reflect the list of ROIs displayed in ImageJ's
   // ROI manager, as well as the active image window (probably the
   // live image display). Two extra columns allow the user to modify
   // whether a particular ROI should be considered a "FRAP" ROI or an
   // "Image" ROI.

   // A simple data class that specifies characteristics of an ROI.
   // Each ROI should be a FRAP or Image type.
   // The intensity should be between 0 and 255 (max).
   public class RoiSettings {

      boolean roiType = false;
      int intensity = 100;
   }

   // Update the ROI table so it reflects what is in the roiListModel.
   private void updateRoiTable(ListModel roiListModel) {
      ArrayList<String> roiNames = new ArrayList<String>();
      for (int i = 0; i < roiListModel.getSize(); ++i) {
         roiNames.add(roiListModel.getElementAt(i).toString());
      }
      DefaultTableModel tableModel = (DefaultTableModel) this.roiListTable_.getModel();
      // clear the table
      tableModel.setRowCount(0);
      int i = 0;
      for (String name : roiNames) {
         Vector<Object> rowVector = new Vector<Object>();
         ++i;
         rowVector.add(i);
         rowVector.add(name);
         rowVector.add("FRAP");
         rowVector.add("15/15");
         tableModel.addRow(rowVector);
      }
   }

   // Bind the Roi List table to Roi Manager list.
   private void mirrorRoiManager() {
      final ListModel roiListModel = ((JList) ((JScrollPane) RoiManager.getInstance()
            .getComponent(0)).getViewport().getComponent(0)).getModel();
      updateRoiTable(roiListModel);
      roiListModel.addListDataListener(new ListDataListener() {
         @Override
         public void intervalAdded(ListDataEvent e) {
            updateRoiTable(roiListModel);
         }

         @Override
         public void intervalRemoved(ListDataEvent e) {
            updateRoiTable(roiListModel);
         }

         @Override
         public void contentsChanged(ListDataEvent e) {
            updateRoiTable(roiListModel);
         }
      });
   }

   // Set the editor for a particular JTable column as a combo box, with the
   // options given by the items array.
   private void setComboBoxColumn(JTable table, int columnIndex, String[] items) {
      table.getColumnModel().getColumn(columnIndex)
            .setCellEditor(new DefaultCellEditor(new JComboBox(items)));
   }

   // Generates a list of intensity percentages, that correspond to 4-bits
   // of grayscale. There are 
   private static List<String> generateIntensityNames() {
      final ArrayList<String> intensities = new ArrayList<String>();
      for (int i = 0; i <= 15; ++i) {
         intensities.add("" + i + "/15");
      }
      return intensities;
   }

   // Create the ROI list table
   private void setupRoiListTable() {
      String[] roiTypes = {"FRAP", "Image"};
      setComboBoxColumn(roiListTable_, 2, roiTypes);
      Vector<String> intensityNames = new Vector<String>();
      intensityNames.addAll(intensityNames_);
      Collections.reverse(intensityNames);
      setComboBoxColumn(roiListTable_, 3, intensityNames.toArray(new String[0]));
      mirrorRoiManager();
   }

   // Get the intensity given the ROI number (zero-based). Intensities values
   // are always provided as 8-bit, so that in BlackAndWhite mode, white pixels
   // have bit7 = 1.
   private int getRoiIntensity(int roiIndex) {
      String intensityName = (String) roiListTable_.getValueAt(roiIndex, 3);
      for (int i = 0; i < intensityNames_.size(); ++i) {
         if (intensityNames_.get(i).contentEquals(intensityName)) {
            return i << 4;
         }
      }
      return 0;
   }

   // Returns true if ROI at a particular index is considered an "Imaging" ROI.
   private boolean isImageROI(int roiIndex) {
      final TableModel roiListTableModel = roiListTable_.getModel();
      String roiType = (String) roiListTableModel.getValueAt(roiIndex - 1, 2);
      return roiType.contentEquals("Image");
   }

   // Returns a list of ROIs that have been designated as Image ROIs.
   private List<Integer> getImageROIs() {
      final List<Integer> imageRois = new ArrayList<Integer>();
      for (int i = 1; i <= roiListTable_.getRowCount(); ++i) {
         if (isImageROI(i)) {
            imageRois.add(i);
         }
      }
      return imageRois;
   }

   // Returns a list of ROIs that have been designated as FRAP ROIs.
   private List<Integer> getFrapROIs() {
      final List<Integer> frapROIs = new ArrayList<Integer>();
      for (int i = 1; i <= roiListTable_.getRowCount(); ++i) {
         if (!isImageROI(i)) {
            frapROIs.add(i);
         }
      }
      return frapROIs;
   }

   // Returns true iff all ROIs are set to 100% or 0% intensity.
   private boolean canUseBlackAndWhite() {
      for (int i = 0; i < roiListTable_.getRowCount(); ++i) {
         int intensity = getRoiIntensity(i);
         if (intensity > 0 && intensity < 240) {
            return false;
         }
      }
      return true;
   }

   // ## Generate ROI grid panel.

   // Generate the ROI grid from a single ROI. The initial ROI will be located
   // in the upper left corner of the grid and the remaining ROIs will be
   // clones of that ROI, with offsets specified.
   private void generateRoiGrid() {
      Roi selectedRoi = IJ.getImage().getRoi();
      RoiManager roiManager = Utils.showRoiManager();
      if (selectedRoi == null && roiManager.getCount() > 0) {
         int firstSelectedRoi = Math.max(0, roiManager.getSelectedIndex());
         selectedRoi = roiManager.getRoisAsArray()[firstSelectedRoi];
      }
      if (selectedRoi == null) {
         ReportingUtils.showError("Please draw a single ROI for duplication in a grid.");
         return;
      }
      roiManager.runCommand("reset");
      int numX = GUIUtils.getIntValue(this.numRoisAcrossField_);
      int numY = GUIUtils.getIntValue(this.numRoisDownField_);
      int spacingX = GUIUtils.getIntValue(this.roiSpacingAcrossField_);
      int spacingY = GUIUtils.getIntValue(this.roiSpacingDownField_);
      for (int j = 0; j < numY; ++j) {
         for (int i = 0; i < numX; ++i) {
            Roi newRoi = (Roi) selectedRoi.clone();
            Rectangle bounds = selectedRoi.getBounds();
            newRoi.setLocation(bounds.x + i * spacingX, bounds.y + j * spacingY);
            roiManager.addRoi(newRoi);
         }
      }
   }

   // ## The Sequence table. This table specifies which ROIs are illuminated,
   // and in what order. It can be autogenerated by the "Create Sequence"
   // tab, but it can also be edited by the user.

   // Alter the Time Slot indices such that the first row is
   // column 1, the second column 2, and so on.
   private void updateTimeSlotIndices(final DefaultTableModel sequenceTableModel) {
      for (int i = 0; i < sequenceTableModel.getRowCount(); ++i) {
         sequenceTableModel.setValueAt(i + 1, i, 0);
      }
   }

   // Ensures that the "Time Slot" column such that the first
   // row is column 1, the second is 2, and so on.
   private void keepTimeSlotIndicesUpToDate(final DefaultTableModel sequenceTableModel) {
      sequenceTableModel.addTableModelListener(new TableModelListener() {
         @Override
         public void tableChanged(TableModelEvent e) {
            sequenceTableModel.removeTableModelListener(this);
            updateTimeSlotIndices(sequenceTableModel);
            sequenceTableModel.addTableModelListener(this);
         }
      });
   }

   // Adds a row to the sequence table, including ROIs, an
   // on-duration period, an off-duration period, and a loop 
   // repeat count. Returns the index of the added row.
   private int addRow(DefaultTableModel sequenceTableModel, String roiIndicesString,
         int onDurationMs, int offDurationMs, int loopCount) {
      ArrayList<String> rows = new ArrayList<String>();
      rows.add("");
      rows.add(roiIndicesString);
      rows.add(String.valueOf(onDurationMs).trim());
      rows.add(String.valueOf(offDurationMs).trim());
      rows.add(String.valueOf(loopCount).trim());
      sequenceTableModel.addRow(rows.toArray());
      sequenceTable_.invalidate();
      return sequenceTableModel.getRowCount() - 1;
   }

   // Implements the Add Time Slot button. Adds the default 
   // integer values. Sends the user to edit the Frame ROIs.
   private void addTimeSlot() {
      int row = addRow(
            sequenceTableModel_,
            "",
            GUIUtils.getIntValue(onDurationTextField_),
            GUIUtils.getIntValue(offDurationTextField_),
            GUIUtils.getIntValue(loopCountTextField_));
      sequenceTable_.setRowSelectionInterval(row, row);
      GUIUtils.startEditingAtCell(sequenceTable_, row, 1);
   }

   // Takes a list of integers and converts that to a string containing
   // a list of integers separated by commas. In clojure: (string/join "," items)
   private String integerList(List<Integer> items) {
      String theString = "";
      for (int item : items) {
         theString += item + ",";
      }
      return theString.substring(0, theString.length() - 1);
   }

   // Generate a new sequence of phototargeting events and place
   // in the sequence table.
   private void generateNewSequence() {
      int roiCount = getRoiCount();
      if (roiCount == 0) {
         throw new RuntimeException("Please draw ROIs for phototargeting.");
      }
      int onDurationMs = GUIUtils.getIntValue(onDurationTextField_);
      int offDurationMs = GUIUtils.getIntValue(offDurationTextField_);
      int loopCount = GUIUtils.getIntValue(loopCountTextField_);
      String sequenceType = (String) sequenceTypeComboBox.getSelectedItem();
      sequenceTable_.clearSelection();
      sequenceTableModel_.getDataVector().clear();
      List<Integer> frapROIs = getFrapROIs();
      if (sequenceType.contentEquals("Simultaneous")) {
         addRow(sequenceTableModel_, "1-" + String.valueOf(roiCount).trim(), onDurationMs,
               offDurationMs, loopCount);
      }
      if (sequenceType.contentEquals("Sequential")) {
         for (int i = 0; i < frapROIs.size(); ++i) {
            final List<Integer> rois = this.getImageROIs();
            rois.addAll(frapROIs.subList(i, i + 1));
            Collections.sort(rois);
            addRow(sequenceTableModel_, integerList(rois),
                  onDurationMs, offDurationMs, loopCount);
         }
      }
      if (sequenceType.contentEquals("Cumulative")) {
         for (int i = 0; i < frapROIs.size(); ++i) {
            final List<Integer> rois = this.getImageROIs();
            rois.addAll(frapROIs.subList(0, i + 1));
            Collections.sort(rois);
            addRow(sequenceTableModel_, integerList(rois),
                  onDurationMs, offDurationMs, loopCount);
         }
      }
      updateTimeSlotIndices(sequenceTableModel_);
   }

   // Convert a textual representation of a set of one-based roi indices to a
   // vector of zero-based integers also representing those indices. For example,
   // "1-3,5,7,9-11" gets converted to [0,1,2,4,6,8,9,10].
   private ArrayList<Integer> frameStringToRoiIndexList(String sequenceString) {
      ArrayList<Integer> sequence = new ArrayList<Integer>();
      String[] pieces = sequenceString.split(",");
      try {
         for (String piece : pieces) {
            // First parse a range fragment, such as "2-4".
            if (piece.contains("-")) {
               String[] limits = piece.split("-");
               int min = Integer.parseInt(limits[0]);
               int max = Integer.parseInt(limits[1]);
               // If the limits are specified in reverse order, fix that.
               if (min > max) {
                  int temp = max;
                  max = min;
                  min = temp;
               }
               // Add each integer in range (inclusive) to the sequence.
               for (int i = min; i <= max; ++i) {
                  sequence.add(i - 1);
               }
            } else {
               // Parse a standalone integer.
               int value = Integer.parseInt(piece);
               sequence.add(value - 1);
            }
         }
         // Throw an exception in case we have a parse error. We'll use
         // the absence (presence) of this exception to (in)validate
         // user entries.
      } catch (NumberFormatException e) {
         throw new RuntimeException("Unable to parse frame specifier");
      }
      return sequence;
   }

   // The SequenceEvent object defines a Sequence event by a number of
   // simple parameters, including the on and off duration, the loopCount,
   // and the pixels belonging to a particular frame.
   private class SequenceEvent {

      public byte[] framePixels;
      public int onDurationMs;
      public int offDurationMs;
      public int loopCount;
   }

   // Read an integer from a sequenceTable_ cell.
   private int integerAt(int rowIndex, int columnIndex) {
      return Integer.parseInt(sequenceTableModel_.getValueAt(rowIndex, columnIndex).toString());
   }

   // Convert a row from the sequenceTable_ to a SequenceEvent object.
   private SequenceEvent sequenceRowToSequenceEvent(int rowIndex,
         List<Polygon> availableRoiPolygons) {
      final ArrayList<Integer> roiIndexList = frameStringToRoiIndexList(
            sequenceTableModel_.getValueAt(rowIndex, 1).toString());
      SequenceEvent sequenceEvent = new SequenceEvent();
      ArrayList<Polygon> selectedRoiPolygons = new ArrayList<Polygon>();
      for (int roiIndex : roiIndexList) {
         if ((roiIndex < 0) || (roiIndex >= availableRoiPolygons.size())) {
            throw new RuntimeException(
                  "An ROI that does not exist was specified in a sequence event.");
         }
         selectedRoiPolygons.add(availableRoiPolygons.get(roiIndex));
      }
      ArrayList<Integer> selectedRoiIntensities = new ArrayList<Integer>();
      for (int roiIndex : roiIndexList) {
         selectedRoiIntensities.add(getRoiIntensity(roiIndex));
      }
      sequenceEvent.framePixels = mosaicDevice_.roisToPixels(mosaicWidth_, mosaicHeight_,
            selectedRoiPolygons,
            selectedRoiIntensities);
      sequenceEvent.onDurationMs = integerAt(rowIndex, 2);
      sequenceEvent.offDurationMs = integerAt(rowIndex, 3);
      sequenceEvent.loopCount = integerAt(rowIndex, 4);
      return sequenceEvent;
   }

   // Get a list of SequenceEvents by extracting information from the sequenceTable_.
   private ArrayList<SequenceEvent> getSequenceEvents() {
      final ImagePlus snapLiveImage = gui_.live().getDisplay().getImagePlus();
      Mapping mapping = projectorControlForm_.getMapping();
      Integer binning = null;
      Rectangle roi = null;
      try {
         binning = Integer.parseInt(core_.getProperty(core_.getCameraDevice(), "Binning"));
         roi = core_.getROI();
      } catch (Exception ex) {
         gui_.logs().logError(ex);
      }
      List<FloatPolygon> availableFloatRoiPolygons =
            ProjectorActions.transformROIs(getRois(), mapping, roi, binning);
      List<Polygon> availableRoiPolygons = Utils.floatToNormalPolygon(
            availableFloatRoiPolygons);
      ArrayList<SequenceEvent> events = new ArrayList<SequenceEvent>();
      for (int i = 0; i < sequenceTableModel_.getRowCount(); ++i) {
         events.add(sequenceRowToSequenceEvent(i, availableRoiPolygons));
      }
      return events;
   }

   // Convert a series of sequence events to a string that can be passed
   // to the Mosaic3's "SequenceSettings" property.
   private String sequenceEventsToString(final ArrayList<SequenceEvent> events) {
      String eventString = "";
      int i = 0;
      // Each sequence event is delimited by a semicolon, and the event's
      // parameters are internally separated by spaces.
      for (SequenceEvent event : events) {
         eventString += i
               + " " + event.onDurationMs
               + " " + event.offDurationMs
               + " " + event.loopCount + ";";
         ++i;
      }
      return eventString;
   }

   // Convert a list of SequenceEvents to a list of byte arrays, each
   // corresponding to a frame in the sequence.
   private List<byte[]> imageListFromSequenceEvents(final ArrayList<SequenceEvent> events) {
      List<byte[]> imageList = new ArrayList<byte[]>();
      for (SequenceEvent event : events) {
         imageList.add(event.framePixels);
      }
      return imageList;
   }

   // ## High-level Mosaic control calls

   // Upload the provided SequenceEvents to the Mosaic, by using the
   // "SequenceSettings" property.
   private void uploadSequence(final ArrayList<SequenceEvent> events) {
      if (events.isEmpty()) {
         throw new RuntimeException("Please first define a sequence for the Mosaic.");
      }
      mosaicExecutor_.submit(new Runnable() {
         @Override
         public void run() {
            try {
               core_.stopSLMSequence(mosaicName_);
               core_.setProperty(mosaicName_, "PixelMode",
                     canUseBlackAndWhite() ? "BlackAndWhite" : "16GraysLinear");
               core_.setProperty(mosaicName_, "SequenceSettings", sequenceEventsToString(events));
               core_.loadSLMSequence(mosaicName_, imageListFromSequenceEvents(events));
            } catch (Exception ex) {
               ReportingUtils.showError(ex);
            }
         }
      });
   }

   // Upload the sequence from the sequence table to the Mosaic.
   private void uploadSequence() {
      uploadSequence(getSequenceEvents());
   }

   // Returns the number of sequence events stored on board the Mosaic.
   private int getSequenceCount() throws Exception {
      return Integer.parseInt(core_.getProperty(mosaicName_, "SequenceEventCount"));
   }

   // Run the Mosaic Sequence. Called by Run button.
   private void runSequence() throws Exception {
      if (getSequenceCount() == 0) {
         throw new Exception("Please upload a sequence to the Mosaic before pressing \"Run\".");
      }
      mosaicExecutor_.submit(new Runnable() {
         @Override
         public void run() {
            try {
               // Make sure a sequence isn't running.
               core_.stopSLMSequence(mosaicName_);
               final String selectedItem = sequenceTriggerComboBox.getSelectedItem().toString();
               core_.setProperty(mosaicName_, "SequenceLoopCount",
                     GUIUtils.getIntValue(sequenceLoopCountTextField_));
               core_.setProperty(mosaicName_, "TriggerMode", triggerProperties_.get(selectedItem));
               core_.setProperty(mosaicName_, "OperationMode", "FrameSequence");
               // Open the Phototargeting shutter, if needed.
               final boolean shutterOriginallyOpen = projectorControlForm_.prepareShutter();
               // Run the SLM sequence according to uploaded
               // settings. startSLMSequence returns immediately,
               // but waitForDevice will block.
               core_.startSLMSequence(mosaicName_);
               // Wait until the end of the sequence. We don't use
               // waitForDevice(...) here, because it uses a module lock
               // and would prevent stopSequence from terminating the
               // sequence early.
               while (core_.deviceBusy(mosaicName_)) {
                  Thread.sleep(10);
               }
               // Close phototargeting shutter if it was
               // originallyOpen.
               projectorControlForm_.returnShutter(shutterOriginallyOpen);
            } catch (Exception ex) {
               ReportingUtils.showError(ex);
            }
         }
      });
   }

   // Abort the Mosaic sequence. Called by Stop button.
   private void stopSequence() {
      try {
         // We don't run this on the Mosaic Executor because
         // we want to interrupt any running sequences.
         core_.stopSLMSequence(mosaicName_);
      } catch (Exception ex) {
         ReportingUtils.showError(ex);
      }
   }

   // ## Mosaic ROI Sequence persistence
   // Loading and saving the ROIs and sequences to a JSON file.

   // Reads the current ROIs and returns their vertices in JSON Array.
   private JSONArray roisToJSON(Roi[] rois) {
      JSONArray allPolygons = new JSONArray();
      for (Roi roi : rois) {
         JSONArray polyPoints = new JSONArray();
         Polygon polygon = roi.getPolygon();
         for (int i = 0; i < polygon.npoints; ++i) {
            JSONArray xy = new JSONArray();
            xy.put(polygon.xpoints[i]);
            xy.put(polygon.ypoints[i]);
            polyPoints.put(xy);
         }
         allPolygons.put(polyPoints);
      }
      return allPolygons;
   }

   // Returns ROI objects generated by interpreting vertices from JSON.
   private Roi[] roisFromJSON(JSONArray data) {
      try {
         Vector<Roi> rois = new Vector<Roi>();
         for (int i = 0; i < data.length(); ++i) {
            Polygon poly = new Polygon();
            JSONArray points = data.getJSONArray(i);
            for (int j = 0; j < points.length(); ++j) {
               int x = points.getJSONArray(j).getInt(0);
               int y = points.getJSONArray(j).getInt(1);
               poly.addPoint(x, y);
            }
            Roi roi = new PolygonRoi(poly, Roi.POLYGON);
            rois.add(roi);
         }
         return rois.toArray(new Roi[0]);
      } catch (JSONException ex) {
         ReportingUtils.logError(ex);
         return new Roi[0];
      }
   }

   // Read the sequence settings from the table and put into a JSON object.
   private JSONArray sequenceTableToJSON() {
      int columnCount = sequenceTableModel_.getColumnCount();
      int rowCount = sequenceTableModel_.getRowCount();
      JSONArray tableData = new JSONArray();
      for (int row = 0; row < rowCount; ++row) {
         // For each row, we produce a JSONObject mapping header name
         // to cell value. This arrangement is robust to future changes
         // in the column positions.
         JSONObject rowData = new JSONObject();
         for (int column = 0; column < columnCount; ++column) {
            try {
               headerNames.get(column);
               rowData.put(headerNames.get(column), sequenceTableModel_.getValueAt(row, column));
            } catch (JSONException ex) {
               ReportingUtils.logError(ex);
            }
         }
         tableData.put(rowData);
      }
      return tableData;
   }

   // Convert current ROI sequence data to a JSON string.
   private String serializeToJSONString() {
      // Instantiate a JSON object that will contain all of the ROI
      // sequence data and will be converted to a string.
      JSONObject toSave = new JSONObject();
      try {
         toSave.put("Rois", roisToJSON(getRois()));
         toSave.put("SequenceData", sequenceTableToJSON());
         toSave.put("SequenceRepeats", GUIUtils.getIntValue(this.sequenceLoopCountTextField_));
      } catch (JSONException ex) {
         ReportingUtils.logError(ex);
      }
      return toSave.toString();
   }

   // Read ROI sequence data from a JSON string and populate window.
   private void deserializeFromJSONString(String jsonString) {
      try {
         JSONObject jsonData = new JSONObject(jsonString);
         JSONArray tableData = jsonData.getJSONArray("SequenceData");
         for (int i = sequenceTableModel_.getRowCount() - 1; i >= 0; --i) {
            sequenceTableModel_.removeRow(i);
         }
         for (int row = 0; row < tableData.length(); ++row) {
            JSONObject rowData = tableData.getJSONObject(row);
            int col = 0;
            Vector<String> newRow = new Vector<String>();
            for (String headerName : headerNames) {
               String value = rowData.getString(headerName);
               newRow.add(value);
               ++col;
            }
            sequenceTableModel_.addRow(newRow);
         }
         sequenceLoopCountTextField_.setText(String.valueOf(jsonData.getInt("SequenceRepeats")));
         Roi[] rois = roisFromJSON(jsonData.getJSONArray("Rois"));
         RoiManager roiManager = Utils.showRoiManager();
         roiManager.runCommand("reset");
         for (Roi roi : rois) {
            roiManager.addRoi(roi);
         }
      } catch (JSONException ex) {
         ReportingUtils.logError(ex);
      }
   }

   // Declare a unique file type for Mosaic sequence descriptor file
   // so that we can return to the last location where a file was loaded
   // or saved.
   private final FileDialogs.FileType fileType = new FileDialogs.FileType("Mosaic",
         "Mosaic Sequence Description", "MosaicSequence.txt", true, "txt");

   // Save the sequence settings to a file.
   private void save() {
      try {
         File theFile = FileDialogs.save(null, "Please save the Mosaic sequence file.", fileType);
         String content = serializeToJSONString();
         TextUtils.writeTextFile(theFile.getAbsolutePath(), content);
      } catch (IOException ex) {
         ReportingUtils.logError(ex);
      }
   }

   // Load the sequence settings from a file.
   private void load() {
      File theFile = FileDialogs
            .openFile(null, "Please choose a Mosaic sequence file to load.", fileType);
      try {
         deserializeFromJSONString(TextUtils.readTextFile(theFile.getAbsolutePath()));
      } catch (IOException ex) {
         ReportingUtils.showError(ex);
      }
   }

   // ## MDA Interfacing
   // Attaching and detaching Mosiac ROI sequences to Micro-Manager's
   // multi-dimensional acquisition sequences.

   // Starts the sequence when the acquisition engine starts.
   public void attachToAcquisition() throws Exception {
      if (getSequenceCount() == 0) {
         throw new Exception(
               "Please upload a sequence to the Mosaic for attaching to "
               + "multi-dimensional acquisition.");
      }
      gui_.acquisitions().attachRunnable(0, 0, 0, 0,
            new Thread(
                  new Runnable() {
                     @Override
                     public void run() {
                        try {
                           runSequence();
                        } catch (Exception e) {
                           ReportingUtils.showError(e);
                        }
                     }
                  }));
   }

   // The acquisition engine will no longer run phototargeting after this
   // is called. Called by the "detach" button.
   public void detachFromAcquisition() {
      gui_.acquisitions().clearRunnables();
   }

   // ## Constructor and main window.

   // Creates a new window, the MosaicSequencingFrame. This frame allows the
   // user to generate sequences of ROIs, and optionally, generate ROIs in
   // a grid pattern.
   public MosaicSequencingFrame(Studio gui, CMMCore core, ProjectorControlForm projectorControlForm,
         SLM mosaicDevice) {
      initComponents();
      gui_ = gui;
      core_ = core;
      projectorControlForm_ = projectorControlForm;
      mosaicDevice_ = mosaicDevice;
      // Get the first available Mosaic device for now.
      mosaicName_ = getMosaicDevices(core_).get(0);
      try {
         mosaicWidth_ = (int) core_.getSLMWidth(mosaicName_);
         mosaicHeight_ = (int) core.getSLMHeight(mosaicName_);
      } catch (Exception ex) {
         // TODO: handle correctly
      }
      sequenceTableModel_ = (DefaultTableModel) sequenceTable_.getModel();
      intensityNames_ = generateIntensityNames();
      // The mosaic executor service makes sure everything happens
      // in sequence, but off the GUI thread.
      mosaicExecutor_ = Executors.newFixedThreadPool(1);

      super.setIconImage(Toolkit.getDefaultToolkit().getImage(
            getClass().getResource("/org/micromanager/icons/microscope.gif")));
      super.setLocation(300, 400);
      WindowPositioning.setUpLocationMemory(this, this.getClass(), null);

      GUIUtils.enforceIntegerTextField(onDurationTextField_, 0, 200000);
      GUIUtils.enforceIntegerTextField(offDurationTextField_, 0, 200000);
      GUIUtils.enforceIntegerTextField(loopCountTextField_, 0, 65535);
      GUIUtils.enforceIntegerTextField(sequenceLoopCountTextField_, 0, 65535);

      GUIUtils.enforceIntegerTextField(numRoisAcrossField_, 1, 30);
      GUIUtils.enforceIntegerTextField(numRoisDownField_, 1, 30);
      GUIUtils.enforceIntegerTextField(this.roiSpacingAcrossField_, 10, 1000);
      GUIUtils.enforceIntegerTextField(this.roiSpacingDownField_, 10, 1000);

      GUIUtils.makeIntoMoveRowUpButton(sequenceTable_, upButton);
      GUIUtils.makeIntoMoveRowDownButton(sequenceTable_, downButton);
      GUIUtils.makeIntoCloneRowButton(sequenceTable_, cloneButton);
      GUIUtils.makeIntoDeleteRowButton(sequenceTable_, deleteButton);
      GUIUtils.tabKeyTraversesTable(sequenceTable_);

      GUIUtils.setClickCountToStartEditing(sequenceTable_, 1);
      GUIUtils.stopEditingOnLosingFocus(sequenceTable_);

      keepTimeSlotIndicesUpToDate(sequenceTableModel_);
      Utils.showRoiManager();
      setupRoiListTable();
   }

   // ## Generated code
   // Warning: the computer-generated code below this line should not be edited
   // by hand. Instead, use the Netbeans Form Editor to make changes.

   /**
    * This method is called from within the constructor to initialize the form. WARNING: Do NOT
    * modify this code. The content of this method is always regenerated by the Form Editor.
    */
   @SuppressWarnings("unchecked")
   // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
   private void initComponents() {

      jLabel5 = new JLabel();
      jPanel1 = new JPanel();
      jLabel2 = new JLabel();
      jLabel3 = new JLabel();
      jLabel4 = new JLabel();
      onDurationTextField_ = new JTextField();
      offDurationTextField_ = new JTextField();
      loopCountTextField_ = new JTextField();
      sequenceTypeComboBox = new JComboBox();
      generateSequenceButton = new JButton();
      jLabel1 = new JLabel();
      jPanel2 = new JPanel();
      jScrollPane2 = new JScrollPane();
      sequenceTable_ = new JTable();
      downButton = new JButton();
      cloneButton = new JButton();
      addTimeSlotButton_ = new JButton();
      upButton = new JButton();
      deleteButton = new JButton();
      jLabel8 = new JLabel();
      detachFromAcquisitionButton_ = new JButton();
      loadButton_ = new JButton();
      uploadButton_ = new JButton();
      jLabel7 = new JLabel();
      sequenceTriggerComboBox = new JComboBox();
      runButton_ = new JButton();
      stopButton_ = new JButton();
      jLabel6 = new JLabel();
      sequenceLoopCountTextField_ = new JTextField();
      attachToAcquisitionButton_ = new JButton();
      saveButton_ = new JButton();
      jSeparator1 = new JSeparator();
      jPanel5 = new JPanel();
      numRoisAcrossLabel_ = new JLabel();
      numRoisDownLabel = new JLabel();
      roisDownSpacingLabel = new JLabel();
      roisAcrossSpacingLabel1 = new JLabel();
      generateROIGridButton_ = new JButton();
      numRoisAcrossField_ = new JTextField();
      roiSpacingAcrossField_ = new JTextField();
      numRoisDownField_ = new JTextField();
      roiSpacingDownField_ = new JTextField();
      roiListPanel_ = new JPanel();
      roiListScrollPane = new JScrollPane();
      roiListTable_ = new JTable();
      jPanel6 = new JPanel();

      jLabel5.setText("jLabel5");

      setTitle("Andor Mosaic 3 ROI Sequencing");
      setResizable(false);

      jPanel1.setBorder(BorderFactory
            .createTitledBorder(BorderFactory.createEtchedBorder(), "Create Sequence",
                  javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION,
                  javax.swing.border.TitledBorder.DEFAULT_POSITION,
                  new java.awt.Font("Tahoma", 1, 11), new java.awt.Color(0, 0, 0))); // NOI18N

      jLabel2.setText("On Duration (ms)");

      jLabel3.setText("Off Duration (ms)");

      jLabel4.setText("Loop Count");

      onDurationTextField_.setText("400");

      offDurationTextField_.setText("50");

      loopCountTextField_.setText("1");

      sequenceTypeComboBox.setModel(new DefaultComboBoxModel(
            new String[]{"Sequential", "Cumulative", "Simultaneous"}));

      generateSequenceButton.setText("Generate Sequence");
      generateSequenceButton.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            generateSequenceButtonActionPerformed(evt);
         }
      });

      jLabel1.setText("Sequence type:");

      GroupLayout jPanel1Layout = new GroupLayout(jPanel1);
      jPanel1.setLayout(jPanel1Layout);
      jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                  .addGroup(jPanel1Layout.createSequentialGroup()
                        .addGap(35, 35, 35)
                        .addGroup(jPanel1Layout
                              .createParallelGroup(GroupLayout.Alignment.LEADING)
                              .addGroup(jPanel1Layout.createSequentialGroup()
                                    .addComponent(jLabel1)
                                    .addPreferredGap(
                                          LayoutStyle.ComponentPlacement.UNRELATED)
                                    .addComponent(sequenceTypeComboBox,
                                          GroupLayout.PREFERRED_SIZE, 127,
                                          GroupLayout.PREFERRED_SIZE))
                              .addGroup(jPanel1Layout.createSequentialGroup()
                                    .addGroup(jPanel1Layout.createParallelGroup(
                                          GroupLayout.Alignment.LEADING)
                                          .addComponent(jLabel3,
                                                GroupLayout.Alignment.TRAILING)
                                          .addComponent(jLabel2,
                                                GroupLayout.Alignment.TRAILING))
                                    .addPreferredGap(
                                          LayoutStyle.ComponentPlacement.UNRELATED)
                                    .addGroup(jPanel1Layout.createParallelGroup(
                                          GroupLayout.Alignment.TRAILING, false)
                                          .addComponent(offDurationTextField_,
                                                GroupLayout.Alignment.LEADING)
                                          .addComponent(onDurationTextField_,
                                                GroupLayout.Alignment.LEADING,
                                                GroupLayout.PREFERRED_SIZE, 57,
                                                GroupLayout.PREFERRED_SIZE))))
                        .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED,
                              GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addGroup(jPanel1Layout
                              .createParallelGroup(GroupLayout.Alignment.LEADING)
                              .addGroup(jPanel1Layout.createSequentialGroup()
                                    .addComponent(jLabel4)
                                    .addGap(18, 18, 18)
                                    .addComponent(loopCountTextField_,
                                          GroupLayout.PREFERRED_SIZE, 57,
                                          GroupLayout.PREFERRED_SIZE))
                              .addComponent(generateSequenceButton))
                        .addGap(97, 97, 97))
      );
      jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                  .addGroup(jPanel1Layout.createSequentialGroup()
                        .addContainerGap()
                        .addGroup(jPanel1Layout
                              .createParallelGroup(GroupLayout.Alignment.BASELINE)
                              .addComponent(sequenceTypeComboBox,
                                    GroupLayout.PREFERRED_SIZE,
                                    GroupLayout.DEFAULT_SIZE,
                                    GroupLayout.PREFERRED_SIZE)
                              .addComponent(jLabel1)
                              .addComponent(loopCountTextField_,
                                    GroupLayout.PREFERRED_SIZE,
                                    GroupLayout.DEFAULT_SIZE,
                                    GroupLayout.PREFERRED_SIZE)
                              .addComponent(jLabel4))
                        .addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED)
                        .addGroup(jPanel1Layout
                              .createParallelGroup(GroupLayout.Alignment.BASELINE)
                              .addComponent(jLabel2)
                              .addComponent(onDurationTextField_,
                                    GroupLayout.PREFERRED_SIZE,
                                    GroupLayout.DEFAULT_SIZE,
                                    GroupLayout.PREFERRED_SIZE)
                              .addComponent(generateSequenceButton))
                        .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel1Layout
                              .createParallelGroup(GroupLayout.Alignment.BASELINE)
                              .addComponent(jLabel3)
                              .addComponent(offDurationTextField_,
                                    GroupLayout.PREFERRED_SIZE,
                                    GroupLayout.DEFAULT_SIZE,
                                    GroupLayout.PREFERRED_SIZE))
                        .addContainerGap(GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
      );

      jPanel2.setBorder(BorderFactory
            .createTitledBorder(BorderFactory.createEtchedBorder(), "Sequence",
                  TitledBorder.DEFAULT_JUSTIFICATION,
                  TitledBorder.DEFAULT_POSITION,
                  new java.awt.Font("Tahoma", 1, 11), new java.awt.Color(0, 0, 0))); // NOI18N
      jPanel2.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N

      sequenceTable_.setModel(new DefaultTableModel(
            new Object[][]{

            },
            new String[]{
                  "Time Slot", "ROIs", "On Duration (ms)", "Off Duration (ms)", "Loops"
            }
      ) {
         Class[] types = new Class[]{
               java.lang.Integer.class, java.lang.String.class, java.lang.Integer.class,
               java.lang.Integer.class, java.lang.Integer.class
         };
         boolean[] canEdit = new boolean[]{
               false, true, true, true, true
         };

         public Class getColumnClass(int columnIndex) {
            return types[columnIndex];
         }

         public boolean isCellEditable(int rowIndex, int columnIndex) {
            return canEdit[columnIndex];
         }
      });
      sequenceTable_.getTableHeader().setResizingAllowed(false);
      sequenceTable_.getTableHeader().setReorderingAllowed(false);
      jScrollPane2.setViewportView(sequenceTable_);
      if (sequenceTable_.getColumnModel().getColumnCount() > 0) {
         sequenceTable_.getColumnModel().getColumn(0).setResizable(false);
         sequenceTable_.getColumnModel().getColumn(0).setPreferredWidth(40);
         sequenceTable_.getColumnModel().getColumn(4).setPreferredWidth(50);
      }

      downButton.setText("Down");

      cloneButton.setText("Clone");

      addTimeSlotButton_.setText("Add");
      addTimeSlotButton_.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            addTimeSlotButtonActionPerformed(evt);
         }
      });

      upButton.setText("Up");

      deleteButton.setText("Delete");

      jLabel8.setText("Time slot:");

      detachFromAcquisitionButton_.setText("Detach");
      detachFromAcquisitionButton_.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            detachFromAcquisitionButtonActionPerformed(evt);
         }
      });

      loadButton_.setText("Load");
      loadButton_.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            loadButtonActionPerformed(evt);
         }
      });

      uploadButton_.setText("Upload Sequence");
      uploadButton_.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            uploadButtonActionPerformed(evt);
         }
      });

      jLabel7.setText("Sequence Trigger:");

      sequenceTriggerComboBox.setModel(new DefaultComboBoxModel(
            new String[]{"Sequence Start", "External Start", "External Advance",
                  "External Frame Bulb"}));
      sequenceTriggerComboBox.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            sequenceTriggerComboBoxActionPerformed(evt);
         }
      });

      runButton_.setText("Run");
      runButton_.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            runButtonActionPerformed(evt);
         }
      });

      stopButton_.setText("Stop");
      stopButton_.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            stopButtonActionPerformed(evt);
         }
      });

      jLabel6.setText("Sequence Repeats:");

      sequenceLoopCountTextField_.setText("1");

      attachToAcquisitionButton_.setText("Attach to Acquisition");
      attachToAcquisitionButton_.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            attachToAcquisitionButtonActionPerformed(evt);
         }
      });

      saveButton_.setText("Save");
      saveButton_.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            saveButtonActionPerformed(evt);
         }
      });

      GroupLayout jPanel2Layout = new GroupLayout(jPanel2);
      jPanel2.setLayout(jPanel2Layout);
      jPanel2Layout.setHorizontalGroup(
            jPanel2Layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                  .addGroup(jPanel2Layout.createSequentialGroup()
                        .addContainerGap()
                        .addGroup(jPanel2Layout
                              .createParallelGroup(GroupLayout.Alignment.LEADING)
                              .addGroup(jPanel2Layout.createSequentialGroup()
                                    .addGroup(jPanel2Layout.createParallelGroup(
                                          GroupLayout.Alignment.LEADING)
                                          .addComponent(jLabel6)
                                          .addGroup(jPanel2Layout.createSequentialGroup()
                                                .addComponent(runButton_)
                                                .addPreferredGap(
                                                      LayoutStyle.ComponentPlacement.RELATED)
                                                .addComponent(stopButton_)))
                                    .addPreferredGap(
                                          LayoutStyle.ComponentPlacement.RELATED)
                                    .addGroup(jPanel2Layout.createParallelGroup(
                                          GroupLayout.Alignment.LEADING)
                                          .addGroup(jPanel2Layout.createSequentialGroup()
                                                .addGap(10, 10, 10)
                                                .addComponent(attachToAcquisitionButton_)
                                                .addPreferredGap(
                                                      LayoutStyle.ComponentPlacement.RELATED)
                                                .addComponent(detachFromAcquisitionButton_)
                                                .addPreferredGap(
                                                      LayoutStyle.ComponentPlacement.RELATED,
                                                      GroupLayout.DEFAULT_SIZE,
                                                      Short.MAX_VALUE)
                                                .addComponent(loadButton_)
                                                .addPreferredGap(
                                                      LayoutStyle.ComponentPlacement.RELATED)
                                                .addComponent(saveButton_))
                                          .addGroup(jPanel2Layout.createSequentialGroup()
                                                .addComponent(sequenceLoopCountTextField_,
                                                      GroupLayout.PREFERRED_SIZE, 47,
                                                      GroupLayout.PREFERRED_SIZE)
                                                .addGap(18, 18, 18)
                                                .addComponent(jLabel7)
                                                .addPreferredGap(
                                                      LayoutStyle.ComponentPlacement.RELATED,
                                                      GroupLayout.DEFAULT_SIZE,
                                                      Short.MAX_VALUE)
                                                .addComponent(sequenceTriggerComboBox,
                                                      GroupLayout.PREFERRED_SIZE, 106,
                                                      GroupLayout.PREFERRED_SIZE)
                                                .addGap(18, 18, 18)
                                                .addComponent(uploadButton_)))
                                    .addGap(20, 20, 20))
                              .addGroup(jPanel2Layout.createSequentialGroup()
                                    .addGroup(jPanel2Layout.createParallelGroup(
                                          GroupLayout.Alignment.LEADING)
                                          .addComponent(jSeparator1,
                                                GroupLayout.PREFERRED_SIZE, 525,
                                                GroupLayout.PREFERRED_SIZE)
                                          .addComponent(jScrollPane2,
                                                GroupLayout.PREFERRED_SIZE, 525,
                                                GroupLayout.PREFERRED_SIZE)
                                          .addGroup(jPanel2Layout.createSequentialGroup()
                                                .addComponent(jLabel8)
                                                .addPreferredGap(
                                                      LayoutStyle.ComponentPlacement.UNRELATED)
                                                .addComponent(addTimeSlotButton_)
                                                .addPreferredGap(
                                                      LayoutStyle.ComponentPlacement.RELATED)
                                                .addComponent(cloneButton)
                                                .addPreferredGap(
                                                      LayoutStyle.ComponentPlacement.RELATED)
                                                .addComponent(deleteButton)
                                                .addPreferredGap(
                                                      LayoutStyle.ComponentPlacement.RELATED)
                                                .addComponent(upButton)
                                                .addPreferredGap(
                                                      LayoutStyle.ComponentPlacement.RELATED)
                                                .addComponent(downButton)))
                                    .addContainerGap(GroupLayout.DEFAULT_SIZE,
                                          Short.MAX_VALUE))))
      );
      jPanel2Layout.setVerticalGroup(
            jPanel2Layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                  .addGroup(jPanel2Layout.createSequentialGroup()
                        .addContainerGap()
                        .addComponent(jScrollPane2, GroupLayout.PREFERRED_SIZE, 197,
                              GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel2Layout
                              .createParallelGroup(GroupLayout.Alignment.BASELINE)
                              .addComponent(cloneButton)
                              .addComponent(deleteButton)
                              .addComponent(upButton)
                              .addComponent(downButton)
                              .addComponent(addTimeSlotButton_)
                              .addComponent(jLabel8))
                        .addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(jSeparator1, GroupLayout.PREFERRED_SIZE, 5,
                              GroupLayout.PREFERRED_SIZE)
                        .addGap(2, 2, 2)
                        .addGroup(jPanel2Layout
                              .createParallelGroup(GroupLayout.Alignment.BASELINE)
                              .addComponent(uploadButton_)
                              .addComponent(jLabel7)
                              .addComponent(sequenceTriggerComboBox,
                                    GroupLayout.PREFERRED_SIZE,
                                    GroupLayout.DEFAULT_SIZE,
                                    GroupLayout.PREFERRED_SIZE)
                              .addComponent(jLabel6)
                              .addComponent(sequenceLoopCountTextField_,
                                    GroupLayout.PREFERRED_SIZE,
                                    GroupLayout.DEFAULT_SIZE,
                                    GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED)
                        .addGroup(jPanel2Layout
                              .createParallelGroup(GroupLayout.Alignment.BASELINE)
                              .addComponent(runButton_)
                              .addComponent(attachToAcquisitionButton_)
                              .addComponent(stopButton_)
                              .addComponent(loadButton_)
                              .addComponent(detachFromAcquisitionButton_)
                              .addComponent(saveButton_))
                        .addContainerGap(GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
      );

      jPanel5.setBorder(BorderFactory
            .createTitledBorder(BorderFactory.createEtchedBorder(), "Create ROI Grid",
                  TitledBorder.DEFAULT_JUSTIFICATION,
                  TitledBorder.DEFAULT_POSITION,
                  new java.awt.Font("Tahoma", 1, 11), new java.awt.Color(0, 0, 0))); // NOI18N

      numRoisAcrossLabel_.setText("Number across:");

      numRoisDownLabel.setText("Number down:");

      roisDownSpacingLabel.setText("Spacing:");

      roisAcrossSpacingLabel1.setText("Spacing:");

      generateROIGridButton_.setText("Generate ROI Grid");
      generateROIGridButton_.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            generateROIGridButtonActionPerformed(evt);
         }
      });

      numRoisAcrossField_.setText("1");

      roiSpacingAcrossField_.setText("100");

      numRoisDownField_.setText("1");

      roiSpacingDownField_.setText("100");

      GroupLayout jPanel5Layout = new GroupLayout(jPanel5);
      jPanel5.setLayout(jPanel5Layout);
      jPanel5Layout.setHorizontalGroup(
            jPanel5Layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                  .addGroup(jPanel5Layout.createSequentialGroup()
                        .addGap(57, 57, 57)
                        .addGroup(jPanel5Layout
                              .createParallelGroup(GroupLayout.Alignment.TRAILING)
                              .addComponent(generateROIGridButton_)
                              .addGroup(jPanel5Layout.createSequentialGroup()
                                    .addGroup(jPanel5Layout.createParallelGroup(
                                          GroupLayout.Alignment.TRAILING)
                                          .addComponent(roisAcrossSpacingLabel1)
                                          .addComponent(roisDownSpacingLabel)
                                          .addComponent(numRoisDownLabel)
                                          .addComponent(numRoisAcrossLabel_))
                                    .addPreferredGap(
                                          LayoutStyle.ComponentPlacement.UNRELATED)
                                    .addGroup(jPanel5Layout.createParallelGroup(
                                          GroupLayout.Alignment.LEADING, false)
                                          .addComponent(roiSpacingAcrossField_)
                                          .addComponent(numRoisAcrossField_)
                                          .addComponent(numRoisDownField_)
                                          .addComponent(roiSpacingDownField_,
                                                GroupLayout.PREFERRED_SIZE, 53,
                                                GroupLayout.PREFERRED_SIZE))))
                        .addContainerGap(70, Short.MAX_VALUE))
      );
      jPanel5Layout.setVerticalGroup(
            jPanel5Layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                  .addGroup(jPanel5Layout.createSequentialGroup()
                        .addContainerGap(GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addGroup(jPanel5Layout
                              .createParallelGroup(GroupLayout.Alignment.BASELINE)
                              .addComponent(numRoisAcrossLabel_)
                              .addComponent(numRoisAcrossField_,
                                    GroupLayout.PREFERRED_SIZE,
                                    GroupLayout.DEFAULT_SIZE,
                                    GroupLayout.PREFERRED_SIZE))
                        .addGap(3, 3, 3)
                        .addGroup(jPanel5Layout
                              .createParallelGroup(GroupLayout.Alignment.BASELINE)
                              .addComponent(roisAcrossSpacingLabel1)
                              .addComponent(roiSpacingAcrossField_,
                                    GroupLayout.PREFERRED_SIZE,
                                    GroupLayout.DEFAULT_SIZE,
                                    GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel5Layout
                              .createParallelGroup(GroupLayout.Alignment.BASELINE)
                              .addComponent(numRoisDownLabel)
                              .addComponent(numRoisDownField_,
                                    GroupLayout.PREFERRED_SIZE,
                                    GroupLayout.DEFAULT_SIZE,
                                    GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel5Layout
                              .createParallelGroup(GroupLayout.Alignment.BASELINE)
                              .addComponent(roisDownSpacingLabel)
                              .addComponent(roiSpacingDownField_,
                                    GroupLayout.PREFERRED_SIZE,
                                    GroupLayout.DEFAULT_SIZE,
                                    GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(generateROIGridButton_)
                        .addContainerGap())
      );

      roiListPanel_.setBorder(BorderFactory
            .createTitledBorder(BorderFactory.createEtchedBorder(), "ROI list",
                  TitledBorder.DEFAULT_JUSTIFICATION,
                  TitledBorder.DEFAULT_POSITION,
                  new java.awt.Font("Tahoma", 1, 11))); // NOI18N

      roiListTable_.setModel(new DefaultTableModel(
            new Object[][]{
                  {null, null, null, null},
                  {null, null, null, null},
                  {null, null, null, null},
                  {null, null, null, null}
            },
            new String[]{
                  "Roi #", "Name", "Mode", "Intensity"
            }
      ) {
         boolean[] canEdit = new boolean[]{
               false, false, true, true
         };

         public boolean isCellEditable(int rowIndex, int columnIndex) {
            return canEdit[columnIndex];
         }
      });
      roiListTable_.getTableHeader().setReorderingAllowed(false);
      roiListScrollPane.setViewportView(roiListTable_);

      GroupLayout jPanel6Layout = new GroupLayout(jPanel6);
      jPanel6.setLayout(jPanel6Layout);
      jPanel6Layout.setHorizontalGroup(
            jPanel6Layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                  .addGap(0, 0, Short.MAX_VALUE)
      );
      jPanel6Layout.setVerticalGroup(
            jPanel6Layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                  .addGap(0, 156, Short.MAX_VALUE)
      );

      GroupLayout roiListPanelLayout = new GroupLayout(roiListPanel_);
      roiListPanel_.setLayout(roiListPanelLayout);
      roiListPanelLayout.setHorizontalGroup(
            roiListPanelLayout.createParallelGroup(GroupLayout.Alignment.LEADING)
                  .addGroup(roiListPanelLayout.createSequentialGroup()
                        .addContainerGap()
                        .addComponent(roiListScrollPane, GroupLayout.DEFAULT_SIZE, 239,
                              Short.MAX_VALUE)
                        .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jPanel6, GroupLayout.DEFAULT_SIZE,
                              GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
      );
      roiListPanelLayout.setVerticalGroup(
            roiListPanelLayout.createParallelGroup(GroupLayout.Alignment.LEADING)
                  .addGroup(roiListPanelLayout.createSequentialGroup()
                        .addGroup(roiListPanelLayout
                              .createParallelGroup(GroupLayout.Alignment.LEADING)
                              .addComponent(jPanel6, GroupLayout.PREFERRED_SIZE,
                                    GroupLayout.DEFAULT_SIZE,
                                    GroupLayout.PREFERRED_SIZE)
                              .addGroup(roiListPanelLayout.createSequentialGroup()
                                    .addContainerGap()
                                    .addComponent(roiListScrollPane,
                                          GroupLayout.PREFERRED_SIZE, 122,
                                          GroupLayout.PREFERRED_SIZE)))
                        .addContainerGap(GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
      );

      GroupLayout layout = new GroupLayout(getContentPane());
      getContentPane().setLayout(layout);
      layout.setHorizontalGroup(
            layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                  .addGroup(layout.createSequentialGroup()
                        .addContainerGap()
                        .addGroup(
                              layout.createParallelGroup(GroupLayout.Alignment.TRAILING,
                                    false)
                                    .addGroup(GroupLayout.Alignment.LEADING,
                                          layout.createSequentialGroup()
                                                .addComponent(roiListPanel_,
                                                      GroupLayout.PREFERRED_SIZE,
                                                      GroupLayout.DEFAULT_SIZE,
                                                      GroupLayout.PREFERRED_SIZE)
                                                .addPreferredGap(
                                                      LayoutStyle.ComponentPlacement.UNRELATED)
                                                .addComponent(jPanel5,
                                                      GroupLayout.DEFAULT_SIZE,
                                                      GroupLayout.DEFAULT_SIZE,
                                                      Short.MAX_VALUE))
                                    .addComponent(jPanel1, GroupLayout.DEFAULT_SIZE,
                                          GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                    .addComponent(jPanel2, GroupLayout.PREFERRED_SIZE,
                                          554, Short.MAX_VALUE))
                        .addContainerGap(GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
      );
      layout.setVerticalGroup(
            layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                  .addGroup(layout.createSequentialGroup()
                        .addContainerGap()
                        .addGroup(
                              layout.createParallelGroup(GroupLayout.Alignment.LEADING,
                                    false)
                                    .addComponent(roiListPanel_,
                                          GroupLayout.PREFERRED_SIZE, 0,
                                          Short.MAX_VALUE)
                                    .addComponent(jPanel5, GroupLayout.DEFAULT_SIZE,
                                          GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                        .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jPanel1, GroupLayout.PREFERRED_SIZE,
                              GroupLayout.DEFAULT_SIZE,
                              GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jPanel2, GroupLayout.PREFERRED_SIZE,
                              GroupLayout.DEFAULT_SIZE,
                              GroupLayout.PREFERRED_SIZE)
                        .addContainerGap(GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
      );

      jPanel2.getAccessibleContext().setAccessibleName("Sequences");

      pack();
   } // </editor-fold>//GEN-END:initComponents

   private void generateSequenceButtonActionPerformed(
         java.awt.event.ActionEvent evt) { //GEN-FIRST:event_generateSequenceButtonActionPerformed
      try {
         generateNewSequence();
      } catch (Exception e) {
         ReportingUtils.showError(e);
      }
   } //GEN-LAST:event_generateSequenceButtonActionPerformed

   private void runButtonActionPerformed(
         java.awt.event.ActionEvent evt) { //GEN-FIRST:event_runButton_ActionPerformed
      try {
         runSequence();
      } catch (Exception e) {
         ReportingUtils.showError(e);
      }
   } //GEN-LAST:event_runButton_ActionPerformed

   private void stopButtonActionPerformed(
         java.awt.event.ActionEvent evt) { //GEN-FIRST:event_stopButton_ActionPerformed
      stopSequence();
   } //GEN-LAST:event_stopButton_ActionPerformed

   private void addTimeSlotButtonActionPerformed(
         java.awt.event.ActionEvent evt) { //GEN-FIRST:event_addTimeSlotButton_ActionPerformed
      addTimeSlot();
   } //GEN-LAST:event_addTimeSlotButton_ActionPerformed

   private void uploadButtonActionPerformed(
         java.awt.event.ActionEvent evt) { //GEN-FIRST:event_uploadButton_ActionPerformed
      try {
         uploadSequence();
      } catch (Exception e) {
         ReportingUtils.showError(e);
      }
   } //GEN-LAST:event_uploadButton_ActionPerformed

   private void generateROIGridButtonActionPerformed(
         java.awt.event.ActionEvent evt) { //GEN-FIRST:event_generateROIGridButton_ActionPerformed
      generateRoiGrid();
   } //GEN-LAST:event_generateROIGridButton_ActionPerformed

   private void loadButtonActionPerformed(
         java.awt.event.ActionEvent evt) { //GEN-FIRST:event_loadButton_ActionPerformed
      load();
   } //GEN-LAST:event_loadButton_ActionPerformed

   private void saveButtonActionPerformed(
         java.awt.event.ActionEvent evt) { //GEN-FIRST:event_saveButton_ActionPerformed
      save();
   } //GEN-LAST:event_saveButton_ActionPerformed

   private void attachToAcquisitionButtonActionPerformed(
         java.awt.event.ActionEvent evt) {
      try {
         attachToAcquisition();
      } catch (Exception e) {
         ReportingUtils.showError(e);
      }
   } //GEN-LAST:event_attachToAcquisitionButton_ActionPerformed

   private void detachFromAcquisitionButtonActionPerformed(
         java.awt.event.ActionEvent evt) {
      detachFromAcquisition();
   } //GEN-LAST:event_detachFromAcquisitionButton_ActionPerformed

   private void sequenceTriggerComboBoxActionPerformed(
         java.awt.event.ActionEvent evt) {//GEN-FIRST:event_sequenceTriggerComboBoxActionPerformed
      // TODO add your handling code here:
   } //GEN-LAST:event_sequenceTriggerComboBoxActionPerformed


   // Variables declaration - do not modify//GEN-BEGIN:variables
   private JButton addTimeSlotButton_;
   private JButton attachToAcquisitionButton_;
   private JButton cloneButton;
   private JButton deleteButton;
   private JButton detachFromAcquisitionButton_;
   private JButton downButton;
   private JButton generateROIGridButton_;
   private JButton generateSequenceButton;
   private JLabel jLabel1;
   private JLabel jLabel2;
   private JLabel jLabel3;
   private JLabel jLabel4;
   private JLabel jLabel5;
   private JLabel jLabel6;
   private JLabel jLabel7;
   private JLabel jLabel8;
   private JPanel jPanel1;
   private JPanel jPanel2;
   private JPanel jPanel5;
   private JPanel jPanel6;
   private JScrollPane jScrollPane2;
   private JSeparator jSeparator1;
   private JButton loadButton_;
   private JTextField loopCountTextField_;
   private JTextField numRoisAcrossField_;
   private JLabel numRoisAcrossLabel_;
   private JTextField numRoisDownField_;
   private JLabel numRoisDownLabel;
   private JTextField offDurationTextField_;
   private JTextField onDurationTextField_;
   private JPanel roiListPanel_;
   private JScrollPane roiListScrollPane;
   private JTable roiListTable_;
   private JTextField roiSpacingAcrossField_;
   private JTextField roiSpacingDownField_;
   private JLabel roisAcrossSpacingLabel1;
   private JLabel roisDownSpacingLabel;
   private JButton runButton_;
   private JButton saveButton_;
   private JTextField sequenceLoopCountTextField_;
   private JTable sequenceTable_;
   private JComboBox sequenceTriggerComboBox;
   private JComboBox sequenceTypeComboBox;
   private JButton stopButton_;
   private JButton upButton;
   private JButton uploadButton_;
   // End of variables declaration//GEN-END:variables
}
