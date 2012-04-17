///////////////////////////////////////////////////////////////////////////////
// FILE:          NI100X.cpp
// PROJECT:       100X micro-manager extensions
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   NI control with multiple devices
//                
// AUTHOR:        Nenad Amodaj, January 2010
//
// COPYRIGHT:     100X Imaging Inc, 2010
//                

#ifdef WIN32
   #define WIN32_LEAN_AND_MEAN
   #include <windows.h>
   #define snprintf _snprintf 
#endif

#include <sstream>
#include "NI100X.h"
#include "ModuleInterface.h"

const char* g_DeviceNameDigitalIO = "DigitalIO";
const char* g_DeviceNameShutter = "Shutter";
const char* g_DeviceNameAnalogIO = "AnalogIO";

const char* g_PropertyVolts = "Volts";
const char* g_PropertyMinVolts = "MinVolts";
const char* g_PropertyMaxVolts = "MaxVolts";
const char* g_PropertyChannel = "IOChannel";

const char* g_PropertyDepthIdx = "DepthIndex";
const char* g_PropertyListIdx = "ListIndex";
const char* g_PropertyZ = "Z";
const char* g_PropertyVD = "VD";
const char* g_PropertyDepthListSize = "DepthListSize";
const char* g_DepthControl = "DepthControl";
const char* g_DepthList = "DepthList";

const char* g_PI_ZStageDeviceName = "PIZStage";
const char* g_PI_ZStageAxisName = "Axis";
const char* g_PropertyMaxUm = "MaxZ_um";
const char* g_PropertyWaitForResponse = "WaitForResponse";

const char* g_PropertyDemo = "Demo";

const char* g_Yes = "Yes";
const char* g_No = "No";

const char* g_PIGCS_ZStageDeviceName = "PIGCSZStage";
const char* g_PI_ZStageAxisLimitUm = "Limit_um";

// Global state of the digital IO to enable simulation of the shutter device.
// The virtual shutter device uses this global variable to restore state of the switch
unsigned g_digitalState = 0;
unsigned g_shutterState = 0;

using namespace std;

set<string> g_analogDevs;

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
   AddAvailableDeviceName(g_DeviceNameDigitalIO, "NI digital IO");
   AddAvailableDeviceName(g_DeviceNameAnalogIO, "NI analog IO");
   AddAvailableDeviceName(g_PI_ZStageDeviceName, "PI E-662 Z-stage");
   AddAvailableDeviceName(g_PIGCS_ZStageDeviceName, "PI GCS Z-stage");
   //AddAvailableDeviceName(g_DeviceNameShutter);
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
   if (deviceName == 0)
      return 0;

   if (strcmp(deviceName, g_DeviceNameDigitalIO) == 0)
   {
      return new DigitalIO;
   }
   else if (strcmp(deviceName, g_DeviceNameAnalogIO) == 0)
   {
      return new AnalogIO;
   }
   else if (strcmp(deviceName, g_PI_ZStageDeviceName) == 0)
   {
      return new PIZStage;
   }
   else if (strcmp(deviceName, g_PIGCS_ZStageDeviceName) == 0)
   {
      return new PIGCSZStage;
   }
   

   return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}

///////////////////////////////////////////////////////////////////////////////
// DigitalIO implementation
// ~~~~~~~~~~~~~~~~~~~~~~~~

DigitalIO::DigitalIO() : numPos_(16), busy_(false), channel_("undef"), task_(0), state_(0)
{
   InitializeDefaultErrorMessages();

   // add custom error messages
   SetErrorText(ERR_UNKNOWN_POSITION, "Invalid position (state) specified");
   SetErrorText(ERR_INITIALIZE_FAILED, "Initialization of the device failed");
   SetErrorText(ERR_WRITE_FAILED, "Failed to write data to the device");
   SetErrorText(ERR_CLOSE_FAILED, "Failed closing the device");

   CPropertyAction* pAct = new CPropertyAction (this, &DigitalIO::OnChannel);
   int nRet = CreateProperty(g_PropertyChannel, "devname", MM::String, false, pAct, true);
   assert(nRet == DEVICE_OK);

}

