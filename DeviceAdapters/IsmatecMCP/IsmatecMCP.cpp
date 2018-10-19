/*
 * Micro-Manager deivce adapter for Hamilton Modular Valve Positioner
 *
 * Author: Mark A. Tsuchida <mark@open-imaging.com>
 *
 * Copyright (C) 2018 Applied Materials, Inc.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its
 * contributors may be used to endorse or promote products derived from this
 * software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

#include "MCPCommands.h"

#include "DeviceBase.h"
#include "ModuleInterface.h"

#include <string>


const char* const DEVICE_NAME_HUB = "IsmatecMCPChain";
const char* const DEVICE_NAME_MCP_PREFIX = "IsmatecMCP-";

const int MIN_MCP_ADDR = 1;
const int MAX_MCP_ADDR = 8;

enum {
   ERR_INVALID_COMMAND = 21001,
   ERR_VALUE_OUT_OF_RANGE,
   ERR_MODE_G_OUT_OF_RANGE,
};


// Treat a chain of pumps on the same serial port as a "hub"
class MCPChain : public HubBase<MCPChain>
{
   std::string port_;

public:
   MCPChain();
   virtual ~MCPChain();

   virtual void GetName(char* name) const;
   virtual int Initialize();
   virtual int Shutdown();
   virtual bool Busy();

   virtual int DetectInstalledDevices();

private: // Property handlers
   int OnPort(MM::PropertyBase *pProp, MM::ActionType eAct);

public: // For access by peripherals
   int SendRecv(MCPCommand& cmd);
};


class MCP : public CGenericBase<MCP>
{
   const int address_;

   int nRollers_;

   // The number of digits after decimal point as reported by the '[' command.
   // Read only after tubing ID change, because '[' sometimes gives an
   // incorrect response when the calibrated flow rate is set to a value with
   // one fewer digit than the default flow rate.
   int nFractionalDigits_;

   // Those few states that cannot be queried:
   bool ccw_;
   bool manualControl_;
   ModeCommand::Mode mode_;

   // Cached values (invalid iff < 0)
   double cachedTubingInnerDiameterMm_;
   double cachedSpeedRpm_;
   double cachedDefaultFlowRateMlPerMin_;
   double cachedCalibratedFlowRateMlPerMin_;
   double cachedDispensingTimeSeconds_;
   int cachedDispensingRollerSteps_;
   int cachedRollerBackSteps_;
   double cachedPauseTimeSeconds_;
   int cachedNumberOfCycles_;

public:
   MCP(int address);
   virtual ~MCP();

   virtual void GetName(char* name) const;
   virtual int Initialize();
   virtual int Shutdown();
   virtual bool Busy();

private: // Property handlers
   int OnNumberOfRollers(MM::PropertyBase* pProp, MM::ActionType eAct); // Pre-init
   int OnStartStop(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnRotationDirection(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnManualControl(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnMode(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnTubingInnerDiameter(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSpeedRPM(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnDefaultFlowRate(MM::PropertyBase* pProp, MM::ActionType eAct); // Read-only
   int OnCalibratedFlowRate(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnDispensingTime(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnDispensingRollerSteps(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnDispensingVolume(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnRollerBackSteps(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnPauseTime(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnNumberOfCycles(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   int SendRecv(MCPCommand& cmd);
};


MODULE_API void InitializeModuleData()
{
   RegisterDevice(DEVICE_NAME_HUB, MM::HubDevice,
         "Ismatec MCP peristaltic pump (possibly chained)");
}


MODULE_API MM::Device* CreateDevice(const char* name)
{
   if (!name)
      return 0;
   if (strcmp(name, DEVICE_NAME_HUB) == 0)
      return new MCPChain();
   if (strncmp(name, DEVICE_NAME_MCP_PREFIX, strlen(DEVICE_NAME_MCP_PREFIX)) == 0)
   {
      // Address must be 1 char
      if (strlen(name) - strlen(DEVICE_NAME_MCP_PREFIX) > 1)
         return 0;

      char addrCh = name[strlen(name) - 1];
      int addr = addrCh - '0';
      if (addr < MIN_MCP_ADDR || addr > MAX_MCP_ADDR)
         return 0;
      return new MCP(addr);
   }
   return 0;
}


MODULE_API void DeleteDevice(MM::Device* device)
{
   delete device;
}


MCPChain::MCPChain() :
   port_("Undefined")
{
   CreateStringProperty("Port", port_.c_str(), false,
         new CPropertyAction(this, &MCPChain::OnPort), true);
}


MCPChain::~MCPChain()
{
}


void
MCPChain::GetName(char* name) const
{
   CDeviceUtils::CopyLimitedString(name, DEVICE_NAME_HUB);
}


int
MCPChain::Initialize()
{
   int err = PurgeComPort(port_.c_str());
   if (err != DEVICE_OK)
      return err;

   return DEVICE_OK;
}


int
MCPChain::Shutdown()
{
   return DEVICE_OK;
}


bool
MCPChain::Busy()
{
   return false;
}


int
MCPChain::DetectInstalledDevices()
{
   ClearInstalledDevices();
   for (int addr = MIN_MCP_ADDR; addr <= MAX_MCP_ADDR; ++addr)
   {
      PumpTypeFirmwareVersionHeadIdQuery q(addr);
      int err = SendRecv(q);
      if (err != DEVICE_OK)
         continue;

      MM::Device* device = new MCP(addr);
      if (device)
         AddInstalledDevice(device);
   }
   return DEVICE_OK;
}


int
MCPChain::OnPort(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(port_.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(port_);
   }
   return DEVICE_OK;
}


int
MCPChain::SendRecv(MCPCommand& cmd)
{
   int err;

   err = SendSerialCommand(port_.c_str(), cmd.Get().c_str(), MCP_CMD_TERM);
   if (err != DEVICE_OK)
      return err;

   if (cmd.ExpectsSingleCharResponse())
   {
      // Read a single character without terminator, with timeout
      MM::MMTime deadline = GetCurrentMMTime() + MM::MMTime(500 * 1000);
      while (GetCurrentMMTime() < deadline)
      {
         unsigned char buf[2];
         unsigned long charsRead;
         err = ReadFromComPort(port_.c_str(), buf, 1, charsRead);
         if (err != DEVICE_OK)
            return err;
         if (charsRead == 1)
            return cmd.ParseResponse(buf[0]);
         CDeviceUtils::SleepMs(10);
      }
   }
   else
   {
      std::string answer;
      err = GetSerialAnswer(port_.c_str(), MCP_RESP_TERM, answer);
      if (err != DEVICE_OK)
      {
         // A single '#' without terminator is returned if there is a syntax
         // error. Check for it.
         unsigned char buf[2];
         unsigned long charsRead;
         int err2 = ReadFromComPort(port_.c_str(), buf, 1, charsRead);
         if (err2 == DEVICE_OK && charsRead == 1 && buf[0] == '#')
            return ERR_INVALID_COMMAND;

         return err;
      }

      // Some responses contain leading spaces. Strip of all leading and
      // trailing spaces.
      const std::string space(" ");
      std::string::size_type start = answer.find_first_not_of(space);
      std::string::size_type stop = answer.find_last_not_of(space);
      if (start <= stop)
         answer = answer.substr(start, stop + 1 - start);
      else
         answer = "";

      err = cmd.ParseResponse(answer);
      if (err != DEVICE_OK)
         return err;
   }

   return DEVICE_OK;
}


MCP::MCP(int address) :
   address_(address),
   nRollers_(3),
   nFractionalDigits_(0),
   ccw_(false),
   manualControl_(false),
   mode_(ModeCommand::ModePumpRPM),
   cachedTubingInnerDiameterMm_(-1.0),
   cachedSpeedRpm_(-1.0),
   cachedDefaultFlowRateMlPerMin_(-1.0),
   cachedCalibratedFlowRateMlPerMin_(-1.0),
   cachedDispensingTimeSeconds_(-1.0),
   cachedDispensingRollerSteps_(-1),
   cachedRollerBackSteps_(-1),
   cachedPauseTimeSeconds_(-1.0),
   cachedNumberOfCycles_(-1)
{
   // In theory, we could build in knowledge of number of rollers (from head
   // ID). But this is more straightforward.
   CreateIntegerProperty("NumberOfRollers", nRollers_, false,
         new CPropertyAction(this, &MCP::OnNumberOfRollers), true);
   AddAllowedValue("NumberOfRollers", "2");
   AddAllowedValue("NumberOfRollers", "3");
   AddAllowedValue("NumberOfRollers", "4");
   AddAllowedValue("NumberOfRollers", "6");
   AddAllowedValue("NumberOfRollers", "8");
   AddAllowedValue("NumberOfRollers", "12");
}


MCP::~MCP()
{
}


void
MCP::GetName(char* name) const
{
   std::string address(1, char('0' + address_));
   CDeviceUtils::CopyLimitedString(name,
         (DEVICE_NAME_MCP_PREFIX + address).c_str());
}


int
MCP::Initialize()
{
   int err;

   ResetOverloadCommand resetCmd(address_);
   err = SendRecv(resetCmd);
   if (err != DEVICE_OK)
      return err;

   // Make sure pump is stopped
   StartStopCommand ssCmd(address_, false);
   err = SendRecv(ssCmd);
   if (err != DEVICE_OK)
      return err;

   // We cannot query the rotation direction, so force sync
   RotationDirectionCommand rdCmd(address_, ccw_);
   err = SendRecv(rdCmd);
   if (err != DEVICE_OK)
      return err;

   // We cannot query the manual control mode, so force sync
   ManualControlCommand mcCmd(address_, manualControl_);
   err = SendRecv(mcCmd);
   if (err != DEVICE_OK)
      return err;

   // We cannot query the mode, so force sync
   ModeCommand modeCmd(address_, mode_);
   err = SendRecv(modeCmd);
   if (err != DEVICE_OK)
      return err;

   FractionalDigitsQuery fdQ(address_);
   err = SendRecv(fdQ);
   if (err != DEVICE_OK)
      return err;
   nFractionalDigits_ = fdQ.GetFractionalDigits();

   PumpTypeFirmwareVersionHeadIdQuery typeQ(address_);
   err = SendRecv(typeQ);
   if (err != DEVICE_OK)
      return err;
   err = CreateStringProperty("PumpModel", typeQ.GetPumpType().c_str(), true);
   if (err != DEVICE_OK)
      return err;

   FirmwareVersionQuery fvQ(address_);
   err = SendRecv(fvQ);
   if (err != DEVICE_OK)
      return err;
   err = CreateStringProperty("FirmwareVersion", fvQ.GetFirmwareVersion().c_str(), true);
   if (err != DEVICE_OK)
      return err;

   HeadIdQuery idQ(address_);
   err = SendRecv(idQ);
   if (err != DEVICE_OK)
      return err;
   err = CreateStringProperty("PumpHeadID", idQ.GetHeadId().c_str(), true);
   if (err != DEVICE_OK)
      return err;

   err = CreateStringProperty("Pumping", "Off", false,
         new CPropertyAction(this, &MCP::OnStartStop));
   if (err != DEVICE_OK)
      return err;
   AddAllowedValue("Pumping", "Off");
   AddAllowedValue("Pumping", "On");

   err = CreateStringProperty("Direction", ccw_ ? "Counterclockwise" : "Clockwise",
         false, new CPropertyAction(this, &MCP::OnRotationDirection));
   if (err != DEVICE_OK)
      return err;
   AddAllowedValue("Direction", "Clockwise");
   AddAllowedValue("Direction", "Counterclockwise");

   err = CreateStringProperty("ManualControl", manualControl_ ? "Enabled" : "Disabled",
         false, new CPropertyAction(this, &MCP::OnManualControl));
   if (err != DEVICE_OK)
      return err;
   AddAllowedValue("ManualControl", "Disabled");
   AddAllowedValue("ManualControl", "Enabled");

   err = CreateStringProperty("Mode", ModeCommand::ModeToString(mode_).c_str(),
         false, new CPropertyAction(this, &MCP::OnMode));
   if (err != DEVICE_OK)
      return err;
   AddAllowedValue("Mode", ModeCommand::ModeToString(
            ModeCommand::ModePumpRPM).c_str());
   AddAllowedValue("Mode", ModeCommand::ModeToString(
            ModeCommand::ModePumpFlowRate).c_str());
   AddAllowedValue("Mode", ModeCommand::ModeToString(
            ModeCommand::ModeDispenseTime).c_str());
   AddAllowedValue("Mode", ModeCommand::ModeToString(
            ModeCommand::ModeDispenseVolume).c_str());
   AddAllowedValue("Mode", ModeCommand::ModeToString(
            ModeCommand::ModeDispenseTimePlusPause).c_str());
   AddAllowedValue("Mode", ModeCommand::ModeToString(
            ModeCommand::ModeDispenseVolumePlusPause).c_str());
   AddAllowedValue("Mode", ModeCommand::ModeToString(
            ModeCommand::ModeDispenseVolumeInTime).c_str());

   err = CreateFloatProperty("TubingInnerDiameter_mm", 0.0, false,
         new CPropertyAction(this, &MCP::OnTubingInnerDiameter));
   if (err != DEVICE_OK)
      return err;
   err = CreateFloatProperty("Speed_rpm", 0.0, false,
         new CPropertyAction(this, &MCP::OnSpeedRPM));
   if (err != DEVICE_OK)
      return err;
   err = CreateFloatProperty("FlowRateUncalibrated_mLperMin_at240rpm", 0.0, true,
         new CPropertyAction(this, &MCP::OnDefaultFlowRate));
   if (err != DEVICE_OK)
      return err;
   err = CreateFloatProperty("FlowRateCalibrated_mLperMin_at240rpm", 0.0, false,
         new CPropertyAction(this, &MCP::OnCalibratedFlowRate));
   if (err != DEVICE_OK)
      return err;
   err = CreateFloatProperty("DispensingTime_s", 0.0, false,
         new CPropertyAction(this, &MCP::OnDispensingTime));
   if (err != DEVICE_OK)
      return err;
   err = CreateIntegerProperty("DispensingRollerSteps", 0, false,
         new CPropertyAction(this, &MCP::OnDispensingRollerSteps));
   if (err != DEVICE_OK)
      return err;
   err = CreateFloatProperty("DispensingVolume_mL", 0.0, false,
         new CPropertyAction(this, &MCP::OnDispensingVolume));
   if (err != DEVICE_OK)
      return err;
   err = CreateIntegerProperty("RollerBackSteps", 0, false,
         new CPropertyAction(this, &MCP::OnRollerBackSteps));
   if (err != DEVICE_OK)
      return err;
   err = CreateFloatProperty("PauseTime_s", 0.0, false,
         new CPropertyAction(this, &MCP::OnPauseTime));
   if (err != DEVICE_OK)
      return err;
   err = CreateIntegerProperty("NumberOfCycles", 0, false,
         new CPropertyAction(this, &MCP::OnNumberOfCycles));
   if (err != DEVICE_OK)
      return err;

   return DEVICE_OK;
}


int
MCP::Shutdown()
{
   return DEVICE_OK;
}


bool
MCP::Busy()
{
   // We cannot say that we are busy when the pump is active. In Micro-Manager
   // semantics, we are never busy.
   return false;
}


int
MCP::OnNumberOfRollers(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(long(nRollers_));
   }
   else if (eAct == MM::AfterSet)
   {
      long v;
      pProp->Get(v);
      nRollers_ = v;
   }
   return DEVICE_OK;
}


int
MCP::OnStartStop(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      PumpActiveQuery q(address_);
      int err = SendRecv(q);
      if (err != DEVICE_OK)
         return err;
      pProp->Set(q.IsPumpActive() ? "On" : "Off");
   }
   else if (eAct == MM::AfterSet)
   {
      std::string v;
      pProp->Get(v);
      bool on = (v == "On");
      StartStopCommand c(address_, on);
      int err = SendRecv(c);
      if (err != DEVICE_OK)
         return err;
      if (c.IsFailedToStartInModeG())
      {
         pProp->Set("Off");
         return ERR_MODE_G_OUT_OF_RANGE;
      }
   }
   return DEVICE_OK;
}


int
MCP::OnRotationDirection(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(ccw_ ? "Counterclockwise" : "Clockwise");
   }
   else if (eAct == MM::AfterSet)
   {
      std::string v;
      pProp->Get(v);
      bool ccw = (v == "Counterclockwise");
      RotationDirectionCommand c(address_, ccw);
      int err = SendRecv(c);
      if (err != DEVICE_OK)
         return err;
      ccw_ = ccw;
   }
   return DEVICE_OK;
}


int
MCP::OnManualControl(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(manualControl_ ? "Enabled" : "Disabled");
   }
   else if (eAct == MM::AfterSet)
   {
      std::string v;
      pProp->Get(v);
      bool enabled = (v == "Enabled");
      ManualControlCommand c(address_, enabled);
      int err = SendRecv(c);
      if (err != DEVICE_OK)
         return err;
      manualControl_ = enabled;

      if (!manualControl_)
      {
         // Invalidate all cached data on disabling manual control
         cachedTubingInnerDiameterMm_ = -1.0;
         cachedSpeedRpm_ = -1.0;
         cachedDefaultFlowRateMlPerMin_ = -1.0;
         cachedCalibratedFlowRateMlPerMin_ = -1.0;
         cachedDispensingTimeSeconds_ = -1.0;
         cachedDispensingRollerSteps_ = -1;
         cachedRollerBackSteps_ = -1;
         cachedPauseTimeSeconds_ = -1.0;
         cachedNumberOfCycles_ = -1;
      }
   }
   return DEVICE_OK;
}


int
MCP::OnMode(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(ModeCommand::ModeToString(mode_).c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      std::string v;
      pProp->Get(v);
      ModeCommand::Mode mode = ModeCommand::ModeFromString(v);
      ModeCommand c(address_, mode);
      int err = SendRecv(c);
      if (err != DEVICE_OK)
         return err;
      mode_ = mode;
   }
   return DEVICE_OK;
}


int
MCP::OnTubingInnerDiameter(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      if (manualControl_ || cachedTubingInnerDiameterMm_ < 0.0)
      {
         TubingInnerDiameterQuery q(address_);
         int err = SendRecv(q);
         if (err != DEVICE_OK)
            return err;
         cachedTubingInnerDiameterMm_ = q.GetInnerDiameterMm();
      }
      pProp->Set(cachedTubingInnerDiameterMm_);
   }
   else if (eAct == MM::AfterSet)
   {
      double v;
      pProp->Get(v);
      if (v <= 0.0 || v > 99.99)
         return ERR_VALUE_OUT_OF_RANGE;
      TubingInnerDiameterCommand c(address_, v);
      int err = SendRecv(c);
      if (err != DEVICE_OK)
         return err;

      FractionalDigitsQuery fdq(address_);
      err = SendRecv(fdq);
      if (err != DEVICE_OK)
         return err;
      nFractionalDigits_ = fdq.GetFractionalDigits();

      cachedTubingInnerDiameterMm_ = -1.0; // Invalidate
      cachedDefaultFlowRateMlPerMin_ = -1.0;
      cachedCalibratedFlowRateMlPerMin_ = -1.0;
   }
   return DEVICE_OK;
}


int
MCP::OnSpeedRPM(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      if (manualControl_ || cachedSpeedRpm_ < 0.0)
      {
         SpeedQuery q(address_);
         int err = SendRecv(q);
         if (err != DEVICE_OK)
            return err;
         cachedSpeedRpm_ = q.GetSpeedRpm();
      }
      pProp->Set(cachedSpeedRpm_);
   }
   else if (eAct == MM::AfterSet)
   {
      double v;
      pProp->Get(v);
      if (v <= 0.0 || v > 9999.9)
         return ERR_VALUE_OUT_OF_RANGE;
      SpeedCommand c(address_, v);
      int err = SendRecv(c);
      if (err != DEVICE_OK)
         return err;

      cachedSpeedRpm_ = -1.0; // Invalidate
   }
   return DEVICE_OK;
}


int
MCP::OnDefaultFlowRate(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      if (manualControl_ || cachedDefaultFlowRateMlPerMin_ < 0.0)
      {
         DefaultFlowRateQuery q(address_);
         int err = SendRecv(q);
         if (err != DEVICE_OK)
            return err;
         cachedDefaultFlowRateMlPerMin_ = q.GetFlowRateMlPerMin();
      }
      pProp->Set(cachedDefaultFlowRateMlPerMin_);
   }
   return DEVICE_OK;
}


int
MCP::OnCalibratedFlowRate(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      if (manualControl_ || cachedCalibratedFlowRateMlPerMin_ < 0.0)
      {
         CalibratedFlowRateQuery q(address_);
         int err = SendRecv(q);
         if (err != DEVICE_OK)
            return err;
         cachedCalibratedFlowRateMlPerMin_ = q.GetFlowRateMlPerMin();
      }
      pProp->Set(cachedCalibratedFlowRateMlPerMin_);
   }
   else if (eAct == MM::AfterSet)
   {
      double maxValue = 9999.0 / pow(10.0, nFractionalDigits_);

      double v;
      pProp->Get(v);
      if (v <= 0.0 || v > maxValue)
         return ERR_VALUE_OUT_OF_RANGE;
      CalibratedFlowRateCommand c(address_, v, nFractionalDigits_);
      int err = SendRecv(c);
      if (err != DEVICE_OK)
         return err;

      cachedCalibratedFlowRateMlPerMin_ = -1.0; // Invalidate
   }
   return DEVICE_OK;
}


int
MCP::OnDispensingTime(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      if (manualControl_ || cachedDispensingTimeSeconds_ < 0.0)
      {
         DispensingTimeQuery q(address_);
         int err = SendRecv(q);
         if (err != DEVICE_OK)
            return err;
         cachedDispensingTimeSeconds_ = q.GetTimeSeconds();
      }
      pProp->Set(cachedDispensingTimeSeconds_);
   }
   else if (eAct == MM::AfterSet)
   {
      double v;
      pProp->Get(v);
      if (v < 0.0 || v > 999 * 3600 * 10.0)
         return ERR_VALUE_OUT_OF_RANGE;
      DispensingTimeCommand c(address_, v);
      int err = SendRecv(c);
      if (err != DEVICE_OK)
         return err;

      cachedDispensingTimeSeconds_ = -1.0; // Invalidate
   }
   return DEVICE_OK;
}


int
MCP::OnDispensingRollerSteps(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      if (manualControl_ || cachedDispensingRollerSteps_ < 0)
      {
         DispensingRollerStepsQuery q(address_);
         int err = SendRecv(q);
         if (err != DEVICE_OK)
            return err;
         cachedDispensingRollerSteps_ = q.GetRollerSteps();
      }
      pProp->Set(long(cachedDispensingRollerSteps_));
   }
   else if (eAct == MM::AfterSet)
   {
      long v;
      pProp->Get(v);
      if (v < 0 || v > 65535)
         return ERR_VALUE_OUT_OF_RANGE;
      DispensingRollerStepsCommand c(address_, v);
      int err = SendRecv(c);
      if (err != DEVICE_OK)
         return err;

      cachedDispensingRollerSteps_ = -1; // Invalidate
   }
   return DEVICE_OK;
}


int
MCP::OnDispensingVolume(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      if (manualControl_ || cachedCalibratedFlowRateMlPerMin_ < 0.0)
      {
         CalibratedFlowRateQuery q(address_);
         int err = SendRecv(q);
         if (err != DEVICE_OK)
            return err;
         cachedCalibratedFlowRateMlPerMin_ = q.GetFlowRateMlPerMin();
      }
      if (manualControl_ || cachedDispensingRollerSteps_ < 0)
      {
         DispensingRollerStepsQuery q(address_);
         int err = SendRecv(q);
         if (err != DEVICE_OK)
            return err;
         cachedDispensingRollerSteps_ = q.GetRollerSteps();
      }

      // rpm = roller_steps / n_rollers
      // volume = flow@240rpm * (rpm / 240)
      double dispensingVolumeMl =
         cachedCalibratedFlowRateMlPerMin_ * cachedDispensingRollerSteps_ /
         (240.0 * nRollers_);

      pProp->Set(dispensingVolumeMl);
   }
   else if (eAct == MM::AfterSet)
   {
      // Unlike calibrated flow rate, the number of fractional digits for
      // dispensing volume actually does follow what the '[' command
      // currently reports.
      FractionalDigitsQuery q(address_);
      int err = SendRecv(q);
      if (err != DEVICE_OK)
         return err;
      double maxValue = 99999.0 / pow(10.0, q.GetFractionalDigits());

      double v;
      pProp->Get(v);
      if (v < 0.0 || v > maxValue)
         return ERR_VALUE_OUT_OF_RANGE;
      DispensingVolumeCommand c(address_, v, q.GetFractionalDigits());
      err = SendRecv(c);
      if (err != DEVICE_OK)
         return err;

      cachedDispensingRollerSteps_ = -1; // Invalidate
   }
   return DEVICE_OK;
}


int
MCP::OnRollerBackSteps(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      if (manualControl_ || cachedRollerBackSteps_ < 0)
      {
         RollerBackStepsQuery q(address_);
         int err = SendRecv(q);
         if (err != DEVICE_OK)
            return err;
         cachedRollerBackSteps_ = q.GetRollerSteps();
      }
      pProp->Set(long(cachedRollerBackSteps_));
   }
   else if (eAct == MM::AfterSet)
   {
      long v;
      pProp->Get(v);
      if (v < 0 || v > 100)
         return ERR_VALUE_OUT_OF_RANGE;
      RollerBackStepsCommand c(address_, v);
      int err = SendRecv(c);
      if (err != DEVICE_OK)
         return err;

      cachedRollerBackSteps_ = -1; // Invalidate
   }
   return DEVICE_OK;
}


int
MCP::OnPauseTime(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      if (manualControl_ || cachedPauseTimeSeconds_ < 0.0)
      {
         PauseTimeQuery q(address_);
         int err = SendRecv(q);
         if (err != DEVICE_OK)
            return err;
         cachedPauseTimeSeconds_ = q.GetTimeSeconds();
      }
      pProp->Set(cachedPauseTimeSeconds_);
   }
   else if (eAct == MM::AfterSet)
   {
      double v;
      pProp->Get(v);
      if (v < 0.0 || v > 999 * 3600 * 10.0)
         return ERR_VALUE_OUT_OF_RANGE;
      PauseTimeCommand c(address_, v);
      int err = SendRecv(c);
      if (err != DEVICE_OK)
         return err;

      cachedPauseTimeSeconds_ = -1; // Invalidate
   }
   return DEVICE_OK;
}


int
MCP::OnNumberOfCycles(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      if (manualControl_ || cachedNumberOfCycles_ < 0)
      {
         NumberOfCyclesQuery q(address_);
         int err = SendRecv(q);
         if (err != DEVICE_OK)
            return err;
         cachedNumberOfCycles_ = q.GetNumberOfCycles();
      }
      pProp->Set(long(cachedNumberOfCycles_));
   }
   else if (eAct == MM::AfterSet)
   {
      long v;
      pProp->Get(v);
      if (v < 0 || v > 9999)
         return ERR_VALUE_OUT_OF_RANGE;
      NumberOfCyclesCommand c(address_, v);
      int err = SendRecv(c);
      if (err != DEVICE_OK)
         return err;

      cachedNumberOfCycles_ = -1; // Invalidate
   }
   return DEVICE_OK;
}


int
MCP::SendRecv(MCPCommand& cmd)
{
   return static_cast<MCPChain*>(GetParentHub())->SendRecv(cmd);
}
