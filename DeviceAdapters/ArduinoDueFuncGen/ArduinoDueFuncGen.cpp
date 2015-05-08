// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//
// DESCRIPTION:   Device adapter for a function generator based on an Arduino
//                Due and Analog Devices AD660 DACs.
//
// AUTHOR:        Mark Tsuchida
//
// COPYRIGHT:     University of California, San Francisco, 2014
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

#ifdef _MSC_VER
// Needed to get M_PI.
#define _USE_MATH_DEFINES
#endif

#include "ArduinoDueFuncGen.h"

#include "due_ad660_firmware/SerialProtocol.h"

#include <boost/bind.hpp>
#include <boost/lexical_cast.hpp>
#include <boost/scoped_array.hpp>
#include <algorithm>
#include <iterator>
#include <cmath>


#ifdef _MSC_VER
inline double round(double x) { return x > 0.0 ? floor(x + 0.5) : ceil(x - 0.5); }
#else
using std::round;
#endif


const char* const g_DeviceName_AD660FunctionGenerator = "AD660FunctionGenerator";


const int MIN_ADHOC_ERROR_CODE = 20001;
const int MAX_ADHOC_ERROR_CODE = 30000;


const uint16_t DAC_MIN = 0;
const uint16_t DAC_MAX = UINT16_MAX;
const uint16_t DAC_ZERO = UINT16_MAX / 2 + 1;
const double DAC_UNITS_PER_VOLT = DAC_ZERO / 10.0;
const double DAC_MIN_VOLTS = -10.0;
const double DAC_MAX_VOLTS = 10.0;

const char* const SERIAL_TERMINATOR = "\r";



MODULE_API void InitializeModuleData()
{
   RegisterDevice(g_DeviceName_AD660FunctionGenerator, MM::GenericDevice,
         "2-channel function generator based on Arduino Due and AD660 DACs");
}


MODULE_API MM::Device* CreateDevice(const char* name)
{
   if (std::string(name) == g_DeviceName_AD660FunctionGenerator)
      return new DueFunctionGenerator();
   return 0;
}


MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}


DueFunctionGenerator::DueFunctionGenerator() :
   nextAdHocErrorCode_(MIN_ADHOC_ERROR_CODE),
   minSamplingFreqHz_(512),
   maxSamplingFreqHz_(512),
   allowInterruption_(true),
   returnToStationaryPoint_(true),
   currentBank_(0),
   waveformGenerationActive_(false),
   DACsEnabled_(false),
   samplingHz_(1000.0),
   tableLength_(512),
   banksNeedUpload_(true)
{
   for (unsigned chan = 0; chan < NUM_CHANNELS; ++chan)
   {
      currentStationaryVoltage_[chan] = 0.0;
      phaseOffset_[chan] = 0;
   }

   Super::CreateStringProperty(MM::g_Keyword_Port, port_.c_str(), false,
         new CPropertyAction(this, &Self::OnPort), true);
}


DueFunctionGenerator::~DueFunctionGenerator()
{
}


void DueFunctionGenerator::GetName(char* name) const
{
   CDeviceUtils::CopyLimitedString(name, g_DeviceName_AD660FunctionGenerator);
}


int DueFunctionGenerator::Initialize()
{
   try
   {
      try
      {
         CheckDeviceId();
      }
      catch (const DFGError&)
      {
         // Ignore first failure
         // (As of this writing, the Arduino Due Serial library appears to
         // return incorrect data when sent bytes right after a connection has
         // been established. Also, we want to guard against any leftover data
         // in the serial buffer.
      }
      CheckDeviceId();
      CollectDeviceInfo();
      InitProperties();
   }
   catch (const DFGError& e)
   {
      return AdHocErrorCode(e.GetMessage());
   }

   return DEVICE_OK;
}


int DueFunctionGenerator::Shutdown()
{
   try
   {
      SetWaveformGenerationActive(false);
   }
   catch (const DFGError& e)
   {
      return AdHocErrorCode(e.GetMessage());
   }

   return DEVICE_OK;
}


int DueFunctionGenerator::OnPort(PropertyBase* pProp, ActionType eAct)
{
   return StringPropertyActionImpl(pProp, eAct,
         boost::bind(&Self::GetPort, this),
         boost::bind(&Self::SetPort, this, _1));
}


