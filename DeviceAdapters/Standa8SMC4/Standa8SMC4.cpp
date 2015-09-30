///////////////////////////////////////////////////////////////////////////////
// FILE:          Standa8SMC4.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Standa stage
//
// AUTHOR:        Eugene Seliverstov, XIMC, http://ximc.ru
//
// COPYRIGHT:     XIMC, 2014-2015
//
// LICENSE:       This file is distributed under the BSD license.
//                License text is included with the source distribution.
//
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//
// Some API descriptions are from Marzhauser-LStep

#include "Standa8SMC4.h"
#include "ModuleInterface.h"
#include "DeviceUtils.h"
#include <set>

const char* g_StageDeviceName = "Standa8SMC4Z";
const char* g_StageDeviceDesc = "Standa8SMC4ZStage";
const char* g_XYStageDeviceName = "Standa8SMC4XY";
const char* g_XYStageDeviceDesc = "Standa8SMC4XYStage";

const char* g_Keyword_Port_X = "Port X";
const char* g_Keyword_Port_Y = "Port Y";
const char* g_Keyword_Port_Z = "Port Z";

const char* g_Keyword_UnitMultiplier = "UnitMultiplier";
const char* g_Keyword_UnitMultiplier_X = "UnitMultiplierX";
const char* g_Keyword_UnitMultiplier_Y = "UnitMultiplierY";

void XIMC_CALLCONV my_logging_callback(int loglevel, const wchar_t* message,
      void *userdata);

// Class tracks a global state needed for stateless ximc logging callback
class LoggingTracker
{
   std::set<MM::Device*> devices_;
   MM::Core *core_;
   bool ximcCallbackSet_;
public:
   LoggingTracker()
      : core_(NULL), ximcCallbackSet_(false)
   {
   }
   void setCoreCallback(MM::Core *core)
   {
      if (!ximcCallbackSet_)
      {
         set_logging_callback(my_logging_callback, NULL);
         ximcCallbackSet_ = true;
      }
      core_ = core;
   }
   void add(MM::Device *device)
   {
      if (device)
         devices_.insert(device);
   }
   void remove(MM::Device *device)
   {
      if (device)
         devices_.erase(device);
   }
   void LogMessage(const std::string& s)
   {
      if (!devices_.empty())
      {
         core_->LogMessage(*(devices_.begin()), s.c_str(), true);
      }
   }
};
LoggingTracker g_loggingTracker;

void XIMC_CALLCONV my_logging_callback(int loglevel, const wchar_t* message,
      void *userdata)
{
   (void)userdata;
   char abuf[4096];
   snprintf( abuf, sizeof(abuf)/sizeof(abuf[0])-1, "Standa8SMC4 %d: %ls", loglevel, message );
   g_loggingTracker.LogMessage(abuf);
}

static unsigned int fixedMicrostepMode(unsigned int microstepMode)
{
   if (microstepMode == 0 || microstepMode > 0x100)
      return MICROSTEP_MODE_FULL;
   return microstepMode;
}

static void FixupPortName(std::string &port)
{
   (void)port;
}


///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////

/**
* List all supported hardware devices here
*/
MODULE_API void InitializeModuleData()
{
   RegisterDevice(g_StageDeviceName, MM::StageDevice, g_StageDeviceDesc);
   RegisterDevice(g_XYStageDeviceName, MM::XYStageDevice, g_XYStageDeviceDesc);
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
   if (deviceName == 0)
      return 0;

   MM::Device *pDevice = NULL;
   // decide which device class to create based on the deviceName parameter
   if (strcmp(deviceName, g_StageDeviceName) == 0)
   {
      pDevice = new Standa8SMC4Z();
   }
   else if (strcmp(deviceName, g_XYStageDeviceName) == 0)
   {
      pDevice = new Standa8SMC4XY();
   }

   g_loggingTracker.add(pDevice);
   return pDevice;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   g_loggingTracker.remove(pDevice);
   delete pDevice;
}

///////////////////////////////////////////////////////////////////////////////
// Standa8SMC4Z implementation
// ~~~~~~~~~~~~~~~~~~~~~~~

/**
* Standa8SMC4Z constructor.
* Setup default all variables and create device properties required to exist
* before intialization. In this case, no such properties were required. All
* properties will be created in the Initialize() method.
*
* As a general guideline Micro-Manager devices do not access hardware in the
* the constructor. We should do as little as possible in the constructor and
* perform most of the initialization in the Initialize() method.
*/
Standa8SMC4Z::Standa8SMC4Z()
   :
      initialized_(false),
      port_("Undefined"),
      unitMultiplier_(1.0),
      originSteps_(0),
      device_(device_undefined),
      operationBusy_(false)
{
   // call the base class method to set-up default error codes/messages
   InitializeDefaultErrorMessages();

   SetErrorText(ERR_PORT_CHANGE, "Cannot update COM port after intialization.");

   // Description property
   int ret = CreateProperty(MM::g_Keyword_Description, g_StageDeviceDesc, MM::String, true);
   assert(ret == DEVICE_OK);
   (void)ret;

   CPropertyAction* pAction;
   // Port
   pAction = new CPropertyAction (this, &Standa8SMC4Z::OnPort);
   CreateProperty(g_Keyword_Port_Z, "Port Z", MM::String, false, pAction, true);

   // Multiplier
   pAction = new CPropertyAction (this, &Standa8SMC4Z::OnUnitMultiplier);
   CreateProperty(g_Keyword_UnitMultiplier, "Unit Multiplier", MM::Float, false, pAction, true);
}

