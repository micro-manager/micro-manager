///////////////////////////////////////////////////////////////////////////////
// FILE:          XYStage.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   ASR100B120B-T3 XY Stage
//                
// AUTHOR:        David Goosen
//                
// COPYRIGHT:     Zaber Technologies, 2013
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
   #define snprintf _snprintf 
   #pragma warning(disable: 4355)
#endif

#include "Zaber.h"
#include "XYStage.h"
#include <ModuleInterface.h>
#include <sstream>
#include <string>

using namespace std;

const char *XYStageName = "XYStage";

/*
* XYStage - two axis stage device.
* Note that this adapter uses two coordinate systems.  There is the adapters own coordinate
* system with the X and Y axis going the 'Micro-Manager standard' direction
* Then, there is the Zaber native system.  All functions using 'steps' use the Zaber system
* All functions using um use the Micro-Manager coordinate system
*/

// CONSTRUCTOR
XYStage::XYStage() :
   CXYStageBase<XYStage>(),
   ZaberBase(this),
   range_measured_(false),
   answerTimeoutMs_(2000),
   stepSizeXUm_(0.15625), //=1000*pitch[mm]/(motorsteps*64)  (for ASR100B120B: pitch=2 mm, motorsteps=200)
   stepSizeYUm_(0.15625),
   speedX_(0.0), //[mm/s]
   speedY_(0.0), //[mm/s]
   accelX_(0.0), //[m/s²]
   accelY_(0.0), //[m/s²]
   originX_(0),
   originY_(0)

{
   this->LogMessage("XYStage::XYStage\n", true);
   InitializeDefaultErrorMessages();

   // create pre-initialization properties
   // ------------------------------------
   // NOTE: pre-initialization properties contain parameters which must be defined for
   // proper startup

   // Name, read-only (RO)
   CreateProperty(MM::g_Keyword_Name, XYStageName, MM::String, true);

   // Description, RO
   CreateProperty(MM::g_Keyword_Description, "Zaber XY stage driver adapter", MM::String, true);

   // Port
   CPropertyAction* pAct = new CPropertyAction (this, &XYStage::OnPort);
   CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);

   // Create device number property
   pAct = new CPropertyAction (this, &XYStage::OnDeviceNum);
   CreateProperty("Controller Device Number", "01", MM::Integer, false, pAct, true);

   //Transpose Properities
   this->SetProperty(MM::g_Keyword_Transpose_MirrorX, "1");
   this->SetProperty(MM::g_Keyword_Transpose_MirrorY, "1");
}

// DESTRUCTOR
XYStage::~XYStage()
{
   this->LogMessage("XYStage::~XYStage\n", true);
   Shutdown();
}

////////////////////////////////////////////////////////////////////////////////
// XYStage methods required by the API
///////////////////////////////////////////////////////////////////////////////

void XYStage::GetName(char* Name) const
{
   CDeviceUtils::CopyLimitedString(Name, XYStageName);
}


