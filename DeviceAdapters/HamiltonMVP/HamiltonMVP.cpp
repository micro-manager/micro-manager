/*
 * Micro-Manager deivce adapter for Hamilton Modular Valve Positioner
 *
 * Author: Mark A. Tsuchida <mark@open-imaging.com>
 *
 * Copyright (C) 2018 Open Imaging, Inc.
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

#include "MVPCommands.h"

#include "DeviceBase.h"
#include "ModuleInterface.h"

#include <cstdint>
#include <string>
#include <vector>


const char* const DEVICE_NAME_HUB = "HamiltonMVPChain";
const char* const DEVICE_NAME_MVP_PREFIX = "HamiltonMVP-";

enum {
   ERR_UNKNOWN_VALVE_TYPE = 21001,
   ERR_INITIALIZATION_TIMED_OUT,
};


// Treat a chain of MVPs on the same serial port as a "hub"
class MVPChain : public HubBase<MVPChain>
{
   std::string port_;
   char maxAddr_;

public:
   MVPChain();
   virtual ~MVPChain();

   virtual void GetName(char* name) const;
   virtual int Initialize();
   virtual int Shutdown();
   virtual bool Busy();

   virtual int DetectInstalledDevices();

private: // Property handlers
   int OnPort(MM::PropertyBase *pProp, MM::ActionType eAct);

public: // For access by peripherals
   int SendRecv(MVPCommand& cmd);
};


class MVP : public CStateDeviceBase<MVP>
{
   const char address_;
   MVPValveType valveType_;

   enum RotationDirection {
      CLOCKWISE,
      COUNTERCLOCKWISE,
      LEAST_ANGLE,
   } rotationDirection_;

public:
   MVP(char address);
   virtual ~MVP();

   virtual void GetName(char* name) const;
   virtual int Initialize();
   virtual int Shutdown();
   virtual bool Busy();

   virtual unsigned long GetNumberOfPositions() const;

private: // Property handlers
   int OnState(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnRotationDirection(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   int SendRecv(MVPCommand& cmd);
   int GetValvePosition(int& pos);
   int SetValvePosition(int pos);

   bool ShouldRotateCCW(int curPos, int newPos);
   static std::string RotationDirectionToString(RotationDirection rd);
   static RotationDirection RotationDirectionFromString(const std::string& s);
};


MODULE_API void InitializeModuleData()
{
   RegisterDevice(DEVICE_NAME_HUB, MM::HubDevice,
         "Hamilton Modular Valve Positioner (possibly chained)");
}


MODULE_API MM::Device* CreateDevice(const char* name)
{
   if (!name)
      return 0;
   if (strcmp(name, DEVICE_NAME_HUB) == 0)
      return new MVPChain();
   if (strncmp(name, DEVICE_NAME_MVP_PREFIX, strlen(DEVICE_NAME_MVP_PREFIX)) == 0)
   {
      // Address must be 1 char
      if (strlen(name) - strlen(DEVICE_NAME_MVP_PREFIX) > 1)
         return 0;

      char address = name[strlen(name) - 1];
      return new MVP(address);
   }
   return 0;
}


MODULE_API void DeleteDevice(MM::Device* device)
{
   delete device;
}


MVPChain::MVPChain() :
   port_("Undefined"),
   maxAddr_('a')
{
   CreateStringProperty("Port", port_.c_str(), false,
         new CPropertyAction(this, &MVPChain::OnPort), true);
}


MVPChain::~MVPChain()
{
}


void
MVPChain::GetName(char* name) const
{
   CDeviceUtils::CopyLimitedString(name, DEVICE_NAME_HUB);
}


int
MVPChain::Initialize()
{
   AutoAddressingCommand autoaddr;
   int err = SendRecv(autoaddr);
   if (err != DEVICE_OK)
      return err;

   if (autoaddr.HasMaxAddr())
   {
      maxAddr_ = autoaddr.GetMaxAddr();
   }
   else
   {
      // Autoaddressing did not happen, presumably because the MVPs have
      // already been assigned addresses.
      // In this case, we test each address.
      char addr;
      for (addr = 'a'; addr < 'z'; ++addr)
      {
         FirmwareVersionRequest req(addr);
         err = SendRecv(req);
         if (err)
         {
            if (addr == 'a')
               return err;
            break;
         }
      }
      maxAddr_ = addr - 1;
   }

   LogMessage(("Last address in chain is '" + std::string(1, maxAddr_) + "'").c_str());

   return DEVICE_OK;
}


int
MVPChain::Shutdown()
{
   return DEVICE_OK;
}


bool
MVPChain::Busy()
{
   return false;
}


int
MVPChain::DetectInstalledDevices()
{
   ClearInstalledDevices();
   for (char addr = 'a'; addr <= maxAddr_; ++addr)
   {
      MM::Device* device = new MVP(addr);
      if (device)
         AddInstalledDevice(device);
   }
   return DEVICE_OK;
}


int
MVPChain::OnPort(MM::PropertyBase* pProp, MM::ActionType eAct)
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
MVPChain::SendRecv(MVPCommand& cmd)
{
   int err;

   err = SendSerialCommand(port_.c_str(), cmd.Get().c_str(), MVP_TERM);
   if (err != DEVICE_OK)
      return err;

   bool expectsMore = true;
   while (expectsMore)
   {
      std::string answer;
      err = GetSerialAnswer(port_.c_str(), MVP_TERM, answer);
      if (err != DEVICE_OK)
         return err;

      err = cmd.ParseResponse(answer, expectsMore);
      if (err != DEVICE_OK)
         return err;
   }

   return DEVICE_OK;
}


MVP::MVP(char address) :
   address_(address),
   valveType_(ValveTypeUnknown),
   rotationDirection_(LEAST_ANGLE)
{
}


MVP::~MVP()
{
}


void
MVP::GetName(char* name) const
{
   std::string address(1, address_);
   CDeviceUtils::CopyLimitedString(name,
         (DEVICE_NAME_MVP_PREFIX + address).c_str());
}


int
MVP::Initialize()
{
   int err;

   FirmwareVersionRequest fvReq(address_);
   err = SendRecv(fvReq);
   if (err != DEVICE_OK)
      return err;
   err = CreateStringProperty("FirmwareVersion",
         fvReq.GetFirmwareVersion().c_str(), true);
   if (err != DEVICE_OK)
      return err;

   InstrumentErrorRequest errReq(address_);
   err = SendRecv(errReq);
   if (err != DEVICE_OK)
      return err;
   if (errReq.IsValveNotInitialized())
   {
      InitializationCommand initCmd(address_);
      err = SendRecv(initCmd);
      if (err != DEVICE_OK)
         return err;

      MM::MMTime deadline = GetCurrentMMTime() + MM::MMTime(15 * 1000 * 1000);
      bool busy = true;
      while (busy && GetCurrentMMTime() < deadline)
      {
         busy = Busy();
         if (!busy)
            break;
         CDeviceUtils::SleepMs(200);
      }
      if (busy)
         return ERR_INITIALIZATION_TIMED_OUT;
   }

   ValveTypeRequest typeReq(address_);
   err = SendRecv(typeReq);
   if (err != DEVICE_OK)
      return err;
   valveType_ = typeReq.GetValveType();
   if (valveType_ == ValveTypeUnknown)
      return ERR_UNKNOWN_VALVE_TYPE;
   err = CreateStringProperty("ValveType",
         GetValveTypeName(typeReq.GetValveType()).c_str(), true);
   if (err != DEVICE_OK)
      return err;

   int pos;
   err = GetValvePosition(pos);
   if (err != DEVICE_OK)
      return err;
   err = CreateIntegerProperty(MM::g_Keyword_State, pos, false,
         new CPropertyAction(this, &MVP::OnState));
   if (err != DEVICE_OK)
      return err;
   for (int i = 0; i < GetNumberOfPositions(); ++i)
   {
      char s[16];
      snprintf(s, 15, "%d", i);
      AddAllowedValue(MM::g_Keyword_State, s);
   }

   err = CreateStringProperty(MM::g_Keyword_Label, "Undefined", false,
         new CPropertyAction(this, &CStateBase::OnLabel));
   if (err != DEVICE_OK)
      return err;
   for (int i = 0; i < GetNumberOfPositions(); ++i)
   {
      char label[32];
      snprintf(label, 31, "Position-%d", i);
      SetPositionLabel(i, label);
   }

   ValveSpeedRequest speedReq(address_);
   err = SendRecv(speedReq);
   if (err != DEVICE_OK)
      return err;
   err = CreateIntegerProperty("ValveSpeedHz",
         speedReq.GetSpeedHz(), true);
   if (err != DEVICE_OK)
      return err;

   err = CreateStringProperty("RotationDirection",
         RotationDirectionToString(rotationDirection_).c_str(), false,
         new CPropertyAction(this, &MVP::OnRotationDirection));
   if (err != DEVICE_OK)
      return err;
   AddAllowedValue("RotationDirection", "Clockwise");
   AddAllowedValue("RotationDirection", "Counterclockwise");
   AddAllowedValue("RotationDirection", "Least rotation angle");

   return DEVICE_OK;
}


int
MVP::Shutdown()
{
   return DEVICE_OK;
}


bool
MVP::Busy()
{
   MovementFinishedReuqest req(address_);
   int err = SendRecv(req);
   if (err != DEVICE_OK)
      return false;
   return !req.IsMovementFinished();
}


unsigned long
MVP::GetNumberOfPositions() const
{
   return GetValveNumberOfPositions(valveType_);
}


int
MVP::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      int pos;
      int err = GetValvePosition(pos);
      if (err != DEVICE_OK)
         return err;
      pProp->Set(long(pos));
   }
   else if (eAct == MM::AfterSet)
   {
      long v;
      pProp->Get(v);
      int err = SetValvePosition(int(v));
      if (err != DEVICE_OK)
         return err;
   }
   return DEVICE_OK;
}


int
MVP::OnRotationDirection(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(RotationDirectionToString(rotationDirection_).c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      std::string v;
      pProp->Get(v);
      rotationDirection_ = RotationDirectionFromString(v);
   }
   return DEVICE_OK;
}


int
MVP::SendRecv(MVPCommand& cmd)
{
   return static_cast<MVPChain*>(GetParentHub())->SendRecv(cmd);
}


int
MVP::GetValvePosition(int& pos)
{
   ValvePositionRequest req(address_);
   int err = SendRecv(req);
   if (err != DEVICE_OK)
      return err;
   pos = req.GetPosition();
   return DEVICE_OK;
}


int
MVP::SetValvePosition(int pos)
{
   int err;

   int curPos;
   err = GetValvePosition(curPos);
   if (err != DEVICE_OK)
      return err;

   ValvePositionCommand cmd(address_, ShouldRotateCCW(curPos, pos), pos);
   err = SendRecv(cmd);
   if (err != DEVICE_OK)
      return err;
   return DEVICE_OK;
}


bool
MVP::ShouldRotateCCW(int curPos, int newPos)
{
   switch (rotationDirection_)
   {
      case CLOCKWISE:
         return false;
      case COUNTERCLOCKWISE:
         return true;
      case LEAST_ANGLE:
      default:
         {
            int cwAngle = GetValveRotationAngle(valveType_, false, curPos, newPos);
            int ccwAngle = GetValveRotationAngle(valveType_, true, curPos, newPos);
            return (ccwAngle < cwAngle);
         }
   }
}


std::string
MVP::RotationDirectionToString(RotationDirection rd)
{
   switch (rd)
   {
      case CLOCKWISE:
         return "Clockwise";
      case COUNTERCLOCKWISE:
         return "Counterclockwise";
      case LEAST_ANGLE:
      default:
         return "Least rotation angle";
   }
}


MVP::RotationDirection
MVP::RotationDirectionFromString(const std::string& s)
{
   if (s == "Clockwise")
      return CLOCKWISE;
   if (s == "Counterclockwise")
      return COUNTERCLOCKWISE;
   return LEAST_ANGLE;
}