int DueFunctionGenerator::OnAllowInterruption(PropertyBase* pProp, ActionType eAct)
{
   return ConvertedStringPropertyActionImpl<bool>(pProp, eAct,
         boost::bind(&Self::GetAllowInterruption, this),
         &Self::BoolToYesNo,
         boost::bind(&Self::SetAllowInterruption, this, _1),
         &Self::YesNoToBool);
}


int DueFunctionGenerator::OnReturnToStationaryPoint(PropertyBase* pProp, ActionType eAct)
{
   return ConvertedStringPropertyActionImpl<bool>(pProp, eAct,
         boost::bind(&Self::GetReturnToStationaryPoint, this),
         &Self::BoolToYesNo,
         boost::bind(&Self::SetReturnToStationaryPoint, this, _1),
         &Self::YesNoToBool);
}


int DueFunctionGenerator::OnBank(PropertyBase* pProp, ActionType eAct)
{
   return PropertyActionImpl<long, unsigned>(pProp, eAct,
         boost::bind(&Self::GetBank, this),
         boost::bind(&Self::SetBank, this, _1));
}


int DueFunctionGenerator::OnWaveformGenerationActive(PropertyBase* pProp, ActionType eAct)
{
   return ConvertedStringPropertyActionImpl<bool>(pProp, eAct,
         boost::bind(&Self::GetWaveformGenerationActive, this),
         &Self::BoolToOnOff,
         boost::bind(&Self::SetWaveformGenerationActive, this, _1, false),
         &Self::OnOffToBool);
}


int DueFunctionGenerator::OnDACsEnabled(PropertyBase* pProp, ActionType eAct)
{
   return ConvertedStringPropertyActionImpl<bool>(pProp, eAct,
         boost::bind(&Self::GetDACsEnabled, this),
         &Self::BoolToYesNo,
         boost::bind(&Self::SetDACsEnabled, this, _1),
         &Self::YesNoToBool);
}


int DueFunctionGenerator::OnSamplingFrequency(PropertyBase* pProp, ActionType eAct)
{
   return PropertyActionImpl<double, double>(pProp, eAct,
         boost::bind(&Self::GetSamplingFrequency, this),
         boost::bind(&Self::SetSamplingFrequency, this, _1));
}


int DueFunctionGenerator::OnWaveformTableLength(PropertyBase* pProp, ActionType eAct)
{
   return PropertyActionImpl<long, size_t>(pProp, eAct,
         boost::bind(&Self::GetWaveformTableLength, this),
         boost::bind(&Self::SetWaveformTableLength, this, _1));
}


int DueFunctionGenerator::OnWaveformFrequency(PropertyBase* pProp, ActionType eAct)
{
   return PropertyActionImpl<double, double>(pProp, eAct,
         boost::bind(&Self::GetWaveformFrequency, this));
}


int DueFunctionGenerator::OnStationaryVoltageImpl(unsigned chan, PropertyBase* pProp, ActionType eAct)
{
   return PropertyActionImpl<double, double>(pProp, eAct,
         boost::bind(&Self::GetStationaryVoltage, this, chan),
         boost::bind(&Self::SetStationaryVoltage, this, chan, _1, false));
}


int DueFunctionGenerator::OnWaveformImpl(unsigned bank, unsigned chan, PropertyBase* pProp, ActionType eAct)
{
   return ConvertedStringPropertyActionImpl<Waveform>(pProp, eAct,
         boost::bind(&Self::GetWaveform, this, bank, chan),
         &Self::WaveformToString,
         boost::bind(&Self::SetWaveform, this, bank, chan, _1),
         &Self::StringToWaveform);
}


int DueFunctionGenerator::OnAmplitudeImpl(unsigned bank, unsigned chan, PropertyBase* pProp, ActionType eAct)
{
   return PropertyActionImpl<double, double>(pProp, eAct,
         boost::bind(&Self::GetAmplitude, this, bank, chan),
         boost::bind(&Self::SetAmplitude, this, bank, chan, _1));
}


int DueFunctionGenerator::OnBiasImpl(unsigned bank, unsigned chan, PropertyBase* pProp, ActionType eAct)
{
   return PropertyActionImpl<double, double>(pProp, eAct,
         boost::bind(&Self::GetBias, this, bank, chan),
         boost::bind(&Self::SetBias, this, bank, chan, _1));
}


int DueFunctionGenerator::OnPhaseOffsetImpl(unsigned chan, PropertyBase* pProp, ActionType eAct)
{
   return PropertyActionImpl<long, long>(pProp, eAct,
         boost::bind(&Self::GetPhaseOffset, this, chan),
         boost::bind(&Self::SetPhaseOffset, this, chan, _1));
}


