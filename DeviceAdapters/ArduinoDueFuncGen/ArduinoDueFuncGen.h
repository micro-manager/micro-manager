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

#pragma once

#include "DeviceBase.h"

#include <boost/function.hpp>

#include <string>
#include <vector>


template <typename T>
inline std::string ToString(const T& d)
{ return boost::lexical_cast<std::string>(d); }

template <>
inline std::string ToString<const char*>(char const* const& d)
{
   if (!d)
      return "(null)";
   return d;
}


template <typename T>
inline std::string ToQuotedString(const T& d)
{ return "\"" + ToString(d) + "\""; }

template <>
inline std::string ToQuotedString<const char*>(char const* const& d)
{
   if (!d) // Don't quote if null
      return ToString(d);
   return "\"" + ToString(d) + "\"";
}


class DFGError
{
   std::string message_;

public:
   DFGError(const std::string& message) : message_(message) {}
   DFGError(const std::string& message, const DFGError& originalError) :
      message_(message + " [ " + originalError.message_ + " ]")
   {}

   std::string GetMessage() const { return message_; }

   static void ThrowIfMMError(int err)
   {
      if (err != DEVICE_OK)
         throw DFGError("Error (code " + ToString(err) + ")");
   }
};


class DueFunctionGenerator : public CGenericBase<DueFunctionGenerator>
{
public:
   DueFunctionGenerator();
   virtual ~DueFunctionGenerator();

public: // MMDevice interface
   virtual void GetName(char* name) const;
   // virtual MM::DeviceDetectionStatus DetectDevice(); // TODO
   virtual int Initialize();
   virtual int Shutdown();
   virtual bool Busy() { return false; }

private: // Simplify the tedious property interface (and enforce a uniform structure)
   typedef DueFunctionGenerator Self;
   typedef CGenericBase<Self> Super;
   typedef MM::PropertyBase PropertyBase;
   typedef MM::ActionType ActionType;

   template <typename UInternalType>
   void CreateStringProperty(const std::string& name, const UInternalType& initialValue,
         bool readOnly, CPropertyAction* action,
         boost::function<std::string (const UInternalType&)> getterConverter)
   {
      int err = Super::CreateStringProperty(name.c_str(), getterConverter(initialValue).c_str(), readOnly, action);
      DFGError::ThrowIfMMError(err);
   }

   template <typename UInternalType>
   void AddAllowedValue(const std::string& propertyName, const UInternalType& value,
         boost::function<std::string (const UInternalType&)> getterConverter)
   {
      int err = Super::AddAllowedValue(propertyName.c_str(), getterConverter(value).c_str());
      DFGError::ThrowIfMMError(err);
   }

   // TValueType is double or long (see ConvertedStringPropertyActionImpl for std::string/char*)
   template <typename TValueType, typename UInternalType>
   int ConvertedPropertyActionImpl(PropertyBase* pProp, ActionType eAct,
         boost::function<UInternalType ()> getter,
         boost::function<TValueType (const UInternalType&)> getterConverter,
         boost::function<void (const UInternalType&)> setter = 0,
         boost::function<UInternalType (const TValueType&)> setterConverter = 0)
   {
      try
      {
         switch (eAct)
         {
            case MM::BeforeGet:
               if (getter && getterConverter)
                  pProp->Set(getterConverter(getter()));
               break;
            case MM::AfterSet:
               if (setter && setterConverter)
               {
                  TValueType value;
                  pProp->Get(value);
                  setter(setterConverter(value));
               }
               break;
         }
      }
      catch (const DFGError& e)
      {
         return AdHocErrorCode(e.GetMessage());
      }

      return DEVICE_OK;
   }

   // Specialize for conversion via static_cast
   template <typename TValueType, typename UInternalType>
   int PropertyActionImpl(PropertyBase* pProp, ActionType eAct,
         boost::function<UInternalType ()> getter,
         boost::function<void (const UInternalType&)> setter = 0)
   {
      try
      {
         switch (eAct)
         {
            case MM::BeforeGet:
               if (getter)
                  pProp->Set(static_cast<TValueType>(getter()));
               break;
            case MM::AfterSet:
               if (setter)
               {
                  TValueType value;
                  pProp->Get(value);
                  setter(static_cast<UInternalType>(value));
               }
               break;
         }
      }
      catch (const DFGError& e)
      {
         return AdHocErrorCode(e.GetMessage());
      }

      return DEVICE_OK;
   }