/**
* Standa8SMC4Z destructor.
* If this device used as intended within the Micro-Manager system,
* Shutdown() will be always called before the destructor. But in any case
* we need to make sure that all resources are properly released even if
* Shutdown() was not called.
*/
Standa8SMC4Z::~Standa8SMC4Z()
{
   if (initialized_)
      Shutdown();

   // Deinit
}

/**
* Obtains device name.
* Required by the MM::Device API.
*/
void Standa8SMC4Z::GetName(char* name) const
{
   // We just return the name we use for referring to this
   // device adapter.
   CDeviceUtils::CopyLimitedString(name, g_StageDeviceName);
}

/**
* Intializes the hardware.
* Typically we access and initialize hardware at this point.
* Device properties are typically created here as well.
* Required by the MM::Device API.
*/
int Standa8SMC4Z::Initialize()
{
   int ret;
   result_t result;

   if (initialized_)
      return DEVICE_OK;

   // set property list
   // -----------------

   // synchronize all properties
   // --------------------------
   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

   // setup
   g_loggingTracker.setCoreCallback(GetCoreCallback());

   FixupPortName(port_);

   LogMessage("In Standa8SMC4Z::initialize", true);
   LogMessage("using port " + port_, true);

   if (device_ != device_undefined)
   {
      LogMessage("device already opened", true);
      return DEVICE_INTERNAL_INCONSISTENCY;
   }

   device_ = open_device(port_.c_str());

   if (device_ == device_undefined)
   {
      LogMessage("cannot open standa device", true);
      return DEVICE_NOT_CONNECTED;
   }

   /* fill calibration structure */
   engine_settings_t dengine;
   result = get_engine_settings(device_, &dengine);
   if (result != result_ok)
   {
      LogMessage("cannot get engine settings", true);
      return DEVICE_NOT_SUPPORTED;
   }
   calibration_.A = unitMultiplier_;
   calibration_.MicrostepMode = fixedMicrostepMode(dengine.MicrostepMode);

   originSteps_ = 0;

   initialized_ = true;
   return DEVICE_OK;
}

/**
* Shuts down (unloads) the device.
* Ideally this method will completely unload the device and release all resources.
* Shutdown() may be called multiple times in a row.
* Required by the MM::Device API.
*/
int Standa8SMC4Z::Shutdown()
{
   result_t result;
   if (device_ != device_undefined)
   {
      result = close_device(&device_);
      if (result != result_ok)
         LogMessage("cannot close device");
      device_ = device_undefined;
   }
   initialized_ = false;
   return DEVICE_OK;
}

bool Standa8SMC4Z::Busy()
{
   LogMessage("Standa8SMC4Z::Busy", true);
   if (!initialized_ || device_ == device_undefined)
      return false;

   result_t result;
   status_t status;
   bool busy = false;

   if (operationBusy_)
   {
     result = get_status(device_, &status);
     if (result != result_ok)
     {
       return false;
     }

     busy = (status.MvCmdSts & MVCMD_RUNNING) != 0;
   }

   LogMessage(std::string("Standa8SMC4Z::Busy returned ") +
      std::string(CDeviceUtils::ConvertToString(busy)),
      true);

   return busy;
}


///////////////////////////////////////////////////////////////////////////////
// Standa8SMC4Z Action handlers
///////////////////////////////////////////////////////////////////////////////

/**
* Sets position in um
*/
int Standa8SMC4Z::SetPositionUm(double pos)
{
   LogMessage(std::string("Standa8SMC4Z::SetPositionUm ") +
      " pos=" + std::string(CDeviceUtils::ConvertToString(pos)), true);

   operationBusy_ = false;
   if (!initialized_ || device_ == device_undefined)
      return DEVICE_NOT_CONNECTED;

   result_t result;

   float newpos = static_cast<float>(originSteps_ * unitMultiplier_ + pos);
   result = command_move_calb(device_, newpos, &calibration_);
   if (result != result_ok)
   {
      return DEVICE_UNKNOWN_POSITION;
   }

   // Set future position
   OnStagePositionChanged(pos);

   return DEVICE_OK;
}