int DueFunctionGenerator::OnPhaseDegreesImpl(unsigned chan, PropertyBase* pProp, ActionType eAct)
{
   return PropertyActionImpl<double, double>(pProp, eAct,
            boost::bind(&Self::GetPhaseOffsetDegrees, this, chan));
}


void DueFunctionGenerator::SetStationaryVoltage(unsigned chan, double voltage, bool forceWrite)
{
   CheckChannelNumber(chan);

   // TODO voltage limits

   if (!forceWrite && voltage == currentStationaryVoltage_[chan])
      return;

   if (!GetWaveformGenerationActive())
      CmdSetDACValue(chan, VoltsToDACUnits(voltage));
   currentStationaryVoltage_[chan] = voltage;
}


double DueFunctionGenerator::GetStationaryVoltage(unsigned chan) const
{
   CheckChannelNumber(chan);
   return currentStationaryVoltage_[chan];
}


void DueFunctionGenerator::SetBank(unsigned bank)
{
   CheckBankNumber(bank);
   CmdSwitchBank(bank);
   currentBank_ = bank;
}


void DueFunctionGenerator::SetWaveformGenerationActive(bool active, bool forceWrite)
{
   if (!forceWrite && GetWaveformGenerationActive() == active)
      return;

   if (active)
   {
      if (banksNeedUpload_)
      {
         UploadWaveforms();
         banksNeedUpload_ = false;
      }
      CmdStartWaveform();
      waveformGenerationActive_ = true;
   }
   else
   {
      CmdStopWaveform();
      waveformGenerationActive_ = false;
      if (GetReturnToStationaryPoint())
      {
         for (unsigned chan = 0; chan < NUM_CHANNELS; ++chan)
            SetStationaryVoltage(chan, currentStationaryVoltage_[chan], true);
      }
      else
      {
         for (unsigned chan = 0; chan < NUM_CHANNELS; ++chan)
            currentStationaryVoltage_[chan] = DACUnitsToVolts(CmdGetDACValue(chan));
         // TODO Notify Core of property change (any other places?)
      }
   }
}


void DueFunctionGenerator::SetDACsEnabled(bool enabled)
{
   CmdEnableDACs(enabled);
   DACsEnabled_ = enabled;
}


void DueFunctionGenerator::SetSamplingFrequency(double freqHz)
{
   // TODO Check limits
   CmdSetFreq(FreqToUnsigned(freqHz));
   samplingHz_ = freqHz;
}


void DueFunctionGenerator::SetWaveformTableLength(size_t length)
{
   // TODO Check limits
   tableLength_ = length;
   // TODO Scale phase offset value (and notify core)
   banksNeedUpload_ = true;
}


double DueFunctionGenerator::GetWaveformFrequency() const
{
   return GetSamplingFrequency() / GetWaveformTableLength();
}


void DueFunctionGenerator::SetWaveform(unsigned bank, unsigned chan, Waveform waveform)
{
   CheckBankNumber(bank);
   CheckChannelNumber(chan);
   waveformDesc_[bank][chan].waveform_ = waveform;
   banksNeedUpload_ = true;
}


DueFunctionGenerator::Waveform
DueFunctionGenerator::GetWaveform(unsigned bank, unsigned chan) const
{
   CheckBankNumber(bank);
   CheckChannelNumber(chan);
   return waveformDesc_[bank][chan].waveform_;
}


void DueFunctionGenerator::SetAmplitude(unsigned bank, unsigned chan, double amplitudeVolts)
{
   CheckBankNumber(bank);
   CheckChannelNumber(chan);
   // TODO Check limits
   waveformDesc_[bank][chan].amplitudeVolts_ = amplitudeVolts;
   banksNeedUpload_ = true;
}


double DueFunctionGenerator::GetAmplitude(unsigned bank, unsigned chan) const
{
   CheckBankNumber(bank);
   CheckChannelNumber(chan);
   return waveformDesc_[bank][chan].amplitudeVolts_;
}


void DueFunctionGenerator::SetBias(unsigned bank, unsigned chan, double biasVolts)
{
   CheckBankNumber(bank);
   CheckChannelNumber(chan);
   // TODO Check limits
   waveformDesc_[bank][chan].biasVolts_ = biasVolts;
   banksNeedUpload_ = true;
}


