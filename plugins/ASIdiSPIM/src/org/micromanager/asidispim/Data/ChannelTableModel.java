///////////////////////////////////////////////////////////////////////////////
//FILE:          ChannelTableModel.java
//PROJECT:       Micro-Manager 
//SUBSYSTEM:     ASIdiSPIM plugin
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nico Stuurman, Jon Daniels
//
// COPYRIGHT:    University of California, San Francisco, & ASI, 2013
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
//
package org.micromanager.asidispim.Data;

import java.util.ArrayList;
import java.util.List;

import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.AbstractTableModel;

import org.micromanager.asidispim.MultiChannelSubPanel;
import org.micromanager.utils.ReportingUtils;


/**
 * Representation of information in channel table of 
 * diSPIM plugin.  Based on org.micromanager.utils.ChannelSpec.java.
 * Handles saving preferences to registry assuming column/row don't change.
 * @author Jon
 * @author Nico
 */
@SuppressWarnings("serial")
public class ChannelTableModel extends AbstractTableModel {
   public static final String[] columnNames = {"Use?", "Preset"};
   public static final int columnIndex_useChannel = 0;
   public static final int columnIndex_config = 1;
   private final ArrayList<ChannelSpec> channels_;
   private final Prefs prefs_;
   private final String prefNode_;
   private final MultiChannelSubPanel multiChannelSubPanel_;  // needed for update duration callback


   public ChannelTableModel(Prefs prefs, String prefNode, String channelGroup,
         MultiChannelSubPanel multiChannelSubPanel) {
      channels_ = new ArrayList<ChannelSpec>();
      prefs_ = prefs;
      prefNode_ = prefNode;
      setChannelGroup(channelGroup);
      multiChannelSubPanel_ = multiChannelSubPanel;
      this.addTableModelListener(new TableModelListener() {
         @Override
         public void tableChanged(TableModelEvent arg0) {
            multiChannelSubPanel_.updateDurationLabels();
         }
      });
   } //constructor
   
   public final void setChannelGroup(String channelGroup) {
      channels_.clear();
      int nrChannels = prefs_.getInt(prefNode_ + "_" + channelGroup, 
              Prefs.Keys.NRCHANNELROWS, 1);
      for (int i=0; i < nrChannels; i++) {
         addChannel(channelGroup);
      }
   }

   public final void addChannel(String channelGroup) {
      String prefKey = prefNode_ + "_" + channelGroup + "_" + channels_.size();
      addNewChannel(new ChannelSpec(
               prefs_.getBoolean(prefKey, 
                       Prefs.Keys.CHANNEL_USE_CHANNEL, false),
               channelGroup,
               prefs_.getString(prefKey, 
                       Prefs.Keys.CHANNEL_CONFIG, "") )) ;
      prefs_.putInt(prefNode_ + "_" + channelGroup, Prefs.Keys.NRCHANNELROWS, 
              channels_.size());
   }
   
   /**
    *  Removes the specified row from the channel table 
    * @param i - 0-based row number of channel to be removed
   */
   public void removeChannel(int i) {
      String prefKey = prefNode_ + "_" + channels_.get(i).group_;
      channels_.remove(i);
      prefs_.putInt(prefKey, Prefs.Keys.NRCHANNELROWS, channels_.size());
   }
   
   @Override
   public int getColumnCount() {
      return columnNames.length;
   }

   @Override
   public String getColumnName(int columnIndex) {
      return columnNames[columnIndex];
   }

   @SuppressWarnings({ "unchecked", "rawtypes" })
   @Override
   public Class getColumnClass(int columnIndex) {
      return getValueAt(0, columnIndex).getClass();
   }

   @Override
   public int getRowCount() {
      return (channels_ == null) ? 0 : channels_.size();
   }
   
   @Override
   public boolean isCellEditable(int rowIndex, int columnIndex) {
      return true;
   }

   @Override
   public void setValueAt(Object value, int rowIndex, int columnIndex) {
      ChannelSpec channel = channels_.get(rowIndex);
      String prefNode = prefNode_ + "_" + channel.group_ + "_" + rowIndex;
      switch (columnIndex) {
      case columnIndex_useChannel:
         if (value instanceof Boolean) {
            boolean val = (Boolean) value;
            channel.useChannel_ = val;
            prefs_.putBoolean(prefNode, Prefs.Keys.CHANNEL_USE_CHANNEL, (Boolean) val);
         }
         break;
      case columnIndex_config:
         if (value instanceof String) {
            String val = (String) value;
            channel.config_ = val;
            prefs_.putString(prefNode, Prefs.Keys.CHANNEL_CONFIG, val);
         }
         break;
      }
      fireTableCellUpdated(rowIndex, columnIndex);
   }

   @Override
   public Object getValueAt(int rowIndex, int columnIndex) {
      ChannelSpec channel = channels_.get(rowIndex);
      switch (columnIndex) {
      case columnIndex_useChannel:
         return channel.useChannel_;
      case columnIndex_config:
         return channel.config_;
      default: 
         ReportingUtils.logError("ColorTableModel getValuAt() didn't match");
         return null;
      }
   }
   
   public final void addNewChannel(ChannelSpec channel) {
      String prefNode = prefNode_ + "_" + channel.group_ + "_" + channels_.size();
      prefs_.putBoolean(prefNode, Prefs.Keys.CHANNEL_USE_CHANNEL, channel.useChannel_);
      prefs_.putString(prefNode, Prefs.Keys.CHANNEL_CONFIG, channel.config_);
      prefs_.putInt(prefNode_ + "_" + channel.group_, Prefs.Keys.NRCHANNELROWS, 
              channels_.size());
      channels_.add(channel);
   }
   
   /**
    * Returns array of channels that are currently set be "used".
    * Returns them in order that they are in the table, omitting unused ones.
    * @return 
    */
   public ChannelSpec[] getUsedChannels() {
      List<ChannelSpec> result = new ArrayList<ChannelSpec>();
      for (ChannelSpec ch : channels_) {
         if (ch.useChannel_) {
            result.add(ch);
         }
      }
      return result.toArray(new ChannelSpec[0]);
   }

}

