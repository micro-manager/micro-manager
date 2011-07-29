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
// REVISIONS:     Ed Simmons 2011 ed@esimaging.co.uk 
//                - Explicit controls for TTL outputs, July 27, 2011
//
// CVS:           $Id:$        
// CVS:           $Id $        
// CVS:           $Id: $        
// CVS:           $Id : $        
// CVS:           $Id :$        
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
//#include "../../MMCore/CoreUtils.h"
#include <sstream>
#include <iostream>

#include "boost/lexical_cast.hpp"

#ifndef _isnan
  #ifdef __GNUC__
    #include <cmath>
    using std::isnan;
  #elif _MSC_VER  // MSVC
    #include <float.h>
    #define isnan _isnan
  #endif
#endif


// Controller
const char* g_ControllerName = "AndorLaserCombiner";
const char* g_PiezoStageName = "PiezoStage";
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
	ALCImpl(void):alcHandle_(0),Create_ALC_REV_(0),Delete_ALC_REV_(0),ALC_REVObject_(0),pALC_REVLaser_(0),
		pALC_REVPiezo_(0), pALC_REV_DIO_(0)
	{
		#ifdef _M_X64
		std::string libraryName = "AB_ALC_REV64.dll";
    #else
		std::string libraryName = "AB_ALC_REV.dll";
    #endif
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
		alcHandle_ = 0;
	};
};


// this is shared between the ALC and the Piezo stage
static ALCImpl* pImplInstance_s;
MMThreadLock ImplLock_s;