DigitalIO::~DigitalIO()
{
   Shutdown();
}

void DigitalIO::GetName(char* name) const
{
   CDeviceUtils::CopyLimitedString(name, g_DeviceNameDigitalIO);
}


int DigitalIO::Initialize()
{
   // set property list
   // -----------------
   
   // Name
   int nRet = CreateProperty(MM::g_Keyword_Name, g_DeviceNameDigitalIO, MM::String, true);
   if (DEVICE_OK != nRet)
      return nRet;

   // Description
   nRet = CreateProperty(MM::g_Keyword_Description, "Digital IO adapter", MM::String, true);
   if (DEVICE_OK != nRet)
      return nRet;

   // create positions and labels
   const int bufSize = 1024;
   char buf[bufSize];
   for (long i=0; i<numPos_; i++)
   {
      snprintf(buf, bufSize, "%d", (unsigned)i);
      SetPositionLabel(i, buf);
   }

   // State
   // -----
   CPropertyAction* pAct = new CPropertyAction (this, &DigitalIO::OnState);
   nRet = CreateProperty(MM::g_Keyword_State, "0", MM::Integer, false, pAct);
   if (nRet != DEVICE_OK)
      return nRet;

   // set up task
   // -----------
   long niRet = DAQmxCreateTask("", &task_);
   if (niRet != DAQmxSuccess)
      return (int)niRet;

   niRet = DAQmxCreateDOChan(task_, channel_.c_str(), "", DAQmx_Val_ChanForAllLines);
   if (niRet != DAQmxSuccess)
      return (int)niRet;

	niRet = DAQmxStartTask(task_);

   nRet = UpdateStatus();
   if (nRet != DEVICE_OK)
      return nRet;

   initialized_ = true;

   return DEVICE_OK;
}

int DigitalIO::Shutdown()
{
   DAQmxStopTask(task_);
	DAQmxClearTask(task_);
   
   initialized_ = false;
   return DEVICE_OK;
}


///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////

int DigitalIO::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set((long)state_);
   }
   else if (eAct == MM::AfterSet)
   {
      long s;
      pProp->Get(s);
      uInt32 data;
      int32 written;
      data = s;
      long niRet = DAQmxWriteDigitalU32(task_, 1, 1, 10.0, DAQmx_Val_GroupByChannel,&data, &written, NULL);
      if (niRet != DAQmxSuccess)
         return (int)niRet;
      state_ = (int)data;
   }

   return DEVICE_OK;
}

int DigitalIO::OnChannel(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(channel_.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(channel_);
   }

   return DEVICE_OK;
}


///////////////////////////////////////////////////////////////////////////////
// AnalogIO implementation
// ~~~~~~~~~~~~~~~~~~~~~~~

AnalogIO::AnalogIO() :
      busy_(false), minV_(0.0), maxV_(5.0), volts_(0.0), gatedVolts_(0.0), encoding_(0),
      resolution_(0), channel_("undef"), gateOpen_(true), task_(0), demo_(false)
{
   InitializeDefaultErrorMessages();

   // add custom error messages
   SetErrorText(ERR_UNKNOWN_POSITION, "Invalid position (state) specified");
   SetErrorText(ERR_INITIALIZE_FAILED, "Initialization of the device failed");
   SetErrorText(ERR_WRITE_FAILED, "Failed to write data to the device");
   SetErrorText(ERR_CLOSE_FAILED, "Failed closing the device");

   // channel
   CPropertyAction* pAct = new CPropertyAction (this, &AnalogIO::OnChannel);
   int nRet = CreateProperty(g_PropertyChannel, "devname", MM::String, false, pAct, true);
   assert(nRet == DEVICE_OK);

   // demo
   pAct = new CPropertyAction (this, &AnalogIO::OnDemo);
   nRet = CreateProperty(g_PropertyDemo, g_No, MM::String, false, pAct, true);
   assert(nRet == DEVICE_OK);
   AddAllowedValue(g_PropertyDemo, g_No);
   AddAllowedValue(g_PropertyDemo, g_Yes);

   // MinVolts
   // --------
   pAct = new CPropertyAction (this, &AnalogIO::OnMinVolts);
   nRet = CreateProperty(g_PropertyMinVolts, "0.0", MM::Float, false, pAct, true);
   assert(nRet == DEVICE_OK);

   // MaxVolts
   // --------
   pAct = new CPropertyAction (this, &AnalogIO::OnMaxVolts);
   nRet = CreateProperty(g_PropertyMaxVolts, "5.0", MM::Float, false, pAct, true);
   assert(nRet == DEVICE_OK);

   lists_.resize(2);
}

