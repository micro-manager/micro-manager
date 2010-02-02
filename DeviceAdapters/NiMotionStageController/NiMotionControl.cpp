///////////////////////////////////////////////////////////////////////////////
// FILE:          NiMotionControl.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   The example implementation of the demo camera.
//                Simulates generic digital camera and associated automated
//                microscope devices and enables testing of the rest of the
//                system without the need to connect to the actual hardware. 
//                
// AUTHOR:        Brian Ashcroft, ashcroft@leidenuniv.nl
//
// COPYRIGHT:     Leiden University, Leiden, 2009
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

#ifdef WIN32
   #define WIN32_LEAN_AND_MEAN
   #include <windows.h>
   #define snprintf _snprintf 
#endif

#include "NIMotionControl.h"
#include <string>
#include <math.h>
#include "../../MMDevice/ModuleInterface.h"
#include <sstream>
#include "ZStage.h"
using namespace std;

// External names used used by the rest of the system
// to load particular device from the "DemoCamera.dll" library

const char* g_XYStageDeviceName = "NI_Motion_XYStage";
const char* g_ZStageDeviceName = "NI_Motion_ZStage";

// windows DLL entry code
#ifdef WIN32
   BOOL APIENTRY DllMain( HANDLE /*hModule*/, 
                          DWORD  ul_reason_for_call, 
                          LPVOID /*lpReserved*/
		   			 )
   {
   	switch (ul_reason_for_call)
   	{
   	case DLL_PROCESS_ATTACH:
  	   case DLL_THREAD_ATTACH:
   	case DLL_THREAD_DETACH:
   	case DLL_PROCESS_DETACH:
   		break;
   	}
       return TRUE;
   }
#endif

///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////

MODULE_API void InitializeModuleData()
{
   AddAvailableDeviceName(g_XYStageDeviceName, "NI Motion XY stage");
   AddAvailableDeviceName(g_ZStageDeviceName,"NI Motion Z or single axis stage");
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
   if (deviceName == 0)
      return 0;

   if (strcmp(deviceName, g_XYStageDeviceName) == 0)
   {
      // create stage
      return new CNIMotionXYStage();
   }
  
   if (strcmp(deviceName, g_ZStageDeviceName) == 0)
   {
      // create stage
      return new CNIMotionZStage();
   }

   // ...supplied name not recognized
   return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}



CNIMotionXYStage::CNIMotionXYStage() : 
   stepSize_um_(0.08),
   posX_um_(0.0),
   posY_um_(0.0),
   busy_(false),
   initialized_(false),
   lowerLimit_(0.0),
   upperLimit_(20000.0),
   XAxis(0),
   YAxis(0),
   BoardID(2),
   MoveVelocity(10000),
   MoveAcceleration(100000),
   MoveJerk(1000)
{
   InitializeDefaultErrorMessages();
}

CNIMotionXYStage::~CNIMotionXYStage()
{
   Shutdown();
}

void CNIMotionXYStage::GetName(char* Name) const
{
   CDeviceUtils::CopyLimitedString(Name, g_XYStageDeviceName);
}