double DueFunctionGenerator::GetBias(unsigned bank, unsigned chan) const
{
   CheckBankNumber(bank);
   CheckChannelNumber(chan);
   return waveformDesc_[bank][chan].biasVolts_;
}


void DueFunctionGenerator::SetPhaseOffset(unsigned chan, long offset)
{
   CheckChannelNumber(chan);
   CmdSetPhaseOffset(chan, offset % GetWaveformTableLength());
   phaseOffset_[chan] = offset;
}


long DueFunctionGenerator::GetPhaseOffset(unsigned chan) const
{
   CheckChannelNumber(chan);
   return phaseOffset_[chan];
}


double DueFunctionGenerator::GetPhaseOffsetDegrees(unsigned chan) const
{
   return 360.0 * GetPhaseOffset(chan) / GetWaveformTableLength();
}


int DueFunctionGenerator::AdHocErrorCode(const std::string& message)
{
   int ret = nextAdHocErrorCode_++;
   if (nextAdHocErrorCode_ > MAX_ADHOC_ERROR_CODE)
      nextAdHocErrorCode_ = MIN_ADHOC_ERROR_CODE;
   SetErrorText(ret, message.c_str());
   return ret;
}


bool DueFunctionGenerator::YesNoToBool(const std::string& yesOrNo)
{
   if (yesOrNo == "Yes")
      return true;
   if (yesOrNo == "No")
      return false;
   throw DFGError("Invalid value (" + ToQuotedString(yesOrNo) +
         "); allowed values are \"Yes\" and \"No\"");
}


bool DueFunctionGenerator::OnOffToBool(const std::string& onOrOff)
{
   if (onOrOff == "On")
      return true;
   if (onOrOff == "Off")
      return false;
   throw DFGError("Invalid value (" + ToQuotedString(onOrOff) +
         "); allowed values are \"Yes\" and \"No\"");
}


std::string DueFunctionGenerator::WaveformToString(Waveform waveform)
{
   switch (waveform)
   {
      case WFConst: return "Constant";
      case WFSquare: return "Square";
      case WFSawtooth: return "Sawtooth";
      case WFInverseSawtooth: return "Inverse Sawtooth";
      case WFSine: return "Sine";
      case WFArctanOfSine: return "Arctangent of Sine";
   }
   throw DFGError("Invalid waveform enum value (" + ToString(waveform) + ")");
}


DueFunctionGenerator::Waveform
DueFunctionGenerator::StringToWaveform(const std::string& s)
{
   if (s == "Constant") return WFConst;
   if (s == "Square") return WFSquare;
   if (s == "Sawtooth") return WFSawtooth;
   if (s == "Inverse Sawtooth") return WFInverseSawtooth;
   if (s == "Sine") return WFSine;
   if (s == "Arctangent of Sine") return WFArctanOfSine;
   throw DFGError("Invalid waveform name (" + ToQuotedString(s) + ")");
}


uint16_t DueFunctionGenerator::VoltsToDACUnits(double voltage)
{
   double units = DAC_ZERO + (DAC_UNITS_PER_VOLT * voltage);
   if (units < DAC_MIN)
      return DAC_MIN;
   if (units > DAC_MAX)
      return DAC_MAX;
   return static_cast<uint16_t>(round(units));
}


double DueFunctionGenerator::DACUnitsToVolts(uint16_t units)
{
   return (static_cast<double>(units) - DAC_ZERO) / DAC_UNITS_PER_VOLT;
}


unsigned DueFunctionGenerator::FreqToUnsigned(double freq) const
{
   if (freq > maxSamplingFreqHz_ || freq < minSamplingFreqHz_)
      throw DFGError("Frequency out of allowed range (" +
            ToString(minSamplingFreqHz_) + " - " +
            ToString(maxSamplingFreqHz_) + " Hz)");
   return static_cast<unsigned>(round(freq));
}


void DueFunctionGenerator::CheckChannelNumber(unsigned chan)
{
   if (chan > NUM_CHANNELS)
      throw DFGError("Invalid channel (" + ToString(chan) +
            "); allowed values are 0 to " + ToString(NUM_CHANNELS));
}


void DueFunctionGenerator::CheckBankNumber(unsigned bank)
{
   if (bank > NUM_BANKS)
      throw DFGError("Invalid bank number (" + ToString(bank) +
            "); allowed values are 0 to " + ToString(NUM_BANKS));
}


