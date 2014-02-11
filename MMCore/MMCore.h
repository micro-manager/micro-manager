///////////////////////////////////////////////////////////////////////////////
// FILE:          MMCore.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     MMCore
//-----------------------------------------------------------------------------
// DESCRIPTION:   The interface to the MM core services. 
//              
// COPYRIGHT:     University of California, San Francisco, 2006,
//                100X Imaging Inc, www.100ximaging.com, 2008
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
//                05/15/2007, N.A. - Circular buffer interface and thread synchronization
//                12/18/2007  N.A. - Callbacks for GUI side notifications
//                05/20/2008  N.A. - Relative positions for stages, cached system state
//
// NOTES:         Public methods follow a slightly different naming convention than
//                the rest of the C++ code, i.e we have:
//                   getConfiguration();
//                instead of:
//                   GetConfiguration();
//                The alternative (lowercase function names) convention is used
//                because public method names appear as wrapped methods in other
//                languages, in particular Java.

#ifndef _MMCORE_H_
#define _MMCORE_H_

// disable exception scpecification warnings in MSVC
#ifdef WIN32
#pragma warning( disable : 4290 )
#endif

#include <string>
#include <cstring>
#include <vector>
#include <deque>
#include <map>
#include <fstream>
#include "../MMDevice/MMDeviceConstants.h"
#include "../MMDevice/MMDevice.h"
#include "PluginManager.h"
#include "Configuration.h"
#include "CoreUtils.h"
#include "Error.h"
#include "ErrorCodes.h"

#include "../MMDevice/DeviceThreads.h"

// forward declarations
class CircularBuffer;
class Configuration;
class PropertyBlock;
class CSerial;
class ConfigGroupCollection;
class CorePropertyCollection;
class CoreCallback;
class PixelSizeConfigGroup;
class Metadata;
class MMEventCallback;
class FastLogger;

typedef unsigned int* imgRGB32;

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
   friend class CorePropertyCollection;