/**
* Returns current position in um.
*/
int Standa8SMC4Z::GetPositionUm(double& pos)
{
   LogMessage("Standa8SMC4Z::GetPositionUm", true);

   // getter must not change busy state of movement comamands so do not set operationBusy here
   if (!initialized_ || device_ == device_undefined)
      return DEVICE_NOT_CONNECTED;

   result_t result;
   get_position_calb_t dpos;
   result = get_position_calb(device_, &dpos, &calibration_);
   if (result != result_ok)
   {
      return DEVICE_UNKNOWN_POSITION;
   }
   pos = static_cast<double>(dpos.Position - originSteps_ * unitMultiplier_);

   LogMessage(std::string("Standa8SMC4Z::GetPositionUm returned ") +
      " pos=" + std::string(CDeviceUtils::ConvertToString(pos)),
      true);

   return DEVICE_OK;
}

/**
* Sets position in steps.
*/
int Standa8SMC4Z::SetPositionSteps(long steps)
{
   LogMessage(std::string("Standa8SMC4Z::SetPositionSteps ") +
      " steps=" + std::string(CDeviceUtils::ConvertToString(steps)), true);

   operationBusy_ = false;
   if (!initialized_ || device_ == device_undefined)
      return DEVICE_NOT_CONNECTED;

   result_t result;

   int newx = originSteps_ + steps;

   result = command_move(device_, newx, 0);
   if (result != result_ok)
   {
      return DEVICE_UNKNOWN_POSITION;
   }

   // Set future position
   OnStagePositionChanged(steps * unitMultiplier_);

   return DEVICE_OK;
}

/**
* Returns current position in steps.
*/
int Standa8SMC4Z::GetPositionSteps(long& steps)
{
   LogMessage("Standa8SMC4Z::GetPositionSteps", true);

   // getter must not change busy state of movement comamands so do not set operationBusy here
   if (!initialized_ || device_ == device_undefined)
      return DEVICE_NOT_CONNECTED;

   result_t result;
   get_position_t dpos;
   result = get_position(device_, &dpos);
   if (result != result_ok)
   {
      return DEVICE_UNKNOWN_POSITION;
   }
   steps = nint(dpos.Position) - originSteps_;

   LogMessage(std::string("Standa8SMC4Z::GetPositionStep returned ") +
      " steps=" + std::string(CDeviceUtils::ConvertToString(steps)),
      true);

   return DEVICE_OK;
}

/**
* Move
*/
int Standa8SMC4Z::Move(double velocity)
{
   LogMessage(std::string("Standa8SMC4Z::Move ") +
      " velocity=" + std::string(CDeviceUtils::ConvertToString(velocity)), true);

   operationBusy_ = false;
   if (!initialized_ || device_ == device_undefined)
      return DEVICE_NOT_CONNECTED;

   result_t result;
   if (velocity > 0)
      result = command_right(device_);
   else
      result = command_left(device_);
   if (result != result_ok)
   {
      return DEVICE_UNKNOWN_POSITION;
   }
   return DEVICE_OK;
}

/**
* Defines position x (relative to current position) as the origin of our coordinate system
*/
int Standa8SMC4Z::SetAdapterOriginUm(double d)
{
   LogMessage(std::string("Standa8SMC4Z::SetAdapterOriginUm d=") +
      CDeviceUtils::ConvertToString(d), true);

   operationBusy_ = false;
   if (!initialized_ || device_ == device_undefined)
      return DEVICE_NOT_CONNECTED;

   result_t result;

   // Issue Zero command
   result = command_zero(device_);
   if (result != result_ok)
   {
     return DEVICE_ERR;
   }

   // Zero coordinate
   originSteps_ = 0;

   return DEVICE_OK;
}

/*
* Defines current position as origin (0) coordinate of the controller
*/
int Standa8SMC4Z::SetOrigin()
{
   LogMessage("Standa8SMC4Z::SetOrigin", true);

   // operationBusy_ and sanity checks are in the following method

   return SetAdapterOriginUm(0.0);
}

/**
* Returns the stage position limits in um.
*/
int Standa8SMC4Z::GetLimits(double& lower, double& upper)
{
   LogMessage("Standa8SMC4Z::GetLimits", true);

   // getter must not change busy state of movement comamands so do not set operationBusy here
   if (!initialized_ || device_ == device_undefined)
      return DEVICE_NOT_CONNECTED;

   result_t result;
   edges_settings_calb_t dedge;
   result = get_edges_settings_calb(device_, &dedge, &calibration_);
   if (result != result_ok)
   {
      return DEVICE_UNKNOWN_POSITION;
   }
   lower = dedge.LeftBorder;
   upper = dedge.RightBorder;
   if (dedge.EnderFlags & ENDER_SWAP)
      std::swap(lower, upper);

   LogMessage(std::string("Standa8SMC4Z::GetLimits returned ") +
      " lower=" + std::string(CDeviceUtils::ConvertToString(lower)) +
      " upper=" + std::string(CDeviceUtils::ConvertToString(upper)),
      true);

   return DEVICE_OK;
}

int Standa8SMC4Z::IsStageSequenceable(bool& isSequenceable) const
{
   isSequenceable = false;
   return DEVICE_OK;
}

bool Standa8SMC4Z::IsContinuousFocusDrive() const
{
   return false;
}

