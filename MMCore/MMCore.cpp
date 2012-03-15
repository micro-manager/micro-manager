//////////////////////////////////////////////////////////////////////////////
// FILE:          MMCore.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     MMCore
//-----------------------------------------------------------------------------
// DESCRIPTION:   The interface to the MM core services. 
//              
// COPYRIGHT:     University of California, San Francisco, 2006
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
// REVISIONS:     see MMCore.h
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


#include "CircularBuffer.h"
#include "Compressor.h"
#include "Configuration.h"
#include "ConfigGroup.h"
#include "CoreCallback.h"
#include "CoreProperty.h"
#include "CoreUtils.h"
#include "FastLogger.h"

#include "Host.h"
#include "MMCore.h"
#include "MMEventCallback.h"
#include "../MMDevice/DeviceUtils.h"
#include "../MMDevice/DeviceThreads.h"
#include "../MMDevice/ModuleInterface.h"
#include "../MMDevice/ImageMetadata.h"
#include <assert.h>
#include <sstream>
#include <algorithm>
#include <vector>
#include <ostream>

#include "boost/date_time/posix_time/posix_time.hpp"



#ifndef _WINDOWS
// Needed on Unix for getcwd() and gethostname()
#include <pwd.h>
#include <sys/types.h>
#include <unistd.h>
#else
// for _getcwd
#include <direct.h>
#endif

using namespace std;

// constants
#ifdef linux
const char* g_logFileName = "/tmp/CoreLog";
#else
const char* g_logFileName = "CoreLog";
#endif

const char* g_CoreName = "MMCore";

// version info
const int MMCore_versionMajor = 2;
const int MMCore_versionMinor = 3;
const int MMCore_versionBuild = 1;

// mutex
MMThreadLock CMMCore::deviceLock_;

///////////////////////////////////////////////////////////////////////////////
// CMMcore class
// -------------

/**
 * Constructor.
 * Initializes buffers and error message text. It does not load any hardware
 * devices at this point.
 */
CMMCore::CMMCore() :
   camera_(0), everSnapped_(false), shutter_(0), focusStage_(0), xyStage_(0), autoFocus_(0), slm_(0), galvo_(0), imageProcessor_(0), pollingIntervalMs_(10), timeoutMs_(5000),
   logStream_(0), autoShutter_(true), callback_(0), configGroups_(0), properties_(0), externalCallback_(0), pixelSizeGroup_(0), cbuf_(0), pPostedErrorsLock_(NULL)
{
   // get current working directory
#ifdef _WINDOWS
   pathBuf_ = _getcwd(NULL, 0);
#else
   pathBuf_ = getcwd(NULL,0);
#endif

   configGroups_ = new ConfigGroupCollection();
   pixelSizeGroup_ = new PixelSizeConfigGroup();
   pPostedErrorsLock_ = new MMThreadLock();

   // build list of error strings
   errorText_[MMERR_OK] = "No errors.";
   errorText_[MMERR_GENERIC] = "Core error occured.";
   errorText_[MMERR_DEVICE_GENERIC] = "Device error encountered.";
   errorText_[MMERR_NoDevice] = "Device not defined or initialized.";
   errorText_[MMERR_SetPropertyFailed] = "Property does not exist, or value not allowed.";
   errorText_[MMERR_LoadLibraryFailed] = "Unable to load library: file not accessible or corrupted.";
   errorText_[MMERR_LibraryFunctionNotFound] =
      "Unable to identify expected interface: the library is not comaptible or corrupted.";
   errorText_[MMERR_CreateNotFound] =
      "Unable to identify CreateDevice function: the library is not comaptible or corrupted.";
   errorText_[MMERR_DeleteNotFound] =
      "Unable to identify DeleteDevice function: the library is not comaptible or corrupted.";
   errorText_[MMERR_CreateFailed] = "DeviceCreate function failed.";
   errorText_[MMERR_DeleteFailed] = "DeviceDelete function failed.";
   errorText_[MMERR_UnknownModule] = "Current device can't be unloaded: using unknown library.";
   errorText_[MMERR_UnexpectedDevice] = "Unexpected device encountered.";
   errorText_[MMERR_ModuleVersionMismatch] = "Module version mismatch.";
   errorText_[MMERR_DeviceVersionMismatch] = "Device interface version mismatch.";
   errorText_[MMERR_DeviceUnloadFailed] = "Requested device seems fine, but the current one failed to unload.";
   errorText_[MMERR_CameraNotAvailable] = "Camera not loaded or initialized.";
   errorText_[MMERR_InvalidStateDevice] = "Unsupported API. This device is not a state device";
   errorText_[MMERR_NoConfiguration] = "Configuration not defined";
   errorText_[MMERR_InvalidPropertyBlock] = "Property block not defined";
   errorText_[MMERR_UnhandledException] = "Internal inconsistency: unknown system exception encountered";
   errorText_[MMERR_DevicePollingTimeout] = "Device timed out";
   errorText_[MMERR_InvalidShutterDevice] = "Unsupported interface. This device is not a shutter.";
   errorText_[MMERR_DuplicateLabel] = "Specified device label already in use.";
   errorText_[MMERR_InvalidSerialDevice] = "Unsupported interface. The specified device is not a serial port.";
   errorText_[MMERR_InvalidSpecificDevice] = "Unsupported interface. Device is not of the correct type.";
   errorText_[MMERR_InvalidLabel] = "Can't find the device with the specified label.";
   errorText_[MMERR_FileOpenFailed] = "File open failed.";
   errorText_[MMERR_InvalidCFGEntry] = "Invalid configuration file line encountered. Wrong number of tokens for the current context.";
   errorText_[MMERR_InvalidContents] = "Reserved character(s) encountered in the value or name string.";
   errorText_[MMERR_InvalidCoreProperty] = "Unrecognized core property.";
   errorText_[MMERR_InvalidCoreValue] = "Core property is read-only or the requested value is not allowed.";
   errorText_[MMERR_NoConfigGroup] = "Configuration group not defined";
   errorText_[MMERR_DuplicateConfigGroup] = "Group name already in use.";
   errorText_[MMERR_CameraBufferReadFailed] = "Camera image buffer read failed.";
   errorText_[MMERR_CircularBufferFailedToInitialize] = "Failed to initialize circular buffer - memory requirements not adequate.";
   errorText_[MMERR_CircularBufferEmpty] = "Circular buffer is empty.";
   errorText_[MMERR_ContFocusNotAvailable] = "Auto-focus focus device not defined.";
   errorText_[MMERR_BadConfigName] = "Configuration name contains illegale characters (/\\*!')";
   errorText_[MMERR_NotAllowedDuringSequenceAcquisition] = "This operation can not be executed while sequence acquisition is runnning.";
   errorText_[MMERR_OutOfMemory] = "Out of memory.";
	errorText_[MMERR_InvalidImageSequence] = "Issue snapImage before getImage.";
   errorText_[MMERR_NullPointerException] = "Null Pointer Exception.";
   errorText_[MMERR_CreatePeripheralFailed] = "Hub failed to create specified peripheral device.";

   initializeLogging();
   CORE_LOG("-------->>\n");
   CORE_LOG2("Core session started on %D by %s on %s\n", getUserId().c_str(), getHostName().c_str());
   enableDebugLog(false);

	try
	{
		callback_ = new CoreCallback(this);
		cbuf_ = new CircularBuffer(10); // allocate 10MB initially

		// build the core property collection
		properties_ = new CorePropertyCollection(this);
		
		if( (NULL == callback_) || (NULL == cbuf_) || ( NULL == properties_)  )
		{
			CORE_LOG("Error in the core constructor\n initial allocations failed\n");
		}

		// set-up core properties
		try
		{

			// Initialize
			CoreProperty propInit("0", false);
			propInit.AddAllowedValue("0");
			propInit.AddAllowedValue("1");
			properties_->Add(MM::g_Keyword_CoreInitialize, propInit);

			// Auto shutter
			CoreProperty propAutoShutter("1", false);
			propAutoShutter.AddAllowedValue("0");
			propAutoShutter.AddAllowedValue("1");
			properties_->Add(MM::g_Keyword_CoreAutoShutter, propAutoShutter);

			// Camera device
			CoreProperty propCamera;
			properties_->Add(MM::g_Keyword_CoreCamera, propCamera);
         properties_->AddAllowedValue(MM::g_Keyword_CoreCamera, "");

			// Shutter device
			CoreProperty propShutter;
			properties_->Add(MM::g_Keyword_CoreShutter, propShutter);
         properties_->AddAllowedValue(MM::g_Keyword_CoreShutter, "");

			// Focus device
			CoreProperty propFocus;
			properties_->Add(MM::g_Keyword_CoreFocus, propFocus);
         properties_->AddAllowedValue(MM::g_Keyword_CoreFocus, "");

			// XYStage device
			CoreProperty propXYStage;
			properties_->Add(MM::g_Keyword_CoreXYStage, propXYStage);
         properties_->AddAllowedValue(MM::g_Keyword_CoreXYStage, "");

			// Auto-focus device
			CoreProperty propAutoFocus;
			properties_->Add(MM::g_Keyword_CoreAutoFocus, propAutoFocus);
         properties_->AddAllowedValue(MM::g_Keyword_CoreAutoFocus, "");

			// Processor device
			CoreProperty propImageProc;
			properties_->Add(MM::g_Keyword_CoreImageProcessor, propImageProc);
         properties_->AddAllowedValue(MM::g_Keyword_CoreImageProcessor, "");

			// SLM device
			CoreProperty propSLM;
			properties_->Add(MM::g_Keyword_CoreSLM, propSLM);
         properties_->AddAllowedValue(MM::g_Keyword_CoreSLM, "");

			// SLM device
			CoreProperty propGalvo;
			properties_->Add(MM::g_Keyword_CoreGalvo, propGalvo);
         properties_->AddAllowedValue(MM::g_Keyword_CoreGalvo, "");

			// channel group
			CoreProperty propChannelGroup;
			properties_->Add(MM::g_Keyword_CoreChannelGroup, propChannelGroup);
			properties_->AddAllowedValue(MM::g_Keyword_CoreChannelGroup, "");

			// Time after which we give up on checking the Busy flag status
			CoreProperty propBusyTimeoutMs;
			properties_->Add(MM::g_Keyword_CoreTimeoutMs, propBusyTimeoutMs);

         properties_->Refresh();
		}
		catch(CMMError& err)
		{
			// trap exceptions and just log the message
			CORE_LOG1("Error in the core constructor\n%s\n", err.getMsg().c_str());
		}
	}
	catch(bad_alloc& memex)
	{
      // trap exceptions and just log the message
      CORE_LOG1("Error in the core constructor\n%s\n", memex.what() );
	}
}

/**
 * Destructor.
 * Cleans-up and unloads all devices.
 */
CMMCore::~CMMCore()
{
   try {
      reset();
   } catch (...) {
      CORE_LOG("Exception occured in MMCore destructor.\n");
      ; // don't let any exceptions leak through
   }
   CORE_LOG("Core session ended on %D\n");

   shutdownLogging();

   delete logStream_;
   delete callback_;
   delete configGroups_;
   delete properties_;
   delete cbuf_;
   delete pixelSizeGroup_;
   delete pPostedErrorsLock_;


	if(NULL!= pathBuf_) 
		free (pathBuf_); // kh
}

/**
 * Delete an exisiting log file and start a new one.
 */
void CMMCore::clearLog()
{
   IMMLogger::Instance()->Reset();
   CORE_LOG("-------->>\n");
   CORE_LOG2("\nLog cleared and re-started on %D by %s on %s\n", getUserId().c_str(), getHostName().c_str());
}

/**
 * Record text message in the log file.
 */
void CMMCore::logMessage(const char* msg)
{
   CORE_LOG1("> %s\n", msg);
}


/**
 * Record text message in the log file.
 */
void CMMCore::logMessage(const char* msg, bool debugOnly)
{
  if (debugOnly) {
    CORE_DEBUG1("> %s\n", msg);
  } else {
    CORE_LOG1("> %s\n", msg);
  }
}



/**
 * Enable or disable logging of debug messages.
 * @param enable - if set to true debug messages will be recorded in the log file 
 */
void CMMCore::enableDebugLog(bool enable)
{
   debugLog_ = enable;
   IMMLogger::Instance()->SetPriorityLevel(debugLog_?IMMLogger::debug:IMMLogger::info);

   CORE_LOG1("Debug logging %s\n", enable ? "enabled" : "disabled");
}

/**
 * Enables or disables log message display on the standard console.
 * @param enable - if set to true, log file messages will be echoed on the stderr.
 */
void CMMCore::enableStderrLog(bool enable)
{
   IMMLogger::Instance()->EnableLogToStderr(enable);
}

/*!
 Displays current user name.
 */
string CMMCore::getUserId() const
{
   char buf[8192];
#ifndef _WINDOWS
   struct passwd* ppw = getpwuid(geteuid());
   strcpy( buf, ppw->pw_name);
#else
   DWORD bufCharCount = 8192;
   if( !GetUserName( buf, &bufCharCount ) )
      buf[0] = 0;
#endif
   return string(buf);
}

/**
 * return current computer name.
 */
string CMMCore::getHostName() const
{
   char buf[8192];
#ifndef _WINDOWS
   gethostname(buf, 8192);
#else
   DWORD bufCharCount = 8192;
   if( !GetComputerName( buf, &bufCharCount ) )
      buf[0] = 0;
#endif
   return string(buf);
}

/**
 * Displays core version.
 */
string CMMCore::getVersionInfo() const
{
   ostringstream txt;
   string debug;
   txt << "MMCore version " << MMCore_versionMajor << "." << MMCore_versionMinor << "." << MMCore_versionBuild;
   #ifdef _DEBUG
   txt << " (debug)";
   #endif
   return txt.str();
}

/**
 * Get available devices from the specified device library.
 */
vector<string> CMMCore::getAvailableDevices(const char* library) throw (CMMError)
{
   try
   {
      return CPluginManager::GetAvailableDevices(library);
   }
   catch (CMMError& /*e*/)
   {
      // logError("core", e.getMsg().c_str());
      throw;
   }
}
/**
 * Get descriptions for available devices from the specified library.
 */
vector<string> CMMCore::getAvailableDeviceDescriptions(const char* library) throw (CMMError)
{
   try
   {
      return CPluginManager::GetAvailableDeviceDescriptions(library);
   }
   catch (CMMError& e)
   {
      logError("core", e.getMsg().c_str());
      throw;
   }
}
/**
 * Get type information for available devices from the specified library.
 */
vector<long> CMMCore::getAvailableDeviceTypes(const char* library) throw (CMMError)
{
   try
   {
      return CPluginManager::GetAvailableDeviceTypes(library);
   }
   catch (CMMError& e)
   {
      logError("core", e.getMsg().c_str());
      throw;
   }
}

/**
 * Displays the module and device interface versions.
 */
string CMMCore::getAPIVersionInfo() const
{
   ostringstream txt;
   txt << "Device API version " << DEVICE_INTERFACE_VERSION << ", " << "Module API version " << MODULE_INTERFACE_VERSION;
   return txt.str();
}

/**
 * Returns the entire system state, i.e. the collection of all property values from all devices.
 * @return - Configuration object containing a collection of device-property-value triplets
 */
Configuration CMMCore::getSystemState() const
{
   Configuration config;
   vector<string> devices = pluginManager_.GetDeviceList();
   vector<string>::const_iterator i;
   for (i=devices.begin(); i!=devices.end(); i++)
   {
      MM::Device* pDev = getDevice(i->c_str());
      for (unsigned j=0; j<pDev->GetNumberOfProperties(); j++)
      {
         char name[MM::MaxStrLength]="";
         char val[MM::MaxStrLength]="";
         bool readOnly;
         pDev->GetPropertyName(j, name);
         pDev->GetPropertyReadOnly(name, readOnly);
         pDev->GetProperty(name, val);
         config.addSetting(PropertySetting(i->c_str(), name, val, readOnly));
     }   
   }

   // add core properties
   vector<string> coreProps = properties_->GetNames();
   for (unsigned i=0; i < coreProps.size(); i++)
   {
      string name = coreProps[i];
      string val = properties_->Get(name.c_str());
      config.addSetting(PropertySetting(MM::g_Keyword_CoreDevice, name.c_str(), val.c_str(), properties_->IsReadOnly(name.c_str())));
   }

   return config;
}

/**
 * Returns the entire system state, i.e. the collection of all property values from all devices.
 * This method will return cached values instead of querying each device
 * @return - Configuration object containing a collection of device-property-value triplets
 */
Configuration CMMCore::getSystemStateCache() const
{
   return stateCache_;
}

/**
 * Returns a partial state of the system, only for devices included in the
 * specified configuration.
 */
Configuration CMMCore::getConfigState(const char* group, const char* config) const throw (CMMError)
{
   Configuration cfgData = getConfigData(group, config);
   Configuration state;
   for (size_t i=0; i < cfgData.size(); i++)
   {
      PropertySetting cs = cfgData.getSetting(i); // config setting
      string value = getProperty(cs.getDeviceLabel().c_str(), cs.getPropertyName().c_str());
      PropertySetting ss(cs.getDeviceLabel().c_str(), cs.getPropertyName().c_str(), value.c_str()); // state setting
      state.addSetting(ss);
   }
   return state;
}


/**
 * Returns the parital state of the system, only for the devices included in the
 * specified group. It will create a union of all devices referenced in a group.
 */
Configuration CMMCore::getConfigGroupState(const char* group) const throw (CMMError)
{

   vector<string> configs = configGroups_->GetAvailableConfigs(group);
   Configuration state;
   for (size_t i=0; i<configs.size(); i++) {
      Configuration cfgData = getConfigData(group, configs[i].c_str());
      for (size_t i=0; i < cfgData.size(); i++)
      {
         PropertySetting cs = cfgData.getSetting(i); // config setting
         if (!state.isPropertyIncluded(cs.getDeviceLabel().c_str(), cs.getPropertyName().c_str()))
         {
            string value = getProperty(cs.getDeviceLabel().c_str(), cs.getPropertyName().c_str());
            PropertySetting ss(cs.getDeviceLabel().c_str(), cs.getPropertyName().c_str(), value.c_str()); // state setting
            state.addSetting(ss);
         }
      }
   }
   return state;
}

/**
 * Sets all properties contained in the Configuration object.
 * The procedure will attempt to set each property it enocunters, but won't stop
 * if any of the properties fail or if the requested device is not present. It will
 * just quietly continue.
 *
 * @param conf - configuration object
 */
void CMMCore::setSystemState(const Configuration& conf)
{
   for (unsigned i=0; i<conf.size(); i++)
   {
      PropertySetting s = conf.getSetting(i);
      if (!s.getReadOnly())
      {
         try
         {
            setProperty(s.getDeviceLabel().c_str(), s.getPropertyName().c_str(), s.getPropertyValue().c_str());
         }
         catch (CMMError& err)
         {
            CORE_LOG3("Property setting failed: %s-%s-%s\n",
            s.getDeviceLabel().c_str(), s.getPropertyName().c_str(), s.getPropertyValue().c_str());
            CORE_LOG1("%s\n", err.getMsg().c_str());
         }
      }
   }

   updateSystemStateCache();
}

/**
 * Add a list of paths to the search path of the plugin manager.
 *
 * We need to make sure that the drivers discovered with getDeviceLibraries()
 * can actually be loaded. This is only possible if we require the search
 * path to be set in the plugin manager, and force the plugin manager respect
 * this setting for both discovery and loading drivers.
 *
 * @param paths - search path.
 */
void CMMCore::addSearchPath(const char *path)
{
   CPluginManager::AddSearchPath(string(path));
}

/**
 * Returns a list of library names available in the search path.
 */
vector<string> CMMCore::getDeviceLibraries() throw (CMMError)
{
   return CPluginManager::GetModules();
}

/**
 * Loads a device from the plugin library.
 * @param label assigned name for the device during the core session
 * @param library the name of the plugin library (dll). The name should be supplied without the
 *                extension and path since the naming convention and locations are platform
 *                dependent
 * @param device the name of the device. The name must correspond to one of the names recognized
 *                   by the specific plugin library.
 */
void CMMCore::loadDevice(const char* label, const char* library, const char* device) throw (CMMError)
{

   try
   {
      MM::Device* pDevice = pluginManager_.LoadDevice(label, library, device);
      CORE_LOG3("Device %s loaded from %s and labeled as %s\n", device, library, label);
      pDevice->SetCallback(callback_);
      assignDefaultRole(pDevice);
   }
   catch (CMMError& err)
   {
      // augment the error message with the core text
      err.setCoreMsg(getCoreErrorText(err.getCode()).c_str());
      logError("MMCore::loadDevice", err.getMsg().c_str());
      throw;
   }
}

void CMMCore::assignDefaultRole(MM::Device* pDevice)
{
   // default special roles for particular devices
   // The roles which are assigned at the load time will make sense for a simple
   // configuration. More complicated configurations will typically override default settings.
   char label[MM::MaxStrLength];
   pDevice->GetLabel(label);
   switch(pDevice->GetType())
   {
      case MM::CameraDevice:
         /* >>> Linux build asserts false here - apparently rtti does not work accross DLL!?
         camera_ = dynamic_cast<MM::Camera*>(pDevice);
         assert(camera_);
         */
         camera_ = static_cast<MM::Camera*>(pDevice);
         CORE_LOG1("Device %s set as default camera.\n", label);
      break;

      case MM::StateDevice:
         // nothing to do for now
      break;

      case MM::ShutterDevice:
         shutter_ = static_cast<MM::Shutter*>(pDevice);
         //assignImageSynchro(label);
         CORE_LOG1("Device %s set as default shutter.\n", label);
      break;

      case MM::XYStageDevice:
         xyStage_ = static_cast<MM::XYStage*>(pDevice);
         CORE_LOG1("Device %s set as default xyStage.\n", label);
      break;

      case MM::AutoFocusDevice:
         autoFocus_ = static_cast<MM::AutoFocus*>(pDevice);
         CORE_LOG1("Device %s set as default auto-focus.\n", label);
      break;

      case MM::SLMDevice:
         slm_ = static_cast<MM::SLM*>(pDevice);
         CORE_LOG1("Device %s set as default SLM.\n", label);
      break;

      case MM::GalvoDevice:
         galvo_ = static_cast<MM::Galvo*>(pDevice);
         CORE_LOG1("Device %s set as default Galvo.\n", label);
      break;

      default:
         // no action on unrecognized device
         //CORE_LOG1("%s: unknown device type\n", label);
     break;
   }
}

/**
 * Unloads the device from the core and adjusts all configuration data.
 */
void CMMCore::unloadDevice(const char* label///< the name of the device to unload
                           ) throw (CMMError)
{
   MM::Device* pDevice = getDevice(label);
   
   try {
   
      switch(pDevice->GetType())
      {
         case MM::CameraDevice:
            camera_ = 0;
            CORE_LOG("default camera unloaded.\n");
         break;

         case MM::StateDevice:
            // nothing to do for now
         break;

         case MM::ShutterDevice:
            shutter_ = 0;
            CORE_LOG("default shutter unloaded.\n");
         break;

         case MM::XYStageDevice:
            xyStage_ = 0;
            CORE_LOG("default xyStage unloaded.\n");
         break;

         case MM::AutoFocusDevice:
            autoFocus_ = 0;
            CORE_LOG("default auto-focus unloaded.\n");
         break;

         case MM::SLMDevice:
            slm_ = 0;
            CORE_LOG("default SLM unloaded.\n");
         break;

         default:
            // no action on unrecognized device
         break;
      }
      pluginManager_.UnloadDevice(pDevice);
        
      
   }
   catch (CMMError& err) {
      // augment the error message with the core text
      err.setCoreMsg(getCoreErrorText(err.getCode()).c_str());
      logError("MMCore::unloadDevice", err.getMsg().c_str());
      throw; 
   }
}


/**
 * Unloads all devices from the core and resets all configuration data.
 */
void CMMCore::unloadAllDevices() throw (CMMError)
{
   try {

      // clear all roles
      camera_ = 0;
      shutter_ = 0;
      focusStage_ = 0;
      xyStage_ = 0;
      autoFocus_ = 0;
      imageProcessor_ = 0;
      slm_ = 0;

      // unload modules
      pluginManager_.UnloadAllDevices();
      CORE_LOG("All devices unloaded.\n");
      imageSynchro_.clear();
      

      // clear configurations
      CConfigMap::const_iterator it;
      for (it = configs_.begin(); it != configs_.end(); it++)
      {
         delete it->second;
      }
      configs_.clear();

      configGroups_->Clear();

      //selected channel group is no longer valid
      //channelGroup_ = "":
	   properties_->Refresh();
   
     // clear equipment definitions ???
	  // TODO
   }
   catch (CMMError& err) {
      // augment the error message with the core text
      err.setCoreMsg(getCoreErrorText(err.getCode()).c_str());
      logError("MMCore::unloadAllDevices", err.getMsg().c_str());
      throw; 
   }
}

/**
 * Unloads all devices from the core, clears all configration data and property blocks.
 */
void CMMCore::reset() throw (CMMError)
{
   try
   {
   // before unloading everything try to apply shutdown configuration
   if (isConfigDefined(MM::g_CFGGroup_System, MM::g_CFGGroup_System_Shutdown))
      this->setConfig(MM::g_CFGGroup_System, MM::g_CFGGroup_System_Shutdown);
   }
   catch(...)
   {
	   logError("MMCore::reset", "problem setting System Shutdown configuration");
   }
   
   
   // of course one reason to reset is that some device is not configured correctly,
   // so we need to handle any exception thrown from here
   try
   {
      waitForSystem();
   }
   catch (CMMError& ) {}

   // unload devices
   unloadAllDevices();

   // clear property blocks
   CPropBlockMap::const_iterator i;
   for (i = propBlocks_.begin(); i != propBlocks_.end(); i++)
      delete i->second;
   propBlocks_.clear();

   // clear configurations
   CConfigMap::const_iterator j;
   for (j = configs_.begin(); j != configs_.end(); j++)
      delete j->second;
   configs_.clear();

   properties_->Refresh();

   CORE_LOG("System reset at %D.\n");
}