public:

   CMMCore();
   ~CMMCore();

   /**
    * A method that does nothing.
    *
    * This method can be called as a sanity check when dynamically loading the
    * Core library.
    */
   static void noop() {}

   /** @name Initialization and set-up
   * Loading of drivers, initialization and setting-up the environment.
   */
   //@ {
   void loadDevice(const char* label, const char* library, const char* adapterName) throw (CMMError);
   void unloadDevice(const char* label) throw (CMMError);
   void unloadAllDevices() throw (CMMError);
   void initializeAllDevices() throw (CMMError);
   void initializeDevice(const char* label) throw (CMMError);
   void updateCoreProperties() throw (CMMError);
   void reset() throw (CMMError);
   void clearLog();
   void enableDebugLog(bool enable);
   bool debugLogEnabled(void) { return debugLog_;};
   void enableStderrLog(bool enable);
   std::string getUserId() const;
   std::string getHostName() const;
   void logMessage(const char* msg);
   void logMessage(const char* msg, bool debugOnly);
   // this creates an archive of the current log contents and returns the path created
   std::string saveLogArchive(void);
   std::string saveLogArchiveWithPreamble(char* preamble, int length);

   std::string getVersionInfo() const;
   std::string getAPIVersionInfo() const;
   Configuration getSystemState();
   Configuration getSystemStateCache() const;
   void updateSystemStateCache();
   void setSystemState(const Configuration& conf);
   Configuration getConfigState(const char* group, const char* config) throw (CMMError);
   Configuration getConfigGroupState(const char* group) throw (CMMError);
   Configuration getConfigGroupStateFromCache(const char* group) throw (CMMError);
   void saveSystemState(const char* fileName) throw (CMMError);
   void loadSystemState(const char* fileName) throw (CMMError);
   void saveSystemConfiguration(const char* fileName) throw (CMMError);
   void loadSystemConfiguration(const char* fileName) throw (CMMError);
   void registerCallback(MMEventCallback* cb);
   //@ }

   /** @name Device discovery and configuration interface.
   */
   std::vector<std::string> getAvailableDevices(const char* library) throw (CMMError);
   std::vector<std::string> getAvailableDeviceDescriptions(const char* library) throw (CMMError);
   std::vector<long> getAvailableDeviceTypes(const char* library) throw (CMMError);

   /** @name Generic device interface
   * API guaranteed to work for all devices.
   */
   //@ {
   std::vector<std::string> getDeviceAdapterSearchPaths();
   void setDeviceAdapterSearchPaths(const std::vector<std::string>& paths);
   std::vector<std::string> getDeviceAdapterNames() throw (CMMError);

   static void addSearchPath(const char *path); // Deprecated
   static std::vector<std::string> getDeviceLibraries() throw (CMMError); // Deprecated

   std::vector<std::string> getLoadedDevices() const;
   std::vector<std::string> getLoadedDevicesOfType(MM::DeviceType devType) const;
   std::vector<std::string> getDevicePropertyNames(const char* label) throw (CMMError);
   std::string getProperty(const char* label, const char* propName) throw (CMMError);
   std::string getPropertyFromCache(const char* label, const char* propName) const throw (CMMError);
   void setProperty(const char* label, const char* propName, const char* propValue) throw (CMMError);

   void setProperty(const char* label, const char* propName, const bool propValue) throw (CMMError);
   void setProperty(const char* label, const char* propName, const long propValue) throw (CMMError);
   void setProperty(const char* label, const char* propName, const float propValue) throw (CMMError);
   void setProperty(const char* label, const char* propName, const double propValue) throw (CMMError);

   bool hasProperty(const char* label, const char* propName) throw (CMMError);
   std::vector<std::string> getAllowedPropertyValues(const char* label, const char* propName) throw (CMMError);
   bool isPropertyReadOnly(const char* label, const char* propName) throw (CMMError);
   bool isPropertyPreInit(const char* label, const char* propName) throw (CMMError);
   bool isPropertySequenceable(const char* label, const char* propName) throw (CMMError);
   bool hasPropertyLimits(const char* label, const char* propName) throw (CMMError);
   double getPropertyLowerLimit(const char* label, const char* propName) throw (CMMError);
   double getPropertyUpperLimit(const char* label, const char* propName) throw (CMMError);
   void startPropertySequence(const char* label, const char* propName) throw (CMMError);
   void stopPropertySequence(const char* label, const char* propName) throw (CMMError);
   long getPropertySequenceMaxLength(const char* label, const char* propName) throw (CMMError);
   void loadPropertySequence(const char* label, const char* propName, std::vector<std::string> eventSequence) throw (CMMError);
   MM::PropertyType getPropertyType(const char* label, const char* propName) throw (CMMError);
   MM::DeviceType getDeviceType(const char* label) throw (CMMError);
   std::string getDeviceLibrary(const char* label) throw (CMMError);
   void unloadLibrary(const char* moduleName) throw (CMMError);
   std::string getDeviceName(const char* label) throw (CMMError);
   std::string getParentLabel(const char* label) throw (CMMError);
   void setParentLabel(const char* label, const char* parentLabel) throw (CMMError);
   std::string getDeviceDescription(const char* label) throw (CMMError);
   bool deviceBusy(const char* deviceName) throw (CMMError);
   void waitForDevice(const char* deviceName) throw (CMMError);
   void waitForConfig(const char* group, const char* configName) throw (CMMError);
   bool systemBusy() throw (CMMError);
   void waitForSystem() throw (CMMError);
   void waitForImageSynchro() throw (CMMError);
   bool deviceTypeBusy(MM::DeviceType devType) throw (CMMError);
   void waitForDeviceType(MM::DeviceType devType) throw (CMMError);
   void sleep(double intervalMs) const;
   double getDeviceDelayMs(const char* label) throw (CMMError);
   void setDeviceDelayMs(const char* label, double delayMs) throw (CMMError);
   void setTimeoutMs(long timeoutMs) {if (timeoutMs > 0) timeoutMs_ = timeoutMs;}
   long getTimeoutMs() { return timeoutMs_;}
   bool usesDeviceDelay(const char* label) throw (CMMError);
   std::string getCoreErrorText(int code) const;


   //@ }

   /**
   * @name System role identification for devices.
   */
   //@ {
   std::string getCameraDevice();
   std::string getShutterDevice();
   std::string getFocusDevice();
   std::string getXYStageDevice();
   std::string getAutoFocusDevice();
   std::string getImageProcessorDevice();
   std::string getSLMDevice();
   std::string getGalvoDevice();
   std::string getChannelGroup();
   void setCameraDevice(const char* cameraLabel) throw (CMMError);
   void setShutterDevice(const char* shutterLabel) throw (CMMError);
   void setFocusDevice(const char* focusLabel) throw (CMMError);
   void setXYStageDevice(const char* xyStageLabel) throw (CMMError);
   void setAutoFocusDevice(const char* focusLabel) throw (CMMError);
   void setImageProcessorDevice(const char* procLabel) throw (CMMError);
   void setSLMDevice(const char* slmLabel) throw (CMMError);
   void setGalvoDevice(const char* galvoLabel) throw (CMMError);
   void setChannelGroup(const char* channelGroup) throw (CMMError);
   //@ }


   /** @name Multiple property settings
   * A single configuration applies to multiple devices at the same time.
   */
   //@ {
   void defineConfig(const char* groupName, const char* configName) throw (CMMError);
   void defineConfig(const char* groupName, const char* configName, const char* deviceName, const char* propName, const char* value) throw (CMMError);
   void defineConfigGroup(const char* groupName) throw (CMMError);
   void deleteConfigGroup(const char* groupName) throw (CMMError);
   void renameConfigGroup(const char* oldGroupName, const char* newGroupName) throw (CMMError);
   bool isGroupDefined(const char* groupName);
   bool isConfigDefined(const char* groupName, const char* configName);
   void setConfig(const char* groupName, const char* configName) throw (CMMError);
   void deleteConfig(const char* groupName, const char* configName) throw (CMMError);
   void deleteConfig(const char* groupName, const char* configName, const char* deviceLabel, const char* propName) throw (CMMError);
   void renameConfig(const char* groupName, const char* oldConfigName, const char* newConfigName) throw (CMMError);
   std::vector<std::string> getAvailableConfigGroups() const;
   std::vector<std::string> getAvailableConfigs(const char* configGroup) const;
   std::string getCurrentConfig(const char* groupName) throw (CMMError);
   std::string getCurrentConfigFromCache(const char* groupName) throw (CMMError);
   Configuration getConfigData(const char* configGroup, const char* configName) throw (CMMError);
   std::string getCurrentPixelSizeConfig() throw (CMMError);
   std::string getCurrentPixelSizeConfig(bool cached) throw (CMMError);
   double getPixelSizeUm();
   double getPixelSizeUm(bool cached);
   double getPixelSizeUmByID(const char* resolutionID) throw (CMMError);
   double getMagnificationFactor() const;
   void setPixelSizeUm(const char* resolutionID, double pixSize)  throw (CMMError);
   void definePixelSizeConfig(const char* resolutionID, const char* deviceName, const char* propName, const char* value);
   void definePixelSizeConfig(const char* resolutionID);
   std::vector<std::string> getAvailablePixelSizeConfigs() const;
   bool isPixelSizeConfigDefined(const char* resolutionID) const;
   void setPixelSizeConfig(const char* resolutionID) throw (CMMError);
   void renamePixelSizeConfig(const char* oldConfigName, const char* newConfigName) throw (CMMError);
   void deletePixelSizeConfig(const char* configName) throw (CMMError);
   Configuration getPixelSizeConfigData(const char* configName) throw (CMMError);

   //@ }

   /** @name Imaging support
   * Imaging related API.
   */
   //@ {
   void setROI(int x, int y, int xSize, int ySize) throw (CMMError); 
   void getROI(int& x, int& y, int& xSize, int& ySize) throw (CMMError); 
   void clearROI() throw (CMMError);
   void setExposure(double exp) throw (CMMError);
   void setExposure(const char* label, double dExp) throw (CMMError);
   double getExposure() throw (CMMError);
   void* getImage() throw (CMMError);
   void* getImage(unsigned numChannel) throw (CMMError);
   void snapImage() throw (CMMError);
   unsigned getImageWidth();
   unsigned getImageHeight();
   unsigned getBytesPerPixel();
   unsigned getImageBitDepth();
   unsigned getNumberOfComponents();
   unsigned getNumberOfCameraChannels();
   std::string getCameraChannelName(unsigned int channelNr);
   long getImageBufferSize();
   void assignImageSynchro(const char* deviceLabel) throw (CMMError);
   void removeImageSynchro(const char* label) throw (CMMError);
   void removeImageSynchroAll();
   void setAutoShutter(bool state);
   bool getAutoShutter();
   void setShutterOpen(bool state) throw (CMMError);
   bool getShutterOpen() throw (CMMError);

   void startSequenceAcquisition(long numImages, double intervalMs, bool stopOnOverflow) throw (CMMError);
   void startSequenceAcquisition(const char* cameraLabel, long numImages, double intervalMs, bool stopOnOverflow) throw (CMMError);
   void prepareSequenceAcquisition(const char* cameraLabel) throw (CMMError);
   void startContinuousSequenceAcquisition(double intervalMs) throw (CMMError);
   void stopSequenceAcquisition() throw (CMMError);
   void stopSequenceAcquisition(const char* label) throw (CMMError);
   bool isSequenceRunning() throw ();
   bool isSequenceRunning(const char* label) throw (CMMError);
   void* getLastImage() throw (CMMError);
   void* popNextImage() throw (CMMError);

   void* getLastImageMD(unsigned channel, unsigned slice, Metadata& md) const throw (CMMError);
   void* popNextImageMD(unsigned channel, unsigned slice, Metadata& md) throw (CMMError);
   void* getLastImageMD(Metadata& md) const throw (CMMError);
   void* getNBeforeLastImageMD(unsigned long n, Metadata& md) const throw (CMMError);
   void* popNextImageMD(Metadata& md) throw (CMMError);

   long getRemainingImageCount();
   long getBufferTotalCapacity();
   long getBufferFreeCapacity();
   double getBufferIntervalMs() const;
   bool isBufferOverflowed() const;
   void setCircularBufferMemoryFootprint(unsigned sizeMB) throw (CMMError);
   void initializeCircularBuffer() throw (CMMError);
   void clearCircularBuffer() throw (CMMError);

   bool isExposureSequenceable(const char* cameraLabel) throw (CMMError);
   void startExposureSequence(const char* cameraLabel) throw (CMMError);
   void stopExposureSequence(const char* cameraLabel) throw (CMMError);
   long getExposureSequenceMaxLength(const char* cameraLabel) throw (CMMError);
   void loadExposureSequence(const char* cameraLabel, std::vector<double> exposureSequence_ms) throw (CMMError);
   //@ }

   /** @name Auto-focusing
   * API for controlling auto-focusing devices or software modules.
   */
   //@ {
   double getLastFocusScore();
   double getCurrentFocusScore();
   void enableContinuousFocus(bool enable) throw (CMMError);
   bool isContinuousFocusEnabled() throw (CMMError);
   bool isContinuousFocusLocked() throw (CMMError);
   bool isContinuousFocusDrive(const char* stageLabel) throw (CMMError);
   void fullFocus() throw (CMMError);
   void incrementalFocus() throw (CMMError);
   void setAutoFocusOffset(double offset) throw (CMMError);
   double getAutoFocusOffset() throw (CMMError);
   //@}

   /** @name State device support
   * API for controlling state devices (filters, turrets, etc.)
   */
   //@ {
   void setState(const char* deviceLabel, long state) throw (CMMError);
   long getState(const char* deviceLabel) throw (CMMError);
   long getNumberOfStates(const char* deviceLabel);
   void setStateLabel(const char* deviceLabel, const char* stateLabel) throw (CMMError);
   std::string getStateLabel(const char* deviceLabel) throw (CMMError);
   void defineStateLabel(const char* deviceLabel, long state, const char* stateLabel) throw (CMMError);
   std::vector<std::string> getStateLabels(const char* deviceLabel) throw (CMMError);
   long getStateFromLabel(const char* deviceLabel, const char* stateLabel) throw (CMMError);
   PropertyBlock getStateLabelData(const char* deviceLabel, const char* stateLabel);
   PropertyBlock getData(const char* deviceLabel);
   //@ }

   /** @name Property blocks
   * API for defining interchangeable equipment attributes
   */
   //@ {
   void definePropertyBlock(const char* blockName, const char* propertyName, const char* propertyValue);
   std::vector<std::string> getAvailablePropertyBlocks() const;
   PropertyBlock getPropertyBlockData(const char* blockName);
   //@ }

   /** @name Stage control
   * API for controlling one-dimensional stages
   */
   //@ {
   void setPosition(const char* deviceLabel, double position) throw (CMMError);
   double getPosition(const char* deviceLabel) throw (CMMError);
   void setRelativePosition(const char* deviceLabel, double d) throw (CMMError);
   void setOrigin(const char* deviceLabel) throw (CMMError);
   void setAdapterOrigin(const char* deviceLabel, double d) throw (CMMError);
   //@ }
   
   /** @name XYStage control
   * API for controlling XY stages
   */
   //@ {
   void setXYPosition(const char* deviceLabel, double x, double y) throw (CMMError);
   void setRelativeXYPosition(const char* deviceLabel, double dx, double dy) throw (CMMError);
   void getXYPosition(const char* deviceLabel, double &x_stage, double &y_stage) throw (CMMError);
   double getXPosition(const char* deviceLabel) throw (CMMError);
   double getYPosition(const char* deviceLabel) throw (CMMError);
   void stop(const char* deviceLabel) throw (CMMError);
   void home(const char* deviceLabel) throw (CMMError);
   void setOriginXY(const char* deviceLabel) throw (CMMError);
   void setAdapterOriginXY(const char* deviceName, double x, double y) throw (CMMError);
   //@ }

   /** @name Stage sequencing
   * API for loading sequences onto single-axis stages
   */
   //@ {
   bool isStageSequenceable(const char* label) throw (CMMError);
   void startStageSequence(const char* label) throw (CMMError);
   void stopStageSequence(const char* label) throw (CMMError);
   long getStageSequenceMaxLength(const char* label) throw (CMMError);
   void loadStageSequence(const char* label, std::vector<double> positionSequence) throw (CMMError);
   //@ }

   /** @name XY Stage sequencing
   * API for loading sequences onto XY stages
   */
   //@ {
   bool isXYStageSequenceable(const char* label) throw (CMMError);
   void startXYStageSequence(const char* label) throw (CMMError);
   void stopXYStageSequence(const char* label) throw (CMMError);
   long getXYStageSequenceMaxLength(const char* label) throw (CMMError);
   void loadXYStageSequence(const char* label,
                            std::vector<double> xSequence,
                            std::vector<double> ySequence) throw (CMMError);
   //@ }

   /** @name Serial port control
   * API for serial ports
   */
   //@ {
   void setSerialProperties(const char* portName,
      const char* answerTimeout,
      const char* baudRate,
      const char* delayBetweenCharsMs,
      const char* handshaking,
      const char* parity,
      const char* stopBits) throw (CMMError);

   void setSerialPortCommand(const char* deviceLabel, const char* command, const char* term) throw (CMMError);
   std::string getSerialPortAnswer(const char* deviceLabel, const char* term) throw (CMMError);
   void writeToSerialPort(const char* deviceLabel, const std::vector<char> &data) throw (CMMError);
   std::vector<char> readFromSerialPort(const char* deviceLabel) throw (CMMError);
   //@ }

   /** @name SLM control
   * API for spatial light modulators such as liquid crystal on silicon (LCoS), digital micromirror devices (DMD), or multimedia projectors. 
   */
   //@ {
   void setSLMImage(const char* deviceLabel, unsigned char * pixels) throw (CMMError);
   void setSLMImage(const char* deviceLabel, imgRGB32 pixels) throw (CMMError);
   void setSLMPixelsTo(const char* deviceLabel, unsigned char intensity) throw (CMMError);
   void setSLMPixelsTo(const char* deviceLabel, unsigned char red, unsigned char green, unsigned char blue) throw (CMMError);
   void displaySLMImage(const char* deviceLabel) throw (CMMError);
   void setSLMExposure(const char* deviceLabel, double exposure_ms) throw (CMMError);
   double getSLMExposure(const char* deviceLabel) throw (CMMError);
   unsigned getSLMWidth(const char* deviceLabel);
   unsigned getSLMHeight(const char* deviceLabel);
   unsigned getSLMNumberOfComponents(const char* deviceLabel);
   unsigned getSLMBytesPerPixel(const char* deviceLabel);
   long getSLMSequenceMaxLength(const char* deviceLabel);
   void startSLMSequence(const char* deviceLabel) throw (CMMError);
   void stopSLMSequence(const char* deviceLabel) throw (CMMError);
   void loadSLMSequence(const char* label, std::vector<unsigned char*> imageSequence) throw (CMMError);
   //@ }

   /** @name Galvo control
   * API for Galvo-based phototargeting devices
   */
   //@ {
   void pointGalvoAndFire(const char* deviceLabel, double x, double y, double pulseTime_us) throw (CMMError);
   void setGalvoSpotInterval(const char* deviceLabel, double pulseTime_us) throw (CMMError);
   void setGalvoPosition(const char* deviceLabel, double x, double y) throw (CMMError);
   void getGalvoPosition(const char* deviceLabel, double &x_stage, double &y_stage) throw (CMMError); // using x_stage to get swig to work
   void setGalvoIlluminationState(const char* deviceLabel, bool on) throw (CMMError);
   double getGalvoXRange(const char* deviceLabel) throw (CMMError);
   double getGalvoYRange(const char* deviceLabel) throw (CMMError);
   void addGalvoPolygonVertex(const char* deviceLabel, int polygonIndex, double x, double y) throw (CMMError);
   void deleteGalvoPolygons(const char* deviceLabel) throw (CMMError);
   void loadGalvoPolygons(const char* deviceLabel) throw (CMMError);
   void setGalvoPolygonRepetitions(const char* deviceLabel, int repetitions) throw (CMMError);
   void runGalvoPolygons(const char* deviceLabel) throw (CMMError);
   void runGalvoSequence(const char* deviceLabel) throw (CMMError);
   std::string getGalvoChannel(const char* deviceLabel) throw (CMMError);
   //@ }

   /** @name Acquisition context API
   * NOTE: experimental feature
   * API notifying core of acquisition context events
   */
   //@ {
   void acqBeforeFrame() throw (CMMError);
   void acqAfterFrame() throw (CMMError);
   //@ }

   // device discovery
   MM::DeviceDetectionStatus detectDevice(char* deviceName);

   // hubs can provide a list of peripheral devices currently attached
   std::vector<std::string> getInstalledDevices(const char* hubDeviceLabel); 
   std::string getInstalledDeviceDescription(const char* hubLabel, const char* deviceLabel);
   std::vector<std::string> getLoadedPeripheralDevices(const char* hubLabel);

   std::vector<std::string> getMACAddresses(void);


