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
package org.micromanager.asidispim.data;

import java.util.ArrayList;
import java.util.List;

import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.AbstractTableModel;
import org.micromanager.Studio;

import org.micromanager.asidispim.MultiChannelSubPanel;
import org.micromanager.internal.utils.ReportingUtils;


/**
 * Representation of information in channel table of 
 * diSPIM plugin.  Based on org.micromanager.utils.ChannelSpec.java.
 * Handles saving preferences to registry assuming column/row don't change.
 * @author Jon
 * @author Nico
 */
@SuppressWarnings("serial")
public class ChannelTableModel extends AbstractTableModel {
   public static final String[] COLUMNNAMES = {"Use?", "Preset"};
   public static final int COLUMNINDEX_USECHANNEL = 0;
   public static final int COLUMNINDEX_CONFIG = 1;
   private final Studio gui_;
   private final ArrayList<ChannelSpec> channels_;
   private final Prefs prefs_;
   private final String prefNode_;
   private final MultiChannelSubPanel multiChannelSubPanel_;  // needed for update duration callback


   public ChannelTableModel(Studio gui, Prefs prefs, String prefNode, 
           String channelGroup, MultiChannelSubPanel multiChannelSubPanel) {
      channels_ = new ArrayList<ChannelSpec>();
      gui_ = gui;
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
      // Check if the first channel is actually present.  If not reset the number
      // of channels so that we will not list stale channels
      if (!hasChannel(channelGroup))
         nrChannels = 1;
      for (int i=0; i < nrChannels; i++) {
         addChannel(channelGroup);
      }
   }
   
   private boolean hasChannel(String channelGroup) {
      String prefKey = prefNode_ + "_" + channelGroup + "_" + channels_.size();
      String channel = prefs_.getString(prefKey,
              Prefs.Keys.CHANNEL_CONFIG, "");
      return gui_.getCMMCore().isConfigDefined(channelGroup, channel);
   }

   public final void addChannel(String channelGroup) {
      String prefKey = prefNode_ + "_" + channelGroup + "_" + channels_.size();
      String channel = prefs_.getString(prefKey,
              Prefs.Keys.CHANNEL_CONFIG, "");
      // only list this channel if it is actually in the current channel group
      if (!gui_.getCMMCore().isConfigDefined(channelGroup, channel)) {
         channel = "";
      }
      addNewChannel(new ChannelSpec(
              prefs_.getBoolean(prefKey,
                      Prefs.Keys.CHANNEL_USE_CHANNEL, false),
              channelGroup,
              channel)
      );
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
      return COLUMNNAMES.length;
   }

   @Override
   public String getColumnName(int columnIndex) {
      return COLUMNNAMES[columnIndex];
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
      case COLUMNINDEX_USECHANNEL:
         if (value instanceof Boolean) {
            boolean val = (Boolean) value;
            channel.useChannel_ = val;
            prefs_.putBoolean(prefNode, Prefs.Keys.CHANNEL_USE_CHANNEL, (Boolean) val);
         }
         break;
      case COLUMNINDEX_CONFIG:
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
      case COLUMNINDEX_USECHANNEL:
         return channel.useChannel_;
      case COLUMNINDEX_CONFIG:
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
   
   // Final function of this function is here to avoid warning in the constructor
   @Override
   public final void addTableModelListener(TableModelListener tml) {
      super.addTableModelListener(tml);
   }

}

