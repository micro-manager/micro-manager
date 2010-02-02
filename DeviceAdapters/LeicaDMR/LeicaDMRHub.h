///////////////////////////////////////////////////////////////////////////////
// FILE:          LeicaDMRHub.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   LeicaDMR hub module. Required for operation of all 
//                LeicaDMR devices
//                
// COPYRIGHT:     University of California, San Francisco, 2006
//                100xImaging, Inc.  2009
//                All rights reserved
//
// LICENSE:       This library is free software; you can redistribute it and/or
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
//                                                                                     
// AUTHOR: Nico Stuurman, nico@cmp.ucsf.edu, 07/02/2009                                                
// 
//
#ifndef _LeicaDMRHUB_H_
#define _LeicaDMRHUB_H_

#include "../../MMDevice/MMDevice.h"


class LeicaDMRHub
{
public:
   LeicaDMRHub();
   ~LeicaDMRHub();

   void SetPort(const char* port) {port_ = port;}
   int Initialize(MM::Device& device, MM::Core& core);
   int DeInitialize() {initialized_ = false; return DEVICE_OK;};
   bool Initialized() {return initialized_;};
   std::string Version() {return version_;};
   std::string Microscope() {return microscope_;};

   int GetLampIntensity(MM::Device& device, MM::Core& core, int& intensity);
   int SetLampIntensity(MM::Device& device, MM::Core& core, int intensity);
   bool LampPresent() {return present_[lamp_];};

   int SetRLModulePosition(MM::Device& device, MM::Core& core, int pos);
   int GetRLModulePosition(MM::Device& device, MM::Core& core, int& pos);
   int GetRLModuleNumberOfPositions(MM::Device& device, MM::Core& core, int& nrPos);
   int RLModulePresent() {return present_[rLFA4_] || present_[rLFA8_];};

   int SetRLShutter(MM::Device& device, MM::Core& core, bool open);
   int GetRLShutter(MM::Device& device, MM::Core& core, bool& open);
   bool RLShutterPresent() {return present_[rLFA4_] || present_[rLFA8_];};

   int SetZAbs(MM::Device& device, MM::Core& core, long position);
   int SetZRel(MM::Device& device, MM::Core& core, long position);
   int MoveZConst(MM::Device& device, MM::Core& core, int speed);
   int StopZ(MM::Device& device, MM::Core& core);
   int GetZAbs(MM::Device& device, MM::Core& core, long& position);
   int GetZUpperThreshold(MM::Device& device, MM::Core& core, long& position);
   bool ZDrivePresent() {return present_[zDrive_];};

   int SetObjNosepiecePosition(MM::Device& device, MM::Core& core, int pos);
   int GetObjNosepiecePosition(MM::Device& device, MM::Core& core, int& pos);
   int SetObjNosepieceImmMode(MM::Device& device, MM::Core& core, int mode);
   int GetObjNosepieceImmMode(MM::Device& device, MM::Core& core, int& mode);
   int SetObjNosepieceRotationMode(MM::Device& device, MM::Core& core, int mode);
   int GetObjNosepieceRotationMode(MM::Device& device, MM::Core& core, int& mode);
   int GetObjNosepieceNumberOfPositions(MM::Device& device, MM::Core& core, int& nrPos);
   bool ObjNosepiecePresent() {return present_[objNosepiece_];};

   int SetApertureDiaphragmPosition(MM::Device& device, MM::Core& core, int pos);
   int GetApertureDiaphragmPosition(MM::Device& device, MM::Core& core, int& pos);
   int BreakApertureDiaphragm(MM::Device& device, MM::Core& core);
   bool ApertureDiaphragmPresent() {return present_[aDia_];};

   int SetFieldDiaphragmPosition(MM::Device& device, MM::Core& core, int pos);
   int GetFieldDiaphragmPosition(MM::Device& device, MM::Core& core, int& pos);
   int SetCondensorPosition(MM::Device& device, MM::Core& core, int pos);
   int GetCondensorPosition(MM::Device& device, MM::Core& core, int& pos);
   int BreakFieldDiaphragm(MM::Device& device, MM::Core& core);
   bool FieldDiaphragmPresent() {return present_[fDia_];};

private:
   int GetVersion(MM::Device& device, MM::Core& core, std::string& version);
   int GetMicroscope(MM::Device& device, MM::Core& core, std::string& microscope);
   int GetPresence(MM::Device& device, MM::Core& core, int deviceId);
   void ClearRcvBuf();
   void ClearAllRcvBuf(MM::Device& device, MM::Core& core);
   int GetCommand(MM::Device& device, MM::Core& core, int deviceId, int command, std::string& answer);
   int GetCommand(MM::Device& device, MM::Core& core, int deviceId, int command, int& answer);
   int SetCommand(MM::Device& device, MM::Core& core, int deviceId, int command, int data);
   int SetCommand(MM::Device& device, MM::Core& core, int deviceId, int command);

   // IDs of devices in the microscope
   static const int gMic_ = 50;
   static const int lamp_ = 68;
   static const int rLFA4_ = 67;
   static const int rLFA8_ = 12;
   static const int zDrive_ = 60;
   static const int objNosepiece_ = 63;
   static const int aDia_ = 64;
   static const int fDia_ = 65;
   static const int iCPrismTurret_ = 66;
   static const int xYStage_ = 10;

   static const int nrDevices_ = 10;
   static const int maxNrDevices_ = 100;
   bool present_[maxNrDevices_];
   int rLFA_;

   static const int RCV_BUF_LENGTH = 1024;
   char rcvBuf_[RCV_BUF_LENGTH];

   std::string port_;
   std::string version_;
   std::string microscope_;
   bool initialized_;
   long expireTimeUs_;
};

#endif // _LeicaDMRHUB_H_
