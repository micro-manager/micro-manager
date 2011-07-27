///////////////////////////////////////////////////////////////////////////////
// FILE:       LeicaDMIModel.cpp
// PROJECT:    MicroManager
// SUBSYSTEM:  DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:
//
// COPYRIGHT:     100xImaging, Inc. 2008
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



#ifdef WIN32
#include <windows.h>
#define snprintf _snprintf 
#else
#include <netinet/in.h>
#endif

#include "LeicaDMIModel.h"
#include "../../MMDevice/ModuleInterface.h"

//MM_THREAD_GUARD mutex;

LeicaDeviceModel::LeicaDeviceModel() :
   position_(1),
   minPosition_(0),
   maxPosition_(1),
	busy_(false)
{
   MM_THREAD_INITIALIZE_GUARD(&mutex_);
}

LeicaDeviceModel::~LeicaDeviceModel()
{
   MM_THREAD_DELETE_GUARD(&mutex_);
}

int LeicaDeviceModel::SetPosition(int position)
{
   MM_THREAD_GUARD_LOCK(&mutex_);
   position_ = position;
   MM_THREAD_GUARD_UNLOCK(&mutex_);
   return DEVICE_OK;
}

int LeicaDeviceModel::GetPosition(int& position)
{
   MM_THREAD_GUARD_LOCK(&mutex_);
   position = position_;
   MM_THREAD_GUARD_UNLOCK(&mutex_);
   return DEVICE_OK;
}

int LeicaDeviceModel::SetBusy(bool busy)
{
   MM_THREAD_GUARD_LOCK(&mutex_);
   busy_ = busy;
   MM_THREAD_GUARD_UNLOCK(&mutex_);
   return DEVICE_OK;
}

int LeicaDeviceModel::GetBusy(bool& busy)
{
   MM_THREAD_GUARD_LOCK(&mutex_);
   busy = busy_;
   MM_THREAD_GUARD_UNLOCK(&mutex_);
   return DEVICE_OK;
}

/*
 * Holds information about individual reflector cubes
 */
LeicaCubeModel::LeicaCubeModel() :
   cubeMethods_(LeicaDMIModel::maxNrMethods_ + 1, false)
{
}

bool LeicaCubeModel::IsMethodAvailable(int methodId)
{
   return cubeMethods_[methodId];
}

/*
 * IL model. Inherits from LeicaDeviceModel
 */
LeicaILTurretModel::LeicaILTurretModel() :
   LeicaDeviceModel(),
   cube_(maxNrCubes_ + 1)
{
   position_ = 1;
}

/*
 * Condensor model. 
 */
LeicaCondensorModel::LeicaCondensorModel() :
   LeicaDeviceModel(),
   filter_(maxNrFilters_ + 1)
{
   position_ = 1;
}

/*
 * Holds info about individual objectives
 */
/*
 * Holds info about individual objectives
 */
LeicaObjectiveModel::LeicaObjectiveModel() :
   methods_(LeicaDMIModel::maxNrMethods_, false)
{
}

bool LeicaObjectiveModel::IsMethodAvailable(int methodId)
{
   return methods_[methodId];
}

/*
 * ObjectiveTurret model. Inherits from LeicaDeviceModel
 */
LeicaObjectiveTurretModel::LeicaObjectiveTurretModel() :
   LeicaDeviceModel(),
   objective_(maxNrObjectives_ + 1)
{
   position_ = 1;
}


/*
 * DIC Turret model
 */
LeicaDICPrismTurretModel::LeicaDICPrismTurretModel() :
   LeicaDeviceModel(),
   motorized_(false),
   prismName_(maxNrPrisms_ + 1)
{
   position_ = 1;
}

int LeicaDICPrismTurretModel::GetFinePosition(int& finePosition)
{
   MM_THREAD_GUARD_LOCK(&mutex_);
   finePosition = finePosition_;
   MM_THREAD_GUARD_UNLOCK(&mutex_);
   return DEVICE_OK;
}

int LeicaDICPrismTurretModel::SetFinePosition(int finePosition)
{
   MM_THREAD_GUARD_LOCK(&mutex_);
   finePosition_ = finePosition;
   MM_THREAD_GUARD_UNLOCK(&mutex_);
   return DEVICE_OK;
}

/*
 * Drive Model
 */
LeicaDriveModel::LeicaDriveModel()  :
   minRamp_ (1),
   maxRamp_ (800),
   minSpeed_ (1),
   maxSpeed_ (16777216)
{
}

int LeicaDriveModel::GetRamp(int& ramp)
{
   MM_THREAD_GUARD_LOCK(&mutex_);
   ramp = ramp_;
   MM_THREAD_GUARD_UNLOCK(&mutex_);
   return DEVICE_OK;
}

int LeicaDriveModel::SetRamp(int ramp)
{
   MM_THREAD_GUARD_LOCK(&mutex_);
   ramp_ = ramp;
   MM_THREAD_GUARD_UNLOCK(&mutex_);
   return DEVICE_OK;
}

int LeicaDriveModel::GetSpeed(int& speed)
{
   MM_THREAD_GUARD_LOCK(&mutex_);
   speed = speed_;
   MM_THREAD_GUARD_UNLOCK(&mutex_);
   return DEVICE_OK;
}

int LeicaDriveModel::SetSpeed(int speed)
{
   MM_THREAD_GUARD_LOCK(&mutex_);
   speed_ = speed;
   MM_THREAD_GUARD_UNLOCK(&mutex_);
   return DEVICE_OK;
}