///////////////////////////////////////////////////////////////////////////////
// Actions
///////////////////////////////////////////////////////////////////////////////

int Standa8SMC4Z::OnPort(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(port_.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      if (initialized_)
      {
         pProp->Set(port_.c_str());
         return ERR_PORT_CHANGE;
      }
      pProp->Get(port_);
   }

   return DEVICE_OK;
}

int Standa8SMC4Z::OnUnitMultiplier(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(unitMultiplier_);
   }
   else if (eAct == MM::AfterSet)
   {
      if (initialized_)
      {
         pProp->Set(unitMultiplier_);
         return ERR_PORT_CHANGE;
      }
      pProp->Get(unitMultiplier_);
   }

   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// Private Standa8SMC4Z methods
///////////////////////////////////////////////////////////////////////////////




///////////////////////////////////////////////////////////////////////////////
// Standa8SMC4XY implementation
// ~~~~~~~~~~~~~~~~~~~~~~~

/**
* Standa8SMC4XY constructor.
* Setup default all variables and create device properties required to exist
* before intialization. In this case, no such properties were required. All
* properties will be created in the Initialize() method.
*
* As a general guideline Micro-Manager devices do not access hardware in the
* the constructor. We should do as little as possible in the constructor and
* perform most of the initialization in the Initialize() method.
*/
Standa8SMC4XY::Standa8SMC4XY()
   :
      initialized_(false),
      portX_("Undefined"),
      portY_("Undefined"),
      unitMultiplierX_(1.0),
      unitMultiplierY_(1.0),
      originStepsX_(0),
      originStepsY_(0),
      deviceX_(device_undefined),
      deviceY_(device_undefined),
      // whether last performed operation made a device busy
      operationBusy_(false)
{
   // call the base class method to set-up default error codes/messages
   InitializeDefaultErrorMessages();

   SetErrorText(ERR_PORT_CHANGE, "Cannot update COM port after intialization.");

   // Description property
   int ret = CreateProperty(MM::g_Keyword_Description, g_XYStageDeviceDesc, MM::String, true);
   assert(ret == DEVICE_OK);
   (void)ret;

   CPropertyAction* pAction;
   // Ports
   pAction = new CPropertyAction (this, &Standa8SMC4XY::OnPortX);
   CreateProperty(g_Keyword_Port_X, "Port X", MM::String, false, pAction, true);

   pAction = new CPropertyAction (this, &Standa8SMC4XY::OnPortY);
   CreateProperty(g_Keyword_Port_Y, "Port Y", MM::String, false, pAction, true);

   // Multiplier
   pAction = new CPropertyAction (this, &Standa8SMC4XY::OnUnitMultiplierX);
   CreateProperty(g_Keyword_UnitMultiplier_X, "Unit Multiplier X", MM::Float, false, pAction, true);

   pAction = new CPropertyAction (this, &Standa8SMC4XY::OnUnitMultiplierY);
   CreateProperty(g_Keyword_UnitMultiplier_Y, "Unit Multiplier Y", MM::Float, false, pAction, true);
}

/**
* Standa8SMC4XY destructor.
* If this device used as intended within the Micro-Manager system,
* Shutdown() will be always called before the destructor. But in any case
* we need to make sure that all resources are properly released even if
* Shutdown() was not called.
*/
Standa8SMC4XY::~Standa8SMC4XY()
{
   if (initialized_)
      Shutdown();
}

/**
* Obtains device name.
* Required by the MM::Device API.
*/
void Standa8SMC4XY::GetName(char* name) const
{
   // We just return the name we use for referring to this
   // device adapter.
   CDeviceUtils::CopyLimitedString(name, g_XYStageDeviceName);
}

/**
* Intializes the hardware.
* Typically we access and initialize hardware at this point.
* Device properties are typically created here as well.
* Required by the MM::Device API.
*/
int Standa8SMC4XY::Initialize()
{
   int ret;
   result_t result;

   if (initialized_)
      return DEVICE_OK;

   // set property list
   // -----------------

   // synchronize all properties
   // --------------------------
   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

   // setups
   g_loggingTracker.setCoreCallback(GetCoreCallback());

   FixupPortName(portX_);
   FixupPortName(portY_);

   LogMessage("In Standa8SMC4XY::initialize", true);
   LogMessage("using ports " + portX_ + " and " + portY_, true);

   if (deviceX_ != device_undefined || deviceY_ != device_undefined)
   {
      LogMessage("device already opened", true);
      return DEVICE_INTERNAL_INCONSISTENCY;
   }

   deviceX_ = deviceY_ = device_undefined;

   deviceX_ = open_device(portX_.c_str());

   if (deviceX_ == device_undefined)
      return DEVICE_NOT_CONNECTED;

   deviceY_ = open_device(portY_.c_str());

   if (deviceY_ == device_undefined)
      return DEVICE_NOT_CONNECTED;

   /* fill calibration structure */
   engine_settings_t dengine;
   result = get_engine_settings(deviceX_, &dengine);
   if (result != result_ok)
   {
      LogMessage("cannot get engine settings", true);
      return DEVICE_NOT_SUPPORTED;
   }
   calibrationX_.A = unitMultiplierX_;
   calibrationX_.MicrostepMode = fixedMicrostepMode(dengine.MicrostepMode);

   result = get_engine_settings(deviceY_, &dengine);
   if (result != result_ok)
   {
      LogMessage("cannot get engine settings", true);
      return DEVICE_NOT_SUPPORTED;
   }
   calibrationY_.A = unitMultiplierY_;
   calibrationY_.MicrostepMode = fixedMicrostepMode(dengine.MicrostepMode);

   originStepsX_ = originStepsY_ = 0;

   initialized_ = true;
   return DEVICE_OK;
}