std::string DueFunctionGenerator::FirmwareErrorCodeToString(unsigned code)
{
   switch (code)
   {
      case DFGERR_OK:
         return "No error";
      case DFGERR_BAD_ALLOC:
         return "Out of memory";
      case DFGERR_BAD_CMD:
         return "Unknown command";
      case DFGERR_BAD_PARAMS:
         return "Invalid command parameters";
      case DFGERR_TOO_FEW_PARAMS:
         return "Too few parameters for command";
      case DFGERR_TOO_MANY_PARAMS:
         return "Too many parameters for command";
      case DFGERR_CMD_TOO_LONG:
         return "Command too long";
      case DFGERR_EXPECTED_UINT:
         return "Expected unsigned integer";
      case DFGERR_FREQ_OUT_OF_RANGE:
         return "Frequency out of allowed range";
      case DFGERR_INVALID_BANK:
         return "Invalid bank number";
      case DFGERR_INVALID_CHANNEL:
         return "Invalid channel number";
      case DFGERR_INVALID_PHASE_OFFSET:
         return "Invalid phase offset";
      case DFGERR_INVALID_SAMPLE_VALUE:
         return "Invalid DAC sample value";
      case DFGERR_INVALID_TABLE:
         return "Invalid table number";
      case DFGERR_OVERFLOW:
         return "Integer overflow";
      case DFGERR_TIMEOUT:
         return "Timeout while waiting for data";
      case DFGERR_NO_WAVEFORM:
         return "No waveform to generate";
      case DFGERR_BUSY:
         return "Request cannot be handled while generating waveform";
   }

   if (code < END_DFGERR)
      return "Error message not provided"; // Omission in device adapter
   return "Unexpected error code";
}


std::string DueFunctionGenerator::Recv() const
{
   std::string response;
   int err = const_cast<Self*>(this)->GetSerialAnswer(port_.c_str(), SERIAL_TERMINATOR, response);
   if (err != DEVICE_OK)
      throw DFGError("Serial receive error (" + ToString(err) + ")");
   return response;
}


std::string DueFunctionGenerator::SendRecv(const std::string& cmd) const
{
   int err = const_cast<Self*>(this)->SendSerialCommand(port_.c_str(), cmd.c_str(), SERIAL_TERMINATOR);
   if (err != DEVICE_OK)
      throw DFGError("Serial send error (" + ToString(err) + ")");
   return Recv();
}


std::string DueFunctionGenerator::SendRecv(const std::vector<uint16_t>& samples) const
{
   char test[2];
   *reinterpret_cast<uint16_t*>(test) = 1;
   const bool hostIsBigEndian = (test[0] == 1);
   const bool needByteSwap = hostIsBigEndian;
   std::vector<uint16_t> swapped;

   const uint16_t* buffer = &(samples[0]);
   if (needByteSwap)
   {
      swapped = samples;
      for (std::vector<uint16_t>::iterator it = swapped.begin(), end = swapped.end();
            it != end; ++it)
         *it = (*it & 0x00FF << 8) + (*it >> 8);
      buffer = &(swapped[0]);
   }

   int err = const_cast<Self*>(this)->WriteToComPort(port_.c_str(),
         reinterpret_cast<const unsigned char*>(buffer),
         static_cast<unsigned>(sizeof(uint16_t) * samples.size()));
   if (err != DEVICE_OK)
      throw DFGError("Serial send error (" + ToString(err) + ")");

   return Recv();
}


uint32_t DueFunctionGenerator::ParseResponseWithParam(const std::string& response,
      const std::string& expectedPrefix, bool handlingError) const
{
   size_t prefixLen = expectedPrefix.size();

   if (response.substr(0, prefixLen) == expectedPrefix && response.size() > prefixLen)
   {
      try
      {
         return boost::lexical_cast<uint32_t>(response.substr(prefixLen));
      }
      catch (const boost::bad_lexical_cast&)
      {
         // Fall through to error handling below.
      }
   }

   if (handlingError)
      throw DFGError("Unexpected response from firmware: " + ToQuotedString(response));
   else
      ParseUnexpectedResponse(response);
}


void DueFunctionGenerator::ParseUnexpectedResponse(const std::string& response) const
{
   uint32_t code = ParseResponseWithParam(response, "ER", true);

   // Let the serial manager log the debug info if in debug mode
   (void)SendRecv("GM");

   throw DFGError("Firmware returned error " + ToString(code) +
         " (" + FirmwareErrorCodeToString(code) + ")");
}


