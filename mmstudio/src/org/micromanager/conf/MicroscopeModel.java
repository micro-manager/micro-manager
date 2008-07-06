///////////////////////////////////////////////////////////////////////////////
//FILE:          MicroscopeModel.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nenad Amodaj, nenad@amodaj.com, October 29, 2006
//
// COPYRIGHT:    University of California, San Francisco, 2006
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
// CVS:          $Id$
//

package org.micromanager.conf;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.GregorianCalendar;
import java.util.Hashtable;

import mmcorej.CMMCore;
import mmcorej.Configuration;
import mmcorej.MMCoreJ;
import mmcorej.PropertySetting;
import mmcorej.StrVector;

import org.micromanager.utils.MMLogger;

/**
 * Configuration data and functionality for the entire automated microscope,
 * from the hardware setup standpoint.
 */
public class MicroscopeModel {
   ArrayList<Device> devices_;
   Device availableDevices_[];
   Device availableComPorts_[];
   boolean comPortInUse_[];
   boolean modified_ = false;
   String fileName_;
   Hashtable<String, ConfigGroup> configGroups_;
   ConfigGroup pixelSizeGroup_;
   ArrayList<String> synchroDevices_;
   
   public static final String DEVLIST_FILE_NAME = "MMDeviceList.txt";
   public static final String PIXEL_SIZE_GROUP = "PixelSizeGroup";
   
   public static boolean generateDeviceListFile() {
      CMMCore core = new CMMCore();
      StrVector libs = core.getDeviceLibraries("");
      ArrayList<Device> devs = new ArrayList<Device>();
            
      for (int i=0; i<libs.size(); i++) {
         try {
            Device devList[] = Device.getLibraryContents(libs.get(i), core);
            for (int j=0; j<devList.length; j++) {
               devs.add(devList[j]);
            }
         } catch (Exception e) {
            MMLogger.getLogger().severe("Unable to load " + libs.get(i) + " library.");
            System.out.println(e.getMessage());
            return false;
         }
      }
      
      File f = new File(MicroscopeModel.DEVLIST_FILE_NAME);
      try {
         BufferedWriter out = new BufferedWriter(new FileWriter(f.getAbsolutePath()));
         for (int i=0; i<devs.size(); i++) {
            Device dev = devs.get(i);
            if (!dev.isSerialPort()) {
               // do not output serial devices
               String descr = dev.getDescription().replaceAll(",", ";");
               out.write(dev.getLibrary() + "," + dev.getAdapterName() + "," + descr + "," + dev.getTypeAsInt());
               out.newLine();
            }
         }
         out.close();
      } catch (IOException e1) {
         System.out.println("Unable to open the output file: " + MicroscopeModel.DEVLIST_FILE_NAME);
         e1.printStackTrace();
         return false;
      }
      
      return true;
   }
   
   public MicroscopeModel() {
      devices_ = new ArrayList<Device>();
      fileName_ = new String("");
      availableDevices_ = new Device[0];
      configGroups_ = new Hashtable<String, ConfigGroup>();
      synchroDevices_ = new ArrayList<String>();
      availableComPorts_ = new Device[0];
      pixelSizeGroup_ = new ConfigGroup(PIXEL_SIZE_GROUP);
      
      Device coreDev = new Device(MMCoreJ.getG_Keyword_CoreDevice(), "Default", "MMCore", "Core controller");
      devices_.add(coreDev);
      addMissingProperties();
      addSystemConfigs();
   }
   
   public boolean isModified() {
      return modified_;
   }
   
   public String getFileName() {
      return fileName_;
   }
   
   public void setFileName(String fname) {
      fileName_ = fname;
   }
   
   public void loadDeviceDataFromHardware(CMMCore core) throws Exception {
      for (int i=0; i<devices_.size(); i++) {
         Device dev = devices_.get(i);
         dev.loadDataFromHardware(core);
      }
      
      for (int i=0; i<availableComPorts_.length; i++) {
         if (comPortInUse_[i])
            availableComPorts_[i].loadDataFromHardware(core);
      }
      
   }
   
   public void loadStateLabelsFromHardware(CMMCore core) throws Exception {
      System.out.println("In Load State Labels From Hardware");
      for (int i=0; i<devices_.size(); i++) {
         Device dev = devices_.get(i);
         // do not override existing device labels:
         if (dev.getNumberOfSetupLabels() == 0)
            dev.getSetupLabelsFromHardware(core);
      }
   }
      