   // Specialize for TValueType = std::string to do the .c_str() conversion (ugh...)
   template <typename UInternalType>
   int ConvertedStringPropertyActionImpl(PropertyBase* pProp, ActionType eAct,
         boost::function<UInternalType ()> getter,
         boost::function<std::string (const UInternalType&)> getterConverter,
         boost::function<void (const UInternalType&)> setter = 0,
         boost::function<UInternalType (const std::string&)> setterConverter = 0)
   {
      try
      {
         switch (eAct)
         {
            case MM::BeforeGet:
               if (getter && getterConverter)
                  pProp->Set(getterConverter(getter()).c_str());
               break;
            case MM::AfterSet:
               if (setter && setterConverter)
               {
                  std::string value;
                  pProp->Get(value);
                  setter(setterConverter(value));
               }
               break;
         }
      }
      catch (const DFGError& e)
      {
         return AdHocErrorCode(e.GetMessage());
      }

      return DEVICE_OK;
   }


   // Further specialize for UInternalType = std::string
   int StringPropertyActionImpl(PropertyBase* pProp, ActionType eAct,
         boost::function<std::string ()> getter,
         boost::function<void (const std::string&)> setter = 0)
   {
      try
      {
         switch (eAct)
         {
            case MM::BeforeGet:
               if (getter)
                  pProp->Set(getter().c_str());
               break;
            case MM::AfterSet:
               if (setter)
               {
                  std::string value;
                  pProp->Get(value);
                  setter(value);
               }
               break;
         }
      }
      catch (const DFGError& e)
      {
         return AdHocErrorCode(e.GetMessage());
      }

      return DEVICE_OK;
   }

private: // Property names
   static std::string PropertyName_StationaryVoltage(unsigned chan)
   { return "Channel " + ToString(chan) + " stationary voltage"; }
   static std::string PropertyName_AllowInterruption() { return "Allow waveform interruption"; }
   static std::string PropertyName_ReturnToStationaryPoint() { return "Return to stationary voltage on halt"; }
   static std::string PropertyName_Bank() { return "Waveform bank"; }
   static std::string PropertyName_WaveformGenerationActive() { return "Waveform generation"; }
   static std::string PropertyName_DACsEnabled() { return "DACs enabled"; }
   static std::string PropertyName_SamplingFrequency() { return "Sampling frequency (Hz)"; }
   static std::string PropertyName_WaveformTableLength() { return "Waveform table length (samples)"; }
   static std::string PropertyName_WaveformFrequency() { return "Waveform frequency (Hz)"; }
   static std::string PropertyName_Waveform(unsigned bank, unsigned chan)
   { return "Bank " + ToString(bank) + " channel " + ToString(chan) + " waveform"; }
   static std::string PropertyName_Amplitude(unsigned bank, unsigned chan)
   { return "Bank " + ToString(bank) + " channel " + ToString(chan) + " amplitude (V)"; }
   static std::string PropertyName_Bias(unsigned bank, unsigned chan)
   { return "Bank " + ToString(bank) + " channel " + ToString(chan) + " bias (V)"; }
   static std::string PropertyName_PhaseOffset(unsigned chan)
   { return "Channel " + ToString(chan) + " phase offset (samples)"; }
   static std::string PropertyName_PhaseOffsetDegrees(unsigned chan)
   { return "Channel " + ToString(chan) + " phase offset (degrees)"; }

private: // Property action handlers
   int OnPort(PropertyBase* pProp, ActionType eAct); // Pre-init

   template <unsigned Chan>
   int OnStationaryVoltage(PropertyBase* pProp, ActionType eAct)
   { return OnStationaryVoltageImpl(Chan, pProp, eAct); }
   int OnStationaryVoltageImpl(unsigned chan, PropertyBase* pProp, ActionType eAct);

