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

#include "MVPValves.h"

#include "MMDeviceConstants.h"

#include <cstdio>
#include <string>
#include <vector>


const char* const MVP_TERM = "\r";
const char MVP_ACK = 6;
const char MVP_NAK = 21;

enum {
   ERR_ECHO_MISMATCH = 20001,
   ERR_NAK,
   ERR_UNEXPECTED_RESPONSE,
   ERR_RESPONSE_PARITY,
};


class MVPCommand
{
protected:
   bool TestBit(char byte, int bit)
   {
      return (byte & (1 << bit)) != 0;
   }

   int ParseDecimal(const std::string& s, int maxNDigits, int& result)
   {
      if (s.empty() || s.size() > maxNDigits)
         return ERR_UNEXPECTED_RESPONSE;
      for (int i = 0; i < s.size(); ++i)
      {
         char c = s[i];
         if (c < '0' || c > '9')
            return ERR_UNEXPECTED_RESPONSE;
      }
      result = atoi(s.c_str());
      return DEVICE_OK;
   }

   std::string FormatDecimal(int /* nDigits */, int value)
   {
      // It turns out that there is no need to zero-pad to a fixed number of
      // digits, despite what the manual may seem to imply.
      char buf[16];
      snprintf(buf, 15, "%d", value);
      return std::string(buf);
   }

public:

   virtual ~MVPCommand() {}

   virtual std::string Get() = 0;

   // expectsMore set to true if further (CR-deliminated) response should be parsed
   virtual int ParseResponse(const std::string& response, bool& expectsMore) = 0;
};


class AutoAddressingCommand : public MVPCommand
{
   char echoedAddr_;
   int responsesParsed_;

public:
   AutoAddressingCommand() :
      echoedAddr_('\0'),
      responsesParsed_(0)
   {}

   virtual std::string Get() { return "1a"; }
   virtual int ParseResponse(const std::string& response, bool& expectsMore)
   {
      expectsMore = false;
      if (responsesParsed_++ == 0)
      {
         if (response.size() != 2)
            return ERR_UNEXPECTED_RESPONSE;
         if (response[0] != '1')
            return ERR_UNEXPECTED_RESPONSE;
         echoedAddr_ = response[1];

         if (echoedAddr_ == 'a')
         {
            // We get the echo '1a' when the address had already been assigned.
            // In this case only, there will be a further ACK response.
            expectsMore = true;
         }

         return DEVICE_OK;
      }

      if (response.size() != 1)
         return ERR_UNEXPECTED_RESPONSE;
      char ack = response[0];
      if (ack == MVP_NAK)
         return ERR_NAK;
      if (ack != MVP_ACK)
         return ERR_UNEXPECTED_RESPONSE;

      return DEVICE_OK;
   }

   bool HasMaxAddr() { return echoedAddr_ > 'a'; }
   char GetMaxAddr() { return echoedAddr_ - 1; }
};


class NormalCommand : public MVPCommand
{
   char address_;
   int responsesParsed_;

protected:
   virtual std::string GetCommandString() = 0;
   virtual int ParseContent(const std::string& content) = 0;

public:
   NormalCommand(char address) :
      address_(address),
      responsesParsed_(0)
   {}

   virtual std::string Get()
   { return std::string(1, address_) + GetCommandString(); }

   virtual int ParseResponse(const std::string& response, bool& expectsMore)
   {
      expectsMore = false;
      if (responsesParsed_++ == 0)
      {
         // The first response should be an exact echo
         if (response != Get())
            return ERR_ECHO_MISMATCH;
         expectsMore = true;
         return DEVICE_OK;
      }

      // The second response ACK or NAK; ACK may also be followed by query
      // result.
      if (response.empty())
         return ERR_UNEXPECTED_RESPONSE;
      char ack = response[0];
      if (ack == MVP_NAK)
         return ERR_NAK;
      if (ack != MVP_ACK)
         return ERR_UNEXPECTED_RESPONSE;
      std::string content = response.substr(1);
      return ParseContent(content);
   }
};


// Commands whose ACK contains no data
class NonQueryCommand : public NormalCommand
{
protected:
   virtual int ParseContent(const std::string& content)
   { return content.empty() ? DEVICE_OK : ERR_UNEXPECTED_RESPONSE; }

public:
   NonQueryCommand(char address) :
      NormalCommand(address)
   {}
};


