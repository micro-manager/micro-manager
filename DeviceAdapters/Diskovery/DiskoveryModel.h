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
#include "boost/atomic/atomic.hpp"

class DiskoveryStateDev;

class DiskoveryModel
{
   public:
      DiskoveryModel(MM::Device* device, MM::Core& core) :
         hubDevice_(device),
         sdDevice_(0),
         wfDevice_(0),
         tirfDevice_(0),
         irisDevice_(0),
         filterWDevice_(0),
         filterTDevice_(0),
         core_(core),
         presetSD_(0),
         presetWF_(0),
         presetIris_(0),
         presetPX_(0),
         presetFilterT_(0),
         presetFilterW_(0),
         hasWFX_(false),
         hasWFY_(false),
         hasSD_(false),
         hasROT_(false),
         hasLIN_(false),
         hasP1_(false),
         hasP2_(false),
         hasIRIS_(false),
         hasFilterW_(false),
         hasFilterT_(false)
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

      void RegisterSDDevice(DiskoveryStateDev* device) { sdDevice_ = device; };
      void RegisterWFDevice(DiskoveryStateDev* device) { wfDevice_ = device; };
      void RegisterTIRFDevice(DiskoveryStateDev* device) { tirfDevice_ = device; };
      void RegisterIRISDevice(DiskoveryStateDev* device) { irisDevice_ = device; };
      void RegisterFILTERWDevice(DiskoveryStateDev* device) { filterWDevice_ = device; };
      void RegisterFILTERTDevice(DiskoveryStateDev* device) { filterTDevice_ = device; };

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

      // Busy, uses atomic boolean
      bool GetBusy() { return busy_; };
      void SetBusy(const bool busy) { busy_ = busy; };

      // Preset SD
      void SetPresetSD(const uint16_t p);
      uint16_t GetPresetSD() { MMThreadGuard guard(mutex_); return presetSD_; };

      // Preset WF
      void SetPresetWF(const uint16_t p); 
      uint16_t GetPresetWF() { MMThreadGuard guard(mutex_); return presetWF_; };

      // Preset Iris
      void SetPresetIris(const uint16_t p);
      uint16_t GetPresetIris() { MMThreadGuard guard(mutex_); return presetIris_; };

      // Preset TIRF 
      void SetPresetTIRF(const uint16_t p);
      uint16_t GetPresetTIRF() { MMThreadGuard guard(mutex_); return presetPX_; };

      // Preset Filter W
      void SetPresetFilterW(const uint16_t p); 
      uint16_t GetPresetFilterW() { MMThreadGuard guard(mutex_); return presetFilterW_; };

      // Preset Filter T
      void SetPresetFilterT(const uint16_t p);
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

      void SetHasWFX(bool h) { hasWFX_ = h; };
      bool GetHasWFX() { return hasWFX_; };

      void SetHasWFY(bool h) { hasWFY_ = h; };
      bool GetHasWFY() { return hasWFY_; };
      
      void SetHasSD(bool h) { hasSD_ = h; };
      bool GetHasSD() { return hasSD_; };

      void SetHasROT(bool h) { hasROT_ = h; };
      bool GetHasROT() { return hasROT_; };

      void SetHasLIN(bool h) { hasLIN_ = h; };
      bool GetHasLIN() { return hasLIN_; };

      void SetHasP1(bool h) { hasP1_ = h; };
      bool GetHasP1() { return hasP1_; };

      void SetHasP2(bool h) { hasP2_ = h; };
      bool GetHasP2() { return hasP2_; };

      void SetHasIRIS(bool h) { hasIRIS_ = h; };
      bool GetHasIRIS() { return hasIRIS_; };

      void SetHasFilterW(bool h) { hasFilterW_ = h; };
      bool GetHasFilterW() { return hasFilterW_; };

      void SetHasFilterT(bool h) { hasFilterT_ = h; };
      bool GetHasFilterT() { return hasFilterT_; };

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
      boost::atomic<bool> busy_; 
      uint16_t presetSD_, presetWF_, presetIris_, presetPX_, presetFilterT_, presetFilterW_;
      boost::atomic<bool> hasWFX_, hasWFY_, hasSD_, hasROT_, hasLIN_, hasP1_, hasP2_, 
         hasIRIS_, hasFilterW_, hasFilterT_;
      bool motorRunningSD_;

      MMThreadLock mutex_;
      MM::Device* hubDevice_;
      DiskoveryStateDev* sdDevice_;
      DiskoveryStateDev* wfDevice_;
      DiskoveryStateDev* tirfDevice_;
      DiskoveryStateDev* irisDevice_;
      DiskoveryStateDev* filterWDevice_;
      DiskoveryStateDev* filterTDevice_;
      MM::Core& core_;
};

#endif