///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////
MODULE_API void InitializeModuleData()
{
   AddAvailableDeviceName(g_ControllerName, "AndorLaserCombiner");
   AddAvailableDeviceName(g_PiezoStageName, "PiezoStage");
   
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
   if (deviceName == 0)
      return 0;

   if ( (strcmp(deviceName, g_ControllerName) == 0) )
   {
      // create Controller
      AndorLaserCombiner* pAndorLaserCombiner = new AndorLaserCombiner(g_ControllerName);
      return pAndorLaserCombiner;
   }


   if( strcmp(deviceName, g_PiezoStageName) ==  0)
      return new PiezoStage(g_PiezoStageName);

   return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{

   delete pDevice;
}

///////////////////////////////////////////////////////////////////////////////
// Controller implementation
// ~~~~~~~~~~~~~~~~~~~~

AndorLaserCombiner::AndorLaserCombiner( const char* name) :
   initialized_(false), 
   name_(name), 
   busy_(false),
   error_(0),
   changedTime_(0.0),
   DOUT_(0),
   pImpl_(NULL),
   nLasers_(0),
   openRequest_(false),
   multiPortUnitPresent_(false),
   laserPort_(0)

{

	for( int il = 0; il <MaxLasers+1; ++il)
	{
		powerSetPoint_[il] = 0;
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


   // multi-port unit present

   CPropertyAction* pAct = new CPropertyAction (this, &AndorLaserCombiner::OnMultiPortUnitPresent);
   CreateProperty("MultiPortUnitPresent", "0", MM::Integer, false, pAct, true);
   AddAllowedValue("MultiPortUnitPresent", "0");
   AddAllowedValue("MultiPortUnitPresent", "1");

   EnableDelay(); // signals that the delay setting will be used
   UpdateStatus();
	LogMessage(("AndorLaserCombiner ctor OK, " + std::string(name)).c_str(), true);
}

AndorLaserCombiner::~AndorLaserCombiner()
{
   Shutdown();
   MMThreadGuard g(ImplLock_s);

   // the implementation is destroyed only from the Combiner, not from ~PiezoStage
	delete pImpl_;
   pImplInstance_s = NULL;
	LogMessage("AndorLaserCombiner dtor OK", true);
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

	try
	{
      MMThreadGuard G( ImplLock_s);
      if(  NULL == pImplInstance_s)
      {
	      pImpl_ = new ALCImpl();
	      CDeviceUtils::SleepMs(100);
         pImplInstance_s = pImpl_;
      }
      else
         pImpl_ = pImplInstance_s;
		nLasers_ = pImpl_->pALC_REVLaser_->Initialize();
		LogMessage(("in AndorLaserCombiner::Initialize, nLasers_ ="+boost::lexical_cast<std::string,int>(nLasers_)), true);
		CDeviceUtils::SleepMs(100);
		bool initialised = pImpl_->pALC_REV_DIO_->InitializeDIO();
		LogMessage(("in AndorLaserCombiner::InitializeDIO() ="+boost::lexical_cast<std::string,bool>(initialised)), true);
		CDeviceUtils::SleepMs(100);		
		TLaserState state[10];
		memset((void*)state, 0, 10*sizeof(state[0]));

		//Andor says that lasers can take up to 90 seconds to initialize;
      MM::TimeoutMs timerout(GetCurrentMMTime(), 91000);
		int iloop  = 0;
		bool btrue = true;
		do
		{
			bool finishWaiting = true;
			for( int il = 1; il <=nLasers_; ++il)
			{
				if ( 0 == state[il])
				{
					pImpl_->pALC_REVLaser_->GetLaserState(il, state + il);
					switch( *(state + il))
					{
					case 0: // ALC_NOT_AVAILABLE ( 0) Laser is not Available
						finishWaiting = false;
						break;
					case 1: //ALC_WARM_UP ( 1) Laser Warming Up
						LogMessage(" laser " + boost::lexical_cast<std::string, int>(il)+ " is warming up", true);
						break;
					case 2: //ALC_READY ( 2) Laser is ready
						LogMessage(" laser " + boost::lexical_cast<std::string, int>(il)+  " is ready ", true);
						break;
					case 3: //ALC_INTERLOCK_ERROR ( 3) Interlock Error Detected
						LogMessage(" laser " + boost::lexical_cast<std::string, int>(il) + " encountered interlock error ", false);
						break;
					case 4: //ALC_POWER_ERROR ( 4) Power Error Detected
						LogMessage(" laser " + boost::lexical_cast<std::string, int>(il) + " encountered power error ", false);
						break;
					}
				}
			}
			if( finishWaiting)
				break;
			else
			{
				if (timerout.expired(GetCurrentMMTime()))
				{
					LogMessage(" some lasers did not respond", false);
					break;
				}
				iloop++;			
			}
			CDeviceUtils::SleepMs(100);
		}while(btrue);  

		//

		GenerateALCProperties();
		GenerateReadOnlyIDProperties();


	}
	catch (std::string& exs)
	{

		nRet = DEVICE_LOCALLY_DEFINED_ERROR;
		LogMessage(exs.c_str());
		SetErrorText(DEVICE_LOCALLY_DEFINED_ERROR,exs.c_str());
		//CodeUtility::DebugOutput(exs.c_str());
		return nRet;
	}

	initialized_ = true;
   return HandleErrors();

}





/////////////////////////////////////////////
// Property Generators
/////////////////////////////////////////////



void AndorLaserCombiner::GenerateALCProperties()
{
   std::string powerName;
   CPropertyActionEx* pAct; 
	std::ostringstream buildname;
	std::ostringstream stmp;


	// 1 based index for the lasers
	for( int il = 1; il < nLasers_+1; ++il)
	{
		buildname.str("");
		pAct = new CPropertyActionEx(this, &AndorLaserCombiner::OnPowerSetpoint, il);
		buildname << g_Keyword_PowerSetpoint << Wavelength(il);
		CreateProperty(buildname.str().c_str(), "0", MM::Float, false, pAct);

		float fullScale = 10.00;
		// set the limits as interrogated from the laser controller.
		LogMessage("Range for " + buildname.str()+"= [0," + boost::lexical_cast<std::string,float>(fullScale) + "]", true);
		SetPropertyLimits(buildname.str().c_str(), 0, fullScale);   // Volts

		buildname.str("");
		buildname << "MaximumLaserPower" << Wavelength(il);
		pAct = new CPropertyActionEx(this, &AndorLaserCombiner::OnMaximumLaserPower, il );
		stmp << fullScale;
		CreateProperty(buildname.str().c_str(), stmp.str().c_str(), MM::Integer, true, pAct);

		// readbacks
		buildname.str("");
		pAct = new CPropertyActionEx(this, &AndorLaserCombiner::OnPowerReadback, il);
		buildname <<  g_Keyword_PowerReadback << Wavelength(il);
		CreateProperty(buildname.str().c_str(), "0", MM::Float, true, pAct);

		// 'states'
		buildname.str("");
		pAct = new CPropertyActionEx(this, &AndorLaserCombiner::OnLaserState, il);
		buildname <<  "LaserState" << Wavelength(il);
		CreateProperty(buildname.str().c_str(), "0", MM::Integer, true, pAct);

	}
	

   CPropertyAction* pA = new CPropertyAction(this, &AndorLaserCombiner::OnDIN);
	stmp.str("");
	stmp << "0x" << std::hex << (unsigned short)DIN();
	CreateProperty("DIN",  stmp.str().c_str(),  MM::String, true, pA);

   if( multiPortUnitPresent_)  // the first two bits of DOUT are the laser output port
   {
      pA = new CPropertyAction(this, &AndorLaserCombiner::OnLaserPort);
      CreateProperty("LaserPort",  "B",  MM::String, false, pA);
      AddAllowedValue("LaserPort",  "A");
      AddAllowedValue("LaserPort",  "B");
      AddAllowedValue("LaserPort",  "C");
   } else {
	    // only create TTL 1 + 2 if no MPU present
      pA = new CPropertyAction(this, &AndorLaserCombiner::OnDOUT1);
      CreateProperty("DOUT1", "0", MM::Integer, false, pA);
      SetPropertyLimits("DOUT1", 0, 1);
      
      pA = new CPropertyAction(this, &AndorLaserCombiner::OnDOUT2);
      CreateProperty("DOUT2", "0", MM::Integer, false, pA);
      SetPropertyLimits("DOUT2", 0, 1);
    }



	pA = new CPropertyAction(this, &AndorLaserCombiner::OnDOUT);
	stmp.str("");
	stmp << "0x" << std::hex << (unsigned short)DOUT_;
	CreateProperty("DOUT",  stmp.str().c_str(),  MM::String, false, pA);

	pA = new CPropertyAction(this, &AndorLaserCombiner::OnDOUT3);
	CreateProperty("DOUT3", "0", MM::Integer, false, pA);
	SetPropertyLimits("DOUT3", 0, 1);
	
	pA = new CPropertyAction(this, &AndorLaserCombiner::OnDOUT4);
	CreateProperty("DOUT4", "0", MM::Integer, false, pA);
	SetPropertyLimits("DOUT4", 0, 1);
	
	pA = new CPropertyAction(this, &AndorLaserCombiner::OnDOUT5);
	CreateProperty("DOUT5", "0", MM::Integer, false, pA);
	SetPropertyLimits("DOUT5", 0, 1);
	
	pA = new CPropertyAction(this, &AndorLaserCombiner::OnDOUT6);
	CreateProperty("DOUT6", "0", MM::Integer, false, pA);
	SetPropertyLimits("DOUT6", 0, 1);
	
	pA = new CPropertyAction(this, &AndorLaserCombiner::OnDOUT7);
	CreateProperty("DOUT7", "0", MM::Integer, false, pA);
	SetPropertyLimits("DOUT7", 0, 1);
	
	pA = new CPropertyAction(this, &AndorLaserCombiner::OnDOUT8);
	CreateProperty("DOUT8", "0", MM::Integer, false, pA);
	SetPropertyLimits("DOUT8", 0, 1);
	
	pA = new CPropertyAction(this, &AndorLaserCombiner::OnNLasers);
	CreateProperty("NLasers",  (boost::lexical_cast<std::string,int>(nLasers_)).c_str(),  MM::Integer, true, pA);

}


void AndorLaserCombiner::GenerateReadOnlyIDProperties()
{
	CPropertyActionEx* pAct; 
	std::ostringstream buildname;
	// 1 based index
	for( int il = 1; il < nLasers_+1; ++il)
	{
		buildname.str("");
		buildname << "Hours"  << Wavelength(il);
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
		double v = PowerReadback((int)il);
		LogMessage(" PowerReadback" + boost::lexical_cast<std::string, long>(Wavelength(il)) + "  = " + boost::lexical_cast<std::string,double>(v), true);
      pProp->Set(v);
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
		powerSetpoint = (double)PowerSetpoint(il);
		LogMessage("from equipment: PowerSetpoint" + boost::lexical_cast<std::string, long>(Wavelength(il)) + "  = " + boost::lexical_cast<std::string,double>(powerSetpoint), true);
      pProp->Set(powerSetpoint);
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(powerSetpoint);
		LogMessage("to equipment: PowerSetpoint" + boost::lexical_cast<std::string, long>(Wavelength(il)) + "  = " + boost::lexical_cast<std::string,double>(powerSetpoint), true);
		PowerSetpoint( il, (double)powerSetpoint);
		if( openRequest_)
			SetOpen();

		//pProp->Set(achievedSetpoint);  ---- for quantization....
   }
   return HandleErrors();
}


int AndorLaserCombiner::OnHours(MM::PropertyBase* pProp, MM::ActionType eAct, long il)
{
   if (eAct == MM::BeforeGet)
   {
		int wval = 0;
		pImpl_->pALC_REVLaser_->GetLaserHours(il, &wval);
		LogMessage("Hours" + boost::lexical_cast<std::string, long>(Wavelength(il)) + "  = " + boost::lexical_cast<std::string,int>(wval), true);
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
		int val = PowerFullScale(il);
		LogMessage("PowerFullScale" + boost::lexical_cast<std::string, long>(Wavelength(il)) + "  = " + boost::lexical_cast<std::string,int>(val), true);
		pProp->Set((long)val);
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
		int val = Wavelength(il);
		LogMessage("Wavelength" + boost::lexical_cast<std::string, long>(il) + "  = " + boost::lexical_cast<std::string,int>(val), true);
		pProp->Set((long)val);
   }
   else if (eAct == MM::AfterSet)
   {
			// never do anything for a read only property!!
   }
   return HandleErrors();
}


// laser state

int AndorLaserCombiner::OnLaserState(MM::PropertyBase* pProp, MM::ActionType eAct, long il)
{

   if (eAct == MM::BeforeGet)
   {
		TLaserState v;
		pImpl_->pALC_REVLaser_->GetLaserState(il,&v);
		long lv = static_cast<long>(v);
		LogMessage(" LaserState" + boost::lexical_cast<std::string, long>(Wavelength(il)) + "  = " + boost::lexical_cast<std::string,long>(lv), true);
		pProp->Set(lv);
   }
   else if (eAct == MM::AfterSet)
   {
			// never do anything!!
   }
   return HandleErrors();
}



int AndorLaserCombiner::OnDIN(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet)
   {
		std::ostringstream s;
		s << "0x" << std::hex << (short)DIN();
		LogMessage("from equip. DIN = " + s.str(), true);
		pProp->Set(s.str().c_str());
   }
   else if (eAct == MM::AfterSet)
   {
		// this is a read-only property!
   }   
   return HandleErrors();
}


int AndorLaserCombiner::OnDOUT(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   
   static bool recursing = false;
   if( recursing)
      return DEVICE_OK;
	if (eAct == MM::BeforeGet)
   {
		std::ostringstream ss;
		ss << "0x" << std::hex << (unsigned short)DOUT_;
		pProp->Set(ss.str().c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      unsigned char valueOut;
		std::string sv;
		unsigned short cv; 
      pProp->Get(sv);
      // the string should be in range 0x00 to 0xFF
      size_t prefixLocation = sv.find("0x");
      if( std::string::npos != prefixLocation)
         sv.replace(prefixLocation,2,"");

         
		std::stringstream stream0;
		stream0 << std::hex << sv;
		stream0 >> cv;
		DOUT_ = (unsigned char)cv;

      // if the multi-port box is there, don't allow user access to the first two bits.
      if(multiPortUnitPresent_)
      {
         DOUT_ &= 0xFC;
         valueOut = DOUT_;
         recursing = true;
         pProp->Set((long)DOUT_);
         recursing = false;
         valueOut |= laserPort_;
      }
      else
         valueOut = DOUT_;

      std::ostringstream debugValue;

      debugValue << std::hex << (unsigned short)valueOut;

		LogMessage("to equip. valueOut = " + debugValue.str(), true);
		DOUT(valueOut);
   }   
   return HandleErrors();
}

int AndorLaserCombiner::OnDOUT1(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet) {
      long prop;
	  prop = DOUT_ & 1; // get just the lowest bit of the switch state
	  pProp->Set(prop);
   }
   else if (eAct == MM::AfterSet)
   {
      long prop;
	  pProp->Get(prop);
	  
	  if(prop){
		  DOUT_ |= prop;
	  } else {
		  DOUT_ &= 254; // clear just the lowest bit
	  }
	  DOUT(DOUT_);
   }
 
   return DEVICE_OK;
}

int AndorLaserCombiner::OnDOUT2(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet) {
      long prop;
	  prop = DOUT_ >> 1 & 1;
	  pProp->Set(prop);
   }
   else if (eAct == MM::AfterSet)
   {
      long prop;
	  pProp->Get(prop);
	  
	  if(prop){
		  DOUT_ |= prop << 1;
	  } else {
		  DOUT_ &= 253; 
	  }
	  DOUT(DOUT_);
   }

   return DEVICE_OK;
}

