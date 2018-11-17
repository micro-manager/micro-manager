/*
 * Micro-Manager deivce adapter for Ismatec MCP peristaltic pump
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

#include "MMDeviceConstants.h"

#include <cmath>
#include <cstdio>
#include <cstdlib>
#include <string>


#if defined(_MSC_VER) && _MSC_VER < 1900
#define snprintf _snprintf
#endif

const char* const MCP_CMD_TERM = "\r";
const char* const MCP_RESP_TERM = "\r\n";

enum {
   ERR_UNEXPECTED_RESPONSE = 20001,
   ERR_UNEXPECTED_UNIT,
};


class MCPCommand
{
   int addr_; // 1-8

protected:
   static int ParseDecimal(const std::string& s, int maxNDigits, int& result)
   {
      if (s.empty() || s.size() > maxNDigits)
         return ERR_UNEXPECTED_RESPONSE;
      for (int i = 0; i < s.size(); ++i)
      {
         char c = s[i];
         if (c < '0' || c > '9')
            return ERR_UNEXPECTED_RESPONSE;
      }
      result = std::atoi(s.c_str());
      return DEVICE_OK;
   }

   static int ParseReal(const std::string& s, double& result)
   {
      std::string::size_type dp = s.find('.');
      if (dp == std::string::npos)
      {
         int r;
         int err = ParseDecimal(s, s.size(), r);
         result = r;
         return err;
      }

      int i;
      int err = ParseDecimal(s.substr(0, dp), dp, i);
      if (err != DEVICE_OK)
         return err;

      int fDigits = s.size() - dp - 1;
      if (fDigits == 0)
      {
         result = i;
         return DEVICE_OK;
      }

      int f;
      err = ParseDecimal(s.substr(dp + 1), fDigits, f);
      if (err != DEVICE_OK)
         return err;

      result = i + f * pow(0.1, fDigits);
      return DEVICE_OK;
   }

   static int ParseRealWithUnit(const std::string& s, const std::string& unit,
         double& result)
   {
      std::string::size_type sp = s.find(' ');
      if (sp == std::string::npos)
         return ERR_UNEXPECTED_RESPONSE;
      if (s.substr(sp + 1) != unit)
         return ERR_UNEXPECTED_UNIT;
      return ParseReal(s.substr(0, sp), result);
   }

   static std::string FormatDecimal(int nDigits, int value)
   {
      char buf[16];
      snprintf(buf, 15, "%d", nDigits);
      std::string format = "%0" + std::string(buf) + "d";
      snprintf(buf, 15, format.c_str(), value);
      return std::string(buf);
   }

   // Note: only for non-negative numbers!
   static std::string FormatFixed(int nDigits, double value,
         int fractionalDigits)
   {
      if (value < 0.0)
         return std::string(nDigits, '0');

      for (int i = 0; i < fractionalDigits; ++i)
         value *= 10;
      int iValue = floor(value + 0.5);
      return FormatDecimal(nDigits, iValue);
   }

protected:
   explicit MCPCommand(int addr) :
      addr_(addr)
   {}

   virtual std::string GetCommand() = 0;

public:
   virtual ~MCPCommand() {}

   std::string Get()
   { return FormatDecimal(1, addr_) + GetCommand(); }

   virtual bool ExpectsSingleCharResponse() = 0;

   virtual int ParseResponse(const std::string& response)
   { return DEVICE_ERR; }
   virtual int ParseResponse(char response) { return DEVICE_ERR; }
};


class SingleCharResponseCommand : public MCPCommand
{
protected:
   explicit SingleCharResponseCommand(int addr) :
      MCPCommand(addr)
   {}

public:
   virtual bool ExpectsSingleCharResponse() { return true; }
   virtual int ParseResponse(char response)
   {
      if (response != '*')
         return ERR_UNEXPECTED_RESPONSE;
      return DEVICE_OK;
   }
};


class StringResponseCommand : public MCPCommand
{
protected:
   explicit StringResponseCommand(int addr) :
      MCPCommand(addr)
   {}

public:
   virtual bool ExpectsSingleCharResponse() { return false; }
};


class ResetOverloadCommand : public SingleCharResponseCommand
{
protected:
   virtual std::string GetCommand() { return "-"; }

public:
   explicit ResetOverloadCommand(int addr) :
      SingleCharResponseCommand(addr)
   {}
};


class StartStopCommand : public SingleCharResponseCommand
{
   bool start_;
   bool failedToStartInModeG_;

protected:
   virtual std::string GetCommand() { return start_ ? "H" : "I"; }

public:
   StartStopCommand(int addr, bool start) :
      SingleCharResponseCommand(addr),
      start_(start),
      failedToStartInModeG_(false)
   {}

   virtual int ParseResponse(char response)
   {
      if (response == '-')
      {
         failedToStartInModeG_ = true;
         return DEVICE_OK;
      }
      return SingleCharResponseCommand::ParseResponse(response);
   }

   bool IsFailedToStartInModeG() { return failedToStartInModeG_; }
};


class RotationDirectionCommand : public SingleCharResponseCommand
{
   bool ccw_;

protected:
   virtual std::string GetCommand() { return ccw_ ? "K" : "J"; }

public:
   RotationDirectionCommand(int addr, bool ccw) :
      SingleCharResponseCommand(addr),
      ccw_(ccw)
   {}
};


class ManualControlCommand : public SingleCharResponseCommand
{
   bool enable_;

protected:
   virtual std::string GetCommand() { return enable_ ? "A" : "B"; }

public:
   ManualControlCommand(int addr, bool enable) :
      SingleCharResponseCommand(addr),
      enable_(enable)
   {}
};


class ModeCommand : public SingleCharResponseCommand
{
public:
   enum Mode {
      ModeUnknown = 0,
      ModePumpRPM = 'L',
      ModePumpFlowRate = 'M',
      ModeDispenseTime = 'N',
      ModeDispenseVolume = 'O',
      ModeDispenseTimePlusPause = 'P',
      ModeDispenseVolumePlusPause = 'Q',
      ModeDispenseVolumeInTime = 'G',
      ModeTotal = 'R',
      ModeProgram = 'F',
   };

   static std::string ModeToString(Mode mode)
   {
      switch (mode)
      {
         case ModePumpRPM:
            return "Continuous-RPM";
         case ModePumpFlowRate:
            return "Continuous-FlowRate";
         case ModeDispenseTime:
            return "Dispense-Time";
         case ModeDispenseVolume:
            return "Dispense-Volume";
         case ModeDispenseTimePlusPause:
            return "Dispense-Time+Pause";
         case ModeDispenseVolumePlusPause:
            return "Dispense-Volume+Pause";
         case ModeDispenseVolumeInTime:
            return "Dispense-TimedVolume";
         case ModeTotal:
            return "Total";
         case ModeProgram:
            return "Program";
         default:
            return "Unknown";
      }
   }

   static Mode ModeFromString(const std::string& s)
   {
      if (s == "Continuous-RPM")
         return ModePumpRPM;
      if (s == "Continuous-FlowRate")
         return ModePumpFlowRate;
      if (s == "Dispense-Time")
         return ModeDispenseTime;
      if (s == "Dispense-Volume")
         return ModeDispenseVolume;
      if (s == "Dispense-Time+Pause")
         return ModeDispenseTimePlusPause;
      if (s == "Dispense-Volume+Pause")
         return ModeDispenseVolumePlusPause;
      if (s == "Dispense-TimedVolume")
         return ModeDispenseVolumeInTime;
      if (s == "Total")
         return ModeTotal;
      if (s == "Program")
         return ModeProgram;
      return ModeUnknown;
   }

private:
   Mode mode_;
   bool modeGTooSlow_;
   bool modeGTooFast_;

protected:
   virtual std::string GetCommand() { return std::string(1, char(mode_)); }

public:
   ModeCommand(int addr, Mode mode) :
      SingleCharResponseCommand(addr),
      mode_(mode),
      modeGTooSlow_(false),
      modeGTooFast_(false)
   {}

   virtual int ParseResponse(char response)
   {
      switch (response)
      {
         case '+':
            modeGTooFast_ = true;
            return DEVICE_OK;
         case '-':
            modeGTooSlow_ = true;
            return DEVICE_OK;
      }
      return SingleCharResponseCommand::ParseResponse(response);
   }

   bool IsModeGFlowRateTooLow() { return modeGTooSlow_; }
   bool IsModeGFlowRateTooHigh() { return modeGTooFast_; }
   bool IsOK() { return !modeGTooSlow_ && !modeGTooFast_; }
};


class PumpActiveQuery : public SingleCharResponseCommand
{
   bool active_;

protected:
   virtual std::string GetCommand() { return "E"; }

public:
   explicit PumpActiveQuery(int addr) :
      SingleCharResponseCommand(addr),
      active_(false)
   {}

   virtual int ParseResponse(char response)
   {
      switch (response)
      {
         case '+':
            active_ = true;
            return DEVICE_OK;
         case '-':
            active_ = false;
            return DEVICE_OK;
         default:
            return ERR_UNEXPECTED_RESPONSE;
      }
   }

   bool IsPumpActive() { return active_; }
};


class PumpTypeFirmwareVersionHeadIdQuery : public StringResponseCommand
{
   std::string pumpType_;
   std::string firmwareVersion_;
   std::string headId_;

protected:
   virtual std::string GetCommand() { return "#"; }

public:
   explicit PumpTypeFirmwareVersionHeadIdQuery(int addr) :
      StringResponseCommand(addr)
   {}

   virtual int ParseResponse(const std::string& response)
   {
      std::string::size_type sp1 = response.find(' ');
      if (sp1 == std::string::npos)
         return ERR_UNEXPECTED_RESPONSE;
      pumpType_ = response.substr(0, sp1);
      std::string::size_type sp2 = response.find(' ', sp1 + 1);
      if (sp2 == std::string::npos)
         return ERR_UNEXPECTED_RESPONSE;
      firmwareVersion_ = response.substr(sp1 + 1, (sp2 - sp1 - 1));
      headId_ = response.substr(sp2 + 1);
      return DEVICE_OK;
   }

   std::string GetPumpType() { return pumpType_; }
   std::string GetFirmwareVersion() { return firmwareVersion_; }
   std::string GetHeadId() { return headId_; }
};


class FirmwareVersionQuery : public StringResponseCommand
{
   std::string firmwareVersion_;

protected:
   virtual std::string GetCommand() { return "("; }

public:
   explicit FirmwareVersionQuery(int addr) :
      StringResponseCommand(addr)
   {}

   virtual int ParseResponse(const std::string& response)
   {
      firmwareVersion_ = response;
      return DEVICE_OK;
   }

   std::string GetFirmwareVersion() { return firmwareVersion_; }
};


class HeadIdQuery : public StringResponseCommand
{
   std::string headId_;

protected:
   virtual std::string GetCommand() { return ")"; }

public:
   explicit HeadIdQuery(int addr) :
      StringResponseCommand(addr)
   {}

   virtual int ParseResponse(const std::string& response)
   {
      headId_ = response;
      return DEVICE_OK;
   }

   std::string GetHeadId() { return headId_; }
};


class TubingInnerDiameterQuery : public StringResponseCommand
{
   double innerDiameterMm_;

protected:
   virtual std::string GetCommand() { return "+"; }

public:
   explicit TubingInnerDiameterQuery(int addr) :
      StringResponseCommand(addr),
      innerDiameterMm_(0.0)
   {}

   virtual int ParseResponse(const std::string& response)
   { return ParseRealWithUnit(response, "mm", innerDiameterMm_); }

   double GetInnerDiameterMm() { return innerDiameterMm_; }
};


class TubingInnerDiameterCommand : public SingleCharResponseCommand
{
   double innerDiameterMm_;

protected:
   virtual std::string GetCommand()
   { return "+" + FormatFixed(4, innerDiameterMm_, 2); }

public:
   TubingInnerDiameterCommand(int addr, double innerDiameterMm) :
      SingleCharResponseCommand(addr),
      innerDiameterMm_(innerDiameterMm)
   {}
};


class SpeedQuery : public StringResponseCommand
{
   double speedRpm_;

protected:
   virtual std::string GetCommand() { return "S"; }

public:
   explicit SpeedQuery(int addr) :
      StringResponseCommand(addr),
      speedRpm_(0.0)
   {}

   virtual int ParseResponse(const std::string& response)
   { return ParseReal(response, speedRpm_); }

   double GetSpeedRpm() { return speedRpm_; }
};


class SpeedCommand : public SingleCharResponseCommand
{
   double speedRpm_;

protected:
   virtual std::string GetCommand()
   { return "S" + FormatFixed(5, speedRpm_, 1); }

public:
   SpeedCommand(int addr, double speedRpm) :
      SingleCharResponseCommand(addr),
      speedRpm_(speedRpm)
   {}
};


class DefaultFlowRateQuery : public StringResponseCommand
{
   double flowRateMlPerMin_;

protected:
   virtual std::string GetCommand() { return "?"; }

public:
   explicit DefaultFlowRateQuery(int addr) :
      StringResponseCommand(addr),
      flowRateMlPerMin_(0.0)
   {}

   virtual int ParseResponse(const std::string& response)
   { return ParseRealWithUnit(response, "ml/min", flowRateMlPerMin_); }

   double GetFlowRateMlPerMin() { return flowRateMlPerMin_; }
};


class CalibratedFlowRateQuery : public StringResponseCommand
{
   double flowRateMlPerMin_;

protected:
   virtual std::string GetCommand() { return "!"; }

public:
   explicit CalibratedFlowRateQuery(int addr) :
      StringResponseCommand(addr),
      flowRateMlPerMin_(0.0)
   {}

   virtual int ParseResponse(const std::string& response)
   { return ParseRealWithUnit(response, "ml/min", flowRateMlPerMin_); }

   double GetFlowRateMlPerMin() { return flowRateMlPerMin_; }
};


class CalibratedFlowRateCommand : public SingleCharResponseCommand
{
   int fractionalDigits_;
   double flowRateMlPerMin_;

protected:
   virtual std::string GetCommand()
   { return "!" + FormatFixed(4, flowRateMlPerMin_, fractionalDigits_); }

public:
   // fractionalDigits must be predetermined using FractionalDigitsQuery
   // for the current pump head and tubing inner diameter.
   CalibratedFlowRateCommand(int addr, double flowRateMlPerMin,
         int fractionalDigits) :
      SingleCharResponseCommand(addr),
      fractionalDigits_(fractionalDigits),
      flowRateMlPerMin_(flowRateMlPerMin)
   {}
};


class FractionalDigitsQuery : public StringResponseCommand
{
   int digits_;

protected:
   virtual std::string GetCommand() { return "["; }

public:
   explicit FractionalDigitsQuery(int addr) :
      StringResponseCommand(addr),
      digits_(0)
   {}

   virtual int ParseResponse(const std::string& response)
   { return ParseDecimal(response, 1, digits_); }

   int GetFractionalDigits() { return digits_; }
};


class DispensingTimeQuery : public StringResponseCommand
{
   double timeSeconds_;

protected:
   virtual std::string GetCommand() { return "V"; }

public:
   explicit DispensingTimeQuery(int addr) :
      StringResponseCommand(addr),
      timeSeconds_(0.0)
   {}

   virtual int ParseResponse(const std::string& response)
   {
      int tenthSecs;
      int err = ParseDecimal(response, 8, tenthSecs);
      if (err != DEVICE_OK)
         return err;
      timeSeconds_ = 0.1 * tenthSecs;
      return DEVICE_OK;
   }

   double GetTimeSeconds() { return timeSeconds_; }
};


class DispensingTimeCommand : public SingleCharResponseCommand
{
   double timeSeconds_;

protected:
   virtual std::string GetCommand()
   {
      int tenthSecs = floor(10.0 * timeSeconds_ + 0.5);
      if (tenthSecs <= 9999)
         return "V" + FormatDecimal(4, tenthSecs);
      int minutes = floor(timeSeconds_ / 60.0 + 0.5);
      if (minutes <= 999)
         return "VM" + FormatDecimal(3, minutes);
      int hours = floor(timeSeconds_ / 3600.0 + 0.5);
      if (hours > 999)
         hours = 999;
      return "VH" + FormatDecimal(3, hours);
   }

public:
   DispensingTimeCommand(int addr, double timeSeconds) :
      SingleCharResponseCommand(addr),
      timeSeconds_(timeSeconds)
   {}
};


class DispensingRollerStepsQuery : public StringResponseCommand
{
   int steps_;

protected:
   virtual std::string GetCommand() { return "U"; }

public:
   explicit DispensingRollerStepsQuery(int addr) :
      StringResponseCommand(addr),
      steps_(0)
   {}

   virtual int ParseResponse(const std::string& response)
   { return ParseDecimal(response, 5, steps_); }

   int GetRollerSteps() { return steps_; }
};


class DispensingRollerStepsCommand : public SingleCharResponseCommand
{
   int steps_;

protected:
   virtual std::string GetCommand()
   {
      if (steps_ <= 9999)
         return "U" + FormatDecimal(4, steps_);
      return "U" + FormatDecimal(5, steps_);
   }

public:
   DispensingRollerStepsCommand(int addr, int steps) :
      SingleCharResponseCommand(addr),
      steps_(steps)
   {}
};


class DispensingVolumeCommand : public SingleCharResponseCommand
{
   int fractionalDigits_;
   double volumeMl_;

protected:
   virtual std::string GetCommand()
   { return "[" + FormatFixed(5, volumeMl_, fractionalDigits_); }

public:
   // fractionalDigits must be predetermined using FractionalDigitsQuery
   // for the current pump head and tubing inner diameter.
   DispensingVolumeCommand(int addr, double volumeMl, int fractionalDigits) :
      SingleCharResponseCommand(addr),
      fractionalDigits_(fractionalDigits),
      volumeMl_(volumeMl)
   {}
};


class RollerBackStepsQuery : public StringResponseCommand
{
   int steps_;

protected:
   virtual std::string GetCommand() { return "%"; }

public:
   explicit RollerBackStepsQuery(int addr) :
      StringResponseCommand(addr),
      steps_(0)
   {}

   virtual int ParseResponse(const std::string& response)
   { return ParseDecimal(response, 3, steps_); }

   int GetRollerSteps() { return steps_; }
};


class RollerBackStepsCommand : public SingleCharResponseCommand
{
   int steps_;

protected:
   virtual std::string GetCommand() { return "%" + FormatDecimal(4, steps_); }

public:
   RollerBackStepsCommand(int addr, int steps) :
      SingleCharResponseCommand(addr),
      steps_(steps)
   {}
};


class PauseTimeQuery : public StringResponseCommand
{
   double timeSeconds_;

protected:
   virtual std::string GetCommand() { return "T"; }

public:
   explicit PauseTimeQuery(int addr) :
      StringResponseCommand(addr),
      timeSeconds_(0.0)
   {}

   virtual int ParseResponse(const std::string& response)
   {
      int tenthSecs;
      int err = ParseDecimal(response, 8, tenthSecs);
      if (err != DEVICE_OK)
         return err;
      timeSeconds_ = 0.1 * tenthSecs;
      return DEVICE_OK;
   }

   double GetTimeSeconds() { return timeSeconds_; }
};


class PauseTimeCommand : public SingleCharResponseCommand
{
   double timeSeconds_;

protected:
   virtual std::string GetCommand()
   {
      int tenthSecs = floor(10.0 * timeSeconds_ + 0.5);
      if (tenthSecs <= 9999)
         return "T" + FormatDecimal(4, tenthSecs);
      int minutes = floor(timeSeconds_ / 60.0 + 0.5);
      if (minutes <= 999)
         return "TM" + FormatDecimal(3, minutes);
      int hours = floor(timeSeconds_ / 3600.0 + 0.5);
      if (hours > 999)
         hours = 999;
      return "TH" + FormatDecimal(3, hours);
   }

public:
   PauseTimeCommand(int addr, double timeSeconds) :
      SingleCharResponseCommand(addr),
      timeSeconds_(timeSeconds)
   {}
};


class NumberOfCyclesQuery : public StringResponseCommand
{
   int cycles_;

protected:
   virtual std::string GetCommand() { return "\""; }

public:
   explicit NumberOfCyclesQuery(int addr) :
      StringResponseCommand(addr),
      cycles_(0)
   {}

   virtual int ParseResponse(const std::string& response)
   { return ParseDecimal(response, 4, cycles_); }

   int GetNumberOfCycles() { return cycles_; }
};


class NumberOfCyclesCommand : public SingleCharResponseCommand
{
   int cycles_;

protected:
   virtual std::string GetCommand()
   { return "\"" + FormatDecimal(4, cycles_); }

public:
   NumberOfCyclesCommand(int addr, int cycles) :
      SingleCharResponseCommand(addr),
      cycles_(cycles)
   {}
};


class TotalDeliveredVolumeQuery : public StringResponseCommand
{
   double volumeMl_;

protected:
   virtual std::string GetCommand() { return ":"; }

public:
   explicit TotalDeliveredVolumeQuery(int addr) :
      StringResponseCommand(addr),
      volumeMl_(0.0)
   {}

   virtual int ParseResponse(const std::string& response)
   { return ParseRealWithUnit(response, "ml", volumeMl_); }

   double GetVolumeMl() { return volumeMl_; }
};


class ResetTotalDeliveredVolumeCommand : public SingleCharResponseCommand
{
protected:
   virtual std::string GetCommand() { return "W"; }

public:
   explicit ResetTotalDeliveredVolumeCommand(int addr) :
      SingleCharResponseCommand(addr)
   {}
};