/**
* Shuts down (unloads) the device.
* Ideally this method will completely unload the device and release all resources.
* Shutdown() may be called multiple times in a row.
* Required by the MM::Device API.
*/
int Standa8SMC4XY::Shutdown()
{
   result_t result;
   if (deviceX_ != device_undefined)
   {
      result = close_device(&deviceX_);
      if (result != result_ok)
         LogMessage("cannot close device");
      deviceX_ = device_undefined;
   }
   if (deviceY_ != device_undefined)
   {
      result = close_device(&deviceY_);
      if (result != result_ok)
         LogMessage("cannot close device");
      deviceY_ = device_undefined;
   }
   initialized_ = false;
   return DEVICE_OK;
}

bool Standa8SMC4XY::Busy()
{
   LogMessage("Standa8SMC4XY::Busy", true);
   if (!initialized_ || deviceX_ == device_undefined || deviceY_ == device_undefined)
      return false;

   result_t result;
   status_t statusX, statusY;
   bool busy = false;

   if (operationBusy_)
   {
      result = get_status(deviceX_, &statusX);
      if (result != result_ok)
      {
         return false;
      }
      result = get_status(deviceY_, &statusY);
      if (result != result_ok)
      {
         return false;
      }

      busy = (statusX.MvCmdSts & MVCMD_RUNNING) != 0
         || (statusY.MvCmdSts & MVCMD_RUNNING) != 0;
   }

   LogMessage(std::string("Standa8SMC4XY::Busy returned ") +
      std::string(CDeviceUtils::ConvertToString(busy)),
      true);

   return busy;
}

// Accept optional relative um offsets
int Standa8SMC4XY::UpdatePositions(double dxum, double dyum)
{
   result_t result;
   get_position_calb_t dpos;

   // Report position
   result = get_position_calb(deviceX_, &dpos, &calibrationX_);
   if (result != result_ok)
   {
      return DEVICE_UNKNOWN_POSITION;
   }
   double curx = dpos.Position - originStepsX_ * GetStepSizeXUm() + dxum;

   result = get_position_calb(deviceY_, &dpos, &calibrationY_);
   if (result != result_ok)
   {
      return DEVICE_UNKNOWN_POSITION;
   }
   double cury = dpos.Position - originStepsY_ * GetStepSizeYUm() + dyum;

   LogMessage(std::string("Standa8SMC4XY::UpdatePositions reported pos ") +
         " x=" + std::string(CDeviceUtils::ConvertToString(curx)) +
         " y=" + std::string(CDeviceUtils::ConvertToString(cury)),
         true);
   OnXYStagePositionChanged(curx, cury);

   return DEVICE_OK;
}


///////////////////////////////////////////////////////////////////////////////
// Standa8SMC4XY Action handlers
///////////////////////////////////////////////////////////////////////////////

int Standa8SMC4XY::GetLimitsUm(double& xMin, double& xMax, double& yMin, double& yMax)
{
   LogMessage("Standa8SMC4XY::GetStepLimitsUm", true);

   // getter must not change busy state of movement comamands so do not set operationBusy here
   if (!initialized_ || deviceX_ == device_undefined || deviceY_ == device_undefined)
      return DEVICE_NOT_CONNECTED;

   result_t result;
   edges_settings_calb_t dedgeX, dedgeY;

   result = get_edges_settings_calb(deviceX_, &dedgeX, &calibrationX_);
   if (result != result_ok)
   {
      return DEVICE_UNKNOWN_POSITION;
   }
   result = get_edges_settings_calb(deviceY_, &dedgeY, &calibrationY_);
   if (result != result_ok)
   {
      return DEVICE_UNKNOWN_POSITION;
   }

   xMin = dedgeX.LeftBorder;
   xMax = dedgeX.RightBorder;
   if (dedgeX.EnderFlags & ENDER_SWAP)
      std::swap(xMin, xMax);

   yMin = dedgeY.LeftBorder;
   yMax = dedgeY.RightBorder;
   if (dedgeY.EnderFlags & ENDER_SWAP)
      std::swap(yMin, yMax);

   LogMessage(std::string("Standa8SMC4XY::GetStepLimitsUm returned ") +
      " xmin=" + std::string(CDeviceUtils::ConvertToString(xMin)) +
      " xmax=" + std::string(CDeviceUtils::ConvertToString(xMax)) +
      " ymin=" + std::string(CDeviceUtils::ConvertToString(yMin)) +
      " ymax=" + std::string(CDeviceUtils::ConvertToString(yMax)),
      true);

   return DEVICE_OK;
}


