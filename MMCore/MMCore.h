///////////////////////////////////////////////////////////////////////////////
// FILE:          MMCore.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     MMCore
//-----------------------------------------------------------------------------
// DESCRIPTION:   The interface to the MM core services. 
//              
// COPYRIGHT:     University of California, San Francisco, 2006,
//                All Rights reserved
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
// AUTHOR:        Nenad Amodaj, nenad@amodaj.com, 06/07/2005
// 
// REVISIONS:     08/09/2005, N.A. - run-time loading of device libraries
//                08/22/2005, N.A. - intelligent loading of devices
//                                   (automatic device type)
//
// NOTES:                   
//                Public methods follow slightly different naming conventions than
//                the rest of the C++ code, i.e we have:
//                   getConfiguration();
//                instead of:
//                   GetConfiguration();
//                The alternative (lowercase function names) convention is used
//                because all public methods will most likely appear in other
//                programming environments (Java or Python).
//
// CVS:           $Id$
//
#ifndef _MMCORE_H_
#define _MMCORE_H_

// disable exception scpecification warnings in MSVC
#pragma warning( disable : 4290 )

#include <string>
#include <vector>
#include <map>
#include <fstream>
#include "../MMDevice/MMDeviceConstants.h"
#include "../MMDevice/MMDevice.h"
#include "PluginManager.h"
#include "Error.h"
#include "ErrorCodes.h"


// forward declarations
class CImageBuffer;
class Configuration;
class PropertyBlock;
class CSerial;
class ConfigGroupCollection;
class CorePropertyCollection;

/**
 * The interface to the core image acquisition services.
 * This class is intended as the top-most level interface to the core services.
 * Its public methods define the programmatic API, typically wrapped into the
 * high-level language wrapper (Python, Java, etc.). Public methods are designed
 * to conform to default processing conventions for the automatic wrapper generator
 * SWIG (http://www.swig.org).
 */
class CMMCore
{
friend class CoreCallback;

public:

	CMMCore();
	~CMMCore();
   
   /** @name Initialization and set-up
    * Loading of drivers, initialization and setting-up the environment.
    */
   //@ {
   void loadDevice(const char* label, const char* library, const char* name) throw (CMMError);
   void unloadAllDevices() throw (CMMError);
   void initializeAllDevices() throw (CMMError);
   void initializeDevice(const char* label) throw (CMMError);
   void reset() throw (CMMError);
   void clearLog();
   void enableDebugLog(bool enable);
   void enableStderrLog(bool enable);
   std::string getUserId() const;
   std::string getHostName() const;
   void logMessage(const char* msg);
   std::string getVersionInfo() const;
   std::string getAPIVersionInfo() const;
   Configuration getSystemState() const;
   void setSystemState(const Configuration& conf);
   Configuration getConfigState(const char* group, const char* config) throw (CMMError);
   void saveSystemState(const char* fileName) throw (CMMError);
   void loadSystemState(const char* fileName) throw (CMMError);
   void saveSystemConfiguration(const char* fileName) throw (CMMError);
   void loadSystemConfiguration(const char* fileName) throw (CMMError);
   //@ }

   /** @name Device discovery and configuration interface.
    */
   std::vector<std::string> getAvailableDevices(const char* library) throw (CMMError);
   std::vector<std::string> getAvailableDeviceDescriptions(const char* library) throw (CMMError);
   std::vector<int> getAvailableDeviceTypes(const char* library) throw (CMMError);
 
