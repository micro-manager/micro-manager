///////////////////////////////////////////////////////////////////////////////
// FILE:       DiskoveryModel.h
// PROJECT:    MicroManager
// SUBSYSTEM:  DeviceAdapters
// AUTHOR:     Nico Stuurman
// COPYRIGHT:  Regenst of the University of California, 2015
// 
//-----------------------------------------------------------------------------
// DESCRIPTION:
// Adapter for the Spectral/Andor/Oxford Instruments Diskovery 1 spinning disk confocal
// microscope system
//
// LICENSE: BSD

#ifndef _DiskoveryModel_H_
#define _DiskoveryModel_H_

#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"
#include <string>
#include <map>
#include <sstream>
#include <vector>

class DiskoveryModel
{
   public:
      DiskoveryModel(MM::Device* device, MM::Core& core) :
         hubDevice_(device),
         sdDevice_(device),
         wfDevice_(device),
         tirfDevice_(device),
         irisDevice_(device),
         filterWDevice_(device),
         filterTDevice_(device),
         core_(core),
         presetSD_(0),
         presetWF_(0),
         presetIris_(0),
         presetPX_(0),
         presetFilterT_(0),
         presetFilterW_(0)
      {
         // propertyname are stored in the model so that we can generate
         // callbacks indicating properties have changed
         hardwareVersionProp_ = "HardwareVersion";
         firmwareVersionProp_ = "FirmwareVersion";
         manufacturingDateProp_ = "ManufacturingDate";
         serialNumberProp_ = "SerialNumber";
         spinningDiskPositionProp_ = "Spinning Disk Position";
         wideFieldPositionProp_ = "Wide Field Position";
         filterPositionProp_ = "Filter Position";
         irisPositionProp_ = "Iris Position";
         tirfPositionProp_ = "TIRF Position";
         motorRunningProp_ = "Motor Running";
      };
      ~DiskoveryModel() {};

      void RegisterSDDevice(MM::Device* device) { sdDevice_ = device; };
      void RegisterWFDevice(MM::Device* device) { wfDevice_ = device; };
      void RegisterTIRFDevice(MM::Device* device) { tirfDevice_ = device; };
      void RegisterIRISDevice(MM::Device* device) { irisDevice_ = device; };
      void RegisterFILTERWDevice(MM::Device* device) { filterWDevice_ = device; };
      void RegisterFILTERTDevice(MM::Device* device) { filterTDevice_ = device; };

      // Hardware version
      std::string GetHardwareVersion() 
         { MMThreadGuard guard(mutex_); return hardwareVersion_; };
      void SetHardwareVersionMajor(const uint16_t major)
      {
         MMThreadGuard guard(mutex_); 
         hwmajor_ = major;
         MakeVersionString(hardwareVersion_, hwmajor_, hwminor_, hwrevision_);
      };
      uint16_t GetHardwareVersionMajor()
         { MMThreadGuard guard(mutex_);  return hwmajor_; }
      void SetHardwareVersionMinor(const uint16_t minor)
      {
         MMThreadGuard guard(mutex_); 
         hwminor_ = minor;
         MakeVersionString(hardwareVersion_, hwmajor_, hwminor_, hwrevision_);
      };
      uint16_t GetHardwareVersionMinor()
         { MMThreadGuard guard(mutex_);  return hwminor_; }
      void SetHardwareVersionRevision(const uint16_t revision)
      {
         MMThreadGuard guard(mutex_); 
         hwrevision_ = revision;
         MakeVersionString(hardwareVersion_, hwmajor_, hwminor_, hwrevision_);
      };
      uint16_t GetHardwareVersionRevision()
         { MMThreadGuard guard(mutex_);  return hwrevision_; }

      // Firmware version
      std::string GetFirmwareVersion() 
         { MMThreadGuard guard(mutex_); return firmwareVersion_; };
      void SetFirmwareVersionMajor(const uint16_t major)
      {
         MMThreadGuard guard(mutex_); 
         fwmajor_ = major;
         MakeVersionString(firmwareVersion_, fwmajor_, fwminor_, fwrevision_);
      };
      uint16_t GetFirmwareVersionMajor()
         { MMThreadGuard guard(mutex_); return fwmajor_; };
      void SetFirmwareVersionMinor(uint16_t minor)
      {
         MMThreadGuard guard(mutex_); 
         fwminor_ = minor;
         MakeVersionString(firmwareVersion_, fwmajor_, fwminor_, fwrevision_);
      };
      uint16_t GetFirmwareVersionMinor()
         { MMThreadGuard guard(mutex_); return fwminor_; };
      void SetFirmwareVersionRevision(const uint16_t revision)
      {
         MMThreadGuard guard(mutex_); 
         fwrevision_ = revision;
         MakeVersionString(firmwareVersion_, fwmajor_, fwminor_, fwrevision_);
      };
      uint16_t GetFirmwareVersionRevision()
         { MMThreadGuard guard(mutex_); return fwrevision_; };

      // Manufacturing date
      uint16_t GetManufactureYear()
         { MMThreadGuard guard(mutex_); return manYear_; };
      void SetManufactureYear(const uint16_t year)
         { MMThreadGuard guard(mutex_); manYear_ = year; };
      uint16_t GetManufactureMonth()
         { MMThreadGuard guard(mutex_); return manMonth_; };
      void SetManufactureMonth(const uint16_t month)
         { MMThreadGuard guard(mutex_); manMonth_ = month; };
      uint16_t GetManufactureDay()
         { MMThreadGuard guard(mutex_); return manDay_; };
      void SetManufactureDay(const uint16_t day)
         { MMThreadGuard guard(mutex_); manDay_ = day; };

