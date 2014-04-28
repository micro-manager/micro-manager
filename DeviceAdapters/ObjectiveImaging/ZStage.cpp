///////////////////////////////////////////////////////////////////////////////
// FILE:          ZStage.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Oasis Controller / ZStage (Objective Imaging)
//
// AUTHOR:        Egor Zindy, egor.zindy@manchester.ac.uk
//                mostly based on Zstage.cpp from NiMotionControl by
//                Brian Ashcroft, ashcroft@leidenuniv.nl
//
// COPYRIGHT:     University of Manchester, 2014 (ZStage.cpp)
//                Leiden University, Leiden, 2009 (ZStage.cpp)
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
//
// vim: set autoindent tabstop=3 softtabstop=3 shiftwidth=3 expandtab textwidth=78:

#ifdef WIN32
   #define WIN32_LEAN_AND_MEAN
   #include <windows.h>
   #define snprintf _snprintf
#endif

#include "ZStage.h"
#include <string>
#include <math.h>
#include "../../MMDevice/ModuleInterface.h"
#include <sstream>
using namespace std;

OasisZStage::OasisZStage() :
   stepSize_um_(0.025),
   pos_um_(0.0),
   busy_(false),
   initialized_(false),

   lowerLimit_(0.0),
   upperLimit_(20000.0),
   Axis(0),
   BoardID(0),

ZStageDeviceName( "Oasis_ZStage")
{
       InitializeDefaultErrorMessages();
}

OasisZStage::~OasisZStage()
{
   Shutdown();
}

void OasisZStage::GetName(char* Name) const
{
   CDeviceUtils::CopyLimitedString(Name, ZStageDeviceName);
}

int OasisZStage::Initialize()
{
   if (initialized_)
      return DEVICE_OK;

   // set property list
   // -----------------

   // Name
   int nRet = CreateProperty(MM::g_Keyword_Name, ZStageDeviceName, MM::String, true);
   if (nRet != DEVICE_OK)
      return nRet;

   // Description
   nRet = CreateProperty(MM::g_Keyword_Description, "Oasis controller to control an single axis stage", MM::String, true);
   if (nRet != DEVICE_OK)
      return nRet;

   //set up axis 1
   CPropertyAction *pAct = new CPropertyAction (this, &OasisZStage::OnAxis);
   nRet = CreateProperty("Axis Number", "0", MM::Integer, false, pAct);
   assert(nRet == DEVICE_OK);

   vector<string> binValues;
   binValues.push_back("0");
   binValues.push_back("1");
   binValues.push_back("2");
   binValues.push_back("3");
   nRet = SetAllowedValues("Axis Number", binValues);
   if (nRet != DEVICE_OK)
      return nRet;


   pAct = new CPropertyAction (this, &OasisZStage::OnBoardID);
   nRet = CreateProperty("Board ID","0",MM::Integer,false, pAct);
   assert(nRet == DEVICE_OK);

   nRet = DEVICE_OK;
   OI_CountCards(&pnNumber);

   for (int i = 0; i < pnNumber)
   {
      nRet += AddAllowedValue("Board ID", CDeviceUtils::ConvertToString(i));
   }

   if (nRet != DEVICE_OK)
      return nRet;

   nRet = UpdateStatus();
   if (nRet != DEVICE_OK)
      return nRet;

   OI_SelectCard(BoardID);
   OI_InitializeXY();
   initialized_ = true;

   return DEVICE_OK;
}

int OasisZStage::SetPositionUm(double pos)
{
}
int OasisZStage::SetPositionSteps(long steps)
{
}

bool OasisZStage::Busy()
{
}
int OasisZStage::Shutdown()
{
}

int OasisZStage::OnAxis(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      // nothing to do, let the caller use cached property
   }
   else if (eAct == MM::AfterSet)
   {
      long axis;
      pProp->Get(axis);
      Axis=(u8)axis;
      return (SetBoard());

   }

   return DEVICE_OK;
}

int OasisZStage::OnBoardID(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      // nothing to do, let the caller use cached property
   }
   else if (eAct == MM::AfterSet)
   {
      long ID;
      pProp->Get(ID);
      BoardID=(u8)ID;
      std::string msg("BoardID");
      // flex_initialize_controller(BoardID,NULL);
      return (SetBoard());
   }

   return DEVICE_OK;
}

int OasisZStage::OnSetVelocity(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      // nothing to do, let the caller use cached property
   }
   else if (eAct == MM::AfterSet)
   {
      long vel;
      pProp->Get(vel);
      MoveVelocity=vel;
      return (SetBoard());

   }

   return DEVICE_OK;
}
int OasisZStage::OnSetAcceleration(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      // nothing to do, let the caller use cached property
   }
   else if (eAct == MM::AfterSet)
   {
      long acc;
      pProp->Get(acc);
      MoveAcceleration=acc;
      return (SetBoard());
   }

   return DEVICE_OK;
}