int XYStage::Initialize()
{
   CPropertyAction* pAct;

   this->LogMessage("XYStage::Initialize\n", true);
   int ret = CheckDeviceStatus();
   if (ret != DEVICE_OK) 
      return ret;
 
   // Initialize Speed (in mm/s)
   pAct = new CPropertyAction (this, &XYStage::OnSpeedX);
   ret = CreateProperty("SpeedX [mm/s]", "0.0", MM::Float, false, pAct); // mm/s
   if (ret != DEVICE_OK) return ret;
   SetPropertyLimits("SpeedX [mm/s]", 0.00, 100.0); // mm/s

   pAct = new CPropertyAction (this, &XYStage::OnSpeedY);
   ret = CreateProperty("SpeedY [mm/s]", "0.0", MM::Float, false, pAct); // mm/s
   if (ret != DEVICE_OK) return ret;
   SetPropertyLimits("SpeedY [mm/s]", 0.00, 100.0); // mm/s

   // Initialize Acceleration (in m/s²)
   pAct = new CPropertyAction (this, &XYStage::OnAccelX);
   ret = CreateProperty("Acceleration X [m/s^2]", "0.0", MM::Float, false, pAct);
   if (ret != DEVICE_OK) return ret;
   SetPropertyLimits("Acceleration X [m/s^2]", 0.00, 2.0);

   pAct = new CPropertyAction (this, &XYStage::OnAccelY);
   ret = CreateProperty("Acceleration Y [m/s^2]", "0.0", MM::Float, false, pAct);
   if (ret != DEVICE_OK) return ret;
   SetPropertyLimits("Acceleration Y [m/s^2]", 0.00, 2.0);

   // for debugging
   ostringstream os;
   os << "Device 1 peripheral ID = "<< peripheralID1_ << "\n" << "Device 2 peripheral ID = " << peripheralID2_ <<"\n";
   this->LogMessage(os.str().c_str());

   ret = UpdateStatus();
   if (ret != DEVICE_OK) return ret;

   initialized_ = true;
   return DEVICE_OK;
}

int XYStage::Shutdown()
{
   this->LogMessage("XYStage::Shutdown\n", true);

   initialized_    = false;
   range_measured_ = false;
  
   return DEVICE_OK;
}

bool XYStage::Busy()
// Returns true if any axis (X or Y) is still moving.
{
   this->LogMessage("XYStage::Busy\n", true);
   
   string resp;
   int ret;

   // format the command
   ostringstream cmd;
   cmd << cmdPrefix_<< "0";

   // send command
   ret = QueryCommand(cmd.str().c_str(), resp);
   if (ret != DEVICE_OK)
   {
      ostringstream os;
      os << "SendSerialCommand failed in XYStage::Busy, error code:" << ret;
      this->LogMessage(os.str().c_str(), false);
      // return false; // can't write, continue just so that we can read an answer in case write succeeded even though we received an error
   }
   if (resp.find("BUSY")!=string::npos) return true;
   else return false;
}


int XYStage::SetPositionSteps(long x, long y)
{
   this->LogMessage("XYStage::SetPositionSteps\n", true);

   //format the commands
   ostringstream cmdX;
   ostringstream cmdY;
   cmdX << cmdPrefix_ << "2 move abs " << x;
   cmdY << cmdPrefix_ << "1 move abs " << y;

   //clear warning flags
   string resp;
   int ret;
   ostringstream cmd;
   cmd << cmdPrefix_ << "warnings clear";
   ret = QueryCommand(cmd.str().c_str(),resp); 
   if (ret != DEVICE_OK) return ret;
   if (resp.length() < 1) return  DEVICE_NOT_CONNECTED;

   //send the x command
   ret = QueryCommand(cmdX.str().c_str(),resp);
   if (ret != DEVICE_OK) return ret;
 
   //send the y command
   ret = QueryCommand(cmdY.str().c_str(),resp);
   if (ret != DEVICE_OK) return ret;

   //get status
   ret = QueryCommand(cmdPrefix_.c_str(), resp);
   if (ret != DEVICE_OK) return ret;
   if (resp.length() < 1) return DEVICE_SERIAL_INVALID_RESPONSE;

   //check for errors
   if (resp.substr(14,2)!= "--") return DEVICE_SERIAL_INVALID_RESPONSE;
   return DEVICE_OK;
}

