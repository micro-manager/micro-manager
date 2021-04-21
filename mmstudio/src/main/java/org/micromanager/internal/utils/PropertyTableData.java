package org.micromanager.internal.utils;

import mmcorej.*;
import org.micromanager.Studio;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Property table data model, representing MMCore data */
public class PropertyTableData extends AbstractTableModel implements MMPropertyTableModel {

  private static final long serialVersionUID = -5582899855072387637L;
  int propertyNameColumn_;
  protected int propertyValueColumn_;
  int propertyUsedColumn_;
  public boolean disabled = false;
  public String groupName_;
  public String presetName_;
  private ShowFlags flags_;
  private Studio studio_;
  private boolean showUnused_;
  protected boolean showReadOnly_;
  String[] columnNames_ = new String[3];
  public List<PropertyItem> propList_ = new ArrayList<>();
  // The table data is stored in here.
  public List<PropertyItem> propListVisible_ = new ArrayList<>();
  // The table data is stored in here.
  protected Map<PropertyItem, Integer> propToRow_ = new HashMap<>();
  protected CMMCore core_ = null;
  Configuration groupData_[];
  PropertySetting groupSignature_[];
  private volatile boolean updating_;
  private final boolean groupOnly_;
  private final boolean allowChangingProperties_;
  private final boolean allowChangesOnlyWhenUsed_;
  private final boolean isPixelSizeConfig_;

  public static class Builder {
    private Studio studio_;
    private String groupName_;
    private String presetName_;
    private int propertyValueColumn_;
    private int propertyUsedColumn_;
    private boolean groupOnly_;
    private boolean allowChangingProperties_;
    private boolean allowChangesOnlyWhenUsed_;
    private boolean isPixelSizeConfig_;

    public Builder(final Studio studio) {
      studio_ = studio;
    }

    public Builder groupName(final String groupName) {
      groupName_ = groupName;
      return this;
    }

    public Builder presetName(final String presetName) {
      presetName_ = presetName;
      return this;
    }

    public Builder propertyValueColumn(final int propertyValueColumn) {
      propertyValueColumn_ = propertyValueColumn;
      return this;
    }

    public Builder propertyUsedColumn(final int propertyUsedColumn) {
      propertyUsedColumn_ = propertyUsedColumn;
      return this;
    }

    public Builder groupOnly(final boolean groupOnly) {
      groupOnly_ = groupOnly;
      return this;
    }

    public Builder allowChangingProperties(final boolean allow) {
      allowChangingProperties_ = allow;
      return this;
    }

    public Builder allowChangesOnlyWhenUsed(final boolean allow) {
      allowChangesOnlyWhenUsed_ = allow;
      return this;
    }

    public Builder isPixelSizeConfig(final boolean is) {
      isPixelSizeConfig_ = is;
      return this;
    }

    public PropertyTableData build() {
      return new PropertyTableData(this);
    }
  }

  /**
   * PropertyTableData constructor
   *
   * <p>This Table model is used by the Device/Property Browser, the GroupEditor, The PresetEditor,
   * and the PixelSizeEditor. Each of these has slightly different requirements, contributing to a
   * multitude of flags in the constructor. This code can likely be cleaned up with investment of
   * time to think everything through a bit better.
   *
   * @param studio
   * @param groupName - Name of group to be edited. Irrelevant for PixelSize editor
   * @param presetName
   * @param PropertyValueColumn # (zero-based) of "Value" column
   * @param PropertyUsedColumn # (zero-based) of "Use" column
   * @param groupOnly - indicates that only properties included in the group should be shown
   * @param allowChangingProperties - when true, the PropertyValueColumn will be editable, and
   *     changes will propagate to the hardware. Otherwise, the column will be read-only.
   * @param allowChangesOnlyWhenUsed - when allowChangingProperties is true setting this flag will
   *     only allow changes to PropertyItems that have the "confInclude" flag set to true
   * @param isPixelSizeConfig - indicates this is a pixel size config rather than a standard config.
   *     Pixel config-specific calls to the core will be used.
   */
  protected PropertyTableData(
      Studio studio,
      String groupName,
      String presetName,
      int PropertyValueColumn,
      int PropertyUsedColumn,
      boolean groupOnly,
      boolean allowChangingProperties,
      boolean allowChangesOnlyWhenUsed,
      boolean isPixelSizeConfig) {
    studio_ = studio;
    core_ = studio_.core();
    groupName_ = groupName;
    presetName_ = presetName;
    propertyNameColumn_ = 0;
    propertyValueColumn_ = PropertyValueColumn;
    propertyUsedColumn_ = PropertyUsedColumn;
    groupOnly_ = groupOnly;
    allowChangingProperties_ = allowChangingProperties;
    allowChangesOnlyWhenUsed_ = allowChangesOnlyWhenUsed;
    isPixelSizeConfig_ = isPixelSizeConfig;
  }

