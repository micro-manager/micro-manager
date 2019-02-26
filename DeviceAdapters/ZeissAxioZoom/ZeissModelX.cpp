///////////////////////////////////////////////////////////////////////////////
// FILE:          ZeissModelX.cpp
// PROJECT:       MicroManager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Models for AxioZoom devices
//                
// AUTHOR:        Nenad Amodaj, 2014
//
// COPYRIGHT:     Luminous Point LLC
//
//

#include "ZeissAxioZoom.h"

MMThreadLock MotorFocusModel::mfLock_;
MMThreadLock StageModel::sLock_;
MMThreadLock OpticsUnitModel::ouLock_;
MMThreadLock FluoTubeModel::ftLock_;
MMThreadLock DL450Model::dlLock_;

/**
 * Motor Focus Model
 */
MotorFocusModel::MotorFocusModel() :
   state_(0), waitingForAnswer(false)
{
  
}

int MotorFocusModel::GetPosition(ZeissLong& pos) 
{
   MMThreadGuard(this->mfLock_); 
   pos = position_;
   return DEVICE_OK;
}

/**
 * This does not actually set device position, just updates the model
 */
int MotorFocusModel::SetPosition(ZeissLong pos) 
{
   MMThreadGuard(this->mfLock_); 
   position_ = pos;
   return DEVICE_OK;
}

int MotorFocusModel::GetBusy(bool& busy)
{
   MMThreadGuard(this->mfLock_);
   busy = (state_ & 0x1F) != 0;
   return DEVICE_OK;
}

int MotorFocusModel::GetInitialized(bool& init)
{
   MMThreadGuard(this->mfLock_);
   init = (state_ & 0x80) != 0;
   return DEVICE_OK;
}


int MotorFocusModel::GetState(ZeissUByte& state) 
{
   MMThreadGuard(this->mfLock_); 
   state = state_; 
   return DEVICE_OK;
} 

int MotorFocusModel::SetState(ZeissUByte state) 
{
   MMThreadGuard(this->mfLock_); 
   state_ = state; 
   return DEVICE_OK;
}

void MotorFocusModel::MakeBusy()
{
   state_ |= 0x02;
}

void MotorFocusModel::setWaiting(bool state)
{
   waitingForAnswer = state;
}

/**
 * STAGE Model
 */
StageModel::StageModel() :
   state_(0), waitingForAnswer(false)
{
   
}

int StageModel::GetPosition(ZeissLong& pos) 
{
   MMThreadGuard(this->sLock_); 
   pos = position_;
   return DEVICE_OK;
}

/**
 * This does not actually set device position, just updates the model
 */
int StageModel::SetPosition(ZeissLong pos) 
{
   MMThreadGuard(this->sLock_); 
   position_ = pos;
   return DEVICE_OK;
}

int StageModel::GetBusy(bool& busy)
{
   MMThreadGuard(this->sLock_);
   busy = (state_ & 0x0004) != 0;
   return DEVICE_OK;
}

int StageModel::GetInitialized(bool& init)
{
   MMThreadGuard(this->sLock_);
   init = (state_ & 0x80) != 0;
   return DEVICE_OK;
}


int StageModel::GetState(ZeissULong& state) 
{
   MMThreadGuard(this->sLock_); 
   state = state_; 
   return DEVICE_OK;
} 

int StageModel::SetState(ZeissULong state) 
{
   MMThreadGuard(this->sLock_); 
   state_ = state; 
   return DEVICE_OK;
}

void StageModel::MakeBusy()
{
   state_ |= 0x0004;
}

void StageModel::setWaiting(bool state)
{
   waitingForAnswer = state;
}

/**
 * Optics Unit Model
 */
OpticsUnitModel::OpticsUnitModel() :
   state(0), waitingForAnswer(false)
{
  
}

int OpticsUnitModel::GetZoomLevel(ZeissUShort& level) 
{
   MMThreadGuard(this->ouLock_); 
   level = zoomLevel;
   return DEVICE_OK;
}

/**
 * This does not actually set device position, just updates the model
 */
int OpticsUnitModel::SetZoomLevel(ZeissUShort level) 
{
   MMThreadGuard(this->ouLock_); 
   zoomLevel = level;
   return DEVICE_OK;
}

int OpticsUnitModel::GetAperture(ZeissByte& a) 
{
   MMThreadGuard(this->ouLock_); 
   a = aperture;
   return DEVICE_OK;
}

int OpticsUnitModel::SetAperture(ZeissByte a) 
{
   MMThreadGuard(this->ouLock_); 
   aperture = a;
   return DEVICE_OK;
}

