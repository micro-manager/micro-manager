///////////////////////////////////////////////////////////////////////////////
// FILE:          MMCore.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     MMCore
//-----------------------------------------------------------------------------
// DESCRIPTION:   The interface to the MM core services. 
//              
// COPYRIGHT:     University of California, San Francisco, 2006-2014
//                100X Imaging Inc, www.100ximaging.com, 2008
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
// NOTES:         Public methods follow a slightly different naming convention than
//                the rest of the C++ code, i.e we have:
//                   getConfiguration();
//                instead of:
//                   GetConfiguration();
//                The alternative (lowercase function names) convention is used
//                because public method names appear as wrapped methods in other
//                languages, in particular Java.

/*
 * Important! Read this before changing this file.
 *
 * Please see the version number and explanatory comment in the implementation
 * file (MMCore.cpp).
 */

#ifndef _MMCORE_H_
#define _MMCORE_H_

#ifdef _MSC_VER
// We use exception specifications to instruct SWIG to generate the correct
// exception specifications for Java. Turn off the warnings that VC++ issues by
// the mere use of exception specifications (which VC++ does not implement).
#pragma warning(disable : 4290)
#endif

#include "../MMDevice/DeviceThreads.h"
#include "../MMDevice/MMDevice.h"
#include "../MMDevice/MMDeviceConstants.h"
#include "Configuration.h"
#include "CoreUtils.h"
#include "Devices/DeviceInstances.h"
#include "Error.h"
#include "ErrorCodes.h"
#include "LogManager.h"
#include "PluginManager.h"

#include <cstring>
#include <deque>
#include <map>
#include <string>
#include <vector>


#ifndef SWIG
#   ifdef _MSC_VER
#      define MMCORE_DEPRECATED(prototype) __declspec(deprecated) prototype
#   elif defined(__GNUC__)
#      define MMCORE_DEPRECATED(prototype) prototype __attribute__((deprecated))
#   else
#      define MMCORE_DEPRECATED(prototype) prototype
#   endif
#else
#   define MMCORE_DEPRECATED(prototype) prototype
#endif


class CircularBuffer;
class Configuration;
class PropertyBlock;
class ConfigGroupCollection;
class CorePropertyCollection;
class CoreCallback;
class PixelSizeConfigGroup;
class Metadata;
class MMEventCallback;
class FastLogger;

typedef unsigned int* imgRGB32;


/// The Micro-Manager Core.
/**
 * Provides a device-independent interface for hardware control. Additionally,
 * provides some facilities (such as configuration groups) for application
 * programming.
 *
 * The signatures of most of the public member functions are designed to be
 * wrapped by SWIG with minimal manual configuration.
 */
class CMMCore
{
   friend class CoreCallback;
   friend class CorePropertyCollection;

public:
   CMMCore();
   ~CMMCore();

   /// A static method that does nothing.
   /**
    * This method can be called as a sanity check when dynamically loading the
    * Core library (e.g. through a foreign function interface for a high-level
    * language).
    */
   static void noop() {}

   /** \name Initialization and setup. */
   ///@{
   void loadDevice(const char* label, const char* library, const char* adapterName) throw (CMMError);
   void unloadDevice(const char* label) throw (CMMError);
   void unloadAllDevices() throw (CMMError);
   void initializeAllDevices() throw (CMMError);
   void initializeDevice(const char* label) throw (CMMError);
   void reset() throw (CMMError);

   void unloadLibrary(const char* moduleName) throw (CMMError);

   void updateCoreProperties() throw (CMMError);

   std::string getCoreErrorText(int code) const;

   std::string getVersionInfo() const;
   std::string getAPIVersionInfo() const;
   Configuration getSystemState();
   void setSystemState(const Configuration& conf);
   Configuration getConfigState(const char* group, const char* config) throw (CMMError);
   Configuration getConfigGroupState(const char* group) throw (CMMError);
   void saveSystemState(const char* fileName) throw (CMMError);
   void loadSystemState(const char* fileName) throw (CMMError);
   void saveSystemConfiguration(const char* fileName) throw (CMMError);
   void loadSystemConfiguration(const char* fileName) throw (CMMError);
   void registerCallback(MMEventCallback* cb);
   ///@}