/**
 * Calls Initialize() method for each loaded device.
 * This method also initialized allowed values for core properties, based
 * on the collection of loaded devices.
 */
void CMMCore::initializeAllDevices() throw (CMMError)
{
   vector<string> devices = pluginManager_.GetDeviceList();
   CORE_LOG1("Starting initialization sequence for %d devices...\n", devices.size());

   for (size_t i=0; i<devices.size(); i++)
   {
      MM::Device* pDevice;
      try {
         pDevice = pluginManager_.GetDevice(devices[i].c_str());
      }
      catch (CMMError& err) {
         err.setCoreMsg(getCoreErrorText(err.getCode()).c_str());
         logError(devices[i].c_str(), err.getMsg().c_str(), __FILE__, __LINE__);
         throw;
      }

      int nRet = pDevice->Initialize();
      if (nRet != DEVICE_OK)
      {
         logError(devices[i].c_str(), getDeviceErrorText(nRet, pDevice).c_str(), __FILE__, __LINE__);
         throw CMMError(getDeviceErrorText(nRet, pDevice).c_str(), MMERR_DEVICE_GENERIC);
      }
      CORE_LOG1("Device %s initialized.\n", devices[i].c_str());
   }

   updateCoreProperties();
}

void CMMCore::updateCoreProperties() throw (CMMError)
{
   updateCoreProperty(MM::g_Keyword_CoreCamera, MM::CameraDevice);
   updateCoreProperty(MM::g_Keyword_CoreShutter, MM::ShutterDevice);
   updateCoreProperty(MM::g_Keyword_CoreFocus,MM::StageDevice);
   updateCoreProperty(MM::g_Keyword_CoreXYStage,MM::XYStageDevice);
   updateCoreProperty(MM::g_Keyword_CoreAutoFocus,MM::AutoFocusDevice);
   updateCoreProperty(MM::g_Keyword_CoreImageProcessor,MM::ImageProcessorDevice);
   updateCoreProperty(MM::g_Keyword_CoreSLM,MM::SLMDevice);
   updateCoreProperty(MM::g_Keyword_CoreGalvo,MM::GalvoDevice);

   properties_->Refresh();
}

void CMMCore::updateCoreProperty(const char* propName, MM::DeviceType devType) throw (CMMError)
{
   vector<string> devices = getLoadedDevicesOfType(devType);
   devices.push_back(""); // add empty value
   properties_->ClearAllowedValues(propName);
   for (size_t i=0; i<devices.size(); i++)
      properties_->AddAllowedValue(propName, devices[i].c_str());
}

/**
 * Initializes specific device.
 *
 * @param label device label
 */
void CMMCore::initializeDevice(const char* label ///< the device to initialize
                               ) throw (CMMError)
{
   MM::Device* pDevice = getDevice(label);
   
   int nRet = pDevice->Initialize();
   if (nRet != DEVICE_OK)
   {
      logError(label, getDeviceErrorText(nRet, pDevice).c_str(), __FILE__, __LINE__);
      throw CMMError(getDeviceErrorText(nRet, pDevice).c_str(), MMERR_DEVICE_GENERIC);
   }
   
   updateCoreProperties();

   CORE_LOG1("Device %s initialized.\n", label);
}



/**
 * Updates the state of the entire hardware.
 */
void CMMCore::updateSystemStateCache()
{
   stateCache_ = getSystemState();
   CORE_LOG("System state cache updated.\n");
}

/**
 * Returns device type.
 */
MM::DeviceType CMMCore::getDeviceType(const char* label) throw (CMMError)
{
   if (strcmp(label, MM::g_Keyword_CoreDevice) == 0)
      return MM::CoreDevice;

   MM::Device* pDevice = getDevice(label);
   return pDevice->GetType();
}


/**
 * Returns device library (aka module, device adapter) name.
 */
std::string CMMCore::getDeviceLibrary(const char* label) throw (CMMError)
{
   if (strcmp(label, MM::g_Keyword_CoreDevice) == 0)
      return "";
   
   MM::Device* pDevice = getDevice(label);

   char moduleName[MM::MaxStrLength];
   pDevice->GetModuleName(moduleName);
   return string(moduleName);
}

/**
 * Returns device name for a given device label.
 * "Name" is determined by the library and is immutable, while "label" is
 * user assigned and represents a high-level handle to a device.
 */
std::string CMMCore::getDeviceName(const char* label) throw (CMMError)
{
   if (strcmp(label, MM::g_Keyword_CoreDevice) == 0)
      return "Core";
   
   MM::Device* pDevice = getDevice(label);

   char name[MM::MaxStrLength];
   pDevice->GetName(name);
   return string(name);
}

/**
 * Returns parent device.
 */
std::string CMMCore::getParentLabel(const char* label) throw (CMMError)
{
   if (strcmp(label, MM::g_Keyword_CoreDevice) == 0)
      return "";
   
   MM::Device* pDevice = getDevice(label);

   char id[MM::MaxStrLength];
   pDevice->GetParentID(id);
   return string(id);
}

/**
 * Sets parent device label
 */ 
void CMMCore::setParentLabel(const char* label, const char* parentLabel) throw (CMMError)
{
   if (strcmp(label, MM::g_Keyword_CoreDevice) == 0)
      return; // core can't have parent ID
   
   MM::Device* pDev = getDevice(label);
   pDev->SetParentID(parentLabel);
}


/**
 * Returns description text for a given device label.
 * "Description" is determined by the library and is immutable.
 */
std::string CMMCore::getDeviceDescription(const char* label) throw (CMMError)
{
   if (strcmp(label, MM::g_Keyword_CoreDevice) == 0)
      return "Core device";
   
   MM::Device* pDevice = getDevice(label);

   char name[MM::MaxStrLength];
   pDevice->GetDescription(name);
   return string(name);
}


/**
 * Reports action delay in milliseconds for the specific device.
 * The delay is used in the synchronization process to ensure that
 * the action is performed, without polling.
 * Value of "0" means that action is either blocking or that polling
 * of device status is required.
 * Some devices ignore this setting.
 *
 * @return double - delay time
 * @param const char* label
 */
double CMMCore::getDeviceDelayMs(const char* label) const throw (CMMError)
{

   if (strcmp(label, MM::g_Keyword_CoreDevice) == 0)
   {
      return 0.0;
   }
   MM::Device* pDevice = getDevice(label);
   return pDevice->GetDelayMs();
}


/**
 * Overrides the built-in value for the action delay.
 * Some devices ignore this setting.
 *
 * @return void 
 * @param const char* label
 * @param double delayMs
 */
void CMMCore::setDeviceDelayMs(const char* label, double delayMs) throw (CMMError)
{
   if (strcmp(label, MM::g_Keyword_CoreDevice) == 0)
      return; // ignore

   MM::Device* pDevice = getDevice(label);
   pDevice->SetDelayMs(delayMs);
}

/**
 * Signals if the device will use the delay setting or not.
 *
 * @return bool - uses delay if true
 */
bool CMMCore::usesDeviceDelay(const char* label) const throw (CMMError)
{
   if (strcmp(label, MM::g_Keyword_CoreDevice) == 0)
   {
      return false;
   }
   MM::Device* pDevice = getDevice(label);
   return pDevice->UsesDelay();
}

/**
 * Checks the busy status of the specific device.
 * @param label - device label
 */
bool CMMCore::deviceBusy(const char* label) throw (CMMError)
{
   MM::Device* pDevice = getDevice(label);
   return pDevice->Busy();
}


/**
 * Waits (blocks the calling thread) for specified time in milliseconds.
 * @param double intervalMs
 */
void CMMCore::sleep(double intervalMs) const
{
	CDeviceUtils::SleepMs( (long)(0.5 + intervalMs));
}


/**
 * Waits (blocks the calling thread) until the specified device becomes
 * non-busy.
 * @param const char* label - device label
 */
void CMMCore::waitForDevice(const char* label) throw (CMMError)
{
   if (strcmp(label, MM::g_Keyword_CoreDevice) == 0)
      return; // core property commands always block - no need to poll

   MM::Device* pDevice = getDevice(label);
   waitForDevice(pDevice);
}


/**
 * Waits (blocks the calling thread) until the specified device becomes
 * @param const MM::Device* pDev - device
 */
void CMMCore::waitForDevice(MM::Device* pDev) throw (CMMError)
{
   CORE_DEBUG1("Waiting for device %s...\n", pluginManager_.GetDeviceLabel(*pDev).c_str());

   MM::TimeoutMs timeout(GetMMTimeNow(),timeoutMs_);

   while (pDev->Busy())
   {
      if (timeout.expired(GetMMTimeNow()))
      {
         string label = pluginManager_.GetDeviceLabel(*pDev);
         std::ostringstream mez;
         mez << "wait timed out after " << timeoutMs_ << " ms. ";
         logError(label.c_str(), mez.str().c_str(), __FILE__, __LINE__);
         throw CMMError(label.c_str(), getCoreErrorText(MMERR_DevicePollingTimeout).c_str(), MMERR_DevicePollingTimeout);
      }

     //CORE_DEBUG("Polling...\n");
     sleep(pollingIntervalMs_);
   }
   CORE_DEBUG("Finished waiting.\n");
}
/**
 * Checks the busy status of the entire system. The system will report busy if any
 * of the devices is busy.
 * @return bool status - true on busy
 */
bool CMMCore::systemBusy() throw (CMMError)
{
   return deviceTypeBusy(MM::AnyType);
}


/**
 * Blocks until all devices in the system become ready (not-busy).
 */
void CMMCore::waitForSystem() throw (CMMError)
{
   waitForDeviceType(MM::AnyType);
}


/**
 * Checks the busy status for all devices of the specific type.
 * The system will report busy if any of the devices of the spefified type are busy.
 *
 * @return bool - true on busy
 * @param MM::DeviceType devType
 */
bool CMMCore::deviceTypeBusy(MM::DeviceType devType) throw (CMMError)
{
   vector<string> devices = pluginManager_.GetDeviceList(devType);
   for (size_t i=0; i<devices.size(); i++)
   {
      try {
         MM::Device* pDevice;
         pDevice = pluginManager_.GetDevice(devices[i].c_str());
         if (pDevice->Busy())
            return true;
      }
      catch (...) {
         // trap all exceptions
         assert(!"Plugin manager can't access device it reported as available.");
      }
   }
   return false;
}


/**
 * Blocks until all devices of the specific type become ready (not-busy).
 * @param devType constant specifying the device type
 */
void CMMCore::waitForDeviceType(MM::DeviceType devType) throw (CMMError)
{
   vector<string> devices = pluginManager_.GetDeviceList(devType);
   for (size_t i=0; i<devices.size(); i++)
      waitForDevice(devices[i].c_str());
}

/**
 * Blocks until all devices included in the configuration become ready.
 * @param group
 * @param config
 */
void CMMCore::waitForConfig(const char* group, const char* configName) throw (CMMError)
{

   Configuration cfg = getConfigData(group, configName);
   try {
      for(size_t i=0; i<cfg.size(); i++)
         waitForDevice(cfg.getSetting(i).getDeviceLabel().c_str());
   } catch (CMMError& err) {
      // trap MM exceptions and keep quiet - this is not a good time to blow up
      logError("waitForConfig", err.getMsg().c_str());
   }
}

/**
 * Wait for the slowest device in the ImageSynchro list. 
 */
void CMMCore::waitForImageSynchro() throw (CMMError)
{
   for (size_t i=0; i<imageSynchro_.size(); i++)
   {
      // poll the device until it stops...
      waitForDevice(imageSynchro_[i]);
   }
}

/**
 * Sets the position of the stage in microns.
 * @param const char* label
 * @param double position
 */
void CMMCore::setPosition(const char* label, double position) throw (CMMError)
{

   MMThreadGuard guard(deviceLock_);

   MM::Stage* pStage = getSpecificDevice<MM::Stage>(label);
   CORE_DEBUG2("attempt to set %s  to %.5g um\n", label, position);
   int ret = pStage->SetPositionUm(position);
   if (ret != DEVICE_OK)
   {
      char name[MM::MaxStrLength];
      pStage->GetName(name);
      logError(name, getDeviceErrorText(ret, pStage).c_str());
      throw CMMError(getDeviceErrorText(ret, pStage).c_str(), MMERR_DEVICE_GENERIC);
   }

}

/**
 * Sets the relative position of the stage in microns.
 * @param const char* label
 * @param double d
 */
void CMMCore::setRelativePosition(const char* label, double d) throw (CMMError)
{
   MMThreadGuard guard(deviceLock_);

   MM::Stage* pStage = getSpecificDevice<MM::Stage>(label);
   CORE_DEBUG2("attempt to move %s relative %.5g um\n", label, d);

   int ret = pStage->SetRelativePositionUm(d);
   if (ret != DEVICE_OK)
   {
      char name[MM::MaxStrLength];
      pStage->GetName(name);
      logError(name, getDeviceErrorText(ret, pStage).c_str());
      throw CMMError(getDeviceErrorText(ret, pStage).c_str(), MMERR_DEVICE_GENERIC);
   }
}

/**
 * Returns the current position of the stage in microns.
 * @return position 
 * @param label
 */
double CMMCore::getPosition(const char* label) const throw (CMMError)
{
   MMThreadGuard guard(deviceLock_);

   MM::Stage* pStage = getSpecificDevice<MM::Stage>(label);
   double pos;
   int ret = pStage->GetPositionUm(pos);
   if (ret != DEVICE_OK)
   {
      char name[MM::MaxStrLength];
      pStage->GetName(name);
      logError(name, getDeviceErrorText(ret, pStage).c_str());
      throw CMMError(getDeviceErrorText(ret, pStage).c_str(), MMERR_DEVICE_GENERIC);
   }
   return pos;
}

/**
 * Sets the position of the XY stage in microns.
 * @param const char* label
 * @param x
 * @param y
 */
void CMMCore::setXYPosition(const char* deviceName, double x, double y) throw (CMMError)
{

   MM::XYStage* pXYStage = getSpecificDevice<MM::XYStage>(deviceName);
   int ret = pXYStage->SetPositionUm(x, y);
   if (ret != DEVICE_OK)
   {
      char name[MM::MaxStrLength];
      pXYStage->GetName(name);
      logError(name, getDeviceErrorText(ret, pXYStage).c_str());
      throw CMMError(getDeviceErrorText(ret, pXYStage).c_str(), MMERR_DEVICE_GENERIC);
   }
   CORE_DEBUG3("%s set to %g.3 %g.3 um\n", deviceName, x, y);
}

/**
 * Sets the relative position of the XY stage in microns.
 * @param const char* label
 * @param dx
 * @param dy
 */
void CMMCore::setRelativeXYPosition(const char* deviceName, double dx, double dy) throw (CMMError)
{
   MMThreadGuard guard(deviceLock_);
   CORE_DEBUG3("Attempt relative move of %s to %g ,  %g um\n", deviceName, dx, dy);

   MM::XYStage* pXYStage = getSpecificDevice<MM::XYStage>(deviceName);
   int ret = pXYStage->SetRelativePositionUm(dx, dy);
   if (ret != DEVICE_OK)
   {
      char name[MM::MaxStrLength];
      pXYStage->GetName(name);
      logError(name, getDeviceErrorText(ret, pXYStage).c_str());
      throw CMMError(getDeviceErrorText(ret, pXYStage).c_str(), MMERR_DEVICE_GENERIC);
   }
}

/**
 * Obtains the current position of the XY stage in microns.
 * @param const char* label
 * @param x
 * @param y
 */
void CMMCore::getXYPosition(const char* deviceName, double& x, double& y) throw (CMMError)
{
   MMThreadGuard guard(deviceLock_);

   MM::XYStage* pXYStage = getSpecificDevice<MM::XYStage>(deviceName);
   int ret = pXYStage->GetPositionUm(x, y);
   if (ret != DEVICE_OK)
   {
      char name[MM::MaxStrLength];
      pXYStage->GetName(name);
      logError(name, getDeviceErrorText(ret, pXYStage).c_str());
      throw CMMError(getDeviceErrorText(ret, pXYStage).c_str(), MMERR_DEVICE_GENERIC);
   }
}

/**
 * Obtains the current position of the X axis of the XY stage in microns.
 * @return x position 
 * @param const char* label
 */
double CMMCore::getXPosition(const char* deviceName) throw (CMMError)
{
   MMThreadGuard guard(deviceLock_);

   MM::XYStage* pXYStage = getSpecificDevice<MM::XYStage>(deviceName);
   double x, y;
   int ret = pXYStage->GetPositionUm(x, y);
   if (ret != DEVICE_OK)
   {
      char name[MM::MaxStrLength];
      pXYStage->GetName(name);
      logError(name, getDeviceErrorText(ret, pXYStage).c_str());
      throw CMMError(getDeviceErrorText(ret, pXYStage).c_str(), MMERR_DEVICE_GENERIC);
   }

   return x;
}

/**
 * Obtains the current position of the Y axis of the XY stage in microns.
 * @return y position 
 * @param const char* label
 */
double CMMCore::getYPosition(const char* deviceName) throw (CMMError)
{
   MMThreadGuard guard(deviceLock_);

   MM::XYStage* pXYStage = getSpecificDevice<MM::XYStage>(deviceName);
   double x, y;
   int ret = pXYStage->GetPositionUm(x, y);
   if (ret != DEVICE_OK)
   {
      char name[MM::MaxStrLength];
      pXYStage->GetName(name);
      logError(name, getDeviceErrorText(ret, pXYStage).c_str());
      throw CMMError(getDeviceErrorText(ret, pXYStage).c_str(), MMERR_DEVICE_GENERIC);
   }

   return y;
}

/**
 * stop the XY stage motors.
 * @return void 
 * @param const char* label
 */
///**
// * Aborts current action.
// */
void CMMCore::stop(const char* deviceName) throw (CMMError)
{
   MMThreadGuard guard(deviceLock_);
   MM::XYStage* pXYStage = getSpecificDevice<MM::XYStage>(deviceName);
   int ret = pXYStage->Stop();
   if (ret != DEVICE_OK)
   {
      logError(deviceName, getDeviceErrorText(ret, pXYStage).c_str());
      throw CMMError(getDeviceErrorText(ret, pXYStage).c_str(), MMERR_DEVICE_GENERIC);
   }
   CORE_LOG1("Device %s stopped!\n", deviceName);
}

/**
 * Calibrates and homes the XY stage.
 */
void CMMCore::home(const char* deviceName) throw (CMMError)
{
   MMThreadGuard guard(deviceLock_);
   MM::XYStage* pXYStage = getSpecificDevice<MM::XYStage>(deviceName);
   int ret = pXYStage->Home();
   if (ret != DEVICE_OK)
   {
      logError(deviceName, getDeviceErrorText(ret, pXYStage).c_str());
      throw CMMError(getDeviceErrorText(ret, pXYStage).c_str(), MMERR_DEVICE_GENERIC);
   }
   CORE_LOG1("Stage %s moved to the HOME position.\n", deviceName);
}

//jizhen, 4/12/2007
/**
 * zero the current XY position.
 * @return void 
 * @param const char* deviceName
 */
void CMMCore::setOriginXY(const char* deviceName) throw (CMMError)
{
   MMThreadGuard guard(deviceLock_);

   MM::XYStage* pXYStage = getSpecificDevice<MM::XYStage>(deviceName);
   int ret = pXYStage->SetOrigin();
   if (ret != DEVICE_OK)
   {
      logError(deviceName, getDeviceErrorText(ret, pXYStage).c_str());
      throw CMMError(getDeviceErrorText(ret, pXYStage).c_str(), MMERR_DEVICE_GENERIC);
   }
   CORE_LOG1("Stage %s's current position was zeroed.\n", deviceName);
}

/**
 * zero the current stage position.
 * @return void 
 * @param const char* deviceName
 */
void CMMCore::setOrigin(const char* deviceName) throw (CMMError)
{
   MMThreadGuard guard(deviceLock_);

   MM::Stage* pStage = getSpecificDevice<MM::Stage>(deviceName);
   int ret = pStage->SetOrigin();
   if (ret != DEVICE_OK)
   {
      logError(deviceName, getDeviceErrorText(ret, pStage).c_str());
      throw CMMError(getDeviceErrorText(ret, pStage).c_str(), MMERR_DEVICE_GENERIC);
   }
   CORE_LOG1("Stage %s's current position was zeroed.\n", deviceName);
}

/**
 * Set the current position in um to be 0
 */
void CMMCore::setAdapterOrigin(const char* deviceName, double d) throw (CMMError)
{
   MMThreadGuard guard(deviceLock_);

   MM::Stage* pStage = getSpecificDevice<MM::Stage>(deviceName);
   int ret = pStage->SetAdapterOriginUm(d);
   if (ret != DEVICE_OK)
   {
      logError(deviceName, getDeviceErrorText(ret, pStage).c_str());
      throw CMMError(getDeviceErrorText(ret, pStage).c_str(), MMERR_DEVICE_GENERIC);
   }
   CORE_LOG2("Stage %s's position %.2f um was set to 0.\n", deviceName, d);
}


/**
 * Set the current position to be  x,y in um
 */
void CMMCore::setAdapterOriginXY(const char* deviceName, double x, double y) throw (CMMError)
{
   MMThreadGuard guard(deviceLock_);

   MM::XYStage* pXYStage = getSpecificDevice<MM::XYStage>(deviceName);
   int ret = pXYStage->SetAdapterOriginUm(x, y);
   if (ret != DEVICE_OK)
   {
      logError(deviceName, getDeviceErrorText(ret, pXYStage).c_str());
      throw CMMError(getDeviceErrorText(ret, pXYStage).c_str(), MMERR_DEVICE_GENERIC);
   }
   CORE_LOG3("Stage %s's current position was set as %.2f,%.2f um.\n", deviceName, x, y);
}


/**
 * Queries camera if exposure can be used in a sequence
 * @param cameraLabel - device label
 */
bool CMMCore::isExposureSequenceable(const char* cameraLabel) const throw (CMMError)
{
   MMThreadGuard guard(deviceLock_);

   MM::Camera* pCamera = getSpecificDevice<MM::Camera>(cameraLabel);

   bool isSequenceable;
   int ret = pCamera->IsExposureSequenceable(isSequenceable);
   if (ret != DEVICE_OK)
      throw CMMError(cameraLabel, getDeviceErrorText(ret, pCamera).c_str(), MMERR_DEVICE_GENERIC);

   return isSequenceable;
}


/**
 * Starts an ongoing sequence of triggered exposures in a camera
 * This should only be called for cameras where exposure time is sequenceable
 * @param cameraLabel - the camera
 */
void CMMCore::startExposureSequence(const char* cameraLabel) const throw (CMMError)
{
   MMThreadGuard guard(deviceLock_);

   MM::Camera* pDevice = getSpecificDevice<MM::Camera>(cameraLabel);

   int ret = pDevice->StartExposureSequence();
   if (ret != DEVICE_OK)
      throw CMMError(cameraLabel, getDeviceErrorText(ret, pDevice).c_str(), MMERR_DEVICE_GENERIC);
   
}

/**
 * Stops an ongoing sequence of triggered exposures in a camera
 * This should only be called for cameras where exposure time is sequenceable
 * @param cameraLabel - deviceName
 */
void CMMCore::stopExposureSequence(const char* cameraLabel) const throw (CMMError)
{
   MMThreadGuard guard(deviceLock_);

   MM::Camera* pDevice = getSpecificDevice<MM::Camera>(cameraLabel);

   int ret = pDevice->StopExposureSequence();
   if (ret != DEVICE_OK)
      throw CMMError(cameraLabel, getDeviceErrorText(ret, pDevice).c_str(), MMERR_DEVICE_GENERIC);
}

/**
 * Gets the maximum length of a camera's exposure sequence.
 * This should only be called for cameras where exposure time is sequenceable
 * @param cameraLabel - deviceName
 */
long CMMCore::getExposureSequenceMaxLength(const char* cameraLabel) const throw (CMMError)
{
   MMThreadGuard guard(deviceLock_);

   MM::Camera* pDevice = getSpecificDevice<MM::Camera>(cameraLabel);
   long length;
   int ret = pDevice->GetExposureSequenceMaxLength(length);
   if (ret != DEVICE_OK)
      throw CMMError(cameraLabel, getDeviceErrorText(ret, pDevice).c_str(), MMERR_DEVICE_GENERIC);

   return length;
}

/**
 * Transfer a sequence of exposure times to the camera.
 * This should only be called for cameras where exposure time is sequenceable
 * @param cameraLabel - deviceName
 * @param exposureTime_ms - sequence of exposure times the camera will use during a sequence acquisition
 */
void CMMCore::loadExposureSequence(const char* cameraLabel, std::vector<double> exposureTime_ms) const throw (CMMError)
{
   MMThreadGuard guard(deviceLock_);

   MM::Camera* pDevice = getSpecificDevice<MM::Camera>(cameraLabel);
   
   int ret;
   ret = pDevice->ClearExposureSequence();
   if (ret != DEVICE_OK)
      throw CMMError(cameraLabel, getDeviceErrorText(ret, pDevice).c_str(), MMERR_DEVICE_GENERIC);

   std::vector<double>::iterator it;
   for ( it=exposureTime_ms.begin() ; it < exposureTime_ms.end(); it++ )
   {
      ret = pDevice->AddToExposureSequence(*it);
      if (ret != DEVICE_OK)
         throw CMMError(cameraLabel, getDeviceErrorText(ret, pDevice).c_str(), MMERR_DEVICE_GENERIC);
   }

   ret = pDevice->SendExposureSequence();
   if (ret != DEVICE_OK)
      throw CMMError(cameraLabel, getDeviceErrorText(ret, pDevice).c_str(), MMERR_DEVICE_GENERIC);
  
}



/**
 * Queries stage if it can be used in a sequence
 * @param deviceName - device label
 */
