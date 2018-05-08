///////////////////////////////////////////////////////////////////////////////
// FILE:       ZeissModel.cpp
// PROJECT:    MicroManager
// SUBSYSTEM:  DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION: Model for state of Colibri and Definite Focus
//                
// AUTHOR: Nico Stuurman, 6/30/2009 
//
// COPYRIGHT:     University of California, San Francisco, 2007, 2008, 2009
// LICENSE:       Please note: This code could only be developed thanks to information 
//                provided by Zeiss under a non-disclosure agreement.  Subsequently, 
//                this code has been reviewed by Zeiss and we were permitted to release 
//                this under the LGPL on 1/16/2008 (permission re-granted on 7/3/2008, 7/1/2009//                after changes to the code).
//                If you modify this code using information you obtained 
//                under a NDA with Zeiss, you will need to ask Zeiss whether you can release 
//                your modifications. 
//                
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

#include "ZeissCAN29.h"

/**
 * DefiniteFocusModel
 */
DefiniteFocusModel::DefiniteFocusModel() :
   controlOnOff_ (0),
   period_ (0),
   status_(0),
   error_(0),
   deviation_(0),
   dataLength_(0),
   brightnessLED_(0),
   shutterFactor_(0),
   waitForStabilizationData_(false),
   busy_(false)
{};

int DefiniteFocusModel::GetControlOnOff(ZeissByte& controlOnOff) 
{
   MMThreadGuard(this->dfLock_); 
   controlOnOff = controlOnOff_; 
   return DEVICE_OK;
} 

int DefiniteFocusModel::SetControlOnOff(ZeissByte controlOnOff) 
{
   MMThreadGuard(this->dfLock_); 
   controlOnOff_ = controlOnOff; 
   return DEVICE_OK;
}

int DefiniteFocusModel::GetPeriod(ZeissULong& period) 
{
   MMThreadGuard(this->dfLock_); 
   period = period_; 
   return DEVICE_OK;
} 

int DefiniteFocusModel::SetPeriod(ZeissULong period) 
{
   MMThreadGuard(this->dfLock_); 
   period_ = period; 
   return DEVICE_OK;
} 

int DefiniteFocusModel::GetStatus(ZeissUShort& status) 
{
   MMThreadGuard(this->dfLock_); 
   status = status_; 
   return DEVICE_OK;
} 

int DefiniteFocusModel::SetStatus(ZeissUShort status) 
{
   MMThreadGuard(this->dfLock_); 
   status_ = status; 
   return DEVICE_OK;
} 

int DefiniteFocusModel::GetVersion(ZeissUShort& version) 
{
   MMThreadGuard(this->dfLock_); 
   version = version_; 
   return DEVICE_OK;
} 

int DefiniteFocusModel::SetVersion(ZeissUShort version) 
{
   MMThreadGuard(this->dfLock_); 
   version_ = version; 
   return DEVICE_OK;
} 

int DefiniteFocusModel::GetError(ZeissUShort& error) 
{
   MMThreadGuard(this->dfLock_); 
   error = error_;
   return DEVICE_OK;
} 

int DefiniteFocusModel::SetError(ZeissUShort error) 
{
   MMThreadGuard(this->dfLock_); 
   error_ = error;
   return DEVICE_OK;
} 

int DefiniteFocusModel::GetDeviation(ZeissLong& deviation) 
{
   MMThreadGuard(this->dfLock_); 
   deviation = deviation_;
   return DEVICE_OK;
} 

int DefiniteFocusModel::SetDeviation(ZeissLong deviation) 
{
   MMThreadGuard(this->dfLock_); 
   deviation_ = deviation;
   return DEVICE_OK;
} 


DFOffset DefiniteFocusModel::GetData() 
{
   MMThreadGuard(this->dfLock_); 
   DFOffset* dt = new DFOffset(dataLength_);
   for (unsigned char i=0; i < dataLength_; i++) {
      dt->data_[i] = data_[i];
   }
   return *dt;
} 

int DefiniteFocusModel::SetData(ZeissUByte dataLength, ZeissUByte* data) 
{
   MMThreadGuard(this->dfLock_); 
   dataLength_ = dataLength;
   for (unsigned char i=0; i < dataLength_; i++) {
      data_[i] = data[i];
   }
   return DEVICE_OK;
} 

int DefiniteFocusModel::GetBrightnessLED(ZeissByte& brightness) 
{
   MMThreadGuard(this->dfLock_); 
   brightness = brightnessLED_;
   return DEVICE_OK;
}

int DefiniteFocusModel::SetBrightnessLED(ZeissByte brightness) 
{
   MMThreadGuard(this->dfLock_); 
   brightnessLED_ = brightness;
   return DEVICE_OK;
}

int DefiniteFocusModel::GetWorkingPosition(double& workingPosition) 
{
   MMThreadGuard(this->dfLock_); 
   workingPosition = workingPosition_;
   return DEVICE_OK;
}

int DefiniteFocusModel::SetWorkingPosition(double workingPosition) 
{
   MMThreadGuard(this->dfLock_); 
   workingPosition_ = workingPosition;
   return DEVICE_OK;
}