   /** \name Logging and log management. */
   ///@{
   void setPrimaryLogFile(const char* filename, bool truncate = false) throw (CMMError);
   std::string getPrimaryLogFile() const;

   void logMessage(const char* msg);
   void logMessage(const char* msg, bool debugOnly);
   void enableDebugLog(bool enable);
   bool debugLogEnabled();
   void enableStderrLog(bool enable);
   bool stderrLogEnabled();

   int startSecondaryLogFile(const char* filename, bool enableDebug,
         bool truncate = true, bool synchronous = false) throw (CMMError);
   void stopSecondaryLogFile(int handle) throw (CMMError);

   MMCORE_DEPRECATED(void clearLog());
   MMCORE_DEPRECATED(std::string saveLogArchive(void));
   MMCORE_DEPRECATED(std::string saveLogArchiveWithPreamble(
            char* preamble, int length));
   ///@}

   /** \name Device listing. */
   ///@{
   std::vector<std::string> getDeviceAdapterSearchPaths();
   void setDeviceAdapterSearchPaths(const std::vector<std::string>& paths);
   MMCORE_DEPRECATED(static void addSearchPath(const char *path));

   std::vector<std::string> getDeviceAdapterNames() throw (CMMError);
   MMCORE_DEPRECATED(static std::vector<std::string> getDeviceLibraries() throw (CMMError));

   std::vector<std::string> getAvailableDevices(const char* library) throw (CMMError);
   std::vector<std::string> getAvailableDeviceDescriptions(const char* library) throw (CMMError);
   std::vector<long> getAvailableDeviceTypes(const char* library) throw (CMMError);
   ///@}

   /** \name Generic device control.
    *
    * Functionality supported by all devices.
    */
   ///@{
   std::vector<std::string> getLoadedDevices() const;
   std::vector<std::string> getLoadedDevicesOfType(MM::DeviceType devType) const;
   MM::DeviceType getDeviceType(const char* label) throw (CMMError);
   std::string getDeviceLibrary(const char* label) throw (CMMError);
   std::string getDeviceName(const char* label) throw (CMMError);
   std::string getDeviceDescription(const char* label) throw (CMMError);

   std::vector<std::string> getDevicePropertyNames(const char* label) throw (CMMError);
   bool hasProperty(const char* label, const char* propName) throw (CMMError);
   std::string getProperty(const char* label, const char* propName) throw (CMMError);
   void setProperty(const char* label, const char* propName, const char* propValue) throw (CMMError);
   void setProperty(const char* label, const char* propName, const bool propValue) throw (CMMError);
   void setProperty(const char* label, const char* propName, const long propValue) throw (CMMError);
   void setProperty(const char* label, const char* propName, const float propValue) throw (CMMError);
   void setProperty(const char* label, const char* propName, const double propValue) throw (CMMError);

   std::vector<std::string> getAllowedPropertyValues(const char* label, const char* propName) throw (CMMError);
   bool isPropertyReadOnly(const char* label, const char* propName) throw (CMMError);
   bool isPropertyPreInit(const char* label, const char* propName) throw (CMMError);
   bool isPropertySequenceable(const char* label, const char* propName) throw (CMMError);
   bool hasPropertyLimits(const char* label, const char* propName) throw (CMMError);
   double getPropertyLowerLimit(const char* label, const char* propName) throw (CMMError);
   double getPropertyUpperLimit(const char* label, const char* propName) throw (CMMError);
   MM::PropertyType getPropertyType(const char* label, const char* propName) throw (CMMError);

   void startPropertySequence(const char* label, const char* propName) throw (CMMError);
   void stopPropertySequence(const char* label, const char* propName) throw (CMMError);
   long getPropertySequenceMaxLength(const char* label, const char* propName) throw (CMMError);
   void loadPropertySequence(const char* label, const char* propName, std::vector<std::string> eventSequence) throw (CMMError);

