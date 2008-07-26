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
   maxPosition_(1)
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
   cubeMethods_(LeicaDMIModel::maxNrMethods_, false)
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
   cube_(maxNrCubes_)
{
   LeicaDeviceModel::LeicaDeviceModel();
   position_ = 1;
}

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
   objective_(maxNrObjectives_)
{
   LeicaDeviceModel::LeicaDeviceModel();
   position_ = 1;
}

/*
 * Drive Model
 */
LeicaDriveModel::LeicaDriveModel() 
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


/*
 * Class that keeps a model of the state of the Leica DMI microscope
 */
LeicaDMIModel::LeicaDMIModel() :
   availableDevices_(maxNrDevices_, false),
   availableMethods_(maxNrMethods_, false),
   methodNames_(maxNrMethods_)
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
   if (methodId > 0 && methodId < maxNrMethods_)
      availableMethods_[methodId] = true;
}

bool LeicaDMIModel::IsMethodAvailable(int methodId)
{
   return availableMethods_[methodId];
}


bool LeicaDMIModel::IsMethodAvailable(std::string methodLabel)
{
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
   for (int i=0; i<16; i++) {
      if (method.compare(methodNames_.at(i)) == 0)
         return i;
   }
   return -1;
}