int AndorLaserCombiner::OnDOUT3(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet) {
      long prop;
	  prop = DOUT_ >> 2 & 1;
	  pProp->Set(prop);
   }
   else if (eAct == MM::AfterSet)
   {
      long prop;
	  pProp->Get(prop);
	  
	  if(prop){
		  DOUT_ |= prop  << 2;
	  } else {
		  DOUT_ &= 251; 
	  }
	  if(multiPortUnitPresent_)
      {
         DOUT_ &= 0xFC;
	  }
	  DOUT(DOUT_);
   }

   return DEVICE_OK;
}

int AndorLaserCombiner::OnDOUT4(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet) {
      long prop;
	  prop = DOUT_ >> 3 & 1;
	  pProp->Set(prop);
   }
   else if (eAct == MM::AfterSet)
   {
      long prop;
	  pProp->Get(prop);
	  
	  if(prop){
		  DOUT_ |= prop << 3;
	  } else {
		  DOUT_ &= 247; 
	  }
	  if(multiPortUnitPresent_)
      {
         DOUT_ &= 0xFC;
	  }
	  DOUT(DOUT_);
   }

   return DEVICE_OK;
}

int AndorLaserCombiner::OnDOUT5(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet) {
      long prop;
	  prop = DOUT_ >> 4 & 1;
	  pProp->Set(prop);
   }
   else if (eAct == MM::AfterSet)
   {
      long prop;
	  pProp->Get(prop);
	  
	  if(prop){
		  DOUT_ |= prop << 4;
	  } else {
		  DOUT_ &= 239; 
	  }
	  if(multiPortUnitPresent_)
      {
         DOUT_ &= 0xFC;
	  }
	  DOUT(DOUT_);
   }

   return DEVICE_OK;
}

