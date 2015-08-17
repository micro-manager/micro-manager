/////////////////////////////////////////////////////////////////////////////
// FILE:       DiskoveryModel.cpp
// PROJECT:    MicroManage
// SUBSYSTEM:  DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:
// Andor/Spectral Diskovery Device adapter
//                
// AUTHOR: Nico Stuurman, 06/31/2015
//
// COPYRIGHT:  Regents of the University of California, 2015
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


#ifdef WIN32
   #include <windows.h>
   #define snprintf _snprintf 
#endif


#include "DiskoveryModel.h"
#include "Diskovery.h"

// Motor Running
void DiskoveryModel::SetMotorRunningSD(const bool p)
{
   MMThreadGuard g(lock_);
   motorRunningSD_ = p;
   std::string s = static_cast<std::ostringstream*>( &(std::ostringstream() << p) )->str();
   core_.OnPropertyChanged(hubDevice_, motorRunningProp_, s.c_str());
}

// Preset SD
void DiskoveryModel::SetPresetSD(const uint16_t p)
{
   MMThreadGuard g(lock_);
   presetSD_ = p;
   if (sdDevice_ != 0)
   {
      sdDevice_->OnStateChanged(p - 1);
   }
}

void DiskoveryModel::SetPresetWF(const uint16_t p)
{
   MMThreadGuard g(lock_);
   presetWF_ = p;
   if (wfDevice_ != 0)
   {
      wfDevice_->OnStateChanged(p - 1);
   }                                                                   
}

// Preset Iris                                                         
void DiskoveryModel::SetPresetIris(const uint16_t p)                         
{
   MMThreadGuard g(lock_);
   presetIris_ = p;
   if (irisDevice_ != 0)
   {
      irisDevice_->OnStateChanged(p - 1);
   }                                                                   
}

// Preset TIRF                                                         
void DiskoveryModel::SetPresetTIRF(const uint16_t p)                         
{
   MMThreadGuard g(lock_);
   presetPX_ = p;
   if (tirfDevice_ != 0)
   {
      tirfDevice_->OnStateChanged(p);
   }
}

// Preset Filter W
void DiskoveryModel::SetPresetFilterW(const uint16_t p) 
{  
   MMThreadGuard g(lock_);
   presetFilterW_ = p;
   if (filterWDevice_ != 0)
   {
      filterWDevice_->OnStateChanged(p - 1);
   }                                                                   
}

// Preset Filter T                                                     
void DiskoveryModel::SetPresetFilterT(const uint16_t p) 
{ 
   MMThreadGuard g(lock_);
   presetFilterT_ = p;                                       
   if (filterTDevice_ != 0)
   {
      filterTDevice_->OnStateChanged(p - 1);
   }                                                                   
}

// TIRF slider Rot
void DiskoveryModel::SetPositionRot(const uint32_t p)
{
   MMThreadGuard g(lock_);
   tirfRotPos_ = p;
}

// TIRF slider Lin
void DiskoveryModel::SetPositionLin(const uint32_t p)
{
   MMThreadGuard g(lock_);
   tirfLinPos_ = p;
}