   int OnAllowInterruption(PropertyBase* pProp, ActionType eAct);
   int OnReturnToStationaryPoint(PropertyBase* pProp, ActionType eAct);
   int OnBank(PropertyBase* pProp, ActionType eAct);
   int OnWaveformGenerationActive(PropertyBase* pProp, ActionType eAct);
   int OnDACsEnabled(PropertyBase* pProp, ActionType eAct);

   int OnSamplingFrequency(PropertyBase* pProp, ActionType eAct);
   int OnWaveformTableLength(PropertyBase* pProp, ActionType eAct);
   int OnWaveformFrequency(PropertyBase* pProp, ActionType eAct); // Read-only

   template <unsigned Bank, unsigned Chan>
   int OnWaveform(PropertyBase* pProp, ActionType eAct)
   { return OnWaveformImpl(Bank, Chan, pProp, eAct); }
   int OnWaveformImpl(unsigned bank, unsigned chan, PropertyBase* pProp, ActionType eAct);

   template <unsigned Bank, unsigned Chan>
   int OnAmplitude(PropertyBase* pProp, ActionType eAct)
   { return OnAmplitudeImpl(Bank, Chan, pProp, eAct); }
   int OnAmplitudeImpl(unsigned bank, unsigned chan, PropertyBase* pProp, ActionType eAct);

   template <unsigned Bank, unsigned Chan>
   int OnBias(PropertyBase* pProp, ActionType eAct)
   { return OnBiasImpl(Bank, Chan, pProp, eAct); }
   int OnBiasImpl(unsigned bank, unsigned chan, PropertyBase* pProp, ActionType eAct);

   template <unsigned Chan>
   int OnPhaseOffset(PropertyBase* pProp, ActionType eAct)
   { return OnPhaseOffsetImpl(Chan, pProp, eAct); }
   int OnPhaseOffsetImpl(unsigned chan, PropertyBase* pProp, ActionType eAct);

   template <unsigned Chan>
   int OnPhaseDegrees(PropertyBase* pProp, ActionType eAct) // Read-only
   { return OnPhaseDegreesImpl(Chan, pProp, eAct); }
   int OnPhaseDegreesImpl(unsigned chan, PropertyBase* pProp, ActionType eAct);

private: // Private types
   enum Waveform { WFConst, WFSquare, WFSawtooth, WFInverseSawtooth, WFSine, WFArctanOfSine, NUM_WAVEFORM_TYPES };
   struct WaveformDescription
   {
      Waveform waveform_;
      double amplitudeVolts_;
      double biasVolts_;

      WaveformDescription() : waveform_(WFSine), amplitudeVolts_(10.0), biasVolts_(0.0) {}

      WaveformDescription(Waveform waveform, double amplitudeVolts, double biasVolts) :
         waveform_(waveform), amplitudeVolts_(amplitudeVolts), biasVolts_(biasVolts)
      {}

      bool operator==(const WaveformDescription& rhs) const
      {
         return (waveform_ == rhs.waveform_ &&
               amplitudeVolts_ == rhs.amplitudeVolts_ &&
               biasVolts_ == rhs.biasVolts_);
      }
   };

private: // Accessors
   void SetPort(const std::string& label) { port_ = label; }
   std::string GetPort() const { return port_; }

   void SetStationaryVoltage(unsigned chan, double voltage, bool forceWrite = false);
   double GetStationaryVoltage(unsigned chan) const;

   void SetAllowInterruption(bool allow) { allowInterruption_ = allow; }
   bool GetAllowInterruption() const { return allowInterruption_; }

   void SetReturnToStationaryPoint(bool flag) { returnToStationaryPoint_ = flag; }
   bool GetReturnToStationaryPoint() const { return returnToStationaryPoint_; }

   void SetBank(unsigned bank);
   unsigned GetBank() const { return currentBank_; }

   void SetWaveformGenerationActive(bool active, bool forceWrite = false);
   bool GetWaveformGenerationActive() const { return waveformGenerationActive_; }

   void SetDACsEnabled(bool enabled);
   bool GetDACsEnabled() const { return DACsEnabled_; }