int CNIMotionXYStage::Initialize()
{
   if (initialized_)
      return DEVICE_OK;
   
   VectorSpace=NIMC_VECTOR_SPACE1;
   // set property list
   // -----------------
   
   // Name
   int ret = CreateProperty(MM::g_Keyword_Name, g_XYStageDeviceName, MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Description
   ret = CreateProperty(MM::g_Keyword_Description, "Ni Motion controller to control an XY stage", MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

  //set up axis 1
   CPropertyAction *pAct = new CPropertyAction (this, &CNIMotionXYStage::OnXAxis);
   int nRet;
   nRet = CreateProperty("X Axis Number", "0", MM::Integer, false, pAct);
   assert(nRet == DEVICE_OK);

   vector<string> binValues;
   binValues.push_back("0");
   binValues.push_back("1");
   binValues.push_back("2");
   binValues.push_back("3");
   binValues.push_back("4");
   nRet = SetAllowedValues("X Axis Number", binValues);
   if (nRet != DEVICE_OK)
      return nRet;

   
   pAct = new CPropertyAction (this, &CNIMotionXYStage::OnYAxis);
   nRet = CreateProperty("Y Axis Number", "0", MM::Integer, false, pAct);
   assert(nRet == DEVICE_OK);

   vector<string> binValuesY;
   binValuesY.push_back("0");
   binValuesY.push_back("1");
   binValuesY.push_back("2");
   binValuesY.push_back("3");
   binValuesY.push_back("4");
   nRet = SetAllowedValues("Y Axis Number", binValuesY);
   if (nRet != DEVICE_OK)
      return nRet;

    
   pAct = new CPropertyAction (this, &CNIMotionXYStage::OnBoardID);
   nRet = CreateProperty("Board ID","2",MM::Integer,false, pAct);
   assert(nRet == DEVICE_OK);

   vector<string> binValuesB;
   binValuesB.push_back("1");
   binValuesB.push_back("2");
   binValuesB.push_back("3");
   binValuesB.push_back("4");
   nRet = SetAllowedValues("Board ID", binValuesB);
   if (nRet != DEVICE_OK)
      return nRet;
   
   pAct = new CPropertyAction (this, &CNIMotionXYStage::OnSetVelocity);
   nRet = CreateProperty("Move Velocity","10000",MM::Integer,false, pAct);
   assert(nRet == DEVICE_OK);

   if (nRet != DEVICE_OK)
      return nRet;

   pAct = new CPropertyAction (this, &CNIMotionXYStage::OnSetAcceleration);
   nRet = CreateProperty("Move Acceleration","100000",MM::Integer,false, pAct);
   assert(nRet == DEVICE_OK);

   if (nRet != DEVICE_OK)
      return nRet;

   pAct = new CPropertyAction (this, &CNIMotionXYStage::OnSetJerk);
   nRet = CreateProperty("Move Jerk","1000",MM::Integer,false, pAct);
   assert(nRet == DEVICE_OK);

   if (nRet != DEVICE_OK)
      return nRet;

   pAct = new CPropertyAction (this, &CNIMotionXYStage::OnSetStepSize);
   nRet = CreateProperty("Step Size","0.08",MM::Float ,false, pAct);
   assert(nRet == DEVICE_OK);

   if (nRet != DEVICE_OK)
      return nRet;


   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;
   

   SetBoard();
   initialized_ = true;
  
   return DEVICE_OK;
}

int CNIMotionXYStage::SetPositionUm(double x, double y)
  {
	  if (stepSize_um_==0) stepSize_um_=1;
	  int xi=(int)(x/stepSize_um_);
      int yi=(int)(y/stepSize_um_);
	  return (SetPositionSteps((long)xi,(long)yi));
  }
int CNIMotionXYStage::SetPositionSteps(long x, long y)
   {
      posX_um_ = x*stepSize_um_ ;
      posY_um_ = y*stepSize_um_ ;
      
      return ( MoveBoard((int)x,(int)y));
   }


bool CNIMotionXYStage::Busy()
{
	u16 moveComplete;

	//Variables for modal error handling
    i32	err=0 ;
    err = flex_check_move_complete_status(BoardID, VectorSpace, 0, &moveComplete);
 	if (err!=0)
		{
			busy_=false;	
			return (busy_);
		}
	busy_=!moveComplete;
	return busy_;
}
int CNIMotionXYStage::Shutdown()
{
   if (initialized_)
   {
      initialized_ = false;
   }
   return DEVICE_OK;
}

int CNIMotionXYStage::OnXAxis(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      // nothing to do, let the caller use cached property
   }
   else if (eAct == MM::AfterSet)
   {
      long axis;
      pProp->Get(axis);
      XAxis=(u8)axis;
	  if (XAxis!=0)
        return (SetBoard());
	  else 
		return (0);
      
   }

   return DEVICE_OK;
}
int CNIMotionXYStage::OnBoardID(MM::PropertyBase* pProp, MM::ActionType eAct)
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
	  
      return (SetBoard());
      
   }

   return DEVICE_OK;
}
int CNIMotionXYStage::OnYAxis(MM::PropertyBase* pProp, MM::ActionType eAct)
	   {
   if (eAct == MM::BeforeGet)
   {
      // nothing to do, let the caller use cached property
   }
   else if (eAct == MM::AfterSet)
   {
      long axis;
      pProp->Get(axis);
      YAxis=(u8)axis;
	  if (YAxis!=0)
	  {
         return (SetBoard());
	  }
	  else 
	     return 0;
      
   }

   return DEVICE_OK;
}
int CNIMotionXYStage::OnSetVelocity(MM::PropertyBase* pProp, MM::ActionType eAct)
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
int CNIMotionXYStage::OnSetAcceleration(MM::PropertyBase* pProp, MM::ActionType eAct)
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

int CNIMotionXYStage::OnSetJerk(MM::PropertyBase* pProp, MM::ActionType eAct)
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