int XYStage::SetRelativePositionSteps(long x, long y)
{   
   this->LogMessage("XYStage::SetRelativePositionSteps\n", true);
 
   //format the commands
   ostringstream cmdX;
   ostringstream cmdY;
   cmdX << cmdPrefix_ << "2 move rel " << x;
   cmdY << cmdPrefix_ << "1 move rel " << y;

   //clear warning flags
   string resp;
   int ret;
   ostringstream cmd;
   cmd << cmdPrefix_ << "warnings clear";
   ret = QueryCommand(cmd.str().c_str(),resp); 
   if (ret != DEVICE_OK) return ret;
   if (resp.length() < 1) return  DEVICE_NOT_CONNECTED;

   //send the x command
   ret = QueryCommand(cmdX.str().c_str(),resp);
   if (ret != DEVICE_OK) return ret;

   //send the y command
   ret = QueryCommand(cmdY.str().c_str(),resp);
   if (ret != DEVICE_OK) return ret;

   //get status
   ret = QueryCommand(cmdPrefix_.c_str(), resp);
   if (ret != DEVICE_OK) return ret;
   if (resp.length() < 1) return DEVICE_SERIAL_INVALID_RESPONSE;

   //check for errors
   if (resp.substr(14,2)!= "--") return DEVICE_SERIAL_INVALID_RESPONSE;
   return DEVICE_OK;
}

int XYStage::GetPositionSteps(long& x, long& y)
{
   this->LogMessage("XYStage::GetPositionSteps\n", true);

   int ret;
   string respPos;
   string resp;

   //clear warning flags  
   /*
   ostringstream cmdW;
   cmdW << cmdPrefix_ << "warnings clear";
   ret = QueryCommand(cmdW.str().c_str(),resp); 
   if (ret != DEVICE_OK) return ret;
   if (resp.length() < 1) return  DEVICE_NOT_CONNECTED;
   */

   //send command
   ostringstream cmd;
   cmd << cmdPrefix_ << "get pos";
   ret = QueryCommand(cmd.str().c_str(), respPos);
   if (ret != DEVICE_OK) return ret;
   if (respPos.length() < 1) return DEVICE_SERIAL_INVALID_RESPONSE;

   //parse response
   string posStringX=respPos.substr(respPos.find(" ", 17)+1,string::npos);
   string posStringY=respPos.substr(17,respPos.find(" ", 17)-17);
   
   //check for errors
   /* if (respPos.substr(14,2)== "WR") return DEVICE_UNKNOWN_POSITION;
   else if (respPos.substr(14,2)!= "--") return DEVICE_SERIAL_INVALID_RESPONSE;
   */
   
   stringstream(posStringX) >> x;
   stringstream(posStringY) >> y;

   return DEVICE_OK;
}

int XYStage::SetOrigin()
//Sets the Origin of the MM coordinate system using default implementation of SetAdapterOriginUm(x,y) (in DeviceBase.h)
//The Zaber coordinate system is NOT zeroed. 
{
   this->LogMessage("XYStage::SetOrigin\n", true); 
   this->LogMessage("XYStage::SetOrigin Calling SetAdapterOriginUm(0,0)\n", true); 
 
   return SetAdapterOriginUm(0,0);
}


int XYStage::Home()
{
   this->LogMessage("XYStage::Home\n", true);

   range_measured_ = false;
   string resp;

   // clear warning flags  
   int ret;
   ostringstream cmdW;
   cmdW << cmdPrefix_ << "warnings clear";
   ret = QueryCommand(cmdW.str().c_str(),resp); 
   if (ret != DEVICE_OK) return ret;
   if (resp.length() < 1) return  DEVICE_NOT_CONNECTED;

   // send the findrange command
   ostringstream cmd;
   cmd << cmdPrefix_ << "tools findrange";
   ret = QueryCommand(cmd.str().c_str(),resp);
   if (ret != DEVICE_OK) return ret;

   bool status;
   int numTries=0, maxTries=400;
   long pollIntervalMs = 100;

   this->LogMessage("Starting read in XY-Stage FINDRANGE\n", true);

   do
   {
      status = Busy();
      numTries++;
      CDeviceUtils::SleepMs(pollIntervalMs);
   }
   while(status && (numTries < maxTries)); // keep trying up to maxTries * pollIntervalMs ( = 20 sec)

   ostringstream os;
   os << "Tried reading "<< numTries << " times, and finally read " << status;
   this->LogMessage(os.str().c_str());

   range_measured_ = true;

   this->LogMessage("XYStage::Home COMPLETE!\n", true);

   //get status
   ret = QueryCommand(cmdPrefix_.c_str(), resp);
   if (ret != DEVICE_OK) return ret;
   if (resp.length() < 1) return DEVICE_SERIAL_INVALID_RESPONSE;

   //check for errors
   if (resp.substr(14,2)!= "--") return DEVICE_SERIAL_INVALID_RESPONSE;
   return DEVICE_OK;
}