private:
   // make object non-copyable
   CMMCore(const CMMCore& /*c*/) {}
   CMMCore& operator=(const CMMCore& /*rhs*/);

   typedef std::map<std::string, Configuration*> CConfigMap;
   typedef std::map<std::string, PropertyBlock*> CPropBlockMap;

   MM::Camera* camera_;
   bool everSnapped_;
   MM::Shutter* shutter_;
   MM::Stage* focusStage_;
   MM::XYStage* xyStage_;
   MM::AutoFocus* autoFocus_;
   MM::SLM* slm_;
   MM::Galvo* galvo_;

   std::string channelGroup_;
   MM::ImageProcessor* imageProcessor_;
   long pollingIntervalMs_;
   long timeoutMs_;
   bool autoShutter_;
   MM::Core* callback_;                 // core services for devices
   ConfigGroupCollection* configGroups_;
   CorePropertyCollection* properties_;
   MMEventCallback* externalCallback_;  // notification hook to the higher layer (e.g. GUI)
   PixelSizeConfigGroup* pixelSizeGroup_;
   CircularBuffer* cbuf_;

   std::vector<MM::Device*> imageSynchro_;
   CPluginManager pluginManager_;
   std::map<int, std::string> errorText_;
   CConfigMap configs_;
   CPropBlockMap propBlocks_;
   bool debugLog_;

   // Must be unlocked when calling MMEventCallback or calling device methods
   // or acquiring a module lock
   mutable MMThreadLock stateCacheLock_;
   mutable Configuration stateCache_; // Synchronized by stateCacheLock_

   // Parameter/value validation
   static void CheckDeviceLabel(const char* label) throw (CMMError);
   static void CheckPropertyName(const char* propName) throw (CMMError);
   static void CheckPropertyValue(const char* propValue) throw (CMMError);
   static void CheckStateLabel(const char* stateLabel) throw (CMMError);
   static void CheckConfigGroupName(const char* groupName) throw (CMMError);
   static void CheckConfigPresetName(const char* presetName) throw (CMMError);
   static void CheckPropertyBlockName(const char* blockName) throw (CMMError);
   bool IsCoreDeviceLabel(const char* label) const throw (CMMError);
   MM::Device* GetDeviceWithCheckedLabel(const char* label) const throw (CMMError);
   template <class TDevice>
   TDevice* GetDeviceWithCheckedLabelAndType(const char* label) const throw (CMMError)
   {
      MM::Device* pDevice = GetDeviceWithCheckedLabel(label);
      if (pDevice->GetType() != TDevice::Type)
         throw CMMError("Device " + ToQuotedString(label) + " is of the wrong type for the requested operation");
      return static_cast<TDevice*>(pDevice);
   }

   bool isConfigurationCurrent(const Configuration& config);
   void applyConfiguration(const Configuration& config) throw (CMMError);
   int applyProperties(std::vector<PropertySetting>& props, std::string& lastError);
   void waitForDevice(MM::Device* pDev) throw (CMMError);
   Configuration getConfigGroupState(const char* group, bool fromCache) throw (CMMError);
   std::string getDeviceErrorText(int deviceCode, MM::Device* pDevice);
   std::string getDeviceName(MM::Device* pDev);
   void logError(const char* device, const char* msg, const char* file=0, int line=0);
   void updateAllowedChannelGroups();
   void assignDefaultRole(MM::Device* pDev);
   void updateCoreProperty(const char* propName, MM::DeviceType devType) throw (CMMError);
   void initializeLogging();

   MMThreadLock* pPostedErrorsLock_;
   mutable std::deque<std::pair< int, std::string> > postedErrors_;
   FastLogger* logger_;
   FastLogger* getLoggerInstance() {return logger_;}

   // >>>>> OBSOLETE >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
   void defineConfiguration(const char* configName, const char* deviceName, const char* propName, const char* value);
   bool isConfigurationDefined(const char* configName);
   void setConfiguration(const char* configName) throw (CMMError);
   void deleteConfiguration(const char* configName) throw (CMMError);
   std::vector<std::string> getAvailableConfigurations() const;
   std::string getConfiguration();
   Configuration getConfigurationData(const char* config) const throw (CMMError);
};

#endif //_MMCORE_H_
