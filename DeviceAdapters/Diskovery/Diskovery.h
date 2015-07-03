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
#include "DiskoveryModel.h"

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


/**
 * Commander device is used to send commands to the Diskovery
 * This is where command strings are stored, and all outgoing
 * communication goes through this class
 */
class DiskoveryCommander
{
   public:
      DiskoveryCommander(MM::Device& device, MM::Core& core, std::string serialPort, 
         DiskoveryModel* model);
      ~DiskoveryCommander();
      int Initialize();
      int GetProductModel();
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

/**
 * The Listener receives all messages from the Diskovery and stores
 * the results in the Diskovery Model.  It starts a thread that 
 * listens on the serial port
 */
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

// Following are the Micro-Manager devices:

class DiskoveryHub : public HubBase<DiskoveryHub>
{
   public:
      DiskoveryHub();
      ~DiskoveryHub();

      // Device API
      // ---------
      int Initialize();
      int Shutdown();
      void GetName(char* pszName) const;
      bool Busy();
      
      MM::DeviceDetectionStatus DetectDevice(void);
      int DetectInstalledDevices();

      DiskoveryModel* GetModel() { return model_;};
      DiskoveryCommander* GetCommander() { return commander_;};

      void RegisterSDDevice(MM::Device* device) 
         { if (model_ != 0) model_->RegisterSDDevice(device);};
      void RegisterWFDevice(MM::Device* device) 
         { if (model_ != 0) model_->RegisterWFDevice(device);};
      void RegisterTIRFDevice(MM::Device* device) 
         { if (model_ != 0) model_->RegisterTIRFDevice(device);};
      
      // action interface                                                       
      // ----------------                                                       
      int OnPort(MM::PropertyBase* pProp, MM::ActionType eAct); 
      int OnHardwareVersion(MM::PropertyBase* pProp, MM::ActionType eAct); 
      int OnFirmwareVersion(MM::PropertyBase* pProp, MM::ActionType eAct); 
      int OnManufacturingDate(MM::PropertyBase* pProp, MM::ActionType eAct); 
      int OnSerialNumber(MM::PropertyBase* pProp, MM::ActionType eAct); 
      int OnFilter(MM::PropertyBase* pProp, MM::ActionType eAct); 
      int OnIris(MM::PropertyBase* pProp, MM::ActionType eAct); 
      int OnTIRF(MM::PropertyBase* pProp, MM::ActionType eAct); 
      int OnMotorRunning(MM::PropertyBase* pProp, MM::ActionType eAct); 

   private:
      int IsControllerPresent(const std::string port, bool& present);
      int QueryCommand(const char* command, std::string& answer);
      int QueryCommandInt(const char* command, unsigned int* result);
      std::vector<std::string> split(const std::string &s, char delim);
      std::vector<std::string> &split(const std::string &s, char delim, std::vector<std::string> &elems);

      std::string port_;
      std::string manufacturingDate_;
      DiskoveryModel* model_;
      DiskoveryListener* listener_;
      DiskoveryCommander* commander_;
      bool initialized_;
};

enum DevType { NONE, SD, WF, TIRF};

class DiskoveryStateDev : public CStateDeviceBase<DiskoveryStateDev>
{
   public :
      DiskoveryStateDev(const std::string devName, const std::string description, const DevType devType);
      ~DiskoveryStateDev();

      int Initialize();
      int Shutdown();
      void GetName(char* name) const;
      bool Busy();

      unsigned long GetNumberOfPositions() const {return numPos_;};

      int OnState(MM::PropertyBase* pProp, MM::ActionType eAct);

   private:
      unsigned int numPos_;
      unsigned int firstPos_;

      std::string devName_;
      DevType devType_;
      bool initialized_;
      DiskoveryHub* hub_;
};

class DiskoverySD : public CStateDeviceBase<DiskoverySD>
{
   public :
      DiskoverySD();
      ~DiskoverySD();

      int Initialize();
      int Shutdown();
      void GetName(char* name) const;
      bool Busy();

      unsigned long GetNumberOfPositions() const {return NUMPOS;};

      int OnState(MM::PropertyBase* pProp, MM::ActionType eAct);

   private:
      static const unsigned int NUMPOS = 5;
      static const unsigned int FIRSTPOS = 1;

      bool initialized_;
      DiskoveryHub* hub_;
};
    

class DiskoveryWF : public CStateDeviceBase<DiskoveryWF>
{
   public :
      DiskoveryWF();
      ~DiskoveryWF();

      int Initialize();
      int Shutdown();
      void GetName(char* name) const;
      bool Busy();

      unsigned long GetNumberOfPositions() const {return NUMPOS;};

      int OnState(MM::PropertyBase* pProp, MM::ActionType eAct);

   private:
      static const unsigned int NUMPOS = 4;
      static const unsigned int FIRSTPOS = 1;

      bool initialized_;
      DiskoveryHub* hub_;
};

class DiskoveryTIRF : public DiskoveryStateDev
{
   private:
      static const unsigned int NUMPOS = 5;
      static const unsigned int FIRSTPOS = 1;
};
#endif // _Diskovery_H_