class InitializationCommand : public NonQueryCommand
{
protected:
   virtual std::string GetCommandString() { return "LXR"; }

public:
   InitializationCommand(char address) :
      NonQueryCommand(address)
   {}
};


class ValvePositionCommand : public NonQueryCommand
{
   bool ccw_;
   int positionOneBased_;

protected:
   virtual std::string GetCommandString()
   {
      return std::string("LP") + (ccw_ ? "1" : "0") +
         FormatDecimal(2, positionOneBased_) + "R";
   }

public:
   ValvePositionCommand(char address, bool counterclockwise, int position) :
      NonQueryCommand(address),
      ccw_(counterclockwise),
      positionOneBased_(position + 1)
   {}
};


class ResetInstrumentCommand : public NonQueryCommand
{
protected:
   virtual std::string GetCommandString() { return "!"; }

public:
   ResetInstrumentCommand(char address) :
      NonQueryCommand(address)
   {}
};


class InstrumentStatusRequest : public NormalCommand
{
   char b1_;

protected:
   virtual std::string GetCommandString() { return "E1"; }
   virtual int ParseContent(const std::string& content)
   {
      if (content.size() != 1)
         return ERR_UNEXPECTED_RESPONSE;

      b1_ = content[0];
      if (TestBit(b1_, 5))
         return ERR_UNEXPECTED_RESPONSE;
      if (!TestBit(b1_, 6))
         return ERR_UNEXPECTED_RESPONSE;
      return DEVICE_OK;
   }

public:
   InstrumentStatusRequest(char address) :
      NormalCommand(address),
      b1_(0)
   {}

   bool IsReceivedCommandButNotExecuted() { return TestBit(b1_, 0); }
   bool IsValveDriveBusy() { return TestBit(b1_, 2); }
   bool IsSyntaxError() { return TestBit(b1_, 3); }
   bool IsInstrumentError() { return TestBit(b1_, 4); }
};


class InstrumentErrorRequest : public NormalCommand
{
   char b1_;

protected:
   virtual std::string GetCommandString() { return "E2"; }
   virtual int ParseContent(const std::string& content)
   {
      if (content.size() != 4)
         return ERR_UNEXPECTED_RESPONSE;
      if (content[0] != 0x50)
         return ERR_UNEXPECTED_RESPONSE;
      if (content[2] != 0x50)
         return ERR_UNEXPECTED_RESPONSE;
      if (content[3] != 0x50)
         return ERR_UNEXPECTED_RESPONSE;

      b1_ = content[1];
      if (TestBit(b1_, 3))
         return ERR_UNEXPECTED_RESPONSE;
      if (TestBit(b1_, 4))
         return ERR_UNEXPECTED_RESPONSE;
      if (TestBit(b1_, 5))
         return ERR_UNEXPECTED_RESPONSE;
      if (!TestBit(b1_, 6))
         return ERR_UNEXPECTED_RESPONSE;
      return DEVICE_OK;
   }

public:
   InstrumentErrorRequest(char address) :
      NormalCommand(address),
      b1_(0)
   {}

   bool IsValveNotInitialized() { return TestBit(b1_, 0); }
   bool IsValveInitializationError() { return TestBit(b1_, 1); }
   bool IsValveOverloadError() { return TestBit(b1_, 2); }
};


class MiscellaneousDeviceStatusRequest : public NormalCommand
{
   char b1_;

protected:
   virtual std::string GetCommandString() { return "E3"; }
   virtual int ParseContent(const std::string& content)
   {
      if (content.size() != 1)
         return ERR_UNEXPECTED_RESPONSE;

      b1_  = content[0];
      if (TestBit(b1_, 5))
         return ERR_UNEXPECTED_RESPONSE;
      if (!TestBit(b1_, 6))
         return ERR_UNEXPECTED_RESPONSE;
      return DEVICE_OK;
   }

public:
   MiscellaneousDeviceStatusRequest(char address) :
      NormalCommand(address),
      b1_(0)
   {}

   bool IsTimerBusy() { return TestBit(b1_, 0); }
   bool IsDiagnosticModeBusy() { return TestBit(b1_, 1); }
   bool IsOverTemperatureError() { return TestBit(b1_, 4); }
};


