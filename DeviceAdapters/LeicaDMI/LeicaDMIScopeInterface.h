///////////////////////////////////////////////////////////////////////////////
// FILE:       LeicaDMIScopeInterface.h
// PROJECT:    MicroManager
// SUBSYSTEM:  DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION: Interface to the microscope.  Communicates with the scope and updates
//              the abstract model
//   
// COPYRIGHT:     100xImaging, Inc., 2008
// LICENSE:        
//                This library is free software; you can redistribute it and/or
//                modify it under the terms of the GNU Lesser General Public
//                License as published by the Free Software Foundation.
//                
//                You should have received a copy of the GNU Lesser General Public
//                License along with the source distribution; if not, write to
//                the Free Software Foundation, Inc., 59 Temple Place, Suite 330,
//                Boston, MA  02111-1307  USA
//
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.  

#ifndef _LEICASCOPEINTERFACE_H_
#define _LEICASCOPEINTERFACE_H_

#include "LeicaDMIModel.h"
#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"
#include "../../MMDevice/DeviceThreads.h"
#include <string>
#include <vector>
#include <map>

//////////////////////////////////////////////////////////////////////////////
// Error codes
//
class LeicaScopeInterface
{
   friend class LeicaScope;
   friend class LeicaMonitoringThread;

   public:
      LeicaScopeInterface();
      ~LeicaScopeInterface();

      int Initialize(MM::Device& device, MM::Core& core);
      bool IsInitialized() {return initialized_;};

      MM::MMTime GetTimeOutTime(){ return timeOutTime_;}
      void SetTimeOutTime(MM::MMTime timeOutTime) { timeOutTime_ = timeOutTime;}

      // Utility function
      int GetAnswer(MM::Device& device, MM::Core& core, const char* command, std::string& answer);
      int GetStandInfo(MM::Device& device, MM::Core& core);
      int GetILTurretInfo(MM::Device& device, MM::Core& core);
      int GetRevolverInfo(MM::Device& device, MM::Core& core);
      int GetZDriveInfo(MM::Device& device, MM::Core& core);
      int GetDriveInfo(MM::Device& device, MM::Core& core, LeicaDriveModel drive, int deviceID);
      int GetDriveParameters(MM::Device& device, MM::Core& core, int deviceID);
     
      // commands to set individual components
      int SetMethod(MM::Device& device, MM::Core& core, int position);
      int SetTLShutterPosition(MM::Device& device, MM::Core& core, int position);
      int SetILShutterPosition(MM::Device& device, MM::Core& core, int position);
      int SetILTurretPosition(MM::Device& device, MM::Core& core, int position);
      int SetRevolverPosition(MM::Device& device, MM::Core& core, int position);
      int SetDrivePosition(MM::Device& device, MM::Core& core, LeicaDriveModel drive, int deviceID, int position);
      int SetDrivePositionRelative(MM::Device& device, MM::Core& core, LeicaDriveModel drive, int deviceID, int position);
      int SetDriveAcceleration(MM::Device& device, MM::Core& core, LeicaDriveModel drive, int deviceID, int position);
      int SetDriveSpeed(MM::Device& device, MM::Core& core, LeicaDriveModel drive, int deviceID, int speed);
      int HomeDrive(MM::Device& device, MM::Core& core, LeicaDriveModel drive, int deviceID);
      int StopDrive(MM::Device& device, MM::Core& core, LeicaDriveModel drive, int deviceID);

      LeicaMonitoringThread* monitoringThread_;
      LeicaDMIModel* scopeModel_;


      std::string port_;
      bool portInitialized_;
      static const int RCV_BUF_LENGTH = 1024;
      unsigned char rcvBuf_[RCV_BUF_LENGTH];

   private:
      void ClearRcvBuf();
      int ClearPort(MM::Device& device, MM::Core& core);

      MM::MMTime timeOutTime_;
      std::string version_;
      bool initialized_;
};

class LeicaMonitoringThread : public MMDeviceThreadBase
{
   public:
      LeicaMonitoringThread(MM::Device& device, MM::Core& core, std::string port, LeicaDMIModel* scopeModel); 
      ~LeicaMonitoringThread(); 
      //MM_THREAD_FUNC_DECL svc(void *arg);
      int svc();
      int open (void*) { return 0;}
      int close(unsigned long) {return 0;}

      void Start();
      void Stop() {stop_ = true;}
      //void wait() {MM_THREAD_JOIN(thread_);}

   private:
      std::string port_;
      void interpretMessage(unsigned char* message);
      MM::Device& device_;
      MM::Core& core_;
      bool stop_;
      long intervalUs_;
      LeicaDMIModel* scopeModel_;
      LeicaMonitoringThread& operator=(LeicaMonitoringThread& ) {assert(false); return *this;}
};

#endif
