///////////////////////////////////////////////////////////////////////////////
// FILE:       Diskovery.h
// PROJECT:    MicroManager
// SUBSYSTEM:  DeviceAdapters
// AUTHOR:     Nico Stuurman
// COPYRIGHT:  Regents of the University of California, 2015
// 
//-----------------------------------------------------------------------------
// DESCRIPTION:
// Adapter for the Spectral/Andor/Oxford Instruments Diskovery 1 spinning disk confocal
// microscope system
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


#ifndef _Diskovery_H_
#define _Diskovery_H_

#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"
#include <string>
#include <map>
#include <sstream>
#include <vector>
#include "DiskoveryModel.h"
#include "BlockingQueue.h"

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
 * Thread that services the blockingqueue; a queue of commands
 * that can only be send to the controller when it is not busy
 */
class MessageSender : public MMDeviceThreadBase
{
   public:
      MessageSender(MM::Device& device, MM::Core& core, std::string port,
            BlockingQueue<std::string>& blockingQueue, DiskoveryModel* model);
      ~MessageSender();
      void Shutdown();
      int svc();
      int open (void*) { return 0; }
      int close (unsigned long) { return 0; }
      void Start();
      void Stop() { stop_ = true; };

   private:
      bool stop_;
      MM::Device& device_;
      MM::Core& core_;
      std::string port_;
      BlockingQueue<std::string>& blockingQueue_;
      DiskoveryModel* model_;
};

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
      int CheckCapabilities();
      int SetMotorRunningSD(uint16_t pos);
      int SetPositionRot(uint32_t pos);
      int SetPositionLin(uint32_t pos);
      int GetProductModel();
      int SetPresetSD(uint16_t pos);
      int SetPresetWF(uint16_t pos);
      int SetPresetFilterW(uint16_t pos);
      int SetPresetFilterT(uint16_t pos);
      int SetPresetIris(uint16_t pos);
      int SetPresetTIRF(uint16_t pos);
      int GetWFButtonName(uint16_t pos);
      int GetIrisButtonName(uint16_t pos);
      int GetFilterWButtonName(uint16_t pos);
      int GetFilterTButtonName(uint16_t pos);
      int GetDiskButtonName(uint16_t pos);

   private:
      inline int SendCommand(const char* command);
      inline int SendSetCommand(const char* commandPart1, uint16_t pos, const char* commandPart2);
      inline int SendSetCommand(const char* command, uint16_t pos);
      inline int SendSetCommand(const char* command, uint32_t pos);

      BlockingQueue<std::string> blockingQueue_;
      MM::Device& device_;
      MM::Core& core_;
      DiskoveryModel* model_;
      std::string port_;
      MessageSender* sender_;
};




/**
 * The Listener receives all messages from the Diskovery and stores
 * the results in the Diskovery Model.  It starts a thread that 
 * listens on the serial port
 */
class DiskoveryListener : public MMDeviceThreadBase
{
   public:
      DiskoveryListener(MM::Device& device, MM::Core& core, 
            std::string serialPort, DiskoveryModel* model);
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

      void RegisterSDDevice(DiskoveryStateDev* device) 
         { if (model_ != 0) model_->RegisterSDDevice(device);};
      void RegisterWFDevice(DiskoveryStateDev* device) 
         { if (model_ != 0) model_->RegisterWFDevice(device);};
      void RegisterTIRFDevice(DiskoveryStateDev* device) 
         { if (model_ != 0) model_->RegisterTIRFDevice(device);};
      void RegisterIRISDevice(DiskoveryStateDev* device) 
         { if (model_ != 0) model_->RegisterIRISDevice(device);};
      void RegisterFILTERWDevice(DiskoveryStateDev* device) 
         { if (model_ != 0) model_->RegisterFILTERWDevice(device);};
      void RegisterFILTERTDevice(DiskoveryStateDev* device) 
         { if (model_ != 0) model_->RegisterFILTERTDevice(device);};
      
      // action interface                                                       
      // ----------------                                                       
      int OnPort(MM::PropertyBase* pProp, MM::ActionType eAct); 
      int OnHardwareVersion(MM::PropertyBase* pProp, MM::ActionType eAct); 
      int OnFirmwareVersion(MM::PropertyBase* pProp, MM::ActionType eAct); 
      int OnManufacturingDate(MM::PropertyBase* pProp, MM::ActionType eAct); 
      int OnSerialNumber(MM::PropertyBase* pProp, MM::ActionType eAct); 
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

enum DevType { NONE, SD, WF, TIRF, IRIS, FILTERW, FILTERT };

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
      int OnPositionRot(MM::PropertyBase* pProp, MM::ActionType eAct); 
      int OnPositionLin(MM::PropertyBase* pProp, MM::ActionType eAct); 
      void SignalPropChanged(const char* propName, const char* val) {
         OnPropertyChanged(propName, val);
      }
      int OnWavelength1(MM::PropertyBase* pProp, MM::ActionType eAct);
      int OnWavelength2(MM::PropertyBase* pProp, MM::ActionType eAct);
      int OnTubeLensFocalLength(MM::PropertyBase* pProp, MM::ActionType eAct);
      int OnExitTIRF(MM::PropertyBase* pProp, MM::ActionType eAct);
      int OnDepth(MM::PropertyBase* pProp, MM::ActionType eAct);
      int OnNA(MM::PropertyBase* pProp, MM::ActionType eAct);
      int OnRI(MM::PropertyBase* pProp, MM::ActionType eAct);


   private:
      void calculatePrismPositions(double& lin, double& rot);
      uint16_t numPos_;
      uint16_t firstPos_;
      long wavelength1_, wavelength2_;
      double tubeLensFocalLength_, depth_, na_, ri_;
      bool exitTIRF_;

      std::string devName_;
      DevType devType_;
      bool initialized_;
      DiskoveryHub* hub_;
};



#endif // _Diskovery_H_
