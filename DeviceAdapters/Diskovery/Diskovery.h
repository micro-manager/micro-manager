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
      };
      ~DiskoveryModel();

      std::string GetHardwareVersion() 
         { MMThreadGuard guard(mutex_); return hardwareVersion_; };
      void SetHardwareVersionMajor(const uint16_t major)
      {
         MMThreadGuard guard(mutex_); 
         hwmajor_ = major;
         MakeVersionString(hardwareVersion_);
      };
      uint16_t GetHardwareVersionMajor()
         { MMThreadGuard guard(mutex_);  return hwmajor_; }
      void SetHardwareVersionMinor(const uint16_t minor)
      {
         MMThreadGuard guard(mutex_); 
         hwminor_ = minor;
         MakeVersionString(hardwareVersion_);
      };
      uint16_t GetHardwareVersionMinor()
         { MMThreadGuard guard(mutex_);  return hwminor_; }
      void SetHardwareVersionRevision(const uint16_t revision)
      {
         MMThreadGuard guard(mutex_); 
         hwrevision_ = revision;
         MakeVersionString(hardwareVersion_);
      };
      uint16_t GetHardwareVersionRevision()
         { MMThreadGuard guard(mutex_);  return hwrevision_; }

      std::string GetFirmwareVersion() 
         { MMThreadGuard guard(mutex_); return firmwareVersion_; };
      void SetFirmwareVersionMajor(const uint16_t major)
      {
         MMThreadGuard guard(mutex_); 
         fwmajor_ = major;
         MakeVersionString(firmwareVersion_);
      };
      uint16_t GetFirmwareVersionMajor()
         { MMThreadGuard guard(mutex_); return fwmajor_; };
      void SetFirmwareVersionMinor(uint16_t minor)
      {
         MMThreadGuard guard(mutex_); 
         fwminor_ = minor;
         MakeVersionString(firmwareVersion_);
      };
      uint16_t GetFirmwareVersionMinor()
         { MMThreadGuard guard(mutex_); return fwminor_; };
      void SetFirmwareVersionRevision(const uint16_t revision)
      {
         MMThreadGuard guard(mutex_); 
         fwrevision_ = revision;
         MakeVersionString(firmwareVersion_);
      };
      uint16_t GetFirmwareVersionRevision()
         { MMThreadGuard guard(mutex_); return fwrevision_; };

      std::string GetManufacturingDate() 
         {MMThreadGuard guard(mutex_); return manufacturingDate_;};
      void SetManufacturingDate(const std::string manufacturingDate)
         {MMThreadGuard guard(mutex_); manufacturingDate_ = manufacturingDate;};
      std::string GetSerialNumber() 
         {MMThreadGuard guard(mutex_); return serialNumber_;};
      void SetSerialNumber(const std::string serialNumber)
         {MMThreadGuard guard(mutex_); serialNumber_ = serialNumber;};
      //
      // TODO: change Busy to lock-free implementation!
      bool GetBusy() { MMThreadGuard guard(mutex_); return busy_; };
      void SetBusy(const bool busy) { MMThreadGuard guard(mutex_); busy_ = busy; };

      // TODO: add callbacks to notify the core that changes occurred
      void SetPresetSD(const uint16_t p) 
      { 
         MMThreadGuard guard(mutex_); 
         presetSD_ = p;
         std::string s = static_cast<std::ostringstream*>( &(std::ostringstream() << p) )->str();
         core_.OnPropertyChanged(&device_, spinningDiskPositionProp_, s.c_str());
      };
      uint16_t GetPresetSD() { MMThreadGuard guard(mutex_); return presetSD_; };
      void SetPresetWF(const uint16_t p) { MMThreadGuard guard(mutex_); presetWF_ = p; };
      uint16_t GetPresetWF() { MMThreadGuard guard(mutex_); return presetWF_; };
      void SetPresetIris(const uint16_t p) { MMThreadGuard guard(mutex_); presetIris_ = p; };
      uint16_t GetPresetIris() { MMThreadGuard guard(mutex_); return presetIris_; };
      void SetPresetPX(const uint16_t p) { MMThreadGuard guard(mutex_); presetPX_ = p; };
      uint16_t GetPresetPX() { MMThreadGuard guard(mutex_); return presetPX_; };
      void SetMotorRunningSD(const bool r) { MMThreadGuard guard(mutex_); motorRunningSD_ = r; };
      bool GetMotorRunningSD() { MMThreadGuard guard(mutex_); return motorRunningSD_; };


      const char* hardwareVersionProp_;
      const char* firmwareVersionProp_;
      const char* manufacturingDateProp_;
      const char* serialNumberProp_;
      const char* spinningDiskPositionProp_;


    private:
      void MakeVersionString(std::string& it) 
      {
         std::ostringstream oss;
         oss << hwmajor_ << "." << hwminor_ << "." << hwrevision_;
         it  = oss.str();
      };

      std::string hardwareVersion_;
      uint16_t hwmajor_, hwminor_, hwrevision_;
      std::string firmwareVersion_;
      uint16_t fwmajor_, fwminor_, fwrevision_;
      std::string manufacturingDate_;
      std::string serialNumber_;
      bool busy_; // should be std::atomic_flag when we go to C++11
      uint16_t presetSD_, presetWF_, presetIris_, presetPX_;
      bool motorRunningSD_;

      MMThreadLock mutex_;
      MM::Device& device_;
      MM::Core& core_;
};


class DiskoveryCommander
{
   DiskoveryCommander(std::string serialPort, DiskoveryModel model);
   ~DiskoveryCommander();
};

class DiskoveryListener : public MMDeviceThreadBase
{
   public:
      DiskoveryListener(MM::Device& device, MM::Core& core, std::string serialPort, 
            DiskoveryModel* model);
      ~DiskoveryListener();
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

   private:
      int QueryCommand(const char* command, std::string& answer);
      int QueryCommandInt(const char* command, unsigned int* result);
      std::vector<std::string> split(const std::string &s, char delim);
      std::vector<std::string> &split(const std::string &s, char delim, std::vector<std::string> &elems);

      std::string hardwareVersion_;
      std::string firmwareVersion_;
      std::string manufacturingDate_;
      std::string serialNumber_;
      unsigned int spDiskPos_;
      bool initialized_;
      double answerTimeoutMs_;
      std::string port_;
      DiskoveryModel model_;
};

#endif // _Diskovery_H_