   void SetSamplingFrequency(double freqHz);
   double GetSamplingFrequency() const { return samplingHz_; }

   void SetWaveformTableLength(size_t length);
   size_t GetWaveformTableLength() const { return tableLength_; }

   double GetWaveformFrequency() const;

   void SetWaveform(unsigned bank, unsigned chan, Waveform waveform);
   Waveform GetWaveform(unsigned bank, unsigned chan) const;

   void SetAmplitude(unsigned bank, unsigned chan, double amplitudeVolts);
   double GetAmplitude(unsigned bank, unsigned chan) const;

   void SetBias(unsigned bank, unsigned chan, double biasVolts);
   double GetBias(unsigned bank, unsigned chan) const;

   void SetPhaseOffset(unsigned chan, long offset);
   long GetPhaseOffset(unsigned chan) const;

   double GetPhaseOffsetDegrees(unsigned chan) const;

private: // Internal implementation functions
   static const int MIN_VOLTAGE = -10;
   static const int MAX_VOLTAGE = 10;

   int AdHocErrorCode(const std::string& message);

   static std::string BoolToYesNo(bool flag) { return flag ? "Yes" : "No"; }
   static bool YesNoToBool(const std::string& yesOrNo);
   static std::string BoolToOnOff(bool flag) { return flag ? "On" : "Off"; }
   static bool OnOffToBool(const std::string& onOrOff);
   static std::string WaveformToString(Waveform waveform);
   static Waveform StringToWaveform(const std::string& waveformString);
   static uint16_t VoltsToDACUnits(double voltage);
   static double DACUnitsToVolts(uint16_t units);
   unsigned FreqToUnsigned(double freq) const;
   static void CheckChannelNumber(unsigned chan);
   static void CheckBankNumber(unsigned bank);
   static std::string FirmwareErrorCodeToString(unsigned code);

   std::string Recv() const;
   std::string SendRecv(const std::string& cmd) const;
   std::string SendRecv(const std::vector<uint16_t>& samples) const;

   uint32_t ParseResponseWithParam(const std::string& response,
      const std::string& expectedPrefix, bool handlingError = false) const;
#ifdef _MSC_VER
   __declspec(noreturn)
#endif
   void ParseUnexpectedResponse(const std::string& response) const
#ifdef __GNUC__
   __attribute__((noreturn))
#endif
   ;
   void ParseOkResponse(const std::string& response) const;
   uint32_t ParseOkResponseWithParam(const std::string& response) const;
   size_t ParseDataExpectedResponse(const std::string& response) const;

   uint32_t CmdGetDeviceId() const;
   void CmdSetDACValue(unsigned chan, uint16_t value) const;
   uint16_t CmdGetDACValue(unsigned chan) const;
   void CmdSwitchBank(unsigned bank) const;
   void CmdStartWaveform() const;
   void CmdStopWaveform() const;
   void CmdEnableDACs(bool enabled) const;
   void CmdSetFreq(unsigned freq) const;
   unsigned CmdGetMinSamplingFreq() const;
   unsigned CmdGetMaxSamplingFreq() const;
   void CmdSetPhaseOffset(unsigned chan, size_t offset) const;
   void CmdSetDimensions(size_t tableLength, unsigned numBanks, unsigned numTables) const;
   void CmdLoadWaveform(unsigned table, const std::vector<uint16_t>& samples) const;
   void CmdAssignWaveform(unsigned table, unsigned bank, unsigned chan) const;

