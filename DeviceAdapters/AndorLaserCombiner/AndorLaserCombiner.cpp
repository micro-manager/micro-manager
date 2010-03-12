///////////////////////////////////////////////////////////////////////////////
// FILE:          AndorLaserCombiner.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   AndorLaserCombiner controller adapter
// COPYRIGHT:     University of California, San Francisco, 2009
//
// AUTHOR:        Karl Hoover, UCSF
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
// CVS:           $Id: AndorLaserCombiner.cpp$        
//



#ifdef WIN32
   #include <windows.h>
   #define snprintf _snprintf 
#endif


// declarations for the ALC library
#include "ALC_REV.h"

#include "../../MMDevice/MMDevice.h"
#include "AndorLaserCombiner.h"
#include <string>
#include <math.h>
#include "../../MMDevice/ModuleInterface.h"
#include "../../MMDevice/DeviceUtils.h"
//#include "../Utilities/CodeUtility.h"
#include <sstream>
#include <iostream>

// Controller
const char* g_ControllerName = "AndorLaserCombiner";
const char* g_Keyword_PowerSetpoint = "PowerSetpoint";
const char* g_Keyword_PowerReadback = "PowerReadback";

const char * carriage_return = "\r";
const char * line_feed = "\n";

class ALCImpl
{

public:

	HMODULE alcHandle_;
	typedef bool (__stdcall *TCreate_ALC_REV)( IALC_REVObject **ALC_REVObject);
   typedef bool (__stdcall *TDelete_ALC_REV)( IALC_REVObject *ALC_REVObject);
   TCreate_ALC_REV Create_ALC_REV_;
   TDelete_ALC_REV Delete_ALC_REV_;
   IALC_REVObject *ALC_REVObject_;
	
	IALC_REV_Laser *pALC_REVLaser_;
   IALC_REV_Piezo *pALC_REVPiezo_;
   IALC_REV_DIO *pALC_REV_DIO_;


	//ctor
	ALCImpl(void):alcHandle_(0),Create_ALC_REV_(0),Delete_ALC_REV_(0),ALC_REVObject_(NULL) 
	{
		std::string libraryName = "AB_ALC_REV.dll";
		alcHandle_ = LoadLibraryA(libraryName.c_str());
		if( NULL == alcHandle_)
		{
			std::ostringstream messs;
			messs << "failed to load library: " << libraryName << " check that the library is in your PATH ";
			throw messs.str();
		}
		Create_ALC_REV_ = ( TCreate_ALC_REV)GetProcAddress( alcHandle_, "Create_ALC_REV");
		if( Create_ALC_REV_ == 0 )
		{
			throw "GetProcAddress Create_ALC_REV failed\n";
		}
		Delete_ALC_REV_ = ( TDelete_ALC_REV)GetProcAddress( alcHandle_, "Delete_ALC_REV");
		if( Delete_ALC_REV_ == 0 )
		{
			throw "GetProcAddress Delete_ALC_REV failed\n";
		}
	   Create_ALC_REV_( &ALC_REVObject_);
	   if( NULL == ALC_REVObject_ != NULL )
	   {
			throw "Create_ALC_REV failed";
	   }

    pALC_REVLaser_ = ALC_REVObject_->GetLaserInterface( );
    if(0 == pALC_REVLaser_)
      throw "GetLaserInterface failed";
    
    pALC_REVPiezo_ = ALC_REVObject_->GetPiezoInterface( );
	 if( 0 == pALC_REVPiezo_)
      throw "GetPiezoInterface failed";
    
    pALC_REV_DIO_ = ALC_REVObject_->GetDIOInterface( );
    if( 0 == pALC_REV_DIO_ )
		 throw " GetDIOInterface failed!";


	};

	// dtor
	~ALCImpl()
	{
		if( 0 != ALC_REVObject_)
			Delete_ALC_REV_(ALC_REVObject_);
		ALC_REVObject_ = 0;
		if( 0 != alcHandle_) 
			FreeLibrary(alcHandle_);
	};
};




///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////
MODULE_API void InitializeModuleData()
{
   AddAvailableDeviceName(g_ControllerName, "AndorLaserCombiner");
   
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
   if (deviceName == 0)
      return 0;

   if (strcmp(deviceName, g_ControllerName) == 0)
   {
      // create Controller
      AndorLaserCombiner* pAndorLaserCombiner = new AndorLaserCombiner(g_ControllerName);

      return pAndorLaserCombiner;
   }

   return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}

///////////////////////////////////////////////////////////////////////////////
// Controller implementation
// ~~~~~~~~~~~~~~~~~~~~

