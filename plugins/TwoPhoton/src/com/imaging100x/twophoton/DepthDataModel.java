//
// Two-photon plugin module for micro-manager
//
// COPYRIGHT:     Nenad Amodaj 2011, 100X Imaging Inc 2009
//
// LICENSE:       This library is free software; you can redistribute it and/or
//                modify it under the terms of the GNU Lesser General Public
//                License as published by the Free Software Foundation.
//                
//                You should have received a copy of the GNU Lesser General Public
//                License along with the source distribution; if not, write to
//                the Free Software Foundation, Inc., 59 Temple Place, Suite 330,
//                Boston, MA  02111-1307  USA
//
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.  
//                
// AUTHOR:        Nenad Amodaj

package com.imaging100x.twophoton;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.Collections;
import java.util.Vector;

import javax.swing.JOptionPane;
import javax.swing.table.AbstractTableModel;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class DepthDataModel extends AbstractTableModel {
   private static final long serialVersionUID = 1L;
   private String[] columnNames_ = {"Z [um]", "EOM [V]"};
   //private Vector<DepthSetting> settings_;
   private Vector<Vector<DepthSetting>> lists_;
   private int currentList_;
   
   private static final String SIZE = "SIZE";
   private static final String Z = "Z";
   private static final String EOM1 = "EOM1";
   private static final String EOM2 = "EOM2";
   private static final String PMT_LIST = "PMTS";
   private static final String PMT_V = "V";
   private static final String PMT_NAME = "NAME";
   private static final String MULTI_LIST = "LISTS";
   
   public DepthDataModel() {
      lists_ = new Vector<Vector<DepthSetting>>();
      Vector<DepthSetting> list0 = new Vector<DepthSetting>();
      Vector<DepthSetting> list1 = new Vector<DepthSetting>();
      lists_.add(list0);
      lists_.add(list1);
      currentList_ = 0;
   }
   
   public void setValueAt(Object value, int row, int col) {
      if (col == 1) {
         
         // update value in the core
         try {
            //core_.setProperty(names_.get(row), "Volts", (String)value);
         } catch (Exception e) {
            handleError(e);
         }
         
         fireTableCellUpdated(row, col);
      }
   }

   public int getColumnCount() {
      return columnNames_.length;
   }

   public int getRowCount() {
      return lists_.get(currentList_).size();
   }

   public Object getValueAt(int rowIndex, int columnIndex) {
      if (columnIndex == 0) {
         return Double.toString(lists_.get(currentList_).get(rowIndex).z);
      } else if (columnIndex == 1) {
         DepthSetting s = lists_.get(currentList_).get(rowIndex);
         String eom = new String(Double.toString(s.eomVolts1_));
         eom += ("," + s.eomVolts2_);
         
         return eom;
      } else
         return null;
   }
 
   public String getColumnName(int column) {
      return columnNames_[column];
   }

   public boolean isCellEditable(int nRow, int nCol) {
      return false;
   }
   
   private void handleError(Exception e) {
      e.printStackTrace();
      JOptionPane.showMessageDialog(null, e.getMessage());
   }
   
   public void refresh() {
      fireTableDataChanged();
   }
   
   public void setDepthSetting(DepthSetting ds) {
      lists_.get(currentList_).add(ds);
      Collections.sort(lists_.get(currentList_));
      
      // re-calculate deltaZ
      double minZ = lists_.get(currentList_).lastElement().z;
      for (int i=0; i<lists_.get(currentList_).size(); i++)
         lists_.get(currentList_).get(i).deltaZ = lists_.get(currentList_).get(i).z - minZ;
      fireTableDataChanged();    
   }
   
   public void deleteDepthSetting(int idx) {
      if (idx >= 0 && idx < lists_.get(currentList_).size())
         lists_.get(currentList_).remove(idx);
      fireTableDataChanged();
   }
   
   DepthSetting getDepthSetting(int idx) {
      return lists_.get(currentList_).get(idx);
   }
   
   DepthSetting[] getAllDepthSettings() {
      DepthSetting[] da = new DepthSetting[0];
      return lists_.get(currentList_).toArray(da);
   }

   public void clear() {
      lists_.get(currentList_).clear();
      fireTableDataChanged();
   }

   public void setTop() {
      // TODO Auto-generated method stub
      
   }
   
   public DepthSetting getInterpolatedDepthSetting(double z) throws TwoPhotonException {
      // find matching entry in the list
      
      if (lists_.get(currentList_).size() < 1)
         throw new TwoPhotonException("Depth List Empty!");
      
      DepthSetting ds = new DepthSetting();
      ds.z = z;
      
      int idx = Collections.binarySearch(lists_.get(currentList_), ds);
   
      System.out.println("Index: " + idx + "Z: " + z);
      
      if (idx >= 0)
         return lists_.get(currentList_).get(idx);
      
      else if (idx == -1) {
         return lists_.get(currentList_).firstElement();
      } else if (-idx == (lists_.get(currentList_).size() + 1)) {
         return lists_.get(currentList_).lastElement();
      } else {
         DepthSetting dsLow = lists_.get(currentList_).get(-idx - 1);
         DepthSetting dsHigh = lists_.get(currentList_).get(-idx - 2);
         ds.resizePMT(dsLow.pmts.length);
         double zFactor = (z - dsLow.z)/(dsHigh.z - dsLow.z);
         ds.eomVolts1_ = linearInterpolation(dsLow.eomVolts1_, dsHigh.eomVolts1_, zFactor);
         System.out.println("VL=" + dsLow.eomVolts1_ + ", VH=" + dsHigh.eomVolts1_ + ", Z=" + z + ", ZL=" + dsLow.z + ", ZH=" + dsHigh.z);
         System.out.println("V=" + ds.eomVolts1_);
         ds.eomVolts2_ = linearInterpolation(dsLow.eomVolts2_, dsHigh.eomVolts2_, zFactor);
         for (int i=0; i<ds.pmts.length; i++) {
            ds.pmts[i].name = new String(dsLow.pmts[i].name);
            ds.pmts[i].volts = linearInterpolation(dsLow.pmts[i].volts, dsHigh.pmts[i].volts, zFactor);
         }
         return ds;
      }
   }

   public double linearInterpolation(double vlow, double vhigh, double zFactor) {
      return vlow + (vhigh - vlow) * zFactor;
   }
   
   public void setCurrentListIndex(int idx) throws TwoPhotonException {
      if (!(idx==0 || idx==1))
         throw new TwoPhotonException("Invalid depth list index:" + idx );
      
      currentList_ = idx;
      fireTableStructureChanged();
   }
   
   public int getCurrentListIndex() {
      return currentList_;
   }
   
   void save(String path) throws TwoPhotonException {

      // serialize the settings list
      JSONArray multiList = new JSONArray();
      try {
         for (int k=0; k<2; k++) {
            JSONArray depthList = new JSONArray();
            for (int i = 0; i < lists_.get(k).size(); i++) {
               DepthSetting ds = lists_.get(k).get(i);
               JSONObject depthSetting = new JSONObject();
               depthSetting.put(EOM1, ds.eomVolts1_);
               depthSetting.put(EOM2, ds.eomVolts2_);
               depthSetting.put(Z, ds.z);
               JSONArray pmtList = new JSONArray();
               for (int j = 0; j < ds.pmts.length; j++) {
                  JSONObject pmt = new JSONObject();
                  pmt.put(PMT_NAME, ds.pmts[j].name);
                  pmt.put(PMT_V, ds.pmts[j].volts);
                  pmtList.put(pmt);
               }
               depthSetting.put(PMT_LIST, pmtList);
               depthList.put(depthSetting);
            }
            multiList.put(depthList);
         }
      } catch (JSONException e) {
         throw new TwoPhotonException(e.getMessage());
      }

      // save it to the file
      File f = new File(path);
      try {
         String serList = multiList.toString(3);
         FileWriter fw = new FileWriter(f);
         fw.write(serList);
         fw.close();
      } catch (Exception e) {
         throw new TwoPhotonException(e.getMessage());
      }

   }
   
   void load(String path) throws TwoPhotonException {
      File f = new File(path);
      StringBuffer contents = new StringBuffer();
      try {
         
         // load file
         BufferedReader input = new BufferedReader(new FileReader(f));
         String line = null;
         while ((line = input.readLine()) != null) {
            contents.append(line);
            contents.append(System.getProperty("line.separator"));
         }
         JSONArray multiList = new JSONArray(contents.toString());
         if (multiList.length() != 2)
            throw new TwoPhotonException("Invalid number of depth lists: " + multiList.length());
         
         // create new list
         lists_.get(0).clear();
         lists_.get(1).clear();
         
         for (int k=0; k<2; k++){
            JSONArray list = multiList.getJSONArray(k);
            for (int i=0; i<list.length(); i++) {
               JSONObject setting = list.getJSONObject(i);
               DepthSetting s = new DepthSetting();
               s.z = setting.getDouble(Z);
               s.eomVolts1_ = setting.getDouble(EOM1);
               s.eomVolts2_ = setting.getDouble(EOM2);
               JSONArray pmtList = setting.getJSONArray(PMT_LIST);
               s.resizePMT(pmtList.length());
               for (int j=0; j<pmtList.length(); j++) {
                  JSONObject pmt = pmtList.getJSONObject(j);
                  s.pmts[j].name = pmt.getString(PMT_NAME);
                  s.pmts[j].volts = pmt.getDouble(PMT_V);
               }
               lists_.get(k).add(s);
            }
         }
         fireTableStructureChanged();

      } catch (Exception e) {
         throw new TwoPhotonException(e.getMessage());
      }
   }
}