bool CMMCore::isStageSequenceable(const char* deviceName) const throw (CMMError)
{
   MMThreadGuard guard(deviceLock_);

   MM::Stage* pStage = getSpecificDevice<MM::Stage>(deviceName);

   bool isSequenceable;
   int ret = pStage->IsStageSequenceable(isSequenceable);
   if (ret != DEVICE_OK)
      throw CMMError(deviceName, getDeviceErrorText(ret, pStage).c_str(), MMERR_DEVICE_GENERIC);

   return isSequenceable;
   
}


/**
 * Starts an ongoing sequence of triggered events in a stage
 * This should only be called for stages
 * @param label - deviceName
 */
void CMMCore::startStageSequence(const char* label) const throw (CMMError)
{
   MMThreadGuard guard(deviceLock_);

   MM::Stage* pDevice = getSpecificDevice<MM::Stage>(label);

   int ret = pDevice->StartStageSequence();
   if (ret != DEVICE_OK)
      throw CMMError(label, getDeviceErrorText(ret, pDevice).c_str(), MMERR_DEVICE_GENERIC);
   
}

/**
 * Stops an ongoing sequence of triggered events in a stage
 * This should only be called for stages that are sequenceable
 * @param label - deviceName
 */
void CMMCore::stopStageSequence(const char* label) const throw (CMMError)
{
   MMThreadGuard guard(deviceLock_);

   MM::Stage* pDevice = getSpecificDevice<MM::Stage>(label);

   int ret = pDevice->StopStageSequence();
   if (ret != DEVICE_OK)
      throw CMMError(label, getDeviceErrorText(ret, pDevice).c_str(), MMERR_DEVICE_GENERIC);

}

/**
 * Gets the maximum length of a stage's position sequence.
 * This should only be called for stages that are sequenceable
 * @param label - deviceName
 */
long CMMCore::getStageSequenceMaxLength(const char* label) const throw (CMMError)
{
   MMThreadGuard guard(deviceLock_);

   MM::Stage* pDevice = getSpecificDevice<MM::Stage>(label);
   long length;
   int ret = pDevice->GetStageSequenceMaxLength(length);
   if (ret != DEVICE_OK)
      throw CMMError(label, getDeviceErrorText(ret, pDevice).c_str(), MMERR_DEVICE_GENERIC);

   return length;
}

/**
 * Transfer a sequence of events/states/whatever to the device
 * This should only be called for device-properties that are sequenceable
 * @param label - deviceName
 * @param positionSequence - sequence of positions that the stage will execute in reponse to external triggers
 */
void CMMCore::loadStageSequence(const char* label, std::vector<double> positionSequence) const throw (CMMError)
{
   MMThreadGuard guard(deviceLock_);

   MM::Stage* pDevice = getSpecificDevice<MM::Stage>(label);
   
   int ret;
   ret = pDevice->ClearStageSequence();
   if (ret != DEVICE_OK)
      throw CMMError(label, getDeviceErrorText(ret, pDevice).c_str(), MMERR_DEVICE_GENERIC);

   std::vector<double>::iterator it;
   for ( it=positionSequence.begin() ; it < positionSequence.end(); it++ )
   {
      ret = pDevice->AddToStageSequence(*it);
      if (ret != DEVICE_OK)
         throw CMMError(label, getDeviceErrorText(ret, pDevice).c_str(), MMERR_DEVICE_GENERIC);
   }

   ret = pDevice->SendStageSequence();
   if (ret != DEVICE_OK)
      throw CMMError(label, getDeviceErrorText(ret, pDevice).c_str(), MMERR_DEVICE_GENERIC);
  
}


/**
 * Queries XY stage if it can be used in a sequence
 * @param deviceName - device label
 */
bool CMMCore::isXYStageSequenceable(const char* deviceName) const throw (CMMError)
{
   MMThreadGuard guard(deviceLock_);

   MM::XYStage* pStage = getSpecificDevice<MM::XYStage>(deviceName);

   bool isSequenceable;
   int ret = pStage->IsXYStageSequenceable(isSequenceable);
   if (ret != DEVICE_OK)
      throw CMMError(deviceName, getDeviceErrorText(ret, pStage).c_str(), MMERR_DEVICE_GENERIC);

   return isSequenceable;
   
}


/**
 * Starts an ongoing sequence of triggered events in an XY stage
 * This should only be called for stages
 * @param label - deviceName
 */
void CMMCore::startXYStageSequence(const char* label) const throw (CMMError)
{
   MMThreadGuard guard(deviceLock_);

   MM::XYStage* pDevice = getSpecificDevice<MM::XYStage>(label);

   int ret = pDevice->StartXYStageSequence();
   if (ret != DEVICE_OK)
      throw CMMError(label, getDeviceErrorText(ret, pDevice).c_str(), MMERR_DEVICE_GENERIC);
   
}

/**
 * Stops an ongoing sequence of triggered events in an XY stage
 * This should only be called for stages that are sequenceable
 * @param label - deviceName
 */
void CMMCore::stopXYStageSequence(const char* label) const throw (CMMError)
{
   MMThreadGuard guard(deviceLock_);

   MM::XYStage* pDevice = getSpecificDevice<MM::XYStage>(label);

   int ret = pDevice->StopXYStageSequence();
   if (ret != DEVICE_OK)
      throw CMMError(label, getDeviceErrorText(ret, pDevice).c_str(), MMERR_DEVICE_GENERIC);

}

/**
 * Gets the maximum length of an XY stage's position sequence.
 * This should only be called for XY stages that are sequenceable
 * @param label - deviceName
 */
long CMMCore::getXYStageSequenceMaxLength(const char* label) const throw (CMMError)
{
   MMThreadGuard guard(deviceLock_);

   MM::XYStage* pDevice = getSpecificDevice<MM::XYStage>(label);
   long length;
   int ret = pDevice->GetXYStageSequenceMaxLength(length);
   if (ret != DEVICE_OK)
      throw CMMError(label, getDeviceErrorText(ret, pDevice).c_str(), MMERR_DEVICE_GENERIC);

   return length;
}

/**
 * Transfer a sequence of stage positions to the xy stage.
 * xSequence and ySequence must have the same length.
 * This should only be called for XY stages that are sequenceable
 * @param label - deviceName
 * @param xSequence - sequence of x positions that the stage will execute in reponse to external triggers
 * @param ySequence - sequence of y positions that the stage will execute in reponse to external triggers
 */
void CMMCore::loadXYStageSequence(const char* label,
                                  std::vector<double> xSequence,
                                  std::vector<double> ySequence) const throw (CMMError)
{
   MMThreadGuard guard(deviceLock_);

   MM::XYStage* pDevice = getSpecificDevice<MM::XYStage>(label);
   
   int ret;
   ret = pDevice->ClearXYStageSequence();
   if (ret != DEVICE_OK)
      throw CMMError(label, getDeviceErrorText(ret, pDevice).c_str(), MMERR_DEVICE_GENERIC);

   std::vector<double>::iterator itx, ity;
   for ( itx=xSequence.begin(), ity=ySequence.begin() ;
         (itx < xSequence.end()) && (ity < ySequence.end()); itx++, ity++)
   {
      ret = pDevice->AddToXYStageSequence(*itx, *ity);
      if (ret != DEVICE_OK)
         throw CMMError(label, getDeviceErrorText(ret, pDevice).c_str(), MMERR_DEVICE_GENERIC);
   }

   ret = pDevice->SendXYStageSequence();
   if (ret != DEVICE_OK)
      throw CMMError(label, getDeviceErrorText(ret, pDevice).c_str(), MMERR_DEVICE_GENERIC);
  
}


/**
 * Acquires a single image with current settings.
 * Snap is not allowed while the acquisition thread is run
 */
void CMMCore::snapImage() throw (CMMError)
{
   if (camera_)
   {
      if(camera_->IsCapturing())
      {
         throw CMMError(getCoreErrorText(
            MMERR_NotAllowedDuringSequenceAcquisition).c_str()
            ,MMERR_NotAllowedDuringSequenceAcquisition);
      }

      int ret = DEVICE_OK;
      try {

         // wait for all synchronized devices to stop before taking an image
         waitForImageSynchro();

         // open the shutter
         if (shutter_ && autoShutter_)
         {
            int sret = shutter_->SetOpen(true);
            if (DEVICE_OK != sret)
            {
               logError("CMMCore::snapImage", getDeviceErrorText(sret, shutter_).c_str(), __FILE__, __LINE__);
               throw CMMError(getDeviceErrorText(sret, shutter_).c_str(), MMERR_DEVICE_GENERIC);
            }               
            waitForDevice(shutter_);
         }
         ret = camera_->SnapImage();

			everSnapped_ = true;
         // close the shutter
         if (shutter_ && autoShutter_)
         {
            int sret  = shutter_->SetOpen(false);
            if (DEVICE_OK != sret)
            {
               logError("CMMCore::snapImage", getDeviceErrorText(sret, shutter_).c_str(), __FILE__, __LINE__);
               throw CMMError(getDeviceErrorText(sret, shutter_).c_str(), MMERR_DEVICE_GENERIC);
            }
            waitForDevice(shutter_);
         }
		}catch( CMMError& e){
			throw e;
		}
		catch (...) {
         logError("CMMCore::snapImage", getCoreErrorText(MMERR_UnhandledException).c_str(), __FILE__, __LINE__);
         throw CMMError(getCoreErrorText(MMERR_UnhandledException).c_str(), MMERR_UnhandledException);
      }

      if (ret != DEVICE_OK)
      {
         logError("CMMCore::snapImage", getDeviceErrorText(ret, camera_).c_str(), __FILE__, __LINE__);
         throw CMMError(getDeviceErrorText(ret, camera_).c_str(), MMERR_DEVICE_GENERIC);
      }

      CORE_DEBUG("Image acquired at %D\n");
   }
   else
      throw CMMError(getCoreErrorText(MMERR_CameraNotAvailable).c_str(), MMERR_CameraNotAvailable);
}


/**
 * Add device to the image-synchro list. Image acquistion waits for all devices
 * in this list.
 * @param const char* label - device label
 */
void CMMCore::assignImageSynchro(const char* label) throw (CMMError)
{
   imageSynchro_.push_back(getDevice(label));
   CORE_LOG1("Image acquisition synchronized with %s\n", label);
}

/**
 * Removes device from the image-synchro list.
 * @param const char* label - device label
 */
void CMMCore::removeImageSynchro(const char* label) throw (CMMError)
{
   MM::Device* dev = getDevice(label);
   vector<MM::Device*>::iterator it = find(imageSynchro_.begin(), imageSynchro_.end(), dev);
   if(it != imageSynchro_.end())
   {
      imageSynchro_.erase(it);
      CORE_LOG1("Device %s removed from the image synchro list.\n", label);
   }
   else
      CORE_DEBUG1("Device %s requested for removal was not in the image synchro list.\n", label);
      //throw CMMError(getCoreErrorText(MMERR_NotInImageSynchro).c_str(), MMERR_NotInImageSynchro);
}

/**
 * Clears the image synchro device list.
 */
void CMMCore::removeImageSynchroAll()
{
   imageSynchro_.clear();
   CORE_LOG("Image synchro cleared. No devices are synchronized with the camera.\n");
}

/**
 * If this option is enabled Shutter automatically opens and closes when the image
 * is acquired.
 * @param bool state - true for enabled
 */
void CMMCore::setAutoShutter(bool state)
{
   properties_->Set(MM::g_Keyword_CoreAutoShutter, state ? "1" : "0");
   autoShutter_ = state;
   stateCache_.addSetting(PropertySetting(MM::g_Keyword_CoreDevice, MM::g_Keyword_CoreAutoShutter, state ? "1" : "0"));
   CORE_LOG1("Auto shutter %s.\n", state ? "ON" : "OFF");
}

/**
 * Returns the current setting of the auto-shutter option.
 */
bool CMMCore::getAutoShutter()
{
   return autoShutter_;
}

/**
 * Opens or closes the currently selected shutter.
 */
void CMMCore::setShutterOpen(bool state) throw (CMMError)
{
   if (shutter_)
   {
      int ret = shutter_->SetOpen(state);
      if (ret != DEVICE_OK)
      {
         logError("CMMCore::setShutterOpen()", getDeviceErrorText(ret, shutter_).c_str());
         throw CMMError(getDeviceErrorText(ret, shutter_).c_str(), MMERR_DEVICE_GENERIC);
      }

      if (shutter_->HasProperty(MM::g_Keyword_State))
      {
         char shutterName[MM::MaxStrLength];
         shutter_->GetLabel(shutterName);
         stateCache_.addSetting(PropertySetting(shutterName, MM::g_Keyword_State, CDeviceUtils::ConvertToString(state)));
      }
   }
}

/**
 * Return the state of the currently selected shutter.
 */
bool CMMCore::getShutterOpen() throw (CMMError)
{
   bool state = true; // default open
   if (shutter_)
   {
      int ret = shutter_->GetOpen(state);
      if (ret != DEVICE_OK)
      {
         logError("CMMCore::getShutterOpen()", getDeviceErrorText(ret, shutter_).c_str());
         throw CMMError(getDeviceErrorText(ret, shutter_).c_str(), MMERR_DEVICE_GENERIC);
      }
   }
   return state;
}

/**
 * Exposes the internal image buffer.
 *
 * Multi-Channel cameras will return the content of the first 
 * channel in this function
 *
 * Designed specifically for the SWIG wrapping for Java and scripting languages.
 * @return a pointer to the internal image buffer.
 * @throws CMMError - when the camera returns no data
 */
void* CMMCore::getImage() const throw (CMMError)
{
   if (!camera_)
      throw CMMError(getCoreErrorText(MMERR_CameraNotAvailable).c_str(), MMERR_CameraNotAvailable);
   else
   {
		if( ! everSnapped_)
		{
         logError("CMMCore::getImage()", getCoreErrorText(MMERR_InvalidImageSequence).c_str());
         throw CMMError(getCoreErrorText(MMERR_InvalidImageSequence).c_str(), MMERR_InvalidImageSequence);
      }

      // scope for the thread guard
      {
         MMThreadGuard g(*pPostedErrorsLock_);

         if(0 < postedErrors_.size())
         {
            std::pair< int, std::string>  toThrow(postedErrors_[0]);
            // todo, process the collection of posted errors.
            postedErrors_.clear();
            throw CMMError( toThrow.second.c_str(), toThrow.first);         
         }
      }

      void* pBuf(0);
      try {
         pBuf = const_cast<unsigned char*> (camera_->GetImageBuffer());
		
      	if (imageProcessor_)
	      {
            imageProcessor_->Process((unsigned char*)pBuf, camera_->GetImageWidth(),  camera_->GetImageHeight(), camera_->GetImageBytesPerPixel() );
	      }
		} catch( CMMError& e){
			throw e;
		} catch (...) {
         logError("CMMCore::getImage()", getCoreErrorText(MMERR_UnhandledException).c_str());
         throw CMMError(getCoreErrorText(MMERR_UnhandledException).c_str(), MMERR_UnhandledException);
      }

      if (pBuf != 0)
         return pBuf;
      else
      {
         logError("CMMCore::getImage()", getCoreErrorText(MMERR_CameraBufferReadFailed).c_str());
         throw CMMError(getCoreErrorText(MMERR_CameraBufferReadFailed).c_str(), MMERR_CameraBufferReadFailed);
      }
   }
}

/**
 * Returns the internal image buffer for a given Camera Channel
 *
 * Single channel cameras will return the content of their image buffer
 * irrespective of the channelNr argument
 * Designed specifically for the SWIG wrapping for Java and scripting languages.
 *
 * @param channelNr Channel number for which the image buffer is requested
 * @return a pointer to the internal image buffer.
 */
void* CMMCore::getImage(unsigned channelNr) const throw (CMMError)
{
   if (!camera_)
      throw CMMError(getCoreErrorText(MMERR_CameraNotAvailable).c_str(), MMERR_CameraNotAvailable);
   else
   {
      void* pBuf(0);
      try {
         pBuf = const_cast<unsigned char*> (camera_->GetImageBuffer(channelNr));
		
      	if (imageProcessor_)
	      {
            imageProcessor_->Process((unsigned char*)pBuf, camera_->GetImageWidth(),  camera_->GetImageHeight(), camera_->GetImageBytesPerPixel() );
	      }
		} catch( CMMError& e){
			throw e;
		} catch (...) {
         logError("CMMCore::getImage()", getCoreErrorText(MMERR_UnhandledException).c_str());
         throw CMMError(getCoreErrorText(MMERR_UnhandledException).c_str(), MMERR_UnhandledException);
      }

      if (pBuf != 0)
         return pBuf;
      else
      {
         logError("CMMCore::getImage()", getCoreErrorText(MMERR_CameraBufferReadFailed).c_str());
         throw CMMError(getCoreErrorText(MMERR_CameraBufferReadFailed).c_str(), MMERR_CameraBufferReadFailed);
      }
   }
}

/**
 * Returns the size of the internal image buffer.
 *
 * @return buffer size
 */
long CMMCore::getImageBufferSize() const
{
   if (camera_)
      return camera_->GetImageBufferSize();
   else
      return 0;
}

/**
 * Starts straming camera sequence acquisition.
 * This command does not block the calling thread for the duration of the acquisition.
 *
 * @param numImages Number of images requested from the camera
 * @param intervalMs interval between images, currently only supported by Andor cameras
 * @param stopOnOverflow whether or not the camera stops acquiring when the circular buffer is full
 */
void CMMCore::startSequenceAcquisition(long numImages, double intervalMs, bool stopOnOverflow) throw (CMMError)
{
   // scope for the thread guard
   {
      MMThreadGuard g(*pPostedErrorsLock_);
      postedErrors_.clear();
   }

   if (camera_)
   {
      if(camera_->IsCapturing())
      {
         throw CMMError(getCoreErrorText(
            MMERR_NotAllowedDuringSequenceAcquisition).c_str()
            ,MMERR_NotAllowedDuringSequenceAcquisition);
      }

		try
		{
			if (!cbuf_->Initialize(camera_->GetNumberOfChannels(), 1, camera_->GetImageWidth(), camera_->GetImageHeight(), camera_->GetImageBytesPerPixel()))
			{
				logError(getDeviceName(camera_).c_str(), getCoreErrorText(MMERR_CircularBufferFailedToInitialize).c_str());
				throw CMMError(getCoreErrorText(MMERR_CircularBufferFailedToInitialize).c_str(), MMERR_CircularBufferFailedToInitialize);
			}
			cbuf_->Clear();
			int nRet = camera_->StartSequenceAcquisition(numImages, intervalMs, stopOnOverflow);
			if (nRet != DEVICE_OK)
				throw CMMError(getDeviceErrorText(nRet, camera_).c_str(), MMERR_DEVICE_GENERIC);
		}
		catch( bad_alloc& ex)
		{
			ostringstream messs;
			messs << getCoreErrorText(MMERR_OutOfMemory).c_str() << " " << ex.what() << endl;
			throw CMMError(messs.str().c_str() , MMERR_OutOfMemory);
		}
   }
   else
   {
      logError(getDeviceName(camera_).c_str(), getCoreErrorText(MMERR_CameraNotAvailable).c_str());
      throw CMMError(getCoreErrorText(MMERR_CameraNotAvailable).c_str(), MMERR_CameraNotAvailable);
   }
   CORE_DEBUG("Sequence acquisition started.");
}

/**
 * Starts straming camera sequence acquisition for a specified camera.
 * This command does not block the calling thread for the uration of the acquisition.
 * The difference between this method and the one with the same name but operating on the "default"
 * camera is that it does not automatically intitialize the circular buffer.
 */
void CMMCore::startSequenceAcquisition(const char* label, long numImages, double intervalMs, bool stopOnOverflow) throw (CMMError)
{
   MM::Camera* pCam = getSpecificDevice<MM::Camera>(label);

   if(pCam->IsCapturing())
      throw CMMError(getCoreErrorText(MMERR_NotAllowedDuringSequenceAcquisition).c_str(), 
                     MMERR_NotAllowedDuringSequenceAcquisition);
   
   int nRet = pCam->StartSequenceAcquisition(numImages, intervalMs, stopOnOverflow);
   if (nRet != DEVICE_OK)
      throw CMMError(getDeviceErrorText(nRet, pCam).c_str(), MMERR_DEVICE_GENERIC);

   CORE_DEBUG1("Sequence acquisition started on %s.\n", label);
}

/**
 * Prepare the camera for the sequence acquisition to save the time in the
 * StartSequenceAcqusition() call which is supposed to come next.
 */
void CMMCore::prepareSequenceAcquisition(const char* label) throw (CMMError)
{
   MM::Camera* pCam = getSpecificDevice<MM::Camera>(label);

   if(pCam->IsCapturing())
      throw CMMError(getCoreErrorText(MMERR_NotAllowedDuringSequenceAcquisition).c_str(), 
                     MMERR_NotAllowedDuringSequenceAcquisition);
   
   int nRet = pCam->PrepareSequenceAcqusition();
   if (nRet != DEVICE_OK)
      throw CMMError(getDeviceErrorText(nRet, pCam).c_str(), MMERR_DEVICE_GENERIC);

   CORE_DEBUG1("Sequence acquisition prepared on %s.\n", label);
}


/**
 * Initialize circular buffer based on the current camera settings.
 */
void CMMCore::initializeCircularBuffer() throw (CMMError)
{
   if (camera_)
   {
      if (!cbuf_->Initialize(camera_->GetNumberOfChannels(), 1, camera_->GetImageWidth(), camera_->GetImageHeight(), camera_->GetImageBytesPerPixel()))
      {
         logError(getDeviceName(camera_).c_str(), getCoreErrorText(MMERR_CircularBufferFailedToInitialize).c_str());
         throw CMMError(getCoreErrorText(MMERR_CircularBufferFailedToInitialize).c_str(), MMERR_CircularBufferFailedToInitialize);
      }
      cbuf_->Clear();
   }
   else
   {
      logError(getDeviceName(camera_).c_str(), getCoreErrorText(MMERR_CameraNotAvailable).c_str());
      throw CMMError(getCoreErrorText(MMERR_CameraNotAvailable).c_str(), MMERR_CameraNotAvailable);
   }
   CORE_DEBUG("Circular buffer intitialized based on the current camera.\n");
}

/**
 * Stops streming camera sequence acquisition for a specified camera.
 * @param label Camera name
 */
void CMMCore::stopSequenceAcquisition(const char* label) throw (CMMError)
{
   MM::Camera* pCam = getSpecificDevice<MM::Camera>(label);
   int nRet = pCam->StopSequenceAcquisition();
   if (nRet != DEVICE_OK)
   {
      logError(getDeviceName(camera_).c_str(), getDeviceErrorText(nRet, camera_).c_str());
      throw CMMError(getDeviceErrorText(nRet, camera_).c_str(), MMERR_DEVICE_GENERIC);
   }

   CORE_DEBUG1("Sequence acquisition stopped on %.\n", label);
}

/**
 * Starts the continuous camera sequence acquisition.
 * This command does not block the calling thread for the duration of the acquisition.
 */
void CMMCore::startContinuousSequenceAcquisition(double intervalMs) throw (CMMError)
{
   if (camera_)
   {
      if(camera_->IsCapturing())
      {
         throw CMMError(getCoreErrorText(
            MMERR_NotAllowedDuringSequenceAcquisition).c_str()
            ,MMERR_NotAllowedDuringSequenceAcquisition);
      }

      if (!cbuf_->Initialize(camera_->GetNumberOfChannels(), 1, camera_->GetImageWidth(), camera_->GetImageHeight(), camera_->GetImageBytesPerPixel()))
      {
         logError(getDeviceName(camera_).c_str(), getCoreErrorText(MMERR_CircularBufferFailedToInitialize).c_str());
         throw CMMError(getCoreErrorText(MMERR_CircularBufferFailedToInitialize).c_str(), MMERR_CircularBufferFailedToInitialize);
      }
      cbuf_->Clear();
      int nRet = camera_->StartSequenceAcquisition(intervalMs);
      if (nRet != DEVICE_OK)
         throw CMMError(getDeviceErrorText(nRet, camera_).c_str(), MMERR_DEVICE_GENERIC);
   }
   else
   {
      logError("no camera available", getCoreErrorText(MMERR_CameraNotAvailable).c_str());
      throw CMMError(getCoreErrorText(MMERR_CameraNotAvailable).c_str(), MMERR_CameraNotAvailable);
   }
   CORE_DEBUG("Sequence acquisition started.");
}

/**
 * Stops straming camera sequence acquisition.
 */
void CMMCore::stopSequenceAcquisition() throw (CMMError)
{
   if (camera_)
   {
      int nRet = camera_->StopSequenceAcquisition();
      if (nRet != DEVICE_OK)
      {
         logError(getDeviceName(camera_).c_str(), getDeviceErrorText(nRet, camera_).c_str());
         throw CMMError(getDeviceErrorText(nRet, camera_).c_str(), MMERR_DEVICE_GENERIC);
      }
   }
   else
   {
      logError("no camera available", getCoreErrorText(MMERR_CameraNotAvailable).c_str());
      throw CMMError(getCoreErrorText(MMERR_CameraNotAvailable).c_str(), MMERR_CameraNotAvailable);
   }

   CORE_DEBUG("Sequence acquisition stopped.");
}

/**
 * Check if the current camera is acquiring the sequence
 * Returns false when the sequence is done
 */
bool CMMCore::isSequenceRunning() throw ()
{
   return camera_ && camera_->IsCapturing();
};

/**
 * Check if the specified camera is acquiring the sequence
 * Returns false when the sequence is done
 */
bool CMMCore::isSequenceRunning(const char* label) throw (CMMError)
{
   MM::Camera* pCam = getSpecificDevice<MM::Camera>(label);
   return pCam->IsCapturing();
};

/**
 * Gets the last image from the circular buffer.
 * Returns 0 if the buffer is empty.
 */