int AndorLaserCombiner::OnDOUT6(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet) {
      long prop;
	  prop = DOUT_ >> 5 & 1;
	  pProp->Set(prop);
   }
   else if (eAct == MM::AfterSet)
   {
      long prop;
	  pProp->Get(prop);
	  
	  if(prop){
		  DOUT_ |= prop  << 5;
	  } else {
		  DOUT_ &= 223; 
	  }
	  if(multiPortUnitPresent_)
      {
         DOUT_ &= 0xFC;
	  }
	  DOUT(DOUT_);
   }

   return DEVICE_OK;
}

int AndorLaserCombiner::OnDOUT7(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet) {
      long prop;
	  prop = DOUT_ >> 6 & 1;
	  pProp->Set(prop);
   }
   else if (eAct == MM::AfterSet)
   {
      long prop;
	  pProp->Get(prop);
	  
	  if(prop){
		  DOUT_ |= prop  << 6;
	  } else {
		  DOUT_ &= 191; 
	  }
	  if(multiPortUnitPresent_)
      {
         DOUT_ &= 0xFC;
	  }
	  DOUT(DOUT_);
   }

   return DEVICE_OK;
}

int AndorLaserCombiner::OnDOUT8(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet) {
      long prop;
	  prop = DOUT_ >> 7 & 1;
	  pProp->Set(prop);
   }
   else if (eAct == MM::AfterSet)
   {
      long prop;
	  pProp->Get(prop);
	  
	  if(prop){
		  DOUT_ |= prop  << 7;
	  } else {
		  DOUT_ &= 127; 
	  }
	  DOUT(DOUT_);
   }

   return DEVICE_OK;
}