   public void scanComPorts(CMMCore core) {
      ArrayList<Device> ports = new ArrayList<Device>();
      StrVector libs = core.getDeviceLibraries("");
      
      System.out.println("Libraries :");
      for (int i=0; i<libs.size(); i++) {
         if (!isLibraryAvailable(libs.get(i))) {
            System.out.println(libs.get(i));         
            Device devs[] = new Device[0];
            try {
               devs = Device.getLibraryContents(libs.get(i), core);
               for (int j=0; j<devs.length; j++) {
                  if (devs[j].isSerialPort()) {
                     System.out.println("   " + devs[j].getAdapterName() + ", " + devs[j].getDescription());
                     devs[j].setName(devs[j].getAdapterName());
                     ports.add(devs[j]);
                  }
               }
            } catch (Exception e) {
               System.out.println("Unable to load " + libs.get(i) + " library.");
               System.out.println(e.getMessage());
            }
         }
      }
      
      availableComPorts_ = new Device[ports.size()];
      comPortInUse_ = new boolean[ports.size()];
      for (int i=0; i<ports.size(); i++) {
         availableComPorts_[i] = ports.get(i);
         comPortInUse_[i] = false;
      }
   }
   /**
    * Inspects the Micro-manager software and gathers information about all available devices.
    */
   public void loadAvailableDeviceList(CMMCore core) {
      ArrayList<Device> devsTotal = new ArrayList<Device>();
      
      // attempt to load device info from file
      File f = new File(DEVLIST_FILE_NAME);
      if (f.exists()) {
         loadDevicesFromListFile(devsTotal);
      }
      
      // assign available devices
      availableDevices_ = new Device[devsTotal.size()];
      for (int i=0; i<devsTotal.size(); i++) {
         availableDevices_[i] = devsTotal.get(i);
      }  
      
      // match the file list against currently available DLLs and add ones that are missing         
      StrVector libs = core.getDeviceLibraries("");
      
      System.out.println("Libraries :");
      for (int i=0; i<libs.size(); i++) {
         if (!isLibraryAvailable(libs.get(i))) {
            System.out.println(libs.get(i));         
            Device devs[] = new Device[0];
            try {
               devs = Device.getLibraryContents(libs.get(i), core);
               for (int j=0; j<devs.length; j++) {
                  if (!devs[j].isSerialPort()) {
                     System.out.println("   " + devs[j].getAdapterName() + ", " + devs[j].getDescription());
                     devsTotal.add(devs[j]);
                  }
               }
            } catch (Exception e) {
               System.out.println("Unable to load " + libs.get(i) + " library.");
               System.out.println(e.getMessage());
            }
         }
      }
      
      // re-assign remaining available devices
      availableDevices_ = new Device[devsTotal.size()];
      for (int i=0; i<devsTotal.size(); i++) {
         availableDevices_[i] = devsTotal.get(i);
      }
      
   }
   
   private void loadDevicesFromListFile(ArrayList<Device> devsTotal) {
      File f = new File(DEVLIST_FILE_NAME);
      BufferedReader input = null;
      try {
         input = new BufferedReader(new FileReader(f));
      } catch (FileNotFoundException e) {
         return;
      }
      String line = null;
      try {
         while (( line = input.readLine()) != null){
            String tokens[] = line.split(",");
            if (tokens.length == 4) {
               String desc = tokens[2].replaceAll(";", ",");
               Device dev = new Device("Undefined", tokens[0], tokens[1], desc);
               dev.setTypeByInt(Integer.parseInt(tokens[3]));
               devsTotal.add(dev);
            }
         }
      } catch (IOException e) {
         return;
      }
   }

   public Device[] getAvailableDeviceList() {
      return availableDevices_;
   }
   
   public Device[] getAvailableSerialPorts() {
      return availableComPorts_;
   }

   public boolean isPortInUse(int index) {
      return comPortInUse_[index];
   }
   
   public boolean isPortInUse(Device device) {
      for (int i=0; i<availableComPorts_.length; i++)
         if (availableComPorts_[i].getAdapterName().compareTo(device.getAdapterName()) == 0)
            return comPortInUse_[i];
      
      return false;
   }
   
   void useSerialPort(int portIndex, boolean use) {
       comPortInUse_[portIndex] = use;       
   }
   void useSerialPort(Device dev, boolean use) {
      for (int i=0; i<availableComPorts_.length; i++)
         if (availableComPorts_[i].getAdapterName().compareTo(dev.getAdapterName()) == 0)
            comPortInUse_[i] = use;
  }
  
   public void addSetupProperty(String deviceName, Property prop) throws MMConfigFileException {
      Device dev = findDevice(deviceName);
      if (dev == null)
         throw new MMConfigFileException("Device " + deviceName + " not defined.");
      Property p = dev.findSetupProperty(prop.name_);
      if (p == null)
         dev.addSetupProperty(prop);
      else
         p.value_ = prop.value_;
   }

   public void addSetupLabel(String deviceName, Label lab) throws MMConfigFileException {
      // find the device
      Device dev = findDevice(deviceName);
      if (dev != null) {
         dev.addSetupLabel(lab);
         modified_ = true;
         return;
      }
      throw new MMConfigFileException("Device " + deviceName + " not defined.");
   }
   
   /**
    * Transfer to hardware all labels defined in the setup
    * @param core
    * @throws Exception
    */
   public void applySetupLabelsToHardware(CMMCore core) throws Exception {
      for (int i=0; i<devices_.size(); i++) {
         Device dev = devices_.get(i);
         for (int j=0; j<dev.getNumberOfSetupLabels(); j++) {
            Label l = dev.getSetupLabel(j);
            core.defineStateLabel(dev.getName(), l.state_, l.label_);
         }
      }
   }
   