void CNIMotionXYStage::nimcDisplayError(i32 errorCode, u16 commandID, u16 resourceID)
{

	i8 *errorDescription;			//Pointer to i8's -  to get error description
	u32 sizeOfArray;				//Size of error description
	u16 descriptionType;			//The type of description to be printed
	i32 status;						//Error returned by function

	if(commandID == 0){
		descriptionType = NIMC_ERROR_ONLY;
	}else{
		descriptionType = NIMC_COMBINED_DESCRIPTION;
	}


	//First get the size for the error description
	sizeOfArray = 0;
	errorDescription = NULL;//Setting this to NULL returns the size required
	status = flex_get_error_description(descriptionType, errorCode, commandID, resourceID,
														errorDescription, &sizeOfArray );

	//Allocate memory on the heap for the description
	errorDescription =(i8*) malloc(sizeOfArray + 1);

	sizeOfArray++; //So that the sizeOfArray is size of description + NULL character
	// Get Error Description
	status = flex_get_error_description(descriptionType, errorCode, commandID, resourceID,
													errorDescription, &sizeOfArray );

	if (errorDescription != NULL){
		::MessageBoxA(0,errorDescription,"",0);
		free(errorDescription);		//Free allocated memory
	}else{
		::MessageBoxA(0,"No Error","",0);
	}
}

int CNIMotionXYStage::SetBoard()
{
  

//::MessageBox(NULL,TEXT("Setting String" ),TEXT("ok" ),MB_OK);
  if (XAxis!=0 && YAxis !=0 )
  {

	  u16 csr = 0;
	  
      i32 err=flex_read_csr_rtn(BoardID,&csr);
	  
      if (err==0)
	  {
		  if  ((csr & NIMC_POWER_UP_RESET )!=0)
		  {
			  
			  flex_initialize_controller(BoardID,NULL);
			  flex_clear_pu_status(BoardID);
		  }

		  err= flex_config_vect_spc(BoardID, VectorSpace, XAxis, YAxis, 0);
			if (err!=0) return (err);
		 
		  err=flex_load_velocity(BoardID, VectorSpace, MoveVelocity, 0xFF);
			if (err!=0) return (err);
		  err= flex_load_acceleration(BoardID, VectorSpace, NIMC_ACCELERATION, MoveAcceleration, 0xFF);
			if (err!=0) return (err);
		  // Set the deceleration for the move (in counts/sec^2)
		  err = flex_load_acceleration(BoardID, VectorSpace, NIMC_DECELERATION, MoveAcceleration, 0xFF);
			if (err!=0) return (err);
		  err= flex_load_scurve_time(BoardID, VectorSpace, MoveJerk, 0xFF);
			if (err!=0) return (err);
		  err =  flex_set_op_mode (BoardID, VectorSpace, NIMC_ABSOLUTE_POSITION);
			if (err!=0) return (err);
	  }
  }
  return  DEVICE_OK;;		// Exit the Application
}

int CNIMotionXYStage::MoveBoard(int x,int y )
{
	u16 axisStatus;			// Axis status
	u16 status;
	u16 moveComplete;

   if (XAxis!=0 && YAxis !=0)
  {

	//Variables for modal error handling
    i32	err=0 ;
	err = flex_check_move_complete_status(BoardID, VectorSpace, 0, &moveComplete);
    if (!moveComplete)
	{
		::flex_stop_motion(BoardID,VectorSpace,1,0x10);//	flex_halt(BoardID,VectorSpace,0);
	}

   	// Load Vector Space Position
    err= flex_load_vs_pos (BoardID, VectorSpace, x, y, 0/* z Position*/, 0xFF);
    if (err!=0) return (err);


	// Start the move
	err = flex_start(BoardID, VectorSpace, 0);
	if (err!=0) return (err);
    busy_=true;



	u16 csr	= 0;
	//do
	{
		axisStatus = 0;
		// Check the move complete status
		err = flex_check_move_complete_status(BoardID, VectorSpace, 0, &moveComplete);
 		if (err!=0)
		{
			busy_=false;	
			return (err);
		}
		
		// Check the following error/axis off status for axis 1
		err = flex_read_axis_status_rtn(BoardID, XAxis, &status);
		if (err!=0)
		{
			busy_=false;	
			return (err);
		}
		axisStatus |= status;

		// Check the following error/axis off status for axis 2
		err = flex_read_axis_status_rtn(BoardID, YAxis, &status);
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
	return DEVICE_OK;;		// Exit the Application

}

int CNIMotionXYStage::OnSetStepSize(MM::PropertyBase* pProp, MM::ActionType eAct)
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

int CNIMotionXYStage::GetPositionUm(double& x, double& y)
{
    i32 pos;
	i32	err=0 ;
	err=flex_read_pos_rtn(BoardID,XAxis,&pos);
	x=(double)pos*stepSize_um_;
	err=flex_read_pos_rtn(BoardID,YAxis,&pos);
	y=(double)pos*stepSize_um_;
	return err;	
}
int CNIMotionXYStage::Stop()
{

	u16 moveComplete;

	//Variables for modal error handling
    i32	err=0 ;
	err = flex_check_move_complete_status(BoardID, VectorSpace, 0, &moveComplete);
    if (!moveComplete)
	{
		err=	flex_halt(BoardID,VectorSpace,0);
	}
	return (err);
}