AndorLaserCombiner::AndorLaserCombiner(const char* name) :
   initialized_(false), 
   //powerSetpoint_(0),
	//powerReadback_(0),
   state_(0),
   name_(name), 
   busy_(false),
   error_(0),
   changedTime_(0.0),
	DOUT_(0),
	pImpl_(NULL),
	nLasers_(0),
	openRequest_(false)

{

	for( int il = 0; il <MaxLasers+1; ++il)
	{
		powerSetPoint_[MaxLasers+1] = 0.;
	}


	//pDevImpl = new DevImpl(*this);
   assert(strlen(name) < (unsigned int) MM::MaxStrLength);

   InitializeDefaultErrorMessages();

   // create pre-initialization properties
   // ------------------------------------

   // Name
   CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);

   // Description
   CreateProperty(MM::g_Keyword_Description, "AndorLaserCombinerLaser", MM::String, true);


   EnableDelay(); // signals that the delay setting will be used
   UpdateStatus();
}

AndorLaserCombiner::~AndorLaserCombiner()
{
	delete pImpl_;
   Shutdown();
}

bool AndorLaserCombiner::Busy()
{
   MM::MMTime interval = GetCurrentMMTime() - changedTime_;
   MM::MMTime delay(GetDelayMs()*1000.0);
   if (interval < delay)
      return true;
   else
      return false;
}

void AndorLaserCombiner::GetName(char* name) const
{
   assert(name_.length() < CDeviceUtils::GetMaxStringLength());
   CDeviceUtils::CopyLimitedString(name, name_.c_str());
}


int AndorLaserCombiner::Initialize()
{
	int nRet = DEVICE_OK;


   LogMessage("AndorLaserCombiner::Initialize()");
	try
	{
		pImpl_ = new ALCImpl();
		nLasers_ = pImpl_->pALC_REVLaser_->Initialize();

	}
	catch (std::string& exs)
	{

		nRet = DEVICE_LOCALLY_DEFINED_ERROR;
		LogMessage(exs.c_str());
		SetErrorText(DEVICE_LOCALLY_DEFINED_ERROR,exs.c_str());
		//CodeUtility::DebugOutput(exs.c_str());
		return nRet;

	}




   GenerateALCProperties();
   GeneratePropertyState();
	GenerateReadOnlyIDProperties();
	std::stringstream msg;




   
   initialized_ = true;



   return HandleErrors();

}





/////////////////////////////////////////////
// Property Generators
/////////////////////////////////////////////

void AndorLaserCombiner::GeneratePropertyState()
{
   
	CPropertyAction* pAct = new CPropertyAction (this, &AndorLaserCombiner::OnState);
   CreateProperty(MM::g_Keyword_State, "0", MM::Integer, false, pAct);
   AddAllowedValue(MM::g_Keyword_State, "0");
   AddAllowedValue(MM::g_Keyword_State, "1");
}


void AndorLaserCombiner::GenerateALCProperties()
{
   std::string powerName;
   CPropertyActionEx* pAct; 
	std::ostringstream buildname;
	std::ostringstream stmp;


	// 1 based index for the lasers
	for( int il = 1; il < nLasers_+1; ++il)
	{
		pAct = new CPropertyActionEx(this, &AndorLaserCombiner::OnPowerSetpoint, il);
		buildname << g_Keyword_PowerSetpoint << il;
		CreateProperty(buildname.str().c_str(), "0", MM::Float, false, pAct);

		double fullScale = PowerFullScale(il);
		// set the limits as interrogated from the laser controller.
		SetPropertyLimits(buildname.str().c_str(), 0., fullScale);  // milliWatts

		buildname.str("");
		buildname << "MaximumLaserPower" << il;
		pAct = new CPropertyActionEx(this, &AndorLaserCombiner::OnMaximumLaserPower, il );
		stmp << fullScale;
		CreateProperty(buildname.str().c_str(), stmp.str().c_str(), MM::Float, true, pAct);

		// readbacks
		buildname.str("");
		pAct = new CPropertyActionEx(this, &AndorLaserCombiner::OnPowerReadback, il);
		buildname <<  g_Keyword_PowerReadback << il;
		CreateProperty(buildname.str().c_str(), "0", MM::Float, true, pAct);

	}
	// piezo properties:
	CPropertyAction* pA = new CPropertyAction(this, &AndorLaserCombiner::OnPiezoRange);
	stmp.str("");
	stmp << PiezoRange();
	CreateProperty("PiezoRange",  stmp.str().c_str(),  MM::Float, false, pA);

	pA = new CPropertyAction(this, &AndorLaserCombiner::OnPiezoPosition);
	stmp.str("");
	stmp << PiezoPosition();
 	CreateProperty("PiezoPosition",  stmp.str().c_str(),  MM::Float, false, pA);

	pA = new CPropertyAction(this, &AndorLaserCombiner::OnDIN);
	stmp.str("");
	stmp << std::hex << (unsigned short)DIN();
	CreateProperty("DIN",  stmp.str().c_str(),  MM::String, true, pA);

	pA = new CPropertyAction(this, &AndorLaserCombiner::OnDOUT);
	stmp.str("");
	stmp << std::hex << (unsigned short)DOUT_;
	CreateProperty("DOUT",  stmp.str().c_str(),  MM::String, false, pA);
}


