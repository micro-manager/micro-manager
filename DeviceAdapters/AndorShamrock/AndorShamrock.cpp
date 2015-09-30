#include "AndorShamrock.h"
#include "ShamrockCIF.h"
#include "../../MMDevice/ModuleInterface.h"
#include <sstream>

using namespace std;

const char* g_ShamrockName = "Andor Shamrock";
const char* g_DeviceDescription = "Device Adapter for Andor Shamrock Spectrographs";
const char* gsz_SerialNo = "Shamrock Serial No.";;
const char* gsz_CentreWavelength = "Centre wavelength";
const char* gsz_RayleighWavelength = "Rayleigh wavelength";
const char* gsz_Grating = "Grating";
const char* gsz_Filter = "Filter";
const char* gsz_Shutter = "Shutter";
const char* gsz_PixelWidth = "Detector Pixel Width (um)";
const char* gsz_NoPixels = "No. of Detector Pixels";
const char* gsz_gratingoffset = "Grating Offset";
const char* gsz_detectoroffset = "Detector Offset";
const char* gsz_Coefficients = "Calibration Coefficients";
const char* gsz_SlitWidth[4] = {"Slit Width - Side Input (um)",
                               "Slit Width - Direct Input (um)",
                               "Slit Width - Side Output (um)",
                               "Slit Width - Direct Output (um)"};
const char* gsz_Port[2] = {"Port (Input)", "Port (Output)"};
//const char* gsz_flipperMirror[2] = {"DIRECT","SIDE"};
const char* gsz_FocusMirror = "Focus Mirror (Motor Steps)";

std::vector<std::string> gsz_flipperMirror;

const int NUM_PORTS = 2;
const int NUM_DETECTORS = 4;
const int NUM_DETECTORS_PER_PORT = 2;
const int NUM_FLIPPER_POS = 2;
const int PORT_1 = 1;
const int PORT_2 = 2;
const int MAX_NUM_SLITS = 4;
const float MAX_SLIT_WIDTH = 2500;
const float MIN_SLIT_WIDTH = 10;

MODULE_API void InitializeModuleData()
{
   RegisterDevice(g_ShamrockName, MM::GenericDevice, g_DeviceDescription);
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
   if (deviceName == 0) {
      return 0;
   }

   string strName(deviceName);

   if (strcmp(deviceName, g_ShamrockName) == 0) {
      MM::Device * device = new AndorShamrock();
      return device;
   }

   return 0;
}

MODULE_API void DeleteDevice(MM::Device * pDevice)
{
   delete pDevice;
}


AndorShamrock::AndorShamrock()
{

}

AndorShamrock::~AndorShamrock()
{

}

int AndorShamrock::Initialize()
{
   unsigned int sham_status(SHAMROCK_SUCCESS);
   int nRet = DEVICE_OK;
   sham_status = ShamrockInitialize("");
   int nodevices(0);
   ShamrockGetNumberDevices(&nodevices);
   if((sham_status==SHAMROCK_SUCCESS) && nodevices > 0) {
      char serial[20];
      sham_status=ShamrockGetSerialNumber(0, serial);
      if(sham_status==SHAMROCK_SUCCESS){
         nRet = CreateProperty(gsz_SerialNo,serial,MM::String,true,0);
      }

      SetCoefficientsProperty();
      SetGratingsProperty();
      SetWavelengthProperty();
	  SetRayleighWavelengthProperty();
      SetPixelWidthProperty();
      SetNumberPixelsProperty();
      SetFilterProperty();
      SetSlitProperty();
      SetShutterProperty();
      SetFlipperProperty();
      SetGratingOffsetProperty();
      SetDetectorOffsetProperty();
      SetFocusMirrorProperty();
   }
   else {
      nRet = DEVICE_NOT_CONNECTED;
   } 
   return nRet;
}

int AndorShamrock::Shutdown()
{
   ShamrockClose();
   return DEVICE_OK;
}