  protected PropertyTableData(Builder b) {
    studio_ = b.studio_;
    core_ = studio_.core();
    groupName_ = b.groupName_;
    presetName_ = b.presetName_;
    propertyValueColumn_ = b.propertyValueColumn_;
    propertyUsedColumn_ = b.propertyUsedColumn_;
    groupOnly_ = b.groupOnly_;
    allowChangingProperties_ = b.allowChangingProperties_;
    allowChangesOnlyWhenUsed_ = b.allowChangesOnlyWhenUsed_;
    isPixelSizeConfig_ = b.isPixelSizeConfig_;
  }

  public List<PropertyItem> getProperties() {
    return propList_;
  }

  public PropertyItem getItem(String device, String propName) {
    for (PropertyItem item : propList_) {
      if ((item.device.contentEquals(device)) && (item.name.contentEquals(propName))) {
        return item;
      }
    }
    return null; // Failed to find the item.
  }

  public boolean verifyPresetSignature() {
    return true;
  }

  public void deleteConfig(String group, String config) {
    try {
      core_.deleteConfig(group, config);
    } catch (Exception e) {
      handleException(e);
    }
  }

  @Override
  public int getRowCount() {
    return propListVisible_.size();
  }

  @Override
  public int getColumnCount() {
    return columnNames_.length;
  }

  @Override
  public PropertyItem getPropertyItem(int row) {
    return propListVisible_.get(row);
  }

  @Override
  public Object getValueAt(int row, int col) {

    PropertyItem item = propListVisible_.get(row);
    if (col == propertyNameColumn_) {
      return item.device + "-" + item.name;
    } else if (col == propertyValueColumn_) {
      return item.value;
    } else if (col == propertyUsedColumn_) {
      return item.confInclude;
    }

    return null;
  }

  public void setValueInCore(PropertyItem item, Object value) {
    ReportingUtils.logMessage(item.device + "/" + item.name + ":" + value);
    try {
      if (item.isInteger()) {
        core_.setProperty(item.device, item.name, NumberUtils.intStringDisplayToCore(value));
      } else if (item.isFloat()) {
        core_.setProperty(item.device, item.name, NumberUtils.doubleStringDisplayToCore(value));
      } else {
        core_.setProperty(item.device, item.name, value.toString());
      }
      item.value = value.toString();
      core_.waitForDevice(item.device);
    } catch (Exception e) {
      handleException(e);
    }
  }

  @Override
  public void setValueAt(Object value, int row, int col) {
    PropertyItem item = propListVisible_.get(row);
    ReportingUtils.logMessage("Setting value " + value + " at row " + row);
    if (col == propertyValueColumn_) {
      if (item.confInclude) {
        setUpdating(true);
        setValueInCore(item, value);
        core_.updateSystemStateCache();
        refresh(true);
        studio_.app().refreshGUIFromCache();
        setUpdating(false);
      }
    } else if (col == propertyUsedColumn_) {
      item.confInclude = ((Boolean) value);
    }
    fireTableCellUpdated(row, col);
  }

  @Override
  public String getColumnName(int column) {
    return columnNames_[column];
  }

  @Override
  public boolean isCellEditable(int nRow, int nCol) {
    if (nCol == propertyValueColumn_) {
      if (!allowChangingProperties_) // do not allow editing in the group editor view
      {
        return false;
      } else {
        if (propListVisible_.get(nRow).readOnly) {
          return false;
        }
        if (allowChangesOnlyWhenUsed_) {
          return propListVisible_.get(nRow).confInclude;
        } else {
          return true;
        }
      }
    } else if (nCol == propertyUsedColumn_) {
      return !groupOnly_;
    } else {
      return false;
    }
  }

  StrVector getAvailableConfigs(String group) {
    return core_.getAvailableConfigs(group);
  }

  public void refresh(boolean fromCache) {
    try {
      update(fromCache);
      this.fireTableDataChanged();
    } catch (Exception e) {
      handleException(e);
    }
  }

  public void update(boolean fromCache) {
    update(flags_, groupName_, presetName_, fromCache);
  }

  public void setShowReadOnly(boolean showReadOnly) {
    showReadOnly_ = showReadOnly;
  }