void* CMMCore::getLastImage() const throw (CMMError)
{

   // scope for the thread guard
   {
      MMThreadGuard g(*pPostedErrorsLock_);

      if(0 < postedErrors_.size())
      {
         std::pair< int, std::string>  toThrow(postedErrors_[0]);
         // todo, process the collection of posted errors.
         postedErrors_.clear();
         throw CMMError( toThrow.second.c_str(), toThrow.first);
      
      }
   }

   unsigned char* pBuf = const_cast<unsigned char*>(cbuf_->GetTopImage());
   if (pBuf != 0)
      return pBuf;
   else
   {
      logError("CMMCore::getLastImage", getCoreErrorText(MMERR_CircularBufferEmpty).c_str());
      throw CMMError(getCoreErrorText(MMERR_CircularBufferEmpty).c_str(), MMERR_CircularBufferEmpty);
   }
}

void* CMMCore::getLastImageMD(unsigned channel, unsigned slice, Metadata& md) const throw (CMMError)
{
   const ImgBuffer* pBuf = cbuf_->GetTopImageBuffer(channel, slice);
   if (pBuf != 0)
   {
      md = pBuf->GetMetadata();
      return const_cast<unsigned char*>(pBuf->GetPixels());
   }
   else
      throw CMMError(getCoreErrorText(MMERR_CircularBufferEmpty).c_str(), MMERR_CircularBufferEmpty);
}

/**
 * Returns a pointer to the pixels of the image that was last inserted into the circular buffer
 * Also provides all metadata associated with that image
 */
void* CMMCore::getLastImageMD(Metadata& md) const throw (CMMError)
{
   return getLastImageMD(0, 0, md);
}

/**
 * Returns a pointer to the pixels of the image that was inserted n images ago 
 * Also provides all metadata associated with that image
 */
void* CMMCore::getNBeforeLastImageMD(unsigned long n, Metadata& md) const throw (CMMError)
{
   const ImgBuffer* pBuf = cbuf_->GetNthFromTopImageBuffer(n);
   if (pBuf != 0)
   {
      md = pBuf->GetMetadata();
      return const_cast<unsigned char*>(pBuf->GetPixels());
   }
   else
      throw CMMError(getCoreErrorText(MMERR_CircularBufferEmpty).c_str(), MMERR_CircularBufferEmpty);
}

/**
 * Gets and removes the next image from the circular buffer.
 * Returns 0 if the buffer is empty.
 */
void* CMMCore::popNextImage() throw (CMMError)
{
   unsigned char* pBuf = const_cast<unsigned char*>(cbuf_->GetNextImage());
   if (pBuf != 0)
      return pBuf;
   else
      throw CMMError(getCoreErrorText(MMERR_CircularBufferEmpty).c_str(), MMERR_CircularBufferEmpty);
}

void* CMMCore::popNextImageMD(unsigned channel, unsigned slice, Metadata& md) throw (CMMError)
{
   const ImgBuffer* pBuf = cbuf_->GetNextImageBuffer(channel, slice);
   if (pBuf != 0)
   {
      md = pBuf->GetMetadata();
      return const_cast<unsigned char*>(pBuf->GetPixels());
   }
   else
      throw CMMError(getCoreErrorText(MMERR_CircularBufferEmpty).c_str(), MMERR_CircularBufferEmpty);
}

void* CMMCore::popNextImageMD(Metadata& md) throw (CMMError)
{
   return popNextImageMD(0, 0, md);
}


/**
 * Reserve memory for the circular buffer.
 */
void CMMCore::setCircularBufferMemoryFootprint(unsigned sizeMB ///< n megabytes
                                               ) throw (CMMError)
{
   delete cbuf_; // discard old buffer
	try
	{
		cbuf_ = new CircularBuffer(sizeMB);
	}
	catch(bad_alloc& ex)
	{
		ostringstream messs;
		messs << getCoreErrorText(MMERR_OutOfMemory).c_str() << " " << ex.what() << endl;
		throw CMMError(messs.str().c_str() , MMERR_OutOfMemory);
	}
	if (NULL == cbuf_) throw CMMError(getCoreErrorText(MMERR_OutOfMemory).c_str(), MMERR_OutOfMemory);


	try
	{

		// attempt to initialize based on the current camera settings
		if (camera_)
		{
			if (!cbuf_->Initialize(camera_->GetNumberOfChannels(), 1, camera_->GetImageWidth(), camera_->GetImageHeight(), camera_->GetImageBytesPerPixel()))
				throw CMMError(getCoreErrorText(MMERR_CircularBufferFailedToInitialize).c_str(), MMERR_CircularBufferFailedToInitialize);
		}

		CORE_DEBUG1("Circular buffer set to %d MB.\n", sizeMB);
	}
	catch(bad_alloc& ex)
	{
		ostringstream messs;
		messs << getCoreErrorText(MMERR_OutOfMemory).c_str() << " " << ex.what() << endl;
		throw CMMError(messs.str().c_str() , MMERR_OutOfMemory);
	}
	if (NULL == cbuf_) throw CMMError(getCoreErrorText(MMERR_OutOfMemory).c_str(), MMERR_OutOfMemory);


}

long CMMCore::getRemainingImageCount()
{
   return cbuf_->GetRemainingImageCount();
}

long CMMCore::getBufferTotalCapacity()
{
   return cbuf_->GetSize();
}

long CMMCore::getBufferFreeCapacity()
{
   return cbuf_->GetFreeSize();
}

double CMMCore::getBufferIntervalMs() const
{
   return cbuf_->GetAverageIntervalMs();
}

bool CMMCore::isBufferOverflowed() const
{
   return cbuf_->Overflow();
}

/**
 * Returns the label of the currently selected camera device.
 * @return camera name
 */
string CMMCore::getCameraDevice()
{
   string deviceName;
   if (camera_)
      try {
         deviceName = pluginManager_.GetDeviceLabel(*camera_);
      }
      catch (CMMError& err)
      {
         // trap the error and ignore it in this case.
         // This happens only if the system is in the inconsistent internal state
         CORE_DEBUG1("Internal error: plugin manager does not recognize device reference. %s\n", err.getMsg().c_str());
      }
   return deviceName;
}

/**
 * Returns the label of the currently selected shutter device.
 * @return shutter name
 */
string CMMCore::getShutterDevice()
{
   string deviceName;
   if (shutter_)
      try {
         deviceName = pluginManager_.GetDeviceLabel(*shutter_);
      }
      catch (CMMError& err)
      {
         // trap the error and ignore it in this case.
         // This happens only if the system is in the inconistent internal state
         CORE_DEBUG1("Internal error: plugin manager does not recognize device reference. %s\n", err.getMsg().c_str());
      }
   return deviceName;
}

/**
 * Returns the label of the currently selected focus device.
 * @return focus stage name
 */
string CMMCore::getFocusDevice()
{
   string deviceName;
   if (focusStage_)
      try {
         deviceName = pluginManager_.GetDeviceLabel(*focusStage_);
      }
      catch (CMMError& err)
      {
         // trap the error and ignore it in this case.
         // This happens only if the system is in the inconistent internal state
         CORE_DEBUG1("Internal error: plugin manager does not recognize device reference. %s\n", err.getMsg().c_str());
      }
   return deviceName;
}

/**
 * Returns the label of the currently selected XYStage device.
 */
string CMMCore::getXYStageDevice()
{
   string deviceName;
   if (xyStage_)
      try {
         deviceName = pluginManager_.GetDeviceLabel(*xyStage_);
      }
      catch (CMMError& err)
      {
         // trap the error and ignore it in this case.
         // This happens only if the system is in the inconistent internal state
         CORE_DEBUG1("Internal error: plugin manager does not recognize device reference. %s\n", err.getMsg().c_str());
      }
   return deviceName;
}

/**
 * Returns the label of the currently selected auto-focus device.
 */
string CMMCore::getAutoFocusDevice()
{
   string deviceName;
   if (autoFocus_)
      try {
         deviceName = pluginManager_.GetDeviceLabel(*autoFocus_);
      }
      catch (CMMError& err)
      {
         // trap the error and ignore it in this case.
         // This happens only if the system is in the inconistent internal state
         CORE_DEBUG1("Internal error: plugin manager does not recognize device reference. %s\n", err.getMsg().c_str());
      }
   return deviceName;
}

/**
 * Sets the current auto-focus device.
 */
void CMMCore::setAutoFocusDevice(const char* autofocusLabel) throw (CMMError)
{
   if (autofocusLabel && strlen(autofocusLabel)>0)
   {
      autoFocus_ = getSpecificDevice<MM::AutoFocus>(autofocusLabel);
      CORE_LOG1("Auto-focus device set to %s\n", autofocusLabel);
   }
   else
   {
      autoFocus_ = 0;
      CORE_LOG("Auto-focus device removed.\n");
   }
   properties_->Refresh(); // TODO: more efficient
   stateCache_.addSetting(PropertySetting(MM::g_Keyword_CoreDevice, MM::g_Keyword_CoreAutoFocus, getAutoFocusDevice().c_str()));
}

/**
 * Returns the label of the currently selected image processor device.
 */
string CMMCore::getImageProcessorDevice()
{
   string deviceName;
   if (imageProcessor_)
      try {
         deviceName = pluginManager_.GetDeviceLabel(*imageProcessor_);
      }
      catch (CMMError& err)
      {
         // trap the error and ignore it in this case.
         // This happens only if the system is in the inconistent internal state
         CORE_DEBUG1("Internal error: plugin manager does not recognize device reference. %s\n", err.getMsg().c_str());
      }
   return deviceName;
}

/**
 * Returns the label of the currently selected SLM device.
 * @return slm name
 */
string CMMCore::getSLMDevice()
{
   string deviceName;
   if (slm_)
      try {
         deviceName = pluginManager_.GetDeviceLabel(*slm_);
      }
      catch (CMMError& err)
      {
         // trap the error and ignore it in this case.
         // This happens only if the system is in the inconsistent internal state
         CORE_DEBUG1("Internal error: plugin manager does not recognize device reference. %s\n", err.getMsg().c_str());
      }
   return deviceName;
}

/**
 * Returns the label of the currently selected Galvo device.
 * @return galvo name
 */
string CMMCore::getGalvoDevice()
{
   string deviceName;
   if (galvo_)
      try {
         deviceName = pluginManager_.GetDeviceLabel(*galvo_);
      }
      catch (CMMError& err)
      {
         // trap the error and ignore it in this case.
         // This happens only if the system is in the inconsistent internal state
         CORE_DEBUG1("Internal error: plugin manager does not recognize device reference. %s\n", err.getMsg().c_str());
      }
   return deviceName;
}


/**
 * Sets the current image processor device.
 */
void CMMCore::setImageProcessorDevice(const char* procLabel) throw (CMMError)
{
   if (procLabel && strlen(procLabel)>0)
   {
      imageProcessor_ = getSpecificDevice<MM::ImageProcessor>(procLabel);
      CORE_LOG1("Image processor device set to %s\n", procLabel);
   }
   else
   {
      imageProcessor_ = 0;
      CORE_LOG("Image processor device removed.\n");
   }
   properties_->Refresh(); // TODO: more efficient
   stateCache_.addSetting(PropertySetting(MM::g_Keyword_CoreDevice, MM::g_Keyword_CoreImageProcessor, getImageProcessorDevice().c_str()));
}

/**
 * Sets the current slm device.
 */
void CMMCore::setSLMDevice(const char* slmLabel) throw (CMMError)
{
   if (slmLabel && strlen(slmLabel)>0)
   {
      slm_ = getSpecificDevice<MM::SLM>(slmLabel);
      CORE_LOG1("Image processor device set to %s\n", slmLabel);
   }
   else
   {
      slm_ = 0;
      CORE_LOG("Image processor device removed.\n");
   }
   properties_->Refresh(); // TODO: more efficient
   stateCache_.addSetting(PropertySetting(MM::g_Keyword_CoreDevice, MM::g_Keyword_CoreSLM, getSLMDevice().c_str()));
}


/**
 * Sets the current galvo device.
 */
void CMMCore::setGalvoDevice(const char* galvoLabel) throw (CMMError)
{
   if (galvoLabel && strlen(galvoLabel)>0)
   {
      galvo_ = getSpecificDevice<MM::Galvo>(galvoLabel);
      CORE_LOG1("Image processor device set to %s\n", galvoLabel);
   }
   else
   {
      galvo_ = 0;
      CORE_LOG("Image processor device removed.\n");
   }
   properties_->Refresh(); // TODO: more efficient
   stateCache_.addSetting(PropertySetting(MM::g_Keyword_CoreDevice, MM::g_Keyword_CoreGalvo, getGalvoDevice().c_str()));
}

/**
 * Speficies the group determining the channel selection.
 */
void CMMCore::setChannelGroup(const char* chGroup) throw (CMMError)
{
   if (chGroup && strlen(chGroup)>0)
   {
      channelGroup_ = chGroup;
      CORE_LOG1("Channel group set to %s\n", chGroup);
   }
   else
   {
      channelGroup_.clear();
   }
   properties_->Refresh(); // TODO: more efficient
   stateCache_.addSetting(PropertySetting(MM::g_Keyword_CoreDevice, MM::g_Keyword_CoreChannelGroup, getChannelGroup().c_str()));
}

/**
 * Returns the group determining the channel selection.
 */
string CMMCore::getChannelGroup()
{
   
   return channelGroup_;
}

/**
 * Sets the current shutter device.
 * @param shutter label
 */
void CMMCore::setShutterDevice(const char* shutterLabel) throw (CMMError)
{
   // Nothing to do if this is the current shutter device:
   if (getShutterDevice().compare(shutterLabel) == 0)
      return;

   // To avoid confusion close the current shutter:
   bool shutterOpen = false;
   if (shutter_) {
      shutterOpen = getShutterOpen();
      if (shutterOpen)
         setShutterOpen(false);
   }

   if (shutterLabel && strlen(shutterLabel)>0)
   {
      // Nothing to do if this is the current shutter device:
      if (getShutterDevice().compare(shutterLabel) == 0)
         return;

      shutter_ = getSpecificDevice<MM::Shutter>(shutterLabel);
      // if old shutter was open, open the new one
      if (shutterOpen)
         setShutterOpen(true);
      //assignImageSynchro(shutterLabel);
      CORE_LOG1("Shutter device set to %s\n", shutterLabel);
   }
   else
   {
      if (shutter_)
      {
         removeImageSynchro(pluginManager_.GetDeviceLabel(*shutter_).c_str());
      }
      shutter_ = 0;
      CORE_LOG("Shutter device removed.\n");
   }
   properties_->Refresh(); // TODO: more efficient
   stateCache_.addSetting(PropertySetting(MM::g_Keyword_CoreDevice, MM::g_Keyword_CoreShutter, getShutterDevice().c_str()));
}

/**
 * Sets the current focus device.
 * @param focus stage label
 */
void CMMCore::setFocusDevice(const char* focusLabel) throw (CMMError)
{
   if (focusLabel && strlen(focusLabel)>0)
   {
      focusStage_ = getSpecificDevice<MM::Stage>(focusLabel);
      CORE_LOG1("Focus device set to %s\n", focusLabel);
   }
   else
   {
      focusStage_ = 0;
      CORE_LOG("Focus device removed.\n");
   }
   properties_->Refresh(); // TODO: more efficient
   stateCache_.addSetting(PropertySetting(MM::g_Keyword_CoreDevice, MM::g_Keyword_CoreFocus, getFocusDevice().c_str()));
}

/**
 * Sets the current XY device.
 */
void CMMCore::setXYStageDevice(const char* xyDeviceLabel) throw (CMMError)
{
   if (xyDeviceLabel && strlen(xyDeviceLabel)>0)
   {
      xyStage_ = getSpecificDevice<MM::XYStage>(xyDeviceLabel);
      CORE_LOG1("XYStage device set to %s\n", xyDeviceLabel);
   }
   else
   {
      xyStage_ = 0;
      CORE_LOG("XYDevice device removed.\n");
   }
   stateCache_.addSetting(PropertySetting(MM::g_Keyword_CoreDevice, MM::g_Keyword_CoreXYStage, getXYStageDevice().c_str()));
}

/**
 * Sets the current camera device.
 * @param camera label
 */
void CMMCore::setCameraDevice(const char* cameraLabel) throw (CMMError)
{
   if (cameraLabel && strlen(cameraLabel) > 0)
   {
      camera_ = getSpecificDevice<MM::Camera>(cameraLabel);
      CORE_LOG1("Camera device set to %s\n", cameraLabel);
   }
   else
   {
      camera_ = 0;
      CORE_LOG("Camera device removed.\n");
   }
   properties_->Refresh(); // TODO: more efficient
   stateCache_.addSetting(PropertySetting(MM::g_Keyword_CoreDevice, MM::g_Keyword_CoreCamera, getCameraDevice().c_str()));
}

/**
 * Returns all property names supported by the device.
 *
 * @return vector<string> property name array
 * @param const char* label device label
 */
vector<string> CMMCore::getDevicePropertyNames(const char* label) const throw (CMMError)
{
   vector<string> propList;

   // in case we requested Core device
   if (strcmp(label, MM::g_Keyword_CoreDevice) == 0)
   {
      propList = properties_->GetNames();
      return propList;
   }

   // regular devices
   try {
      MM::Device* pDevice = pluginManager_.GetDevice(label);
      for (unsigned i=0; i<pDevice->GetNumberOfProperties(); i++)
      {
         char Name[MM::MaxStrLength];
         pDevice->GetPropertyName(i, Name);
         propList.push_back(string(Name));
      }   
   }
   catch (CMMError& err) {
      err.setCoreMsg(getCoreErrorText(err.getCode()).c_str());
      throw;
   }

   return propList;
}

/**
 * Returns an array of labels for currently loaded devices.
 * @return vector<string> array of labels
 */
vector<string> CMMCore::getLoadedDevices() const
{
  vector<string> deviceList = pluginManager_.GetDeviceList();
  deviceList.push_back(MM::g_Keyword_CoreDevice);
  return deviceList;
}

/**
 * Returns an array of labels for currently loaded devices of specific type.
 * @param devType - device type identifier
 * @return vector<string> array of labels
 */
vector<string> CMMCore::getLoadedDevicesOfType(MM::DeviceType devType) const
{
   if (devType == MM::CoreDevice) {
      vector<string> coreDev;
      coreDev.push_back(MM::g_Keyword_CoreDevice);
      return coreDev;
   }

   return pluginManager_.GetDeviceList(devType);
}

/**
 * Returns all valid values for the specified property.
 * If the array is empty it means that there are no restrictions for values.
 * However, even if all values are allowed it is not guaranteed that all of them will be
 * acually accepted by the device at run time.
 *
 * @return vector<string> the array of values
 * @param const char* label device label
 * @param const std::string& propName property name
 */
std::vector<std::string> CMMCore::getAllowedPropertyValues(const char* label, const char* propName) const throw (CMMError)
{
   // in case we requested Core device
   std::vector<std::string> valueList;
   if (strcmp(label, MM::g_Keyword_CoreDevice) == 0)
   {
      valueList = properties_->GetAllowedValues(propName);
   }
   else
   {
      try {
         MM::Device* pDevice = pluginManager_.GetDevice(label);
         for (unsigned i=0; i<pDevice->GetNumberOfPropertyValues(propName); i++)
         {
            char value[MM::MaxStrLength];
            pDevice->GetPropertyValueAt(propName, i, value);
            string strVal(value);
            valueList.push_back(strVal);
         }
      } catch (CMMError& err) {
         err.setCoreMsg(getCoreErrorText(err.getCode()).c_str());
         throw;
      }
   }
   return valueList;
}

/**
 * Returns the property value for the specified device.

 * @return string property value
 * @param const char* label device label
 * @param const char* propName property name
 */
string CMMCore::getProperty(const char* label, const char* propName) const throw (CMMError)
{

   if (label == NULL || propName == NULL)
      throw CMMError(MMERR_NullPointerException);

   // in case we requested Core device
   if (strcmp(label, MM::g_Keyword_CoreDevice) == 0)
   {
      return properties_->Get(propName);
   }

   MM::Device* pDevice;
   try {
      pDevice = pluginManager_.GetDevice(label);
   } catch (CMMError& err) {
      err.setCoreMsg(getCoreErrorText(err.getCode()).c_str());
      throw;
   }

   char value[MM::MaxStrLength];
   int nRet = pDevice->GetProperty(propName, value);
   if (nRet != DEVICE_OK)
      throw CMMError(label, getDeviceErrorText(nRet, pDevice).c_str(), MMERR_DEVICE_GENERIC);
   
   // use the opportunity to update the cache
   // Note, stateCache is mutable so that we can update it from this const function
   PropertySetting s(label, propName, value);
   stateCache_.addSetting(s);

   return string(value);
}
/**
 * Changes the value of the device property.
 *
 * @return void 
 * @param const char* label device label
 * @param const char* propName property name
 * @param const char* propValue the new property value
 */
void CMMCore::setProperty(const char* label, const char* propName, 
                          const char* propValue) throw (CMMError)
{
  if (label == NULL || propName == NULL || propValue == NULL)
     throw CMMError(MMERR_NullPointerException);

  // check for forbiden characters
   string val(propValue);
   if (std::string::npos != val.find_first_of(MM::g_FieldDelimiters, 0))
      throw CMMError(label, getCoreErrorText(MMERR_InvalidContents).c_str(), MMERR_InvalidContents);

   // perform special processing for core initialization commands
   if (strcmp(label, MM::g_Keyword_CoreDevice) == 0)
   {
      properties_->Execute(propName, propValue);
      stateCache_.addSetting(PropertySetting(MM::g_Keyword_CoreDevice, propName, propValue));
   }
   else
   {
      MM::Device* pDevice;
      try {
         pDevice = pluginManager_.GetDevice(label);
      } catch (CMMError& err) {
         err.setCoreMsg(getCoreErrorText(err.getCode()).c_str());
         throw;
      }

      int nRet = pDevice->SetProperty(propName, propValue);
      if (nRet != DEVICE_OK) {
         std::ostringstream se;
         se << getDeviceErrorText(nRet, pDevice).c_str() << "(Error code: " << nRet << ")";
         logError(label, se.str().c_str());
         throw CMMError(se.str().c_str(), MMERR_DEVICE_GENERIC);
      }
      stateCache_.addSetting(PropertySetting(label, propName, propValue));
   }

   CORE_DEBUG3("Property set: device=%s, name=%s, value=%s\n", label, propName, propValue);
}

/**
 * Changes the value of the device property.
 *
 * @return void 
 * @param const char* label device label
 * @param const char* propName property name
 * @param const bool propValue the new property value
 */
void CMMCore::setProperty(const char* label, const char* propName, 
                          const bool propValue) throw (CMMError)
{
   std::string svalue = (propValue?"1":"0");
   setProperty(label, propName, svalue.c_str());
}

/**
 * Changes the value of the device property.
 *
 * @return void 
 * @param const char* label device label
 * @param const char* propName property name
 * @param const long propValue the new property value
 */
void CMMCore::setProperty(const char* label, const char* propName, 
                          const long propValue) throw (CMMError)
{
   std::ostringstream ovalue;
   ovalue << propValue;
   setProperty(label, propName, ovalue.str().c_str());
}

/**
 * Changes the value of the device property.
 *
 * @return void 
 * @param const char* label device label
 * @param const char* propName property name
 * @param const float propValue the new property value
 */
void CMMCore::setProperty(const char* label, const char* propName, 
                          const float propValue) throw (CMMError)
{
   std::ostringstream ovalue;
   ovalue << propValue;
   setProperty(label, propName, ovalue.str().c_str());
}

/**
 * Changes the value of the device property.
 *
 * @return void 
 * @param const char* label device label
 * @param const char* propName property name
 * @param const double propValue the new property value
 */
void CMMCore::setProperty(const char* label, const char* propName, 
                          const double propValue) throw (CMMError)
{
   std::ostringstream ovalue;
   ovalue << propValue;
   setProperty(label, propName, ovalue.str().c_str());
}






/**
 * Checks if device has a property with a specified name.
 * The exception will be thrown in case device label is not defined.
 */
bool CMMCore::hasProperty(const char* label, const char* propName) const throw (CMMError)
{
   // in case we requested Core device
   if (strcmp(label, MM::g_Keyword_CoreDevice) == 0)
   {
      return properties_->Has(propName);
   }

   MM::Device* pDevice;
   try {
      pDevice = pluginManager_.GetDevice(label);
   } catch (CMMError& err) {
      err.setCoreMsg(getCoreErrorText(err.getCode()).c_str());
      throw;
   }

   return pDevice->HasProperty(propName);
}

/**
 * Tells us whether the property can be modified. 
 *
 * @return bool true for read-only property
 * @param const char* label device label
 * @param const char* propName property name
 */
bool CMMCore::isPropertyReadOnly(const char* label, const char* propName) const throw (CMMError)
{
   // in case we requested Core device
   if (strcmp(label, MM::g_Keyword_CoreDevice) == 0)
   {
      return properties_->IsReadOnly(propName);
   }

   MM::Device* pDevice;
   try {
      pDevice = pluginManager_.GetDevice(label);
   } catch (CMMError& err) {
      err.setCoreMsg(getCoreErrorText(err.getCode()).c_str());
      throw;
   }

   bool bReadOnly;
   int nRet = pDevice->GetPropertyReadOnly(propName, bReadOnly);
   if (nRet != DEVICE_OK)
      throw CMMError(label, getDeviceErrorText(nRet, pDevice).c_str(), MMERR_DEVICE_GENERIC);

   return bReadOnly;
}

/**
 * Tells us whether the property must be defined prior to initialization. 
 *
 * @return bool true for pre-init property
 * @param const char* label device label
 * @param const char* propName property name
 */
bool CMMCore::isPropertyPreInit(const char* label, const char* propName) const throw (CMMError)
{
   // in case we requested Core device
   if (strcmp(label, MM::g_Keyword_CoreDevice) == 0)
   {
      return false;
   }

   MM::Device* pDevice;
   try {
      pDevice = pluginManager_.GetDevice(label);
   } catch (CMMError& err) {
      err.setCoreMsg(getCoreErrorText(err.getCode()).c_str());
      throw;
   }

   bool preInit;
   int nRet = pDevice->GetPropertyInitStatus(propName, preInit);
   if (nRet != DEVICE_OK)
      throw CMMError(label, getDeviceErrorText(nRet, pDevice).c_str(), MMERR_DEVICE_GENERIC);

   return preInit;
}

