///////////////////////////////////////////////////////////////////////////////
// FILE:       Diskovery.h
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

#ifndef _Diskovery_H_
#define _Diskovery_H_

#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"
#include <string>
#include <map>
#include <sstream>
#include <vector>

// Use the name 'return_value' that is unlikely to appear within 'result'.
#define RETURN_ON_MM_ERROR( result ) do { \
   int return_value = (result); \
   if (return_value != DEVICE_OK) { \
      return return_value; \
   } \
} while (0)

//////////////////////////////////////////////////////////////////////////////
// Error codes
//
#define ERR_UNKNOWN_POSITION         10002
#define ERR_INVALID_SPEED            10003
#define ERR_PORT_CHANGE_FORBIDDEN    10004                                   
#define ERR_SET_POSITION_FAILED      10005                                   
#define ERR_INVALID_STEP_SIZE        10006                                   
#define ERR_LOW_LEVEL_MODE_FAILED    10007                                   
#define ERR_INVALID_MODE             10008 


class DiskoveryModel
{
   public:
      DiskoveryModel(MM::Device& device, MM::Core& core) :
         device_(device),
         core_(core)
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
         std::string s = static_cast<std::ostringstream*>( &(std::ostringstream() << p) )->str();
         core_.OnPropertyChanged(&device_, spinningDiskPositionProp_, s.c_str());
      };
      uint16_t GetPresetSD() { MMThreadGuard guard(mutex_); return presetSD_; };

      // Preset WF
      void SetPresetWF(const uint16_t p) 
      { 
         MMThreadGuard guard(mutex_); 
         presetWF_ = p; 
         std::string s = static_cast<std::ostringstream*>( &(std::ostringstream() << p) )->str();
         core_.OnPropertyChanged(&device_, wideFieldPositionProp_, s.c_str());
      };
      uint16_t GetPresetWF() { MMThreadGuard guard(mutex_); return presetWF_; };

      // Preset Iris
      void SetPresetIris(const uint16_t p) 
      { 
         MMThreadGuard guard(mutex_); 
         presetIris_ = p; 
         std::string s = static_cast<std::ostringstream*>( &(std::ostringstream() << p) )->str();
         core_.OnPropertyChanged(&device_, irisPositionProp_, s.c_str());
      };
      uint16_t GetPresetIris() { MMThreadGuard guard(mutex_); return presetIris_; };

      // Preset TIRF 
      void SetPresetTIRF(const uint16_t p) 
      { 
         MMThreadGuard guard(mutex_); 
         presetPX_ = p; 
         std::string s = static_cast<std::ostringstream*>( &(std::ostringstream() << p) )->str();
         core_.OnPropertyChanged(&device_, tirfPositionProp_, s.c_str());
      };
      uint16_t GetPresetTIRF() { MMThreadGuard guard(mutex_); return presetPX_; };

      // Preset Filter 
      void SetPresetFilter(const uint16_t p) 
      { 
         MMThreadGuard guard(mutex_); 
         presetFilter_ = p; 
         std::string s = static_cast<std::ostringstream*>( &(std::ostringstream() << p) )->str();
         core_.OnPropertyChanged(&device_, filterPositionProp_, s.c_str());
      };
      uint16_t GetPresetFilter() { MMThreadGuard guard(mutex_); return presetFilter_; };

      // Motor Running
      void SetMotorRunningSD(const bool p) 
      { 
         MMThreadGuard guard(mutex_); 
         motorRunningSD_ = p; 
         std::string s = static_cast<std::ostringstream*>( &(std::ostringstream() << p) )->str();
         core_.OnPropertyChanged(&device_, motorRunningProp_, s.c_str());
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
      uint16_t presetSD_, presetWF_, presetIris_, presetPX_, presetFilter_;
      bool motorRunningSD_;

      MMThreadLock mutex_;
      MM::Device& device_;
      MM::Core& core_;
};


class DiskoveryCommander
{
   public:
      DiskoveryCommander(MM::Device& device, MM::Core& core, std::string serialPort, 
         DiskoveryModel* model);
      ~DiskoveryCommander();
      int Initialize();
      int SetPresetSD(uint16_t pos);
      int SetPresetWF(uint16_t pos);
      int SetPresetFilter(uint16_t pos);
      int SetPresetIris(uint16_t pos);
      int SetPresetTIRF(uint16_t pos);
      int SetMotorRunningSD(uint16_t pos);

   private:
      inline int SendCommand(const char* command);

      MM::Device& device_;
      MM::Core& core_;
      DiskoveryModel* model_;
      std::string port_;
};

class DiskoveryListener : public MMDeviceThreadBase
{
   public:
      DiskoveryListener(MM::Device& device, MM::Core& core, std::string serialPort, 
            DiskoveryModel* model);
      ~DiskoveryListener();
      void Shutdown();
      int svc();
      int open (void*) { return 0; }
      int close (unsigned long) { return 0; }
      void Start();
      void Stop() { stop_ = true; };

   private:
      void ParseMessage(std::string message);
      std::vector<std::string>& split(const std::string &s, char delim, std::vector<std::string> & elems);
      std::vector<std::string> split(const std::string &s, char delim);

      bool stop_;
      MM::Device& device_;
      MM::Core& core_;
      DiskoveryModel* model_;
      std::string port_;

};

class Diskovery : public CGenericBase<Diskovery>
{
   public:
      Diskovery();
      ~Diskovery();

      // Device API
      // ---------
      int Initialize();
      int Shutdown();
      void GetName(char* pszName) const;
      bool Busy();
      
      // action interface                                                       
      // ----------------                                                       
      int OnPort(MM::PropertyBase* pProp, MM::ActionType eAct); 
      int OnHardwareVersion(MM::PropertyBase* pProp, MM::ActionType eAct); 
      int OnFirmwareVersion(MM::PropertyBase* pProp, MM::ActionType eAct); 
      int OnManufacturingDate(MM::PropertyBase* pProp, MM::ActionType eAct); 
      int OnSerialNumber(MM::PropertyBase* pProp, MM::ActionType eAct); 
      int OnSpDiskPresetPosition(MM::PropertyBase* pProp, MM::ActionType eAct); 
      int OnWideFieldPreset(MM::PropertyBase* pProp, MM::ActionType eAct); 
      int OnFilter(MM::PropertyBase* pProp, MM::ActionType eAct); 
      int OnIris(MM::PropertyBase* pProp, MM::ActionType eAct); 
      int OnTIRF(MM::PropertyBase* pProp, MM::ActionType eAct); 
      int OnMotorRunning(MM::PropertyBase* pProp, MM::ActionType eAct); 

   private:
      int QueryCommand(const char* command, std::string& answer);
      int QueryCommandInt(const char* command, unsigned int* result);
      std::vector<std::string> split(const std::string &s, char delim);
      std::vector<std::string> &split(const std::string &s, char delim, std::vector<std::string> &elems);

      std::string port_;
      std::string manufacturingDate_;
      DiskoveryModel* model_;
      DiskoveryListener* listener_;
      DiskoveryCommander* commander_;
};

#endif // _Diskovery_H_