AnalogIO::~AnalogIO()
{
   Shutdown();
}

void AnalogIO::GetName(char* name) const
{
   CDeviceUtils::CopyLimitedString(name, g_DeviceNameAnalogIO);
}


int AnalogIO::Initialize()
{
   // set property list
   // -----------------
   char label[MM::MaxStrLength];
   GetLabel(label);
   g_analogDevs.insert(label);
   
   // Name
   int nRet = CreateProperty(MM::g_Keyword_Name, g_DeviceNameAnalogIO, MM::String, true);
   if (DEVICE_OK != nRet)
      return nRet;

   // Description
   nRet = CreateProperty(MM::g_Keyword_Description, "NI DAC", MM::String, true);
   if (DEVICE_OK != nRet)
      return nRet;

   // Volts
   // -----
   CPropertyAction* pAct = new CPropertyAction (this, &AnalogIO::OnVolts);
   nRet = CreateProperty(g_PropertyVolts, "0.0", MM::Float, false, pAct);
   if (nRet != DEVICE_OK)
      return nRet;

   nRet = SetPropertyLimits(g_PropertyVolts, minV_, maxV_);
   assert(nRet == DEVICE_OK);

   // depth idx
   CreateProperty(g_PropertyDepthIdx, "-1", MM::Integer, false);

   // list idx
   pAct = new CPropertyAction (this, &AnalogIO::OnPropertyListIdx);
   CreateProperty(g_PropertyListIdx, "0", MM::Integer, false, pAct);
   AddAllowedValue(g_PropertyListIdx, "0");
   AddAllowedValue(g_PropertyListIdx, "1");

   // z-map
   pAct = new CPropertyAction (this, &AnalogIO::OnZ);
   CreateProperty(g_PropertyZ, "0.0", MM::Float, false, pAct);

   // VD-map
   pAct = new CPropertyAction (this, &AnalogIO::OnVD);
   CreateProperty(g_PropertyVD, "0.0", MM::Float, false, pAct);

   // list size
   pAct = new CPropertyAction (this, &AnalogIO::OnDepthListSize);
   CreateProperty(g_PropertyDepthListSize, "0", MM::Integer, false, pAct);

   // set up task
   // -----------

   if (!demo_)
   {
      long niRet = DAQmxCreateTask("", &task_);
      if (niRet != DAQmxSuccess)
         return (int)niRet;

      niRet = DAQmxCreateAOVoltageChan(task_, channel_.c_str(), "", minV_, maxV_, DAQmx_Val_Volts, "");
      if (niRet != DAQmxSuccess)
         return (int)niRet;

	   niRet = DAQmxStartTask(task_);
   }

   nRet = UpdateStatus();
   if (nRet != DEVICE_OK)
      return nRet;

   initialized_ = true;

   return DEVICE_OK;
}

int AnalogIO::Shutdown()
{
   if (!demo_)
   {
      DAQmxStopTask(task_);
	   DAQmxClearTask(task_);
   }

   initialized_ = false;
   return DEVICE_OK;
}