   /** @name Generic device interface
    * API guaranteed to work for all devices.
    */
   //@ {
   std::vector<std::string> getDeviceLibraries(const char* path);
   std::vector<std::string> getLoadedDevices() const;
   std::vector<std::string> getLoadedDevicesOfType(MM::DeviceType devType) throw (CMMError);
   std::vector<std::string> getDevicePropertyNames(const char* label) const throw (CMMError);
   std::string getProperty(const char* label, const char* propName) const throw (CMMError);
   void setProperty(const char* label, const char* propName, const char* propValue) throw (CMMError);
   std::vector<std::string> getAllowedPropertyValues(const char* label, const char* propName) const throw (CMMError);
   bool isPropertyReadOnly(const char* label, const char* propName) const throw (CMMError);
   bool isPropertyPreInit(const char* label, const char* propName) const throw (CMMError);
   MM::DeviceType getDeviceType(const char* label) throw (CMMError);
   bool deviceBusy(const char* deviceName) throw (CMMError);
   void waitForDevice(const char* deviceName) throw (CMMError);
   void waitForConfig(const char* group, const char* configName) throw (CMMError);
   bool systemBusy() throw (CMMError);
   void waitForSystem() throw (CMMError);
   void waitForImageSynchro() throw (CMMError);
   bool deviceTypeBusy(MM::DeviceType devType) throw (CMMError);
   void waitForDeviceType(MM::DeviceType devType) throw (CMMError);
   void sleep(double intervalMs) const;
   double getDeviceDelayMs(const char* label) const throw (CMMError);
   void setDeviceDelayMs(const char* label, double delayMs) throw (CMMError);
   std::string getCoreErrorText(int code) const;
   //@ }

   /** @name Multiple property settings
    * A single configuration applies to multiple devices at the same time.
    */
   //@ {
   void defineConfig(const char* groupName, const char* configName, const char* deviceName, const char* propName, const char* value);
   void defineConfigGroup(const char* groupName) throw (CMMError);
   void deleteConfigGroup(const char* groupName) throw (CMMError);
   bool isGroupDefined(const char* groupName);
   bool isConfigDefined(const char* groupName, const char* configName);
   void setConfig(const char* groupName, const char* configName) throw (CMMError);
   void deleteConfig(const char* groupName, const char* configName) throw (CMMError);
   std::vector<std::string> getAvailableConfigGroups() const;
   std::vector<std::string> getAvailableConfigs(const char* configGroup) const;
   std::string getCurrentConfig(const char* groupName) const;
   Configuration getConfigData(const char* configGroup, const char* configName) const throw (CMMError);
   //@ }

   /** @name Imaging support
    * Imaging related API.
    */
   //@ {
   void setROI(unsigned x, unsigned y, unsigned xSize, unsigned ySize) throw (CMMError); 
   void getROI(unsigned& x, unsigned& y, unsigned& xSize, unsigned& ySize) const throw (CMMError); 
   void clearROI() throw (CMMError);
   void setExposure(double exp) throw (CMMError);
   double getExposure() const throw (CMMError);
   void* getImage() const throw (CMMError);
   void snapImage() throw (CMMError);
   unsigned getImageWidth() const;
   unsigned getImageHeight() const;
   unsigned getBytesPerPixel() const;
   unsigned getImageBitDepth() const;
   long getImageBufferSize() const;
   void assignImageSynchro(const char* deviceLabel) throw (CMMError);
   void removeImageSynchro(const char* label) throw (CMMError);
   void removeImageSynchroAll();
   void setAutoShutter(bool state);
   bool getAutoShutter();
   void setShutterOpen(bool state) throw (CMMError);
   bool getShutterOpen() throw (CMMError);
   std::string getCameraDevice();
   std::string getShutterDevice();
   std::string getFocusDevice();
   std::string getXYStageDevice();
   std::string getAutoFocusDevice();
   void setCameraDevice(const char* cameraLabel) throw (CMMError);
   void setShutterDevice(const char* shutterLabel) throw (CMMError);
   void setFocusDevice(const char* focusLabel) throw (CMMError);
   void setXYStageDevice(const char* xyStageLabel) throw (CMMError);
   void setAutoFocusDevice(const char* focusLabel) throw (CMMError);
   //@ }