   bool deviceBusy(const char* deviceName) throw (CMMError);
   void waitForDevice(const char* deviceName) throw (CMMError);
   void waitForConfig(const char* group, const char* configName) throw (CMMError);
   bool systemBusy() throw (CMMError);
   void waitForSystem() throw (CMMError);
   void waitForImageSynchro() throw (CMMError);
   bool deviceTypeBusy(MM::DeviceType devType) throw (CMMError);
   void waitForDeviceType(MM::DeviceType devType) throw (CMMError);

   double getDeviceDelayMs(const char* label) throw (CMMError);
   void setDeviceDelayMs(const char* label, double delayMs) throw (CMMError);
   bool usesDeviceDelay(const char* label) throw (CMMError);

   void setTimeoutMs(long timeoutMs) {if (timeoutMs > 0) timeoutMs_ = timeoutMs;}
   long getTimeoutMs() { return timeoutMs_;}

   void sleep(double intervalMs) const;
   ///@}

   /** \name Management of 'current' device for specific roles. */
   ///@{
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
   ///@}

   /** \name System state cache.
    *
    * The system state cache retains the last-set or last-read value of each
    * device property.
    */
   ///@{
   Configuration getSystemStateCache() const;
   void updateSystemStateCache();
   std::string getPropertyFromCache(const char* label, const char* propName) const throw (CMMError);
   std::string getCurrentConfigFromCache(const char* groupName) throw (CMMError);
   Configuration getConfigGroupStateFromCache(const char* group) throw (CMMError);
   ///@}

   /** \name Configuration groups. */
   ///@{
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
   Configuration getConfigData(const char* configGroup, const char* configName) throw (CMMError);
   ///@}

   /** \name The pixel size configuration group. */
   ///@{
   std::string getCurrentPixelSizeConfig() throw (CMMError);
   std::string getCurrentPixelSizeConfig(bool cached) throw (CMMError);
   double getPixelSizeUm();
   double getPixelSizeUm(bool cached);
   double getPixelSizeUmByID(const char* resolutionID) throw (CMMError);
   double getMagnificationFactor() const;
   void setPixelSizeUm(const char* resolutionID, double pixSize)  throw (CMMError);
   void definePixelSizeConfig(const char* resolutionID, const char* deviceName, const char* propName, const char* value) throw (CMMError);
   void definePixelSizeConfig(const char* resolutionID) throw (CMMError);
   std::vector<std::string> getAvailablePixelSizeConfigs() const;
   bool isPixelSizeConfigDefined(const char* resolutionID) const throw (CMMError);
   void setPixelSizeConfig(const char* resolutionID) throw (CMMError);
   void renamePixelSizeConfig(const char* oldConfigName, const char* newConfigName) throw (CMMError);
   void deletePixelSizeConfig(const char* configName) throw (CMMError);
   Configuration getPixelSizeConfigData(const char* configName) throw (CMMError);
   ///@}

   /** \name Property blocks. */
   ///@{
   void definePropertyBlock(const char* blockName, const char* propertyName, const char* propertyValue);
   std::vector<std::string> getAvailablePropertyBlocks() const;
   PropertyBlock getPropertyBlockData(const char* blockName);
   ///@}

   /** \name Image acquisition. */
   ///@{
   void setROI(int x, int y, int xSize, int ySize) throw (CMMError); 
   void getROI(int& x, int& y, int& xSize, int& ySize) throw (CMMError); 
   void clearROI() throw (CMMError);

   void setExposure(double exp) throw (CMMError);
   void setExposure(const char* label, double dExp) throw (CMMError);
   double getExposure() throw (CMMError);

   void snapImage() throw (CMMError);
   void* getImage() throw (CMMError);
   void* getImage(unsigned numChannel) throw (CMMError);

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
   void setShutterOpen(const char* shutterLabel, bool state) throw (CMMError);
   bool getShutterOpen(const char* shutterLabel) throw (CMMError);

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
   MMCORE_DEPRECATED(double getBufferIntervalMs() const);
   bool isBufferOverflowed() const;
   void setCircularBufferMemoryFootprint(unsigned sizeMB) throw (CMMError);
   unsigned getCircularBufferMemoryFootprint();
   void initializeCircularBuffer() throw (CMMError);
   void clearCircularBuffer() throw (CMMError);