void AndorShamrock::GetName(char* pszName) const
{
   CDeviceUtils::CopyLimitedString(pszName, g_ShamrockName);
}

int AndorShamrock::OnSetWavelength(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   int retVal=DEVICE_OK;
   unsigned int rc;
   if (eAct == MM::BeforeGet)
   {
      float currentwavelength(0.0f);
      rc = ShamrockGetWavelength(0,&currentwavelength);
	  if( SHAMROCK_SUCCESS == rc) {
         pProp->Set(currentwavelength);
	  }
   }
   else if (eAct == MM::AfterSet)
   {
      double wavelength;
      pProp->Get(wavelength);
      rc = ShamrockSetWavelength(0, static_cast<float>(wavelength));
      if (SHAMROCK_SUCCESS != rc) {
         retVal = DEVICE_CAN_NOT_SET_PROPERTY;
   }
   OnPropertyChanged(gsz_CentreWavelength, CDeviceUtils::ConvertToString(wavelength));
   }
   SetCoefficientsProperty();
   return retVal;
}

int AndorShamrock::OnSetPixelWidth(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   int retVal=DEVICE_OK;
   if (eAct == MM::BeforeGet)
   {
      float pixelwidth(0.0f);
      ShamrockGetPixelWidth(0,&pixelwidth);
      pProp->Set(pixelwidth);
   }
   else if (eAct == MM::AfterSet)
   {
      double pixelwidth;
      pProp->Get(pixelwidth);
      unsigned ret = ShamrockSetPixelWidth(0, static_cast<float>(pixelwidth));
      if (SHAMROCK_SUCCESS != ret) {
         retVal = DEVICE_CAN_NOT_SET_PROPERTY;
      }
      OnPropertyChanged(gsz_PixelWidth, CDeviceUtils::ConvertToString(pixelwidth));
   }
   SetCoefficientsProperty();
   return retVal;
}

int AndorShamrock::OnSetNumberOfPixels(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   int retVal=DEVICE_OK;
   if (eAct == MM::BeforeGet)
   {
      int nopixels(0);
      ShamrockGetNumberPixels(0,&nopixels);
      pProp->Set(static_cast<long>(nopixels));
   }
   else if (eAct == MM::AfterSet)
   {
      long nopixels;
      pProp->Get(nopixels);
      unsigned ret = ShamrockSetNumberPixels(0, static_cast<int>(nopixels));
      if (SHAMROCK_SUCCESS != ret) {
         retVal = DEVICE_CAN_NOT_SET_PROPERTY;
      }
      OnPropertyChanged(gsz_NoPixels, CDeviceUtils::ConvertToString(nopixels));
   }
   SetCoefficientsProperty();
   return retVal;
}

int AndorShamrock::OnSetGrating(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   int retVal=DEVICE_OK;
   int currentgrating(0);
   int newgrating(0);
   ShamrockGetGrating(0,&currentgrating);
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(mvGratings[currentgrating-1].c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      string sz_grating;
      pProp->Get(sz_grating);
      for(int i = 0; i < static_cast<int>(mvGratings.size()); ++i) {
         if(mvGratings[i].compare(sz_grating)==0) {
            newgrating = i+1;
            break;
         }
      }
      if(newgrating!=currentgrating) {
         unsigned ret = ShamrockSetGrating(0, newgrating);
         if(SHAMROCK_SUCCESS == ret) {
            float Min(0.0f);
            float Max(0.0f);
            ShamrockGetWavelengthLimits(0, newgrating, &Min, &Max);
            SetPropertyLimits(gsz_CentreWavelength, Min, Max);
            OnPropertyChanged(gsz_Grating, CDeviceUtils::ConvertToString(newgrating));
         }
         else {
            retVal = DEVICE_CAN_NOT_SET_PROPERTY;
         }
      }
   }
   return retVal;
}