void DueFunctionGenerator::ParseOkResponse(const std::string& response) const
{
   if (response == "OK")
      return;
   ParseUnexpectedResponse(response);
}


uint32_t DueFunctionGenerator::ParseOkResponseWithParam(const std::string& response) const
{
   return ParseResponseWithParam(response, "OK");
}


size_t DueFunctionGenerator::ParseDataExpectedResponse(const std::string& response) const
{
   return ParseResponseWithParam(response, "EX");
}


uint32_t DueFunctionGenerator::CmdGetDeviceId() const
{
   return ParseOkResponseWithParam(SendRecv("ID"));
}


void DueFunctionGenerator::CmdSetDACValue(unsigned chan, uint16_t value) const
{
   ParseOkResponse(SendRecv("MV" + ToString(chan) + "," + ToString(value)));
}


uint16_t DueFunctionGenerator::CmdGetDACValue(unsigned chan) const
{
   uint32_t value = ParseOkResponseWithParam(SendRecv("WH" + ToString(chan)));
   if (value > 0xffffffff)
      throw DFGError("Firmware returned DAC value out of 16-bit range (" +
            ToString(value) + ")");
   return static_cast<uint16_t>(value);
}


void DueFunctionGenerator::CmdSwitchBank(unsigned bank) const
{
   ParseOkResponse(SendRecv("SB" + ToString(bank)));
}


void DueFunctionGenerator::CmdStartWaveform() const
{
   ParseOkResponse(SendRecv("RN"));
}


void DueFunctionGenerator::CmdStopWaveform() const
{
   ParseOkResponse(SendRecv("HL"));
}


void DueFunctionGenerator::CmdEnableDACs(bool enabled) const
{
   ParseOkResponse(SendRecv("ED" + ToString(enabled ? 1 : 0)));
}


void DueFunctionGenerator::CmdSetFreq(unsigned freq) const
{
   ParseOkResponse(SendRecv("FQ" + ToString(freq)));
}


unsigned DueFunctionGenerator::CmdGetMinSamplingFreq() const
{
   return ParseOkResponseWithParam(SendRecv("MN"));
}


unsigned DueFunctionGenerator::CmdGetMaxSamplingFreq() const
{
   return ParseOkResponseWithParam(SendRecv("MX"));
}


void DueFunctionGenerator::CmdSetPhaseOffset(unsigned chan, size_t offset) const
{
   ParseOkResponse(SendRecv("PH" + ToString(chan) + "," + ToString(offset)));
}


void DueFunctionGenerator::CmdSetDimensions(size_t tableLength, unsigned numBanks, unsigned numTables) const
{
   ParseOkResponse(SendRecv("DM" + ToString(tableLength) + "," +
            ToString(numBanks) + "," + ToString(numTables)));
}


void DueFunctionGenerator::CmdLoadWaveform(unsigned table, const std::vector<uint16_t>& samples) const
{
   size_t expectedSize = ParseDataExpectedResponse(SendRecv("LW" + ToString(table)));
   if (sizeof(uint16_t) * samples.size() != expectedSize)
   {
      // Keep firmware in consistent state by sending zeros
      try
      {
         std::vector<uint16_t> zeros(expectedSize, 0);
         ParseOkResponse(SendRecv(zeros));
      }
      catch (const DFGError& e)
      {
         throw DFGError("Firmware did not acknowledge receipt of waveform; "
               "it may be in an inconsistent state", e);
      }
      throw DFGError("Tried to upload " + ToString(samples.size()) +
            " 16-bit samples (" + ToString(sizeof(uint16_t) * samples.size()) +
            " bytes) but firmware requested " + ToString(expectedSize) + " bytes");
   }

   try
   {
      ParseOkResponse(SendRecv(samples));
   }
   catch (const DFGError& e)
   {
      throw DFGError("Firmware did not acknowledge receipt of waveform; "
            "it may be in an inconsistent state", e);
   }
}


void DueFunctionGenerator::CmdAssignWaveform(unsigned table, unsigned bank, unsigned chan) const
{
   ParseOkResponse(SendRecv("AW" + ToString(table) + "," +
            ToString(bank) + "," + ToString(chan)));
}


void DueFunctionGenerator::CheckDeviceId() const
{
   uint32_t deviceId;
   try
   {
      deviceId = CmdGetDeviceId();
   }
   catch (const DFGError& e)
   {
      throw DFGError("Cannot communicate with device (ID command failed)", e);
   }

   if (deviceId != DFG_SERIAL_MAGIC_ID)
      throw DFGError("Cannot communicate with device (ID command returned " +
            ToString(deviceId) + " where " + ToString(DFG_SERIAL_MAGIC_ID) +
            " was expected)");
}