class MovementFinishedReuqest : public NormalCommand
{
   char response_;

protected:
   virtual std::string GetCommandString() { return "F"; }
   virtual int ParseContent(const std::string& content)
   {
      if (content.size() != 1)
         return ERR_UNEXPECTED_RESPONSE;
      response_ = content[0];
      switch (response_)
      {
         case 'N':
         case 'Y':
         case '*':
            return DEVICE_OK;
         default:
            return ERR_UNEXPECTED_RESPONSE;
      }
   }

public:
   MovementFinishedReuqest(char address) :
      NormalCommand(address),
      response_('\0')
   {}

   bool IsReceivedCommandButNotExecuted() { return response_ == 'N'; }
   bool IsValveDriveBusy() { return response_ == '*'; }
   bool IsMovementFinished() { return response_ == 'Y'; }
};


class ValveOverloadedRequest : public NormalCommand
{
   char response_;

protected:
   virtual std::string GetCommandString() { return "G"; }
   virtual int ParseContent(const std::string& content)
   {
      if (content.size() != 1)
         return ERR_UNEXPECTED_RESPONSE;
      response_ = content[0];
      switch (response_)
      {
         case 'N':
         case 'Y':
         case '*':
            return DEVICE_OK;
         default:
            return ERR_UNEXPECTED_RESPONSE;
      }
   }

public:
   ValveOverloadedRequest(char address) :
      NormalCommand(address),
      response_('\0')
   {}

   bool IsValveOverload() { return response_ == 'Y'; }
   bool IsValveDriveBusy() { return response_ == '*'; }
   bool IsNoError() { return response_ == 'N'; }
};


class ValvePositionRequest : public NormalCommand
{
   int positionOneBased_;

protected:
   virtual std::string GetCommandString() { return "LQP"; }
   virtual int ParseContent(const std::string& content)
   { return ParseDecimal(content, 2, positionOneBased_); }

public:
   ValvePositionRequest(char address) :
      NormalCommand(address),
      positionOneBased_(0)
   {}

   int GetPosition() { return positionOneBased_ - 1; }
};


class ValveAngleRequest : public NormalCommand
{
   int angle_;

protected:
   virtual std::string GetCommandString() { return "LQA"; }
   virtual int ParseContent(const std::string& content)
   { return ParseDecimal(content, 3, angle_); }

public:
   ValveAngleRequest(char address) :
      NormalCommand(address),
      angle_(-1)
   {}

   int GetAngle() { return angle_; } // 0-359
};


class ValveTypeRequest : public NormalCommand
{
private:
   int type_;

protected:
   virtual std::string GetCommandString() { return "LQT"; }
   virtual int ParseContent(const std::string& content)
   { return ParseDecimal(content, 1, type_); }

public:
   ValveTypeRequest(char address) :
      NormalCommand(address),
      type_(ValveTypeUnknown)
   {}

   MVPValveType GetValveType() { return MVPValveType(type_); }
};


class ValveSpeedRequest : public NormalCommand
{
   int speed_;

protected:
   virtual std::string GetCommandString() { return "LQF"; }
   virtual int ParseContent(const std::string& content)
   { return ParseDecimal(content, 1, speed_); }

public:
   ValveSpeedRequest(char address) :
      NormalCommand(address),
      speed_(-1)
   {}

   int GetSpeedHz()
   {
      switch (speed_)
      {
         case 0: return 30;
         case 1: return 40;
         case 2: return 50;
         case 3: return 60;
         case 4: return 70;
         case 5: return 80;
         case 6: return 90;
         case 7: return 100;
         case 8: return 110;
         case 9: return 120;
         default: return 0;
      }
   }
};


class FirmwareVersionRequest : public NormalCommand
{
   std::string version_;

protected:
   virtual std::string GetCommandString() { return "U"; }
   virtual int ParseContent(const std::string& content)
   {
      if (content.empty())
         return ERR_UNEXPECTED_RESPONSE;
      version_ = content;
      return DEVICE_OK;
   }

public:
   FirmwareVersionRequest(char address) :
      NormalCommand(address)
   {}

   std::string GetFirmwareVersion() { return version_; }
};