void AndorLaserCombiner::GenerateReadOnlyIDProperties()
{
	CPropertyActionEx* pAct; 
	std::ostringstream buildname;
	// 1 based index
	for( int il = 1; il < nLasers_+1; ++il)
	{
		buildname.str("");
		buildname << "Hours"  << il;
		pAct = new CPropertyActionEx(this, &AndorLaserCombiner::OnHours, il);
		CreateProperty(buildname.str().c_str(), "", MM::String, true, pAct);

		buildname.str("");
		buildname << "Wavelength"  << il;
		pAct = new CPropertyActionEx(this, &AndorLaserCombiner::OnWaveLength, il);
		CreateProperty(buildname.str().c_str(), "", MM::Float, true, pAct);
	}

}

int AndorLaserCombiner::Shutdown()
{
   if (initialized_)
   {
      initialized_ = false;
   }
   return HandleErrors();
}




///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////




int AndorLaserCombiner::OnPowerReadback(MM::PropertyBase* pProp, MM::ActionType eAct, long il)
{

   if (eAct == MM::BeforeGet)
   {
		this->PowerReadback((int)il);
      pProp->Set((double)il);
   }
   else if (eAct == MM::AfterSet)
   {
			// never do anything!!
   }
   return HandleErrors();
}

int AndorLaserCombiner::OnPowerSetpoint(MM::PropertyBase* pProp, MM::ActionType eAct, long  il)
{

   double powerSetpoint;
   if (eAct == MM::BeforeGet)
   {
      pProp->Set((double)PowerSetpoint(il));
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(powerSetpoint);

		PowerSetpoint( il, (float)powerSetpoint);
		if( openRequest_)
			SetOpen();

		//pProp->Set(achievedSetpoint);  ---- for quantization....
   }
   return HandleErrors();
}


int AndorLaserCombiner::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      //GetState(state_);
      pProp->Set(state_);
   }
   else if (eAct == MM::AfterSet)
   {
		long requestedState;
      pProp->Get(requestedState);
//SetState(requestedState);
		if (state_ != requestedState)
		{
			//error_ = DEVICE_CAN_NOT_SET_PROPERTY;
		}
   }
   
   return HandleErrors();
}


int AndorLaserCombiner::OnHours(MM::PropertyBase* pProp, MM::ActionType eAct, long il)
{
   if (eAct == MM::BeforeGet)
   {
		int wval = 0;
		pImpl_->pALC_REVLaser_->GetLaserHours(il, &wval);
		pProp->Set((long)wval);
   }
   else if (eAct == MM::AfterSet)
   {
			// never do anything!!
   }
   return HandleErrors();
}



int AndorLaserCombiner::OnMaximumLaserPower(MM::PropertyBase* pProp, MM::ActionType eAct, long il)
{
   if (eAct == MM::BeforeGet)
   {
		pProp->Set((long)PowerFullScale(il));
   }
   else if (eAct == MM::AfterSet)
   {
			// never do anything!!
   }
   return HandleErrors();
}


// il will be 1 - based laser index
int AndorLaserCombiner::OnWaveLength(MM::PropertyBase* pProp, MM::ActionType eAct, long il)
{
   if (eAct == MM::BeforeGet)
   {
		pProp->Set((long)Wavelength(il));
   }
   else if (eAct == MM::AfterSet)
   {
			// never do anything for a read only property!!
   }
   return HandleErrors();
}


int AndorLaserCombiner::OnPiezoRange(MM::PropertyBase* pProp, MM::ActionType eAct)
{   
	if (eAct == MM::BeforeGet)
   {
      pProp->Set((double)PiezoRange());
   }
   else if (eAct == MM::AfterSet)
   {
		double r = 0.;
      pProp->Get(r);
		PiezoRange((float)r);
   }   
   return HandleErrors();
}

int AndorLaserCombiner::OnPiezoPosition(MM::PropertyBase* pProp, MM::ActionType eAct)
{	
	if (eAct == MM::BeforeGet)
   {
      pProp->Set((double)PiezoPosition());
   }
   else if (eAct == MM::AfterSet)
   {
		double p = 0.;
      pProp->Get(p);
		PiezoPosition((float)p);
   }   
   return HandleErrors();
}