int OpticsUnitModel::GetBusy(bool& busy)
{
   MMThreadGuard(this->ouLock_);
   busy = (state & 0x3F) != 0;
   return DEVICE_OK;
}

int OpticsUnitModel::GetInitialized(bool& init)
{
   MMThreadGuard(this->ouLock_);
   init = (state & 0x40) != 0;
   return DEVICE_OK;
}


int OpticsUnitModel::GetState(ZeissUByte& s) 
{
   MMThreadGuard(this->ouLock_); 
   s = state; 
   return DEVICE_OK;
} 

int OpticsUnitModel::SetState(ZeissUByte s) 
{
   MMThreadGuard(this->ouLock_); 
   state = s; 
   return DEVICE_OK;
}

void OpticsUnitModel::MakeBusy()
{
   state |= 0x02;
}

void OpticsUnitModel::setWaiting(bool state)
{
   waitingForAnswer = state;
}

/**
 * FLUO TUBE MODEL
 */
FluoTubeModel::FluoTubeModel() :
   state(0), waitingForAnswer(false)
{
  
}

int FluoTubeModel::GetPosition(ZeissUShort& a) 
{
   MMThreadGuard(this->ftLock_); 
   a = position;
   return DEVICE_OK;
}

int FluoTubeModel::SetPosition(ZeissUShort a) 
{
   MMThreadGuard(this->ftLock_); 
   position = a;
   return DEVICE_OK;
}

int FluoTubeModel::GetShutterPosition(ZeissUShort& a) 
{
   MMThreadGuard(this->ftLock_); 
   a = shutterPos;
   return DEVICE_OK;
}

int FluoTubeModel::SetShutterPosition(ZeissUShort a) 
{
   MMThreadGuard(this->ftLock_); 
   shutterPos = a;
   return DEVICE_OK;
}

int FluoTubeModel::GetBusy(bool& busy)
{
   MMThreadGuard(this->ftLock_);
   busy = (state & 0x0400) != 0;
   return DEVICE_OK;
}

int FluoTubeModel::GetShutterBusy(bool& busy)
{
   MMThreadGuard(this->ftLock_);
   busy = (shutterState & 0x0400) != 0;
   return DEVICE_OK;
}


int FluoTubeModel::GetInitialized(bool& init)
{
   MMThreadGuard(this->ftLock_);
   init = (state & 0x0200) != 0;
   return DEVICE_OK;
}


int FluoTubeModel::GetState(ZeissUShort& s) 
{
   MMThreadGuard(this->ftLock_); 
   s = state; 
   return DEVICE_OK;
} 

int FluoTubeModel::SetState(ZeissUShort s) 
{
   MMThreadGuard(this->ftLock_); 
   state = s; 
   return DEVICE_OK;
}

int FluoTubeModel::GetShutterState(ZeissUShort& s) 
{
   MMThreadGuard(this->ftLock_); 
   s = shutterState; 
   return DEVICE_OK;
} 

int FluoTubeModel::SetShutterState(ZeissUShort s) 
{
   MMThreadGuard(this->ftLock_); 
   shutterState = s; 
   return DEVICE_OK;
}
void FluoTubeModel::MakeBusy()
{
   state |= 0x0400;
}

void FluoTubeModel::setWaiting(bool state)
{
   waitingForAnswer = state;
}

/**
 * DL450 MODEL
 */
DL450Model::DL450Model() :
   state(0), waitingForAnswer(false)
{
  
}

int DL450Model::GetPosition(ZeissUShort& a) 
{
   MMThreadGuard(this->dlLock_); 
   a = position;
   return DEVICE_OK;
}

int DL450Model::SetPosition(ZeissUShort a) 
{
   MMThreadGuard(this->dlLock_); 
   position = a;
   return DEVICE_OK;
}

int DL450Model::GetBusy(bool& busy)
{
   MMThreadGuard(this->dlLock_);
   busy = (state & 0x0400) != 0;
   busy = false;
   return DEVICE_OK;
}

int DL450Model::GetInitialized(bool& init)
{
   MMThreadGuard(this->dlLock_);
   init = (state & 0x0200) != 0;
   return DEVICE_OK;
}

int DL450Model::GetState(ZeissUShort& s) 
{
   MMThreadGuard(this->dlLock_); 
   s = state; 
   return DEVICE_OK;
} 

int DL450Model::SetState(ZeissUShort s) 
{
   MMThreadGuard(this->dlLock_); 
   state = s; 
   return DEVICE_OK;
}

void DL450Model::MakeBusy()
{
   state |= 0x0400;
}

void DL450Model::setWaiting(bool state)
{
   waitingForAnswer = state;
}