int AndorShamrock::OnSetFilter(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   int retVal=DEVICE_OK;
   int currentfilter(0);
   int newfilter(0);
   ShamrockGetFilter(0,&currentfilter);
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(mvFilters[currentfilter].c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      string sz_filter;
      pProp->Get(sz_filter);
      for(int i = 0; i < static_cast<int>(mvFilters.size()); ++i) {
         if(mvFilters[i].compare(sz_filter)==0) {
            newfilter = i;
            break;
         }
      }
      if(newfilter!=currentfilter) {
         unsigned ret = ShamrockSetFilter(0, newfilter);
         if(SHAMROCK_SUCCESS != ret) {
            retVal = DEVICE_CAN_NOT_SET_PROPERTY;
            OnPropertyChanged(gsz_Filter, CDeviceUtils::ConvertToString(newfilter));
         }
         
      }
   }
   return retVal;
}

int AndorShamrock::OnSetInputSideSlitWidth(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   return SetSlitWidth(pProp, eAct, SHAMROCK_INPUT_SLIT_SIDE);
}

int AndorShamrock::OnSetInputDirectSlitWidth(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   return SetSlitWidth(pProp, eAct, SHAMROCK_INPUT_SLIT_DIRECT);
}

int AndorShamrock::OnSetOutputSideSlitWidth(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   return SetSlitWidth(pProp, eAct, SHAMROCK_OUTPUT_SLIT_SIDE);
}

int AndorShamrock::OnSetOutputDirectSlitWidth(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   return SetSlitWidth(pProp, eAct, SHAMROCK_OUTPUT_SLIT_DIRECT);
}

bool AndorShamrock::Busy()
{
   return false;
}

void AndorShamrock::SetCoefficientsProperty()
{
   float A,B,C,D;
   int nRet(0);
   unsigned int rc = ShamrockGetPixelCalibrationCoefficients(0,&A,&B,&C,&D);
   stringstream ss("");
   ss << A << " " << B << " " << C << " " << D;
   if(!HasProperty(gsz_Coefficients)) {
    
      nRet = CreateProperty(gsz_Coefficients,ss.str().c_str(),MM::String,false,0);
   }
   else {
      if(rc == SHAMROCK_SUCCESS) {
         nRet = SetProperty(gsz_Coefficients,ss.str().c_str()); 
      }
   }
}

void AndorShamrock::SetWavelengthProperty()
{
   float wavelength(0.0f);
   int nRet(0);
   ShamrockGetWavelength(0, &wavelength);
   stringstream ss("");
   ss << wavelength;
   if(HasProperty(gsz_CentreWavelength)) {
      nRet = SetProperty(gsz_CentreWavelength,ss.str().c_str()); 
   }
   else {
      CPropertyAction *pAct = new CPropertyAction (this, &AndorShamrock::OnSetWavelength);
      nRet = CreateProperty(gsz_CentreWavelength,ss.str().c_str(), MM::Float, false, pAct);
      int grating(0);
      ShamrockGetGrating(0, &grating);
      float Min(0.0f);
      float Max(0.0f);
      ShamrockGetWavelengthLimits(0, grating, &Min, &Max);
      SetPropertyLimits(gsz_CentreWavelength, Min, Max);
   }
   SetCoefficientsProperty();
}

void AndorShamrock::SetRayleighWavelengthProperty()
{
   float rayleigh_wavelength(0.0f);
   int nRet(0);

   stringstream ss("");
   ss << rayleigh_wavelength;
   if(HasProperty(gsz_RayleighWavelength)) {
      nRet = SetProperty(gsz_RayleighWavelength,ss.str().c_str()); 
   }
   else {
      nRet = CreateProperty(gsz_RayleighWavelength,ss.str().c_str(), MM::Float, false);
      float Min(0.0f);
      float Max(2000.0f);
      SetPropertyLimits(gsz_RayleighWavelength, Min, Max);
   }
}