   bool isExposureSequenceable(const char* cameraLabel) throw (CMMError);
   void startExposureSequence(const char* cameraLabel) throw (CMMError);
   void stopExposureSequence(const char* cameraLabel) throw (CMMError);
   long getExposureSequenceMaxLength(const char* cameraLabel) throw (CMMError);
   void loadExposureSequence(const char* cameraLabel, std::vector<double> exposureSequence_ms) throw (CMMError);
   ///@}

   /** \name Autofocus control. */
   ///@{
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
   ///@}

   /** \name State device control. */
   ///@{
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
   ///@}

   /** \name Focus (Z) stage control. */
   ///@{
   void setPosition(const char* deviceLabel, double position) throw (CMMError);
   double getPosition(const char* deviceLabel) throw (CMMError);
   void setRelativePosition(const char* deviceLabel, double d) throw (CMMError);
   void setOrigin(const char* deviceLabel) throw (CMMError);
   void setAdapterOrigin(const char* deviceLabel, double d) throw (CMMError);

   bool isStageSequenceable(const char* label) throw (CMMError);
   void startStageSequence(const char* label) throw (CMMError);
   void stopStageSequence(const char* label) throw (CMMError);
   long getStageSequenceMaxLength(const char* label) throw (CMMError);
   void loadStageSequence(const char* label, std::vector<double> positionSequence) throw (CMMError);
   ///@}
   
   /** \name XY stage control. */
   ///@{
   void setXYPosition(const char* deviceLabel, double x, double y) throw (CMMError);
   void setRelativeXYPosition(const char* deviceLabel, double dx, double dy) throw (CMMError);
   void getXYPosition(const char* deviceLabel, double &x_stage, double &y_stage) throw (CMMError);
   double getXPosition(const char* deviceLabel) throw (CMMError);
   double getYPosition(const char* deviceLabel) throw (CMMError);
   void stop(const char* deviceLabel) throw (CMMError);
   void home(const char* deviceLabel) throw (CMMError);
   void setOriginXY(const char* deviceLabel) throw (CMMError);
   void setAdapterOriginXY(const char* deviceName, double x, double y) throw (CMMError);

   bool isXYStageSequenceable(const char* label) throw (CMMError);
   void startXYStageSequence(const char* label) throw (CMMError);
   void stopXYStageSequence(const char* label) throw (CMMError);
   long getXYStageSequenceMaxLength(const char* label) throw (CMMError);
   void loadXYStageSequence(const char* label,
                            std::vector<double> xSequence,
                            std::vector<double> ySequence) throw (CMMError);
   ///@}

   /** \name Serial port control. */
   ///@{
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
   ///@}

   /** \name SLM control.
    *
    * Control of spatial light modulators such as liquid crystal on silicon
    * (LCoS), digital micromirror devices (DMD), or multimedia projectors. 
    */
   ///@{
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
   ///@}

   /** \name Galvo control.
    *
    * Control of beam-steering devices.
    */
   ///@{
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
   ///@}

   /** \name Acquisition context.
    *
    * Experimental feature. Not functional. Deprecated.
    */
   ///@{
   MMCORE_DEPRECATED(void acqBeforeFrame() throw (CMMError));
   MMCORE_DEPRECATED(void acqAfterFrame() throw (CMMError));
   ///@}

   /** \name Device discovery. */
   ///@{
   MM::DeviceDetectionStatus detectDevice(char* deviceName);
   ///@}

   /** \name Hub and peripheral devices. */
   ///@{
   std::string getParentLabel(const char* label) throw (CMMError);
   void setParentLabel(const char* label, const char* parentLabel) throw (CMMError);

   std::vector<std::string> getInstalledDevices(const char* hubDeviceLabel); 
   std::string getInstalledDeviceDescription(const char* hubLabel, const char* deviceLabel);
   std::vector<std::string> getLoadedPeripheralDevices(const char* hubLabel);
   ///@}

   /** \name Miscellaneous. */
   ///@{
   std::string getUserId() const;
   std::string getHostName() const;
   std::vector<std::string> getMACAddresses(void);
   ///@}

private:
   // make object non-copyable
   CMMCore(const CMMCore&);
   CMMCore& operator=(const CMMCore&);