/**
 * Returns the property lower limit value, if the property has limits - 0 otherwise.
 */
double CMMCore::getPropertyLowerLimit(const char* label, const char* propName) const throw (CMMError)
{
   if (strcmp(label, MM::g_Keyword_CoreDevice) == 0)
   {
      return 0.0;
   }
   else
   {
      MM::Device* pDevice;
      try {
         pDevice = pluginManager_.GetDevice(label);
      } catch (CMMError& err) {
         err.setCoreMsg(getCoreErrorText(err.getCode()).c_str());
         throw;
      }
      double limit;
      int ret = pDevice->GetPropertyLowerLimit(propName, limit);
      if (ret != DEVICE_OK)
         throw CMMError(label, getDeviceErrorText(ret, pDevice).c_str(), MMERR_DEVICE_GENERIC);

      return limit;
   }
}

/**
 * Returns the property uper limit value, if the property has limits - 0 otherwise.
 */
double CMMCore::getPropertyUpperLimit(const char* label, const char* propName) const throw (CMMError)
{
   if (strcmp(label, MM::g_Keyword_CoreDevice) == 0)
   {
      return 0.0;
   }
   else
   {
      MM::Device* pDevice;
      try {
         pDevice = pluginManager_.GetDevice(label);
      } catch (CMMError& err) {
         err.setCoreMsg(getCoreErrorText(err.getCode()).c_str());
         throw;
      }
      double limit;
      int ret = pDevice->GetPropertyUpperLimit(propName, limit);
      if (ret != DEVICE_OK)
         throw CMMError(label, getDeviceErrorText(ret, pDevice).c_str(), MMERR_DEVICE_GENERIC);

      return limit;
   }
}

/**
 * Queries device if the specific property has limits.
 * @param label - devicename
 * @param propName - propertyName
 */
bool CMMCore::hasPropertyLimits(const char* label, const char* propName) const throw (CMMError)
{
   if (strcmp(label, MM::g_Keyword_CoreDevice) == 0)
   {
      return false;
   }
   else
   {
      MM::Device* pDevice;
      try {
         pDevice = pluginManager_.GetDevice(label);
      } catch (CMMError& err) {
         err.setCoreMsg(getCoreErrorText(err.getCode()).c_str());
         throw;
      }

      bool hasLimits;
      int ret = pDevice->HasPropertyLimits(propName, hasLimits);
      if (ret != DEVICE_OK)
         throw CMMError(label, getDeviceErrorText(ret, pDevice).c_str(), MMERR_DEVICE_GENERIC);

      return hasLimits;
   }
}

/**
 * Queries device if the specified property can be used in a sequence
 * @param label - devicename
 * @param propName - propertyName
 */
bool CMMCore::isPropertySequenceable(const char* label, const char* propName) const throw (CMMError)
{
   if (strcmp(label, MM::g_Keyword_CoreDevice) == 0)
   {
      return false;
   }
   else
   {
      MM::Device* pDevice;
      try {
         pDevice = pluginManager_.GetDevice(label);
      } catch (CMMError& err) {
         err.setCoreMsg(getCoreErrorText(err.getCode()).c_str());
         throw;
      }

      bool isSequenceable;
      int ret = pDevice->IsPropertySequenceable(propName, isSequenceable);
      if (ret != DEVICE_OK)
         throw CMMError(label, getDeviceErrorText(ret, pDevice).c_str(), MMERR_DEVICE_GENERIC);

      return isSequenceable;
   }
}


/**
 * Queries device property for the maximum number of events that can be put in a sequence
 * @param label - devicename
 * @param propName - propertyName
 */
long CMMCore::getPropertySequenceMaxLength(const char* label, const char* propName) const throw (CMMError)
{
   if (strcmp(label, MM::g_Keyword_CoreDevice) == 0)
   {
      return 0;
   }
   else
   {
      MM::Device* pDevice;
      try {
         pDevice = pluginManager_.GetDevice(label);
      } catch (CMMError& err) {
         err.setCoreMsg(getCoreErrorText(err.getCode()).c_str());
         throw;
      }

      long numEvents;
      int ret = pDevice->GetPropertySequenceMaxLength(propName, numEvents);
      if (ret != DEVICE_OK)
         throw CMMError(label, getDeviceErrorText(ret, pDevice).c_str(), MMERR_DEVICE_GENERIC);

      return numEvents;
   }
}


/**
 * Starts an ongoing sequence of triggered events in a property of a device
 * This should only be called for device-properties that are sequenceable
 * @param label - deviceName
 * @param propName - propertyName
 */
void CMMCore::startPropertySequence(const char* label, const char* propName) const throw (CMMError)
{
   if (strcmp(label, MM::g_Keyword_CoreDevice) == 0)
   {
      return;
   }
   else
   {
      MM::Device* pDevice;
      try {
         pDevice = pluginManager_.GetDevice(label);
      } catch (CMMError& err) {
         err.setCoreMsg(getCoreErrorText(err.getCode()).c_str());
         throw;
      }

      int ret = pDevice->StartPropertySequence(propName);
      if (ret != DEVICE_OK)
         throw CMMError(label, getDeviceErrorText(ret, pDevice).c_str(), MMERR_DEVICE_GENERIC);
   }
}

/**
 * Stops an ongoing sequence of triggered events in a property of a device
 * This should only be called for device-properties that are sequenceable
 * @param label - deviceName
 * @param propName - propertyName
 */
void CMMCore::stopPropertySequence(const char* label, const char* propName) const throw (CMMError)
{
   if (strcmp(label, MM::g_Keyword_CoreDevice) == 0)
   {
      return;
   }
   else
   {
      MM::Device* pDevice;
      try {
         pDevice = pluginManager_.GetDevice(label);
      } catch (CMMError& err) {
         err.setCoreMsg(getCoreErrorText(err.getCode()).c_str());
         throw;
      }

      int ret = pDevice->StopPropertySequence(propName);
      if (ret != DEVICE_OK)
         throw CMMError(label, getDeviceErrorText(ret, pDevice).c_str(), MMERR_DEVICE_GENERIC);
   }
}

/**
 * Transfer a sequence of events/states/whatever to the device
 * This should only be called for device-properties that are sequenceable
 * @param label - deviceName
 * @param propName - propertyName
 * @param eventSequence - sequence of events/states that the device will execute in reponse to external triggers
 */
void CMMCore::loadPropertySequence(const char* label, const char* propName, std::vector<std::string> eventSequence) const throw (CMMError)
{
   if (strcmp(label, MM::g_Keyword_CoreDevice) == 0)
   {
      return;
   }
   else
   {
      MM::Device* pDevice;
      try {
         pDevice = pluginManager_.GetDevice(label);
      } catch (CMMError& err) {
         err.setCoreMsg(getCoreErrorText(err.getCode()).c_str());
         throw;
      }

      int ret = pDevice->ClearPropertySequence(propName);
      if (ret != DEVICE_OK)
         throw CMMError(label, getDeviceErrorText(ret, pDevice).c_str(), MMERR_DEVICE_GENERIC);

      std::vector<std::string>::iterator it;
      for ( it=eventSequence.begin() ; it < eventSequence.end(); it++ )
      {
         ret = pDevice->AddToPropertySequence(propName, (*it).c_str());
         if (ret != DEVICE_OK)
            throw CMMError(label, getDeviceErrorText(ret, pDevice).c_str(), MMERR_DEVICE_GENERIC);
      }

      ret = pDevice->SendPropertySequence(propName);
      if (ret != DEVICE_OK)
         throw CMMError(label, getDeviceErrorText(ret, pDevice).c_str(), MMERR_DEVICE_GENERIC);

   }
}

/**
 * Returns the intrinsic property type.
 */
MM::PropertyType CMMCore::getPropertyType(const char* label, const char* propName) const throw (CMMError)
{
   if (strcmp(label, MM::g_Keyword_CoreDevice) == 0)
   {
      // TODO: return the proper core type
      return MM::Undef;
   }
   else
   {
      MM::Device* pDevice;
      try {
         pDevice = pluginManager_.GetDevice(label);
      } catch (CMMError& err) {
         err.setCoreMsg(getCoreErrorText(err.getCode()).c_str());
         throw;
      }
      MM::PropertyType pt;
      int ret = pDevice->GetPropertyType(propName, pt);
      if (ret != DEVICE_OK)
         throw CMMError(label, getDeviceErrorText(ret, pDevice).c_str(), MMERR_DEVICE_GENERIC);

      return pt;
   }
}


/**
 * Horizontal dimentsion of the image buffer in pixels.
 * @return unsigned X size
 */
unsigned CMMCore::getImageWidth() const
{
   if (camera_ == 0)
      return 0;

   return camera_->GetImageWidth();
}

/**
 * Vertical dimentsion of the image buffer in pixels.
 * @return unsigned Y size
 */
unsigned CMMCore::getImageHeight() const
{
   if (camera_ == 0)
      return 0;

   return camera_->GetImageHeight();
}

/**
 * How many bytes for each pixel. This value does not necessarily reflect the
 * capabilities of the particular camera A/D converter.
 * @return unsigned number of bytes
 */
unsigned CMMCore::getBytesPerPixel() const
{
   if (camera_ == 0)
      return 0;

   return camera_->GetImageBytesPerPixel();
}

/**
 * How many bits of dynamic range are to be expected from the camera. This value should
 * be used only as a guideline - it does not guarante that image buffer will contain
 * only values from the returned dynamic range.
 *
 * @return unsigned number of bits
 */
unsigned CMMCore::getImageBitDepth() const
{
   if (camera_)
      return camera_->GetBitDepth();
   return 0;
}

/**
 * Returns the number of components the default camera is returning.
 * For example color camera will return 4 components (RGBA) on each snap.
 */
unsigned CMMCore::getNumberOfComponents() const
{
   if (camera_)
      return camera_->GetNumberOfComponents();

   return 0;
}

/**
 * Returns the number of simultaneous channels the default camera is returning.
 */
unsigned CMMCore::getNumberOfCameraChannels() const
{
   if (camera_)
      return camera_->GetNumberOfChannels();

   return 0;
}

string CMMCore::getCameraChannelName(unsigned int channelNr) const
{
   if (camera_)
   {
      char name[MM::MaxStrLength];
      camera_->GetChannelName(channelNr, name);
      return name;
   }
   return "";
}

/**
 * Sets the current exposure setting of the camera in milliseconds.
 * @param double dExp exposure in milliseconds
 */
void CMMCore::setExposure(double dExp) throw (CMMError)
{
   if (camera_)
   {
      camera_->SetExposure(dExp);
      if (camera_->HasProperty(MM::g_Keyword_Exposure))
      {
         char cameraName[MM::MaxStrLength];
         camera_->GetLabel(cameraName);
         stateCache_.addSetting(PropertySetting(cameraName, MM::g_Keyword_Exposure, CDeviceUtils::ConvertToString(dExp)));
      }
   }
   else
      throw CMMError(getCoreErrorText(MMERR_CameraNotAvailable).c_str(), MMERR_CameraNotAvailable);

   CORE_DEBUG1("Exposure set to %.3f ms\n", dExp);
}

/**
 * Returns the current exposure setting of the camera in milliseconds.
 * @return double dExp exposure in milliseconds
 */
double CMMCore::getExposure() const throw (CMMError)
{
   if (camera_)
      return camera_->GetExposure();
   else
      //throw CMMError(getCoreErrorText(MMERR_CameraNotAvailable).c_str(), MMERR_CameraNotAvailable);
      return 0.0;
}

/**
 * Sets hardware Region Of Interest (ROI). This command will
 * change dimensions of the image.
 * 
 * @param int x coordinate of the top left corner
 * @param int y coordinate of the top left corner
 * @param int xSize horizontal dimension
 * @param int ySize vertical dimension
 */
void CMMCore::setROI(int x, int y, int xSize, int ySize) throw (CMMError)
{
   if (camera_)
   {
      int nRet = camera_->SetROI(x, y, xSize, ySize);
      if (nRet != DEVICE_OK)
         throw CMMError(getDeviceErrorText(nRet, camera_).c_str(), MMERR_DEVICE_GENERIC);
   }
   else
      throw CMMError(getCoreErrorText(MMERR_CameraNotAvailable).c_str(), MMERR_CameraNotAvailable);

   CORE_DEBUG4("ROI set to (%d, %d, %d, %d)\n", x, y, xSize, ySize);
}

/**
 * Returns info on the current Region Of Interest (ROI).
 * 
 * @param int x coordinate of the top left corner
 * @param int y coordinate of the top left corner
 * @param int xSize horizontal dimension
 * @param int ySize vertical dimension
 */
void CMMCore::getROI(int& x, int& y, int& xSize, int& ySize) const throw (CMMError)
{
   unsigned uX(0), uY(0), uXSize(0), uYSize(0);
   if (camera_)
   {
      int nRet = camera_->GetROI(uX, uY, uXSize, uYSize);
      if (nRet != DEVICE_OK)
         throw CMMError(getDeviceErrorText(nRet, camera_).c_str(), MMERR_DEVICE_GENERIC);
   }

   x = (int) uX;
   y = (int) uY;
   xSize = (int) uXSize;
   ySize = (int) uYSize;
}

/**
 * Resets the current ROI to the full frame.
 */
void CMMCore::clearROI() throw (CMMError)
{
   if (camera_)
   {
      // effectively clears the current ROI setting
      int nRet = camera_->ClearROI();
      if (nRet != DEVICE_OK)
         throw CMMError(getDeviceErrorText(nRet, camera_).c_str(), MMERR_DEVICE_GENERIC);
   }
}

/**
 * Sets the state (position) on the specific device. The command will fail if
 * the device does not support states.
 *
 * @param const char* deviceLabel device label
 * @param long state new state
 */
void CMMCore::setState(const char* deviceLabel, long state) throw (CMMError)
{
   MM::State* pStateDev = getSpecificDevice<MM::State>(deviceLabel);

   int nRet = pStateDev->SetPosition(state);
   if (nRet != DEVICE_OK)
      throw CMMError(deviceLabel, getDeviceErrorText(nRet, pStateDev).c_str(), MMERR_DEVICE_GENERIC);

   if (pStateDev->HasProperty(MM::g_Keyword_State))
   {
      stateCache_.addSetting(PropertySetting(deviceLabel, MM::g_Keyword_State, CDeviceUtils::ConvertToString(state)));
   }
   if (pStateDev->HasProperty(MM::g_Keyword_Label))
   {
      stateCache_.addSetting(PropertySetting(deviceLabel, MM::g_Keyword_Label, getStateLabel(deviceLabel).c_str()));
   }

   CORE_DEBUG2("%s set to state %d\n", deviceLabel, (int)state);
}

/**
 * Returns the current state (position) on the specific device. The command will fail if
 * the device does not support states.
 *
 * @return long current state
 * @param const char* deviceLabel device label
 */
long CMMCore::getState(const char* deviceLabel) const throw (CMMError)
{
   MM::State* pStateDev = getSpecificDevice<MM::State>(deviceLabel);

   long state;
   int nRet = pStateDev->GetPosition(state);
   if (nRet != DEVICE_OK)
      throw CMMError(deviceLabel, getDeviceErrorText(nRet, pStateDev).c_str(), MMERR_DEVICE_GENERIC);

   return state;
}

/**
 * Returns the total number of available positions (states).
 */
long CMMCore::getNumberOfStates(const char* deviceLabel)
{
   MM::State* pStateDev = getSpecificDevice<MM::State>(deviceLabel);

   return pStateDev->GetNumberOfPositions();
}

/**
 * Sets device state using the previously assigned label (string).
 * 
 * @param const char* deviceLabel device label
 * @param const char* stateLabel state label
 */
void CMMCore::setStateLabel(const char* deviceLabel, const char* stateLabel) throw (CMMError)
{
   MM::State* pStateDev = getSpecificDevice<MM::State>(deviceLabel);

   int nRet = pStateDev->SetPosition(stateLabel);
   if (nRet != DEVICE_OK)
      throw CMMError(deviceLabel, getDeviceErrorText(nRet, pStateDev).c_str(), MMERR_DEVICE_GENERIC);

   if (pStateDev->HasProperty(MM::g_Keyword_Label))
   {
      stateCache_.addSetting(PropertySetting(deviceLabel, MM::g_Keyword_Label, stateLabel));
   }
   if (pStateDev->HasProperty(MM::g_Keyword_State))
   {
      stateCache_.addSetting(PropertySetting(deviceLabel, MM::g_Keyword_State,
                             CDeviceUtils::ConvertToString(getStateFromLabel(deviceLabel, stateLabel))));
   }

   CORE_DEBUG2("%s set to state label %s\n", deviceLabel, stateLabel);
}

/**
 * Returns the current state as the label (string).
 *
 * @return string state label 
 * @param const char* deviceLabel device label
 */
string CMMCore::getStateLabel(const char* deviceLabel) const throw (CMMError)
{
   MM::State* pStateDev = getSpecificDevice<MM::State>(deviceLabel);

   char pos[MM::MaxStrLength];
   int nRet = pStateDev->GetPosition(pos);
   if (nRet != DEVICE_OK)
      throw CMMError(deviceLabel, getDeviceErrorText(nRet, pStateDev).c_str(), MMERR_DEVICE_GENERIC);

   return string(pos);
}

/**
 * Defines a label for the specific state/
 *
 * @param const char* deviceLabel device label
 * @param long state state
 * @param const char* label assingned label
 */
void CMMCore::defineStateLabel(const char* deviceLabel, long state, const char* label) throw (CMMError)
{
   MM::State* pStateDev = getSpecificDevice<MM::State>(deviceLabel);

   // Remember old label so that we can update configurations that use it
   char oldLabel[MM::MaxStrLength];
   int nRet = pStateDev->GetPositionLabel(state, oldLabel);
   if (nRet != DEVICE_OK)
      throw CMMError(deviceLabel, getDeviceErrorText(nRet, pStateDev).c_str(), MMERR_DEVICE_GENERIC);

   // Set new label
   nRet = pStateDev->SetPositionLabel(state, label);
   if (nRet != DEVICE_OK)
      throw CMMError(deviceLabel, getDeviceErrorText(nRet, pStateDev).c_str(), MMERR_DEVICE_GENERIC);

   if (strcmp(label, oldLabel) != 0)
   {
      // Fix existing configurations that use the old label
      std::vector<std::string> configGroups = getAvailableConfigGroups();
      std::vector<std::string>::const_iterator itcfg = configGroups.begin();
      while (itcfg != configGroups.end()) 
      {
         std::vector<std::string> configs = getAvailableConfigs((*itcfg).c_str());
         std::vector<std::string>::const_iterator itcf = configs.begin();
         while (itcf != configs.end()) 
         {
            Configuration conf = getConfigData((*itcfg).c_str(), (*itcf).c_str());
            if (conf.isPropertyIncluded(deviceLabel, MM::g_Keyword_Label) ) 
            {
               PropertySetting setting(deviceLabel, MM::g_Keyword_Label, oldLabel);
               if (conf.isSettingIncluded(setting))
               {
                  deleteConfig((*itcfg).c_str(), (*itcf).c_str(), deviceLabel, MM::g_Keyword_Label);
                  defineConfig((*itcfg).c_str(), (*itcf).c_str(), deviceLabel, MM::g_Keyword_Label, label);
               }
            }
            itcf++;
         }

         itcfg++;
      }
   }

   CORE_DEBUG3("State %d for device %s defined as label %s\n", (int)state, deviceLabel, label);
}

/**
 * Return labels for all states
 *
 * @return vector<string> an array of labels
 * @param const char* deviceLabel device label
 */
vector<string> CMMCore::getStateLabels(const char* deviceLabel) const throw (CMMError)
{
   MM::State* pStateDev = getSpecificDevice<MM::State>(deviceLabel);
   vector<string> stateLabels;
   char label[MM::MaxStrLength];
   for (unsigned i=0; i<pStateDev->GetNumberOfPositions(); i++)
   {
      int nRet = pStateDev->GetPositionLabel(i, label);
      if (nRet != DEVICE_OK)
         throw CMMError(deviceLabel, getDeviceErrorText(nRet, pStateDev).c_str(), MMERR_DEVICE_GENERIC);
      stateLabels.push_back(label);
   }
   return stateLabels;
}

/**
 * Obtain the state for a given label.
 *
 * @return long state 
 * @param const char* deviceLabel device label
 * @param const char* stateLabel state label
 */
long CMMCore::getStateFromLabel(const char* deviceLabel, const char* stateLabel) const throw (CMMError)
{
   MM::State* pStateDev = getSpecificDevice<MM::State>(deviceLabel);
   long state;
   int nRet = pStateDev->GetLabelPosition(stateLabel, state);
   if (nRet != DEVICE_OK)
      throw CMMError(deviceLabel, getDeviceErrorText(nRet, pStateDev).c_str(), MMERR_DEVICE_GENERIC);

   return state;
}

/**
 * Defines a single configuration entry (setting). If the configuration name
 * was not previously defined a new configuration will be automatically created.
 * If the name was previously defined the new setting will be added to its list of
 * property settings. The new setting will override previously defined ones if it
 * refers to the same property name.
 *
 * @param const char* configName configuration name
 * @param const char* deviceLabel device label
 * @param const char* propName property name
 * @param const char* value property value
 */
void CMMCore::defineConfiguration(const char* configName, const char* deviceLabel, const char* propName, const char* value)
{
   // check if the configuration allready exists
   CConfigMap::const_iterator it = configs_.find(configName);
   Configuration* pConf;

   if (it == configs_.end())
   {
		try
		{
			pConf = new Configuration();
		}
		catch(bad_alloc& ex)
		{
			ostringstream messs;
			messs << getCoreErrorText(MMERR_OutOfMemory).c_str() << " " << ex.what() << endl;
			throw CMMError(messs.str().c_str() , MMERR_OutOfMemory);
		}
		if (NULL == pConf) throw CMMError(getCoreErrorText(MMERR_OutOfMemory).c_str(), MMERR_OutOfMemory);
      configs_[configName] = pConf; // add new configuration
   }   
   else
      pConf = it->second;

   // add the setting
   PropertySetting setting(deviceLabel, propName, value);
   pConf->addSetting(setting);

   CORE_DEBUG4("Configuration %s: new setting for device %s defined as %s=%s\n", configName, deviceLabel, propName, value);
}

/**
 * Applies a configuration. The command will fail if the
 * configuration was not previously defined.
 *
 * @param const char* configName configuration name
 */
void CMMCore::setConfiguration(const char* configName) throw (CMMError)
{
   // tolerate empty configuration names
   if (configName == 0 || strlen(configName) == 0)
      return;

   CConfigMap::const_iterator it = configs_.find(configName);
   if (it == configs_.end())
      throw CMMError(configName, getCoreErrorText(MMERR_NoConfiguration).c_str(), MMERR_NoConfiguration);
   
   try {
      applyConfiguration(*it->second);
   } catch (CMMError& err) {
      err.setCoreMsg(getCoreErrorText(err.getCode()).c_str());
      throw;
   }

   CORE_DEBUG1("Configuration %s applied.\n", configName);
}

/**
 * Deletes a configuration. The command will fail if the
 * configuration was not previously defined.
 *
 * @param const char* configName configuration name
 */
void CMMCore::deleteConfiguration(const char* configName) throw (CMMError)
{
   // tolerate empty configuration names
   if (configName == 0 || strlen(configName) == 0)
      return;

   CConfigMap::const_iterator it = configs_.find(configName);
   if (it == configs_.end())
      throw CMMError(configName, getCoreErrorText(MMERR_NoConfiguration).c_str(), MMERR_NoConfiguration);
   configs_.erase(configName);
   CORE_LOG1("Configuration %s deleted.\n", configName);
}


/**
 * Returns all defined configuration names.
 * @return std::vector<string> an array of configuration names
 */
std::vector<string> CMMCore::getAvailableConfigurations() const
{
   vector<string> configList;
   CConfigMap::const_iterator it = configs_.begin();
   while(it != configs_.end())
      configList.push_back(it++->first);

   return configList;
}

/**
 * Returns the current configuration.
 * An empty string is a valid return value, since the system state will not
 * always correspond to any of the defined configurations.
 * Also, in general it is possible that the system state fits multiple configurations.
 * This method will return only the first maching configuration, if any.
 *
 * @return string configuration name
 */
string CMMCore::getConfiguration() const
{


   // Here we find all configurations that "fit" the current state of the system
   // but return only the first one.
   // Still not sure how to treat multiple configurations fitting the system state -
   // but for the time being we will assume that all configurations are unique and
   // therefore we need to return only one. In the future we may revert to returning
   // a vector of mathcing configurations...

   vector<string> configList;
   string empty("");

   CConfigMap::const_iterator it = configs_.begin();
   while(it != configs_.end())
   {
      if (isConfigurationCurrent(*it->second))
         configList.push_back(it->first);
      it++;
   }
   if (configList.empty())
      return empty;
   else
      return configList[0];
}

/**
 * Returns the configuration object for a given name.
 *
 * @return Configuration configuration object
 * @param const char* configName configuration name
 */
Configuration CMMCore::getConfigurationData(const char* configName) const throw (CMMError)
{



   CConfigMap::const_iterator it = configs_.find(configName);
   if (it == configs_.end())
      throw CMMError(configName, getCoreErrorText(MMERR_NoConfiguration).c_str(), MMERR_NoConfiguration);
   return *it->second;
}

/**
 * Checks if the configuration already exists.
 *
 * @return true if the configuration is already defined
 * @param configName configuration name 
 */
bool CMMCore::isConfigurationDefined(const char* configName)
{
   CConfigMap::const_iterator it = configs_.find(configName);
   return it == configs_.end() ? false : true;
}

/**
 * Creates an empty configuration group.
 */