void AndorShamrock::SetNumberPixelsProperty()
{
   int nopixels(0);
   ShamrockGetNumberPixels(0,&nopixels);
   stringstream ss("");
   ss << nopixels;
   if(HasProperty(gsz_NoPixels)) {
      SetProperty(gsz_NoPixels,ss.str().c_str()); 
   }
   else {
      CPropertyAction *pAct = new CPropertyAction (this, &AndorShamrock::OnSetNumberOfPixels);
      CreateProperty(gsz_NoPixels,ss.str().c_str(), MM::Integer, false, pAct);
   }
}

void AndorShamrock::SetPixelWidthProperty() {
   float pixelwidth(0.0f);
   ShamrockGetPixelWidth(0, &pixelwidth);
   stringstream ss("");
   ss << pixelwidth;
   if(HasProperty(gsz_PixelWidth)) {
      SetProperty(gsz_PixelWidth,ss.str().c_str()); 
   }
   else {
      CPropertyAction *pAct = new CPropertyAction (this, &AndorShamrock::OnSetPixelWidth);
      CreateProperty(gsz_PixelWidth,ss.str().c_str(), MM::Float, false, pAct);
   }
}

void AndorShamrock::SetGratingsProperty()
{
   int noGratings(0);
   unsigned int sham_status=ShamrockGetNumberGratings(0, &noGratings);
   if((sham_status==SHAMROCK_SUCCESS) && noGratings>0){
      float Lines(0);
      char Blaze[10];
      int Home(0);
      int Offset(0);
      mvGratings.clear();
      for(int i = 1; i <= noGratings; ++i) {
         ShamrockGetGratingInfo(0, i, &Lines, Blaze, &Home, &Offset);
         stringstream ss("");
         ss << i << ". Lines[" << Lines << "/mm] - Blaze[" << Blaze << "nm]";
         mvGratings.push_back(ss.str());
      }
      int grating(0);
      ShamrockGetGrating(0, &grating);
      CPropertyAction *pAct = new CPropertyAction (this, &AndorShamrock::OnSetGrating);
      CreateProperty(gsz_Grating,mvGratings[grating].c_str() , MM::String, false, pAct);
      SetAllowedValues(gsz_Grating, mvGratings);
   }
}

void AndorShamrock::SetFilterProperty()
{
   int filterPresent(0);
   unsigned int sham_status=ShamrockFilterIsPresent(0, &filterPresent);
   if((sham_status==SHAMROCK_SUCCESS) && filterPresent==1){
      char info[25];

      mvFilters.clear();
      for(int i = 0; i <= 6; ++i) {
         ShamrockGetFilterInfo(0, i, info);
         stringstream ss("");
         ss << i+1 << ". " << info;
         mvFilters.push_back(ss.str());
      }
      int filter(0);
      ShamrockGetFilter(0, &filter);
      CPropertyAction *pAct = new CPropertyAction (this, &AndorShamrock::OnSetFilter);
      CreateProperty(gsz_Filter,mvFilters[filter].c_str() , MM::String, false, pAct);
      SetAllowedValues(gsz_Filter, mvFilters);
   }
}

void AndorShamrock::SetShutterProperty()
{
   mvShutters.clear();
   mvShutters.push_back("Closed");
   mvShutters.push_back("Open");
   
   int shutterPresent(0);
   unsigned int sham_status=ShamrockShutterIsPresent(0, &shutterPresent);
   if((sham_status==SHAMROCK_SUCCESS) && shutterPresent==1){
      int mode(0);
      ShamrockGetShutter(0, &mode);

      if (mode == -1) {
         mode = 0;
         ShamrockSetShutter(0,mode);
      }
	  unsigned int rc = ShamrockSetShutter(0,2);
	  if(rc == SHAMROCK_SUCCESS) {
		  mvShutters.push_back("External BNC");
		  ShamrockSetShutter(0,mode);
	  }
      CPropertyAction *pAct = new CPropertyAction (this, &AndorShamrock::OnSetShutter);
      CreateProperty(gsz_Shutter,mvShutters[mode].c_str() , MM::String, false, pAct);
      SetAllowedValues(gsz_Shutter, mvShutters);
   }
}