   /**
    * Transfer to hardware all configuration settings defined in the setup
    * @param core
    * @throws Exception
    */
   public void applySetupConfigsToHardware(CMMCore core) throws Exception {
      // first clear any existing configurations
      StrVector curGroups = core.getAvailableConfigGroups();
      for (int i=0; i<curGroups.size(); i++)
         core.deleteConfigGroup(curGroups.get(i));
      
      // now apply all the settings
      Object[] groups = configGroups_.values().toArray();
      for (int i=0; i<groups.length; i++) {
         ConfigGroup group = (ConfigGroup) groups[i];
         core.defineConfigGroup(group.getName());
         ConfigPreset[] presets = group.getConfigPresets();
         for (int j=0; j<presets.length; j++) {
            for (int k=0; k<presets[j].getNumberOfSettings(); k++) {
               Setting s = presets[j].getSetting(k);
               // apply setting
               core.defineConfig(group.getName(), presets[j].getName(), s.deviceName_, s.propertyName_, s.propertyValue_);
            }
         }
      }
      
   }
   
   /**
    * Copy the configuration presets from the hardware and override the
    * current setup data. 
    * @throws MMConfigFileException 
    * @throws Exception 
    */
   public void createSetupConfigsFromHardware(CMMCore core) throws MMConfigFileException{
      // first clear all setup data
      configGroups_.clear();
      
      // get current preset info
      StrVector curGroups = core.getAvailableConfigGroups();
      try {
         for (int i=0; i<curGroups.size(); i++) {
            ConfigGroup grp = new ConfigGroup(curGroups.get(i));
            StrVector presets = core.getAvailableConfigs(curGroups.get(i));
            for (int j=0; j<presets.size(); j++) {
               Configuration cfg;
               cfg = core.getConfigData(curGroups.get(i), presets.get(j));
               ConfigPreset p = new ConfigPreset(presets.get(j));
               for (int k=0; k<cfg.size(); k++) {
                  PropertySetting ps = cfg.getSetting(k);
                  Setting s = new Setting(ps.getDeviceLabel(), ps.getPropertyName(), ps.getPropertyValue());
                  p.addSetting(s);
               }
               grp.addConfigPreset(p);
            }
            configGroups_.put(curGroups.get(i), grp);
            modified_ = true;
         }
      } catch (Exception e) {
         throw new MMConfigFileException(e);
      }
   }

   /**
    * Copy the configuration presets from the hardware and override the
    * current setup data. 
    * @throws MMConfigFileException 
    * @throws Exception 
    */
   public void createResolutionsFromHardware(CMMCore core) throws MMConfigFileException{
      // first clear all setup data
      pixelSizeGroup_ = new ConfigGroup(PIXEL_SIZE_GROUP);
      
      try {
            StrVector pixelSizeConfigs = core.getAvailablePixelSizeConfigs();
            for (int j=0; j<pixelSizeConfigs.size(); j++) {
               Configuration pcfg;
               pcfg = core.getPixelSizeConfigData(pixelSizeConfigs.get(j));
               ConfigPreset p = new ConfigPreset(pixelSizeConfigs.get(j));
               p.setPixelSizeUm(core.getPixelSizeUm(pixelSizeConfigs.get(j)));
               for (int k=0; k<pcfg.size(); k++) {
                  PropertySetting ps = pcfg.getSetting(k);
                  Setting s = new Setting(ps.getDeviceLabel(), ps.getPropertyName(), ps.getPropertyValue());
                  p.addSetting(s);
               }
               pixelSizeGroup_.addConfigPreset(p);
            }
            //configGroups_.put(PIXEL_SIZE_GROUP, pixelSizeGroup_);
            modified_ = true;
      } catch (Exception e) {
         throw new MMConfigFileException(e);
      }
   }

      // get current preset info
//      try {
//            StrVector presets = core.getAvailableConfigs(curGroups.get(i));
//            for (int j=0; j<presets.size(); j++) {
//               Configuration cfg;
//               cfg = core.getConfigData(curGroups.get(i), presets.get(j));
//               ConfigPreset p = new ConfigPreset(presets.get(j));
//               for (int k=0; k<cfg.size(); k++) {
//                  PropertySetting ps = cfg.getSetting(k);
//                  Setting s = new Setting(ps.getDeviceLabel(), ps.getPropertyName(), ps.getPropertyValue());
//                  p.addSetting(s);
//               }
//               grp.addConfigPreset(p);
//            }
//            configGroups_.put(curGroups.get(i), grp);
//            modified_ = true;
//      } catch (Exception e) {
//         throw new MMConfigFileException(e);
//      }
   
   
   public void applyDelaysToHardware(CMMCore core) throws Exception {
      for (int i=0; i<devices_.size(); i++) {
         Device dev = devices_.get(i);
         core.setDeviceDelayMs(dev.getName(), dev.getDelay());
      }
   }
   
