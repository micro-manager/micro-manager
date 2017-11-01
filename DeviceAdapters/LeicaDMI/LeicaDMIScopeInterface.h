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

class LeicaMonitoringThread;

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
	  int GetAFCFocusScore(MM::Device& device, MM::Core& core);
	  int GetAFCMode(MM::Device& device, MM::Core& core);
	  int GetAFCLEDIntensity(MM::Device& device, MM::Core& core);
	  int GetDevicesPresent(MM::Device& device, MM::Core& core);
      int GetILTurretInfo(MM::Device& device, MM::Core& core);
      int GetCondensorInfo(MM::Device& device, MM::Core& core);
      int GetRevolverInfo(MM::Device& device, MM::Core& core);
      int GetZDriveInfo(MM::Device& device, MM::Core& core);
      int GetDriveInfo(MM::Device& device, MM::Core& core, LeicaDriveModel& drive, int deviceID);
      int GetDiaphragmInfo(MM::Device& device, MM::Core& core, LeicaDeviceModel& diaphrahm, int deviceID);
      int GetTLPolarizerInfo(MM::Device& device, MM::Core& core);
      int GetDICTurretInfo(MM::Device& device, MM::Core& core);
      int GetFastFilterWheelInfo(MM::Device& device, MM::Core& core);
      int GetMagChangerInfo(MM::Device& device, MM::Core& core);
      int GetDriveParameters(MM::Device& device, MM::Core& core, int deviceID);
	  int GetTransmittedLightState(MM::Device& device, MM::Core& core, int & position);
      int GetTransmittedLightManual(MM::Device& device, MM::Core& core, int & position);
	  int GetTransmittedLightShutterPosition(MM::Device& device, MM::Core& core, int & position);

		int GetSidePortInfo(MM::Device& device, MM::Core& core);

      // commands to set individual components
      int SetMethod(MM::Device& device, MM::Core& core, int position);
      int SetTLShutterPosition(MM::Device& device, MM::Core& core, int position);
      int SetILShutterPosition(MM::Device& device, MM::Core& core, int position);
      int SetILTurretPosition(MM::Device& device, MM::Core& core, int position);
      int SetFastFilterWheelPosition(MM::Device& device, MM::Core& core, int filterID, int position);
      int SetCondensorPosition(MM::Device& device, MM::Core& core, int position);
      int SetRevolverPosition(MM::Device& device, MM::Core& core, int position);
      int SetObjectiveImmersion(MM::Device& device, MM::Core& core, char method);
      int SetDrivePosition(MM::Device& device, MM::Core& core, LeicaDriveModel& drive, int deviceID, int position);
      int SetDrivePositionRelative(MM::Device& device, MM::Core& core, LeicaDriveModel& drive, int deviceID, int position);
      int SetDriveAcceleration(MM::Device& device, MM::Core& core, LeicaDriveModel& drive, int deviceID, int position);
      int SetDriveSpeed(MM::Device& device, MM::Core& core, LeicaDriveModel& drive, int deviceID, int speed);
      int HomeDrive(MM::Device& device, MM::Core& core, LeicaDriveModel& drive, int deviceID);
      int StopDrive(MM::Device& device, MM::Core& core, LeicaDriveModel& drive, int deviceID);
      int SetDiaphragmPosition(MM::Device& device, MM::Core& core, LeicaDeviceModel* diaphragm, int deviceID, int position);
      int SetMagChangerPosition(MM::Device& device, MM::Core& core, int position);
      int SetTLPolarizerPosition(MM::Device& device, MM::Core& core, int position);
      int SetDICPrismTurretPosition(MM::Device& device, MM::Core& core, int position);
      int SetDICPrismFinePosition(MM::Device& device, MM::Core& core, int position);
	  int SetTransmittedLightState(MM::Device& device, MM::Core& core, int position);
      int SetTransmittedLightManual(MM::Device& device, MM::Core& core, int position);
	  int SetTransmittedLightShutterPosition(MM::Device& device, MM::Core& core, int position);
      int SetAFCMode(MM::Device& device, MM::Core& core, bool on);
      int SetAFCOffset(MM::Device &device, MM::Core &core, double offset);
      int SetAFCDichroicMirrorPosition(MM::Device &device, MM::Core &core, int position);
	  int SetAFCLEDIntensity(MM::Device &device, MM::Core &core, int intensity);
	  int SetSidePortPosition(MM::Device& device, MM::Core& core, int position);

      bool portInitialized_;
      LeicaMonitoringThread* monitoringThread_;
      LeicaDMIModel* scopeModel_;

      std::string port_;
      static const int RCV_BUF_LENGTH = 1024;
      unsigned char rcvBuf_[RCV_BUF_LENGTH];

   private:
      int standFamily_;
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
      int svc();
      int open (void*) { return 0;}
      int close(unsigned long) {return 0;}

      void Start();
      void Stop() {stop_ = true;}

   private:
      std::string port_;
      MM::Device& device_;
      MM::Core& core_;
      bool stop_;
      long intervalUs_;
      LeicaDMIModel* scopeModel_;
      int standFamily_;
      LeicaMonitoringThread& operator=(LeicaMonitoringThread& ) {assert(false); return *this;}
};

#endif