void AndorShamrock::SetSlitProperty() 
{
   int slitPresent(0);
   char widthBuffer[100];

   for(int i = 1; i <= MAX_NUM_SLITS; ++i) {

      unsigned int sham_status = ShamrockAutoSlitIsPresent(0,i,&slitPresent);

      if((sham_status==SHAMROCK_SUCCESS) && slitPresent==1) {

         float width;
         ShamrockGetAutoSlitWidth(0, i, &width);
         sprintf(widthBuffer, "%.2f", width);

         if(HasProperty(gsz_SlitWidth[i-1])) {
            SetProperty(gsz_SlitWidth[i-1],widthBuffer); 
         }
         else {

            CPropertyAction *pAct = 0;

            switch(i){

            case(1):
               pAct = new CPropertyAction(this, &AndorShamrock::OnSetInputSideSlitWidth);
               break;
            case(2):
               pAct = new CPropertyAction(this, &AndorShamrock::OnSetInputDirectSlitWidth);
               break;
            case(3):
               pAct = new CPropertyAction(this, &AndorShamrock::OnSetOutputSideSlitWidth);
               break;
            case(4):
               pAct = new CPropertyAction(this, &AndorShamrock::OnSetOutputDirectSlitWidth);
               break;
            }

            CreateProperty(gsz_SlitWidth[i-1], widthBuffer, MM::Float, false, pAct);

            SetPropertyLimits(gsz_SlitWidth[i-1], MIN_SLIT_WIDTH, MAX_SLIT_WIDTH);
         }
      }
   }
}

int AndorShamrock::OnSetShutter(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   int retVal=DEVICE_OK;
   int currentshutter(0);
   int newshutter(0);
   ShamrockGetShutter(0,&currentshutter);
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(mvShutters[currentshutter].c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      string sz_shutter;
      pProp->Get(sz_shutter);
      for(int i = 0; i < static_cast<int>(mvShutters.size()); ++i) {
         if(mvShutters[i].compare(sz_shutter)==0) {
            newshutter = i;
            break;
         }
      }
      if(newshutter!=currentshutter) {
      unsigned ret = ShamrockSetShutter(0, newshutter);
         if(SHAMROCK_SUCCESS != ret) {
            retVal = DEVICE_CAN_NOT_SET_PROPERTY;
         }
      }
      OnPropertyChanged(gsz_Shutter, CDeviceUtils::ConvertToString(newshutter));
   }
   return retVal;
}

void AndorShamrock::SetFlipperProperty() 
{
   gsz_flipperMirror.clear();
   gsz_flipperMirror.push_back("DIRECT");
   gsz_flipperMirror.push_back("SIDE");

   int flipperPresent(0);
   for(int i = 1; i <= NUM_PORTS; ++i) {
      unsigned int sham_status = ShamrockFlipperMirrorIsPresent(0,i,&flipperPresent);

      int port(0);
      ShamrockGetFlipperMirror(0, i, &port);

      if((sham_status==SHAMROCK_SUCCESS) && flipperPresent==1) {
      
         //create property
         if(HasProperty(gsz_Port[i-1])) {
            SetProperty(gsz_Port[i-1],gsz_flipperMirror[port].c_str()); 
         }
         else {
            CPropertyAction *pAct = 0;
            switch(i){
            case(1):
               pAct = new CPropertyAction(this, &AndorShamrock::OnSetInputPort);
               break;
            case(2):
               pAct = new CPropertyAction(this, &AndorShamrock::OnSetOutputPort);
               break;
            }

            CreateProperty(gsz_Port[i-1],gsz_flipperMirror[port].c_str(), MM::String, false, pAct);
            SetAllowedValues(gsz_Port[i-1],gsz_flipperMirror);

         }
      }
      else {

      //If port flipper not available, set up port anyway and default to DIRECT (readonly)
      CreateProperty(gsz_Port[i-1],gsz_flipperMirror[port].c_str(), MM::String, true, 0);
      }
   }
}