  // note: public since it is overridden in internal.PropertyEditor
  public void update(ShowFlags flags, String groupName, String presetName, boolean fromCache) {
    // when updating, we do need to keep track which properties have their
    // "Use" checkbox checked.  Otherwise, this information get lost, which
    // is annoying and confusing for the user
    List<PropertyItem> usedItems = new ArrayList<>();
    for (PropertyItem item : propListVisible_) {
      if (item.confInclude) {
        usedItems.add(item);
      }
    }

    StrVector devices = core_.getLoadedDevices();
    propList_.clear();

    Configuration cfg;

    studio_.live().setSuspended(true);
    try {
      if (isPixelSizeConfig_) {
        if (core_.isPixelSizeConfigDefined(presetName)) {
          cfg = core_.getPixelSizeConfigData(presetName);
        } else {
          // We need a config, preferably any pixel size config so:
          StrVector availablePixelSizeConfigs = core_.getAvailablePixelSizeConfigs();
          if (availablePixelSizeConfigs.size() > 0) {
            cfg = core_.getPixelSizeConfigData(availablePixelSizeConfigs.get(0));
          } else if (fromCache) {
            cfg = core_.getConfigGroupStateFromCache(groupName);
          } else {
            cfg = core_.getConfigGroupState(groupName);
          }
        }
      } else if (fromCache) {
        cfg = core_.getConfigGroupStateFromCache(groupName);
      } else {
        cfg = core_.getConfigGroupState(groupName);
      }

      setUpdating(true);

      for (int i = 0; i < devices.size(); i++) {

        if (showDevice(flags, devices.get(i))) {

          StrVector properties = core_.getDevicePropertyNames(devices.get(i));
          for (int j = 0; j < properties.size(); j++) {
            PropertyItem item = new PropertyItem();
            if (!groupOnly_ || cfg.isPropertyIncluded(devices.get(i), properties.get(j))) {
              item.readFromCore(core_, devices.get(i), properties.get(j), fromCache);
              if ((!item.readOnly || showReadOnly_) && !item.preInit) {

                item.confInclude = cfg.isPropertyIncluded(item.device, item.name);

                for (PropertyItem usedItem : usedItems) {
                  if (item.device.equals(usedItem.device) && item.name.equals(usedItem.name)) {
                    item.confInclude = true;
                  }
                }

                propList_.add(item);
              }
            }
          }
        }
      }

      setUpdating(false);

      updateRowVisibility(flags);

    } catch (Exception e) {
      handleException(e);
    } finally {
      studio_.live().setSuspended(false);
    }

    this.fireTableStructureChanged();
  }

  public void updateRowVisibility(ShowFlags flags) {
    propListVisible_.clear();

    boolean showDevice;

    for (PropertyItem item : propList_) {
      // select which devices to display

      showDevice = showDevice(flags, item.device);

      if (showUnused_ == false && item.confInclude == false) {
        showDevice = false;
      }

      if (showDevice && !flags.searchFilter_.isEmpty()) {
        // Check the device/property name against the search filter.
        String name = String.format("%s-%s", item.device, item.name);
        showDevice = name.toLowerCase().contains(flags.searchFilter_.toLowerCase());
      }

      if (showDevice) {
        propListVisible_.add(item);
      }
    }
    propToRow_.clear();
    for (int row = 0; row < propListVisible_.size(); row++) {
      propToRow_.put(propListVisible_.get(row), row);
    }

    this.fireTableStructureChanged();
    this.fireTableDataChanged();
  }

  public Boolean showDevice(ShowFlags flags, String deviceName) {
    DeviceType dType = null;
    try {
      dType = core_.getDeviceType(deviceName);
    } catch (Exception e) {
      handleException(e);
    }

    Boolean showDevice;
    if (dType == DeviceType.SerialDevice) {
      showDevice = false;
    } else if (dType == DeviceType.CameraDevice) {
      showDevice = flags.cameras_;
    } else if (dType == DeviceType.ShutterDevice) {
      showDevice = flags.shutters_;
    } else if (dType == DeviceType.StageDevice) {
      showDevice = flags.stages_;
    } else if (dType == DeviceType.XYStageDevice) {
      showDevice = flags.stages_;
    } else if (dType == DeviceType.StateDevice) {
      showDevice = flags.state_;
    } else {
      showDevice = flags.other_;
    }

    return showDevice;
  }

  public void setColumnNames(String col0, String col1, String col2) {
    columnNames_[0] = col0;
    columnNames_[1] = col1;
    columnNames_[2] = col2;
  }

  private void handleException(Exception e) {
    ReportingUtils.showError(e);
  }

  public void setGUI(Studio gui) {
    studio_ = gui;
  }

  public void setFlags(ShowFlags flags) {
    flags_ = flags;
  }

  public void setShowUnused(boolean showUnused) {
    showUnused_ = showUnused;
  }

  public void setUpdating(boolean updating) {
    updating_ = updating;
  }

  public boolean updating() {
    return updating_;
  }
}