int AndorLaserCombiner::OnDIN(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet)
   {
		std::ostringstream s;
		s << std::hex << (short)DIN();
		pProp->Set(s.str().c_str());
   }
   else if (eAct == MM::AfterSet)
   {
		// this is a read-only property!
   }   
   return HandleErrors();}


int AndorLaserCombiner::OnDOUT(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet)
   {
		std::ostringstream ss;
		ss << std::hex << (unsigned short)DOUT_;
		pProp->Set(ss.str().c_str());
   }
   else if (eAct == MM::AfterSet)
   {
		std::string sv;
		unsigned short cv; 
      pProp->Get(sv);
		std::stringstream stream0;
		stream0 << std::hex << sv;
		stream0 >> cv;
		DOUT_ = (unsigned char)cv;
		DOUT(DOUT_);
   }   
   return HandleErrors();
}



int AndorLaserCombiner::HandleErrors()
{
   int lastError = error_;
   error_ = 0;
   return lastError;
}





//********************
// Shutter API
//********************

int AndorLaserCombiner::SetOpen(bool open)
{
	if(open)
	{
		for( int il = 1; il <= this->nLasers_; ++il)
		{
			double fullScale = PowerFullScale(il);
			bool onn = ( 0. < PowerSetpoint(il))  && (0. < fullScale);
			double percentScale = 0.;
			if( onn)
				percentScale = 100.*(double)PowerSetpoint(il)/fullScale;
			pImpl_->pALC_REVLaser_->SetLas_I( il,percentScale, onn );
		}
	}

	pImpl_->pALC_REVLaser_->SetLas_Shutter(open);
	openRequest_ = open;

	return DEVICE_OK;
}


int AndorLaserCombiner::GetOpen(bool& open)
{
	// todo check that all requested lasers are 'ready'
	open = openRequest_ ; // && Ready();

	return DEVICE_OK;

}

// ON for deltaT milliseconds
// other implementations of Shutter don't implement this
// is this perhaps because this blocking call is not appropriate
int AndorLaserCombiner::Fire(double deltaT)
{
	SetOpen(true);
	CDeviceUtils::SleepMs((long)(deltaT+.5));
	SetOpen(false);
   return HandleErrors();
}



int AndorLaserCombiner::Wavelength(const int laserIndex_a)
{
	int wval = 0;
	pImpl_->pALC_REVLaser_->GetWavelength(laserIndex_a, &wval);
	return wval;
}

int AndorLaserCombiner::PowerFullScale(const int laserIndex_a)
{
	int val = 0;
	pImpl_->pALC_REVLaser_->GetPower(laserIndex_a, &val);
	return val;
}


float AndorLaserCombiner::PowerReadback(const int laserIndex_a)
{
	double val = 0.;
	pImpl_->pALC_REVLaser_->GetCurrentPower(laserIndex_a, &val);
	return (float) val;
}


float AndorLaserCombiner::PowerSetpoint(const int laserIndex_a)
{
	return powerSetPoint_[laserIndex_a];

}

void  AndorLaserCombiner::PowerSetpoint(const int laserIndex_a, const float val_a)
{
	powerSetPoint_[laserIndex_a] = val_a;
}



bool AndorLaserCombiner::Ready(const int laserIndex_a)
{
	TLaserState state = ALC_NOT_AVAILABLE;
	bool ret =	pImpl_->pALC_REVLaser_->GetLaserState(laserIndex_a, &state);
	return ret && ( ALC_READY == state);	
}


float AndorLaserCombiner::PiezoRange(void)
{
	double dtmp = 0.;
	pImpl_->pALC_REVPiezo_->GetRange(&dtmp);
	return (float)dtmp;
}

void AndorLaserCombiner::PiezoRange(const float val)
{
	pImpl_->pALC_REVPiezo_->SetRange( (const double)val);
}


float AndorLaserCombiner::PiezoPosition(void)
{
	double dtmp = 0.;
	pImpl_->pALC_REVPiezo_->GetPosition(&dtmp);
	return (float)dtmp;
}

void AndorLaserCombiner::PiezoPosition(const float val)
{
	pImpl_->pALC_REVPiezo_->SetPosition((const double)val);
}


unsigned char AndorLaserCombiner::DIN(void)
{
	unsigned char btmp=0;
	pImpl_->pALC_REV_DIO_->GetDIN(&btmp);
	return btmp;
}

void AndorLaserCombiner::DOUT(const unsigned char val)
{
	pImpl_->pALC_REV_DIO_->SetDOUT(val);
}