int AnalogIO::SetSignal(double volts)
{
   ostringstream txt;
   txt << "2P >>>> AnalogIO::SetVoltage() = " << volts; 
   LogMessage(txt.str());
   return SetProperty(g_PropertyVolts, CDeviceUtils::ConvertToString(volts));
}

int AnalogIO::SetGateOpen(bool open)
{
   int ret(DEVICE_OK);

   if (open)
   {
      // opening gate: restore voltage
      if (!gateOpen_)
         ret = ApplyVoltage(gatedVolts_);
   }
   else
   {
      // closing gate: set voltage to 0
      if (gateOpen_)
      {
         ret = ApplyVoltage(0.0);
      }
   }

   if (ret != DEVICE_OK)
      return ret;

   gateOpen_ = open;
   return DEVICE_OK;
}

int AnalogIO::GetGateOpen(bool& open)
{
   open = gateOpen_;
   return DEVICE_OK;
}

int AnalogIO::ApplyVoltage(double v)
{
   if (!demo_)
   {
      float64 data[1];
      data[0] = v;
      long niRet = DAQmxWriteAnalogF64(task_, 1, 1, 10.0, DAQmx_Val_GroupByChannel, data, NULL, NULL);
      if (niRet != DAQmxSuccess)
      {
         ostringstream os;
         os << "Error setting voltage on the NI card: v=" << v << " V, error=" << niRet;
         LogMessage(os.str());
         return (int)niRet;
      }
   }
   volts_ = v;
   return DEVICE_OK;
}

double AnalogIO::GetInterpolatedV(double z, int listIdx)
{
   if (lists_[listIdx].zList_.size() == 0)
      return 0.0;

   if (lists_[listIdx].zList_.size() == 1)
      return lists_[listIdx].zList_[0];

   // find matching entry in the list
   // assuming the list is sorted from high to low
   ostringstream log;
   for (int i=0; i<(int)lists_[listIdx].zList_.size(); i++)
   {
      if (z == lists_[listIdx].zList_[i])
      {
         log << "i=" << i << ", v=" << lists_[listIdx].vList_[i];
         LogMessage(log.str().c_str());
         return lists_[listIdx].vList_[i];
      }
      else if (z > lists_[listIdx].zList_[i])
      {
         if (i==0)
         {
            log << "max:i=" << i << ", v=" << lists_[listIdx].vList_[0];
            LogMessage(log.str().c_str());
            return lists_[listIdx].vList_[0];
         }
         else
         {
            double zLow = lists_[listIdx].zList_[i];
            double zHigh = lists_[listIdx].zList_[i-1];
            double vLow = lists_[listIdx].vList_[i];
            double vHigh = lists_[listIdx].vList_[i-1];
            
            double zFactor;

            if (zHigh == zLow)
               zFactor = 0.0;
            else
               zFactor = (z - zLow)/(zHigh - zLow);

            double vi = vLow + (vHigh - vLow) * zFactor;
            log << "i=" << i << ", v=" << vi;
            LogMessage(log.str().c_str());

            return vi;
         }
      }
   }
   log << "min:i=" << lists_[listIdx].vList_.size() << ", v=" << lists_[listIdx].vList_.back();
   LogMessage(log.str().c_str());
   return lists_[listIdx].vList_.back();
}

int AnalogIO::ApplyDepthControl(double z)
{
   long listIdx = GetListIndex();
   if (lists_[listIdx].zList_.size() > 0)
   {
      double v = GetInterpolatedV(z, listIdx);
      ostringstream os;
      os << "2P >>>> Applying depth control, list=" << listIdx << ", z=" << z << ", v=" << v;
      LogMessage(os.str());
      return SetSignal(v);
   }
   return DEVICE_OK;
}