   public boolean addConfigGroup(String name) {
      ConfigGroup cg = new ConfigGroup(name);
      Object obj = configGroups_.get(name);
      if (obj == null) {
         configGroups_.put(cg.getName(), cg);
         modified_ = true;
         return true;
      } else {
         return false;
      }
   }
      
   public void loadFromFile(String path) throws MMConfigFileException {

      reset();
      
      File configFile = new File(path);
      if (!configFile.exists()) {
         throw new MMConfigFileException("Configuration file does not exist.");
      }

      boolean initialized = false;
      
      try {
         // read metadata from file            
         BufferedReader input = null;
         input = new BufferedReader(new FileReader(configFile));
         String line = null;
         while (( line = input.readLine()) != null){
            String tokens[] = line.split(",");
            if (tokens.length == 0 || tokens[0].startsWith("#"))
               continue;
            
            //System.out.println(line);
            if (tokens[0].contentEquals(new StringBuffer().append(MMCoreJ.getG_CFGCommand_Device()))) {
               // -------------------------------------------------------------
               // "Device" command
               // -------------------------------------------------------------
               if (tokens.length != 4)
                  throw new MMConfigFileException("Invalid number of parameters (4 required):\n" + line);
               Device dev = new Device(tokens[1], tokens[2], tokens[3], getDeviceDescription(tokens[2], tokens[3]));
               //System.out.println("Adding: " + tokens[1] + "," + tokens[2] + "," + tokens[3]);
               // get description
               devices_.add(dev);
            } else if (tokens[0].contentEquals(new StringBuffer().append(MMCoreJ.getG_CFGCommand_Property()))) {
               
               // -------------------------------------------------------------
               // "Property" command
               // -------------------------------------------------------------
               if (!(tokens.length == 4 || tokens.length == 3))
                  throw new MMConfigFileException("Invalid number of parameters (4 required):\n" + line);
               
               if (tokens.length == 3) {
                  // resize tokens array to 4 elements
                  String extTokens[] = new String[4];
                  for (int i=0; i<3; i++)
                     extTokens[i] = tokens[i];
                  extTokens[3] = new String("");
                  tokens = extTokens;
               }
               
               if (tokens[1].contentEquals(new StringBuffer().append(MMCoreJ.getG_Keyword_CoreDevice()))) {
                  
                  // core device processing
                  // ----------------------
                  if (tokens[2].contentEquals(new StringBuffer().append(MMCoreJ.getG_Keyword_CoreInitialize()))) {
                     if (tokens[3].contentEquals(new StringBuffer().append("0"))) {
                        initialized = false;
                     } else {
                        initialized = true;
                     }                     
                  } else {
                     Property prop = new Property();
                     prop.name_ = tokens[2];
                     prop.value_ = tokens[3];
                     addSetupProperty(tokens[1], prop);
                  }
                  
               } else {
                  Property prop = new Property();
                  if (initialized)
                     prop.preInit_ = false;
                  else
                     prop.preInit_ = true;
                  prop.name_ = tokens[2];
                  prop.value_ = tokens[3];
                  addSetupProperty(tokens[1], prop);
              }
            } else if (tokens[0].contentEquals(new StringBuffer().append(MMCoreJ.getG_CFGCommand_Label()))) {
               // -------------------------------------------------------------
               // "Label" command
               // -------------------------------------------------------------
               if (tokens.length != 4)
                  throw new MMConfigFileException("Invalid number of parameters (4 required):\n" + line);
               Label lab = new Label(tokens[3], Integer.parseInt(tokens[2]));
               addSetupLabel(tokens[1], lab);
            } else if (tokens[0].contentEquals(new StringBuffer().append(MMCoreJ.getG_CFGCommand_ImageSynchro()))) {
               // -------------------------------------------------------------
               // "ImageSynchro" commands
               // -------------------------------------------------------------
               if (tokens.length != 2)
                  throw new MMConfigFileException("Invalid number of parameters (2 required):\n" + line);
               synchroDevices_.add(tokens[1]);
            } else if (tokens[0].contentEquals(new StringBuffer().append(MMCoreJ.getG_CFGCommand_ConfigGroup()))) {
               // -------------------------------------------------------------
               // "ConfigGroup" commands
               // -------------------------------------------------------------
               if (!(tokens.length == 6 || tokens.length == 5))
                  throw new MMConfigFileException("Invalid number of parameters (6 required):\n" + line);
               addConfigGroup(tokens[1]);
               ConfigGroup cg = findConfigGroup(tokens[1]);
               if (tokens.length == 6)
                  cg.addConfigSetting(tokens[2], tokens[3], tokens[4], tokens[5]);
               else
                  cg.addConfigSetting(tokens[2], tokens[3], tokens[4], new String(""));
               
            } else if (tokens[0].contentEquals(new StringBuffer().append(MMCoreJ.getG_CFGCommand_ConfigPixelSize()))) {
               // -------------------------------------------------------------
               // "ConfigPixelSize" commands
               // -------------------------------------------------------------
               if (!(tokens.length == 5))
                  throw new MMConfigFileException("Invalid number of parameters (5 required):\n" + line);
               
               pixelSizeGroup_.addConfigSetting(tokens[1], tokens[2], tokens[3], tokens[4]);
               
            } else if (tokens[0].contentEquals(new StringBuffer().append(MMCoreJ.getG_CFGCommand_PixelSize_um()))) {
               // -------------------------------------------------------------
               // "PixelSize" commands
               // -------------------------------------------------------------
               if (tokens.length != 3)
                  throw new MMConfigFileException("Invalid number of parameters (3 required):\n" + line);
               
               ConfigPreset cp = pixelSizeGroup_.findConfigPreset(tokens[1]);
               if (cp != null)
                  cp.setPixelSizeUm(Double.parseDouble(tokens[2]));
            } else if (tokens[0].contentEquals(new StringBuffer().append(MMCoreJ.getG_CFGCommand_Delay()))) {
               // -------------------------------------------------------------
               // "Delay" commands
               // -------------------------------------------------------------
               if (tokens.length != 3)
                  throw new MMConfigFileException("Invalid number of parameters (3 required):\n" + line);
               Device dev = findDevice(tokens[1]);
               if (dev != null)
                  dev.setDelay(Double.parseDouble(tokens[2]));
            }

         }
      } catch (IOException e) {
         reset();
         throw new MMConfigFileException(e);
      } finally {
         modified_ = false;
         fileName_ = path;
         addMissingProperties();
         addSystemConfigs();
         //dumpDeviceProperties(MMCoreJ.getG_Keyword_CoreDevice()); 
         
         // check com ports usage
         for (int i=0; i<availableComPorts_.length; i++) {
            Device dev = findDevice(availableComPorts_[i].getName());
            if (dev != null)
               useSerialPort(dev, true);
         }
      }
   }
   