int AndorShamrock::OnSetInputPort(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   return setPort(pProp, eAct, 1);

}

int AndorShamrock::OnSetOutputPort(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   return setPort(pProp, eAct, 2);

}

int AndorShamrock::setPort(MM::PropertyBase* pProp, MM::ActionType eAct, int flipper)
{
   int retVal=DEVICE_OK;
   int currentport(0);
   int newport(0);
   ShamrockGetFlipperMirror(0,flipper,&currentport);
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(gsz_flipperMirror[currentport].c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      string sz_port;
      pProp->Get(sz_port);

      for(int i = 0; i < NUM_FLIPPER_POS; ++i) {
         if(sz_port == gsz_flipperMirror[i]) {
            newport = i;
            break;
         }
      }
      if(newport!=currentport) {
         unsigned ret = ShamrockSetFlipperMirror(0, flipper, newport);
         if(SHAMROCK_SUCCESS != ret) {
            retVal = DEVICE_CAN_NOT_SET_PROPERTY;
         }
         OnPropertyChanged(gsz_Port[flipper-1], gsz_flipperMirror[newport].c_str());
      }
   } 
   return retVal;
}

void AndorShamrock::SetGratingOffsetProperty()
{
   int offset(0);
   int currentGrating(0);
   ShamrockGetGrating(0,&currentGrating);
   ShamrockGetGratingOffset(0,currentGrating,&offset);
   stringstream ss("");
   ss << offset;
   //create property
   if(HasProperty(gsz_gratingoffset)) {
      SetProperty(gsz_gratingoffset,ss.str().c_str()); 
   }
   else {
      CPropertyAction *pAct = new CPropertyAction(this, &AndorShamrock::OnSetGratingOffset);
      CreateProperty(gsz_gratingoffset,ss.str().c_str(), MM::Integer, false, pAct);
   }
}

void AndorShamrock::SetDetectorOffsetProperty() 
{
   int currentOffset(0);
   int offsetIndex1(0);
   int offsetIndex2(0);
   stringstream currentOffsetStr("");

   GetDetectorOffsetIndices(&offsetIndex1, &offsetIndex2);
   ShamrockGetDetectorOffsetEx(0, offsetIndex1, offsetIndex2, &currentOffset);

   currentOffsetStr << currentOffset;

   if (HasProperty(gsz_detectoroffset)) {
      SetProperty(gsz_detectoroffset,currentOffsetStr.str().c_str());
   }
   else {
      CPropertyAction *pAct = new CPropertyAction(this, &AndorShamrock::OnSetDetectorOffset);
      CreateProperty(gsz_detectoroffset, currentOffsetStr.str().c_str(), MM::Integer, false, pAct);
   }
}


int AndorShamrock::OnSetDetectorOffset(MM::PropertyBase* pProp, MM::ActionType eAct) 
{
   int currentOffset(0);
   long newOffset(0);
   int offsetIndex1(0);
   int offsetIndex2(0);

   GetDetectorOffsetIndices(&offsetIndex1, &offsetIndex2);

   if (eAct == MM::BeforeGet) {
      ShamrockGetDetectorOffsetEx(0, offsetIndex1, offsetIndex2, &currentOffset);
      pProp->Set(static_cast<long>(currentOffset));
   }
   else if (eAct == MM::AfterSet) {

      pProp->Get(newOffset);
      ShamrockSetDetectorOffsetEx(0, offsetIndex1, offsetIndex2, newOffset);
      OnPropertyChanged(gsz_detectoroffset, CDeviceUtils::ConvertToString(newOffset));
   }

   return DEVICE_OK;
   }

