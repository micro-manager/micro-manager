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
#include "Error.h"
#include "ErrorCodes.h"
#include "Logging/Logger.h"

#include <boost/shared_ptr.hpp>
#include <boost/weak_ptr.hpp>

#include <cstring>
#include <deque>
#include <map>
#include <string>
#include <vector>


#if !defined(SWIGJAVA) && !defined(SWIGPYTHON)
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


class CPluginManager;
class CircularBuffer;
class ConfigGroupCollection;
class CoreCallback;
class CorePropertyCollection;
class MMEventCallback;
class Metadata;
class PixelSizeConfigGroup;
class PropertyBlock;

class AutoFocusInstance;
class CameraInstance;
class DeviceInstance;
class GalvoInstance;
class ImageProcessorInstance;
class SLMInstance;
class ShutterInstance;
class StageInstance;
class XYStageInstance;

class CMMCore;

namespace mm {
   class DeviceManager;
   class LogManager;
} // namespace mm

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
   void loadDevice(const char* label, const char* moduleName,
         const char* deviceName) throw (CMMError);
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

   bool deviceBusy(const char* label) throw (CMMError);
   void waitForDevice(const char* label) throw (CMMError);
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
   std::string getPropertyFromCache(const char* deviceLabel,
         const char* propName) const throw (CMMError);
   std::string getCurrentConfigFromCache(const char* groupName) throw (CMMError);
   Configuration getConfigGroupStateFromCache(const char* group) throw (CMMError);
   ///@}

   /** \name Configuration groups. */
   ///@{
   void defineConfig(const char* groupName, const char* configName) throw (CMMError);
   void defineConfig(const char* groupName, const char* configName,
         const char* deviceLabel, const char* propName,
         const char* value) throw (CMMError);
   void defineConfigGroup(const char* groupName) throw (CMMError);
   void deleteConfigGroup(const char* groupName) throw (CMMError);
   void renameConfigGroup(const char* oldGroupName,
         const char* newGroupName) throw (CMMError);
   bool isGroupDefined(const char* groupName);
   bool isConfigDefined(const char* groupName, const char* configName);
   void setConfig(const char* groupName, const char* configName) throw (CMMError);
   void deleteConfig(const char* groupName, const char* configName) throw (CMMError);
   void deleteConfig(const char* groupName, const char* configName,
         const char* deviceLabel, const char* propName) throw (CMMError);
   void renameConfig(const char* groupName, const char* oldConfigName,
         const char* newConfigName) throw (CMMError);
   std::vector<std::string> getAvailableConfigGroups() const;
   std::vector<std::string> getAvailableConfigs(const char* configGroup) const;
   std::string getCurrentConfig(const char* groupName) throw (CMMError);
   Configuration getConfigData(const char* configGroup,
         const char* configName) throw (CMMError);
   ///@}

   /** \name The pixel size configuration group. */
   ///@{
   std::string getCurrentPixelSizeConfig() throw (CMMError);
   std::string getCurrentPixelSizeConfig(bool cached) throw (CMMError);
   double getPixelSizeUm();
   double getPixelSizeUm(bool cached);
   double getPixelSizeUmByID(const char* resolutionID) throw (CMMError);
   std::vector<double> getPixelSizeAffine() throw (CMMError);
   std::vector<double> getPixelSizeAffine(bool cached) throw (CMMError);
   std::vector<double> getPixelSizeAffineByID(const char* resolutionID) throw (CMMError);
   double getMagnificationFactor() const;
   void setPixelSizeUm(const char* resolutionID, double pixSize)  throw (CMMError);
   void setPixelSizeAffine(const char* resolutionID, std::vector<double> affine)  throw (CMMError);
   void definePixelSizeConfig(const char* resolutionID,
         const char* deviceLabel, const char* propName,
         const char* value) throw (CMMError);
   void definePixelSizeConfig(const char* resolutionID) throw (CMMError);
   std::vector<std::string> getAvailablePixelSizeConfigs() const;
   bool isPixelSizeConfigDefined(const char* resolutionID) const throw (CMMError);
   void setPixelSizeConfig(const char* resolutionID) throw (CMMError);
   void renamePixelSizeConfig(const char* oldConfigName,
         const char* newConfigName) throw (CMMError);
   void deletePixelSizeConfig(const char* configName) throw (CMMError);
   Configuration getPixelSizeConfigData(const char* configName) throw (CMMError);
   ///@}

   /** \name Property blocks. */
   ///@{
   void definePropertyBlock(const char* blockName, const char* propertyName,
         const char* propertyValue);
   std::vector<std::string> getAvailablePropertyBlocks() const;
   PropertyBlock getPropertyBlockData(const char* blockName);
   ///@}

   /** \name Image acquisition. */
   ///@{
   void setROI(int x, int y, int xSize, int ySize) throw (CMMError);
   void setROI(const char* label, int x, int y, int xSize, int ySize) throw (CMMError);
   void getROI(int& x, int& y, int& xSize, int& ySize) throw (CMMError);
   void getROI(const char* label, int& x, int& y, int& xSize, int& ySize) throw (CMMError);
   void clearROI() throw (CMMError);

   bool isMultiROISupported() throw (CMMError);
   bool isMultiROIEnabled() throw (CMMError);
   void setMultiROI(std::vector<unsigned> xs, std::vector<unsigned> ys,
           std::vector<unsigned> widths,
           std::vector<unsigned> heights) throw (CMMError);
   void getMultiROI(std::vector<unsigned>& xs, std::vector<unsigned>& ys,
           std::vector<unsigned>& widths,
           std::vector<unsigned>& heights) throw (CMMError);

   void setExposure(double exp) throw (CMMError);
   void setExposure(const char* cameraLabel, double dExp) throw (CMMError);
   double getExposure() throw (CMMError);
   double getExposure(const char* label) throw (CMMError);

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
   void removeImageSynchro(const char* deviceLabel) throw (CMMError);
   void removeImageSynchroAll();

   void setAutoShutter(bool state);
   bool getAutoShutter();
   void setShutterOpen(bool state) throw (CMMError);
   bool getShutterOpen() throw (CMMError);
   void setShutterOpen(const char* shutterLabel, bool state) throw (CMMError);
   bool getShutterOpen(const char* shutterLabel) throw (CMMError);

   void startSequenceAcquisition(long numImages, double intervalMs,
         bool stopOnOverflow) throw (CMMError);
   void startSequenceAcquisition(const char* cameraLabel, long numImages,
         double intervalMs, bool stopOnOverflow) throw (CMMError);
   void prepareSequenceAcquisition(const char* cameraLabel) throw (CMMError);
   void startContinuousSequenceAcquisition(double intervalMs) throw (CMMError);
   void stopSequenceAcquisition() throw (CMMError);
   void stopSequenceAcquisition(const char* cameraLabel) throw (CMMError);
   bool isSequenceRunning() throw ();
   bool isSequenceRunning(const char* cameraLabel) throw (CMMError);

   void* getLastImage() throw (CMMError);
   void* popNextImage() throw (CMMError);
   void* getLastImageMD(unsigned channel, unsigned slice, Metadata& md)
      const throw (CMMError);
   void* popNextImageMD(unsigned channel, unsigned slice, Metadata& md)
      throw (CMMError);
   void* getLastImageMD(Metadata& md) const throw (CMMError);
   void* getNBeforeLastImageMD(unsigned long n, Metadata& md)
      const throw (CMMError);
   void* popNextImageMD(Metadata& md) throw (CMMError);

   long getRemainingImageCount();
   long getBufferTotalCapacity();
   long getBufferFreeCapacity();
   bool isBufferOverflowed() const;
   void setCircularBufferMemoryFootprint(unsigned sizeMB) throw (CMMError);
   unsigned getCircularBufferMemoryFootprint();
   void initializeCircularBuffer() throw (CMMError);
   void clearCircularBuffer() throw (CMMError);

   bool isExposureSequenceable(const char* cameraLabel) throw (CMMError);
   void startExposureSequence(const char* cameraLabel) throw (CMMError);
   void stopExposureSequence(const char* cameraLabel) throw (CMMError);
   long getExposureSequenceMaxLength(const char* cameraLabel) throw (CMMError);
   void loadExposureSequence(const char* cameraLabel,
         std::vector<double> exposureSequence_ms) throw (CMMError);
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
   void setState(const char* stateDeviceLabel, long state) throw (CMMError);
   long getState(const char* stateDeviceLabel) throw (CMMError);
   long getNumberOfStates(const char* stateDeviceLabel);
   void setStateLabel(const char* stateDeviceLabel,
         const char* stateLabel) throw (CMMError);
   std::string getStateLabel(const char* stateDeviceLabel) throw (CMMError);
   void defineStateLabel(const char* stateDeviceLabel,
         long state, const char* stateLabel) throw (CMMError);
   std::vector<std::string> getStateLabels(const char* stateDeviceLabel)
      throw (CMMError);
   long getStateFromLabel(const char* stateDeviceLabel,
         const char* stateLabel) throw (CMMError);
   PropertyBlock getStateLabelData(const char* stateDeviceLabel,
         const char* stateLabel);
   PropertyBlock getData(const char* stateDeviceLabel);
   ///@}

   /** \name Focus (Z) stage control. */
   ///@{
   void setPosition(const char* stageLabel, double position) throw (CMMError);
   void setPosition(double position) throw (CMMError);
   double getPosition(const char* stageLabel) throw (CMMError);
   double getPosition() throw (CMMError);
   void setRelativePosition(const char* stageLabel, double d) throw (CMMError);
   void setRelativePosition(double d) throw (CMMError);
   void setOrigin(const char* stageLabel) throw (CMMError);
   void setOrigin() throw (CMMError);
   void setAdapterOrigin(const char* stageLabel, double newZUm) throw (CMMError);
   void setAdapterOrigin(double newZUm) throw (CMMError);

   void setFocusDirection(const char* stageLabel, int sign);
   int getFocusDirection(const char* stageLabel) throw (CMMError);

   bool isStageSequenceable(const char* stageLabel) throw (CMMError);
   void startStageSequence(const char* stageLabel) throw (CMMError);
   void stopStageSequence(const char* stageLabel) throw (CMMError);
   long getStageSequenceMaxLength(const char* stageLabel) throw (CMMError);
   void loadStageSequence(const char* stageLabel,
         std::vector<double> positionSequence) throw (CMMError);
   ///@}
   
   /** \name XY stage control. */
   ///@{
   void setXYPosition(const char* xyStageLabel,
         double x, double y) throw (CMMError);
   void setXYPosition(double x, double y) throw (CMMError);
   void setRelativeXYPosition(const char* xyStageLabel,
         double dx, double dy) throw (CMMError);
   void setRelativeXYPosition(double dx, double dy) throw (CMMError);
   void getXYPosition(const char* xyStageLabel,
         double &x_stage, double &y_stage) throw (CMMError);
   void getXYPosition(double &x_stage, double &y_stage) throw (CMMError);
   double getXPosition(const char* xyStageLabel) throw (CMMError);
   double getYPosition(const char* xyStageLabel) throw (CMMError);
   double getXPosition() throw (CMMError);
   double getYPosition() throw (CMMError);
   void stop(const char* xyOrZStageLabel) throw (CMMError);
   void home(const char* xyOrZStageLabel) throw (CMMError);
   void setOriginXY(const char* xyStageLabel) throw (CMMError);
   void setOriginXY() throw (CMMError);
   void setOriginX(const char* xyStageLabel) throw (CMMError);
   void setOriginX() throw (CMMError);
   void setOriginY(const char* xyStageLabel) throw (CMMError);
   void setOriginY() throw (CMMError);
   void setAdapterOriginXY(const char* xyStageLabel,
         double newXUm, double newYUm) throw (CMMError);
   void setAdapterOriginXY(double newXUm, double newYUm) throw (CMMError);

   bool isXYStageSequenceable(const char* xyStageLabel) throw (CMMError);
   void startXYStageSequence(const char* xyStageLabel) throw (CMMError);
   void stopXYStageSequence(const char* xyStageLabel) throw (CMMError);
   long getXYStageSequenceMaxLength(const char* xyStageLabel) throw (CMMError);
   void loadXYStageSequence(const char* xyStageLabel,
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

   void setSerialPortCommand(const char* portLabel, const char* command,
         const char* term) throw (CMMError);
   std::string getSerialPortAnswer(const char* portLabel,
         const char* term) throw (CMMError);
   void writeToSerialPort(const char* portLabel,
         const std::vector<char> &data) throw (CMMError);
   std::vector<char> readFromSerialPort(const char* portLabel)
      throw (CMMError);
   ///@}

   /** \name SLM control.
    *
    * Control of spatial light modulators such as liquid crystal on silicon
    * (LCoS), digital micromirror devices (DMD), or multimedia projectors. 
    */
   ///@{
   void setSLMImage(const char* slmLabel,
         unsigned char * pixels) throw (CMMError);
   void setSLMImage(const char* slmLabel, imgRGB32 pixels) throw (CMMError);
   void setSLMPixelsTo(const char* slmLabel,
         unsigned char intensity) throw (CMMError);
   void setSLMPixelsTo(const char* slmLabel,
         unsigned char red, unsigned char green,
         unsigned char blue) throw (CMMError);
   void displaySLMImage(const char* slmLabel) throw (CMMError);
   void setSLMExposure(const char* slmLabel, double exposure_ms)
      throw (CMMError);
   double getSLMExposure(const char* slmLabel) throw (CMMError);
   unsigned getSLMWidth(const char* slmLabel);
   unsigned getSLMHeight(const char* slmLabel);
   unsigned getSLMNumberOfComponents(const char* slmLabel);
   unsigned getSLMBytesPerPixel(const char* slmLabel);

   long getSLMSequenceMaxLength(const char* slmLabel);
   void startSLMSequence(const char* slmLabel) throw (CMMError);
   void stopSLMSequence(const char* slmLabel) throw (CMMError);
   void loadSLMSequence(const char* slmLabel,
         std::vector<unsigned char*> imageSequence) throw (CMMError);
   ///@}

   /** \name Galvo control.
    *
    * Control of beam-steering devices.
    */
   ///@{
   void pointGalvoAndFire(const char* galvoLabel, double x, double y,
         double pulseTime_us) throw (CMMError);
   void setGalvoSpotInterval(const char* galvoLabel,
         double pulseTime_us) throw (CMMError);
   void setGalvoPosition(const char* galvoLabel, double x, double y)
      throw (CMMError);
   void getGalvoPosition(const char* galvoLabel,
         double &x_stage, double &y_stage) throw (CMMError); // using x_stage to get swig to work
   void setGalvoIlluminationState(const char* galvoLabel, bool on)
      throw (CMMError);
   double getGalvoXRange(const char* galvoLabel) throw (CMMError);
   double getGalvoXMinimum(const char* galvoLabel) throw (CMMError);
   double getGalvoYRange(const char* galvoLabel) throw (CMMError);
   double getGalvoYMinimum(const char* galvoLabel) throw (CMMError);
   void addGalvoPolygonVertex(const char* galvoLabel, int polygonIndex,
         double x, double y) throw (CMMError);
   void deleteGalvoPolygons(const char* galvoLabel) throw (CMMError);
   void loadGalvoPolygons(const char* galvoLabel) throw (CMMError);
   void setGalvoPolygonRepetitions(const char* galvoLabel, int repetitions)
      throw (CMMError);
   void runGalvoPolygons(const char* galvoLabel) throw (CMMError);
   void runGalvoSequence(const char* galvoLabel) throw (CMMError);
   std::string getGalvoChannel(const char* galvoLabel) throw (CMMError);
   ///@}

   /** \name Device discovery. */
   ///@{
   bool supportsDeviceDetection(char* deviceLabel);
   MM::DeviceDetectionStatus detectDevice(char* deviceLabel);
   ///@}

   /** \name Hub and peripheral devices. */
   ///@{
   std::string getParentLabel(const char* peripheralLabel) throw (CMMError);
   void setParentLabel(const char* deviceLabel,
         const char* parentHubLabel) throw (CMMError);

   std::vector<std::string> getInstalledDevices(const char* hubLabel) throw (CMMError);
   std::string getInstalledDeviceDescription(const char* hubLabel,
         const char* peripheralLabel) throw (CMMError);
   std::vector<std::string> getLoadedPeripheralDevices(const char* hubLabel) throw (CMMError);
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
   boost::shared_ptr<mm::LogManager> logManager_;
   mm::logging::Logger appLogger_;
   mm::logging::Logger coreLogger_;

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

   std::vector< boost::weak_ptr<DeviceInstance> > imageSynchroDevices_;
   boost::shared_ptr<CPluginManager> pluginManager_;
   boost::shared_ptr<mm::DeviceManager> deviceManager_;
   std::map<int, std::string> errorText_;
   CPropBlockMap propBlocks_;

   // Must be unlocked when calling MMEventCallback or calling device methods
   // or acquiring a module lock
   mutable MMThreadLock stateCacheLock_;
   mutable Configuration stateCache_; // Synchronized by stateCacheLock_

   MMThreadLock* pPostedErrorsLock_;
   mutable std::deque<std::pair< int, std::string> > postedErrors_;

private:
   void InitializeErrorMessages();
   void CreateCoreProperties();

   // Parameter/value validation
   static void CheckDeviceLabel(const char* label) throw (CMMError);
   static void CheckPropertyName(const char* propName) throw (CMMError);
   static void CheckPropertyValue(const char* propValue) throw (CMMError);
   static void CheckStateLabel(const char* stateLabel) throw (CMMError);
   static void CheckConfigGroupName(const char* groupName) throw (CMMError);
   static void CheckConfigPresetName(const char* presetName) throw (CMMError);
   static void CheckPropertyBlockName(const char* blockName) throw (CMMError);
   bool IsCoreDeviceLabel(const char* label) const throw (CMMError);

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
   void loadSystemConfigurationImpl(const char* fileName) throw (CMMError);
};

#endif //_MMCORE_H_