   private String getDeviceDescription(String library, String adapter) {
      Device dev = findAvailableDevice(library, adapter);
      if (dev != null)
         return dev.getDescription();
      return "";
   }
   
   private Device findAvailableDevice(String library, String adapter) {
      for (int i=0; i<availableDevices_.length; i++)
         if (availableDevices_[i].getLibrary().compareTo(library) == 0 && availableDevices_[i].getAdapterName().compareTo(adapter) == 0)
            return availableDevices_[i];
      return null;
   }
   
   private boolean isLibraryAvailable(String library) {
      for (int i=0; i<availableDevices_.length; i++)
         if (availableDevices_[i].getLibrary().compareTo(library) == 0)
            return true;
      return false;
   }
   private void addMissingProperties() {
      Device c = findDevice(MMCoreJ.getG_Keyword_CoreDevice());
      if (c == null) {
         c =new Device(MMCoreJ.getG_Keyword_CoreDevice(), "MMCore", "CoreDevice");
      }
      
      Property p = c.findSetupProperty(MMCoreJ.getG_Keyword_CoreCamera());
      if (p==null)
         c.addSetupProperty(new Property(MMCoreJ.getG_Keyword_CoreCamera(), ""));
      
      p = c.findSetupProperty(MMCoreJ.getG_Keyword_CoreShutter());
      if (p==null)
         c.addSetupProperty(new Property(MMCoreJ.getG_Keyword_CoreShutter(), ""));

      p = c.findSetupProperty(MMCoreJ.getG_Keyword_CoreFocus());
      if (p==null)
         c.addSetupProperty(new Property(MMCoreJ.getG_Keyword_CoreFocus(), ""));
      
      p = c.findSetupProperty(MMCoreJ.getG_Keyword_CoreAutoShutter());
      if (p==null)
         c.addSetupProperty(new Property(MMCoreJ.getG_Keyword_CoreAutoShutter(), "1"));
   }
   
   private void addSystemConfigs() {
      ConfigGroup cg = findConfigGroup(MMCoreJ.getG_CFGGroup_System());
      if (cg == null)
         addConfigGroup(MMCoreJ.getG_CFGGroup_System());
      
      cg = findConfigGroup(MMCoreJ.getG_Keyword_Channel());
      if (cg == null)
         addConfigGroup(MMCoreJ.getG_Keyword_Channel());
      
      cg = findConfigGroup(MMCoreJ.getG_CFGGroup_System());
      ConfigPreset cp = cg.findConfigPreset(MMCoreJ.getG_CFGGroup_System_Startup());
      if (cp == null) {
         cp = new ConfigPreset(MMCoreJ.getG_CFGGroup_System_Startup());
         cg.addConfigPreset(cp);
      }
   }
   