int Standa8SMC4XY::Move(double vx, double vy)
{
   LogMessage(std::string("Standa8SMC4XY::Move ") +
      " vx=" + std::string(CDeviceUtils::ConvertToString(vx)) +
      " vy=" + std::string(CDeviceUtils::ConvertToString(vy)), true);

   operationBusy_ = false;
   if (!initialized_ || deviceX_ == device_undefined || deviceY_ == device_undefined)
      return DEVICE_NOT_CONNECTED;

   UpdatePositions();

   result_t result;
   if (vx > 0)
      result = command_right(deviceX_);
   else
      result = command_left(deviceX_);
   if (result != result_ok)
   {
      return DEVICE_UNKNOWN_POSITION;
   }

   if (vy > 0)
      result = command_right(deviceY_);
   else
      result = command_left(deviceY_);
   if (result != result_ok)
   {
      return DEVICE_UNKNOWN_POSITION;
   }

   return DEVICE_OK;
}


int Standa8SMC4XY::SetPositionSteps(long x, long y)
{
   LogMessage(std::string("Standa8SMC4XY::SetPositionSteps ") +
      " x=" + std::string(CDeviceUtils::ConvertToString(x)) +
      " y=" + std::string(CDeviceUtils::ConvertToString(y)), true);

   operationBusy_ = false;
   if (!initialized_ || deviceX_ == device_undefined || deviceY_ == device_undefined)
      return DEVICE_NOT_CONNECTED;

   UpdatePositions();

   result_t result;
   int newx = originStepsX_ + x;
   int newy = originStepsY_ + y;

   result = command_move(deviceX_, newx, 0);
   if (result != result_ok)
   {
      return DEVICE_UNKNOWN_POSITION;
   }

   result = command_move(deviceY_, newy, 0);
   if (result != result_ok)
   {
      return DEVICE_UNKNOWN_POSITION;
   }

   // Set future position
   OnXYStagePositionChanged(x * GetStepSizeXUm(), y * GetStepSizeYUm());

   return DEVICE_OK;
}

int Standa8SMC4XY::GetPositionSteps(long& x, long& y)
{
   LogMessage("Standa8SMC4XY::GetPositionSteps", true);

   // getter must not change busy state of movement comamands so do not set operationBusy here
   if (!initialized_ || deviceX_ == device_undefined || deviceY_ == device_undefined)
      return DEVICE_NOT_CONNECTED;

   result_t result;
   get_position_t dpos;

   result = get_position(deviceX_, &dpos);
   if (result != result_ok)
   {
      return DEVICE_UNKNOWN_POSITION;
   }
   x = nint(dpos.Position) - originStepsX_;

   result = get_position(deviceY_, &dpos);
   if (result != result_ok)
   {
      return DEVICE_UNKNOWN_POSITION;
   }
   y = nint(dpos.Position) - originStepsY_;

   LogMessage(std::string("Standa8SMC4XY::GetPositionSteps returned ") +
      " x=" + std::string(CDeviceUtils::ConvertToString(x)) +
      " y=" + std::string(CDeviceUtils::ConvertToString(y)),
      true);

   return DEVICE_OK;
}

int Standa8SMC4XY::SetPositionUm(double x, double y)
{
   LogMessage(std::string("Standa8SMC4XY::SetPositionUm ") +
      " x=" + std::string(CDeviceUtils::ConvertToString(x)) +
      " y=" + std::string(CDeviceUtils::ConvertToString(y)), true);

   operationBusy_ = false;

   if (!initialized_ || deviceX_ == device_undefined || deviceY_ == device_undefined)
      return DEVICE_NOT_CONNECTED;

   result_t result;
   float newx = static_cast<float>(originStepsX_ * GetStepSizeXUm() + x);
   float newy = static_cast<float>(originStepsY_ * GetStepSizeYUm() + y);

   UpdatePositions();

   result = command_move_calb(deviceX_, newx, &calibrationX_);
   if (result != result_ok)
   {
      return DEVICE_UNKNOWN_POSITION;
   }

   result = command_move_calb(deviceY_, newy, &calibrationY_);
   if (result != result_ok)
   {
      return DEVICE_UNKNOWN_POSITION;
   }

   // Set future position
   OnXYStagePositionChanged(x, y);

   return DEVICE_OK;
}