void CMMCore::defineConfigGroup(const char* groupName) throw (CMMError)
{
   if (!configGroups_->Define(groupName))
      throw CMMError(groupName, getCoreErrorText(MMERR_DuplicateConfigGroup).c_str(), MMERR_DuplicateConfigGroup);

   updateAllowedChannelGroups();
   CORE_LOG1("Configuration group %s created.\n", groupName);
}

/**
 * Deletes an entire configuration group.
 */
void CMMCore::deleteConfigGroup(const char* groupName) throw (CMMError)
{
   if (!configGroups_->Delete(groupName))
      throw CMMError(groupName, getCoreErrorText(MMERR_NoConfigGroup).c_str(), MMERR_NoConfigGroup);

   if (0 == channelGroup_.compare(groupName))
      setChannelGroup("");

   updateAllowedChannelGroups();

   CORE_LOG1("Configuration group %s deleted.\n", groupName);
}

/**
 * Renames a configuration group.
 */
void CMMCore::renameConfigGroup(const char* oldGroupName, const char* newGroupName) throw (CMMError)
{
   if (!configGroups_->RenameGroup(oldGroupName, newGroupName))
      throw CMMError(oldGroupName, getCoreErrorText(MMERR_NoConfigGroup).c_str(), MMERR_NoConfigGroup);
   CORE_LOG2("Configuration group %s renamed to %s.\n", oldGroupName, newGroupName);

   updateAllowedChannelGroups();

   if (0 == channelGroup_.compare(oldGroupName))
      setChannelGroup(newGroupName);
}

/**
 * Defines a configuration. If the configuration group/name was not previously defined 
 * a new configuration will be automatically created; otherwise nothing happens.
 *
 * @param groupName group name
 * @param configName configuration name
 */
void CMMCore::defineConfig(const char* groupName, const char* configName) throw (CMMError)
{

   if (strcspn(configName, "/\\*!'") != strlen(configName))
      throw CMMError(configName, getCoreErrorText(MMERR_BadConfigName).c_str(), MMERR_BadConfigName);
   configGroups_->Define(groupName, configName);
   ostringstream txt;
   txt << groupName << "/" << configName;
   CORE_LOG1("Configuration %s defined.\n", txt.str().c_str());
}

/**
 * Defines a single configuration entry (setting). If the configuration group/name
 * was not previously defined a new configuration will be automatically created.
 * If the name was previously defined the new setting will be added to its list of
 * property settings. The new setting will override previously defined ones if it
 * refers to the same property name.
 *
 * @param groupName group name
 * @param configName configuration name
 * @param deviceLabel device label
 * @param propName property name
 * @param value property value
 */
void CMMCore::defineConfig(const char* groupName, const char* configName, const char* deviceLabel, const char* propName, const char* value) throw (CMMError)
{

   if (strcspn(configName, "/\\*!'") != strlen(configName))
      throw CMMError(configName, getCoreErrorText(MMERR_BadConfigName).c_str(), MMERR_BadConfigName);
   configGroups_->Define(groupName, configName, deviceLabel, propName, value);
   ostringstream txt;
   txt << groupName << "/" << configName;
   CORE_LOG4("Configuration %s: new setting for device %s defined as %s=%s\n", txt.str().c_str(), deviceLabel, propName, value);
}



/**
 * Defines a single pixel size entry (setting). 
 * The system will treat pixel size configurations very similar to configuration presets,
 * i.e. it will try to detect if any of the pixel size presets matches the current state of
 * the system.
 * If the pixel size was previously defined the new setting will be added to its list of
 * property settings. The new setting will override previously defined ones if it
 * refers to the same property name.
 *
 * @param resolutionID identifier for one unique property setting
 * @param deviceLabel device label
 * @param propName property name
 * @param value property value
*/
void CMMCore::definePixelSizeConfig(const char* resolutionID, const char* deviceLabel, const char* propName, const char* value)
{
   pixelSizeGroup_->Define(resolutionID, deviceLabel, propName, value);
   CORE_LOG4("Resolution ID %s: for device %s defined as %s=%s\n", resolutionID, deviceLabel, propName, value);
}

/**
 * Defines an empty pixel size entry.
*/

void CMMCore::definePixelSizeConfig(const char* resolutionID)
{
   pixelSizeGroup_->Define(resolutionID);
   CORE_LOG1("Empty Resolution ID %s defined", resolutionID);
}

/**
 * Checks if the Pixel Size Resolution already exists
 *
 * @return true if the configuration is already defined
 */
bool CMMCore::isPixelSizeConfigDefined(const char* resolutionID) const
{
   return  pixelSizeGroup_->Find(resolutionID) != 0;
}

/**
 * Sets pixel size in microns for the specified resolution sensing configuration preset.
 */ 
void CMMCore::setPixelSizeUm(const char* resolutionID, double pixSize)  throw (CMMError)
{
   PixelSizeConfiguration* psc = pixelSizeGroup_->Find(resolutionID);
   if (psc == 0)
      throw CMMError(resolutionID, getCoreErrorText(MMERR_NoConfigGroup).c_str(), MMERR_NoConfigGroup);
   psc->setPixelSizeUm(pixSize);
   CORE_LOG2("Pixel size %f um set for resolution ID: %s\n", pixSize, resolutionID);
}

/**
 * Applies a Pixel Size Configurdation. The command will fail if the
 * configuration was not previously defined.
 * 
 * @param groupName
 * @param configName
 */
void CMMCore::setPixelSizeConfig(const char* resolutionID) throw (CMMError)
{
   PixelSizeConfiguration* psc = pixelSizeGroup_->Find(resolutionID);
   ostringstream os;
   os << resolutionID;
   if (!psc)
   {
      throw CMMError(os.str().c_str(), getCoreErrorText(MMERR_NoConfiguration).c_str(), MMERR_NoConfiguration);
   }
   
   try {
      applyConfiguration(*psc);
   } catch (CMMError& err) {
      err.setCoreMsg(getCoreErrorText(err.getCode()).c_str());
      logError("setPixelSizeConfig", getCoreErrorText(err.getCode()).c_str());
      throw;
   }

   CORE_DEBUG1("Pixel Size Configuration %s applied.\n", os.str().c_str());
}

/**
 * Applies a configuration to a group. The command will fail if the
 * configuration was not previously defined.
 * 
 * @param groupName
 * @param configName
 */
void CMMCore::setConfig(const char* groupName, const char* configName) throw (CMMError)
{
   if (groupName == 0)
   {
      throw CMMError("GroupName not defined", MMERR_NoConfigGroup);
   }
   if (configName == 0)
   {
      throw CMMError("ConfigName not defined", MMERR_NoConfiguration);
   }

   Configuration* pCfg = configGroups_->Find(groupName, configName);
   ostringstream os;
   os << groupName << "/" << configName;
   if (!pCfg)
   {
      throw CMMError(os.str().c_str(), getCoreErrorText(MMERR_NoConfiguration).c_str(), MMERR_NoConfiguration);
   }
   
   try {
      applyConfiguration(*pCfg);
   } catch (CMMError& err) {
      if (err.getCode() != MMERR_DEVICE_GENERIC)
         err.setCoreMsg(getCoreErrorText(err.getCode()).c_str());
      throw;
   }

   CORE_DEBUG1("Configuration %s applied.\n", os.str().c_str());
}

/**
 * Renames a configuration within a specified group. The command will fail if the
 * configuration was not previously defined.
 *
 */
void CMMCore::renameConfig(const char* groupName, const char* oldConfigName, const char* newConfigName) throw (CMMError)
{
   ostringstream os1, os2;
   os1 << groupName << "/" << oldConfigName;
   os2 << groupName << "/" << newConfigName;
   if (!configGroups_->RenameConfig(groupName, oldConfigName, newConfigName)) {
      logError("renameConfig", getCoreErrorText(MMERR_NoConfiguration).c_str());
      throw CMMError(os1.str().c_str(), getCoreErrorText(MMERR_NoConfiguration).c_str(), MMERR_NoConfiguration);
   }
   CORE_LOG2("Configuration %s renamed to %s.\n", os1.str().c_str(), os2.str().c_str());
}

/**
 * Deletes a configuration from a group. The command will fail if the
 * configuration was not previously defined.
 *
 */
void CMMCore::deleteConfig(const char* groupName, const char* configName) throw (CMMError)
{
   ostringstream os;
   os << groupName << "/" << configName;
   if (!configGroups_->Delete(groupName, configName)) {
      logError("deleteConfig", getCoreErrorText(MMERR_NoConfiguration).c_str());
      throw CMMError(os.str().c_str(), getCoreErrorText(MMERR_NoConfiguration).c_str(), MMERR_NoConfiguration);
   }
   CORE_LOG1("Configuration %s deleted.\n", os.str().c_str());
}

/**
 * Deletes a property from a configuration in the specified group. The command will fail if the
 * configuration was not previously defined.
 *
 */
void CMMCore::deleteConfig(const char* groupName, const char* configName, const char* deviceLabel, const char* propName) throw (CMMError)
{
   ostringstream os;
   os << groupName << "/" << configName << "/" << deviceLabel << "/" << propName;
   if (!configGroups_->Delete(groupName, configName, deviceLabel, propName)) {
      logError("deleteConfig", getCoreErrorText(MMERR_NoConfiguration).c_str());
      throw CMMError(os.str().c_str(), getCoreErrorText(MMERR_NoConfiguration).c_str(), MMERR_NoConfiguration);
   }
   CORE_LOG1("Configuration property %s deleted.\n", os.str().c_str());
}




/**
 * Returns all defined configuration names in a given group
 * @return std::vector<string> an array of configuration names
 */
vector<string> CMMCore::getAvailableConfigs(const char* group) const
{
   return configGroups_->GetAvailableConfigs(group);
}

/**
 * Returns the names of all defined configuration groups
 * @return std::vector<string> an array of names of configuration groups
 */
vector<string> CMMCore::getAvailableConfigGroups() const
{
   return configGroups_->GetAvailableGroups();
}

/**
 * Returns all defined resolution preset names
 * @return std::vector<string> an array of resolution presets
 */
vector<string> CMMCore::getAvailablePixelSizeConfigs() const
{
   return pixelSizeGroup_->GetAvailable();
}

/**
 * Returns the current configuration for a given group.
 * An empty string as a valid return value, since the system state will not
 * always correspond to any of the defined configurations.
 * Also, in general it is possible that the system state fits multiple configurations.
 * This method will return only the first matching configuration, if any.
 *
 * @return string configuration name
 */
string CMMCore::getCurrentConfig(const char* groupName) const throw (CMMError)
{


   string empty("");
   if (groupName == NULL)
      throw CMMError(MMERR_NullPointerException);

   vector<string> cfgs = configGroups_->GetAvailableConfigs(groupName);
   if (cfgs.empty())
      return empty;

   Configuration curState = getConfigGroupState(groupName);

   for (size_t i=0; i<cfgs.size(); i++)
   {
      Configuration* pCfg = configGroups_->Find(groupName, cfgs[i].c_str());
      if (pCfg && curState.isConfigurationIncluded(*pCfg))
         return cfgs[i];
   }

   // no match
   return empty;
}

/**
 * Returns the configuration object for a given group and name.
 *
 * @return Configuration configuration object
 */
Configuration CMMCore::getConfigData(const char* groupName, const char* configName) const throw (CMMError)
{
   Configuration* pCfg = configGroups_->Find(groupName, configName);
   if (!pCfg)
   {
      // not found
      ostringstream os;
      os << groupName << "/" << configName;
      logError(os.str().c_str(), getCoreErrorText(MMERR_NoConfiguration).c_str());
      throw CMMError(os.str().c_str(), getCoreErrorText(MMERR_NoConfiguration).c_str(), MMERR_NoConfiguration);
   }
   return *pCfg;
}

/**
 * Returns the configuration object for a give pixel size preset.
 * @return Configuration configuration object
 */
Configuration CMMCore::getPixelSizeConfigData(const char* configName) const throw (CMMError)
{
   Configuration* pCfg = pixelSizeGroup_->Find(configName);
   if (!pCfg)
   {
      // not found
      ostringstream os;
      os << "Pixel size" << "/" << configName;
      logError(os.str().c_str(), getCoreErrorText(MMERR_NoConfiguration).c_str());
      throw CMMError(os.str().c_str(), getCoreErrorText(MMERR_NoConfiguration).c_str(), MMERR_NoConfiguration);
   }
   return *pCfg;
}

/**
 * Renames a pixel size configuration. The command will fail if the
 * configuration was not previously defined.
 *
 */
void CMMCore::renamePixelSizeConfig(const char* oldConfigName, const char* newConfigName) throw (CMMError)
{
   ostringstream os1, os2;
   os1 << "Pixel size" << "/" << oldConfigName;
   os2 << "Pixel size" << "/" << newConfigName;
   if (!pixelSizeGroup_->Rename(oldConfigName, newConfigName)) {
      logError("renamePixelSizeConfig", getCoreErrorText(MMERR_NoConfiguration).c_str());
      throw CMMError(os1.str().c_str(), getCoreErrorText(MMERR_NoConfiguration).c_str(), MMERR_NoConfiguration);
   }
   CORE_LOG2("Pixel Size Configuration %s renamed to %s.\n", os1.str().c_str(), os2.str().c_str());
}

/**
 * Deletes a pixel size configuration. The command will fail if the
 * configuration was not previously defined.
 *
 */
void CMMCore::deletePixelSizeConfig(const char* configName) const throw (CMMError)
{
   ostringstream os;
   os << "Pixel size" << "/" << configName;
   if (!pixelSizeGroup_->Delete(configName)) {
      logError("deletePixelSizeConfig", getCoreErrorText(MMERR_NoConfiguration).c_str());
      throw CMMError(os.str().c_str(), getCoreErrorText(MMERR_NoConfiguration).c_str(), MMERR_NoConfiguration);
   }
   CORE_LOG1("Pixel Size Configuration %s deleted.\n", os.str().c_str());
}


/**
 * Get the current pixel configuration name
 **/
string CMMCore::getCurrentPixelSizeConfig() const throw (CMMError)
{
   // get a list of configuration names
   vector<string> cfgs = pixelSizeGroup_->GetAvailable();
   if (cfgs.empty())
      return "";

   // create a union of configuration settings used in this group
   // and obtain the current state of the system
   Configuration curState;
   for (size_t i=0; i<cfgs.size(); i++) {
      PixelSizeConfiguration* cfgData = pixelSizeGroup_->Find(cfgs[i].c_str());
      assert(cfgData);
      for (size_t i=0; i < cfgData->size(); i++)
      {
         PropertySetting cs = cfgData->getSetting(i); // config setting
         if (!curState.isPropertyIncluded(cs.getDeviceLabel().c_str(), cs.getPropertyName().c_str()))
         {
            try
            {
               string value = getProperty(cs.getDeviceLabel().c_str(), cs.getPropertyName().c_str());
               PropertySetting ss(cs.getDeviceLabel().c_str(), cs.getPropertyName().c_str(), value.c_str()); // state setting
               curState.addSetting(ss);
            }
            catch (CMMError& err)
            {
               // just log error
               logError("GetPixelSizeUm", err.getMsg().c_str());
            }
         }
      }
   }

   // check which one matches the current state
   for (size_t i=0; i<cfgs.size(); i++)
   {
      PixelSizeConfiguration* pCfg = pixelSizeGroup_->Find(cfgs[i].c_str());
      if (pCfg && curState.isConfigurationIncluded(*pCfg))
      {
		 return cfgs[i];
      }
   }

   return "";
}

/**
 * Returns the curent pixel size in microns.
 * This method is based on sensing the current pixel size configuration and adjusting
 * for the binning.
 */
double CMMCore::getPixelSizeUm() const
{
	 string resolutionID = getCurrentPixelSizeConfig();

	 if (resolutionID.length()>0) {
		 // check which one matches the current state
		 PixelSizeConfiguration* pCfg = pixelSizeGroup_->Find(resolutionID.c_str());
		 double pixSize = pCfg->getPixelSizeUm();
		 if (camera_)
		 {
			pixSize *= camera_->GetBinning() / getMagnificationFactor();
		 }
		 return pixSize;
	 } else {
		return 0.0;
	 }
}

/**
 * Returns the pixel size in um for the requested pixel size group
 */
double CMMCore::getPixelSizeUmByID(const char* resolutionID) throw (CMMError)
{
   PixelSizeConfiguration* psc = pixelSizeGroup_->Find(resolutionID);
   if (psc == 0)
      throw CMMError(resolutionID, getCoreErrorText(MMERR_NoConfigGroup).c_str(), MMERR_NoConfigGroup);
   return psc->getPixelSizeUm();
}


/**
 * Returns the product of all Magnifiers in the system or 1.0 when none is found
 * This is used internally by GetPixelSizeUm 
 *
 * @return products of all magnifier devices in the system or 1.0 when none is found
 */
double CMMCore::getMagnificationFactor() const
{
   double magnification = 1.0;
   vector<string> magnifiers = getLoadedDevicesOfType(MM::MagnifierDevice);
   for (size_t i=0; i<magnifiers.size(); i++)
   {
      try
      {
         magnification *= getSpecificDevice<MM::Magnifier>(magnifiers[i].c_str())->GetMagnification();
      }
      catch (CMMError e)
      {
         assert(!"Internal error in generating a list of specific devices");
      }
   }
   return magnification;
}

/**
 * Checks if the configuration already exists within a group.
 *
 * @return true if the configuration is already defined
 */
bool CMMCore::isConfigDefined(const char* groupName, const char* configName)
{
   return  configGroups_->Find(groupName, configName) != 0;
}

/**
 * Checks if the group already exists.
 *
 * @return true if the group is already defined
 */
bool CMMCore::isGroupDefined(const char* groupName)
{
   return  configGroups_->isDefined(groupName);
}

/**
 * Defines a reference for the collection of property-value pairs.
 * This construct is useful for defining
 * interchangeable equipment features, such as objective magnifications, filter wavelengths, etc.
 */
void CMMCore::definePropertyBlock(const char* blockName, const char* propertyName, const char* propertyValue)
{
   // check if the block allready exists
   CPropBlockMap::const_iterator it = propBlocks_.find(blockName);
   PropertyBlock* pBlock;
   if (it == propBlocks_.end())
   {
      pBlock = new PropertyBlock();
      propBlocks_[blockName] = pBlock; // add new block
   }   
   else
      pBlock = it->second;

   // add the pair
   PropertyPair pair(propertyName, propertyValue);
   pBlock->addPair(pair);

   CORE_DEBUG3("Property block %s defined as %s=%s\n", blockName, propertyName, propertyValue);
}

/**
 * Returns all defined property block identifiers.
 */
std::vector<std::string> CMMCore::getAvailablePropertyBlocks() const
{
   vector<string> blkList;
   CPropBlockMap::const_iterator it = propBlocks_.begin();
   while(it != propBlocks_.end())
      blkList.push_back(it++->first);

   return blkList;
}

/**
 * Returns the collection of property-value pairs defined in this block.
 */
PropertyBlock CMMCore::getPropertyBlockData(const char* blockName) const
{
   CPropBlockMap::const_iterator it = propBlocks_.find(blockName);
   if (it == propBlocks_.end())
   {
      logError(blockName, getCoreErrorText(MMERR_InvalidPropertyBlock).c_str());
      throw CMMError(blockName, getCoreErrorText(MMERR_InvalidPropertyBlock).c_str(), MMERR_InvalidPropertyBlock);
   }
   return *it->second;
}

/**
 * Returns the collection of property-value pairs defined for the specific device and state label.
 */
PropertyBlock CMMCore::getStateLabelData(const char* deviceLabel, const char* stateLabel) const
{
   MM::Device* pDevice;
   try {
      pDevice = pluginManager_.GetDevice(deviceLabel);
   } catch (CMMError& err) {
      err.setCoreMsg(getCoreErrorText(err.getCode()).c_str());
      logError(deviceLabel, err.getMsg().c_str());
      throw;
   }

   if (pDevice->GetType() != MM::StateDevice)
   {
      logError(deviceLabel, getCoreErrorText(MMERR_InvalidStateDevice).c_str());
      throw CMMError(deviceLabel, getCoreErrorText(MMERR_InvalidStateDevice).c_str(), MMERR_InvalidStateDevice);
   }

   MM::State* pStateDev = static_cast<MM::State*>(pDevice);

   // check if corresponding label exists
   long pos;
   int nRet = pStateDev->GetLabelPosition(stateLabel, pos);
   if (nRet != DEVICE_OK)
   {
      logError(deviceLabel, getDeviceErrorText(nRet, pStateDev).c_str());
      throw CMMError(deviceLabel, getDeviceErrorText(nRet, pStateDev).c_str(), MMERR_DEVICE_GENERIC);
   }

   PropertyBlock blk;
   try {
      blk = getPropertyBlockData(stateLabel);
   } catch (...) {
      ;
      // if getting data did not succeed for any reason we will assume
      // that there is no connection between state label and property block.
      // In this context it is not an error, we'll just say there is no data. 
   }
   return blk;
}

/**
 * Returns the collection of property-value pairs defined for the current state.
 */
PropertyBlock CMMCore::getData(const char* deviceLabel) const
{
   // here we could have written simply: 
   // return getStateLabelData(deviceLabel, getStateLabel(deviceLabel).c_str());
   // but that would be inefficient beacuse of the multiple index lookup, so we'll
   // do it explicitely:

   // find the device
   MM::Device* pDevice;
   try {
      pDevice = pluginManager_.GetDevice(deviceLabel);
   } catch (CMMError& err) {
      err.setCoreMsg(getCoreErrorText(err.getCode()).c_str());
      logError(deviceLabel, err.getMsg().c_str());
      throw;
   }

   if (pDevice->GetType() != MM::StateDevice)
   {
      logError(deviceLabel, getCoreErrorText(MMERR_InvalidStateDevice).c_str());
      throw CMMError(deviceLabel, getCoreErrorText(MMERR_InvalidStateDevice).c_str(), MMERR_InvalidStateDevice);
   }

   MM::State* pStateDev = static_cast<MM::State*>(pDevice);

   // obtain the current state label
   char pos[MM::MaxStrLength];
   int nRet = pStateDev->GetPosition(pos);
   if (nRet != DEVICE_OK)
   {
      logError(deviceLabel, getDeviceErrorText(nRet, pStateDev).c_str());
      throw CMMError(deviceLabel, getDeviceErrorText(nRet, pStateDev).c_str(), MMERR_DEVICE_GENERIC);
   }

   PropertyBlock blk;
   try {
      blk = getPropertyBlockData(pos);
   } catch (...) {
      ;
      // not an error here - there is just no data for this entry. 
   }
   return blk;
}

int CMMCore::setSerialProperties(const char* portName,
                                 const char* answerTimeout,
                                 const char* baudRate,
                                 const char* delayBetweenCharsMs,
                                 const char* handshaking,
                                 const char* parity,
                                 const char* stopBits) {
   MM::Serial* pSerial = getSpecificDevice<MM::Serial>(portName);
   int ret;
   ret = pSerial->SetProperty(MM::g_Keyword_AnswerTimeout, answerTimeout);
   if (ret != DEVICE_OK) return ret;
   ret = pSerial->SetProperty(MM::g_Keyword_BaudRate, baudRate);
   if (ret != DEVICE_OK) return ret;
   ret = pSerial->SetProperty(MM::g_Keyword_DelayBetweenCharsMs, delayBetweenCharsMs);
   if (ret != DEVICE_OK) return ret;
   ret = pSerial->SetProperty(MM::g_Keyword_Handshaking, handshaking);
   if (ret != DEVICE_OK) return ret;
   pSerial->SetProperty(MM::g_Keyword_Parity, parity);
   if (ret != DEVICE_OK) return ret;
   ret = pSerial->SetProperty(MM::g_Keyword_StopBits, stopBits);
   return ret;
}


/**
 * Send string to the serial device and return an answer.
 * This command blocks until it recives an answer fromt he device terminated by the specified
 * sequence.
 */
void CMMCore::setSerialPortCommand(const char* name, const char* command, const char* term) throw (CMMError)
{
   MM::Serial* pSerial = getSpecificDevice<MM::Serial>(name);
   int ret = pSerial->SetCommand(command, term);
   if (ret != DEVICE_OK)
   {
      logError(name, getDeviceErrorText(ret, pSerial).c_str());
      throw CMMError(name, getDeviceErrorText(ret, pSerial).c_str(), MMERR_DEVICE_GENERIC);
   }
}

/**
 * Continouously read from the serial port until the terminating sequence is encountered.
 */
std::string CMMCore::getSerialPortAnswer(const char* name, const char* term) throw (CMMError) 
{
   MM::Serial* pSerial = getSpecificDevice<MM::Serial>(name);

   const int bufLen = 1024;
   char answerBuf[bufLen];
   int ret = pSerial->GetAnswer(answerBuf, bufLen, term);
   if (ret != DEVICE_OK)
   {
      string errText = getDeviceErrorText(ret, pSerial).c_str();
      logError(name, errText.c_str());
      throw CMMError(name, errText.c_str(), MMERR_DEVICE_GENERIC);
   }

   return string(answerBuf);
}

/**
 * Sends an array of characters to the serial port and returns immediately.
 */
void CMMCore::writeToSerialPort(const char* name, const std::vector<char> &data) throw (CMMError)
{
   MM::Serial* pSerial = getSpecificDevice<MM::Serial>(name);
   int ret = pSerial->Write((unsigned char*)(&(data[0])), (unsigned long)data.size());
   if (ret != DEVICE_OK)
   {
      logError(name, getDeviceErrorText(ret, pSerial).c_str());
      throw CMMError(name, getDeviceErrorText(ret, pSerial).c_str(), MMERR_DEVICE_GENERIC);
   }
}

/**
 * Reads the contents of the Rx buffer.
 */