   /** @name State device support
    * API for controlling state devices (filters, turrets, etc.)
    */
   //@ {
   void setState(const char* deviceLabel, long state) throw (CMMError);
   long getState(const char* deviceLabel) const throw (CMMError);
   long getNumberOfStates(const char* deviceLabel);
   void setStateLabel(const char* deviceLabel, const char* stateLabel) throw (CMMError);
   std::string getStateLabel(const char* deviceLabel) const throw (CMMError);
   void defineStateLabel(const char* deviceLabel, long state, const char* stateLabel) throw (CMMError);
   std::vector<std::string> getStateLabels(const char* deviceLabel) const throw (CMMError);
   long getStateFromLabel(const char* deviceLabel, const char* stateLabel) const throw (CMMError);
   PropertyBlock getStateLabelData(const char* deviceLabel, const char* stateLabel) const;
   PropertyBlock getData(const char* deviceLabel) const;
   //@ }

   /** @name Property blocks
    * API for defining interchangeable equipment attributes
    */
   //@ {
   void definePropertyBlock(const char* blockName, const char* propertyName, const char* propertyValue);
   std::vector<std::string> getAvailablePropertyBlocks() const;
   PropertyBlock getPropertyBlockData(const char* blockName) const;
   //@ }

   /** @name Stage control
    * API for controlling X, Y and Z stages
    */
   //@ {
   void setPosition(const char* deviceName, double position) throw (CMMError);
   double getPosition(const char* deviceName) const throw (CMMError);
   void setXYPosition(const char* deviceName, double x, double y) throw (CMMError);
   void getXYPosition(const char* deviceName, double &x, double &y) throw (CMMError);
   double getXPosition(const char* deviceName) throw (CMMError);
   double getYPosition(const char* deviceName) throw (CMMError);
   void stop(const char* deviceName) throw (CMMError);
   void home(const char* deviceName) throw (CMMError);
   void setOriginXY(const char* deviceName) throw (CMMError);//jizhen 4/12/2007
   //@ }

   /** @name Serial port control
    * API for serial ports
    */
   //@ {
   void setSerialPortCommand(const char* name, const char* command, const char* term) throw (CMMError);
   std::string getSerialPortAnswer(const char* name, const char* term) throw (CMMError);
   void writeToSerialPort(const char* name, const std::vector<char> &data) throw (CMMError);
   std::vector<char> readFromSerialPort(const char* name) throw (CMMError);
   //@ }

   /** @name "  "
    */
   //@ {
   
   //@ }

private:

   // make object non-copyable
   CMMCore(const CMMCore& /*c*/) {}
   CMMCore& operator=(const CMMCore& /*rhs*/);

   MM::Camera* camera_;
   MM::Shutter* shutter_;
   MM::Stage* focusStage_;
   MM::XYStage* xyStage_;
   MM::AutoFocus* autoFocus_;

   std::vector<MM::Device*> imageSynchro_;

   CPluginManager pluginManager_;
   CorePropertyCollection* properties_;

   std::map<int, std::string> errorText_;
   typedef std::map<std::string, Configuration*> CConfigMap;
   typedef std::map<std::string, PropertyBlock*> CPropBlockMap;
   
   CConfigMap configs_;
   ConfigGroupCollection* configGroups_;
   CPropBlockMap propBlocks_;
   std::ofstream* logStream_;
   MM::Core* callback_;

   bool isConfigurationCurrent(const Configuration& config) const;
   void applyConfiguration(const Configuration& config) throw (CMMError);
   MM::Device* getDevice(const char* label) const throw (CMMError);
   template <class T>
   T* getSpecificDevice(const char* deviceLabel) const throw (CMMError);
   void initializeLogging();
   void shutdownLogging();
   void waitForDevice(MM::Device* pDev) throw (CMMError);
   std::string getDeviceErrorText(int deviceCode, MM::Device* pDevice) const;

   // system parameters
   long pollingIntervalMs_;
   long timeoutMs_;
   bool debugLog_;
   bool autoShutter_;

   // >>>>> OBSOLETE >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
   void defineConfiguration(const char* configName, const char* deviceName, const char* propName, const char* value);
   bool isConfigurationDefined(const char* configName);
   void setConfiguration(const char* configName) throw (CMMError);
   void deleteConfiguration(const char* configName) throw (CMMError);
   std::vector<std::string> getAvailableConfigurations() const;
   std::string getConfiguration() const;
   Configuration getConfigurationData(const char* config) const throw (CMMError);
};

#endif //_MMCORE_H_