int XYStage::Stop()
{
   this->LogMessage("XYStage::Stop\n", true);
   string resp;
   ostringstream cmd;
   cmd << cmdPrefix_ << "stop";
   int ret = QueryCommand(cmd.str().c_str(),resp);
   if (ret != DEVICE_OK) return ret;
   return ret;
}

int XYStage::GetLimitsUm(double& xMin, double& xMax, double& yMin, double& yMax)
{
   this->LogMessage("XYStage::GetLimitsUm\n", true); 
  
   if (!range_measured_) return DEVICE_UNKNOWN_POSITION;
  
   int ret;
   string resp;

   //get min limits  
   ostringstream cmdMin;
   cmdMin << cmdPrefix_ << "get limit.min";
   ret = QueryCommand(cmdMin.str().c_str(),resp);
   if (ret != DEVICE_OK) return ret;
   if (resp.length() < 1) return DEVICE_SERIAL_INVALID_RESPONSE;
   
   string limStringXMin=resp.substr(resp.find(" ", 17)+1,string::npos);
   string limStringYMin=resp.substr(17,resp.find(" ", 17)-17);
   int limDataXMin, limDataYMin;
   stringstream(limStringXMin) >> limDataXMin;
   stringstream(limStringYMin) >> limDataYMin;
   xMin=limDataXMin;
   yMin=limDataYMin;

   //get max limits
   ostringstream cmdMax;
   cmdMax << cmdPrefix_ << "get limit.max";
   ret = QueryCommand(cmdMax.str().c_str(),resp);
   if (ret != DEVICE_OK) return ret;
   if (resp.length() < 1) return DEVICE_SERIAL_INVALID_RESPONSE;

   string limStringXMax=resp.substr(resp.find(" ", 17)+1,string::npos);
   string limStringYMax=resp.substr(17,resp.find(" ", 17)-17);
   int limDataXMax, limDataYMax;
   stringstream(limStringXMax) >> limDataXMax;
   stringstream(limStringYMax) >> limDataYMax;
   xMax=limDataXMax;
   yMax=limDataYMax;

   return DEVICE_OK;
}

int XYStage::Move(double vx, double vy)
{
   this->LogMessage("XYStage::Move\n", true);
   // move command in mm/s

   // convert vx and vy into Zaber data values for ASR100B120B (.15625 um/u-step)
   double vx_ = vx*10485.76;
   double vy_ = vy*10485.76;

   //format the commands
   ostringstream cmdX;
   ostringstream cmdY;
   cmdX << cmdPrefix_ << "2 move vel " << vx_;
   cmdY << cmdPrefix_ << "1 move vel " << vy_;

   // clear warning flags  
   int ret;
   string resp;
   ostringstream cmdW;
   cmdW << cmdPrefix_ << "warnings clear";
   ret = QueryCommand(cmdW.str().c_str(),resp); 
   if (ret != DEVICE_OK) return ret;
   if (resp.length() < 1) return  DEVICE_NOT_CONNECTED;

   //send x and y commands
   ret = QueryCommand(cmdX.str().c_str(),resp);
   if (ret != DEVICE_OK) return ret;
   
   ret = QueryCommand(cmdY.str().c_str(),resp);
   if (ret != DEVICE_OK) return ret;

   //get status
   ret = QueryCommand(cmdPrefix_.c_str(), resp);
   if (ret != DEVICE_OK) return ret;
   if (resp.length() < 1) return DEVICE_SERIAL_INVALID_RESPONSE;

   //check for errors
   if (resp.substr(14,2)!= "--") return DEVICE_SERIAL_INVALID_RESPONSE;
   return DEVICE_OK;
}