vector<char> CMMCore::readFromSerialPort(const char* name) throw (CMMError)
{
   MM::Serial* pSerial = getSpecificDevice<MM::Serial>(name);

   const int bufLen = 1024; // internal chunk size limit
   unsigned char answerBuf[bufLen];
   unsigned long read;
   int ret = pSerial->Read(answerBuf, bufLen, read);
   if (ret != DEVICE_OK)
   {
      logError(name, getDeviceErrorText(ret, pSerial).c_str());
      throw CMMError(name, getDeviceErrorText(ret, pSerial).c_str(), MMERR_DEVICE_GENERIC);
   }

   vector<char> data;
   data.resize(read, 0);
   memcpy(&(data[0]), answerBuf, read);

   return data;
}


/**
 * Write an 8-bit monochrome image to the SLM.
 */
void CMMCore::setSLMImage(const char* deviceLabel, unsigned char* pixels) throw (CMMError)
{
   MM::SLM* pSLM = getSpecificDevice<MM::SLM>(deviceLabel);

   int ret = pSLM->SetImage(pixels);
   if (ret != DEVICE_OK)
   {
      logError(deviceLabel, getDeviceErrorText(ret, pSLM).c_str());
      throw CMMError(deviceLabel, getDeviceErrorText(ret, pSLM).c_str(), MMERR_DEVICE_GENERIC);
   }
}

/**
 * Write a 32-bit color image to the SLM.
 */
void CMMCore::setSLMImage(const char* deviceLabel, imgRGB32 pixels) throw (CMMError)
{
   MM::SLM* pSLM = getSpecificDevice<MM::SLM>(deviceLabel);

   int ret = pSLM->SetImage((unsigned int *) pixels);
   if (ret != DEVICE_OK)
   {
      logError(deviceLabel, getDeviceErrorText(ret, pSLM).c_str());
      throw CMMError(deviceLabel, getDeviceErrorText(ret, pSLM).c_str(), MMERR_DEVICE_GENERIC);
   }
}

/**
 * Set all SLM pixels to a single 8-bit intensity.
 */
void CMMCore::setSLMPixelsTo(const char* deviceLabel, unsigned char intensity) throw (CMMError)
{
   MM::SLM* pSLM = getSpecificDevice<MM::SLM>(deviceLabel);

   int ret = pSLM->SetPixelsTo(intensity);
   if (ret != DEVICE_OK)
   {
      logError(deviceLabel, getDeviceErrorText(ret, pSLM).c_str());
      throw CMMError(deviceLabel, getDeviceErrorText(ret, pSLM).c_str(), MMERR_DEVICE_GENERIC);
   }
}

/**
 * Set all SLM pixels to an RGB color.
 */
void CMMCore::setSLMPixelsTo(const char* deviceLabel, unsigned char red, unsigned char green, unsigned char blue) throw (CMMError)
{
   MM::SLM* pSLM = getSpecificDevice<MM::SLM>(deviceLabel);

   int ret = pSLM->SetPixelsTo(red, green, blue);
   if (ret != DEVICE_OK)
   {
      logError(deviceLabel, getDeviceErrorText(ret, pSLM).c_str());
      throw CMMError(deviceLabel, getDeviceErrorText(ret, pSLM).c_str(), MMERR_DEVICE_GENERIC);
   }
}

/**
 * Display the waiting image on the SLM.
 */
void CMMCore::displaySLMImage(const char* deviceLabel) throw (CMMError)
{
   MM::SLM* pSLM = getSpecificDevice<MM::SLM>(deviceLabel);

   int ret = pSLM->DisplayImage();
   if (ret != DEVICE_OK)
   {
      logError(deviceLabel, getDeviceErrorText(ret, pSLM).c_str());
      throw CMMError(deviceLabel, getDeviceErrorText(ret, pSLM).c_str(), MMERR_DEVICE_GENERIC);
   }
}

unsigned CMMCore::getSLMWidth(const char* deviceLabel) const
{
   MM::SLM* pSLM = getSpecificDevice<MM::SLM>(deviceLabel);

   return pSLM->GetWidth();
}

unsigned CMMCore::getSLMHeight(const char* deviceLabel) const
{
   MM::SLM* pSLM = getSpecificDevice<MM::SLM>(deviceLabel);

   return pSLM->GetHeight();
}

unsigned CMMCore::getSLMNumberOfComponents(const char* deviceLabel) const
{
   MM::SLM* pSLM = getSpecificDevice<MM::SLM>(deviceLabel);

   return pSLM->GetNumberOfComponents();
}

unsigned CMMCore::getSLMBytesPerPixel(const char* deviceLabel) const
{
   MM::SLM* pSLM = getSpecificDevice<MM::SLM>(deviceLabel);

   return pSLM->GetBytesPerPixel();
}

/* GALVO CODE */


/**
 * Set the Galvo to an x,y position and fire the laser for a predetermined duration.
 */
void CMMCore::pointGalvoAndFire(const char* deviceLabel, double x, double y, double pulseTime_us) throw (CMMError)
{
   MM::Galvo* pGalvo = getSpecificDevice<MM::Galvo>(deviceLabel);

   int ret = pGalvo->PointAndFire(x,y,pulseTime_us);

   if (ret != DEVICE_OK)
   {
      logError(deviceLabel, getDeviceErrorText(ret, pGalvo).c_str());
      throw CMMError(deviceLabel, getDeviceErrorText(ret, pGalvo).c_str(), MMERR_DEVICE_GENERIC);
   }
}


/**
 * Set the Galvo to an x,y position
 */
void CMMCore::setGalvoPosition(const char* deviceLabel, double x, double y) throw (CMMError)
{
   MM::Galvo* pGalvo = getSpecificDevice<MM::Galvo>(deviceLabel);

   int ret = pGalvo->SetPosition(x, y);

   if (ret != DEVICE_OK)
   {
      logError(deviceLabel, getDeviceErrorText(ret, pGalvo).c_str());
      throw CMMError(deviceLabel, getDeviceErrorText(ret, pGalvo).c_str(), MMERR_DEVICE_GENERIC);
   }
}

/**
 * Get the Galvo x,y position
 */
void CMMCore::getGalvoPosition(const char* deviceLabel, double &x, double &y) throw (CMMError)
{
   MM::Galvo* pGalvo = getSpecificDevice<MM::Galvo>(deviceLabel);

   int ret = pGalvo->GetPosition(x, y);

   if (ret != DEVICE_OK)
   {
      logError(deviceLabel, getDeviceErrorText(ret, pGalvo).c_str());
      throw CMMError(deviceLabel, getDeviceErrorText(ret, pGalvo).c_str(), MMERR_DEVICE_GENERIC);
   }
}


/**
 * Get the Galvo x range
 */
double CMMCore::getGalvoXRange(const char* deviceLabel) throw (CMMError)
{
   MM::Galvo* pGalvo = getSpecificDevice<MM::Galvo>(deviceLabel);
   return pGalvo->GetXRange();
}

/**
 * Get the Galvo y range
 */
double CMMCore::getGalvoYRange(const char* deviceLabel) throw (CMMError)
{
   MM::Galvo* pGalvo = getSpecificDevice<MM::Galvo>(deviceLabel);
   return pGalvo->GetYRange();
}


/**
 * Add a vertex to a galvo polygon.
 */
void CMMCore::addGalvoPolygonVertex(const char* deviceLabel, int polygonIndex, double x, double y) throw (CMMError)
{
   MM::Galvo* pGalvo = getSpecificDevice<MM::Galvo>(deviceLabel);
   int ret =  pGalvo->AddPolygonVertex(polygonIndex, x, y);
   
   if (ret != DEVICE_OK)
   {
      logError(deviceLabel, getDeviceErrorText(ret, pGalvo).c_str());
      throw CMMError(deviceLabel, getDeviceErrorText(ret, pGalvo).c_str(), MMERR_DEVICE_GENERIC);
   }
}

/**
 * Remove all added polygons
 */
void CMMCore::deleteGalvoPolygons(const char* deviceLabel) throw (CMMError)
{
   MM::Galvo* pGalvo = getSpecificDevice<MM::Galvo>(deviceLabel);
   int ret = pGalvo->DeletePolygons();
   
   if (ret != DEVICE_OK)
   {
      logError(deviceLabel, getDeviceErrorText(ret, pGalvo).c_str());
      throw CMMError(deviceLabel, getDeviceErrorText(ret, pGalvo).c_str(), MMERR_DEVICE_GENERIC);
   }
}


/**
 * Load a set of galvo polygons to the device
 */
void CMMCore::loadGalvoPolygons(const char* deviceLabel) throw (CMMError)
{
   MM::Galvo* pGalvo = getSpecificDevice<MM::Galvo>(deviceLabel);
   int ret =  pGalvo->LoadPolygons();
   
   if (ret != DEVICE_OK)
   {
      logError(deviceLabel, getDeviceErrorText(ret, pGalvo).c_str());
      throw CMMError(deviceLabel, getDeviceErrorText(ret, pGalvo).c_str(), MMERR_DEVICE_GENERIC);
   }
}

/**
 * Set the number of times to loop galvo polygons
 */
void CMMCore::setGalvoPolygonRepetitions(const char* deviceLabel, int repetitions) throw (CMMError)
{
   MM::Galvo* pGalvo = getSpecificDevice<MM::Galvo>(deviceLabel);
   int ret =  pGalvo->SetPolygonRepetitions(repetitions);
   
   if (ret != DEVICE_OK)
   {
      logError(deviceLabel, getDeviceErrorText(ret, pGalvo).c_str());
      throw CMMError(deviceLabel, getDeviceErrorText(ret, pGalvo).c_str(), MMERR_DEVICE_GENERIC);
   }
}


/**
 * Run a loop of galvo polygons
 */
void CMMCore::runGalvoPolygons(const char* deviceLabel) throw (CMMError)
{
   MM::Galvo* pGalvo = getSpecificDevice<MM::Galvo>(deviceLabel);
   int ret =  pGalvo->RunPolygons();
   
   if (ret != DEVICE_OK)
   {
      logError(deviceLabel, getDeviceErrorText(ret, pGalvo).c_str());
      throw CMMError(deviceLabel, getDeviceErrorText(ret, pGalvo).c_str(), MMERR_DEVICE_GENERIC);
   }
}

/**
 * Run a sequence of galvo positions
 */
void CMMCore::runGalvoSequence(const char* deviceLabel) throw (CMMError)
{
   MM::Galvo* pGalvo = getSpecificDevice<MM::Galvo>(deviceLabel);
   int ret =  pGalvo->RunSequence();
   
   if (ret != DEVICE_OK)
   {
      logError(deviceLabel, getDeviceErrorText(ret, pGalvo).c_str());
      throw CMMError(deviceLabel, getDeviceErrorText(ret, pGalvo).c_str(), MMERR_DEVICE_GENERIC);
   }
}

/* SYSTEM STATE */


/**
 * Saves the current system state to a text file of the MM specific format.
 * The file records only read-write properties.
 * The file format is directly readable by the complementary loadSystemState() command.
 */
void CMMCore::saveSystemState(const char* fileName) throw (CMMError)
{
   ofstream os;
   os.open(fileName, ios_base::out | ios_base::trunc);
   if (!os.is_open())
   {
      logError(fileName, getCoreErrorText(MMERR_FileOpenFailed).c_str());
      throw CMMError(fileName, getCoreErrorText(MMERR_FileOpenFailed).c_str(), MMERR_FileOpenFailed);
   }

   // save system state
   Configuration config = getSystemState();
   for (size_t i=0; i<config.size(); i++)
   {
      PropertySetting s = config.getSetting(i);
      if (!isPropertyReadOnly(s.getDeviceLabel().c_str(), s.getPropertyName().c_str()))
      {
         os << MM::g_CFGCommand_Property << ',' << s.getDeviceLabel()
            << ',' << s.getPropertyName() << ',' << s.getPropertyValue() << endl;
      }
   }
}

/**
 * Loads the system configuration from the text file conforming to the MM specific format.
 * The configuration contains a list of commands to build the desired system state from
 * read-write properties.
 *
 * Format specification: the same as in loadSystemConfiguration() command
 */
void CMMCore::loadSystemState(const char* fileName) throw (CMMError)
{
   ifstream is;
   is.open(fileName, ios_base::in);
   if (!is.is_open())
   {
      logError(fileName, getCoreErrorText(MMERR_FileOpenFailed).c_str());
      throw CMMError(fileName, getCoreErrorText(MMERR_FileOpenFailed).c_str(), MMERR_FileOpenFailed);
   }

   // Process commands
   const int maxLineLength = 4 * MM::MaxStrLength + 4; // accomodate up to 4 strings and delimiters
   char line[maxLineLength+1];
   vector<string> tokens;
   while(is.getline(line, maxLineLength, '\n'))
   {
      // strip a potential Windows/dos CR
      istringstream il(line);
      il.getline(line, maxLineLength, '\r');
      if (strlen(line) > 0)
      {
         if (line[0] == '#')
         {
            // comment, so skip processing
            continue;
         }

         // parse tokens
         tokens.clear();
         CDeviceUtils::Tokenize(line, tokens, MM::g_FieldDelimiters);

         // non-empty and non-comment lines mush have at least one token
         if (tokens.size() < 1)
            throw CMMError(line, getCoreErrorText(MMERR_InvalidCFGEntry).c_str(), MMERR_InvalidCFGEntry);
            
         if(tokens[0].compare(MM::g_CFGCommand_Property) == 0)
         {
            // set property command
            // --------------------
            if (tokens.size() != 4)
               // invalid format
               throw CMMError(line, getCoreErrorText(MMERR_InvalidCFGEntry).c_str(), MMERR_InvalidCFGEntry);
            try
            {          
               // apply the command
               setProperty(tokens[1].c_str(), tokens[2].c_str(), tokens[3].c_str());
            }
            catch (CMMError& err)
            {
               // log the failure but continue parsing
               CORE_LOG3("Property setting failed: %s-%s-%s\n",
                         tokens[1].c_str(), tokens[2].c_str(), tokens[3].c_str());
               CORE_LOG1("%s\n", err.getMsg().c_str());
            }
         }
      }
   }
   updateAllowedChannelGroups();
}


/**
 * Saves the current system configuration to a text file of the MM specific format.
 * The configuration file records only the information essential to the hardware
 * setup: devices, labels, equipment pre-initialization properties, and configurations. 
 * The file format is the same as for the system state.
 */
void CMMCore::saveSystemConfiguration(const char* fileName) throw (CMMError)
{
   ofstream os;
   os.open(fileName, ios_base::out | ios_base::trunc);
   if (!os.is_open())
   {
      logError(fileName, getCoreErrorText(MMERR_FileOpenFailed).c_str());
      throw CMMError(fileName, getCoreErrorText(MMERR_FileOpenFailed).c_str(), MMERR_FileOpenFailed);
   }

   // insert the system reset command
   // this will unload all current devices
   os << "# Unload all devices" << endl; 
   os << "Property,Core,Initialize,0" << endl;

   // save device list
   os << "# Load devices" << endl;
   vector<string> devices = pluginManager_.GetDeviceList();
   vector<string>::const_iterator it;
   for (it=devices.begin(); it != devices.end(); it++)
   {
      MM::Device* pDev = pluginManager_.GetDevice((*it).c_str());
      char deviceName[MM::MaxStrLength] = "";
      char moduleName[MM::MaxStrLength] = "";
      pDev->GetName(deviceName);
      pDev->GetModuleName(moduleName);
      os << MM::g_CFGCommand_Device << "," << *it << "," << moduleName << "," << deviceName << endl; 
   }

   // save equipment
   os << "# Equipment attributes" << endl;
   vector<string> propBlocks = getAvailablePropertyBlocks();
   for (size_t i=0; i<propBlocks.size(); i++)
   {
      PropertyBlock pb = getPropertyBlockData(propBlocks[i].c_str());
      PropertyPair p;
      for (size_t j=0; j<pb.size(); j++)
      {
         p = pb.getPair(j);
         os << MM::g_CFGCommand_Equipment << ',' << propBlocks[i] << ',' << p.getPropertyName() << ',' << p.getPropertyValue() << endl;
      }
   }

   // save the pre-initlization properties
   os << "# Pre-initialization properties" << endl;
   Configuration config = getSystemState();
   for (size_t i=0; i<config.size(); i++)
   {
      PropertySetting s = config.getSetting(i);

      // check if the property must be set before initialization
      MM::Device* pDevice = pluginManager_.GetDevice(s.getDeviceLabel().c_str());
      if (pDevice)
      {
         bool initStatus = false;
         pDevice->GetPropertyInitStatus(s.getPropertyName().c_str(), initStatus);
         if (initStatus)
         {
            os << MM::g_CFGCommand_Property << ',' << s.getDeviceLabel()
               << ',' << s.getPropertyName() << ',' << s.getPropertyValue() << endl;
         }
      }
   }

   // save the parent (hub) references
   os << "# Hub references" << endl;
   for (it=devices.begin(); it != devices.end(); it++)
   {
      MM::Device* pDev = pluginManager_.GetDevice((*it).c_str());
      char parentID[MM::MaxStrLength];
      pDev->GetParentID(parentID);
      if (strlen(parentID) > 0)
      {
         char label[MM::MaxStrLength];
         pDev->GetLabel(label);
         os << MM::g_CFGCommand_Property << ',' << label << ',' << parentID << endl;
      }
   }


   // insert the initialize command
   os << "Property,Core,Initialize,1" << endl;

   // save delays
   os << "# Delays" << endl;
   for (it=devices.begin(); it != devices.end(); it++)
   {
      MM::Device* pDev = pluginManager_.GetDevice((*it).c_str());
      if (pDev->GetDelayMs() > 0.0)
         os << MM::g_CFGCommand_Delay << "," << *it << "," << pDev->GetDelayMs() << endl; 
   }

   // save labels
   os << "# Labels" << endl;
   vector<string> deviceLabels = pluginManager_.GetDeviceList(MM::StateDevice);
   for (size_t i=0; i<deviceLabels.size(); i++)
   {
      MM::State* pSD = getSpecificDevice<MM::State>(deviceLabels[i].c_str());
      unsigned numPos = pSD->GetNumberOfPositions();
      for (unsigned long j=0; j<numPos; j++)
      {
         char lbl[MM::MaxStrLength];
         pSD->GetPositionLabel(j, lbl);
         os << MM::g_CFGCommand_Label << ',' << deviceLabels[i] << ',' << j << ',' << lbl << endl;
      }
   }

   // save global configurations
   os << "# Global configurations" << endl;
   vector<string> configs = getAvailableConfigurations();
   for (size_t i=0; i<configs.size(); i++)
   {
      Configuration c = getConfigurationData(configs[i].c_str());
      for (size_t j=0; j<c.size(); j++)
      {
         PropertySetting s = c.getSetting(j);
         os << MM::g_CFGCommand_Configuration << ',' << configs[i] << ',' << s.getDeviceLabel() << ',' << s.getPropertyName() << ',' << s.getPropertyValue() << endl;
      }
   }

   // save configuration groups
   os << "# Group configurations" << endl;
   vector<string> groups = getAvailableConfigGroups();
   for (size_t i=0; i<groups.size(); i++)
   {
      // empty group record
      vector<string> configs = getAvailableConfigs(groups[i].c_str());
      if (configs.size() == 0)
            os << MM::g_CFGCommand_ConfigGroup << ',' << groups[i] << endl;

      // normal group records
      for (size_t j=0; j<configs.size(); j++)
      {
         Configuration c = getConfigData(groups[i].c_str(), configs[j].c_str());
         for (size_t k=0; k<c.size(); k++)
         {
            PropertySetting s = c.getSetting(k);
            os << MM::g_CFGCommand_ConfigGroup << ',' << groups[i] << ','
               << configs[j] << ',' << s.getDeviceLabel() << ',' << s.getPropertyName() << ',' << s.getPropertyValue() << endl;
         }
      }
   }

   // save device roles
   os << "# Roles" << endl;
   if (camera_)
   {
      os << MM::g_CFGCommand_Property << ',' << MM::g_Keyword_CoreDevice << ',' << MM::g_Keyword_CoreCamera << ',' << getCameraDevice() << endl; 
   }
   if (shutter_)
   {
      os << MM::g_CFGCommand_Property << ',' << MM::g_Keyword_CoreDevice << ',' << MM::g_Keyword_CoreShutter << ',' << getShutterDevice() << endl; 
   }
   if (focusStage_)
   {
      os << MM::g_CFGCommand_Property << ',' << MM::g_Keyword_CoreDevice << ',' << MM::g_Keyword_CoreFocus << ',' << getFocusDevice() << endl; 
   }
}

/**
 * Loads the system configuration from the text file conforming to the MM specific format.
 * The configuration contains a list of commands to build the desired system state:
 * devices, labels, equipment, properties, and configurations.
 *
 * Format specification:
 * Each line consists of a number of string fields separated by "," (comma) characters.
 * Lines beggining with "#" are ignored (can be used for comments).
 * Each line in the file will be parsed by the system and as a result a corresponding command 
 * will be immediately extecuted.
 * The first field in the line always specifies the command from the following set of values:
 *    Device - executes loadDevice()
 *    Label - executes defineStateLabel() command
 *    Equipment - executes definePropertyBlockCommand()
 *    Property - executes setPropertyCommand()
 *    Configuration - executes defineConfiguration()
 *  
 * The remaining fields in the line will be used for corresponding command parameters.
 * The number of parameters depends on the actual command used.
 * 
 */
void CMMCore::loadSystemConfiguration(const char* fileName) throw (CMMError)
{

   ifstream is;
   is.open(fileName, ios_base::in);
   if (!is.is_open())
   {
      logError(fileName, getCoreErrorText(MMERR_FileOpenFailed).c_str());
      throw CMMError(fileName, getCoreErrorText(MMERR_FileOpenFailed).c_str(), MMERR_FileOpenFailed);
   }

   // Process commands
   const int maxLineLength = 4 * MM::MaxStrLength + 4; // accomodate up to 4 strings and delimiters
   char line[maxLineLength+1];
   vector<string> tokens;

   const int errorLimit = 100; // errors allowed before aborting the load
   ostringstream summaryErrorText;
   int errorCount = 0;
   int lineCount = 0;

   while(is.getline(line, maxLineLength, '\n'))
   {
      // strip a potential Windows/dos CR
      istringstream il(line);
      il.getline(line, maxLineLength, '\r');

      lineCount++;
      if (strlen(line) > 0)
      {
         if (line[0] == '#')
         {
            // comment, so skip processing
            continue;
         }

         // parse tokens
         tokens.clear();
         CDeviceUtils::Tokenize(line, tokens, MM::g_FieldDelimiters);

         try
         {

            // non-empty and non-comment lines mush have at least one token
            if (tokens.size() < 1)
               throw CMMError(line, getCoreErrorText(MMERR_InvalidCFGEntry).c_str(), MMERR_InvalidCFGEntry);
               
            if(tokens[0].compare(MM::g_CFGCommand_Device) == 0)
            {
               // load device command
               // -------------------
               if (tokens.size() != 4)
                  throw CMMError(line, getCoreErrorText(MMERR_InvalidCFGEntry).c_str(), MMERR_InvalidCFGEntry);
               loadDevice(tokens[1].c_str(), tokens[2].c_str(), tokens[3].c_str());
            }
            else if(tokens[0].compare(MM::g_CFGCommand_Property) == 0)
            {
               // set property command
               // --------------------
               if (tokens.size() == 4)
                  setProperty(tokens[1].c_str(), tokens[2].c_str(), tokens[3].c_str());
               else if (tokens.size() == 3)
                  // ...assuming here that the last missing toke represents an empty string
                  setProperty(tokens[1].c_str(), tokens[2].c_str(), "");
               else
                  throw CMMError(line, getCoreErrorText(MMERR_InvalidCFGEntry).c_str(), MMERR_InvalidCFGEntry);
            }
            else if(tokens[0].compare(MM::g_CFGCommand_Delay) == 0)
            {
               // set delay command
               // -----------------
               if (tokens.size() != 3)
                  throw CMMError(line, getCoreErrorText(MMERR_InvalidCFGEntry).c_str(), MMERR_InvalidCFGEntry);
               setDeviceDelayMs(tokens[1].c_str(), atof(tokens[2].c_str()));
            }
            else if(tokens[0].compare(MM::g_CFGCommand_Label) == 0)
            {
               // define label command
               // --------------------
               if (tokens.size() != 4)
                  throw CMMError(line, getCoreErrorText(MMERR_InvalidCFGEntry).c_str(), MMERR_InvalidCFGEntry);
               defineStateLabel(tokens[1].c_str(), atol(tokens[2].c_str()), tokens[3].c_str());
            }
            else if(tokens[0].compare(MM::g_CFGCommand_Configuration) == 0)
            {
               // define configuration command
               // ----------------------------
               if (tokens.size() != 5)
                  throw CMMError(line, getCoreErrorText(MMERR_InvalidCFGEntry).c_str(), MMERR_InvalidCFGEntry);
               defineConfiguration(tokens[1].c_str(), tokens[2].c_str(), tokens[3].c_str(), tokens[4].c_str());
               CORE_LOG1("Obsolete command %s used in configuration file.\n", MM::g_CFGCommand_Configuration);
            }
            else if(tokens[0].compare(MM::g_CFGCommand_ConfigGroup) == 0)
            {
               // define grouped configuration command
               // ------------------------------------
               if (tokens.size() == 6)
                  defineConfig(tokens[1].c_str(), tokens[2].c_str(), tokens[3].c_str(), tokens[4].c_str(), tokens[5].c_str());
               else if (tokens.size() == 5)
               {
                  // we will assume here that the last (missing) token is representing an empty string
                  defineConfig(tokens[1].c_str(), tokens[2].c_str(), tokens[3].c_str(), tokens[4].c_str(), "");
               }
               else if (tokens.size() == 2)
                  defineConfigGroup(tokens[1].c_str());
               else
                  throw CMMError(line, getCoreErrorText(MMERR_InvalidCFGEntry).c_str(), MMERR_InvalidCFGEntry);
            }
            else if(tokens[0].compare(MM::g_CFGCommand_ConfigPixelSize) == 0)
            {
               // define pxiel size configuration command
               // ---------------------------------------
               if (tokens.size() == 5)
                  definePixelSizeConfig(tokens[1].c_str(), tokens[2].c_str(), tokens[3].c_str(), tokens[4].c_str());
               else
                  throw CMMError(line, getCoreErrorText(MMERR_InvalidCFGEntry).c_str(), MMERR_InvalidCFGEntry);
            }
            else if(tokens[0].compare(MM::g_CFGCommand_PixelSize_um) == 0)
            {
               // set pixel size
               // --------------
               if (tokens.size() == 3)
                  setPixelSizeUm(tokens[1].c_str(), atof(tokens[2].c_str()));
               else
                  throw CMMError(line, getCoreErrorText(MMERR_InvalidCFGEntry).c_str(), MMERR_InvalidCFGEntry);
            }
            else if(tokens[0].compare(MM::g_CFGCommand_Equipment) == 0)
            {
               // define configuration command
               // ----------------------------
               if (tokens.size() != 4)
                  throw CMMError(line, getCoreErrorText(MMERR_InvalidCFGEntry).c_str(), MMERR_InvalidCFGEntry);
               definePropertyBlock(tokens[1].c_str(), tokens[2].c_str(), tokens[3].c_str());
            }
            else if(tokens[0].compare(MM::g_CFGCommand_ImageSynchro) == 0)
            {
               // define image sycnhro
               // --------------------
               if (tokens.size() != 2)
                  throw CMMError(line, getCoreErrorText(MMERR_InvalidCFGEntry).c_str(), MMERR_InvalidCFGEntry);
               assignImageSynchro(tokens[1].c_str());
            }
            else if(tokens[0].compare(MM::g_CFGCommand_ParentID) == 0)
            {
               // set parent ID
               // -------------
               if (tokens.size() != 3)
                  throw CMMError(line, getCoreErrorText(MMERR_InvalidCFGEntry).c_str(), MMERR_InvalidCFGEntry);

               setParentLabel(tokens[1].c_str(), tokens[2].c_str());
            }

         }
         catch (CMMError& err)
         {
            summaryErrorText << "Line " << lineCount << ": " << line << endl;
            summaryErrorText << err.getMsg() << endl << endl;
            errorCount++;
            if (errorCount >= errorLimit)
            {
               summaryErrorText << "Too many errors. Loading stopped.";
               throw CMMError(summaryErrorText.str().c_str(), MMERR_InvalidConfigurationFile);
            }
         }
      }
   }

   if (errorCount > 0)
   {
      throw CMMError(summaryErrorText.str().c_str(), MMERR_InvalidConfigurationFile);
   }

   updateAllowedChannelGroups();

   // file parsing finished, try to set startup configuration
   if (isConfigDefined(MM::g_CFGGroup_System, MM::g_CFGGroup_System_Startup))
      this->setConfig(MM::g_CFGGroup_System, MM::g_CFGGroup_System_Startup);

   waitForSystem();

   // update the system cache
   updateSystemStateCache();
}