int AndorLaserCombiner::OnNLasers(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet)
   {
	   long v = nLasers_;
	   pProp->Set(v);
   }
   else if (eAct == MM::AfterSet)
   {
		// this is a read-only property!
   }   
   return HandleErrors();
}




int AndorLaserCombiner::OnMultiPortUnitPresent(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet)
   {
	   long v = (long)multiPortUnitPresent_;
	   pProp->Set(v);
   }
   else if (eAct == MM::AfterSet)
   {
		long v;
      pProp->Get(v);
      multiPortUnitPresent_ = (0==v?false:true);

   }   
   return HandleErrors();
}

int AndorLaserCombiner::OnLaserPort(MM::PropertyBase* pProp , MM::ActionType eAct)
{


   if(multiPortUnitPresent_)
   {
      if (eAct == MM::BeforeGet)
      {
	      char portLabel[2];
         portLabel[1] = 0;
         switch ( laserPort_)
         {
         case 0:
            portLabel[0] = 'B';
            break;
         case 1:
            portLabel[0] = 'C';
            break;
         case 2:
            portLabel[0] = 'A';
            break;
         default:
            portLabel[0] = 'B';
         }
               
	      pProp->Set(portLabel);
      }
      else if (eAct == MM::AfterSet)
      {  
         std::string value;
		   
         pProp->Get(value);
         laserPort_ = 0;
         if( 0 < value.length())
         {
            switch(value.at(0))
            {
            case 'A':
               laserPort_ = 2;
               break;
            case 'B':
               laserPort_ = 0;
               break;
            case 'C':
               laserPort_ = 1;
               break;
            }
         }

         // make sure lowest two bits weren't used
         unsigned char tvalue = DOUT_ & 0xFC;
         tvalue |= laserPort_;
         DOUT(tvalue);

      }
  }
   return DEVICE_OK;
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

    for( int il = 1; il <= this->nLasers_; ++il)
	{
		if(open)
		{
         double fullScale = 10.00; /* Volts instead of milliWatts, and  double instead of int */
         bool onn = ( 0 < PowerSetpoint(il))  && (0. < fullScale);
         double percentScale = 0.;
         if( onn)
            percentScale = 100.*PowerSetpoint(il)/fullScale;

         if( 100. < percentScale )
            percentScale = 100.;
         LogMessage("SetLas" + boost::lexical_cast<std::string, long>(il) + "  = " + boost::lexical_cast<std::string,double>(percentScale) + 
         "("+boost::lexical_cast<std::string,bool>(onn)+")" , true);

         TLaserState ttmp;
         pImpl_->pALC_REVLaser_->GetLaserState(il, &ttmp);
         if( onn && ( 2 != ttmp))
         {
            std::string messg = "Laser # " + boost::lexical_cast<std::string,int>(il) + " is not ready!";
            // laser is not ready!
            LogMessage(messg.c_str(), false);
           // GetCoreCallback()->PostError(std::make_pair<int,std::string>(DEVICE_ERR,messg));
         }

         if( ALC_NOT_AVAILABLE < ttmp)
         {
            LogMessage("setting Laser " + boost::lexical_cast<std::string,int>(Wavelength(il)) + " to " + boost::lexical_cast<std::string, double>(percentScale) + "% full scale", true);
            pImpl_->pALC_REVLaser_->SetLas_I( il,percentScale, onn );
         }

		}
		LogMessage("set shutter " + boost::lexical_cast<std::string, bool>(open), true);
		bool succ = pImpl_->pALC_REVLaser_->SetLas_Shutter(open);
      if( !succ)
         LogMessage("set shutter " + boost::lexical_cast<std::string, bool>(open) + " failed", false);
	}

 
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
	if( isnan(val))
	{
		LogMessage("invalid PowerReadback on # " + boost::lexical_cast<std::string,int>(laserIndex_a), false);
		val = 0.;
	}
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


PiezoStage::PiezoStage( const char* name) :
   name_(name),   
   pImpl_(NULL)
{

};

PiezoStage::~PiezoStage()
{
   Shutdown();
};

int PiezoStage::SetPositionUm(double pos)
{
   bool ret = pImpl_->pALC_REVPiezo_->SetPosition(pos);
   return (ret?DEVICE_OK:DEVICE_ERR);
}

int PiezoStage::GetPositionUm(double& pos)
{
   pos = 0.;
   bool ret = pImpl_->pALC_REVPiezo_->GetPosition(&pos);
   return (ret?DEVICE_OK:DEVICE_ERR);
//   return DEVICE_OK;
}
  
int PiezoStage::SetPositionSteps(long pos)
{
   double dpos = pos;
   return SetPositionUm(dpos);
}
  
int PiezoStage::SetRelativePositionUm(double pos)
{
   double curPos = 0.;

   pImpl_->pALC_REVPiezo_->GetPosition(&curPos);
   curPos += pos;
   if( curPos < 0.)
      curPos = 0.;
   if( PiezoRange() < curPos)
      curPos = PiezoRange();
   pImpl_->pALC_REVPiezo_->SetPosition(curPos);

   return DEVICE_OK;  
}

int PiezoStage::GetPositionSteps(long& steps)
{
   double posTmp = 0.;
   int ret = GetPositionUm( posTmp);
   steps = (long)( 0.5 + posTmp);
   return ret;

}

int PiezoStage::SetOrigin()
{
   return DEVICE_UNSUPPORTED_COMMAND;
}

int PiezoStage::GetLimits(double& min, double& max )
{
   min  = 0.;
   pImpl_->pALC_REVPiezo_->GetRange(&max);
   return DEVICE_OK;
}

void PiezoStage::GetName(char* Name) const
{
   CDeviceUtils::CopyLimitedString(Name, g_PiezoStageName );
}

int PiezoStage::Initialize()
{

   MMThreadGuard g( ImplLock_s);
   if(  NULL == pImplInstance_s)
   {
      pImpl_ = new ALCImpl();
      CDeviceUtils::SleepMs(100);
      pImplInstance_s = pImpl_;
   }
   else
      pImpl_ = pImplInstance_s;
   bool initialised = pImpl_->pALC_REVPiezo_->InitializePiezo();
	LogMessage(("in PiezoStage::InitializePiezo() ="+boost::lexical_cast<std::string,bool>(initialised)), true);
   CDeviceUtils::SleepMs(100);
// piezo properties:
	CPropertyAction* pA = new CPropertyAction(this, &PiezoStage::OnPiezoRange);
   std::ostringstream stmp;
	stmp.str("");
	PiezoRange(100.); // on startup default the range to 100.
	CDeviceUtils::SleepMs(500); 
	stmp << PiezoRange();
	CreateProperty("PiezoRange",  stmp.str().c_str(),  MM::Float, false, pA);

	pA = new CPropertyAction(this, &PiezoStage::OnPiezoPosition);
	stmp.str("");
	stmp << PiezoPosition();
 	CreateProperty("PiezoPosition",  stmp.str().c_str(),  MM::Float, false, pA);
   return DEVICE_OK;
}

int PiezoStage::Shutdown()
{
   // ensure we never make calls to implementation after 'shutdown'
   pImpl_ = NULL;
   return DEVICE_OK;
}

bool PiezoStage::Busy()
{
   return false;
}



int PiezoStage::OnPiezoRange(MM::PropertyBase* pProp, MM::ActionType eAct)
{   
	if (eAct == MM::BeforeGet)
   {
		float v = PiezoRange();
		LogMessage("from equip. PiezoRange= " + boost::lexical_cast<std::string,float>(v), true);
		pProp->Set((double)v);
   }
   else if (eAct == MM::AfterSet)
   {
		double r = 0.;
      pProp->Get(r);
		LogMessage("to equip. PiezoRange= " + boost::lexical_cast<std::string,float>((float)r), true);
		PiezoRange((float)r);
   }   
   return DEVICE_OK;
}

int PiezoStage::OnPiezoPosition(MM::PropertyBase* pProp, MM::ActionType eAct)
{	
	if (eAct == MM::BeforeGet)
   {
		float v = PiezoPosition();
		LogMessage("from equip. PiezoPosition= " + boost::lexical_cast<std::string,float>(v), true);
      pProp->Set((double)v);
   }
   else if (eAct == MM::AfterSet)
   {
		double p = 0.;
      pProp->Get(p);
		LogMessage("to equip. PiezoPosition= " + boost::lexical_cast<std::string,float>((float)p), true);
		PiezoPosition((float)p);
   }   
   return DEVICE_OK;
}


float PiezoStage::PiezoRange(void)
{
	double dtmp = 0.;
   if( NULL != pImpl_)
   {
	   pImpl_->pALC_REVPiezo_->GetRange(&dtmp);
	   if( isnan(dtmp))
	   {
		   LogMessage("invalid PiezoRange!",  false);
		   dtmp = 0.;
	   }
   }
	return (float)dtmp;
}

void PiezoStage::PiezoRange(const float val)
{
	if( NULL != pImpl_) 
      pImpl_->pALC_REVPiezo_->SetRange( (const double)val);
}


float PiezoStage::PiezoPosition(void)
{
	double dtmp = 0.;

if( NULL != pImpl_)
{
	pImpl_->pALC_REVPiezo_->GetPosition(&dtmp);
	if( isnan(dtmp))
	{
		LogMessage("invalid PiezoPosition!" , false);
		dtmp = 0.;
	}
	
}return (float)dtmp;}

void PiezoStage::PiezoPosition(const float val)
{
   if( NULL != pImpl_)
   {
	   pImpl_->pALC_REVPiezo_->SetPosition((const double)val);
   }
}






