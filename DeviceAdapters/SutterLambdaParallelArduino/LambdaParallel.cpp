/*
 * Micro-Manager device adapter for Sutter Lambda controlled by Arduino through
 * parallel port, supporting hardware-triggered sequencing
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

#include "DeviceBase.h"
#include "ModuleInterface.h"

#include <string>
#include <vector>


const int NUM_POSITIONS = 10;
const int NUM_SPEEDS = 8;
const int MAX_SEQUENCE_LENGTH = 16;
const char* const DEVICE_NAME_WHEEL_A = "ArduinoWheelA";
const char* const TERMINATOR = "\r";

enum {
   ERR_INVALID_RESPONSE = 30001,
   ERR_BUSY_TIMEOUT,
   ERR_DEVICE_ERROR,
};


class LambdaParallel : public CStateDeviceBase<LambdaParallel>
{
   std::string port_;
   bool useSequencing_;

public:
   LambdaParallel();
   virtual ~LambdaParallel();

   virtual void GetName(char* name) const;
   virtual int Initialize();
   virtual int Shutdown();
   virtual bool Busy();

   virtual unsigned long GetNumberOfPositions() const { return NUM_POSITIONS; }

private: // Property handlers
   int OnPort(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnState(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSpeed(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnUseSequencing(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   int SendRecv(const std::string& cmd, std::string& resp);
   int GoOnline(bool online);
   int GetBusy(bool& busy);
   int WaitForQuiescent();
   int SetSequencing(bool start);
   int GetWheelPosition(int& position);
   int SetWheelPosition(int position);
   int GetWheelSpeed(int& speed);
   int SetWheelSpeed(int speed);
   int LoadPositionSequence(std::vector<int> seq);
};


MODULE_API void InitializeModuleData()
{
   RegisterDevice(DEVICE_NAME_WHEEL_A, MM::StateDevice, "Lambda Wheel A");
}


MODULE_API MM::Device* CreateDevice(const char* name)
{
   if (!name)
      return 0;
   if (strcmp(name, DEVICE_NAME_WHEEL_A) == 0)
      return new LambdaParallel();
   return 0;
}


MODULE_API void DeleteDevice(MM::Device* device)
{
   delete device;
}


LambdaParallel::LambdaParallel() :
   port_("Undefined"),
   useSequencing_(false)
{
   SetErrorText(ERR_INVALID_RESPONSE, "Invalid response from device");
   SetErrorText(ERR_BUSY_TIMEOUT, "Device busy for too long");
   SetErrorText(ERR_DEVICE_ERROR, "The device indicated an error");

   CreateStringProperty("Port", port_.c_str(), false,
         new CPropertyAction(this, &LambdaParallel::OnPort), true);
}


LambdaParallel::~LambdaParallel()
{
}


void
LambdaParallel::GetName(char* name) const
{
   CDeviceUtils::CopyLimitedString(name, DEVICE_NAME_WHEEL_A);
}


int
LambdaParallel::Initialize()
{
   // Wait a little in case the Arduino is resetting on connect
   CDeviceUtils::SleepMs(2000);

   // Send empty command (ignored) to ensure consistent state
   int err = SendSerialCommand(port_.c_str(), "", TERMINATOR);
   if (err != DEVICE_OK)
      return err;
   err = PurgeComPort(port_.c_str());
   if (err != DEVICE_OK)
      return err;

   // Put device into known state
   err = GoOnline(true);
   if (err != DEVICE_OK)
      return err;
   err = WaitForQuiescent();
   if (err != DEVICE_OK)
      return err;
   err = SetSequencing(false);
   if (err != DEVICE_OK)
      return err;

   err = CreateStringProperty(MM::g_Keyword_Closed_Position, "0", false);
   if (err != DEVICE_OK)
      return err;
   for (int i = 0; i < NUM_POSITIONS; ++i) {
      char v[16];
      snprintf(v, 15, "%d", i);
      AddAllowedValue(MM::g_Keyword_Closed_Position, v);
   }

   int pos;
   err = GetWheelPosition(pos);
   if (err != DEVICE_OK)
      return err;
   err = CreateIntegerProperty(MM::g_Keyword_State, pos, false,
         new CPropertyAction(this, &LambdaParallel::OnState));
   if (err != DEVICE_OK)
      return err;
   SetPropertyLimits(MM::g_Keyword_State, 0, NUM_POSITIONS - 1);

   err = CreateStringProperty(MM::g_Keyword_Label, "Undefined", false,
         new CPropertyAction(this, &CStateBase::OnLabel));
   if (err != DEVICE_OK)
      return err;
   for (int i = 0; i < NUM_POSITIONS; ++i)
   {
      char label[32];
      snprintf(label, 31, "Position-%d", i);
      SetPositionLabel(i, label);
   }

   int speed;
   err = GetWheelSpeed(speed);
   if (err != DEVICE_OK)
      return err;
   err = CreateIntegerProperty("Speed", speed, false,
         new CPropertyAction(this, &LambdaParallel::OnSpeed));
   if (err != DEVICE_OK)
      return err;
   SetPropertyLimits("Speed", 0, NUM_SPEEDS - 1);

   err = CreateStringProperty("UseSequencing", useSequencing_ ? "Yes" : "No", false,
         new CPropertyAction(this, &LambdaParallel::OnUseSequencing));
   if (err != DEVICE_OK)
      return err;
   AddAllowedValue("UseSequencing", "No");
   AddAllowedValue("UseSequencing", "Yes");

   return DEVICE_OK;
}


int
LambdaParallel::Shutdown()
{
   int err = GoOnline(false);
   if (err != DEVICE_OK)
      return err;

   return DEVICE_OK;
}


bool
LambdaParallel::Busy()
{
   bool busy;
   int err = GetBusy(busy);
   if (err != DEVICE_OK)
      return false;
   return busy;
}


int
LambdaParallel::OnPort(MM::PropertyBase* pProp, MM::ActionType eAct)
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
LambdaParallel::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      int position;
      int err = GetWheelPosition(position);
      if (err != DEVICE_OK)
         return err;
      pProp->Set(static_cast<long>(position));
   }
   else if (eAct == MM::AfterSet)
   {
      long v;
      pProp->Get(v);
      int err = SetWheelPosition(static_cast<int>(v));
      if (err != DEVICE_OK)
         return err;
   }
   else if (eAct == MM::IsSequenceable)
   {
      pProp->SetSequenceable(useSequencing_ ? MAX_SEQUENCE_LENGTH : 0);
   }
   else if (eAct == MM::AfterLoadSequence)
   {
      std::vector<std::string> strSequence = pProp->GetSequence();
      std::vector<int> sequence;
      for (std::vector<std::string>::const_iterator it = strSequence.begin(),
            end = strSequence.end();
            it != end;
            ++it)
      {
         if (it->size() != 1)
            return DEVICE_ERR; // Shouldn't happen
         char ch = (*it)[0];
         if (ch < '0' || ch > '9')
            return DEVICE_ERR; // Shouldn't happen
         sequence.push_back(ch - '0');
      }
      int err = LoadPositionSequence(sequence);
      if (err != DEVICE_OK)
         return err;
   }
   else if (eAct == MM::StartSequence)
   {
      int err = SetSequencing(true);
      if (err != DEVICE_OK)
         return err;
   }
   else if (eAct == MM::StopSequence)
   {
      int err = SetSequencing(false);
      if (err != DEVICE_OK)
         return err;
   }
   return DEVICE_OK;
}


int LambdaParallel::OnSpeed(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      int speed;
      int err = GetWheelSpeed(speed);
      if (err != DEVICE_OK)
         return err;
      pProp->Set(static_cast<long>(speed));
   }
   else if (eAct == MM::AfterSet)
   {
      long v;
      pProp->Get(v);
      int err = SetWheelSpeed(static_cast<int>(v));
      if (err != DEVICE_OK)
         return err;
   }
   return DEVICE_OK;
}


int LambdaParallel::OnUseSequencing(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(useSequencing_ ? "Yes" : "No");
   }
   else if (eAct == MM::AfterSet)
   {
      std::string v;
      pProp->Get(v);
      useSequencing_ = (v == "Yes");
   }
   return DEVICE_OK;
}


int
LambdaParallel::SendRecv(const std::string& cmd, std::string& resp)
{
   int err = SendSerialCommand(port_.c_str(), cmd.c_str(), TERMINATOR);
   if (err != DEVICE_OK)
      return err;

   err = GetSerialAnswer(port_.c_str(), TERMINATOR, resp);
   if (err != DEVICE_OK)
      return err;

   return DEVICE_OK;
}


int
LambdaParallel::GoOnline(bool online)
{
   std::string response;
   int err = SendRecv(online ? "O" : "L", response);
   if (err != DEVICE_OK)
      return err;

   if (response == "E")
      return ERR_DEVICE_ERROR;
   if (response != "K")
      return ERR_INVALID_RESPONSE;

   return DEVICE_OK;
}


int
LambdaParallel::GetBusy(bool& busy)
{
   std::string response;
   int err = SendRecv("B", response);
   if (err != DEVICE_OK)
      return err;

   if (response == "0")
      busy = false;
   else if (response == "1")
      busy = true;
   else
      return ERR_INVALID_RESPONSE;

   return DEVICE_OK;
}


int
LambdaParallel::WaitForQuiescent()
{
   MM::MMTime start = GetCurrentMMTime();
   MM::MMTime deadline = start + MM::MMTime(10 * 1000 * 1000); // 10 seconds

   bool busy;
   do {
      int err = GetBusy(busy);
      if (err != DEVICE_OK)
         return err;
   } while (busy && GetCurrentMMTime() < deadline);
   if (busy)
      return ERR_BUSY_TIMEOUT;
   return DEVICE_OK;
}


int
LambdaParallel::SetSequencing(bool start)
{
   std::string response;
   int err = SendRecv(start ? "R" : "E", response);
   if (err != DEVICE_OK)
      return err;

   if (response == "E")
      return ERR_DEVICE_ERROR;
   if (response != "K")
      return ERR_INVALID_RESPONSE;
   return DEVICE_OK;
}


int
LambdaParallel::GetWheelPosition(int& position)
{
   std::string response;
   int err = SendRecv("W", response);
   if (err != DEVICE_OK)
      return err;

   if (response.size() != 1)
      return ERR_INVALID_RESPONSE;
   char ch = response[0];
   if (ch < '0' || ch > '9')
      return ERR_INVALID_RESPONSE;
   position = ch - '0';
   return DEVICE_OK;
}


int
LambdaParallel::SetWheelPosition(int position)
{
   char ch = '0' + position;
   if (ch < '0' || ch > '9')
      return DEVICE_ERR; // Shouldn't happen

   std::string command("M");
   command.push_back(ch);
   std::string response;
   int err = SendRecv(command, response);
   if (err != DEVICE_OK)
      return err;

   if (response == "E")
      return ERR_DEVICE_ERROR;
   if (response != "K")
      return ERR_INVALID_RESPONSE;
   return DEVICE_OK;
}


int
LambdaParallel::GetWheelSpeed(int& speed)
{
   std::string response;
   int err = SendRecv("F", response);
   if (err != DEVICE_OK)
      return err;

   if (response.size() != 1)
      return ERR_INVALID_RESPONSE;
   char ch = response[0];
   if (ch < '0' || ch > '7')
      return ERR_INVALID_RESPONSE;
   speed = ch - '0';
   return DEVICE_OK;
}


int
LambdaParallel::SetWheelSpeed(int speed)
{
   char ch = '0' + speed;
   if (ch < '0' || ch > '7')
      return DEVICE_ERR; // Shouldn't happen

   std::string command("S");
   command.push_back(ch);
   std::string response;
   int err = SendRecv(command, response);
   if (err != DEVICE_OK)
      return err;

   if (response == "E")
      return ERR_DEVICE_ERROR;
   if (response != "K")
      return ERR_INVALID_RESPONSE;
   return DEVICE_OK;
}


int
LambdaParallel::LoadPositionSequence(std::vector<int> seq)
{
   std::string command("Q");
   for (std::vector<int>::const_iterator it = seq.begin(), end = seq.end();
         it != end; ++it)
   {
      command.push_back('0' + *it);
   }
   std::string response;
   int err = SendRecv(command, response);
   if (err != DEVICE_OK)
      return err;

   if (response == "E")
      return ERR_DEVICE_ERROR;
   if (response != "K")
      return ERR_INVALID_RESPONSE;
   return DEVICE_OK;
}