int Standa8SMC4XY::GetPositionUm(double& x, double& y)
{
   LogMessage("Standa8SMC4XY::GetPositionUm", true);

   // getter must not change busy state of movement comamands so do not set operationBusy here
   if (!initialized_ || deviceX_ == device_undefined || deviceY_ == device_undefined)
      return DEVICE_NOT_CONNECTED;

   result_t result;
   get_position_calb_t dpos;

   result = get_position_calb(deviceX_, &dpos, &calibrationX_);
   if (result != result_ok)
   {
      return DEVICE_UNKNOWN_POSITION;
   }
   x = dpos.Position - originStepsX_ * GetStepSizeXUm();

   result = get_position_calb(deviceY_, &dpos, &calibrationY_);
   if (result != result_ok)
   {
      return DEVICE_UNKNOWN_POSITION;
   }
   y = dpos.Position - originStepsY_ * GetStepSizeYUm();

   LogMessage(std::string("Standa8SMC4XY::GetPositionUm returned ") +
      " x=" + std::string(CDeviceUtils::ConvertToString(x)) +
      " y=" + std::string(CDeviceUtils::ConvertToString(y)),
      true);

   return DEVICE_OK;
}

int Standa8SMC4XY::SetRelativePositionUm(double dx, double dy)
{
   LogMessage(std::string("Standa8SMC4XY::SetRelativePositionUm ") +
      " dx=" + std::string(CDeviceUtils::ConvertToString(dx)) +
      " dy=" + std::string(CDeviceUtils::ConvertToString(dy)), true);

   operationBusy_ = false;
   if (!initialized_ || deviceX_ == device_undefined || deviceY_ == device_undefined)
      return DEVICE_NOT_CONNECTED;

   result_t result;

   UpdatePositions();

   result = command_movr_calb(deviceX_, static_cast<float>(dx), &calibrationX_);
   if (result != result_ok)
   {
      return DEVICE_UNKNOWN_POSITION;
   }

   result = command_movr_calb(deviceY_, static_cast<float>(dy), &calibrationY_);
   if (result != result_ok)
   {
      return DEVICE_UNKNOWN_POSITION;
   }

   // Set future position
   UpdatePositions(dx, dy);

   return DEVICE_OK;
}

int Standa8SMC4XY::SetRelativePositionSteps(long dx, long dy)
{
   LogMessage(std::string("Standa8SMC4XY::SetRelativePositionSteps ") +
      " dx=" + std::string(CDeviceUtils::ConvertToString(dx)) +
      " dy=" + std::string(CDeviceUtils::ConvertToString(dy)), true);

   operationBusy_ = false;
   if (!initialized_ || deviceX_ == device_undefined || deviceY_ == device_undefined)
      return DEVICE_NOT_CONNECTED;

   result_t result;

   UpdatePositions();

   result = command_movr(deviceX_, dx, 0);
   if (result != result_ok)
   {
      return DEVICE_UNKNOWN_POSITION;
   }

   result = command_movr(deviceY_, dy, 0);
   if (result != result_ok)
   {
      return DEVICE_UNKNOWN_POSITION;
   }

   // Set future position
   UpdatePositions(dx, dy);

   return DEVICE_OK;
}

int Standa8SMC4XY::Home()
{
   LogMessage("Standa8SMC4XY::Home", true);

   operationBusy_ = false;

   if (!initialized_ || deviceX_ == device_undefined || deviceY_ == device_undefined)
      return DEVICE_NOT_CONNECTED;

   result_t result;

   // Do not send get_position via UpdatePositions during homing so it is before homing
   UpdatePositions();

   // Issue Home commands
   result = command_home(deviceX_);
   if (result != result_ok)
   {
     return DEVICE_ERR;
   }
   result = command_home(deviceY_);
   if (result != result_ok)
   {
     return DEVICE_ERR;
   }

   // Wait while busy

   for (bool busy = true; busy; )
   {
      status_t status;
      result = get_status(deviceX_, &status);
      if (result != result_ok)
      {
         return false;
      }
      busy = (status.MvCmdSts & MVCMD_RUNNING) != 0;
      if (!busy)
      {
         result = get_status(deviceY_, &status);
         if (result != result_ok)
         {
            return false;
         }
         busy = (status.MvCmdSts & MVCMD_RUNNING) != 0;
      }
      CDeviceUtils::SleepMs(100);
      UpdatePositions();
   }

   // Zero coordinates

   result = command_zero(deviceX_);
   if (result != result_ok)
   {
     return DEVICE_ERR;
   }
   result = command_zero(deviceY_);
   if (result != result_ok)
   {
     return DEVICE_ERR;
   }

   UpdatePositions();

   return DEVICE_OK;
}

int Standa8SMC4XY::Stop()
{
   LogMessage("Standa8SMC4XY::Stop", true);

   operationBusy_ = true;
   if (!initialized_ || deviceX_ == device_undefined || deviceY_ == device_undefined)
      return DEVICE_NOT_CONNECTED;

   result_t result;

   result = command_stop(deviceX_);
   if (result != result_ok)
   {
      return DEVICE_UNKNOWN_POSITION;
   }

   result = command_stop(deviceY_);
   if (result != result_ok)
   {
      return DEVICE_UNKNOWN_POSITION;
   }

   UpdatePositions();

   return DEVICE_OK;
}