int XYStage::GetStepLimits(long& xMin, long& xMax, long& yMin, long& yMax)
{
   this->LogMessage("XYStage::GetStepLimits\n", true);
   if (!range_measured_) return DEVICE_UNKNOWN_POSITION;

   int ret;
   string resp;

   //get min limits  
   ostringstream cmdMin;
   cmdMin << cmdPrefix_ << "get limit.min";
   ret = QueryCommand(cmdMin.str().c_str(),resp);
   if (ret != DEVICE_OK) return ret;
   if (resp.length() < 1) return DEVICE_SERIAL_INVALID_RESPONSE;

   string limStringXMin=resp.substr(resp.find(" ", 17)+1,string::npos);
   string limStringYMin=resp.substr(17,resp.find(" ", 17)-17);
   stringstream(limStringXMin) >> xMin;
   stringstream(limStringYMin) >> yMin;


   //get max limits
   ostringstream cmdMax;
   cmdMax << cmdPrefix_ << "get limit.max";
   ret = QueryCommand(cmdMax.str().c_str(),resp);
   if (ret != DEVICE_OK) return ret;
   if (resp.length() < 1) return DEVICE_SERIAL_INVALID_RESPONSE;

   string limStringXMax=resp.substr(resp.find(" ", 17)+1,string::npos);
   string limStringYMax=resp.substr(17,resp.find(" ", 17)-17);

   stringstream(limStringXMax) >> xMax;
   stringstream(limStringYMax) >> yMax;

   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers
// Handle changes and updates to property values.
///////////////////////////////////////////////////////////////////////////////

int XYStage::OnPort(MM::PropertyBase* pProp, MM::ActionType eAct)
{  
   ostringstream os;
   os << "XYStage::OnPort(" << pProp << ", " << eAct << ")\n";
   this->LogMessage(os.str().c_str(), false);


   if (eAct == MM::BeforeGet)
   {
      pProp->Set(port_.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      if (initialized_)
      {
         // revert
         pProp->Set(port_.c_str());
         return ERR_PORT_CHANGE_FORBIDDEN;
      }

      pProp->Get(port_);
   }

   return DEVICE_OK;
}

int XYStage::OnSpeedX(MM::PropertyBase* pProp, MM::ActionType eAct)
{ 
   this->LogMessage("XYStage::OnSpeedX\n", true);

   if (eAct == MM::BeforeGet)
   {
      // get maxspeed from controller
      ostringstream cmd;
      cmd << cmdPrefix_ << "2 get maxspeed";
      string respSpeed;
      int ret = QueryCommand(cmd.str().c_str(), respSpeed);
      if (ret != DEVICE_OK) return ret;
      if (respSpeed.length() < 1) return DEVICE_SERIAL_INVALID_RESPONSE;
   
      // convert to mm/s
      string speedString=respSpeed.substr(17,respSpeed.length()-17);
      int speedDataX;
      stringstream(speedString) >> speedDataX;
      double speedX_ = (speedDataX/1.6384)*stepSizeXUm_/1000;
      pProp->Set(speedX_);
   }
   else if (eAct == MM::AfterSet)
   {
      // get maxspeed from MM property
      double uiSpeed;
      pProp->Get(uiSpeed);
      double speed = uiSpeed;
    
      // convert to data
      double speedData = (speed*1.6384*1000/stepSizeXUm_);
   
      //format the command
      ostringstream cmd; 
      cmd << cmdPrefix_ << "2 set maxspeed " << speedData;
   
      // clear warning flags 
      string resp;
      ostringstream cmdW;
      cmdW << cmdPrefix_ << "warnings clear";
      int ret = QueryCommand(cmdW.str().c_str(),resp); 
      if (ret != DEVICE_OK) return ret;
      if (resp.length() < 1) return  DEVICE_NOT_CONNECTED;

      //send the command
      ret = QueryCommand(cmd.str().c_str(),resp);
      if (ret != DEVICE_OK) return ret;
      if (resp.length() < 1) return DEVICE_SERIAL_INVALID_RESPONSE;
      
      //get status
      ret = QueryCommand(cmdPrefix_.c_str(), resp);
      if (ret != DEVICE_OK) return ret;
      if (resp.length() < 1) return DEVICE_SERIAL_INVALID_RESPONSE;

      //check for errors
      if (resp.substr(14,2)!= "--") return DEVICE_SERIAL_INVALID_RESPONSE;

      speedX_ = speed;
   }
   return DEVICE_OK;
}

int XYStage::OnSpeedY(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   this->LogMessage("XYStage::OnSpeedY\n", true);

   if (eAct == MM::BeforeGet)
   {
      // get maxspeed from controller
      ostringstream cmd;
      cmd << cmdPrefix_ << "1 get maxspeed";
      string respSpeed;
      int ret = QueryCommand(cmd.str().c_str(), respSpeed);
      if (ret != DEVICE_OK) return ret;
      if (respSpeed.length() < 1) return DEVICE_SERIAL_INVALID_RESPONSE;


      // convert to mm/s
      string speedString=respSpeed.substr(17,respSpeed.length()-17);
      double speedDataY;
      stringstream(speedString) >> speedDataY;
      double speedY_ = (speedDataY/1.6384)*stepSizeYUm_/1000;
      pProp->Set(speedY_);
   }
   else if (eAct == MM::AfterSet)
   {
      // get maxspeed from MM property
      double uiSpeed;
      pProp->Get(uiSpeed);
      double speed = uiSpeed;
    
      // convert to data
      double speedData = (speed*1.6384*1000/stepSizeYUm_);
         
      //format the command
      ostringstream cmd; 
      cmd << cmdPrefix_ << "1 set maxspeed " << speedData;
   
      // clear warning flags 
      string resp;
      ostringstream cmdW;
      cmdW << cmdPrefix_ << "warnings clear";
      int ret = QueryCommand(cmdW.str().c_str(),resp); 
      if (ret != DEVICE_OK) return ret;
      if (resp.length() < 1) return  DEVICE_NOT_CONNECTED;

      //send the command
      ret = QueryCommand(cmd.str().c_str(),resp);
      if (ret != DEVICE_OK) return ret;
      if (resp.length() < 1) return DEVICE_SERIAL_INVALID_RESPONSE;
      
      //get status
      ret = QueryCommand(cmdPrefix_.c_str(), resp);
      if (ret != DEVICE_OK) return ret;
      if (resp.length() < 1) return DEVICE_SERIAL_INVALID_RESPONSE;

      //check for errors
      if (resp.substr(14,2)!= "--") return DEVICE_SERIAL_INVALID_RESPONSE;
      
      speedY_ = speed;
   }
   return DEVICE_OK;
}

int XYStage::OnAccelX(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   this->LogMessage("XYStage::OnAccelX\n", true);

   if (eAct == MM::BeforeGet)
   {
      // get accel from controller
      ostringstream cmd;
      cmd << cmdPrefix_ << "2 get accel";
      string respAccel;
      int ret = QueryCommand(cmd.str().c_str(), respAccel);
      if (ret != DEVICE_OK) return ret;
      if (respAccel.length() < 1) return DEVICE_SERIAL_INVALID_RESPONSE;
   
      // convert to m/s²
      string accelString=respAccel.substr(17,respAccel.length()-17);
      double accelDataX;
      stringstream(accelString) >> accelDataX;
      double accelX_ = (accelDataX*10/1.6384)*stepSizeXUm_/1000;
      pProp->Set(accelX_);
   }
   else if (eAct == MM::AfterSet)
   {
      // get accel from MM property
      double accel;
      pProp->Get(accel);
      if (accel < 0.001)  accel = 0.001; //clipping to useful values
      if (accel > 10.0 )  accel = 10.0;

      // convert to data
      double accelData = accel*1.6384*100/(stepSizeXUm_);
      
      //format the command
      ostringstream cmd; 
      cmd << cmdPrefix_ << "2 set accel " << accelData;
   
      //clear warning flags 
      string resp;
      ostringstream cmdW;
      cmdW << cmdPrefix_ << "warnings clear";
      int ret = QueryCommand(cmdW.str().c_str(),resp); 
      if (ret != DEVICE_OK) return ret;
      if (resp.length() < 1) return  DEVICE_NOT_CONNECTED;

      //send the command
      ret = QueryCommand(cmd.str().c_str(),resp);
      if (ret != DEVICE_OK) return ret;
    
      //get status
      ret = QueryCommand(cmdPrefix_.c_str(), resp);
      if (ret != DEVICE_OK) return ret;
      if (resp.length() < 1) return DEVICE_SERIAL_INVALID_RESPONSE;

      //check for errors
      if (resp.substr(14,2)!= "--") return DEVICE_SERIAL_INVALID_RESPONSE;

      accelX_ = accel;
   }
   return DEVICE_OK;
}

int XYStage::OnAccelY(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   this->LogMessage("XYStage::OnAccelY\n", true);

   if (eAct == MM::BeforeGet)
   {
      // get accel from controller
   ostringstream cmd;
      cmd << cmdPrefix_ << "1 get accel";
      string respAccel;
      int ret = QueryCommand(cmd.str().c_str(), respAccel);
      if (ret != DEVICE_OK) return ret;
      if (respAccel.length() < 1) return DEVICE_SERIAL_INVALID_RESPONSE;
   
      // convert to m/s²
      string accelString=respAccel.substr(17,respAccel.length()-17);
      double accelDataY;
      stringstream(accelString) >> accelDataY;
      double accelY_ = (accelDataY*10/1.6384)*stepSizeYUm_/1000;
      pProp->Set(accelY_);
   }
   else if (eAct == MM::AfterSet)
   {
      // get accel from MM property
      double accel;
      pProp->Get(accel);
      if (accel < 0.001)  accel =  0.001; //clipping to useful values
      if (accel > 10.0 )  accel = 10.0;

      // convert to data
      double accelData = accel*1.6384*100/(stepSizeYUm_);
      
      //format the command
      ostringstream cmd; 
      cmd << cmdPrefix_ << "1 set accel " << accelData;
   
      //clear warning flags 
      string resp;
      ostringstream cmdW;
      cmdW << cmdPrefix_ << "warnings clear";
      int ret = QueryCommand(cmdW.str().c_str(),resp); 
      if (ret != DEVICE_OK) return ret;
      if (resp.length() < 1) return  DEVICE_NOT_CONNECTED;

      //send the command
      ret = QueryCommand(cmd.str().c_str(),resp);
      if (ret != DEVICE_OK) return ret;
    
      //get status
      ret = QueryCommand(cmdPrefix_.c_str(), resp);
      if (ret != DEVICE_OK) return ret;
      if (resp.length() < 1) return DEVICE_SERIAL_INVALID_RESPONSE;

      //check for errors
      if (resp.substr(14,2)!= "--") return DEVICE_SERIAL_INVALID_RESPONSE;

      accelY_ = accel;
   }
   return DEVICE_OK;
}

int XYStage::OnDeviceNum(MM::PropertyBase* pProp, MM::ActionType eAct)
{ 
   if (eAct == MM::AfterSet)
   {
      double deviceNum;
      pProp->Get(deviceNum);
      deviceNum_=(long) deviceNum;
     
      ostringstream dNumString;
      dNumString << "/" << deviceNum_ <<" ";
      cmdPrefix_=dNumString.str();
   }
   else if (eAct == MM::BeforeGet)
   {
      pProp->Set(deviceNum_);
   }
   return DEVICE_OK;
}