   public void saveToFile(String path) throws MMConfigFileException {
      try {
         BufferedWriter out = new BufferedWriter(new FileWriter(path));
         
         out.write("# Generated by Configurator on " + GregorianCalendar.getInstance().getTime());
         out.newLine();
         out.newLine();
         
         // unload previous
         out.write("# Reset");
         out.newLine();
         out.write("Property,Core,Initialize,0");
         out.newLine();
         out.newLine();
          
         // device section
         out.write("# Devices");
         out.newLine();
         for (int i=0; i<availableComPorts_.length; i++) {
            Device dev = availableComPorts_[i];
            if (isPortInUse(dev)) {
               out.write(MMCoreJ.getG_CFGCommand_Device()+","+ dev.getName() + "," + dev.getLibrary() + "," + dev.getAdapterName());
               out.newLine();
            }
         }
         for (int i=0; i<devices_.size(); i++) {
            Device dev = devices_.get(i);
            if (!dev.isCore()) {
               out.write(MMCoreJ.getG_CFGCommand_Device()+","+ dev.getName() + "," + dev.getLibrary() + "," + dev.getAdapterName());
               out.newLine();
            }
         }
         out.newLine();
         
         // pre-init properties
         out.write("# Pre-init settings for devices");
         out.newLine();
         for (int i=0; i<devices_.size(); i++) {
            Device dev = devices_.get(i);
            for (int j=0; j<dev.getNumberOfSetupProperties(); j++) {
               Property prop = dev.getSetupProperty(j);
               if (prop.preInit_) {
                  out.write(MMCoreJ.getG_CFGCommand_Property()+"," + dev.getName() + "," + prop.name_ + "," + prop.value_);
                  out.newLine();
               }
            }
         }
         out.newLine();
         
         // pre-init properties for ports
         out.write("# Pre-init settings for COM ports");
         out.newLine();
         for (int i=0; i<availableComPorts_.length; i++) {
            Device dev = availableComPorts_[i];
            for (int j=0; j<dev.getNumberOfSetupProperties(); j++) {
               Property prop = dev.getSetupProperty(j);
               if (isPortInUse(dev) && prop.preInit_) {
                  out.write(MMCoreJ.getG_CFGCommand_Property()+"," + dev.getName() + "," + prop.name_ + "," + prop.value_);
                  out.newLine();
               }
            }
         }
         out.newLine();
         
         // initialize
         out.write("# Initialize");
         out.newLine();
         out.write("Property,Core,Initialize,1");
         out.newLine();
         out.newLine();
         
         // delays
         out.write("# Delays");
         out.newLine();
         for (int i=0; i<devices_.size(); i++) {
            Device dev = devices_.get(i);
            if (dev.getDelay() > 0.0) {
               out.write(MMCoreJ.getG_CFGCommand_Delay() + ","+ dev.getName() + "," + dev.getDelay());
               out.newLine();
            }
         }
         out.newLine();
         
         // roles
         out.write("# Roles");
         out.newLine();
         Device coreDev = findDevice(MMCoreJ.getG_Keyword_CoreDevice());
         Property p = coreDev.findSetupProperty(MMCoreJ.getG_Keyword_CoreCamera());
         if (p.value_.length() > 0) {
            out.write(MMCoreJ.getG_CFGCommand_Property()+","+MMCoreJ.getG_Keyword_CoreDevice()+","+MMCoreJ.getG_Keyword_CoreCamera()+","+p.value_);
            out.newLine();
         }
         p = coreDev.findSetupProperty(MMCoreJ.getG_Keyword_CoreShutter());
         if (p.value_.length() > 0) {
            out.write(MMCoreJ.getG_CFGCommand_Property()+","+MMCoreJ.getG_Keyword_CoreDevice()+","+MMCoreJ.getG_Keyword_CoreShutter()+","+p.value_);
            out.newLine();
         }
         p = coreDev.findSetupProperty(MMCoreJ.getG_Keyword_CoreFocus());
         if (p.value_.length() > 0) {
            out.write(MMCoreJ.getG_CFGCommand_Property()+","+MMCoreJ.getG_Keyword_CoreDevice()+","+MMCoreJ.getG_Keyword_CoreFocus()+","+p.value_);
            out.newLine();
         }
         p = coreDev.findSetupProperty(MMCoreJ.getG_Keyword_CoreAutoShutter());
         if (p.value_.length() > 0) {
            out.write(MMCoreJ.getG_CFGCommand_Property()+","+MMCoreJ.getG_Keyword_CoreDevice()+","+MMCoreJ.getG_Keyword_CoreAutoShutter()+","+p.value_);
            out.newLine();
         }
         out.newLine();
         
         // synchro devices
         out.write("# Camera-synchronized devices");
         out.newLine();
         for (int i=0; i<synchroDevices_.size(); i++) {
            out.write(MMCoreJ.getG_CFGCommand_ImageSynchro()+","+synchroDevices_.get(i));
            out.newLine();
         }
         out.newLine();
         
         // labels
         out.write("# Labels");
         out.newLine();
         for (int i=0; i<devices_.size(); i++) {
            Device dev = devices_.get(i);
            if (dev.getNumberOfSetupLabels() > 0) {
               out.write("# " + dev.getName());
               out.newLine();
            }
            for (int j=0; j<dev.getNumberOfSetupLabels(); j++) {
               Label l = dev.getSetupLabel(j);
               out.write(MMCoreJ.getG_CFGCommand_Label()+","+ dev.getName() + "," + l.state_ + "," + l.label_);
               out.newLine();
            }
         }
         out.newLine();
         
         // configuration presets
         out.write("# Configuration presets");
         out.newLine();
         Object[] groups = configGroups_.values().toArray();
         for (int i=0; i<groups.length; i++) {
            ConfigGroup group = (ConfigGroup) groups[i];
            out.write("# Group: " + group.getName());
            out.newLine();
            ConfigPreset[] presets = group.getConfigPresets();
            for (int j=0; j<presets.length; j++) {
               out.write("# Preset: " + presets[j].getName());
               out.newLine();
               for (int k=0; k<presets[j].getNumberOfSettings(); k++) {
                  Setting s = presets[j].getSetting(k);
                  // write setting
                  out.write(MMCoreJ.getG_CFGCommand_ConfigGroup()+","+group.getName()+","+presets[j].getName()+","+s.deviceName_+","+s.propertyName_+","+s.propertyValue_);
                  out.newLine();
               }
               out.newLine();
            }
            out.newLine();
         }
         out.newLine();
         
         // pixel size
         out.write("# PixelSize settings");
         out.newLine();
         ConfigPreset[] presets = pixelSizeGroup_.getConfigPresets();
         for (int j=0; j<presets.length; j++) {
            out.write("# Resolution preset: " + presets[j].getName());
            out.newLine();
            for (int k=0; k<presets[j].getNumberOfSettings(); k++) {
               Setting s = presets[j].getSetting(k);
               // write setting
               out.write(MMCoreJ.getG_CFGCommand_ConfigPixelSize()+","+presets[j].getName()+","+s.deviceName_+","+s.propertyName_+","+s.propertyValue_);
               out.newLine();
               out.write(MMCoreJ.getG_CFGGroup_PixelSizeUm()+","+presets[j].getName()+","+Double.toString(presets[j].getPixelSize()));
               out.newLine();
            }
            out.newLine();
         }
         out.newLine();
         
         out.close();
     } catch (IOException e) {
        throw new MMConfigFileException(e);
     }      
      fileName_ = path;
      modified_ = false;
   }
   