int OasisZStage::OnSetJerk(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      // nothing to do, let the caller use cached property
   }
   else if (eAct == MM::AfterSet)
   {
      long jerk;
      pProp->Get(jerk);
      MoveJerk=(u16)jerk;
      return (SetBoard());
   }

   return DEVICE_OK;
}



int OasisZStage::SetBoard()
{
   if (Axis!=0 )
   {
      u16 csr = 0;

      i32 err=flex_read_csr_rtn(BoardID,&csr);
      if (err==0)
      {
         if ((csr & NIMC_POWER_UP_RESET )!=0)
         {
            flex_initialize_controller(BoardID,NULL);
            flex_clear_pu_status(BoardID);
         }

         // Set the velocity for the move (in counts/sec)
         i32 err = flex_load_velocity(BoardID, Axis, MoveVelocity, 0xFF);
         if (err!=0) return (err);

         // Set the acceleration for the move (in counts/sec^2)
         err = flex_load_acceleration(BoardID, Axis, NIMC_ACCELERATION, MoveAcceleration, 0xFF);
         if (err!=0) return (err);

         // Set the deceleration for the move (in counts/sec^2)
         err = flex_load_acceleration(BoardID, Axis, NIMC_DECELERATION, MoveAcceleration, 0xFF);
         if (err!=0) return (err);

         // Set the jerk - scurve time (in sample periods)
         err = flex_load_scurve_time(BoardID, Axis, MoveJerk, 0xFF);
         if (err!=0) return (err);

         // Set the operation mode
         err =  flex_set_op_mode (BoardID, Axis, NIMC_ABSOLUTE_POSITION);
         if (err!=0) return (err);
      }
  }

  return  DEVICE_OK;;// Exit the Application
}

int OasisZStage::MoveBoard(int pos_steps )
{
   u16 axisStatus;			// Axis status
   u16 status;
   u16 moveComplete;


   if (Axis!=0 )
   {
      //Variables for modal error handling
      i32 err=0 ;
      err = flex_check_move_complete_status(BoardID, Axis, 0, &moveComplete);
      if (!moveComplete)
      {
         ::flex_stop_motion(BoardID,Axis,1,0x10);//	flex_halt(BoardID,VectorSpace,0);
      }

      // Load Position
      err = flex_load_target_pos (BoardID, Axis, pos_steps, 0xFF);
      if (err!=0) return (err);

      // Start the move
      err = flex_start(BoardID, Axis, 0);
      if (err!=0) return (err);
      busy_=true;

      u16 csr = 0;
      //do
      {
         axisStatus = 0;
         // Check the move complete status
         err = flex_check_move_complete_status(BoardID, Axis, 0, &moveComplete);
         if (err!=0)
         {
            busy_=false;
            return (err);
         }

         // Check the following error/axis off status for axis 1
         err = flex_read_axis_status_rtn(BoardID, Axis, &status);
         if (err!=0)
         {
            busy_=false;
            return (err);
         }
         axisStatus |= status;

         // Read the communication status register and check the modal errors
         err = flex_read_csr_rtn(BoardID, &csr);
         if (err!=0)
         {
            busy_=false;
            return (err);
         }
         // Check the modal errors
         if (csr & NIMC_MODAL_ERROR_MSG)
         {
            err = csr & NIMC_MODAL_ERROR_MSG;
            if (err!=0)
            {
               busy_=false;
               return (err);
            }
         }
      }//while (!moveComplete && !(axisStatus & NIMC_FOLLOWING_ERROR_BIT) && !(axisStatus & NIMC_AXIS_OFF_BIT)); //Exit on move complete/following error/axis off
   }
   busy_=false;

   return DEVICE_OK; // Exit the Application

}

int OasisZStage::OnSetStepSize(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      // nothing to do, let the caller use cached property
   }
   else if (eAct == MM::AfterSet)
   {
      double stepsize;
      pProp->Get(stepsize);
      stepSize_um_ = stepsize;
   }

   return DEVICE_OK;
}

int OasisZStage::GetPositionUm(double& pos_um)
{
   i32 pos;
   i32 err=0 ;
   if (Axis!=0)
   {
      err=flex_read_pos_rtn(BoardID,Axis,&pos);
      pos_um=(double)pos*stepSize_um_;
   }
   else
      pos_um=0;

   return err;
}

int OasisZStage::GetPositionSteps(long& steps)
{
   i32 pos;
   i32 err=0 ;
   if (Axis!=0)
   {
      err=flex_read_pos_rtn(BoardID,Axis,&pos);
      steps=pos;
   }
   else
      steps=0;

   return err;
}

int OasisZStage::Stop()
{

   u16 moveComplete;
   i32 err=0 ;
   //Variables for modal error handling
   if (Axis!=0)
   {
      err = flex_check_move_complete_status(BoardID, Axis, 0, &moveComplete);
      if (!moveComplete)
      {
         err = flex_halt(BoardID,Axis,0);
      }
   }

   return (err);
}