int DefiniteFocusModel::GetShutterFactor(ZeissShort& shutterFactor)
{
   MMThreadGuard(this->dfLock_); 
   shutterFactor = shutterFactor_;
   return DEVICE_OK;
}

int DefiniteFocusModel::SetShutterFactor(ZeissShort shutterFactor)
{
   MMThreadGuard(this->dfLock_); 
   shutterFactor_ = shutterFactor;
   return DEVICE_OK;
}

int DefiniteFocusModel::GetBusy(bool& busy) {
   MMThreadGuard(this->dfLock_);
   busy = busy_;
   return DEVICE_OK;
}

int DefiniteFocusModel::SetBusy(bool busy) {
   MMThreadGuard(this->dfLock_);
   busy_ = busy;
   return DEVICE_OK;
}

bool DefiniteFocusModel::GetWaitForStabilizationData() {
   MMThreadGuard(this->dfLock_);
   return waitForStabilizationData_;
}

int DefiniteFocusModel::SetWaitForStabilizationData(bool state) {
   MMThreadGuard(this->dfLock_);
   waitForStabilizationData_ = state;
   return DEVICE_OK;
}

/**
 * ColibriModel
 */
ColibriModel::ColibriModel() :
   operationMode_(1),
   busyExternal_(false)
{
   // Note: this value is currently 1023 in all systems.
   // It might be implemented in the future by Zeiss and then should not be hard-coded
   // here anymore
   for (int i=0; i< NRLEDS; i++) {
      calibrationValue_[i] = 1023;
      available_[i] = false;
      brightness_[i] = 0;
      busy_[i] = false;
   }
}

int ColibriModel::GetStatus(ZeissULong& status) {
   MMThreadGuard(this->dfLock_); 
   status = status_; 
   return DEVICE_OK;
}

int ColibriModel::SetStatus(ZeissULong status) {
   MMThreadGuard(this->dfLock_); 
   status_ = status; 
   return DEVICE_OK;
}

ZeissShort ColibriModel::GetBrightness(int ledNr) {
   MMThreadGuard(this->dfLock_); 
   return brightness_[ledNr];
}

ZeissShort ColibriModel::GetCalibrationValue(int ledNr) {
   return calibrationValue_[ledNr];
}

std::string ColibriModel::GetName(int ledNr) {
   MMThreadGuard(this->dfLock_); 
   return info_[ledNr].name_;
}

std::string ColibriModel::GetInfo(int ledNr) {
   MMThreadGuard(this->dfLock_); 
   std::ostringstream os;
   os << info_[ledNr].wavelengthNm_ << "nm " << (unsigned char) 177 << info_[ledNr].halfPowerBandwidth_ << "nm, " << info_[ledNr].nominalCurrent_ << "mA";
   return os.str();
}

int ColibriModel::SetBrightness(int ledNr, ZeissShort brightness) {
   MMThreadGuard(this->dfLock_); 
   brightness_[ledNr] = brightness;
   return DEVICE_OK;
}

bool ColibriModel::GetOpen() {
   MMThreadGuard(this->dfLock_);
   if (operationMode_ == 1) {
      for (int i=0; i<NRLEDS; i++) {
         if (available_[i])
            if (onOff_[i] == 2)
               return true;
      }
   } else if (operationMode_ == 4) {
      return externalShutterState_ == 2;
   }
   return false;
}

ZeissByte ColibriModel::GetExternalShutterState() {
   MMThreadGuard(this->dfLock_);
   return externalShutterState_;
}

void ColibriModel::SetExternalShutterState(ZeissByte externalShutterState) {
   MMThreadGuard(this->dfLock_);
   externalShutterState_ = externalShutterState;
}


int ColibriModel::GetOnOff(int ledNr) {
   MMThreadGuard(this->dfLock_);
   return onOff_[ledNr];
}

int ColibriModel::SetOnOff(int ledNr, ZeissByte onOff) {
   MMThreadGuard(this->dfLock_);
   onOff_[ledNr] = onOff;
   return DEVICE_OK;
}

bool ColibriModel::GetBusy() {
   MMThreadGuard(this->dfLock_);
   bool busy = false;
   for (int i=0; i < NRLEDS; i++)
      if (busy_[i])
         busy = true;
   if (busyExternal_)
      busy = true;
   if (operationMode_ == 0)
      busy = true;
   return busy;
}

bool ColibriModel::GetBusy(int ledNr) {
   MMThreadGuard(this->dfLock_);
   return busy_[ledNr];
}

int ColibriModel::SetBusy(int ledNr, bool busy) {
   MMThreadGuard(this->dfLock_);
   busy_[ledNr] = busy;
   return DEVICE_OK;
}

int ColibriModel::SetBusyExternal(bool busy) {
   MMThreadGuard(this->dfLock_);
   busyExternal_ = busy;
   return DEVICE_OK;
}

LEDInfo ColibriModel::GetLEDInfo(int ledNr) {
   return info_[ledNr];
}

ZeissByte ColibriModel::GetMode() {
   MMThreadGuard(this->dfLock_);
   return operationMode_;
}

void ColibriModel::SetMode (ZeissByte mode) {
   MMThreadGuard(this->dfLock_);
   operationMode_ = mode;
}