void DueFunctionGenerator::CollectDeviceInfo()
{
   minSamplingFreqHz_ = CmdGetMinSamplingFreq();
   maxSamplingFreqHz_ = CmdGetMaxSamplingFreq();

   // Frequencies above 100 kHz can interfere with serial communication.
   maxSamplingFreqHz_ = 100000;
}


void DueFunctionGenerator::InitProperties()
{
   UploadWaveforms();
   InitScalarProperties();
   InitChannelProperties<0>();
   InitChannelProperties<1>();
   InitBankChannelProperties<0, 0>();
   InitBankChannelProperties<0, 1>();
   InitBankChannelProperties<1, 0>();
   InitBankChannelProperties<1, 1>();
}


void DueFunctionGenerator::InitScalarProperties()
{
   int err;

   SetWaveformGenerationActive(false, true);
   CreateStringProperty<bool>(PropertyName_WaveformGenerationActive(), GetWaveformGenerationActive(),
         false, new CPropertyAction(this, &Self::OnWaveformGenerationActive),
         &Self::BoolToOnOff);
   AddAllowedValue<bool>(PropertyName_WaveformGenerationActive(), false, &Self::BoolToOnOff);
   AddAllowedValue<bool>(PropertyName_WaveformGenerationActive(), true, &Self::BoolToOnOff);

   CreateStringProperty<bool>(PropertyName_AllowInterruption(), GetAllowInterruption(),
         false, new CPropertyAction(this, &Self::OnAllowInterruption),
         &Self::BoolToYesNo);
   AddAllowedValue<bool>(PropertyName_AllowInterruption(), false, &Self::BoolToYesNo);
   AddAllowedValue<bool>(PropertyName_AllowInterruption(), true, &Self::BoolToYesNo);

   CreateStringProperty<bool>(PropertyName_ReturnToStationaryPoint(), GetReturnToStationaryPoint(),
         false, new CPropertyAction(this, &Self::OnReturnToStationaryPoint),
         &Self::BoolToYesNo);
   AddAllowedValue<bool>(PropertyName_ReturnToStationaryPoint(), false, &Self::BoolToYesNo);
   AddAllowedValue<bool>(PropertyName_ReturnToStationaryPoint(), true, &Self::BoolToYesNo);

   SetBank(0);
   err = CreateIntegerProperty(PropertyName_Bank().c_str(), GetBank(),
         false, new CPropertyAction(this, &Self::OnBank));
   DFGError::ThrowIfMMError(err);
   err = SetPropertyLimits(PropertyName_Bank().c_str(), 0, NUM_BANKS - 1);
   DFGError::ThrowIfMMError(err);

   SetDACsEnabled(false);
   CreateStringProperty<bool>(PropertyName_DACsEnabled(), GetDACsEnabled(),
         false, new CPropertyAction(this, &Self::OnDACsEnabled),
         &Self::BoolToYesNo);
   AddAllowedValue<bool>(PropertyName_DACsEnabled(), false, &Self::BoolToYesNo);
   AddAllowedValue<bool>(PropertyName_DACsEnabled(), true, &Self::BoolToYesNo);

   SetSamplingFrequency(minSamplingFreqHz_);
   err = CreateFloatProperty(PropertyName_SamplingFrequency().c_str(), GetSamplingFrequency(),
         false, new CPropertyAction(this, &Self::OnSamplingFrequency));
   DFGError::ThrowIfMMError(err);
   err = SetPropertyLimits(PropertyName_SamplingFrequency().c_str(),
         minSamplingFreqHz_, maxSamplingFreqHz_);
   DFGError::ThrowIfMMError(err);

   err = CreateIntegerProperty(PropertyName_WaveformTableLength().c_str(),
         static_cast<long>(GetWaveformTableLength()),
         false, new CPropertyAction(this, &Self::OnWaveformTableLength));
   DFGError::ThrowIfMMError(err);
   err = SetPropertyLimits(PropertyName_WaveformTableLength().c_str(), 2, 8192);
   DFGError::ThrowIfMMError(err);

   err = CreateFloatProperty(PropertyName_WaveformFrequency().c_str(), GetWaveformFrequency(),
         true, new CPropertyAction(this, &Self::OnWaveformFrequency));
   DFGError::ThrowIfMMError(err);
}