/**
 * Register a callback (listener class).
 * MMCore will send notifications on internal events using this interface
 */
void CMMCore::registerCallback(MMEventCallback* cb)
{
   externalCallback_ = cb;
}


/**
 * Returns the latest focus score from the focusing device.
 * Use this value to estimate or record how reliable the focus is.
 * The range of values is device dependent.
 */
double CMMCore::getLastFocusScore()
{
   if (autoFocus_ != 0)
   {
      double score;
      int ret = autoFocus_->GetLastFocusScore(score);
      if (ret != DEVICE_OK)
         return 0.0;
      return score;
   }
   else
      return 0.0;
}

/**
 * Returns the focus score from the default focusing device measured
 * at the current Z position.
 * Use this value to create profiles or just to verify that the image is in focus.
 * The absolute range of returned scores depends on the actual focusing device.
 */
double CMMCore::getCurrentFocusScore()
{
   if (autoFocus_ != 0)
   {
      double score;
      int ret = autoFocus_->GetCurrentFocusScore(score);
      if (ret != DEVICE_OK)
         return 0.0;
      return score;
   }
   else
      return 0.0;
}


/**
 * Enables or disables the operation of the continouous focusing hardware device.
 */
void CMMCore::enableContinuousFocus(bool enable) throw (CMMError)
{
   if (autoFocus_ != 0)
   {
      int ret = autoFocus_->SetContinuousFocusing(enable);
      if (ret != DEVICE_OK)
      {
         logError(getDeviceName(autoFocus_).c_str(), getDeviceErrorText(ret, autoFocus_).c_str());
         throw CMMError(getDeviceErrorText(ret, autoFocus_).c_str(), MMERR_DEVICE_GENERIC);
      }

      if (enable)
         CORE_LOG("Continuous focusing enabled\n");
      else
         CORE_LOG("Continuous focusing disabled\n");
   }
   else
   {
      if (enable)
      {
         logError("Core",getCoreErrorText(MMERR_ContFocusNotAvailable).c_str());
         throw CMMError(getCoreErrorText(MMERR_ContFocusNotAvailable).c_str(), MMERR_ContFocusNotAvailable);
      }
   }
}

/**
 * Checks if the continouous focusing hardware device is ON or OFF.
 */
bool CMMCore::isContinuousFocusEnabled() throw (CMMError)
{
   if (autoFocus_ != 0)
   {
      bool state;
      int ret = autoFocus_->GetContinuousFocusing(state);
      if (ret != DEVICE_OK)
      {
         logError(getDeviceName(autoFocus_).c_str(), getDeviceErrorText(ret, autoFocus_).c_str());
         throw CMMError(getDeviceErrorText(ret, autoFocus_).c_str(), MMERR_DEVICE_GENERIC);
      }
      return state;
   }
   else
      return false; // no auto-focus device
}

/**
 * Returns the lock-in status of the continuous focusing device.
 */
bool CMMCore::isContinuousFocusLocked() throw (CMMError)
{
   if (autoFocus_ != 0)
      return autoFocus_->IsContinuousFocusLocked();
   else
      return false; // no auto-focus device
}

/**
 * Check if a stage has continuous focusing capability (positions can be set while continuous focus runs).
 */
bool CMMCore::isContinuousFocusDrive(const char* stageLabel) throw (CMMError)
{
   MMThreadGuard guard(deviceLock_);
   MM::Stage* pStage = getSpecificDevice<MM::Stage>(stageLabel);
   return pStage->IsContinuousFocusDrive();
}


/**
 * Performs focus acquisition and lock for the one-shot focusing device.
 */
void CMMCore::fullFocus() throw (CMMError)
{
   if (autoFocus_)
   {
      int ret = autoFocus_->FullFocus();
      if (ret != DEVICE_OK)
      {
         logError(getDeviceName(autoFocus_).c_str(), getDeviceErrorText(ret, autoFocus_).c_str());
         throw CMMError(getDeviceErrorText(ret, autoFocus_).c_str(), MMERR_DEVICE_GENERIC);
      }
   }
   else
   {
      throw CMMError(getCoreErrorText(MMERR_AutoFocusNotAvailable).c_str(), MMERR_AutoFocusNotAvailable);
   }
}

/**
 * Performs incremental focus for the one-shot focusing device.
 */
void CMMCore::incrementalFocus() throw (CMMError)
{
   if (autoFocus_)
   {
      int ret = autoFocus_->IncrementalFocus();
      if (ret != DEVICE_OK)
      {
         logError(getDeviceName(autoFocus_).c_str(), getDeviceErrorText(ret, autoFocus_).c_str());
         throw CMMError(getDeviceErrorText(ret, autoFocus_).c_str(), MMERR_DEVICE_GENERIC);
      }
   }
   else
   {
      throw CMMError(getCoreErrorText(MMERR_AutoFocusNotAvailable).c_str(), MMERR_AutoFocusNotAvailable);
   }
}


/**
 * Applies offset the one-shot focusing device.
 */
void CMMCore::setAutoFocusOffset(double offset) throw (CMMError)
{
   if (autoFocus_)
   {
      int ret = autoFocus_->SetOffset(offset);
      if (ret != DEVICE_OK)
      {
         logError(getDeviceName(autoFocus_).c_str(), getDeviceErrorText(ret, autoFocus_).c_str());
         throw CMMError(getDeviceErrorText(ret, autoFocus_).c_str(), MMERR_DEVICE_GENERIC);
      }
   }
   else
   {
      throw CMMError(getCoreErrorText(MMERR_AutoFocusNotAvailable).c_str(), MMERR_AutoFocusNotAvailable);
   }
}

/**
 * Measures offset for the one-shot focusing device.
 */
double CMMCore::getAutoFocusOffset() throw (CMMError)
{
   if (autoFocus_)
   {
      double offset;
      int ret = autoFocus_->GetOffset(offset);
      if (ret != DEVICE_OK)
      {
         logError(getDeviceName(autoFocus_).c_str(), getDeviceErrorText(ret, autoFocus_).c_str());
         throw CMMError(getDeviceErrorText(ret, autoFocus_).c_str(), MMERR_DEVICE_GENERIC);
      }
      return offset;
   }
   else
   {
      throw CMMError(getCoreErrorText(MMERR_AutoFocusNotAvailable).c_str(), MMERR_AutoFocusNotAvailable);
   }
}



///////////////////////////////////////////////////////////////////////////////
// Private methods
///////////////////////////////////////////////////////////////////////////////

bool CMMCore::isConfigurationCurrent(const Configuration& config) const
{


  // getConfigState(   

   // to dermine whether the current state of the system matches our configuration
   // we need to check property settings one by one
   for (size_t i=0; i<config.size(); i++)
   {
      PropertySetting setting = config.getSetting(i);

      // perform special processing for core commands
      if (setting.getDeviceLabel().compare(MM::g_Keyword_CoreDevice) == 0)
      {
         string coreValue = properties_->Get(setting.getPropertyName().c_str());
         if (coreValue.compare(setting.getPropertyValue()) != 0)
            return false;
         else
            continue;
      }

      // first get device
	   MM::Device* pDevice = 0;
	   try {
		   // >>> TODO: is throwing an exception an efficient way of doing this???
	      // (is getting a wrong device is going to happen a lot?)
		   pDevice = pluginManager_.GetDevice(setting.getDeviceLabel().c_str());
	   } catch (CMMError&) {
	      // trap exception and return "device not found"
         return false;
	   }

      // then fetch property
      char value[MM::MaxStrLength];
      int ret = pDevice->GetProperty(setting.getPropertyName().c_str(), value);
      if (ret != DEVICE_OK)
         return false; // property not found

      // and finally check the value
      if (setting.getPropertyValue().compare(value) != 0)
         return false; // value does not match
   }
   return true;
}

/**
 * Set all properties in a configuration
 * Upon error, don't stop, but try to set all failed properties again
 * until all succees or no more change takes place
 * If errors remain, throw an error 
 */
void CMMCore::applyConfiguration(const Configuration& config) throw (CMMError)
{
   std::ostringstream sall;
   bool error = false;
   vector<PropertySetting> failedProps;
   for (size_t i=0; i<config.size(); i++)
   {
      PropertySetting setting = config.getSetting(i);
      
      // perform special processing for core commands
      if (setting.getDeviceLabel().compare(MM::g_Keyword_CoreDevice) == 0)
      {
         properties_->Execute(setting.getPropertyName().c_str(), setting.getPropertyValue().c_str());
         stateCache_.addSetting(PropertySetting(MM::g_Keyword_CoreDevice, setting.getPropertyName().c_str(), setting.getPropertyValue().c_str()));
      }
      else
      {
         // normal processing
         MM::Device* pDevice = pluginManager_.GetDevice(setting.getDeviceLabel().c_str());
         int ret = pDevice->SetProperty(setting.getPropertyName().c_str(), setting.getPropertyValue().c_str());
         if (ret != DEVICE_OK)
         {
            failedProps.push_back(setting);
            error = true;
         } else
            stateCache_.addSetting(setting);
      }
   }
   if (error) 
   {
      string errorString;
      while (failedProps.size() > (unsigned) applyProperties(failedProps, errorString) )
      {
         if (failedProps.size() == 0)
            return;
      }

      throw CMMError(errorString.c_str(), MMERR_DEVICE_GENERIC);
   }
}

/*
 * Helper function for applyConfiguration
 * It is possible that setting certain properties failed because they are dependend
 * on other properties to be set first. As a workaround, continue to apply these failed
 * properties until there are none left or none succeed
 * returns number of properties succefully set
 */
int CMMCore::applyProperties(vector<PropertySetting>& props, string& lastError)
{
  // int succeeded = 0;
   vector<PropertySetting> failedProps;
   for (size_t i=0; i<props.size(); i++)
   {
      // normal processing
      MM::Device* pDevice = pluginManager_.GetDevice(props[i].getDeviceLabel().c_str());
      int ret = pDevice->SetProperty(props[i].getPropertyName().c_str(), props[i].getPropertyValue().c_str());
      if (ret != DEVICE_OK)
      {
         failedProps.push_back(props[i]);
         std::ostringstream se;
         se << getDeviceErrorText(ret, pDevice).c_str() << "(Error code: " << ret << ")";
         logError(props[i].getDeviceLabel().c_str(), se.str().c_str());
         lastError = se.str();
      } else
         stateCache_.addSetting(props[i]);
   }
   props = failedProps;
   return (int) failedProps.size();
}




string CMMCore::getDeviceErrorText(int deviceCode, MM::Device* pDevice) const
{
   ostringstream txt;
   if (pDevice)
   {
      // device specific error
      char devName[MM::MaxStrLength];
      pDevice->GetLabel(devName);
      txt <<  "Error in device " << devName  << ": ";

      char text[MM::MaxStrLength];
      pDevice->GetErrorText(deviceCode, text);
      if (strlen(text) > 0)
         txt << text << ". ";
   }

   return txt.str();
}

string CMMCore::getCoreErrorText(int code) const
{
   // core info
   string txt;
   map<int, string>::const_iterator it;
   it = errorText_.find(code);   
   if (it != errorText_.end())
      txt = it->second;

   return txt;
}

MM::Device* CMMCore::getDevice(const char* label) const throw (CMMError)
{
   try {
      return pluginManager_.GetDevice(label);
   } catch (CMMError& err) {
      err.setCoreMsg(getCoreErrorText(err.getCode()).c_str());
      logError("core", getCoreErrorText(err.getCode()).c_str());
      throw;
   }
}


void CMMCore::initializeLogging()
{
   // append start day to corelog name
   boost::gregorian::date today( boost::gregorian::day_clock::local_day());

   std::ostringstream sout; 
	sout.fill('0'); 
	sout.width(4);
   sout<<today.year();
   sout << std::setw(2) << today.month().as_number() << std::setw(2) << today.day() ; 
  
   std::string logName = g_logFileName + sout.str() + std::string(".txt");


   IMMLogger::Instance()->Initialize(logName, g_CoreName);
   IMMLogger::Instance()->EnableLogToStderr(true);
   //only "debug" priority messages will have time stamp
   //- requested feature
}

void CMMCore::shutdownLogging()
{
   IMMLogger::Instance()->Shutdown();
}

void CMMCore::logError(const char* device, const char* msg, const char* fileName, int line) const
{
   ostringstream os;
   os << "Device " << device << ". " << msg << endl;
   if (fileName == 0)
      CORE_LOG1("Error occured. %s\n", os.str().c_str());
   else
      CORE_LOG3("Error occured. %s, file %s, line %d\n", os.str().c_str(), fileName, line);
}

string CMMCore::getDeviceName(MM::Device* pDev)
{
   char devName[MM::MaxStrLength];
   pDev->GetName(devName);
   string name(devName);
   return name;
}

/**
* Compress the core log into a gz archive return the path of the archive
* 
*/

std::string CMMCore::saveLogArchive(void)
{
   char* pLogContents = 0;
   unsigned long logLength = 0;
   IMMLogger::Instance()->LogContents(&pLogContents, logLength);
   if( 0 == pLogContents) // file reading failed
   {
      const char* pWarning =
         "MMCore was not able to read the log file!";
      logLength = static_cast<unsigned long>(strlen(pWarning));
      pLogContents = new char[logLength];
      strcpy( pLogContents, pWarning);
   }

   char* pCompressedContents = 0;
   unsigned long compressedLength = 0;

   // prepare a gz archive
   Compressor::CompressData(pLogContents, logLength, &pCompressedContents, compressedLength);
   // finished with the log contents
   delete [] pLogContents;
   pLogContents = 0;

   std::string payLoadPath = IMMLogger::Instance()->LogPath() + ".gz";

   std::ofstream ofile( payLoadPath.c_str(), ios::out|ios::binary);
   if (ofile.is_open())
   {
     if( 0!=pCompressedContents)
      ofile.write( pCompressedContents, compressedLength);
   }
   // finished with the compressed contents
   if( 0 != pCompressedContents)
    free(pCompressedContents);

   CORE_LOG("prepared log archive...");
   return payLoadPath;

}

/**
* just like saveLogArchive, but client can add whatever header desired
* 
*/
std::string CMMCore::saveLogArchiveWithPreamble(char* preamble, ///< beginning of a header to be prepended to the corelog
                                                int preambleLength ///< length of the header
                                                )
{
   if( 0 == preamble)
      preambleLength = 0; // intentionally modifying local copy of arg

   char* pLogContents = 0;
   unsigned long logLength = 0;
   IMMLogger::Instance()->LogContents(&pLogContents, logLength);
   if( 0 == pLogContents) // file reading failed
   {
      const char* pWarning =
         "MMCore was not able to read the log file!";
      logLength = static_cast<unsigned long>(strlen(pWarning));
      pLogContents = new char[logLength];
      strcpy( pLogContents, pWarning);
   }

   char* pEntireMessage = new char[preambleLength + logLength];
   if(0 < preambleLength)
      memcpy( pEntireMessage, preamble, preambleLength);
   memcpy( pEntireMessage + preambleLength, pLogContents, logLength);

   // finished with the log contents
   delete [] pLogContents;
   pLogContents = 0;


   char* pCompressedContents = 0;
   unsigned long compressedLength = 0;

   // prepare a gz archive
   Compressor::CompressData(pEntireMessage, preambleLength + logLength, &pCompressedContents, compressedLength);

   std::string payLoadPath = IMMLogger::Instance()->LogPath() + ".gz";

   std::ofstream ofile( payLoadPath.c_str(), ios::out|ios::binary);
   if (ofile.is_open())
   {
     if( 0!=pCompressedContents)
      ofile.write( pCompressedContents, compressedLength);
   }
   // finished with the compressed contents
   if( 0 != pCompressedContents)
    free(pCompressedContents);

   CORE_LOG("prepared log archive...");
   return payLoadPath;

}

void CMMCore::updateAllowedChannelGroups()
{
   std::vector<std::string> groups = getAvailableConfigGroups();
   properties_->ClearAllowedValues(MM::g_Keyword_CoreChannelGroup);
   properties_->AddAllowedValue(MM::g_Keyword_CoreChannelGroup, ""); // No channel group
   for (unsigned i=0; i<groups.size(); i++)
      properties_->AddAllowedValue(MM::g_Keyword_CoreChannelGroup, groups[i].c_str());

   // If we don't have the group assigned to ChannelGroup anymore, set ChannelGroup to blank.
   if (!isGroupDefined(getChannelGroup().c_str()))
      setChannelGroup("");
}


///////////////////////////////////////////////////////////////////////////////
// Acqusition context methods
// experimental implementation works only with image processor devices
//
void CMMCore::acqBefore() throw (CMMError)
{
}

void CMMCore::acqAfter() throw (CMMError)
{
}

void CMMCore::acqBeforeFrame() throw (CMMError)
{

   if (imageProcessor_)
   {
      int ret = imageProcessor_->AcqBeforeFrame();
      if (ret != DEVICE_OK)
      {
         char name[MM::MaxStrLength];
         imageProcessor_->GetName(name);
         logError(name, getDeviceErrorText(ret, imageProcessor_).c_str());
         throw CMMError(getDeviceErrorText(ret, imageProcessor_).c_str(), MMERR_DEVICE_GENERIC);
      }
   }
}

void CMMCore::acqAfterFrame() throw (CMMError)
{
   if (imageProcessor_)
   {
      int ret = imageProcessor_->AcqAfterFrame();
      if (ret != DEVICE_OK)
      {
         char name[MM::MaxStrLength];
         imageProcessor_->GetName(name);
         logError(name, getDeviceErrorText(ret, imageProcessor_).c_str());
         throw CMMError(getDeviceErrorText(ret, imageProcessor_).c_str(), MMERR_DEVICE_GENERIC);
      }
   }
}

void CMMCore::acqBeforeStack() throw (CMMError)
{
}

void CMMCore::acqAfterStack() throw (CMMError)
{
}


MM::DeviceDetectionStatus CMMCore::detectDevice(char* deviceName)
{
   MM::DeviceDetectionStatus result = MM::Unimplemented; 
   std::vector< std::string> propertiesToRestore;
   std::map< std::string, std::string> valuesToRestore;
   char p[MM::MaxStrLength];
   p[0] = 0;

   try
   {

      MM::Device* pDevice  = pluginManager_.GetDevice(deviceName);

      if( NULL != pDevice)
      {
         if (DEVICE_OK == pDevice->GetProperty(MM::g_Keyword_Port, p))
         {
            if( 0 < strlen(p))
            {
               // there is a valid serial port setting for this device, so 
               // gather the properties that will be restored if we don't find the device


               propertiesToRestore.push_back(MM::g_Keyword_BaudRate);
               propertiesToRestore.push_back(MM::g_Keyword_DataBits);
               propertiesToRestore.push_back(MM::g_Keyword_StopBits);
               propertiesToRestore.push_back(MM::g_Keyword_Parity);
               propertiesToRestore.push_back(MM::g_Keyword_Handshaking);
               propertiesToRestore.push_back(MM::g_Keyword_AnswerTimeout);
               propertiesToRestore.push_back(MM::g_Keyword_DelayBetweenCharsMs);
               // record the current settings before running device detection.
               std::string previousValue;
               for( std::vector< std::string>::iterator sit = propertiesToRestore.begin(); sit!= propertiesToRestore.end(); ++sit)
               {
                  previousValue = getProperty(p,(*sit).c_str());
                  valuesToRestore[*sit] = std::string(previousValue);
               }  
            }
         }
      }

      // run device detection routine
      result = pDevice->DetectDevice();
   }
   catch(...)
   {
      ostringstream txt;
      string port("none");
      if (strlen(p) > 0)
         port = p;
      txt << "Device Detection: error testing ports " << port << " for device " << deviceName;  
      logMessage(txt.str().c_str());
   }

   // if the device is not there, restore the parameters to the original settings
   if ( MM::CanCommunicate != result)
   {
      for( std::vector< std::string>::iterator sit = propertiesToRestore.begin(); sit!= propertiesToRestore.end(); ++sit)
      {
         if( 0 <strlen(p))
         {
            try
            {
               setProperty(p, (*sit).c_str(), (valuesToRestore[*sit]).c_str());
            }
            catch(...)
            {
               ostringstream txt;
               txt << "Device Detection: error restoring port " << p << " state after testing for device " << deviceName;  
               logMessage(txt.str().c_str());
            }
         }
      }
   }

   return result;
}
/**
 * Performs auto-detection and loading of child devices that are attached to a Hub device.
 * For example, if a motorized microscope is represented by a Hub device, it is capable of
 * discovering what specific child devices are currently attached. In that case this call might 
 * report that Z-stage, filter changer and objective turrent are currently installed and return three
 * device names in the string list.
 *
 * @param hubDeviceLabel - A device of type Hub
 */
std::vector<std::string> CMMCore::getInstalledDevices(const char* hubDeviceLabel)
{
   std::vector<std::string> result;
   MM::Device* pDevice  = pluginManager_.GetDevice(hubDeviceLabel);
   if (pDevice && pDevice->GetType() == MM::HubDevice)
   {
      MM::Hub* pHub = static_cast<MM::Hub*>(pDevice);
      pHub->DetectInstalledDevices();
      for(unsigned i=0; i<pHub->GetNumberOfInstalledDevices(); i++)
      {
         MM::Device* pInstDev = pHub->GetInstalledDevice(i);
         char devName[MM::MaxStrLength];
         pInstDev->GetName(devName);
         result.push_back(devName);
      }
   }
   return result;
}

std::vector<std::string> CMMCore::getLoadedPeripheralDevices(const char* hubLabel)
{
   return pluginManager_.GetLoadedPeripherals(hubLabel);
}

std::string CMMCore::getInstalledDeviceDescription(const char* hubLabel, const char* deviceLabel)
{
   std::string result("N/A");
   MM::Device* pDevice  = pluginManager_.GetDevice(hubLabel);
   if (pDevice && pDevice->GetType() == MM::HubDevice)
   {
      MM::Hub* pHub = static_cast<MM::Hub*>(pDevice);
      // do not attempt to detect devices, assume instead
      // that detection has already been performed
      for(unsigned i=0; i<pHub->GetNumberOfInstalledDevices(); i++)
      {
         MM::Device* pInstDev = pHub->GetInstalledDevice(i);
         char descr[MM::MaxStrLength] = "N/A";
         char name[MM::MaxStrLength] = "";
         pInstDev->GetName(name);
         if (strcmp(name, deviceLabel) == 0)
         {
            pInstDev->GetDescription(descr);
            result = descr;
         }
      }
   }
   return result;
}

// at least on OS X, there is a 'primary' MAC address, so we'll
// assume that is the first one.
/**
* Retrieve vector of MAC addresses for the Ethernet cards in the current computer
* formatted xx-xx-xx-xx-xx-xx
* 
*/
std::vector<std::string> CMMCore::getMACAddresses(void)
{
   std::vector<std::string> retv;
   try
   {

      Host* pHost = new Host();
      if(NULL != pHost)
      {
         long status;
         retv =  pHost->MACAddresses(status);

         if( 0 != status)
         {
            std::ostringstream m;
            m << "error retrieving MAC address " << status;
            logMessage(m.str().c_str());
         }
         delete pHost;
      }
   }
   catch(...)
   {

   }
   return retv;
}