   void CheckDeviceId() const;
   void CollectDeviceInfo();
   void InitProperties();
   void InitScalarProperties();
   template <unsigned Chan>
   void InitChannelProperties()
   {
      int err;

      SetStationaryVoltage(Chan, GetStationaryVoltage(Chan), true);
      err = CreateFloatProperty(PropertyName_StationaryVoltage(Chan).c_str(), GetStationaryVoltage(Chan),
            false, new CPropertyAction(this, &Self::OnStationaryVoltage<Chan>));
      DFGError::ThrowIfMMError(err);
      err = SetPropertyLimits(PropertyName_StationaryVoltage(Chan).c_str(), MIN_VOLTAGE, MAX_VOLTAGE);
      DFGError::ThrowIfMMError(err);

      err = CreateIntegerProperty(PropertyName_PhaseOffset(Chan).c_str(), GetPhaseOffset(Chan),
            false, new CPropertyAction(this, &Self::OnPhaseOffset<Chan>));
      DFGError::ThrowIfMMError(err);

      err = CreateFloatProperty(PropertyName_PhaseOffsetDegrees(Chan).c_str(), GetPhaseOffsetDegrees(Chan),
            true, new CPropertyAction(this, &Self::OnPhaseDegrees<Chan>));
      DFGError::ThrowIfMMError(err);
   }
   template <unsigned Bank, unsigned Chan>
   void InitBankChannelProperties()
   {
      int err;

      CreateStringProperty<Waveform>(PropertyName_Waveform(Bank, Chan), GetWaveform(Bank, Chan),
            false, new CPropertyAction(this, &Self::OnWaveform<Bank, Chan>),
	    &Self::WaveformToString);
      for (int waveform = 0; waveform != NUM_WAVEFORM_TYPES; ++waveform)
         AddAllowedValue<Waveform>(PropertyName_Waveform(Bank, Chan),
               static_cast<Waveform>(waveform), &Self::WaveformToString);

      err = CreateFloatProperty(PropertyName_Amplitude(Bank, Chan).c_str(), GetAmplitude(Bank, Chan),
            false, new CPropertyAction(this, &Self::OnAmplitude<Bank, Chan>));
      DFGError::ThrowIfMMError(err);
      err = SetPropertyLimits(PropertyName_Amplitude(Bank, Chan).c_str(), MIN_VOLTAGE, MAX_VOLTAGE);
      DFGError::ThrowIfMMError(err);

      err = CreateFloatProperty(PropertyName_Bias(Bank, Chan).c_str(), GetBias(Bank, Chan),
            false, new CPropertyAction(this, &Self::OnBias<Bank, Chan>));
      DFGError::ThrowIfMMError(err);
      err = SetPropertyLimits(PropertyName_Bias(Bank, Chan).c_str(), MIN_VOLTAGE, MAX_VOLTAGE);
      DFGError::ThrowIfMMError(err);
   }

   void UploadWaveforms() const;
   static std::vector<uint16_t> GenerateWaveform(const WaveformDescription& desc, size_t tableLength);

private: // Data
   static const unsigned NUM_CHANNELS = 2; // This is fixed in the hardware
   static const unsigned NUM_BANKS = 2; // This is an arbitrary limitation of the device adapter

   int nextAdHocErrorCode_;

   // Serial port label.
   std::string port_;

   // Static limits read from the device.
   uint32_t minSamplingFreqHz_;
   uint32_t maxSamplingFreqHz_;

   // Current voltages, when waveform generation is halted. During waveform
   // generation, holds the voltages that were set just before waveform
   // generation started.
   double currentStationaryVoltage_[NUM_CHANNELS];

   // If true, when setting a property that cannot be set during waveform
   // generation, pause and resume waveform generation. If false, return an
   // error.
   bool allowInterruption_;

   // If true, set voltages to prevous stationary voltages when waveform
   // generation is halted. Otherwise, stay at the voltages set by the last
   // waveform sample.
   bool returnToStationaryPoint_;

   // Remember the currently selected bank.
   unsigned currentBank_;

   // True if we are currently generating waveforms.
   bool waveformGenerationActive_;

   // True if DACs are enabled (this is independent of all other
   // voltage/waveform settings and states).
   bool DACsEnabled_;

   // Sampling frequency.
   double samplingHz_;

   // Current waveform table length.
   size_t tableLength_;

   // Parameters to compute the waveform to be loaded.
   WaveformDescription waveformDesc_[NUM_BANKS][NUM_CHANNELS];

   // Starting phase offset (in number of samples).
   // Not clipped to table indices.
   long phaseOffset_[NUM_CHANNELS];

   // Set to true if waveform settings have changed and must be re-uploaded.
   bool banksNeedUpload_;
};