   /**
    * Display report for the current configuration.
    *
    */
   public void dumpSetupConf() {
      System.out.println("\nStep 1: load devices");
      for (int i=0; i<devices_.size(); i++) {
         Device dev = devices_.get(i);
         System.out.println(dev.getName() + " from library " + dev.getLibrary() + ", using adapter " + dev.getAdapterName());
      }
      
      System.out.println("\nStep 2: set pre-initialization properties");
      for (int i=0; i<devices_.size(); i++) {
         Device dev = devices_.get(i);
         for (int j=0; j<dev.getNumberOfSetupProperties(); j++) {
            Property prop = dev.getSetupProperty(j);
            if (prop.preInit_)
               System.out.println(dev.getName() + ", property " + prop.name_ + "=" + prop.value_);
         }
      }
      
      System.out.println("\nStep 3: initialize");
      
      System.out.println("\nStep 4: define device labels");
      for (int i=0; i<devices_.size(); i++) {
         Device dev = devices_.get(i);
         System.out.println(dev.getName() + " labels:");
         for (int j=0; j<dev.getNumberOfSetupLabels(); j++) {
            Label lab = dev.getSetupLabel(j);
            System.out.println("    State " + lab.state_ + "=" + lab.label_);
         }
      }
      
      System.out.println("\nStep 5: set initial properties");
      for (int i=0; i<devices_.size(); i++) {
         Device dev = devices_.get(i);
         for (int j=0; j<dev.getNumberOfSetupProperties(); j++) {
            Property prop = dev.getSetupProperty(j);
            if (!prop.preInit_)
               System.out.println(dev.getName() + ", property " + prop.name_ + "=" + prop.value_);
         }
      }

   }
   
   public void dumpDeviceProperties(String device) {
      Device d = findDevice(device);
      if (d == null)
         return;
      for (int i=0; i<d.getNumberOfSetupProperties(); i++) {
         Property prop = d.getSetupProperty(i);
         System.out.println(d.getName() + ", property " + prop.name_ + "=" + prop.value_);
         for (int j=0; j<prop.allowedValues_.length; j++)
            System.out.println("   " + prop.allowedValues_[j]);      }
   }

   public void dumpComPortProperties(String device) {
      Device d = findSerialPort(device);
      if (d == null)
         return;
      for (int i=0; i<d.getNumberOfSetupProperties(); i++) {
         Property prop = d.getSetupProperty(i);
         System.out.println(d.getName() + ", property " + prop.name_ + "=" + prop.value_);
         for (int j=0; j<prop.allowedValues_.length; j++)
            System.out.println("   " + prop.allowedValues_[j]);      }
   }
   
   public void dumpComPortsSetupProps() {
      for (int i=0; i<availableComPorts_.length; i++) {
         dumpDeviceProperties(availableComPorts_[i].getName());
         dumpComPortProperties(availableComPorts_[i].getName());
      }
   }
   