void DueFunctionGenerator::UploadWaveforms() const
{
   std::vector<WaveformDescription> uniqueWaveforms;
   unsigned tableAssignment[NUM_BANKS][NUM_CHANNELS];

   for (unsigned bank = 0; bank < NUM_BANKS; ++bank)
   {
      for (unsigned chan = 0; chan < NUM_CHANNELS; ++chan)
      {
         typedef std::vector<WaveformDescription>::const_iterator Iterator;
         Iterator found = std::find(uniqueWaveforms.begin(), uniqueWaveforms.end(),
                  waveformDesc_[bank][chan]);
         if (found != uniqueWaveforms.end())
         {
            tableAssignment[bank][chan] =
               static_cast<unsigned>(std::distance<Iterator>(uniqueWaveforms.begin(), found));
         }
         else
         {
            tableAssignment[bank][chan] = static_cast<unsigned>(uniqueWaveforms.size());
            uniqueWaveforms.push_back(waveformDesc_[bank][chan]);
         }
      }
   }

   unsigned numTables = static_cast<unsigned>(uniqueWaveforms.size());

   CmdSetDimensions(GetWaveformTableLength(), NUM_BANKS, numTables);

   for (unsigned bank = 0; bank < NUM_BANKS; ++bank)
      for (unsigned chan = 0; chan < NUM_CHANNELS; ++chan)
         CmdAssignWaveform(tableAssignment[bank][chan], bank, chan);

   for (unsigned table = 0; table < numTables; ++table)
   {
      std::vector<uint16_t> samples = GenerateWaveform(uniqueWaveforms[table], GetWaveformTableLength());
      CmdLoadWaveform(table, samples);
   }

   // Since the banks have changed, we need to reset the current bank.
   CmdSwitchBank(GetBank());
}


std::vector<uint16_t> DueFunctionGenerator::GenerateWaveform(const WaveformDescription& desc,
      size_t tableLength)
{
   boost::scoped_array<double> voltage(new double[tableLength]);
   double amplitude = desc.amplitudeVolts_;
   double bias = desc.biasVolts_;
   switch (desc.waveform_)
   {
      case WFConst:
         for (size_t i = 0; i < tableLength; ++i)
            voltage[i] = bias;
         break;

      case WFSquare:
         for (size_t i = 0; i < tableLength; ++i)
            voltage[i] = (i < tableLength / 2 ?
                  bias + amplitude :
                  bias - amplitude);
         break;

      case WFSawtooth:
         for (size_t i = 0; i < tableLength; ++i)
         {
            double unitSawtooth = 2.0 * (static_cast<double>(i) / tableLength) - 1.0;
            voltage[i] = bias + amplitude * unitSawtooth;
         }
         break;

      case WFInverseSawtooth:
         for (size_t i = 0; i < tableLength; ++i)
         {
            double unitSawtooth = 2.0 * (static_cast<double>(i) / tableLength) - 1.0;
            voltage[i] = bias + amplitude * -unitSawtooth;
         }
         break;

      case WFSine:
         for (size_t i = 0; i < tableLength; ++i)
         {
            double theta = 2.0 * M_PI * i / tableLength;
            voltage[i] = bias + amplitude * std::sin(theta);
         }
         break;

      case WFArctanOfSine:
         for (size_t i = 0; i < tableLength; ++i)
         {
            double theta = 2.0 * M_PI * i / tableLength;
            voltage[i] = bias + amplitude * (4.0 / M_PI) * std::atan(std::sin(theta));
         }
         break;
   }

   // Clip samples that exceed 10.0 V
   for (size_t i = 0; i < tableLength; ++i)
   {
      if (voltage[i] > DAC_MAX_VOLTS)
         voltage[i] = DAC_MAX_VOLTS;
      else if (voltage[i] < DAC_MIN_VOLTS)
         voltage[i] = DAC_MIN_VOLTS;
   }

   boost::scoped_array<uint16_t> samples(new uint16_t[tableLength]);
   for (size_t i = 0; i < tableLength; ++i)
      samples[i] = static_cast<uint16_t>(round(
               DAC_MAX * (voltage[i] - DAC_MIN_VOLTS) / (DAC_MAX_VOLTS - DAC_MIN_VOLTS)));

   return std::vector<uint16_t>(samples.get(), samples.get() + tableLength);
}