      // Serial number
      std::string GetSerialNumber() 
         {MMThreadGuard guard(mutex_); return serialNumber_;};
      void SetSerialNumber(const std::string serialNumber)
         {MMThreadGuard guard(mutex_); serialNumber_ = serialNumber;};

      // TODO: change Busy to lock-free implementation!
      bool GetBusy() { MMThreadGuard guard(mutex_); return busy_; };
      void SetBusy(const bool busy) { MMThreadGuard guard(mutex_); busy_ = busy; };

      // Preset SD
      void SetPresetSD(const uint16_t p) 
      { 
         MMThreadGuard guard(mutex_); 
         presetSD_ = p;
         if (sdDevice_->GetType() == MM::StateDevice)
         {
            std::string s = static_cast<std::ostringstream*>( &(std::ostringstream() << (p - 1) ) )->str();
            core_.OnPropertyChanged(sdDevice_, MM::g_Keyword_State, s.c_str());
         }
      };
      uint16_t GetPresetSD() { MMThreadGuard guard(mutex_); return presetSD_; };

      // Preset WF
      void SetPresetWF(const uint16_t p) 
      { 
         MMThreadGuard guard(mutex_); 
         presetWF_ = p; 
         if (wfDevice_->GetType() == MM::StateDevice)
         {
            std::string s = static_cast<std::ostringstream*>( &(std::ostringstream() << (p - 1)) )->str();
            core_.OnPropertyChanged(wfDevice_, MM::g_Keyword_State, s.c_str());
         }
      };
      uint16_t GetPresetWF() { MMThreadGuard guard(mutex_); return presetWF_; };

      // Preset Iris
      void SetPresetIris(const uint16_t p) 
      { 
         MMThreadGuard guard(mutex_); 
         presetIris_ = p; 
         if (irisDevice_->GetType() == MM::StateDevice)
         {
            std::string s = static_cast<std::ostringstream*>( &(std::ostringstream() << (p - 1)) )->str();
            core_.OnPropertyChanged(irisDevice_, MM::g_Keyword_State, s.c_str());
         }
      };
      uint16_t GetPresetIris() { MMThreadGuard guard(mutex_); return presetIris_; };

      // Preset TIRF 
      void SetPresetTIRF(const uint16_t p) 
      { 
         MMThreadGuard guard(mutex_); 
         presetPX_ = p; 
         if (tirfDevice_->GetType() == MM::StateDevice)
         {
            std::string s = static_cast<std::ostringstream*>( &(std::ostringstream() << (p)) )->str();
            core_.OnPropertyChanged(tirfDevice_, MM::g_Keyword_State, s.c_str());
         }
      };
      uint16_t GetPresetTIRF() { MMThreadGuard guard(mutex_); return presetPX_; };

      // Preset Filter W
      void SetPresetFilterW(const uint16_t p) 
      { 
         MMThreadGuard guard(mutex_); 
         presetFilterW_ = p; 
         if (filterWDevice_->GetType() == MM::StateDevice)
         {
            std::string s = static_cast<std::ostringstream*>( &(std::ostringstream() << (p - 1)) )->str();
            core_.OnPropertyChanged(filterWDevice_, MM::g_Keyword_State, s.c_str());
         }
      };
      uint16_t GetPresetFilterW() { MMThreadGuard guard(mutex_); return presetFilterW_; };

      // Preset Filter T
      void SetPresetFilterT(const uint16_t p) 
      { 
         MMThreadGuard guard(mutex_); 
         presetFilterT_ = p; 
         if (filterTDevice_->GetType() == MM::StateDevice)
         {
            std::string s = static_cast<std::ostringstream*>( &(std::ostringstream() << (p - 1)) )->str();
            core_.OnPropertyChanged(filterTDevice_, MM::g_Keyword_State, s.c_str());
         }
      };
      uint16_t GetPresetFilterT() { MMThreadGuard guard(mutex_); return presetFilterT_; };

      // Motor Running
      void SetMotorRunningSD(const bool p) 
      { 
         MMThreadGuard guard(mutex_); 
         motorRunningSD_ = p; 
         std::string s = static_cast<std::ostringstream*>( &(std::ostringstream() << p) )->str();
         core_.OnPropertyChanged(hubDevice_, motorRunningProp_, s.c_str());
      };
      bool GetMotorRunningSD() 
         { MMThreadGuard guard(mutex_); return motorRunningSD_; };


      const char* hardwareVersionProp_;
      const char* firmwareVersionProp_;
      const char* manufacturingDateProp_;
      const char* serialNumberProp_;
      const char* spinningDiskPositionProp_;
      const char* wideFieldPositionProp_;
      const char* motorRunningProp_;
      const char* filterPositionProp_;
      const char* irisPositionProp_;
      const char* tirfPositionProp_;

    private:
      void MakeVersionString(std::string& it, uint16_t maj, 
            uint16_t min, uint16_t rev) 
      {
         std::ostringstream oss;
         oss << maj << "." << min << "." << rev;
         it  = oss.str();
      };

      std::string hardwareVersion_;
      uint16_t hwmajor_, hwminor_, hwrevision_;
      std::string firmwareVersion_;
      uint16_t fwmajor_, fwminor_, fwrevision_;
      uint16_t manYear_, manMonth_, manDay_;
      std::string serialNumber_;
      bool busy_; // should be std::atomic_flag when we go to C++11
      uint16_t presetSD_, presetWF_, presetIris_, presetPX_, presetFilterT_, presetFilterW_;
      bool motorRunningSD_;

      MMThreadLock mutex_;
      MM::Device* hubDevice_;
      MM::Device* sdDevice_;
      MM::Device* wfDevice_;
      MM::Device* tirfDevice_;
      MM::Device* irisDevice_;
      MM::Device* filterWDevice_;
      MM::Device* filterTDevice_;
      MM::Core& core_;
};

#endif