   public void reset() {
      devices_.clear();
      configGroups_.clear();
      synchroDevices_.clear();
      pixelSizeGroup_.clear();
      Device coreDev = new Device(MMCoreJ.getG_Keyword_CoreDevice(), "Default", "MMCore", "Core controller");
      devices_.add(coreDev);
      addMissingProperties();
      addSystemConfigs();
      modified_ = true;
   }
   
    public Device[] getDevices() {
      Device[] devs = new Device[devices_.size()];
      for (int i=0; i<devs.length; i++)
         devs[i] = devices_.get(i);
      
      return devs;
   }
   
   public void removeDevice(String devName) {
      Device dev = findDevice(devName);
      if (dev != null) {
         devices_.remove(dev);
         modified_ = true;
      }
   }

   Device findDevice(String devName) {
      for (int i=0; i<devices_.size(); i++) {
         Device dev = devices_.get(i);
         if (dev.getName().contentEquals(new StringBuffer().append(devName)))
            return dev;
      }
      return null;
   }
   
   Device findSerialPort(String name) {
      for (int i=0; i<availableComPorts_.length; i++) {
         if (availableComPorts_[i].getName().contentEquals(new StringBuffer().append(name)))
            return availableComPorts_[i];
      }
      return null;
   }
   
   ConfigGroup findConfigGroup(String name) {
      return configGroups_.get(name);
   }
   
   String[] getConfigGroupList() {
      String cgList[] = new String[configGroups_.size()];
      Object[] cgs = configGroups_.values().toArray();
      for (int i=0; i<cgs.length; i++)
         cgList[i] = ((ConfigGroup)cgs[i]).getName();
      
      return cgList;
   }
   
   String[] getSynchroList() {
      String synchro[] = new String[synchroDevices_.size()];
      for (int i=0; i<synchroDevices_.size(); i++)
         synchro[i] = new String(synchroDevices_.get(i));
      return synchro;
   }
   
   public void addSynchroDevice(String name) {
      synchroDevices_.add(name);
      modified_ = true;
   }
   
   public void clearSynchroDevices() {
      synchroDevices_.clear();
      modified_ = true;
   }

   public void addDevice(Device dev) throws MMConfigFileException {
      if (dev.getName().length() == 0)
         throw new MMConfigFileException("Empty device names are not allowed, please choose a different name.");
      
      if (findDevice(dev.getName()) != null)
         throw new MMConfigFileException(dev.getName() + " already defined, please choose a different name.");
      
      devices_.add(dev);
      modified_ = true;
   }
   
   public void changeDeviceName(String oldName, String newName) throws MMConfigFileException {
      Device dev = findDevice(oldName);
      if (dev == null)
         throw new MMConfigFileException("Device " + oldName +  " is not defined");
      
      dev.setName(newName);
      modified_ = true;
   }
   
   public String getDeviceSetupProperty(String devName, String propName) throws MMConfigFileException {
      Device c = findDevice(devName);
      if (c == null)
         return null;
      return c.getSetupPropertyValue(propName);
   }
   
   public void setDeviceSetupProperty(String devName, String propName, String value) throws MMConfigFileException {
      Device c = findDevice(devName);
      if (c == null)
         throw new MMConfigFileException("Device " + devName +  " is not defined");
      
      c.setSetupPropertyValue(propName, value);
      modified_ = true;
   }

   public void removeGroup(String name) {
      configGroups_.remove(name);
      modified_ = true;
   }

   public void renameGroup(ConfigGroup grp, String name) {
      configGroups_.remove(grp.getName());
      grp.setName(name);
      configGroups_.put(name, grp);
      modified_ = true;
   }

   public void removeDuplicateComPorts() {
      // remove devices with names corresponding to available com ports
      for (int i=0; i<availableComPorts_.length; i++) {
         Device dev = findDevice(availableComPorts_[i].getName());
         if (dev != null) {
            availableComPorts_[i] = dev;
            removeDevice(dev.getName());
         }
      }
      
      // remove differently named com ports from device lists
      for (int i=0; i<devices_.size(); i++) {
         Device dev = devices_.get(i);
         if (dev.isSerialPort())
            removeDevice(dev.getName());
      } 
   }
   
   /**
    * Remove configurations which refer to non existent devices.
    * This situation may occur if some devices are removed while still
    * referred to by the config group.
    */
   public void removeInvalidConfigurations() {
      Object[] groups = configGroups_.values().toArray();
      for (int i=0; i<groups.length; i++) {
         ConfigGroup group = (ConfigGroup) groups[i];
         ConfigPreset[] presets = group.getConfigPresets();
         groupSearch:
         for (int j=0; j<presets.length; j++) {
            for (int k=0; k<presets[j].getNumberOfSettings(); k++) {
               Setting s = presets[j].getSetting(k);
               // check if device is available
               if (null == findDevice(s.deviceName_)) {
                  // if not, remove the entire group
                  removeGroup(group.name_);
                  break groupSearch;
               }
            }
         }
      }
   }
}
