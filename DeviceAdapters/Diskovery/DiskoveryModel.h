///////////////////////////////////////////////////////////////////////////////
// FILE:       DiskoveryModel.h
// PROJECT:    MicroManager
// SUBSYSTEM:  DeviceAdapters
// AUTHOR:     Nico Stuurman
// COPYRIGHT:  Regents of the University of California, 2015
// 
//-----------------------------------------------------------------------------
// DESCRIPTION:
// Adapter for the Spectral/Andor/Oxford Instruments Diskovery 1 spinning disk confocal
// microscope system
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
      std::string GetHardwareVersion() { return hardwareVersion_; };
      void SetHardwareVersionMajor(const uint16_t major)
      {
         hwmajor_ = major;
         MakeVersionString(hardwareVersion_, hwmajor_, hwminor_, hwrevision_);
      };
      uint16_t GetHardwareVersionMajor() { return hwmajor_; }
      void SetHardwareVersionMinor(const uint16_t minor)
      {
         hwminor_ = minor;
         MakeVersionString(hardwareVersion_, hwmajor_, hwminor_, hwrevision_);
      };
      uint16_t GetHardwareVersionMinor() { return hwminor_; }
      void SetHardwareVersionRevision(const uint16_t revision)
      {
         hwrevision_ = revision;
         MakeVersionString(hardwareVersion_, hwmajor_, hwminor_, hwrevision_);
      };
      uint16_t GetHardwareVersionRevision() { return hwrevision_; }

      // Firmware version
      std::string GetFirmwareVersion() { return firmwareVersion_; };
      void SetFirmwareVersionMajor(const uint16_t major)
      {
         fwmajor_ = major;
         MakeVersionString(firmwareVersion_, fwmajor_, fwminor_, fwrevision_);
      };
      uint16_t GetFirmwareVersionMajor() { return fwmajor_; };
      void SetFirmwareVersionMinor(uint16_t minor)
      {
         fwminor_ = minor;
         MakeVersionString(firmwareVersion_, fwmajor_, fwminor_, fwrevision_);
      };
      uint16_t GetFirmwareVersionMinor() { return fwminor_; };
      void SetFirmwareVersionRevision(const uint16_t revision)
      {
         fwrevision_ = revision;
         MakeVersionString(firmwareVersion_, fwmajor_, fwminor_, fwrevision_);
      };
      uint16_t GetFirmwareVersionRevision() { return fwrevision_; };

      // Manufacturing date
      uint16_t GetManufactureYear() { return manYear_; };
      void SetManufactureYear(const uint16_t year) {  manYear_ = year; };
      uint16_t GetManufactureMonth() { return manMonth_; };
      void SetManufactureMonth(const uint16_t month) { manMonth_ = month; };
      uint16_t GetManufactureDay() { return manDay_; };
      void SetManufactureDay(const uint16_t day) { manDay_ = day; };

      // Serial number
      std::string GetSerialNumber() { return serialNumber_; };
      void SetSerialNumber(const std::string serialNumber) { serialNumber_ = serialNumber; };

      // Busy, uses atomic boolean
      bool GetBusy() { return busy_; };
      void SetBusy(const bool busy) { busy_ = busy; };

      // Preset SD
      void SetPresetSD(const uint16_t p);
      uint16_t GetPresetSD() { return presetSD_; };

      // Preset WF
      void SetPresetWF(const uint16_t p); 
      uint16_t GetPresetWF() { return presetWF_; };

      // Preset Iris
      void SetPresetIris(const uint16_t p);
      uint16_t GetPresetIris() { return presetIris_; };

      // Preset TIRF 
      void SetPresetTIRF(const uint16_t p);
      uint16_t GetPresetTIRF() { return presetPX_; };

      // Preset Filter W
      void SetPresetFilterW(const uint16_t p); 
      uint16_t GetPresetFilterW() { return presetFilterW_; };

      // Preset Filter T
      void SetPresetFilterT(const uint16_t p);
      uint16_t GetPresetFilterT() { return presetFilterT_; };

      // Motor Running
      void SetMotorRunningSD(const bool p); 
      bool GetMotorRunningSD() { return motorRunningSD_; };

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
      void MakeVersionString(boost::atomic<std::string>& it, uint16_t maj, 
            uint16_t min, uint16_t rev) 
      {
         std::ostringstream oss;
         oss << maj << "." << min << "." << rev;
         it  = oss.str();
      };

      boost::atomic<std::string> hardwareVersion_;
      boost::atomic<uint16_t> hwmajor_, hwminor_, hwrevision_;
      boost::atomic<std::string> firmwareVersion_;
      boost::atomic<uint16_t> fwmajor_, fwminor_, fwrevision_;
      boost::atomic<uint16_t> manYear_, manMonth_, manDay_;
      boost::atomic<std::string> serialNumber_;
      boost::atomic<bool> busy_; 
      boost::atomic<uint16_t> presetSD_, presetWF_, presetIris_, presetPX_, presetFilterT_, presetFilterW_;
      boost::atomic<bool> hasWFX_, hasWFY_, hasSD_, hasROT_, hasLIN_, hasP1_, hasP2_, 
         hasIRIS_, hasFilterW_, hasFilterT_;
      boost::atomic<bool> motorRunningSD_;

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