int AnalogIO::ApplyDepthControl(double z, int list)
{
   if (list >= (int)lists_.size() || list < 0)
      return ERR_WRONG_DEPTH_LIST;

   if (lists_[list].zList_.size() > 0)
   {
      double v = GetInterpolatedV(z, list);
      ostringstream os;
      os << "2P >>>> Applying depth control, list=" << list << ", z=" << z << ", v=" << v;
      LogMessage(os.str());
      return SetSignal(v);
   }
   return DEVICE_OK;
}
long AnalogIO::GetListIndex()
{
   long listIdx(0);
   int ret = GetProperty(g_PropertyListIdx, listIdx);
   assert(ret == DEVICE_OK);
   assert(listIdx < lists_.size());
   return listIdx;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////

int AnalogIO::OnVolts(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(gatedVolts_);
   }
   else if (eAct == MM::AfterSet)
   {
      double v;
      pProp->Get(v);
      gatedVolts_ = v;

      if (gateOpen_)
         return ApplyVoltage(v);
   }

   return DEVICE_OK;
}

int AnalogIO::OnMinVolts(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(minV_);
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(minV_);
   }

   return DEVICE_OK;
}

int AnalogIO::OnMaxVolts(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(maxV_);
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(maxV_);
   }

   return DEVICE_OK;
}

int AnalogIO::OnChannel(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(channel_.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(channel_);
   }

   return DEVICE_OK;
}

int AnalogIO::OnZ(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   long idx(0);
   int ret = GetProperty(g_PropertyDepthIdx, idx);
   assert(ret == DEVICE_OK);

   long listIdx = GetListIndex();

   if (idx >= (long)lists_[listIdx].zList_.size())
      return ERR_DEPTH_IDX_OUT_OF_RANGE;

   if (eAct == MM::BeforeGet)
   {
      if (idx >= 0)
         pProp->Set(lists_[listIdx].zList_[idx]);
   }
   else if (eAct == MM::AfterSet)
   {
      if (idx >=0 )
      pProp->Get(lists_[listIdx].zList_[idx]);
   }

   return DEVICE_OK;
}

int AnalogIO::OnVD(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   long idx(0);
   int ret = GetProperty(g_PropertyDepthIdx, idx);
   assert(ret == DEVICE_OK);

   long listIdx = GetListIndex();
   
   if (idx >= (long)lists_[listIdx].vList_.size())
      return ERR_DEPTH_IDX_OUT_OF_RANGE;

   if (eAct == MM::BeforeGet)
   {
      if (idx >= 0)
         pProp->Set(lists_[listIdx].vList_[idx]);
   }
   else if (eAct == MM::AfterSet)
   {
      if (idx >= 0)
         pProp->Get(lists_[listIdx].vList_[idx]);
   }

   return DEVICE_OK;
}

int AnalogIO::OnDepthListSize(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   long listIdx = GetListIndex();

   if (eAct == MM::BeforeGet)
   {
      pProp->Set((long)lists_[listIdx].zList_.size());
   }
   else if (eAct == MM::AfterSet)
   {
      long sz;
      pProp->Get(sz);
      lists_[listIdx].zList_.resize(sz);
      lists_[listIdx].vList_.resize(sz);
   }

   return DEVICE_OK;
}

int AnalogIO::OnDemo(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(demo_ ? g_Yes : g_No);
   }
   else if (eAct == MM::AfterSet)
   {
      string val;
      pProp->Get(val);
      if (val.compare(g_Yes) == 0)
         demo_ = true;
      else
         demo_ = false;
   }

   return DEVICE_OK;
}

int AnalogIO::OnPropertyListIdx(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   {
      //MM::Stage* pStage = GetCoreCallback()->GetFocusDevice(this);
      //if (pStage)
      //{
      //   char value[MM::MaxStrLength];
      //   int ret = pStage->GetProperty(g_DepthControl, value);
      //   if (ret == DEVICE_OK)
      //   {
      //      if (strcmp(value, g_Yes) == 0)
      //      {
      //         double pos;
      //         ret = pStage->GetPositionUm(pos);
      //         if (ret == DEVICE_OK)
      //            ApplyDepthControl(pos);
      //      }
      //   }
      //}
   }

   return DEVICE_OK;
}