   typedef std::map<std::string, PropertyBlock*> CPropBlockMap;

private:
   // LogManager should be the first data member, so that it is available for
   // as long as possible during construction and (especially) destruction.
   mm::LogManager logManager_;
   boost::shared_ptr<mm::logging::Logger> appLogger_;
   boost::shared_ptr<mm::logging::Logger> coreLogger_;

   FastLogger* legacyLogger_;

   bool everSnapped_;

   boost::weak_ptr<CameraInstance> currentCameraDevice_;
   boost::weak_ptr<ShutterInstance> currentShutterDevice_;
   boost::weak_ptr<StageInstance> currentFocusDevice_;
   boost::weak_ptr<XYStageInstance> currentXYStageDevice_;
   boost::weak_ptr<AutoFocusInstance> currentAutofocusDevice_;
   boost::weak_ptr<SLMInstance> currentSLMDevice_;
   boost::weak_ptr<GalvoInstance> currentGalvoDevice_;
   boost::weak_ptr<ImageProcessorInstance> currentImageProcessor_;

   std::string channelGroup_;
   long pollingIntervalMs_;
   long timeoutMs_;
   bool autoShutter_;
   MM::Core* callback_;                 // core services for devices
   ConfigGroupCollection* configGroups_;
   CorePropertyCollection* properties_;
   MMEventCallback* externalCallback_;  // notification hook to the higher layer (e.g. GUI)
   PixelSizeConfigGroup* pixelSizeGroup_;
   CircularBuffer* cbuf_;

   std::vector< boost::shared_ptr<DeviceInstance> > imageSynchro_;
   CPluginManager pluginManager_;
   std::map<int, std::string> errorText_;
   CPropBlockMap propBlocks_;

   // Must be unlocked when calling MMEventCallback or calling device methods
   // or acquiring a module lock
   mutable MMThreadLock stateCacheLock_;
   mutable Configuration stateCache_; // Synchronized by stateCacheLock_

   MMThreadLock* pPostedErrorsLock_;
   mutable std::deque<std::pair< int, std::string> > postedErrors_;

private:
   // Parameter/value validation
   static void CheckDeviceLabel(const char* label) throw (CMMError);
   static void CheckPropertyName(const char* propName) throw (CMMError);
   static void CheckPropertyValue(const char* propValue) throw (CMMError);
   static void CheckStateLabel(const char* stateLabel) throw (CMMError);
   static void CheckConfigGroupName(const char* groupName) throw (CMMError);
   static void CheckConfigPresetName(const char* presetName) throw (CMMError);
   static void CheckPropertyBlockName(const char* blockName) throw (CMMError);
   bool IsCoreDeviceLabel(const char* label) const throw (CMMError);
   boost::shared_ptr<DeviceInstance> GetDeviceWithCheckedLabel(const char* label) const throw (CMMError);
   template <class TDeviceInstance>
   boost::shared_ptr<TDeviceInstance> GetDeviceWithCheckedLabelAndType(const char* label) const throw (CMMError)
   {
      boost::shared_ptr<DeviceInstance> device = GetDeviceWithCheckedLabel(label);
      if (device->GetType() != TDeviceInstance::RawDeviceClass::Type)
         throw CMMError("Device " + ToQuotedString(label) + " is of the wrong type for the requested operation");
      return boost::static_pointer_cast<TDeviceInstance>(device);
   }

   void applyConfiguration(const Configuration& config) throw (CMMError);
   int applyProperties(std::vector<PropertySetting>& props, std::string& lastError);
   void waitForDevice(boost::shared_ptr<DeviceInstance> pDev) throw (CMMError);
   Configuration getConfigGroupState(const char* group, bool fromCache) throw (CMMError);
   std::string getDeviceErrorText(int deviceCode, boost::shared_ptr<DeviceInstance> pDevice);
   std::string getDeviceName(boost::shared_ptr<DeviceInstance> pDev);
   void logError(const char* device, const char* msg);
   void updateAllowedChannelGroups();
   void assignDefaultRole(boost::shared_ptr<DeviceInstance> pDev);
   void updateCoreProperty(const char* propName, MM::DeviceType devType) throw (CMMError);
};

#endif //_MMCORE_H_