int AndorShamrock::OnSetGratingOffset(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   int retVal=DEVICE_OK;
   int currentgrating(0);
   int currentoffset(0);
   long newoffset(0);
   ShamrockGetGrating(0,&currentgrating);
   ShamrockGetGratingOffset(0, currentgrating, &currentoffset);
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(static_cast<long>(currentoffset));
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(newoffset);

      if(static_cast<int>(newoffset)!=currentoffset) {
         unsigned int ret = ShamrockSetGratingOffset(0, currentgrating,static_cast<int>(newoffset));
         if(SHAMROCK_SUCCESS != ret) {
            retVal = DEVICE_CAN_NOT_SET_PROPERTY;
         }
      }
      OnPropertyChanged(gsz_gratingoffset, CDeviceUtils::ConvertToString(newoffset));
   }
   return retVal;
}

int AndorShamrock::SetSlitWidth(MM::PropertyBase* pProp, MM::ActionType eAct, int slit)
{
   int retVal=DEVICE_OK;

   if (eAct == MM::BeforeGet)
   {

      float currentwidth(0);
      ShamrockGetAutoSlitWidth(0,slit,&currentwidth);
      pProp->Set(currentwidth);

   }
   else if (eAct == MM::AfterSet)
   {

      double width;
      pProp->Get(width);
      unsigned ret = ShamrockSetAutoSlitWidth(0, slit, static_cast<float>(width));

      if (width <= MIN_SLIT_WIDTH) ret = ShamrockAutoSlitReset(0, slit);

         if (SHAMROCK_SUCCESS != ret) {
            retVal = DEVICE_CAN_NOT_SET_PROPERTY;
         }
         OnPropertyChanged(gsz_SlitWidth[slit-1], CDeviceUtils::ConvertToString(width));
      }
   return retVal;
}

void AndorShamrock::SetFocusMirrorProperty()
{
   int focusMirrorPresent(0);
   int currentPosition(0);
   int maxSteps(0);
   stringstream currentPositionStr("");

   ShamrockFocusMirrorIsPresent(0, &focusMirrorPresent);
   ShamrockGetFocusMirror(0, &currentPosition);
   ShamrockGetFocusMirrorMaxSteps(0, &maxSteps);
   currentPositionStr << currentPosition;

   if (focusMirrorPresent == 1) {

      if (HasProperty(gsz_FocusMirror)) {
         SetProperty(gsz_FocusMirror, currentPositionStr.str().c_str());
      }
      else {
         CPropertyAction *pAct = new CPropertyAction(this, &AndorShamrock::OnSetFocusMirror);
         CreateProperty(gsz_FocusMirror, currentPositionStr.str().c_str(), MM::Integer, false, pAct);
         SetPropertyLimits(gsz_FocusMirror, 1, static_cast<double>(maxSteps));
      }
   }
}

int AndorShamrock::OnSetFocusMirror(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   int currentPosition(0);
   long newPosition(0);
   long calculatedNewPosition(0);

   ShamrockGetFocusMirror(0, &currentPosition);

   if (eAct == MM::BeforeGet)
   {
      pProp->Set(static_cast<long>(currentPosition));
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(newPosition);
      calculatedNewPosition = (newPosition - currentPosition);
      ShamrockSetFocusMirror(0, static_cast<int>(calculatedNewPosition));
      OnPropertyChanged(gsz_FocusMirror, CDeviceUtils::ConvertToString(newPosition));
   }
   return DEVICE_OK;
}

int AndorShamrock::GetDetectorOffsetIndices(int *index1, int *index2)
{
   char portStr[MM::MaxStrLength];
   int currentIndices[NUM_PORTS];

   for (int i = 0; i < NUM_PORTS; i++) {

      GetProperty(gsz_Port[i], portStr);

      if (strstr(portStr, gsz_flipperMirror[0].c_str())) {
         currentIndices[i] = 0;
      }
      else {
         currentIndices[i] = 1;
      }
   }
   *index1 = currentIndices[0];
   *index2 = currentIndices[1];

   return DEVICE_OK;
}