/**
* Defines position x,y (relative to current position) as the origin of our coordinate system
* Get the current (stage-native) XY position
*/
int Standa8SMC4XY::SetAdapterOriginUm(double x, double y)
{
   LogMessage(std::string("Standa8SMC4XY::SetAdapterOriginUm ") +
      " x=" + std::string(CDeviceUtils::ConvertToString(x)) +
      " y=" + std::string(CDeviceUtils::ConvertToString(y)), true);

   operationBusy_ = false;
   if (!initialized_ || deviceX_ == device_undefined || deviceY_ == device_undefined)
      return DEVICE_NOT_CONNECTED;

   // Zero coordinate system origin

   originStepsX_ = 0;
   originStepsY_ = 0;

   UpdatePositions();

   return DEVICE_OK;
}

/* From DemoCamera:
 * This sets the 0,0 position of the adapter to the current position.
 * If possible, the stage controller itself should also be set to 0,0
 * Note that this differs form the function SetAdapterOrigin(), which
 * sets the coordinate system used by the adapter
 * to values different from the system used by the stage controller
 */
int Standa8SMC4XY::SetOrigin()
{
   LogMessage("Standa8SMC4XY::SetOrigin", true);

   operationBusy_ = false;
   if (!initialized_ || deviceX_ == device_undefined || deviceY_ == device_undefined)
      return DEVICE_NOT_CONNECTED;

   return SetAdapterOriginUm(0.0, 0.0);
}

int Standa8SMC4XY::GetStepLimits(long& xMin, long& xMax, long& yMin, long& yMax)
{
   LogMessage("Standa8SMC4XY::GetStepLimits", true);

   // getter must not change busy state of movement comamands so do not set operationBusy here
   if (!initialized_ || deviceX_ == device_undefined || deviceY_ == device_undefined)
      return DEVICE_NOT_CONNECTED;

   result_t result;
   edges_settings_t dedgeX, dedgeY;

   result = get_edges_settings(deviceX_, &dedgeX);
   if (result != result_ok)
   {
      return DEVICE_UNKNOWN_POSITION;
   }
   result = get_edges_settings(deviceY_, &dedgeY);
   if (result != result_ok)
   {
      return DEVICE_UNKNOWN_POSITION;
   }

   xMin = dedgeX.LeftBorder;
   xMax = dedgeX.RightBorder;
   if (dedgeX.EnderFlags & ENDER_SWAP)
      std::swap(xMin, xMax);

   yMin = dedgeY.LeftBorder;
   yMax = dedgeY.RightBorder;
   if (dedgeY.EnderFlags & ENDER_SWAP)
      std::swap(yMin, yMax);

   LogMessage(std::string("Standa8SMC4XY::GetStepLimits returned ") +
      " xmin=" + std::string(CDeviceUtils::ConvertToString(xMin)) +
      " xmax=" + std::string(CDeviceUtils::ConvertToString(xMax)) +
      " ymin=" + std::string(CDeviceUtils::ConvertToString(yMin)) +
      " ymax=" + std::string(CDeviceUtils::ConvertToString(yMax)),
      true);

   return DEVICE_OK;
}

double Standa8SMC4XY::GetStepSizeXUm()
{
   //LogMessage("Standa8SMC4XY::GetStepSizeXUm", true);

   return calibrationX_.A;
}

double Standa8SMC4XY::GetStepSizeYUm()
{
   //LogMessage("Standa8SMC4XY::GetStepSizeYUm", true);

   return calibrationY_.A;
}

int Standa8SMC4XY::IsXYStageSequenceable(bool& isSequenceable) const
{
   isSequenceable = false;
   return DEVICE_OK;
}

bool Standa8SMC4XY::IsContinuousFocusDrive() const
{
   return false;
}


///////////////////////////////////////////////////////////////////////////////
// Actions
///////////////////////////////////////////////////////////////////////////////

int Standa8SMC4XY::OnPortX(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(portX_.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      if (initialized_)
      {
         pProp->Set(portX_.c_str());
         return ERR_PORT_CHANGE;
      }
      pProp->Get(portX_);
   }

   return DEVICE_OK;
}

int Standa8SMC4XY::OnPortY(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(portY_.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      if (initialized_)
      {
         pProp->Set(portY_.c_str());
         return ERR_PORT_CHANGE;
      }
      pProp->Get(portY_);
   }

   return DEVICE_OK;
}

int Standa8SMC4XY::OnUnitMultiplierX(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(unitMultiplierX_);
   }
   else if (eAct == MM::AfterSet)
   {
      if (initialized_)
      {
         pProp->Set(unitMultiplierX_);
         return ERR_PORT_CHANGE;
      }
      pProp->Get(unitMultiplierX_);
   }

   return DEVICE_OK;
}

int Standa8SMC4XY::OnUnitMultiplierY(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(unitMultiplierY_);
   }
   else if (eAct == MM::AfterSet)
   {
      if (initialized_)
      {
         pProp->Set(unitMultiplierY_);
         return ERR_PORT_CHANGE;
      }
      pProp->Get(unitMultiplierY_);
   }

   return DEVICE_OK;
}

// vim: ts=3 sw=3 et