int LeicaDriveModel::GetPosFocus(int& posFocus)
{
   MM_THREAD_GUARD_LOCK(&mutex_);
   posFocus = posFocus_;
   MM_THREAD_GUARD_UNLOCK(&mutex_);
   return DEVICE_OK;
}

int LeicaDriveModel::SetPosFocus(int posFocus)
{
   MM_THREAD_GUARD_LOCK(&mutex_);
   posFocus_ = posFocus;
   MM_THREAD_GUARD_UNLOCK(&mutex_);
   return DEVICE_OK;
}

/**
 * Leica Motorized Magnification changer.
 */
LeicaMagChangerModel::LeicaMagChangerModel() :
   magnification_(maxMags_ + 1, 0)
{
}

int LeicaMagChangerModel::GetMagnification(int pos, double& mag)
{
   MM_THREAD_GUARD_LOCK(&mutex_);
   mag = magnification_.at(pos);
   MM_THREAD_GUARD_UNLOCK(&mutex_);
   return DEVICE_OK;
}

int LeicaMagChangerModel::SetMagnification(int pos, double mag)
{
   MM_THREAD_GUARD_LOCK(&mutex_);
   magnification_[pos] = mag;
   MM_THREAD_GUARD_UNLOCK(&mutex_);
   return DEVICE_OK;
}

/**
 * Leica AFC (Hardware autofocus)
 */
LeicaAFCModel::LeicaAFCModel() :
   offset_(0.0)
{
}

int LeicaAFCModel::GetOffset(double& offset)
{
   MM_THREAD_GUARD_LOCK(&mutex_);
   offset = offset_;
   MM_THREAD_GUARD_UNLOCK(&mutex_);
   return DEVICE_OK;
}

int LeicaAFCModel::SetOffset(double offset)
{
   MM_THREAD_GUARD_LOCK(&mutex_);
   offset_ = offset;
   MM_THREAD_GUARD_UNLOCK(&mutex_);
   return DEVICE_OK;
}

int LeicaAFCModel::GetMode(bool& on)
{
   MM_THREAD_GUARD_LOCK(&mutex_);
   on = mode_;
   MM_THREAD_GUARD_UNLOCK(&mutex_);
   return DEVICE_OK;
}

int LeicaAFCModel::SetMode(bool on)
{
   MM_THREAD_GUARD_LOCK(&mutex_);
   mode_ = on;
   MM_THREAD_GUARD_UNLOCK(&mutex_);
   return DEVICE_OK;
}

int LeicaAFCModel::GetLEDColors(int& topColor, int& bottomColor)
{
   MM_THREAD_GUARD_LOCK(&mutex_);
   topColor = topLEDColor_;
   bottomColor = bottomLEDColor_;
   MM_THREAD_GUARD_UNLOCK(&mutex_);
   return DEVICE_OK;
}

int LeicaAFCModel::SetLEDColors(int topColor, int bottomColor)
{
   MM_THREAD_GUARD_LOCK(&mutex_);
   topLEDColor_ = topColor;
   bottomLEDColor_ = bottomColor;
   MM_THREAD_GUARD_UNLOCK(&mutex_);
   return DEVICE_OK;
}

/*
 * Class that keeps a model of the state of the Leica DMI microscope
 */
LeicaDMIModel::LeicaDMIModel() :
   usesMethods_(false),
   availableDevices_(maxNrDevices_, false),
   availableMethods_(maxNrMethods_, false),
   methodNames_(maxNrMethods_ + 1)
{
   methodNames_[0] = "TL BF";
   methodNames_[1] = "TL PH";
   methodNames_[2] = "TL DF";
   methodNames_[3] = "TL DIC";
   methodNames_[4] = "TL POL";
   methodNames_[5] = "TL_IMC";
   methodNames_[6] = "IL BF";
   methodNames_[7] = "IL DF";
   methodNames_[8] = "IL DIC";
   methodNames_[9] = "IL POL";
   methodNames_[10] = "FLUO";
   methodNames_[11] = "FLUO/PH";
   methodNames_[12] = "FLUO/DIC";
   methodNames_[13] = "BF/BF";
   methodNames_[14] = "CS";
   methodNames_[15] = "nn";
}

LeicaDMIModel::~LeicaDMIModel()
{
}

void LeicaDMIModel::SetDeviceAvailable(int devId)
{
   if (devId > 0 && devId < maxNrDevices_)
      availableDevices_[devId] = true;;
}


bool LeicaDMIModel::IsDeviceAvailable(int devId)
{
   if (devId > 0 && devId < maxNrDevices_)
      return availableDevices_[devId];
   return false;
}


void LeicaDMIModel::SetMethodAvailable(int methodId)
{
   if (methodId >= 0 && methodId < maxNrMethods_)
      availableMethods_[methodId] = true;
}

bool LeicaDMIModel::IsMethodAvailable(int methodId)
{
   if (usesMethods_)
      return availableMethods_[methodId];

   return false;
}


bool LeicaDMIModel::IsMethodAvailable(std::string methodLabel)
{
   if (usesMethods_)
      for (int i=0; i<16; i++)
         if (methodNames_.at(i).compare(methodLabel) == 0 && IsMethodAvailable(i))
            return true;

   return false;
}
         
std::string LeicaDMIModel::GetMethod(int methodId)
{
   return methodNames_.at(methodId);
}

int LeicaDMIModel::GetMethodID(std::string method)
{
   if (usesMethods_) {
      for (int i=0; i<16; i++) {
         if (method.compare(methodNames_.at(i)) == 0)
            return i;
      }
   }
   return -1;
}

bool LeicaDMIModel::UsesMethods()
{
   return usesMethods_;
}

void LeicaDMIModel::SetUsesMethods(bool use)
{
   usesMethods_ = use;
}
