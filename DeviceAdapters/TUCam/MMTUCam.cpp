///////////////////////////////////////////////////////////////////////////////
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
//                
//
// COPYRIGHT:     Tucsen Photonics Co., Ltd., 2018
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

#include "MMTUCam.h"
#include <cstdio>
#include <string>
#include <math.h>
#include "../../MMDevice/ModuleInterface.h"
#include <sstream>
#include <algorithm>
#include "WriteCompactTiffRGB.h"
#include <iostream>
#include <process.h>

#include <shlwapi.h>
#pragma comment(lib, "shlwapi.lib")

using namespace std;
const double CMMTUCam::nominalPixelSizeUm_ = 1.0;
double g_IntensityFactor_ = 1.0;

// External names used used by the rest of the system
// to load particular device from the "DemoCamera.dll" library
const char* g_TUDeviceName  = "TUCam";

const char* g_PropNamePCLK  = "PixelClock";
const char* g_PropNameBODP  = "BitDepth";
const char* g_PropNameGain  = "Gain";
const char* g_PropNameMode  = "Gain";
const char* g_PropNameFLPH  = "FlipH";
const char* g_PropNameFLPV  = "FlipV";
const char* g_PropNameGAMM  = "Gamma";
const char* g_PropNameCONT  = "Contrast";
const char* g_PropNameSATU  = "Saturation";
const char* g_PropNameRGAN  = "ChannelR";
const char* g_PropNameGGAN  = "ChannelG";
const char* g_PropNameBGAN  = "ChannelB";
const char* g_PropNameATWB  = "ATWhiteBalance";
const char* g_PropNameONWB  = "OnWhiteBalance";
const char* g_PropNameATEXP = "ATExposure";
const char* g_PropNameTEMP  = "Temperature";
const char* g_PropNameFANG  = "FanGear";
const char* g_PropNameLLev  = "LeftLevels";
const char* g_PropNameRLev  = "RightLevels";
const char* g_PropNameIFMT  = "SaveImage";
const char* g_PropNameCMS   = "CMSMode";

const char* g_DeviceName = "Dhyana";   //"400Li"
const char* g_SdkName = "TUCam";
//const char* g_SdkName = "CamCore";

//const char* g_DeviceName = "Dhyana400A";
//const char* g_SdkName = "CamKernel";

const char* g_Color = "Color Mode";
const char* g_Gray  = "Gray Mode";

const char* g_WB  = "Click WhiteBalance";

const char* g_AE_ON  = "On";
const char* g_AE_OFF = "Off";

const char* g_CMS_ON  = "On";
const char* g_CMS_OFF = "Off";

const char* g_Format_PNG = "PNG";
const char* g_Format_TIF = "TIF";
const char* g_Format_JPG = "JPG";
const char* g_Format_BMP = "BMP";
const char* g_Format_RAW = "RAW";
const char* g_FileName   = "\\Image";

// constants for naming pixel types (allowed values of the "PixelType" property)
const char* g_PixelType_8bit     = "8bit";
const char* g_PixelType_16bit    = "16bit";
const char* g_PixelType_32bitRGB = "32bitRGB";
const char* g_PixelType_64bitRGB = "64bitRGB";
const char* g_PixelType_32bit    = "32bit";  // floating point greyscale

///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////

MODULE_API void InitializeModuleData()
{
   RegisterDevice(g_TUDeviceName, MM::CameraDevice, "TUCSEN Camera");
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
   if (deviceName == 0)
      return 0;

   // decide which device class to create based on the deviceName parameter
   if (strcmp(deviceName, g_TUDeviceName) == 0)
   {
      // create camera
      return new CMMTUCam();
   }

   // ...supplied name not recognized
   return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}


///////////////////////////////////////////////////////////////////////////////
// CMMTUCam implementation
// ~~~~~~~~~~~~~~~~~~~~~~~~~~

/**
* CMMTUCam constructor.
* Setup default all variables and create device properties required to exist
* before intialization. In this case, no such properties were required. All
* properties will be created in the Initialize() method.
*
* As a general guideline Micro-Manager devices do not access hardware in the
* the constructor. We should do as little as possible in the constructor and
* perform most of the initialization in the Initialize() method.
*/
CMMTUCam::CMMTUCam() :
    CCameraBase<CMMTUCam> (),
    exposureMaximum_(10000.0),     
    dPhase_(0),
    initialized_(false),
    readoutUs_(0.0),
    scanMode_(1),
    bitDepth_(8),
    roiX_(0),
    roiY_(0),
    sequenceStartTime_(0),
    isSequenceable_(false),
    sequenceMaxLength_(100),
    sequenceRunning_(false),
    sequenceIndex_(0),
    binSize_(1),
    cameraCCDXSize_(512),
    cameraCCDYSize_(512),
    ccdT_ (0.0),
    triggerDevice_(""),
    stopOnOverflow_(false),
    dropPixels_(false),
    fastImage_(false),
    saturatePixels_(false),
    fractionOfPixelsToDropOrSaturate_(0.002),
    shouldRotateImages_(false),
    shouldDisplayImageNumber_(false),
    stripeWidth_(1.0),
    nComponents_(1)
{
    memset(testProperty_,0,sizeof(testProperty_));

    // call the base class method to set-up default error codes/messages
    InitializeDefaultErrorMessages();
    readoutStartTime_ = GetCurrentMMTime();
    thd_ = new CTUCamThread(this);

    // parent ID display
    //   CreateHubIDProperty();

    m_fCurTemp    = 0.0f;
    m_fValTemp    = 0.0f;
    m_bROI        = false;
    m_bSaving     = false;
    m_bLiving     = false;
    m_bTemping    = false;
    m_hThdWaitEvt = NULL;
    m_hThdTempEvt = NULL;

    m_frame.uiRsdSize   = 1;
    m_frame.ucFormatGet = TUFRM_FMT_USUAl;
    m_frame.pBuffer     = NULL;

    m_nIdxGain = 0;
}

/**
* CMMTUCam destructor.
* If this device used as intended within the Micro-Manager system,
* Shutdown() will be always called before the destructor. But in any case
* we need to make sure that all resources are properly released even if
* Shutdown() was not called.
*/
CMMTUCam::~CMMTUCam()
{
	if (m_hThdTempEvt != NULL)
	{
        m_bTemping = false;
		WaitForSingleObject(m_hThdTempEvt, INFINITE);	
		CloseHandle(m_hThdTempEvt);
		m_hThdTempEvt = NULL;
	}

    StopCapture();
    UninitTUCamApi();

    delete thd_;   
}

/**
* Obtains device name.
* Required by the MM::Device API.
*/
void CMMTUCam::GetName(char* name) const
{
    // Return the name used to referr to this device adapte
    CDeviceUtils::CopyLimitedString(name, g_TUDeviceName);
}

/**
* Intializes the hardware.
* Required by the MM::Device API.
* Typically we access and initialize hardware at this point.
* Device properties are typically created here as well, except
* the ones we need to use for defining initialization parameters.
* Such pre-initialization properties are created in the constructor.
* (This device does not have any pre-initialization properties)
*/
int CMMTUCam::Initialize()
{
    OutputDebugString("[Initialize]:Enter!\n");

    if (initialized_)
        return DEVICE_OK;

    DemoHub* pHub = static_cast<DemoHub*>(GetParentHub());

    if (pHub)
    {
        char hubLabel[MM::MaxStrLength];
        pHub->GetLabel(hubLabel);
        SetParentID(hubLabel); // for backward comp.
    }
    else
        LogMessage(NoHubError);

    // init camera api
    // -----------------
    int nRet = InitTUCamApi();
    if (DEVICE_OK != nRet)
    {
        return nRet;
    }

    TUCAM_Capa_SetValue(m_opCam.hIdxTUCam, TUIDC_VERCORRECTION, 0);

    // set property list
    // -----------------

    TUCAM_CAPA_ATTR  capaAttr;
    TUCAM_PROP_ATTR  propAttr;
    TUCAM_VALUE_INFO valInfo;

    // Name
    nRet = CreateStringProperty(MM::g_Keyword_Name, g_TUDeviceName, true);
    if (DEVICE_OK != nRet)
        return nRet;

    // Description
    nRet = CreateStringProperty(MM::g_Keyword_Description, "TUCSEN Camera Device Adapter", true);
    if (DEVICE_OK != nRet)
        return nRet;

    // CameraName
    // Get camera type
    valInfo.nID = TUIDI_CAMERA_MODEL;
    if (TUCAMRET_SUCCESS == TUCAM_Dev_GetInfo(m_opCam.hIdxTUCam, &valInfo))
    {
        nRet = CreateProperty(MM::g_Keyword_CameraName, valInfo.pText, MM::String, true);
        assert(nRet == DEVICE_OK);
    }
    else
        return DEVICE_NOT_SUPPORTED;


    // CameraID
    nRet = CreateProperty(MM::g_Keyword_CameraID, "V1.0", MM::String, true);
    assert(nRet == DEVICE_OK);

    // binning
    CPropertyAction *pAct = new CPropertyAction (this, &CMMTUCam::OnBinning);
    nRet = CreateProperty(MM::g_Keyword_Binning, "", MM::String, false, pAct);
    assert(nRet == DEVICE_OK);

    nRet = SetAllowedBinning();
    if (nRet != DEVICE_OK)
        return nRet;

    // Bit depth
    capaAttr.idCapa = TUIDC_BITOFDEPTH;
    if (TUCAMRET_SUCCESS == TUCAM_Capa_GetAttr(m_opCam.hIdxTUCam, &capaAttr))
    {
        TUCAM_Capa_SetValue(m_opCam.hIdxTUCam, TUIDC_BITOFDEPTH, 8);

        pAct = new CPropertyAction (this, &CMMTUCam::OnBitDepth);
        nRet = CreateProperty(g_PropNameBODP, "8", MM::String, false, pAct);
        assert(nRet == DEVICE_OK);

        vector<string> bitDepths;
        bitDepths.push_back("8");
        bitDepths.push_back("16");
/*
        bitDepths.push_back("10");
        bitDepths.push_back("12");
        bitDepths.push_back("14");
        bitDepths.push_back("32");
*/
        nRet = SetAllowedValues(g_PropNameBODP, bitDepths);
        if (nRet != DEVICE_OK)
            return nRet;
    } 


    // Pixels clock
    capaAttr.idCapa = TUIDC_PIXELCLOCK;
    if (TUCAMRET_SUCCESS == TUCAM_Capa_GetAttr(m_opCam.hIdxTUCam, &capaAttr))
    {
        pAct = new CPropertyAction (this, &CMMTUCam::OnPixelClock);
        nRet = CreateProperty(g_PropNamePCLK, "High", MM::String, false, pAct);
        assert(nRet == DEVICE_OK);

        nRet = SetAllowedPixelClock();
        if (nRet != DEVICE_OK)
            return nRet;

        SetProperty(g_PropNamePCLK, "High");
    }

    // Exposure
    propAttr.nIdxChn= 0;
    propAttr.idProp = TUIDP_EXPOSURETM;
    if (TUCAMRET_SUCCESS == TUCAM_Prop_GetAttr(m_opCam.hIdxTUCam, &propAttr))
    {
        pAct = new CPropertyAction (this, &CMMTUCam::OnExposure);
        nRet = CreateProperty(MM::g_Keyword_Exposure, "10.0", MM::Float, false, pAct);
        assert(nRet == DEVICE_OK);

        double dblMax = (int)propAttr.dbValMax > 1000 ? (propAttr.dbValMax * 100 / 967) : (int)propAttr.dbValMax;
        SetPropertyLimits(MM::g_Keyword_Exposure, propAttr.dbValMin, dblMax);
        
//      SetPropertyLimits(MM::g_Keyword_Exposure, propAttr.dbValMin, 10/*propAttr.dbValMax*/);
//      SetPropertyLimits(MM::g_Keyword_Exposure, propAttr.dbValMin, (propAttr.dbValMax * 100 / 967)); // rainfan
    }

    // Global Gain
    propAttr.nIdxChn= 0;
    propAttr.idProp = TUIDP_GLOBALGAIN;
    if (TUCAMRET_SUCCESS == TUCAM_Prop_GetAttr(m_opCam.hIdxTUCam, &propAttr))
    {
        if (propAttr.dbValMax > 5)
        {
            pAct = new CPropertyAction (this, &CMMTUCam::OnGlobalGain);
            nRet = CreateProperty(g_PropNameGain, "1", MM::Integer, false, pAct);
            assert(nRet == DEVICE_OK);

            SetPropertyLimits(g_PropNameGain, propAttr.dbValMin, propAttr.dbValMax);
        }
        else
        {
            pAct = new CPropertyAction (this, &CMMTUCam::OnImageMode);
            nRet = CreateProperty(g_PropNameMode, "HDR", MM::String, false, pAct);
            assert(nRet == DEVICE_OK);

            nRet = SetAllowedImageMode();
            if (nRet != DEVICE_OK)
                return nRet;
        }
    }

    // Auto Exposure
    capaAttr.idCapa = TUIDC_ATEXPOSURE;
    if (TUCAMRET_SUCCESS == TUCAM_Capa_GetAttr(m_opCam.hIdxTUCam, &capaAttr))
    {
        pAct = new CPropertyAction (this, &CMMTUCam::OnATExposure);
        nRet = CreateProperty(g_PropNameATEXP, "FALSE", MM::String, false, pAct);
        assert(nRet == DEVICE_OK);

        vector<string> ATExpValues;
        ATExpValues.push_back("FALSE");
        ATExpValues.push_back("TRUE");

        nRet = SetAllowedValues(g_PropNameATEXP, ATExpValues);
        if (nRet != DEVICE_OK)
            return nRet;
    }

    // Flip Horizontal
    capaAttr.idCapa = TUIDC_HORIZONTAL;
    if (TUCAMRET_SUCCESS == TUCAM_Capa_GetAttr(m_opCam.hIdxTUCam, &capaAttr))
    {
        pAct = new CPropertyAction (this, &CMMTUCam::OnFlipH);
        nRet = CreateProperty(g_PropNameFLPH, "FALSE", MM::String, false, pAct);
        assert(nRet == DEVICE_OK);

        vector<string> HFlipValues;
        HFlipValues.push_back("FALSE");
        HFlipValues.push_back("TRUE");

        nRet = SetAllowedValues(g_PropNameFLPH, HFlipValues);
        if (nRet != DEVICE_OK)
            return nRet;
    }

    // Flip Vertical
    capaAttr.idCapa = TUIDC_VERTICAL;
    if (TUCAMRET_SUCCESS == TUCAM_Capa_GetAttr(m_opCam.hIdxTUCam, &capaAttr))
    {
        pAct = new CPropertyAction (this, &CMMTUCam::OnFlipV);
        nRet = CreateProperty(g_PropNameFLPV, "FALSE", MM::String, false, pAct);
        assert(nRet == DEVICE_OK);

        vector<string> VFlipValues;
        VFlipValues.push_back("FALSE");
        VFlipValues.push_back("TRUE");

        nRet = SetAllowedValues(g_PropNameFLPV, VFlipValues);
        if (nRet != DEVICE_OK)
            return nRet;
    }
              

    // Gamma
    propAttr.nIdxChn= 0;
    propAttr.idProp = TUIDP_GAMMA;
    if (TUCAMRET_SUCCESS == TUCAM_Prop_GetAttr(m_opCam.hIdxTUCam, &propAttr))
    {
        pAct = new CPropertyAction (this, &CMMTUCam::OnGamma);
        nRet = CreateProperty(g_PropNameGAMM, "100", MM::Integer, false, pAct);
        assert(nRet == DEVICE_OK);

        SetPropertyLimits(g_PropNameGAMM, propAttr.dbValMin, propAttr.dbValMax);
    }

    
    // Contrast
    propAttr.nIdxChn= 0;
    propAttr.idProp = TUIDP_CONTRAST;
    if (TUCAMRET_SUCCESS == TUCAM_Prop_GetAttr(m_opCam.hIdxTUCam, &propAttr))
    {
        pAct = new CPropertyAction (this, &CMMTUCam::OnContrast);
        nRet = CreateProperty(g_PropNameCONT, "128", MM::Integer, false, pAct);
        assert(nRet == DEVICE_OK);

        SetPropertyLimits(g_PropNameCONT, (int)propAttr.dbValMin, (int)propAttr.dbValMax);
    }

    // Saturation
    propAttr.nIdxChn= 0;
    propAttr.idProp = TUIDP_SATURATION;
    if (TUCAMRET_SUCCESS == TUCAM_Prop_GetAttr(m_opCam.hIdxTUCam, &propAttr))
    {
        pAct = new CPropertyAction (this, &CMMTUCam::OnSaturation);
        nRet = CreateProperty(g_PropNameSATU, "128", MM::Integer, false, pAct);
        assert(nRet == DEVICE_OK);

        SetPropertyLimits(g_PropNameSATU, propAttr.dbValMin, propAttr.dbValMax);
    }

    // White Balance
    capaAttr.idCapa = TUIDC_ATWBALANCE;
    if (TUCAMRET_SUCCESS == TUCAM_Capa_GetAttr(m_opCam.hIdxTUCam, &capaAttr))
    {
        pAct = new CPropertyAction (this, &CMMTUCam::OnWhiteBalance);

        if (2 == capaAttr.nValMax)
        {
            nRet = CreateProperty(g_PropNameATWB, "FALSE", MM::String, false, pAct);
            assert(nRet == DEVICE_OK);

            vector<string> WBLNValues;
            WBLNValues.push_back("FALSE");
            WBLNValues.push_back("TRUE");

            nRet = SetAllowedValues(g_PropNameATWB, WBLNValues);
            if (nRet != DEVICE_OK)
                return nRet;
        }
        else
        {
            nRet = CreateProperty(g_PropNameONWB, "Click", MM::String, false, pAct);
            assert(nRet == DEVICE_OK);

            vector<string> WBLNValues;
            WBLNValues.push_back("Click");

            nRet = SetAllowedValues(g_PropNameONWB, WBLNValues);
            if (nRet != DEVICE_OK)
                return nRet;
        }
    }
 
    // Red Channel Gain
    propAttr.nIdxChn= 1;
    propAttr.idProp = TUIDP_CHNLGAIN;
    if (TUCAMRET_SUCCESS == TUCAM_Prop_GetAttr(m_opCam.hIdxTUCam, &propAttr))
    {
        pAct = new CPropertyAction (this, &CMMTUCam::OnRedGain);
        nRet = CreateProperty(g_PropNameRGAN, "256", MM::Integer, false, pAct);
        assert(nRet == DEVICE_OK);

        SetPropertyLimits(g_PropNameRGAN, propAttr.dbValMin, propAttr.dbValMax);
    }

    // Green Channel Gain
    propAttr.nIdxChn= 2;
    propAttr.idProp = TUIDP_CHNLGAIN;
    if (TUCAMRET_SUCCESS == TUCAM_Prop_GetAttr(m_opCam.hIdxTUCam, &propAttr))
    {
        pAct = new CPropertyAction (this, &CMMTUCam::OnGreenGain);
        nRet = CreateProperty(g_PropNameGGAN, "256", MM::Integer, false, pAct);
        assert(nRet == DEVICE_OK);

        SetPropertyLimits(g_PropNameGGAN, propAttr.dbValMin, propAttr.dbValMax);
    }

    // Blue Channel Gain
    propAttr.nIdxChn= 3;
    propAttr.idProp = TUIDP_CHNLGAIN;
    if (TUCAMRET_SUCCESS == TUCAM_Prop_GetAttr(m_opCam.hIdxTUCam, &propAttr))
    {
        pAct = new CPropertyAction (this, &CMMTUCam::OnBlueGain);
        nRet = CreateProperty(g_PropNameBGAN, "256", MM::Integer, false, pAct);
        assert(nRet == DEVICE_OK);

        SetPropertyLimits(g_PropNameBGAN, propAttr.dbValMin, propAttr.dbValMax);
    }

    // Temperature
    propAttr.idProp = TUIDP_TEMPERATURE;
    propAttr.nIdxChn= 0;
    if (TUCAMRET_SUCCESS == TUCAM_Prop_GetAttr(m_opCam.hIdxTUCam, &propAttr))
    {
// 	    pAct = new CPropertyAction (this, &CMMTUCam::OnTemperatureCurrent);
//         nRet = CreateProperty(g_PropNameTEMPC, "0", MM::String, false, pAct);
//         assert(nRet == DEVICE_OK);

        pAct = new CPropertyAction (this, &CMMTUCam::OnTemperature);
        nRet = CreateProperty(g_PropNameTEMP, "0", MM::Integer, false, pAct);

        m_nMidTemp = (int)((propAttr.dbValMax - propAttr.dbValMin) / 2); 
        SetPropertyLimits(g_PropNameTEMP, -m_nMidTemp, m_nMidTemp);

        // Set default temperature
        if (TUCAMRET_SUCCESS == TUCAM_Prop_SetValue(m_opCam.hIdxTUCam, TUIDP_TEMPERATURE, 40.0f))
        {
            char sz[10] = {0};
            sprintf(sz, "%d", -10);
            SetProperty(g_PropNameTEMP, sz);   
        }

        if (NULL == m_hThdTempEvt)
        {
            m_bTemping = true;
            m_hThdTempEvt = CreateEvent(NULL, TRUE, FALSE, NULL);
            _beginthread(GetTemperatureThread, 0, this);            // Start the get value of temperature thread
        }
    }

    // Fan gear
    capaAttr.idCapa = TUIDC_FAN_GEAR;
    if (TUCAMRET_SUCCESS == TUCAM_Capa_GetAttr(m_opCam.hIdxTUCam, &capaAttr))
    {
        pAct = new CPropertyAction (this, &CMMTUCam::OnFan);
        nRet = CreateProperty(g_PropNameFANG, "Fan 1", MM::String, false, pAct);
        assert(nRet == DEVICE_OK);

        nRet = SetAllowedFanGear();
        if (nRet != DEVICE_OK)
            return nRet;
    }

    // Left Levels
    propAttr.nIdxChn= 0;
    propAttr.idProp = TUIDP_LFTLEVELS;
    if (TUCAMRET_SUCCESS == TUCAM_Prop_GetAttr(m_opCam.hIdxTUCam, &propAttr))
    {
        pAct = new CPropertyAction (this, &CMMTUCam::OnLeftLevels);
        nRet = CreateProperty(g_PropNameLLev, "0", MM::Integer, false, pAct);
        assert(nRet == DEVICE_OK);

        SetPropertyLimits(g_PropNameLLev, propAttr.dbValMin, propAttr.dbValMax);
    }

    // Right Levels
    propAttr.nIdxChn= 0;
    propAttr.idProp = TUIDP_RGTLEVELS;
    if (TUCAMRET_SUCCESS == TUCAM_Prop_GetAttr(m_opCam.hIdxTUCam, &propAttr))
    {
        pAct = new CPropertyAction (this, &CMMTUCam::OnRightLevels);
        nRet = CreateProperty(g_PropNameRLev, "0", MM::Integer, false, pAct);
        assert(nRet == DEVICE_OK);

        SetPropertyLimits(g_PropNameRLev, propAttr.dbValMin, propAttr.dbValMax);
    }

    // Image format
    pAct = new CPropertyAction (this, &CMMTUCam::OnImageFormat);
	nRet = CreateStringProperty(g_PropNameIFMT, "RAW", false, pAct);
	assert(nRet == DEVICE_OK);

	AddAllowedValue(g_PropNameIFMT, g_Format_RAW);

    // CMS
    capaAttr.idCapa = TUIDC_IMGMODESELECT;
    if (TUCAMRET_SUCCESS == TUCAM_Capa_GetAttr(m_opCam.hIdxTUCam, &capaAttr))
    {
        pAct = new CPropertyAction (this, &CMMTUCam::OnCMSMode);

        nRet = CreateProperty(g_PropNameCMS, g_CMS_OFF, MM::String, false, pAct);
        assert(nRet == DEVICE_OK);

        vector<string>CMSValues;
        CMSValues.push_back(g_CMS_OFF);
        CMSValues.push_back(g_CMS_ON);

        nRet = SetAllowedValues(g_PropNameCMS, CMSValues);
        if (nRet != DEVICE_OK)
            return nRet;
    }

/*
    CPropertyActionEx *pActX = 0;
    // create an extended (i.e. array) properties 1 through 4

    for(int ij = 1; ij < 7;++ij)
    {
        std::ostringstream os;
        os<<ij;
        std::string propName = "TestProperty" + os.str();
        pActX = new CPropertyActionEx(this, &CMMTUCam::OnTestProperty, ij);
        nRet = CreateFloatProperty(propName.c_str(), 0., false, pActX);
        if(0!=(ij%5))
        {
            // try several different limit ranges
            double upperLimit = (double)ij*pow(10.,(double)(((ij%2)?-1:1)*ij));
            double lowerLimit = (ij%3)?-upperLimit:0.;
            SetPropertyLimits(propName.c_str(), lowerLimit, upperLimit);
        }
    }

    // scan mode
    pAct = new CPropertyAction (this, &CMMTUCam::OnScanMode);
    nRet = CreateIntegerProperty("ScanMode", 1, false, pAct);
    assert(nRet == DEVICE_OK);
    AddAllowedValue("ScanMode","1");
    AddAllowedValue("ScanMode","2");
    AddAllowedValue("ScanMode","3");

    // camera offset
    nRet = CreateIntegerProperty(MM::g_Keyword_Offset, 0, false);
    assert(nRet == DEVICE_OK);

    // camera temperature
    pAct = new CPropertyAction (this, &CMMTUCam::OnCCDTemp);
    nRet = CreateFloatProperty(MM::g_Keyword_CCDTemperature, 0, false, pAct);
    assert(nRet == DEVICE_OK);
    SetPropertyLimits(MM::g_Keyword_CCDTemperature, -100, 10);

    // camera temperature RO
    pAct = new CPropertyAction (this, &CMMTUCam::OnCCDTemp);
    nRet = CreateFloatProperty("CCDTemperature RO", 0, true, pAct);
    assert(nRet == DEVICE_OK);

    // readout time
    pAct = new CPropertyAction (this, &CMMTUCam::OnReadoutTime);
    nRet = CreateFloatProperty(MM::g_Keyword_ReadoutTime, 0, false, pAct);
    assert(nRet == DEVICE_OK);

    // CCD size of the camera we are modeling
    pAct = new CPropertyAction (this, &CMMTUCam::OnCameraCCDXSize);
    CreateIntegerProperty("OnCameraCCDXSize", 512, false, pAct);
    pAct = new CPropertyAction (this, &CMMTUCam::OnCameraCCDYSize);
    CreateIntegerProperty("OnCameraCCDYSize", 512, false, pAct);

    // Trigger device
    pAct = new CPropertyAction (this, &CMMTUCam::OnTriggerDevice);
    CreateStringProperty("TriggerDevice", "", false, pAct);

    pAct = new CPropertyAction (this, &CMMTUCam::OnDropPixels);
    CreateIntegerProperty("DropPixels", 0, false, pAct);
    AddAllowedValue("DropPixels", "0");
    AddAllowedValue("DropPixels", "1");

    pAct = new CPropertyAction (this, &CMMTUCam::OnSaturatePixels);
    CreateIntegerProperty("SaturatePixels", 0, false, pAct);
    AddAllowedValue("SaturatePixels", "0");
    AddAllowedValue("SaturatePixels", "1");

    pAct = new CPropertyAction (this, &CMMTUCam::OnFastImage);
    CreateIntegerProperty("FastImage", 0, false, pAct);
    AddAllowedValue("FastImage", "0");
    AddAllowedValue("FastImage", "1");

    pAct = new CPropertyAction (this, &CMMTUCam::OnFractionOfPixelsToDropOrSaturate);
    CreateFloatProperty("FractionOfPixelsToDropOrSaturate", 0.002, false, pAct);
    SetPropertyLimits("FractionOfPixelsToDropOrSaturate", 0., 0.1);

    pAct = new CPropertyAction(this, &CMMTUCam::OnShouldRotateImages);
    CreateIntegerProperty("RotateImages", 0, false, pAct);
    AddAllowedValue("RotateImages", "0");
    AddAllowedValue("RotateImages", "1");

    pAct = new CPropertyAction(this, &CMMTUCam::OnShouldDisplayImageNumber);
    CreateIntegerProperty("DisplayImageNumber", 0, false, pAct);
    AddAllowedValue("DisplayImageNumber", "0");
    AddAllowedValue("DisplayImageNumber", "1");

    pAct = new CPropertyAction(this, &CMMTUCam::OnStripeWidth);
    CreateFloatProperty("StripeWidth", 0, false, pAct);
    SetPropertyLimits("StripeWidth", 0, 10);

    // Whether or not to use exposure time sequencing
    pAct = new CPropertyAction (this, &CMMTUCam::OnIsSequenceable);
    std::string propName = "UseExposureSequences";
    CreateStringProperty(propName.c_str(), "No", false, pAct);
    AddAllowedValue(propName.c_str(), "Yes");
    AddAllowedValue(propName.c_str(), "No");
*/
    // initialize image buffer
    nRet = StartCapture();
    if (nRet != DEVICE_OK)
        return nRet;

    // pixel type
    vector<string> pixelTypeValues;
    pAct = new CPropertyAction (this, &CMMTUCam::OnPixelType);

    if (3 == m_frame.ucChannels)
    {
#ifdef _WIN64        
        if (2 == m_frame.ucElemBytes)
        {
            nRet = CreateProperty(MM::g_Keyword_PixelType, g_PixelType_64bitRGB, MM::String, false, pAct);
            assert(nRet == DEVICE_OK);

            pixelTypeValues.push_back(g_PixelType_64bitRGB);
        }  
        else
        {
            nRet = CreateProperty(MM::g_Keyword_PixelType, g_PixelType_32bitRGB, MM::String, false, pAct);
            assert(nRet == DEVICE_OK);

            pixelTypeValues.push_back(g_PixelType_32bitRGB);
        } 
#else
//         // Do not have enough memory
//         if (2 == m_frame.ucElemBytes)
//         {
//             nRet = CreateProperty(MM::g_Keyword_PixelType, g_PixelType_64bitRGB, MM::String, false, pAct);
//             assert(nRet == DEVICE_OK);
// 
//             pixelTypeValues.push_back(g_PixelType_64bitRGB);
//         }  
//         else
        {
            nRet = CreateProperty(MM::g_Keyword_PixelType, g_PixelType_32bitRGB, MM::String, false, pAct);
            assert(nRet == DEVICE_OK);

            pixelTypeValues.push_back(g_PixelType_32bitRGB);
        }  
#endif
  
    }
    else
    {
        if (2 == m_frame.ucElemBytes)
        {
            nRet = CreateProperty(MM::g_Keyword_PixelType, g_PixelType_16bit, MM::String, false, pAct);
            assert(nRet == DEVICE_OK);

            pixelTypeValues.push_back(g_PixelType_16bit);
        }  
        else 
        {
            nRet = CreateProperty(MM::g_Keyword_PixelType, g_PixelType_8bit, MM::String, false, pAct);
            assert(nRet == DEVICE_OK);

            pixelTypeValues.push_back(g_PixelType_8bit);
        } 
    }  
/*
    if (3 == m_frame.ucChannels)
    {
        nRet = CreateProperty(MM::g_Keyword_PixelType, g_PixelType_32bitRGB, MM::String, false, pAct);
        assert(nRet == DEVICE_OK);

#ifdef _WIN64
        if (2 == m_frame.ucElemBytes)
        {
            pixelTypeValues.push_back(g_PixelType_64bitRGB);
        }  
        else
        {
            pixelTypeValues.push_back(g_PixelType_32bitRGB);
        }    
#else
        pixelTypeValues.push_back(g_PixelType_32bitRGB);
#endif        
    }
    else
    {
        nRet = CreateProperty(MM::g_Keyword_PixelType, g_PixelType_8bit, MM::String, false, pAct);
        assert(nRet == DEVICE_OK);

#ifdef _WIN64
        if (2 == m_frame.ucElemBytes)
        {
            pixelTypeValues.push_back(g_PixelType_16bit);
        }  
        else 
        {
            pixelTypeValues.push_back(g_PixelType_8bit);
        } 
#else
        pixelTypeValues.push_back(g_PixelType_8bit);
#endif
    }   
*/
/*
    pixelTypeValues.push_back(g_PixelType_16bit); 
    pixelTypeValues.push_back(g_PixelType_32bitRGB);
    pixelTypeValues.push_back(g_PixelType_64bitRGB);
    pixelTypeValues.push_back(::g_PixelType_32bit);
*/
    nRet = SetAllowedValues(MM::g_Keyword_PixelType, pixelTypeValues);
    if (nRet != DEVICE_OK)
        return nRet;


    // synchronize all properties
    // --------------------------
    nRet = UpdateStatus();
    if (nRet != DEVICE_OK)
        return nRet;

    // setup the buffer
    // ----------------
    nRet = ResizeImageBuffer();
    if (nRet != DEVICE_OK)
        return nRet;

#ifdef TESTRESOURCELOCKING
    TestResourceLocking(true);
    LogMessage("TestResourceLocking OK",true);
#endif

    initialized_ = true;

    // initialize image buffer
    GenerateEmptyImage(img_);

//     char sz[256] = {0};
//     sprintf(sz, "%d\n", m_pfSave->pFrame);
//     OutputDebugString(sz);
//     TUCAM_File_SaveImage(m_opCam.hIdxTUCam, *m_pfSave);

    OutputDebugString("[Initialize]:Success!\n");

    return DEVICE_OK;
}

/**
* Shuts down (unloads) the device.
* Required by the MM::Device API.
* Ideally this method will completely unload the device and release all resources.
* Shutdown() may be called multiple times in a row.
* After Shutdown() we should be allowed to call Initialize() again to load the device
* without causing problems.
*/
int CMMTUCam::Shutdown()
{
    // Close the get value of temperature thread

    OutputDebugString("[Shutdown]:enter");

    if (NULL != m_hThdTempEvt)
    {
        m_bTemping = false;
        WaitForSingleObject(m_hThdTempEvt, INFINITE);	
        CloseHandle(m_hThdTempEvt);
        m_hThdTempEvt = NULL;
    }

    StopCapture();

    UninitTUCamApi();
    initialized_ = false;
    return DEVICE_OK;
}

/**
* Performs exposure and grabs a single image.
* This function should block during the actual exposure and return immediately afterwards 
* (i.e., before readout).  This behavior is needed for proper synchronization with the shutter.
* Required by the MM::Camera API.
*/
int CMMTUCam::SnapImage()
{
    static int callCounter = 0;
    ++callCounter;

    MM::MMTime startTime = GetCurrentMMTime();
    double exp = GetExposure();
    if (sequenceRunning_ && IsCapturing()) 
    {
        exp = GetSequenceExposure();
    }

    if (!fastImage_)
    {
        //  GenerateSyntheticImage(img_, exp);  //ȡͼ
        WaitForFrame(img_);
    }

    MM::MMTime s0(0,0);
    if( s0 < startTime )
    {
        while (exp > (GetCurrentMMTime() - startTime).getMsec())
        {
            CDeviceUtils::SleepMs(1);
        }		
    }
    else
    {
        std::cerr << "You are operating this device adapter without setting the core callback, timing functions aren't yet available" << std::endl;
        // called without the core callback probably in off line test program
        // need way to build the core in the test program

    }
    readoutStartTime_ = GetCurrentMMTime();

    return DEVICE_OK;
}


/**
* Returns pixel data.
* Required by the MM::Camera API.
* The calling program will assume the size of the buffer based on the values
* obtained from GetImageBufferSize(), which in turn should be consistent with
* values returned by GetImageWidth(), GetImageHight() and GetImageBytesPerPixel().
* The calling program allso assumes that camera never changes the size of
* the pixel buffer on its own. In other words, the buffer can change only if
* appropriate properties are set (such as binning, pixel type, etc.)
*/
const unsigned char* CMMTUCam::GetImageBuffer()
{
    MMThreadGuard g(imgPixelsLock_);
    MM::MMTime readoutTime(readoutUs_);
    while (readoutTime > (GetCurrentMMTime() - readoutStartTime_)) {}		
    unsigned char *pB = (unsigned char*)(img_.GetPixels());
    return pB;  //NULL
}

/**
* Returns image buffer X-size in pixels.
* Required by the MM::Camera API.
*/
unsigned CMMTUCam::GetImageWidth() const
{
/*
    if (NULL != m_frame.pBuffer)
    {
        return m_frame.usWidth;
    }

    return 0;
*/
    return img_.Width();
}

/**
* Returns image buffer Y-size in pixels.
* Required by the MM::Camera API.
*/
unsigned CMMTUCam::GetImageHeight() const
{
/*
    if (NULL != m_frame.pBuffer)
    {
        return m_frame.usHeight;
    }

    return 0;
*/
    return img_.Height();
}

/**
* Returns image buffer pixel depth in bytes.
* Required by the MM::Camera API.
*/
unsigned CMMTUCam::GetImageBytesPerPixel() const
{
/*
    if (NULL != m_frame.pBuffer)
    {
        int nChnnels = (1 == m_frame.ucChannels) ? 1 : 4;

        return (m_frame.ucElemBytes * nChnnels);
    }

    return 1;
*/
    return img_.Depth();
} 

/**
* Returns the bit depth (dynamic range) of the pixel.
* This does not affect the buffer size, it just gives the client application
* a guideline on how to interpret pixel values.
* Required by the MM::Camera API.
*/
unsigned CMMTUCam::GetBitDepth() const
{
/*
    if (NULL != m_frame.pBuffer)
    {
        return (1 == m_frame.ucElemBytes) ? 8 : 16;
    }

    return 8;
*/
    return bitDepth_;
}

/**
* Returns the size in bytes of the image buffer.
* Required by the MM::Camera API.
*/
long CMMTUCam::GetImageBufferSize() const
{
/*
    if (NULL != m_frame.pBuffer)
    {
        return (m_frame.usWidth * m_frame.usHeight * GetImageBytesPerPixel());
    }

    return 0;
*/    
    
    return img_.Width() * img_.Height() * GetImageBytesPerPixel();
}

/**
* Sets the camera Region Of Interest.
* Required by the MM::Camera API.
* This command will change the dimensions of the image.
* Depending on the hardware capabilities the camera may not be able to configure the
* exact dimensions requested - but should try do as close as possible.
* If the hardware does not have this capability the software should simulate the ROI by
* appropriately cropping each frame.
* This demo implementation ignores the position coordinates and just crops the buffer.
* @param x - top-left corner coordinate
* @param y - top-left corner coordinate
* @param xSize - width
* @param ySize - height
*/

int CMMTUCam::SetROI(unsigned x, unsigned y, unsigned xSize, unsigned ySize)
{
    if (NULL == m_opCam.hIdxTUCam)
        return DEVICE_NOT_CONNECTED;

    if (xSize == 0 && ySize == 0)
    {
        // effectively clear ROI
        ResizeImageBuffer();
        roiX_ = 0;
        roiY_ = 0;
        m_bROI = false;
    }
    else
    {
        if (NULL == m_opCam.hIdxTUCam)
            return DEVICE_NOT_CONNECTED;

        m_bLiving = false;
        TUCAM_Cap_Stop(m_opCam.hIdxTUCam);      // Stop capture   
        ReleaseBuffer();

        // apply ROI 
        TUCAM_ROI_ATTR roiAttr;
        roiAttr.bEnable = TRUE;
        roiAttr.nHOffset= ((x >> 2) << 2);
        roiAttr.nVOffset= ((y >> 2) << 2);
//        roiAttr.nVOffset= (((m_nMaxHeight/*img_.Height()*/ - y - ySize) >> 2) << 2);
        roiAttr.nWidth  = (xSize >> 2) << 2;
        roiAttr.nHeight = (ySize >> 2) << 2;
        TUCAM_Cap_SetROI(m_opCam.hIdxTUCam, roiAttr);
        TUCAM_Cap_GetROI(m_opCam.hIdxTUCam, &roiAttr);

        char sz[256] = {0};
        sprintf(sz, "x:%d, y:%d, xsize:%d, ysize:%d, h:%d, v:%d, wid:%d, hei:%d, maxhei:%d", x, y, xSize, ySize, roiAttr.nHOffset, roiAttr.nVOffset, roiAttr.nWidth, roiAttr.nHeight, m_nMaxHeight);
        OutputDebugString(sz);

        roiX_ = x;
        roiY_ = y;
        m_bROI = true;


        StartCapture();
        ResizeImageBuffer();
    }

    return DEVICE_OK;
}

/**
* Returns the actual dimensions of the current ROI.
* Required by the MM::Camera API.
*/
int CMMTUCam::GetROI(unsigned& x, unsigned& y, unsigned& xSize, unsigned& ySize)
{
    x = roiX_;
    y = roiY_;

    xSize = img_.Width();
    ySize = img_.Height();

    return DEVICE_OK;
}

/**
* Resets the Region of Interest to full frame.
* Required by the MM::Camera API.
*/
int CMMTUCam::ClearROI()
{
    if (NULL == m_opCam.hIdxTUCam)
        return DEVICE_NOT_CONNECTED;

    m_bLiving = false;
    TUCAM_Cap_Stop(m_opCam.hIdxTUCam);      // Stop capture   
    ReleaseBuffer();

    // close ROI 
    TUCAM_ROI_ATTR roiAttr;
    TUCAM_Cap_GetROI(m_opCam.hIdxTUCam, &roiAttr);

    roiAttr.bEnable = FALSE;
    TUCAM_Cap_SetROI(m_opCam.hIdxTUCam, roiAttr);
    
    roiX_ = 0;
    roiY_ = 0;
    m_bROI = false;

    StartCapture();
    ResizeImageBuffer();

    return DEVICE_OK;
}

/**
* Returns the current exposure setting in milliseconds.
* Required by the MM::Camera API.
*/
double CMMTUCam::GetExposure() const
{
    char buf[MM::MaxStrLength];
    int ret = GetProperty(MM::g_Keyword_Exposure, buf);
    if (ret != DEVICE_OK)
        return 0.0;

    return atof(buf);
}

/**
 * Returns the current exposure from a sequence and increases the sequence counter
 * Used for exposure sequences
 */
double CMMTUCam::GetSequenceExposure() 
{
    if (exposureSequence_.size() == 0) 
        return this->GetExposure();

    double exposure = exposureSequence_[sequenceIndex_];

    sequenceIndex_++;
    if (sequenceIndex_ >= exposureSequence_.size())
        sequenceIndex_ = 0;

    return exposure;
}

/**
* Sets exposure in milliseconds.
* Required by the MM::Camera API.
*/
void CMMTUCam::SetExposure(double exp)
{
    SetProperty(MM::g_Keyword_Exposure, CDeviceUtils::ConvertToString(exp));
    GetCoreCallback()->OnExposureChanged(this, exp);
}

/**
* Returns the current binning factor.
* Required by the MM::Camera API.
*/
int CMMTUCam::GetBinning() const
{
    char buf[MM::MaxStrLength];
    int ret = GetProperty(MM::g_Keyword_Binning, buf);
    if (ret != DEVICE_OK)
        return 1;
    return atoi(buf);
}

/**
* Sets binning factor.
* Required by the MM::Camera API.
*/
int CMMTUCam::SetBinning(int binF)
{
    return SetProperty(MM::g_Keyword_Binning, CDeviceUtils::ConvertToString(binF));
}

int CMMTUCam::IsExposureSequenceable(bool& isSequenceable) const
{
    isSequenceable = isSequenceable_;
    return DEVICE_OK;
}

int CMMTUCam::GetExposureSequenceMaxLength(long& nrEvents) const
{
    if (!isSequenceable_) 
    {
        return DEVICE_UNSUPPORTED_COMMAND;
    }

    nrEvents = sequenceMaxLength_;
    return DEVICE_OK;
}

int CMMTUCam::StartExposureSequence()
{
    if (!isSequenceable_) {
        return DEVICE_UNSUPPORTED_COMMAND;
    }

    // may need thread lock
    sequenceRunning_ = true;
    return DEVICE_OK;
}

int CMMTUCam::StopExposureSequence()
{
    if (!isSequenceable_) 
    {
        return DEVICE_UNSUPPORTED_COMMAND;
    }

    // may need thread lock
    sequenceRunning_ = false;
    sequenceIndex_ = 0;
    return DEVICE_OK;
}

/**
 * Clears the list of exposures used in sequences
 */
int CMMTUCam::ClearExposureSequence()
{
    if (!isSequenceable_) 
    {
        return DEVICE_UNSUPPORTED_COMMAND;
    }

    exposureSequence_.clear();
    return DEVICE_OK;
}

/**
 * Adds an exposure to a list of exposures used in sequences
 */
int CMMTUCam::AddToExposureSequence(double exposureTime_ms) 
{
    if (!isSequenceable_)
    {
        return DEVICE_UNSUPPORTED_COMMAND;
    }

    exposureSequence_.push_back(exposureTime_ms);
    return DEVICE_OK;
}

int CMMTUCam::SendExposureSequence() const 
{
    if (!isSequenceable_) 
    {
        return DEVICE_UNSUPPORTED_COMMAND;
    }

    return DEVICE_OK;
}

int CMMTUCam::SetAllowedBinning() 
{
    if (NULL == m_opCam.hIdxTUCam)
        return DEVICE_NOT_CONNECTED;

    TUCAM_CAPA_ATTR capaAttr;
    capaAttr.idCapa = TUIDC_RESOLUTION;

    if (TUCAMRET_SUCCESS != TUCAM_Capa_GetAttr(m_opCam.hIdxTUCam, &capaAttr))
    {
        return DEVICE_NOT_SUPPORTED;
    }

    char szBuf[64] = {0};
    TUCAM_VALUE_TEXT valText;
    valText.nID       = TUIDC_RESOLUTION;
    valText.nTextSize = 64;
    valText.pText     = &szBuf[0];

    vector<string> binValues;
    int nCnt = capaAttr.nValMax - capaAttr.nValMin + 1;

    for (int i=0; i<nCnt; i++)
    {
        valText.dbValue = i;
        TUCAM_Capa_GetValueText(m_opCam.hIdxTUCam, &valText); 

        binValues.push_back(string(valText.pText));
    }

    LogMessage("Setting allowed binning settings", true);
    return SetAllowedValues(MM::g_Keyword_Binning, binValues);
}

int CMMTUCam::SetAllowedPixelClock()
{
    if (NULL == m_opCam.hIdxTUCam)
        return DEVICE_NOT_CONNECTED;

    TUCAM_CAPA_ATTR capaAttr;
    capaAttr.idCapa = TUIDC_PIXELCLOCK;

    if (TUCAMRET_SUCCESS != TUCAM_Capa_GetAttr(m_opCam.hIdxTUCam, &capaAttr))
    {
        return DEVICE_NOT_SUPPORTED;
    }

    char szBuf[64] = {0};
    TUCAM_VALUE_TEXT valText;
    valText.nID       = TUIDC_PIXELCLOCK;
    valText.nTextSize = 64;
    valText.pText     = &szBuf[0];

    vector<string> plkValues;
    int nCnt = capaAttr.nValMax - capaAttr.nValMin + 1;

    for (int i=0; i<nCnt; i++)
    {
        valText.dbValue = i;
        TUCAM_Capa_GetValueText(m_opCam.hIdxTUCam, &valText); 

        plkValues.push_back(string(valText.pText));
    }

    LogMessage("Setting allowed pixel clock settings", true);
    return SetAllowedValues(g_PropNamePCLK, plkValues);
}

int CMMTUCam::SetAllowedFanGear()
{
    if (NULL == m_opCam.hIdxTUCam)
        return DEVICE_NOT_CONNECTED;

    TUCAM_CAPA_ATTR capaAttr;
    capaAttr.idCapa = TUIDC_FAN_GEAR;

    if (TUCAMRET_SUCCESS != TUCAM_Capa_GetAttr(m_opCam.hIdxTUCam, &capaAttr))
    {
        return DEVICE_NOT_SUPPORTED;
    }

    char szBuf[64] = {0};
    TUCAM_VALUE_TEXT valText;
    valText.nID       = TUIDC_FAN_GEAR;
    valText.nTextSize = 64;
    valText.pText     = &szBuf[0];

    vector<string> fanValues;
    int nCnt = capaAttr.nValMax - capaAttr.nValMin + 1;

    for (int i=0; i<nCnt; i++)
    {
        valText.dbValue = i;
        TUCAM_Capa_GetValueText(m_opCam.hIdxTUCam, &valText); 

        fanValues.push_back(string(valText.pText));
    }

    LogMessage("Setting allowed fan gear settings", true);
    return SetAllowedValues(g_PropNameFANG, fanValues);
}

int CMMTUCam::SetAllowedImageMode()
{
    if (NULL == m_opCam.hIdxTUCam)
        return DEVICE_NOT_CONNECTED;

    TUCAM_PROP_ATTR propAttr;
    propAttr.nIdxChn = 0;
    propAttr.idProp  = TUIDP_GLOBALGAIN;

    if (TUCAMRET_SUCCESS != TUCAM_Prop_GetAttr(m_opCam.hIdxTUCam, &propAttr))
    {
        return DEVICE_NOT_SUPPORTED;
    }

    char szBuf[64] = {0};
    TUCAM_VALUE_TEXT valText;
    valText.nID       = TUIDP_GLOBALGAIN;
    valText.nTextSize = 64;
    valText.pText     = &szBuf[0];

    vector<string> modValues;
    int nCnt = 2/*(int)propAttr.dbValMax*/ - (int)propAttr.dbValMin + 1;

    for (int i=0; i<nCnt; i++)
    {
        valText.dbValue = i;
        TUCAM_Prop_GetValueText(m_opCam.hIdxTUCam, &valText); 

        modValues.push_back(string(valText.pText));
    }

    LogMessage("Setting allowed image mode settings", true);
    return SetAllowedValues(g_PropNameMode, modValues);
}


/**
 * Required by the MM::Camera API
 * Please implement this yourself and do not rely on the base class implementation
 * The Base class implementation is deprecated and will be removed shortly
 */
int CMMTUCam::StartSequenceAcquisition(double interval)
{
    return StartSequenceAcquisition(LONG_MAX, interval, false);            
}

/**                                                                       
* Stop and wait for the Sequence thread finished                                   
*/                                                                        
int CMMTUCam::StopSequenceAcquisition()                                     
{
    OutputDebugString("[StopSequenceAcquisition]:Enter\n");

    return StopCapture();
/*
    if (!thd_->IsStopped()) 
    {
        if (NULL == m_opCam.hIdxTUCam)
            return DEVICE_NOT_CONNECTED;

        thd_->Stop();  
        TUCAM_Buf_AbortWait(m_opCam.hIdxTUCam);                 // If you called TUCAM_Buf_WaitForFrames()
        thd_->wait();      

//        StopCapture();

    }                                                                      
                                                                          
    return DEVICE_OK; 
*/
} 

/**
* Simple implementation of Sequence Acquisition
* A sequence acquisition should run on its own thread and transport new images
* coming of the camera into the MMCore circular buffer.
*/
int CMMTUCam::StartSequenceAcquisition(long numImages, double interval_ms, bool stopOnOverflow)
{
    OutputDebugString("[StartSequenceAcquisition]:Enter\n");

    if (IsCapturing())
        return DEVICE_CAMERA_BUSY_ACQUIRING;

    // initialize image buffer
    int nRet = StartCapture();
    if (nRet != DEVICE_OK)
        return nRet;

    int ret = GetCoreCallback()->PrepareForAcq(this);
    if (ret != DEVICE_OK)
        return ret;

    sequenceStartTime_ = GetCurrentMMTime();
    imageCounter_ = 0;
    thd_->Start(numImages,interval_ms);
    stopOnOverflow_ = stopOnOverflow;
    return DEVICE_OK;
}

/*
 * Inserts Image and MetaData into MMCore circular Buffer
 */
int CMMTUCam::InsertImage()
{
    MM::MMTime timeStamp = this->GetCurrentMMTime();
    char label[MM::MaxStrLength];
    this->GetLabel(label);

    // Important:  metadata about the image are generated here:
    Metadata md;
    md.put("Camera", label);
    md.put(MM::g_Keyword_Metadata_StartTime, CDeviceUtils::ConvertToString(sequenceStartTime_.getMsec()));
    md.put(MM::g_Keyword_Elapsed_Time_ms, CDeviceUtils::ConvertToString((timeStamp - sequenceStartTime_).getMsec()));
    md.put(MM::g_Keyword_Metadata_ROI_X, CDeviceUtils::ConvertToString( (long) roiX_)); 
    md.put(MM::g_Keyword_Metadata_ROI_Y, CDeviceUtils::ConvertToString( (long) roiY_)); 

    imageCounter_++;

//     char buf[MM::MaxStrLength];
//     GetProperty(MM::g_Keyword_Binning, buf);
//     md.put(MM::g_Keyword_Binning, buf);

    char szTemp[256] = {0};
    sprintf(szTemp, "%.3f", m_fCurTemp);
    md.put("Temperature", szTemp); 

    MMThreadGuard g(imgPixelsLock_);

    const unsigned char* pI;
    pI = GetImageBuffer();

    unsigned int w = GetImageWidth();
    unsigned int h = GetImageHeight();
    unsigned int b = GetImageBytesPerPixel();

    int ret = GetCoreCallback()->InsertImage(this, pI, w, h, b, md.Serialize().c_str());

    if (!stopOnOverflow_ && ret == DEVICE_BUFFER_OVERFLOW)
    {
        // do not stop on overflow - just reset the buffer
        GetCoreCallback()->ClearImageBuffer(this);
        // don't process this same image again...
        return GetCoreCallback()->InsertImage(this, pI, w, h, b, md.Serialize().c_str(), false);
    } else
        return ret;
}

/*
 * Do actual capturing
 * Called from inside the thread  
 */
int CMMTUCam::RunSequenceOnThread(MM::MMTime startTime)
{
    int ret = DEVICE_ERR;

    // Trigger
    if (triggerDevice_.length() > 0) 
    {
        MM::Device* triggerDev = GetDevice(triggerDevice_.c_str());
        if (triggerDev != 0) 
        {
            LogMessage("trigger requested");
            triggerDev->SetProperty("Trigger","+");
        }
    }

    ret = WaitForFrame(img_);
/*   
    if (!fastImage_)
    {
        GenerateSyntheticImage(img_, GetSequenceExposure());
    }
*/
    ret = InsertImage();

    while (((double) (this->GetCurrentMMTime() - startTime).getMsec() / imageCounter_) < this->GetSequenceExposure())
    {
        CDeviceUtils::SleepMs(1);
    }

    if (ret != DEVICE_OK)
    {
        return ret;
    }

    return ret;
}

bool CMMTUCam::IsCapturing() 
{
     return !thd_->IsStopped();
}

/*
 * called from the thread function before exit 
 */
void CMMTUCam::OnThreadExiting() throw()
{
   try
   {
      LogMessage(g_Msg_SEQUENCE_ACQUISITION_THREAD_EXITING);
      GetCoreCallback()?GetCoreCallback()->AcqFinished(this,0):DEVICE_OK;
   }
   catch(...)
   {
      LogMessage(g_Msg_EXCEPTION_IN_ON_THREAD_EXITING, false);
   }
}


CTUCamThread::CTUCamThread(CMMTUCam* pCam)
   :intervalMs_(default_intervalMS)
   ,numImages_(default_numImages)
   ,imageCounter_(0)
   ,stop_(true)
   ,suspend_(false)
   ,camera_(pCam)
   ,startTime_(0)
   ,actualDuration_(0)
   ,lastFrameTime_(0)
{};

CTUCamThread::~CTUCamThread() {};

void CTUCamThread::Stop() 
{
    MMThreadGuard g(this->stopLock_);
    stop_=true;
}

void CTUCamThread::Start(long numImages, double intervalMs)
{
    OutputDebugString("[CTUCamThread]:Start");
    MMThreadGuard g1(this->stopLock_);
    MMThreadGuard g2(this->suspendLock_);
    numImages_=numImages;
    intervalMs_=intervalMs;
    imageCounter_=0;
    stop_ = false;
    suspend_=false;
    activate();
    actualDuration_ = 0;
    startTime_= camera_->GetCurrentMMTime();
    lastFrameTime_ = 0;
}

bool CTUCamThread::IsStopped()
{
    MMThreadGuard g(this->stopLock_);
    return stop_;
}

void CTUCamThread::Suspend() 
{
    MMThreadGuard g(this->suspendLock_);
    suspend_ = true;
}

bool CTUCamThread::IsSuspended()
{
    MMThreadGuard g(this->suspendLock_);
    return suspend_;
}

void CTUCamThread::Resume()
{
    MMThreadGuard g(this->suspendLock_);
    suspend_ = false;
}

int CTUCamThread::svc(void) throw()
{
    int ret=DEVICE_ERR;
    try 
    {
        do
        {  
            ret = camera_->RunSequenceOnThread(startTime_);

        } while (DEVICE_OK == ret && !IsStopped() && imageCounter_++ < numImages_-1);
        if (IsStopped())
            camera_->LogMessage("SeqAcquisition interrupted by the user\n");
    }catch(...){
        camera_->LogMessage(g_Msg_EXCEPTION_IN_THREAD, false);
    }
    stop_=true;
    actualDuration_ = camera_->GetCurrentMMTime() - startTime_;
    camera_->OnThreadExiting();
    return ret;
}


///////////////////////////////////////////////////////////////////////////////
// CMMTUCam Action handlers
///////////////////////////////////////////////////////////////////////////////

/*
* this Read Only property will update whenever any property is modified
*/
int CMMTUCam::OnTestProperty(MM::PropertyBase* pProp, MM::ActionType eAct, long indexx)
{
    if (eAct == MM::BeforeGet)
    {
        pProp->Set(testProperty_[indexx]);
    }
    else if (eAct == MM::AfterSet)
    {
        pProp->Get(testProperty_[indexx]);
    }
    return DEVICE_OK;
}


/**
* Handles "Binning" property.
*/
int CMMTUCam::OnBinning(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    if (NULL == m_opCam.hIdxTUCam)
        return DEVICE_NOT_CONNECTED;

    int ret = DEVICE_ERR;
    switch(eAct)
    {
    case MM::AfterSet:
        {
            if(IsCapturing())
                return DEVICE_CAMERA_BUSY_ACQUIRING;

            // the user just set the new value for the property, so we have to
            // apply this value to the 'hardware'.

            string val;
            pProp->Get(val);
            if (val.length() != 0)
            {
                TUCAM_CAPA_ATTR capaAttr;
                capaAttr.idCapa = TUIDC_RESOLUTION;

                if (TUCAMRET_SUCCESS == TUCAM_Capa_GetAttr(m_opCam.hIdxTUCam, &capaAttr))
                {
                    m_bLiving = false;
                    TUCAM_Cap_Stop(m_opCam.hIdxTUCam);      // Stop capture   
                    ReleaseBuffer();

                    char szBuf[64] = {0};
                    TUCAM_VALUE_TEXT valText;
                    valText.nID       = TUIDC_RESOLUTION;
                    valText.nTextSize = 64;
                    valText.pText     = &szBuf[0];

                    int nCnt = capaAttr.nValMax - capaAttr.nValMin + 1;

                    for (int i=0; i<nCnt; i++)
                    {
                        valText.dbValue = i;
                        TUCAM_Capa_GetValueText(m_opCam.hIdxTUCam, &valText); 
                   
                        if (0 == val.compare(valText.pText))
                        {
                            TUCAM_Capa_SetValue(m_opCam.hIdxTUCam, TUIDC_RESOLUTION, i);
                            break;
                        }                         
                    }

                    StartCapture();
                    ResizeImageBuffer();

                    roiX_ = 0;
                    roiY_ = 0;
                }

                OnPropertyChanged(MM::g_Keyword_Binning, val.c_str());

                ret = DEVICE_OK;
            }
        }
        break;
    case MM::BeforeGet:
        {
            int nIdx = 0;
            TUCAM_Capa_GetValue(m_opCam.hIdxTUCam, TUIDC_RESOLUTION, &nIdx);

            char szBuf[64] = {0};
            TUCAM_VALUE_TEXT valText;
            valText.nID       = TUIDC_RESOLUTION;
            valText.nTextSize = 64;
            valText.pText     = &szBuf[0];

            valText.dbValue = nIdx;
            TUCAM_Capa_GetValueText(m_opCam.hIdxTUCam, &valText); 

            pProp->Set(valText.pText);
            ret = DEVICE_OK;
        }
        break;
    default:
        break;
    }
    return ret; 
}

/**
* Handles "PixelClock" property.
*/
int CMMTUCam::OnPixelClock(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    if (NULL == m_opCam.hIdxTUCam)
        return DEVICE_NOT_CONNECTED;

    int ret = DEVICE_ERR;
    switch(eAct)
    {
    case MM::AfterSet:
        {
            if(IsCapturing())
                return DEVICE_CAMERA_BUSY_ACQUIRING;

            // the user just set the new value for the property, so we have to
            // apply this value to the 'hardware'.

            string val;
            pProp->Get(val);

            if (val.length() != 0)
            {
                TUCAM_CAPA_ATTR capaAttr;
                capaAttr.idCapa = TUIDC_PIXELCLOCK;

                if (TUCAMRET_SUCCESS == TUCAM_Capa_GetAttr(m_opCam.hIdxTUCam, &capaAttr))
                {
                    char szBuf[64] = {0};
                    TUCAM_VALUE_TEXT valText;
                    valText.nID       = TUIDC_PIXELCLOCK;
                    valText.nTextSize = 64;
                    valText.pText     = &szBuf[0];

                    int nCnt = capaAttr.nValMax - capaAttr.nValMin + 1;

                    for (int i=0; i<nCnt; i++)
                    {
                        valText.dbValue = i;
                        TUCAM_Capa_GetValueText(m_opCam.hIdxTUCam, &valText); 
                     
                        if (0 == val.compare(valText.pText))
                        {
                            TUCAM_Capa_SetValue(m_opCam.hIdxTUCam, TUIDC_PIXELCLOCK, i);
                            break;
                        }                         
                    }
                }

                OnPropertyChanged(g_PropNamePCLK, val.c_str());

                ret = DEVICE_OK;
            }
        }
        break;
    case MM::BeforeGet:
        {
            int nIdx = 0;
            TUCAM_Capa_GetValue(m_opCam.hIdxTUCam, TUIDC_PIXELCLOCK, &nIdx);

            char szBuf[64] = {0};
            TUCAM_VALUE_TEXT valText;
            valText.nID       = TUIDC_PIXELCLOCK;
            valText.nTextSize = 64;
            valText.pText     = &szBuf[0];

            valText.dbValue = nIdx;
            TUCAM_Capa_GetValueText(m_opCam.hIdxTUCam, &valText); 

            pProp->Set(valText.pText);

            ret = DEVICE_OK;
        }
        break;
    default:
        break;
    }

    return ret; 
}

/**
* Handles "Exposure" property.
*/
int CMMTUCam::OnExposure(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    if (NULL == m_opCam.hIdxTUCam)
        return DEVICE_NOT_CONNECTED;

    int ret = DEVICE_ERR;
    switch(eAct)
    {
    case MM::AfterSet:
        {
            double dblExp;
            pProp->Get(dblExp);          

            TUCAM_Prop_SetValue(m_opCam.hIdxTUCam, TUIDP_EXPOSURETM, dblExp);

            ret = DEVICE_OK;
        }
        break;
    case MM::BeforeGet:
        {
            double dblExp = 0.0f;

            TUCAM_Prop_GetValue(m_opCam.hIdxTUCam, TUIDP_EXPOSURETM, &dblExp);
            pProp->Set(dblExp);

            ret = DEVICE_OK;
        }
        break;
    default:
        break;
    }

    return ret;
}

/**
* Handles "GlobalGain" property.
*/
int CMMTUCam::OnGlobalGain(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    if (NULL == m_opCam.hIdxTUCam)
        return DEVICE_NOT_CONNECTED;

    int ret = DEVICE_ERR;
    switch(eAct)
    {
    case MM::AfterSet:
        {
            double dblGain;
            pProp->Get(dblGain);          

            TUCAM_Prop_SetValue(m_opCam.hIdxTUCam, TUIDP_GLOBALGAIN, dblGain);

            ret = DEVICE_OK;
        }
        break;
    case MM::BeforeGet:
        {
            double dblGain = 0.0f;

            TUCAM_Prop_GetValue(m_opCam.hIdxTUCam, TUIDP_GLOBALGAIN, &dblGain);
            pProp->Set(dblGain);

            ret = DEVICE_OK;
        }
        break;
    default:
        break;
    }

    return ret;
}

/**
* Handles "CMSMode" property.
*/
int CMMTUCam::OnCMSMode(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    if (NULL == m_opCam.hIdxTUCam)
        return DEVICE_NOT_CONNECTED;

    int ret = DEVICE_ERR;
    switch(eAct)
    {
    case MM::AfterSet:
        {
            string val;
            pProp->Get(val);
            if (val.length() != 0)
            {
                if (0 == val.compare(g_CMS_ON))
                {
                    TUCAM_Prop_SetValue(m_opCam.hIdxTUCam, TUIDP_GLOBALGAIN, 0);

                    int nVal = 0;
                    if (TUCAMRET_SUCCESS == TUCAM_Capa_GetValue(m_opCam.hIdxTUCam, TUIDC_IMGMODESELECT, &nVal))
                    {
                        if (1 != nVal)
                        {
                            TUCAM_Capa_SetValue(m_opCam.hIdxTUCam, TUIDC_IMGMODESELECT, 1);
                        }
                    }                    
                }
                else
                {
                    TUCAM_Prop_SetValue(m_opCam.hIdxTUCam, TUIDP_GLOBALGAIN, m_nIdxGain);

                    int nVal = 0;
                    if (TUCAMRET_SUCCESS == TUCAM_Capa_GetValue(m_opCam.hIdxTUCam, TUIDC_IMGMODESELECT, &nVal))
                    {
                        if (0 != nVal)
                        {
                            TUCAM_Capa_SetValue(m_opCam.hIdxTUCam, TUIDC_IMGMODESELECT, 0);
                        }
                    } 
                }

                OnPropertyChanged(g_PropNameFLPH, val.c_str());

                ret = DEVICE_OK;
            }
        }
        break;
    case MM::BeforeGet:
        {
            int nVal = 0;
            TUCAM_Capa_GetValue(m_opCam.hIdxTUCam, TUIDC_IMGMODESELECT, &nVal);

            string val;
            pProp->Get(val);

            if (1 == nVal)
            {
                pProp->Set(g_CMS_ON);
            }
            else
            {
                pProp->Set(g_CMS_OFF);
            }

            ret = DEVICE_OK;
        }
        break;
    default:
        break;
    }

    return ret;
}

/**
* Handles "ImageMode" property.
*/
int CMMTUCam::OnImageMode(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    if (NULL == m_opCam.hIdxTUCam)
        return DEVICE_NOT_CONNECTED;

    int ret = DEVICE_ERR;
    switch(eAct)
    {
    case MM::AfterSet:
        {
            string val;
            pProp->Get(val);            

            if (val.length() != 0)
            {
                TUCAM_PROP_ATTR propAttr;
                propAttr.nIdxChn= 0;
                propAttr.idProp = TUIDP_GLOBALGAIN;

                if (TUCAMRET_SUCCESS == TUCAM_Prop_GetAttr(m_opCam.hIdxTUCam, &propAttr))
                {
                    char szBuf[64] = {0};
                    TUCAM_VALUE_TEXT valText;
                    valText.nID       = TUIDP_GLOBALGAIN;
                    valText.nTextSize = 64;
                    valText.pText     = &szBuf[0];

                    int nCnt = 2/*(int)propAttr.dbValMax*/ - (int)propAttr.dbValMin + 1;

                    for (int i=0; i<nCnt; i++)
                    {
                        valText.dbValue = i;
                        TUCAM_Prop_GetValueText(m_opCam.hIdxTUCam, &valText);   

                        if (0 == val.compare(valText.pText))
                        {
                            TUCAM_Prop_SetValue(m_opCam.hIdxTUCam, TUIDP_GLOBALGAIN, i);
                            m_nIdxGain = i;

                            int nVal = 0;
                            if (TUCAMRET_SUCCESS == TUCAM_Capa_GetValue(m_opCam.hIdxTUCam, TUIDC_IMGMODESELECT, &nVal))
                            {
                                if (0 != nVal)
                                {
                                    TUCAM_Capa_SetValue(m_opCam.hIdxTUCam,TUIDC_IMGMODESELECT, 0);
                                }                                
                            }

                            break;
                        }                         
                    }
                }

                OnPropertyChanged(g_PropNameMode, val.c_str());

                ret = DEVICE_OK;
            }
        }
        break;
    case MM::BeforeGet:
        {
            double dblVal = 0;
            TUCAM_Prop_GetValue(m_opCam.hIdxTUCam, TUIDP_GLOBALGAIN, &dblVal);

            char szBuf[64] = {0};
            TUCAM_VALUE_TEXT valText;
            valText.nID       = TUIDP_GLOBALGAIN;
            valText.nTextSize = 64;
            valText.pText     = &szBuf[0];

            valText.dbValue = dblVal;
            TUCAM_Prop_GetValueText(m_opCam.hIdxTUCam, &valText); 

            m_nIdxGain = (int)dblVal;

            pProp->Set(valText.pText);

            ret = DEVICE_OK;
        }
        break;
    default:
        break;
    }

    return ret; 
}

/**
* Handles "PixelType" property.
*/
int CMMTUCam::OnPixelType(MM::PropertyBase* pProp, MM::ActionType eAct)
{      
    int ret = DEVICE_ERR;
    switch(eAct)
    {
    case MM::AfterSet:
        {
            if(IsCapturing())
                return DEVICE_CAMERA_BUSY_ACQUIRING;

            string pixelType;
            pProp->Get(pixelType);

            if (pixelType.compare(g_PixelType_8bit) == 0)
            {
                nComponents_ = 1;
                img_.Resize(img_.Width(), img_.Height(), 1);
                bitDepth_ = 8;
                ret=DEVICE_OK;
            }
            else if (pixelType.compare(g_PixelType_16bit) == 0)
            {
                nComponents_ = 1;
                img_.Resize(img_.Width(), img_.Height(), 2);
                bitDepth_ = 16;
                ret=DEVICE_OK;
            }
            else if ( pixelType.compare(g_PixelType_32bitRGB) == 0)
            {
                nComponents_ = 4;
                img_.Resize(img_.Width(), img_.Height(), 4);
                bitDepth_ = 8;
                ret=DEVICE_OK;
            }
            else if ( pixelType.compare(g_PixelType_64bitRGB) == 0)
            {
                nComponents_ = 4;
                img_.Resize(img_.Width(), img_.Height(), 8);
                bitDepth_ = 16;
                ret=DEVICE_OK;
            }
            else if ( pixelType.compare(g_PixelType_32bit) == 0)
            {
                nComponents_ = 1;
                img_.Resize(img_.Width(), img_.Height(), 4);
                bitDepth_ = 32;
                ret=DEVICE_OK;
            }
            else
            {
                // on error switch to default pixel type
                nComponents_ = 1;
                img_.Resize(img_.Width(), img_.Height(), 1);
                pProp->Set(g_PixelType_8bit);
                bitDepth_ = 8;
                ret = ERR_UNKNOWN_MODE;
            }
        }
        break;
    case MM::BeforeGet:
        {
            long bytesPerPixel = GetImageBytesPerPixel();
            if (bytesPerPixel == 1)
            {
                pProp->Set(g_PixelType_8bit);
            }
            else if (bytesPerPixel == 2)
            {
                pProp->Set(g_PixelType_16bit);
            }
            else if (bytesPerPixel == 4)
            {              
                if (nComponents_ == 4)
                {
                    pProp->Set(g_PixelType_32bitRGB);
                }
                else if (nComponents_ == 1)
                {
                    pProp->Set(::g_PixelType_32bit);
                }
            }
            else if (bytesPerPixel == 8)
            {
                pProp->Set(g_PixelType_64bitRGB);
            }
            else
            {
                pProp->Set(g_PixelType_8bit);
            }
            ret = DEVICE_OK;
        } break;
    default:
        break;
    }

    return ret; 
}

/**
* Handles "BitDepth" property.
*/
int CMMTUCam::OnBitDepth(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    if (NULL == m_opCam.hIdxTUCam)
        return DEVICE_NOT_CONNECTED;

    int ret = DEVICE_ERR;
    switch(eAct)
    {
    case MM::AfterSet:
        {
            if(IsCapturing())
                return DEVICE_CAMERA_BUSY_ACQUIRING;

            // the user just set the new value for the property, so we have to
            // apply this value to the 'hardware'.

            string val;
            pProp->Get(val);
            if (val.length() != 0)
            {
                TUCAM_CAPA_ATTR capaAttr;
                capaAttr.idCapa = TUIDC_BITOFDEPTH;

                if (TUCAMRET_SUCCESS == TUCAM_Capa_GetAttr(m_opCam.hIdxTUCam, &capaAttr))
                {
                    m_bLiving = false;
                    TUCAM_Cap_Stop(m_opCam.hIdxTUCam);      // Stop capture   
                    ReleaseBuffer();

                    if (0 == val.compare("16"))
                    {
                        TUCAM_Capa_SetValue(m_opCam.hIdxTUCam, TUIDC_BITOFDEPTH, 16);
                        SetPropertyLimits(g_PropNameLLev, 0, 65534);
                        SetPropertyLimits(g_PropNameRLev, 1, 65535);
                    }
                    else
                    {
                        TUCAM_Capa_SetValue(m_opCam.hIdxTUCam, TUIDC_BITOFDEPTH, 8);
                        SetPropertyLimits(g_PropNameLLev, 0, 254);
                        SetPropertyLimits(g_PropNameRLev, 1, 255);
                    }

                    StartCapture();
                    ResizeImageBuffer();

                    roiX_ = 0;
                    roiY_ = 0;
                }

                OnPropertyChanged(g_PropNameBODP, val.c_str());

                ret = DEVICE_OK;
            }
        }
        break;
    case MM::BeforeGet:
        {
            int nVal = 0;
            TUCAM_Capa_GetValue(m_opCam.hIdxTUCam, TUIDC_BITOFDEPTH, &nVal);

            if (16 == nVal)
            {
                pProp->Set("16");
            }
            else
            {
                pProp->Set("8");
            }

            ret = DEVICE_OK;
        }
        break;
    default:
        break;
    }

    return ret;

/*
    int ret = DEVICE_ERR;
    switch(eAct)
    {
    case MM::AfterSet:
        {
            if(IsCapturing())
                return DEVICE_CAMERA_BUSY_ACQUIRING;

            long bitDepth;
            pProp->Get(bitDepth);

            unsigned int bytesPerComponent;

            switch (bitDepth) 
            {
            case 8:
                bytesPerComponent = 1;
                bitDepth_ = 8;
                ret=DEVICE_OK;
                break;
            case 10:
                bytesPerComponent = 2;
                bitDepth_ = 10;
                ret=DEVICE_OK;
                break;
            case 12:
                bytesPerComponent = 2;
                bitDepth_ = 12;
                ret=DEVICE_OK;
                break;
            case 14:
                bytesPerComponent = 2;
                bitDepth_ = 14;
                ret=DEVICE_OK;
                break;
            case 16:
                bytesPerComponent = 2;
                bitDepth_ = 16;
                ret=DEVICE_OK;
                break;
            case 32:
                bytesPerComponent = 4;
                bitDepth_ = 32; 
                ret=DEVICE_OK;
                break;
            default: 
                // on error switch to default pixel type
                bytesPerComponent = 1;

                pProp->Set((long)8);
                bitDepth_ = 8;
                ret = ERR_UNKNOWN_MODE;
                break;
            }

            char buf[MM::MaxStrLength];
            GetProperty(MM::g_Keyword_PixelType, buf);
            std::string pixelType(buf);
            unsigned int bytesPerPixel = 1;

            // automagickally change pixel type when bit depth exceeds possible value
            if (pixelType.compare(g_PixelType_8bit) == 0)
            {
                if( 2 == bytesPerComponent)
                {
                    SetProperty(MM::g_Keyword_PixelType, g_PixelType_16bit);
                    bytesPerPixel = 2;
                }
                else if ( 4 == bytesPerComponent)
                {
                    SetProperty(MM::g_Keyword_PixelType, g_PixelType_32bit);
                    bytesPerPixel = 4;

                }else
                {
                    bytesPerPixel = 1;
                }
            }
            else if (pixelType.compare(g_PixelType_16bit) == 0)
            {
                bytesPerPixel = 2;
            }
            else if ( pixelType.compare(g_PixelType_32bitRGB) == 0)
            {
                bytesPerPixel = 4;
            }
            else if ( pixelType.compare(g_PixelType_32bit) == 0)
            {
                bytesPerPixel = 4;
            }
            else if ( pixelType.compare(g_PixelType_64bitRGB) == 0)
            {
                bytesPerPixel = 8;
            }
            img_.Resize(img_.Width(), img_.Height(), bytesPerPixel);
        }
        break;
    case MM::BeforeGet:
        {
            pProp->Set((long)bitDepth_);
            ret=DEVICE_OK;
        }
        break;
    }
    return ret; 
*/
}

/**
* Handles "FlipHorizontal" property.
*/
int CMMTUCam::OnFlipH(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    if (NULL == m_opCam.hIdxTUCam)
        return DEVICE_NOT_CONNECTED;

    int ret = DEVICE_ERR;
    switch(eAct)
    {
    case MM::AfterSet:
        {
            string val;
            pProp->Get(val);
            if (val.length() != 0)
            {
                TUCAM_CAPA_ATTR capaAttr;
                capaAttr.idCapa = TUIDC_HORIZONTAL;

                if (TUCAMRET_SUCCESS == TUCAM_Capa_GetAttr(m_opCam.hIdxTUCam, &capaAttr))
                {
                    if (0 == val.compare("TRUE"))
                    {
                        TUCAM_Capa_SetValue(m_opCam.hIdxTUCam, TUIDC_HORIZONTAL, 1);
                    }
                    else
                    {
                        TUCAM_Capa_SetValue(m_opCam.hIdxTUCam, TUIDC_HORIZONTAL, 0);
                    }
                }

                OnPropertyChanged(g_PropNameFLPH, val.c_str());

                ret = DEVICE_OK;
            }
        }
        break;
    case MM::BeforeGet:
        {
            int nVal = 0;
            TUCAM_Capa_GetValue(m_opCam.hIdxTUCam, TUIDC_HORIZONTAL, &nVal);

            if (1 == nVal)
            {
                pProp->Set("TRUE");
            }
            else
            {
                pProp->Set("FALSE");
            }

            ret = DEVICE_OK;
        }
        break;
    default:
        break;
    }

    return ret;
}

/**
* Handles "FlipVertical" property.
*/
int CMMTUCam::OnFlipV(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    if (NULL == m_opCam.hIdxTUCam)
        return DEVICE_NOT_CONNECTED;

    int ret = DEVICE_ERR;
    switch(eAct)
    {
    case MM::AfterSet:
        {
            string val;
            pProp->Get(val);
            if (val.length() != 0)
            {
                TUCAM_CAPA_ATTR capaAttr;
                capaAttr.idCapa = TUIDC_VERTICAL;

                if (TUCAMRET_SUCCESS == TUCAM_Capa_GetAttr(m_opCam.hIdxTUCam, &capaAttr))
                {
                    if (0 == val.compare("TRUE"))
                    {
                        TUCAM_Capa_SetValue(m_opCam.hIdxTUCam, TUIDC_VERTICAL, 1);
                    }
                    else
                    {
                        TUCAM_Capa_SetValue(m_opCam.hIdxTUCam, TUIDC_VERTICAL, 0);
                    }
                }

                OnPropertyChanged(g_PropNameFLPV, val.c_str());

                ret = DEVICE_OK;
            }
        }
        break;
    case MM::BeforeGet:
        {
            int nVal = 0;
            TUCAM_Capa_GetValue(m_opCam.hIdxTUCam, TUIDC_VERTICAL, &nVal);

            if (1 == nVal)
            {
                pProp->Set("TRUE");
            }
            else
            {
                pProp->Set("FALSE");
            }

            ret = DEVICE_OK;
        }
        break;
    default:
        break;
    }

    return ret;
}

/**
* Handles "Gamma" property.
*/
int CMMTUCam::OnGamma(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    if (NULL == m_opCam.hIdxTUCam)
        return DEVICE_NOT_CONNECTED;

    int ret = DEVICE_ERR;
    switch(eAct)
    {
    case MM::AfterSet:
        {
            long lVal = 0;
            pProp->Get(lVal);

            TUCAM_Prop_SetValue(m_opCam.hIdxTUCam, TUIDP_GAMMA, lVal);

            ret = DEVICE_OK;
        }
        break;
    case  MM::BeforeGet:
        {
            double dblVal = 0.0f;
            TUCAM_Prop_GetValue(m_opCam.hIdxTUCam, TUIDP_GAMMA, &dblVal);

            pProp->Set((long)(dblVal));

            ret = DEVICE_OK;
        }
        break;
    default:
        break;
    }

    return ret;
}

/**
* Handles "Contrast" property.
*/
int CMMTUCam::OnContrast(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    if (NULL == m_opCam.hIdxTUCam)
        return DEVICE_NOT_CONNECTED;

    int ret = DEVICE_ERR;
    switch(eAct)
    {
    case MM::AfterSet:
        {
            long lVal = 0;
            pProp->Get(lVal);

            TUCAM_Prop_SetValue(m_opCam.hIdxTUCam, TUIDP_CONTRAST, lVal);

            ret = DEVICE_OK;
        }
        break;
    case  MM::BeforeGet:
        {
            double dblVal = 0.0f;
            TUCAM_Prop_GetValue(m_opCam.hIdxTUCam, TUIDP_CONTRAST, &dblVal);

            pProp->Set((long)(dblVal));

            ret = DEVICE_OK;
        }
        break;
    default:
        break;
    }

    return ret;
}

/**
* Handles "Saturation" property.
*/
int CMMTUCam::OnSaturation(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    if (NULL == m_opCam.hIdxTUCam)
        return DEVICE_NOT_CONNECTED;

    int ret = DEVICE_ERR;
    switch(eAct)
    {
    case MM::AfterSet:
        {
            long lVal = 0;
            pProp->Get(lVal);

            TUCAM_Prop_SetValue(m_opCam.hIdxTUCam, TUIDP_SATURATION, lVal);

            ret = DEVICE_OK;
        }
        break;
    case  MM::BeforeGet:
        {
            double dblVal = 0.0f;
            TUCAM_Prop_GetValue(m_opCam.hIdxTUCam, TUIDP_SATURATION, &dblVal);

            pProp->Set((long)(dblVal));

            ret = DEVICE_OK;
        }
        break;
    default:
        break;
    }

    return ret;
}

/**
* Handles "WhiteBalance" property.
*/
int CMMTUCam::OnWhiteBalance(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    if (NULL == m_opCam.hIdxTUCam)
        return DEVICE_NOT_CONNECTED;

    int ret = DEVICE_ERR;
    switch(eAct)
    {
    case MM::AfterSet:
        {
            string val;
            pProp->Get(val);
            if (val.length() != 0)
            {
                TUCAM_CAPA_ATTR capaAttr;
                capaAttr.idCapa = TUIDC_ATWBALANCE;

                if (TUCAMRET_SUCCESS == TUCAM_Capa_GetAttr(m_opCam.hIdxTUCam, &capaAttr))
                {
                    if (0 == val.compare("Click"))
                    {
                        TUCAM_Capa_SetValue(m_opCam.hIdxTUCam, TUIDC_ATWBALANCE, 1);
                    }
                    else
                    {
                        if (0 == val.compare("TRUE"))
                        {
                            TUCAM_Capa_SetValue(m_opCam.hIdxTUCam, TUIDC_ATWBALANCE, 2);
                        }
                        else
                        {
                            TUCAM_Capa_SetValue(m_opCam.hIdxTUCam, TUIDC_ATWBALANCE, 0);
                        }
                    }
                }

                OnPropertyChanged(g_PropNameATWB, val.c_str());

                ret = DEVICE_OK;
            }
        }
        break;
    case MM::BeforeGet:
        {
            int nVal = 0;
            TUCAM_Capa_GetValue(m_opCam.hIdxTUCam, TUIDC_ATWBALANCE, &nVal);

            string val;
            pProp->Get(val);

            if (0 == val.compare("Click"))
            {

            }
            else
            {
                if (2 == nVal)
                {
                    pProp->Set("TRUE");
                }
                else
                {
                    pProp->Set("FALSE");
                }
            }

            ret = DEVICE_OK;
        }
        break;
    default:
        break;
    }

    return ret;
}

/**
* Handles "RedGain" property.
*/
int CMMTUCam::OnRedGain(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    if (NULL == m_opCam.hIdxTUCam)
        return DEVICE_NOT_CONNECTED;

    int ret = DEVICE_ERR;
    switch(eAct)
    {
    case MM::AfterSet:
        {
            long lVal = 0;
            pProp->Get(lVal);

            TUCAM_Prop_SetValue(m_opCam.hIdxTUCam, TUIDP_CHNLGAIN, lVal, 1);

            ret = DEVICE_OK;
        }
        break;
    case  MM::BeforeGet:
        {
            double dblVal = 0.0f;
            TUCAM_Prop_GetValue(m_opCam.hIdxTUCam, TUIDP_CHNLGAIN, &dblVal, 1);

            pProp->Set((long)(dblVal));

            ret = DEVICE_OK;
        }
        break;
    default:
        break;
    }

    return ret;
}

/**
* Handles "GreenGain" property.
*/
int CMMTUCam::OnGreenGain(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    if (NULL == m_opCam.hIdxTUCam)
        return DEVICE_NOT_CONNECTED;

    int ret = DEVICE_ERR;
    switch(eAct)
    {
    case MM::AfterSet:
        {
            long lVal = 0;
            pProp->Get(lVal);

            TUCAM_Prop_SetValue(m_opCam.hIdxTUCam, TUIDP_CHNLGAIN, lVal, 2);

            ret = DEVICE_OK;
        }
        break;
    case  MM::BeforeGet:
        {
            double dblVal = 0.0f;
            TUCAM_Prop_GetValue(m_opCam.hIdxTUCam, TUIDP_CHNLGAIN, &dblVal, 2);

            pProp->Set((long)(dblVal));

            ret = DEVICE_OK;
        }
        break;
    default:
        break;
    }

    return ret;
}

/**
* Handles "BlueGain" property.
*/
int CMMTUCam::OnBlueGain(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    if (NULL == m_opCam.hIdxTUCam)
        return DEVICE_NOT_CONNECTED;

    int ret = DEVICE_ERR;
    switch(eAct)
    {
    case MM::AfterSet:
        {
            long lVal = 0;
            pProp->Get(lVal);

            TUCAM_Prop_SetValue(m_opCam.hIdxTUCam, TUIDP_CHNLGAIN, lVal, 3);

            ret = DEVICE_OK;
        }
        break;
    case  MM::BeforeGet:
        {
            double dblVal = 0.0f;
            TUCAM_Prop_GetValue(m_opCam.hIdxTUCam, TUIDP_CHNLGAIN, &dblVal, 3);

            pProp->Set((long)(dblVal));

            ret = DEVICE_OK;
        }
        break;
    default:
        break;
    }

    return ret;
}

/**
* Handles "ATExposure" property.
*/
int CMMTUCam::OnATExposure(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    if (NULL == m_opCam.hIdxTUCam)
        return DEVICE_NOT_CONNECTED;

    int ret = DEVICE_ERR;
    switch(eAct)
    {
    case MM::AfterSet:
        {
            string val;
            pProp->Get(val);
            if (val.length() != 0)
            {
                TUCAM_CAPA_ATTR capaAttr;
                capaAttr.idCapa = TUIDC_ATEXPOSURE;

                if (TUCAMRET_SUCCESS == TUCAM_Capa_GetAttr(m_opCam.hIdxTUCam, &capaAttr))
                {
                    if (0 == val.compare("TRUE"))
                    {
                        TUCAM_Capa_SetValue(m_opCam.hIdxTUCam, TUIDC_ATEXPOSURE, 1);
                    }
                    else
                    {
                        TUCAM_Capa_SetValue(m_opCam.hIdxTUCam, TUIDC_ATEXPOSURE, 0);
                    }
                }

                OnPropertyChanged(g_PropNameATEXP, val.c_str());

                ret = DEVICE_OK;
            }
        }
        break;
    case MM::BeforeGet:
        {
            int nVal = 0;
            TUCAM_Capa_GetValue(m_opCam.hIdxTUCam, TUIDC_ATEXPOSURE, &nVal);

            if (1 == nVal)
            {
                pProp->Set("TRUE");
            }
            else
            {
                pProp->Set("FALSE");
            }

            ret = DEVICE_OK;
        }
        break;
    default:
        break;
    }

    return ret;
}

/**
* Handles "Temperature" property.
*/
int CMMTUCam::OnTemperature(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    if (NULL == m_opCam.hIdxTUCam)
        return DEVICE_NOT_CONNECTED;

    int ret = DEVICE_ERR;
    switch(eAct)
    {
    case MM::AfterSet:
        {
            double dblTemp;
            pProp->Get(dblTemp);          

            m_fValTemp = (float)dblTemp;
            TUCAM_Prop_SetValue(m_opCam.hIdxTUCam, TUIDP_TEMPERATURE, (dblTemp + 50));

            ret = DEVICE_OK;
        }
        break;
    case MM::BeforeGet:
        {
            pProp->Set(m_fValTemp);

            ret = DEVICE_OK;
        }
        break;
    default:
        break;
    }

    return ret;
}

/**
* Handles "Fan" property.
*/
int CMMTUCam::OnFan(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    if (NULL == m_opCam.hIdxTUCam)
        return DEVICE_NOT_CONNECTED;

    int ret = DEVICE_ERR;
    switch(eAct)
    {
    case MM::AfterSet:
        {
            string val;
            pProp->Get(val);

            if (val.length() != 0)
            {
                TUCAM_CAPA_ATTR capaAttr;
                capaAttr.idCapa = TUIDC_FAN_GEAR;

                if (TUCAMRET_SUCCESS == TUCAM_Capa_GetAttr(m_opCam.hIdxTUCam, &capaAttr))
                {
                    char szBuf[64] = {0};
                    TUCAM_VALUE_TEXT valText;
                    valText.nID       = TUIDC_FAN_GEAR;
                    valText.nTextSize = 64;
                    valText.pText     = &szBuf[0];

                    int nCnt = capaAttr.nValMax - capaAttr.nValMin + 1;

                    for (int i=0; i<nCnt; i++)
                    {
                        valText.dbValue = i;
                        TUCAM_Capa_GetValueText(m_opCam.hIdxTUCam, &valText); 

                        if (0 == val.compare(valText.pText))
                        {
                            TUCAM_Capa_SetValue(m_opCam.hIdxTUCam, TUIDC_FAN_GEAR, i);
                            break;
                        }                         
                    }
                }

                OnPropertyChanged(g_PropNamePCLK, val.c_str());

                ret = DEVICE_OK;
            }
        }
        break;
    case MM::BeforeGet:
        {
            int nIdx = 0;
            TUCAM_Capa_GetValue(m_opCam.hIdxTUCam, TUIDC_FAN_GEAR, &nIdx);

            char szBuf[64] = {0};
            TUCAM_VALUE_TEXT valText;
            valText.nID       = TUIDC_FAN_GEAR;
            valText.nTextSize = 64;
            valText.pText     = &szBuf[0];

            valText.dbValue = nIdx;
            TUCAM_Capa_GetValueText(m_opCam.hIdxTUCam, &valText); 

            pProp->Set(valText.pText);

            ret = DEVICE_OK;
        }
        break;
    default:
        break;
    }

    return ret; 
}

/**
* Handles "LeftLevels" property.
*/
int CMMTUCam::OnLeftLevels(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    if (NULL == m_opCam.hIdxTUCam)
        return DEVICE_NOT_CONNECTED;

    int ret = DEVICE_ERR;
    switch(eAct)
    {
    case MM::AfterSet:
        {
            double dblLLev = 0.0f;
            double dblRLev = 0.0f;
            pProp->Get(dblLLev);          

            TUCAM_Prop_GetValue(m_opCam.hIdxTUCam, TUIDP_RGTLEVELS, &dblRLev);
            TUCAM_Prop_SetValue(m_opCam.hIdxTUCam, TUIDP_LFTLEVELS, dblLLev);

            if ((int)dblLLev > (int)dblRLev)
            {
                dblRLev = dblLLev + 1;
                TUCAM_Prop_SetValue(m_opCam.hIdxTUCam, TUIDP_RGTLEVELS, dblRLev);
            }

            ret = DEVICE_OK;
        }
        break;
    case MM::BeforeGet:
        {
            double dblLLev = 0.0f;

            TUCAM_Prop_GetValue(m_opCam.hIdxTUCam, TUIDP_LFTLEVELS, &dblLLev);
            
            pProp->Set(dblLLev);

            ret = DEVICE_OK;
        }
        break;
    default:
        break;
    }

    return ret;
}

/**
* Handles "RightLevels" property.
*/
int CMMTUCam::OnRightLevels(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    if (NULL == m_opCam.hIdxTUCam)
        return DEVICE_NOT_CONNECTED;

    int ret = DEVICE_ERR;
    switch(eAct)
    {
    case MM::AfterSet:
        {
            double dblLLev = 0.0f;
            double dblRLev = 0.0f;
            pProp->Get(dblRLev);          

            TUCAM_Prop_GetValue(m_opCam.hIdxTUCam, TUIDP_LFTLEVELS, &dblLLev);
            TUCAM_Prop_SetValue(m_opCam.hIdxTUCam, TUIDP_RGTLEVELS, dblRLev);

            if ((int)dblLLev > (int)dblRLev)
            {
                dblLLev = dblRLev - 1;
                TUCAM_Prop_SetValue(m_opCam.hIdxTUCam, TUIDP_LFTLEVELS, dblLLev);
            }

            ret = DEVICE_OK;
        }
        break;
    case MM::BeforeGet:
        {
            double dblRLev = 0.0f;

            TUCAM_Prop_GetValue(m_opCam.hIdxTUCam, TUIDP_RGTLEVELS, &dblRLev);

            pProp->Set(dblRLev);

            ret = DEVICE_OK;
        }
        break;
    default:
        break;
    }

    return ret;
}

/**
* Handles "IamgeFormat" property.
*/
int CMMTUCam::OnImageFormat(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    if (NULL == m_opCam.hIdxTUCam)
        return DEVICE_NOT_CONNECTED;

    int ret = DEVICE_ERR;
    switch(eAct)
    {
    case MM::AfterSet:
        {
            string val;
            pProp->Get(val);

            if (val.length() != 0)
            {
                if (0 == val.compare(g_Format_RAW))
                {

                }  

                char szPath[MAX_PATH];
                GetCurrentDirectory(MAX_PATH, szPath);
                strcat(szPath, g_FileName);

                OutputDebugString(szPath);

                // Create not exists folder
                if (!PathIsDirectory(szPath))
                    CreateDirectory(szPath, NULL);

                SYSTEMTIME sysTm;
                GetLocalTime(&sysTm);
                sprintf(m_szImgPath, ("%s\\MM_%02d%02d%02d%02d%03d"), szPath, sysTm.wDay, sysTm.wHour, sysTm.wMinute, sysTm.wSecond, sysTm.wMilliseconds);

                m_bSaving = true;

                OutputDebugString(m_szImgPath);
            }

            ret = DEVICE_OK;
        }
        break;
    case MM::BeforeGet:
        {
            pProp->Set(g_Format_RAW);

            ret = DEVICE_OK;
        }
        break;
    default:
        break;
    }

    return ret;
}


/*
int CMMTUCam::OnFormatState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet)
	{
		int picType = 0;
		m_Cam.CGetImageFormat(picType);
		if (picType & SNAP_PNG)
		{
			pProp->Set(g_Format_PNG);
		}
		else if (picType & SNAP_TIF)
		{
			pProp->Set(g_Format_TIF);
		}
		else if (picType & SNAP_JPG)
		{
			pProp->Set(g_Format_JPG);
		}
		else if (picType & SNAP_BMP)
		{
			pProp->Set(g_Format_BMP);
		}
		else if (picType & SNAP_RAW)
		{
			pProp->Set(g_Format_RAW);
		}
	}
	else if (eAct == MM::AfterSet)
	{
		string val;
		pProp->Get(val);
		if (val.compare(g_Format_PNG) == 0)
		{
			m_Cam.CSetImageFormat(SNAP_PNG);
		}
		else if (val.compare(g_Format_TIF) == 0)
		{
			m_Cam.CSetImageFormat(SNAP_TIF);
		}
		else if (val.compare(g_Format_JPG) == 0)
		{
			m_Cam.CSetImageFormat(SNAP_JPG);
		}
		else if (val.compare(g_Format_BMP) == 0)
		{
			m_Cam.CSetImageFormat(SNAP_BMP);
		}
		else if (val.compare(g_Format_RAW) == 0)
		{
			m_Cam.CSetImageFormat(SNAP_RAW);
		}
	}
	return DEVICE_OK;
}

int CMMTUCam::OnFilePath(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet)
	{
		pProp->Set(m_Cam.GetFilePath());
	}
	else if (eAct == MM::AfterSet)
	{
		string val;
		pProp->Get(val);
		m_Cam.SetFilePath((char*)val.c_str());
	}
	return DEVICE_OK;
}
*/

/**
* Handles "ReadoutTime" property.
*/
int CMMTUCam::OnReadoutTime(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   {
      double readoutMs;
      pProp->Get(readoutMs);

      readoutUs_ = readoutMs * 1000.0;
   }
   else if (eAct == MM::BeforeGet)
   {
      pProp->Set(readoutUs_ / 1000.0);
   }

   return DEVICE_OK;
}

int CMMTUCam::OnDropPixels(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   {
      long tvalue = 0;
      pProp->Get(tvalue);
		dropPixels_ = (0==tvalue)?false:true;
   }
   else if (eAct == MM::BeforeGet)
   {
      pProp->Set(dropPixels_?1L:0L);
   }

   return DEVICE_OK;
}

int CMMTUCam::OnFastImage(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   {
      long tvalue = 0;
      pProp->Get(tvalue);
		fastImage_ = (0==tvalue)?false:true;
   }
   else if (eAct == MM::BeforeGet)
   {
      pProp->Set(fastImage_?1L:0L);
   }

   return DEVICE_OK;
}

int CMMTUCam::OnSaturatePixels(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   {
      long tvalue = 0;
      pProp->Get(tvalue);
		saturatePixels_ = (0==tvalue)?false:true;
   }
   else if (eAct == MM::BeforeGet)
   {
      pProp->Set(saturatePixels_?1L:0L);
   }

   return DEVICE_OK;
}

int CMMTUCam::OnFractionOfPixelsToDropOrSaturate(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   {
      double tvalue = 0;
      pProp->Get(tvalue);
		fractionOfPixelsToDropOrSaturate_ = tvalue;
   }
   else if (eAct == MM::BeforeGet)
   {
      pProp->Set(fractionOfPixelsToDropOrSaturate_);
   }

   return DEVICE_OK;
}

int CMMTUCam::OnShouldRotateImages(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   {
      long tvalue = 0;
      pProp->Get(tvalue);
      shouldRotateImages_ = (tvalue != 0);
   }
   else if (eAct == MM::BeforeGet)
   {
      pProp->Set((long) shouldRotateImages_);
   }

   return DEVICE_OK;
}

int CMMTUCam::OnShouldDisplayImageNumber(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   {
      long tvalue = 0;
      pProp->Get(tvalue);
      shouldDisplayImageNumber_ = (tvalue != 0);
   }
   else if (eAct == MM::BeforeGet)
   {
      pProp->Set((long) shouldDisplayImageNumber_);
   }

   return DEVICE_OK;
}

int CMMTUCam::OnStripeWidth(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   {
      pProp->Get(stripeWidth_);
   }
   else if (eAct == MM::BeforeGet)
   {
      pProp->Set(stripeWidth_);
   }

   return DEVICE_OK;
}
/*
* Handles "ScanMode" property.
* Changes allowed Binning values to test whether the UI updates properly
*/
int CMMTUCam::OnScanMode(MM::PropertyBase* pProp, MM::ActionType eAct)
{ 
   if (eAct == MM::AfterSet) {
      pProp->Get(scanMode_);
      SetAllowedBinning();
      if (initialized_) {
         int ret = OnPropertiesChanged();
         if (ret != DEVICE_OK)
            return ret;
      }
   } else if (eAct == MM::BeforeGet) {
      LogMessage("Reading property ScanMode", true);
      pProp->Set(scanMode_);
   }
   return DEVICE_OK;
}


int CMMTUCam::OnCameraCCDXSize(MM::PropertyBase* pProp , MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
		pProp->Set(cameraCCDXSize_);
   }
   else if (eAct == MM::AfterSet)
   {
      long value;
      pProp->Get(value);
		if ( (value < 16) || (33000 < value))
			return DEVICE_ERR;  // invalid image size
		if( value != cameraCCDXSize_)
		{
			cameraCCDXSize_ = value;
			img_.Resize(cameraCCDXSize_/binSize_, cameraCCDYSize_/binSize_);
		}
   }
	return DEVICE_OK;

}

int CMMTUCam::OnCameraCCDYSize(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
		pProp->Set(cameraCCDYSize_);
   }
   else if (eAct == MM::AfterSet)
   {
      long value;
      pProp->Get(value);
		if ( (value < 16) || (33000 < value))
			return DEVICE_ERR;  // invalid image size
		if( value != cameraCCDYSize_)
		{
			cameraCCDYSize_ = value;
			img_.Resize(cameraCCDXSize_/binSize_, cameraCCDYSize_/binSize_);
		}
   }
	return DEVICE_OK;

}

int CMMTUCam::OnTriggerDevice(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(triggerDevice_.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(triggerDevice_);
   }
   return DEVICE_OK;
}


int CMMTUCam::OnCCDTemp(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(ccdT_);
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(ccdT_);
   }
   return DEVICE_OK;
}

int CMMTUCam::OnIsSequenceable(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   std::string val = "Yes";
   if (eAct == MM::BeforeGet)
   {
      if (!isSequenceable_) 
      {
         val = "No";
      }
      pProp->Set(val.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      isSequenceable_ = false;
      pProp->Get(val);
      if (val == "Yes") 
      {
         isSequenceable_ = true;
      }
   }

   return DEVICE_OK;
}


///////////////////////////////////////////////////////////////////////////////
// Private CMMTUCam methods
///////////////////////////////////////////////////////////////////////////////

/**
* Sync internal image buffer size to the chosen property values.
*/
int CMMTUCam::ResizeImageBuffer()
{
    if (NULL == m_opCam.hIdxTUCam)
        return DEVICE_NOT_CONNECTED;

    if (NULL == m_frame.pBuffer)
        return DEVICE_OUT_OF_MEMORY;

    char buf[MM::MaxStrLength];
    //int ret = GetProperty(MM::g_Keyword_Binning, buf);
    //if (ret != DEVICE_OK)
    //   return ret;
    //binSize_ = atol(buf);

    int ret = GetProperty(MM::g_Keyword_PixelType, buf);
    if (ret != DEVICE_OK)
        return ret;

    std::string pixelType(buf);

    int byteDepth = 0;

    if (pixelType.compare(g_PixelType_8bit) == 0)
    {
        byteDepth = 1;
    }
    else if (pixelType.compare(g_PixelType_16bit) == 0)
    {
        byteDepth = 2;
    }
    else if ( pixelType.compare(g_PixelType_32bitRGB) == 0)
    {
        byteDepth = 4;
    }
    else if ( pixelType.compare(g_PixelType_32bit) == 0)
    {
        byteDepth = 4;
    }
    else if ( pixelType.compare(g_PixelType_64bitRGB) == 0)
    {
        byteDepth = 8;
    }

    TUCAM_VALUE_INFO valWidth;
    TUCAM_VALUE_INFO valHeight;
    int nChnnels = (1 == m_frame.ucChannels) ? 1 : 4;

    int nIdxRes = 0;
    if (TUCAMRET_SUCCESS == TUCAM_Capa_GetValue(m_opCam.hIdxTUCam, TUIDC_RESOLUTION, &nIdxRes))
    {
        valWidth.nTextSize = nIdxRes;
        valHeight.nTextSize= nIdxRes;
    }
    
    valWidth.nID = TUIDI_CURRENT_WIDTH;
    if (TUCAMRET_SUCCESS != TUCAM_Dev_GetInfo(m_opCam.hIdxTUCam, &valWidth))
        return DEVICE_NATIVE_MODULE_FAILED;  

    valHeight.nID = TUIDI_CURRENT_HEIGHT;
    if (TUCAMRET_SUCCESS != TUCAM_Dev_GetInfo(m_opCam.hIdxTUCam, &valHeight))
        return DEVICE_NATIVE_MODULE_FAILED; 

    char sz[256] = {0};
    sprintf(sz, "[ResizeImageBuffer]:Width:%d, Height:%d, BytesPerPixel:%d\n", valWidth.nValue, valHeight.nValue, m_frame.ucElemBytes * nChnnels);
    OutputDebugString(sz);

    if (!m_bROI)
    {
        m_nMaxHeight = valHeight.nValue;
    }

#ifdef _WIN64
    img_.Resize(valWidth.nValue, valHeight.nValue, (m_frame.ucElemBytes * nChnnels));
#else
    img_.Resize(valWidth.nValue, valHeight.nValue, (4 == nChnnels ? 4 : (m_frame.ucElemBytes * nChnnels)));
#endif


// #ifdef _WIN64
//     img_.Resize(valWidth.nValue, valHeight.nValue, (m_frame.ucElemBytes * nChnnels));
// #else
//     // We don't use the 16bit data in this app, because of the win32 memory not allowed to create large buffer.
//     img_.Resize(valWidth.nValue, valHeight.nValue, (1/*m_frame.ucElemBytes*/ * nChnnels));
// #endif
//     
    return DEVICE_OK;
}

void CMMTUCam::GenerateEmptyImage(ImgBuffer& img)
{
   MMThreadGuard g(imgPixelsLock_);
   if (img.Height() == 0 || img.Width() == 0 || img.Depth() == 0)
      return;

   unsigned char* pBuf = const_cast<unsigned char*>(img.GetPixels());
   memset(pBuf, 0, img.Height()*img.Width()*img.Depth());
}

void CMMTUCam::TestImage(ImgBuffer& img, double exp)
{
	//std::string pixelType;
	char buf[MM::MaxStrLength];
	GetProperty(MM::g_Keyword_PixelType, buf);
	std::string pixelType(buf);

	if (img.Height() == 0 || img.Width() == 0 || img.Depth() == 0)
		return;

	double lSinePeriod = 3.14159265358979 * stripeWidth_;
	unsigned imgWidth = img.Width();
	unsigned int* rawBuf = (unsigned int*) img.GetPixelsRW();
	double maxDrawnVal = 0;
	long lPeriod = (long) imgWidth / 2;
	double dLinePhase = 0.0;
	const double dAmp = exp;
	double cLinePhaseInc = 2.0 * lSinePeriod / 4.0 / img.Height();
	if (shouldRotateImages_) {
		// Adjust the angle of the sin wave pattern based on how many images
		// we've taken, to increase the period (i.e. time between repeat images).
		cLinePhaseInc *= (((int) dPhase_ / 6) % 24) - 12;
	}

	static bool debugRGB = false;
#ifdef TIFFDEMO
	debugRGB = true;
#endif
	static  unsigned char* pDebug  = NULL;
	static unsigned long dbgBufferSize = 0;
	static long iseq = 1;



	// for integer images: bitDepth_ is 8, 10, 12, 16 i.e. it is depth per component
	long maxValue = (1L << bitDepth_)-1;

	long pixelsToDrop = 0;
	if( dropPixels_)
		pixelsToDrop = (long)(0.5 + fractionOfPixelsToDropOrSaturate_*img.Height()*imgWidth);
	long pixelsToSaturate = 0;
	if( saturatePixels_)
		pixelsToSaturate = (long)(0.5 + fractionOfPixelsToDropOrSaturate_*img.Height()*imgWidth);

	unsigned j, k;
	if (pixelType.compare(g_PixelType_8bit) == 0)
	{
		double pedestal = 127 * exp / 100.0 * GetBinning() * GetBinning();
		unsigned char* pBuf = const_cast<unsigned char*>(img.GetPixels());
		for (j=0; j<img.Height(); j++)
		{
			for (k=0; k<imgWidth; k++)
			{
				long lIndex = imgWidth*j + k;
				unsigned char val = (unsigned char) (g_IntensityFactor_ * min(255.0, (pedestal + dAmp * sin(dPhase_ + dLinePhase + (2.0 * lSinePeriod * k) / lPeriod))));
				if (val > maxDrawnVal) {
					maxDrawnVal = val;
				}
				*(pBuf + lIndex) = val;
			}
			dLinePhase += cLinePhaseInc;
		}
		for(int snoise = 0; snoise < pixelsToSaturate; ++snoise)
		{
			j = (unsigned)( (double)(img.Height()-1)*(double)rand()/(double)RAND_MAX);
			k = (unsigned)( (double)(imgWidth-1)*(double)rand()/(double)RAND_MAX);
			*(pBuf + imgWidth*j + k) = (unsigned char)maxValue;
		}
		int pnoise;
		for(pnoise = 0; pnoise < pixelsToDrop; ++pnoise)
		{
			j = (unsigned)( (double)(img.Height()-1)*(double)rand()/(double)RAND_MAX);
			k = (unsigned)( (double)(imgWidth-1)*(double)rand()/(double)RAND_MAX);
			*(pBuf + imgWidth*j + k) = 0;
		}

	}
	else if (pixelType.compare(g_PixelType_16bit) == 0)
	{
		double pedestal = maxValue/2 * exp / 100.0 * GetBinning() * GetBinning();
		double dAmp16 = dAmp * maxValue/255.0; // scale to behave like 8-bit
		unsigned short* pBuf = (unsigned short*) const_cast<unsigned char*>(img.GetPixels());
		for (j=0; j<img.Height(); j++)
		{
			for (k=0; k<imgWidth; k++)
			{
				long lIndex = imgWidth*j + k;
				unsigned short val = (unsigned short) (g_IntensityFactor_ * min((double)maxValue, pedestal + dAmp16 * sin(dPhase_ + dLinePhase + (2.0 * lSinePeriod * k) / lPeriod)));
				if (val > maxDrawnVal) {
					maxDrawnVal = val;
				}
				*(pBuf + lIndex) = val;
			}
			dLinePhase += cLinePhaseInc;
		}         
		for(int snoise = 0; snoise < pixelsToSaturate; ++snoise)
		{
			j = (unsigned)(0.5 + (double)img.Height()*(double)rand()/(double)RAND_MAX);
			k = (unsigned)(0.5 + (double)imgWidth*(double)rand()/(double)RAND_MAX);
			*(pBuf + imgWidth*j + k) = (unsigned short)maxValue;
		}
		int pnoise;
		for(pnoise = 0; pnoise < pixelsToDrop; ++pnoise)
		{
			j = (unsigned)(0.5 + (double)img.Height()*(double)rand()/(double)RAND_MAX);
			k = (unsigned)(0.5 + (double)imgWidth*(double)rand()/(double)RAND_MAX);
			*(pBuf + imgWidth*j + k) = 0;
		}

	}
	else if (pixelType.compare(g_PixelType_32bit) == 0)
	{
		double pedestal = 127 * exp / 100.0 * GetBinning() * GetBinning();
		float* pBuf = (float*) const_cast<unsigned char*>(img.GetPixels());
		float saturatedValue = 255.;
		memset(pBuf, 0, img.Height()*imgWidth*4);
		// static unsigned int j2;
		for (j=0; j<img.Height(); j++)
		{
			for (k=0; k<imgWidth; k++)
			{
				long lIndex = imgWidth*j + k;
				double value =  (g_IntensityFactor_ * min(255.0, (pedestal + dAmp * sin(dPhase_ + dLinePhase + (2.0 * lSinePeriod * k) / lPeriod))));
				if (value > maxDrawnVal) {
					maxDrawnVal = value;
				}
				*(pBuf + lIndex) = (float) value;
				if( 0 == lIndex)
				{
					std::ostringstream os;
					os << " first pixel is " << (float)value;
					LogMessage(os.str().c_str(), true);

				}
			}
			dLinePhase += cLinePhaseInc;
		}

		for(int snoise = 0; snoise < pixelsToSaturate; ++snoise)
		{
			j = (unsigned)(0.5 + (double)img.Height()*(double)rand()/(double)RAND_MAX);
			k = (unsigned)(0.5 + (double)imgWidth*(double)rand()/(double)RAND_MAX);
			*(pBuf + imgWidth*j + k) = saturatedValue;
		}
		int pnoise;
		for(pnoise = 0; pnoise < pixelsToDrop; ++pnoise)
		{
			j = (unsigned)(0.5 + (double)img.Height()*(double)rand()/(double)RAND_MAX);
			k = (unsigned)(0.5 + (double)imgWidth*(double)rand()/(double)RAND_MAX);
			*(pBuf + imgWidth*j + k) = 0;
		}

	}
	else if (pixelType.compare(g_PixelType_32bitRGB) == 0)
	{
		double pedestal = 127 * exp / 100.0;
		unsigned int * pBuf = (unsigned int*) rawBuf;

		unsigned char* pTmpBuffer = NULL;

		if(debugRGB)
		{
			const unsigned long bfsize = img.Height() * imgWidth * 3;
			if(  bfsize != dbgBufferSize)
			{
				if (NULL != pDebug)
				{
					free(pDebug);
					pDebug = NULL;
				}
				pDebug = (unsigned char*)malloc( bfsize);
				if( NULL != pDebug)
				{
					dbgBufferSize = bfsize;
				}
			}
		}

		// only perform the debug operations if pTmpbuffer is not 0
		pTmpBuffer = pDebug;
		unsigned char* pTmp2 = pTmpBuffer;
		if( NULL!= pTmpBuffer)
			memset( pTmpBuffer, 0, img.Height() * imgWidth * 3);

		for (j=0; j<img.Height(); j++)
		{
			unsigned char theBytes[4];
			for (k=0; k<imgWidth; k++)
			{
				long lIndex = imgWidth*j + k;
				unsigned char value0 =   (unsigned char) min(255.0, (pedestal + dAmp * sin(dPhase_ + dLinePhase + (2.0 * lSinePeriod * k) / lPeriod)));
				theBytes[0] = value0;
				if( NULL != pTmpBuffer)
					pTmp2[2] = value0;
				unsigned char value1 =   (unsigned char) min(255.0, (pedestal + dAmp * sin(dPhase_ + dLinePhase*2 + (2.0 * lSinePeriod * k) / lPeriod)));
				theBytes[1] = value1;
				if( NULL != pTmpBuffer)
					pTmp2[1] = value1;
				unsigned char value2 = (unsigned char) min(255.0, (pedestal + dAmp * sin(dPhase_ + dLinePhase*4 + (2.0 * lSinePeriod * k) / lPeriod)));
				theBytes[2] = value2;

				if( NULL != pTmpBuffer){
					pTmp2[0] = value2;
					pTmp2+=3;
				}
				theBytes[3] = 0;
				unsigned long tvalue = *(unsigned long*)(&theBytes[0]);
				if (tvalue > maxDrawnVal) {
					maxDrawnVal = tvalue;
				}
				*(pBuf + lIndex) =  tvalue ;  //value0+(value1<<8)+(value2<<16);
			}
			dLinePhase += cLinePhaseInc;
		}


		// ImageJ's AWT images are loaded with a Direct Color processor which expects BGRA, that's why we swapped the Blue and Red components in the generator above.
		if(NULL != pTmpBuffer)
		{
			// write the compact debug image...
			char ctmp[12];
			snprintf(ctmp,12,"%ld",iseq++);
			writeCompactTiffRGB(imgWidth, img.Height(), pTmpBuffer, ("democamera" + std::string(ctmp)).c_str());
		}

	}

	// generate an RGB image with bitDepth_ bits in each color
	else if (pixelType.compare(g_PixelType_64bitRGB) == 0)
	{
		double pedestal = maxValue/2 * exp / 100.0 * GetBinning() * GetBinning();
		double dAmp16 = dAmp * maxValue/255.0; // scale to behave like 8-bit

		double maxPixelValue = (1<<(bitDepth_))-1;
		unsigned long long * pBuf = (unsigned long long*) rawBuf;
		for (j=0; j<img.Height(); j++)
		{
			for (k=0; k<imgWidth; k++)
			{
				long lIndex = imgWidth*j + k;
				unsigned long long value0 = (unsigned short) min(maxPixelValue, (pedestal + dAmp16 * sin(dPhase_ + dLinePhase + (2.0 * lSinePeriod * k) / lPeriod)));
				unsigned long long value1 = (unsigned short) min(maxPixelValue, (pedestal + dAmp16 * sin(dPhase_ + dLinePhase*2 + (2.0 * lSinePeriod * k) / lPeriod)));
				unsigned long long value2 = (unsigned short) min(maxPixelValue, (pedestal + dAmp16 * sin(dPhase_ + dLinePhase*4 + (2.0 * lSinePeriod * k) / lPeriod)));
				unsigned long long tval = value0+(value1<<16)+(value2<<32);
				if (tval > maxDrawnVal) {
					maxDrawnVal = static_cast<double>(tval);
				}
				*(pBuf + lIndex) = tval;
			}
			dLinePhase += cLinePhaseInc;
		}
	}

	if (shouldDisplayImageNumber_) {
		// Draw a seven-segment display in the upper-left corner of the image,
		// indicating the image number.
		int divisor = 1;
		int numDigits = 0;
		while (imageCounter_ / divisor > 0) {
			divisor *= 10;
			numDigits += 1;
		}
		int remainder = imageCounter_;
		for (int i = 0; i < numDigits; ++i) {
			// Black out the background for this digit.
			// TODO: for now, hardcoded sizes, which will cause buffer
			// overflows if the image size is too small -- but that seems
			// unlikely.
			int xBase = (numDigits - i - 1) * 20 + 2;
			int yBase = 2;
			for (int x = xBase; x < xBase + 20; ++x) {
				for (int y = yBase; y < yBase + 20; ++y) {
					long lIndex = imgWidth*y + x;

					if (pixelType.compare(g_PixelType_8bit) == 0) {
						*((unsigned char*) rawBuf + lIndex) = 0;
					}
					else if (pixelType.compare(g_PixelType_16bit) == 0) {
						*((unsigned short*) rawBuf + lIndex) = 0;
					}
					else if (pixelType.compare(g_PixelType_32bit) == 0 ||
						pixelType.compare(g_PixelType_32bitRGB) == 0) {
							*((unsigned int*) rawBuf + lIndex) = 0;
					}
				}
			}
			// Draw each segment, if appropriate.
			int digit = remainder % 10;
			for (int segment = 0; segment < 7; ++segment) {
				if (!((1 << segment) & SEVEN_SEGMENT_RULES[digit])) {
					// This segment is not drawn.
					continue;
				}
				// Determine if the segment is horizontal or vertical.
				int xStep = SEVEN_SEGMENT_HORIZONTALITY[segment];
				int yStep = (xStep + 1) % 2;
				// Calculate starting point for drawing the segment.
				int xStart = xBase + SEVEN_SEGMENT_X_OFFSET[segment] * 16;
				int yStart = yBase + SEVEN_SEGMENT_Y_OFFSET[segment] * 8 + 1;
				// Draw one pixel at a time of the segment.
				for (int pixNum = 0; pixNum < 8 * (xStep + 1); ++pixNum) {
					long lIndex = imgWidth * (yStart + pixNum * yStep) + (xStart + pixNum * xStep);
					if (pixelType.compare(g_PixelType_8bit) == 0) {
						*((unsigned char*) rawBuf + lIndex) = static_cast<unsigned char>(maxDrawnVal);
					}
					else if (pixelType.compare(g_PixelType_16bit) == 0) {
						*((unsigned short*) rawBuf + lIndex) = static_cast<unsigned short>(maxDrawnVal);
					}
					else if (pixelType.compare(g_PixelType_32bit) == 0 ||
						pixelType.compare(g_PixelType_32bitRGB) == 0) {
							*((unsigned int*) rawBuf + lIndex) = static_cast<unsigned int>(maxDrawnVal);
					}
				}
			}
			remainder /= 10;
		}
	}
	dPhase_ += lSinePeriod / 4.;
}

/**
* Generate a spatial sine wave.
*/
void CMMTUCam::GenerateSyntheticImage(ImgBuffer& img, double exp)
{ 

    MMThreadGuard g(imgPixelsLock_);

    TestImage(img, exp);
    OutputDebugString("[GenerateSyntheticImage]\n");
}


void CMMTUCam::TestResourceLocking(const bool recurse)
{
    if(recurse)
        TestResourceLocking(false);
}

void __cdecl CMMTUCam::GetTemperatureThread(LPVOID lParam)
{
    CMMTUCam *pCam = (CMMTUCam *)lParam;

    if (NULL != pCam)
    {
        pCam->RunTemperature();
    }

    SetEvent(pCam->m_hThdTempEvt);
    OutputDebugString("Leave get the value of temperature thread!\n");
    _endthread();
}

void CMMTUCam::RunTemperature()
{
    DWORD dw = GetTickCount();

    while (m_bTemping)
    {
        if (GetTickCount() - dw > 1000)
        {
            double dblVal = 0.0f;
            TUCAM_Prop_GetValue(m_opCam.hIdxTUCam, TUIDP_TEMPERATURE, &dblVal);

            m_fCurTemp = (float)dblVal;
           
            dw = GetTickCount();
        }
        else
        {
            Sleep(100);
        }
    }
}

void __cdecl CMMTUCam::WaitForFrameThread(LPVOID lParam)
{
    CMMTUCam *pCam = (CMMTUCam *)lParam;

    if (NULL != pCam)
    {
        pCam->RunWaiting();
    }

    SetEvent(pCam->m_hThdWaitEvt);
    OutputDebugString("Leave wait for frame thread!\n");
    _endthread();
}

void CMMTUCam::RunWaiting()
{
    while (m_bLiving)
    {
        m_frame.ucFormatGet = TUFRM_FMT_USUAl;
        if (TUCAMRET_SUCCESS == TUCAM_Buf_WaitForFrame(m_opCam.hIdxTUCam, &m_frame))
        {
        }
    }
}

int CMMTUCam::InitTUCamApi()
{
    m_itApi.pstrConfigPath = "";
    m_itApi.uiCamCount     = 0;

    TUCAMRET nRet = TUCAM_Api_Init(&m_itApi);

    if (TUCAMRET_SUCCESS != nRet && TUCAMRET_INIT != nRet)
        return DEVICE_NOT_CONNECTED;
    
    if (0 == m_itApi.uiCamCount)
        return DEVICE_NOT_CONNECTED;

    m_opCam.uiIdxOpen = 0;
    if (TUCAMRET_SUCCESS !=  TUCAM_Dev_Open(&m_opCam))
        return DEVICE_NOT_CONNECTED;

    if (NULL == m_opCam.hIdxTUCam)
        return DEVICE_NOT_CONNECTED;

    return DEVICE_OK;
}

int CMMTUCam::UninitTUCamApi()
{
    ReleaseBuffer();

    if (NULL != m_opCam.hIdxTUCam)
    {
        OutputDebugString("[TUCAM_Dev_Close]\n");
        TUCAM_Dev_Close(m_opCam.hIdxTUCam);     // close camera
        m_opCam.hIdxTUCam = NULL;
    }

    TUCAM_Api_Uninit();                         // release SDK resource

    return DEVICE_OK;
}

int CMMTUCam::AllocBuffer()
{
    if (NULL == m_opCam.hIdxTUCam)
        return DEVICE_NOT_CONNECTED;

    // TUCam resource
    m_frame.pBuffer     = NULL;
    m_frame.ucFormatGet = TUFRM_FMT_USUAl;
    m_frame.uiRsdSize   = 1;                    // how many frames do you want

    // Alloc buffer after set resolution or set ROI attribute
    if (TUCAMRET_SUCCESS != TUCAM_Buf_Alloc(m_opCam.hIdxTUCam, &m_frame))
    {
        return DEVICE_OUT_OF_MEMORY; 
    }

    if (3 == m_frame.ucChannels)
    {
        nComponents_ = 4;   // channels RGBA

#ifdef _WIN64
        // Do not have enough memory 
        if (2 == m_frame.ucElemBytes)
        {
            bitDepth_ = 16;
            SetProperty(MM::g_Keyword_PixelType, g_PixelType_64bitRGB);
        }  
        else
        {
            bitDepth_ = 8;
            SetProperty(MM::g_Keyword_PixelType, g_PixelType_32bitRGB);
        } 
#else
        bitDepth_ = 8;
        SetProperty(MM::g_Keyword_PixelType, g_PixelType_32bitRGB);
#endif

    }
    else
    {
        nComponents_ = 1;   // channels Gray

        if (2 == m_frame.ucElemBytes)
        {
            bitDepth_ = 16;
            SetProperty(MM::g_Keyword_PixelType, g_PixelType_16bit);
        }  
        else
        {
            bitDepth_ = 8;
            SetProperty(MM::g_Keyword_PixelType, g_PixelType_8bit);
        }
    }

    
    SetProperty(MM::g_Keyword_PixelType, g_PixelType_16bit);
/*
    if (3 == m_frame.ucChannels)
    {
        

#ifdef _WIN64
        if (2 == m_frame.ucElemBytes)
        {
            SetProperty(MM::g_Keyword_PixelType, g_PixelType_64bitRGB);
        }  
        else
        {
            SetProperty(MM::g_Keyword_PixelType, g_PixelType_32bitRGB);
        }     
#else
        SetProperty(MM::g_Keyword_PixelType, g_PixelType_32bitRGB);
#endif
        
    }
    else
    {
        nComponents_ = 1;   // channels Gray

#ifdef _WIN64
        if (2 == m_frame.ucElemBytes)
        {
            SetProperty(MM::g_Keyword_PixelType, g_PixelType_16bit);
        }  
        else
        {
            SetProperty(MM::g_Keyword_PixelType, g_PixelType_8bit);
        }
#else
        SetProperty(MM::g_Keyword_PixelType, g_PixelType_8bit);
#endif  

    }    
*/
    return DEVICE_OK;
}

int CMMTUCam::ResizeBuffer()
{
    if (NULL == m_opCam.hIdxTUCam)
        return DEVICE_NOT_CONNECTED;

    TUCAM_Buf_Release(m_opCam.hIdxTUCam);
    AllocBuffer();

    return DEVICE_OK;
}

int CMMTUCam::ReleaseBuffer()
{
    if (NULL == m_opCam.hIdxTUCam)
        return DEVICE_NOT_CONNECTED;

    TUCAM_Buf_Release(m_opCam.hIdxTUCam);                   // Release alloc buffer after stop capture and quit drawing thread

    return DEVICE_OK;
}

int CMMTUCam::StopCapture()
{
    if (thd_->IsStopped())
        return DEVICE_OK;

    m_bLiving = false;

    thd_->Stop(); 

    TUCAM_Buf_AbortWait(m_opCam.hIdxTUCam);                 // If you called TUCAM_Buf_WaitForFrames()

    thd_->wait();

    TUCAM_Cap_Stop(m_opCam.hIdxTUCam);                      // Stop capture   
    ReleaseBuffer();

    return DEVICE_OK;

/*
    if (!m_bLiving)
        return DEVICE_OK;

    m_bLiving = false;

    if (NULL != m_hThdWait)
    {
        TUCAM_Buf_AbortWait(m_opCam.hIdxTUCam);             // If you called TUCAM_Buf_WaitForFrames()

        WaitForSingleObject(m_hThdWait, INFINITE);
        CloseHandle(m_hThdWait);	
        m_hThdWait = NULL;

        TUCAM_Cap_Stop(m_opCam.hIdxTUCam);                  // Stop capture   
        ReleaseBuffer();
    }

    return DEVICE_OK;
*/
}

int CMMTUCam::StartCapture()
{
    if (m_bLiving)
        return DEVICE_OK;

    m_bLiving = true;

    int nRet = AllocBuffer();
    if (nRet != DEVICE_OK)
    {
        return nRet;
    }

    // Start capture
    if (TUCAMRET_SUCCESS == TUCAM_Cap_Start(m_opCam.hIdxTUCam, TUCCM_SEQUENCE))
    {
        return nRet;
    }

    return DEVICE_ERR;

/*
    if (m_bLiving)
        return DEVICE_OK;

    m_bLiving = true;

    int nRet = DEVICE_OK;

    if (NULL == m_hThdWait)
    {
        nRet = AllocBuffer();
        if (nRet != DEVICE_OK)
        {
            return nRet;
        }

        TUCAM_Cap_Start(m_opCam.hIdxTUCam, TUCCM_SEQUENCE); // Start capture

        m_hThdWait = CreateEvent(NULL, TRUE, FALSE, NULL);
        _beginthread(WaitForFrameThread, 0, this);          // Start wait for frame thread
    }

    return nRet;
*/
}

int CMMTUCam::WaitForFrame(ImgBuffer& img)
{
    MMThreadGuard g(imgPixelsLock_);

    m_frame.ucFormatGet = TUFRM_FMT_USUAl;  // Set usual format
    if (TUCAMRET_SUCCESS == TUCAM_Buf_WaitForFrame(m_opCam.hIdxTUCam, &m_frame))
    {
//         char sz[256] = {0};
//         sprintf(sz, "[TUCAM_Buf_WaitForFrame]:%d, %d, %d, %d\n", m_frame.usWidth, m_frame.usHeight, m_frame.ucElemBytes, m_frame.ucChannels);
//         OutputDebugString(sz);

        if (img.Height() == 0 || img.Width() == 0 || img.Depth() == 0)
            return DEVICE_OUT_OF_MEMORY;

        int nWid = m_frame.usWidth;
        int nHei = m_frame.usHeight;
        int nPix = nWid * nHei;

//#ifdef _WIN64
        
        if (2 == m_frame.ucElemBytes)
        {
            if (3 == m_frame.ucChannels)
            {
#ifdef _WIN64
                unsigned short* pSrc = (unsigned short *)(m_frame.pBuffer + m_frame.usHeader);
                unsigned short* pDst = (unsigned short *)(img.GetPixelsRW());

                for (int i=0; i<nPix; ++i) 
                {
                    *pDst++ = *pSrc++;
                    *pDst++ = *pSrc++;
                    *pDst++ = *pSrc++;
                    *pDst++ = 0;
                }
#else
                unsigned short* pSrc = (unsigned short *)(m_frame.pBuffer + m_frame.usHeader);
                unsigned char* pDst  = (unsigned char *)(img.GetPixelsRW());

                for (int i=0; i<nPix; ++i) 
                {
                    *pDst++ = (*pSrc++) >> 8;
                    *pDst++ = (*pSrc++) >> 8;
                    *pDst++ = (*pSrc++) >> 8;
                    *pDst++ = 0;
                }
#endif
            }
            else
            {
                unsigned short* pSrc = (unsigned short *)(m_frame.pBuffer + m_frame.usHeader);
                unsigned short* pDst = (unsigned short *)(img.GetPixelsRW());

                memcpy(pDst, pSrc, m_frame.uiImgSize);
            }   
        }
        else
        {
            unsigned char* pSrc = (unsigned char *)(m_frame.pBuffer + m_frame.usHeader);
            unsigned char* pDst = (unsigned char *)(img.GetPixelsRW());

            if (3 == m_frame.ucChannels)
            {
                for (int i=0; i<nPix; ++i) 
                {
                    *pDst++ = *pSrc++;
                    *pDst++ = *pSrc++;
                    *pDst++ = *pSrc++;
                    *pDst++ = 0;
                }
            }
            else
            {
                memcpy(pDst, pSrc, m_frame.uiImgSize);
            }  
        }

  
// #else
//         unsigned char* pSrc = (unsigned char *)(m_frame.pBuffer + m_frame.usHeader + m_frame.ucElemBytes / 2);
//         unsigned char* pDst = (unsigned char *)(img.GetPixelsRW());
// 
//         if (3 == m_frame.ucChannels)
//         {
//             for (int i=0; i<nPix; ++i) 
//             {
//                 *pDst++ = *pSrc;
//                 pSrc += m_frame.ucElemBytes;
//                 *pDst++ = *pSrc;
//                 pSrc += m_frame.ucElemBytes;
//                 *pDst++ = *pSrc;
//                 pSrc += m_frame.ucElemBytes;
//                 *pDst++ = 0;
//             }
//         }
//         else
//         {
//             for (int i=0; i<nPix; ++i) 
//             {
//                 *pDst++ = *pSrc;
//                 pSrc += m_frame.ucElemBytes;
//             }
//         }
//#endif

        if (m_bSaving)
        {
            m_frame.ucFormatGet = TUFRM_FMT_RAW;
            TUCAM_Buf_CopyFrame(m_opCam.hIdxTUCam, &m_frame);
            SaveRaw(m_szImgPath, m_frame.pBuffer, m_frame.uiImgSize + m_frame.usHeader);

            m_bSaving = false;
        }

//         if (3 == m_frame.ucChannels)
//         {
//             if (2 == m_frame.ucElemBytes)
//             {
//                 unsigned short* pSrc = (unsigned short *)(m_frame.pBuffer + m_frame.usHeader);
//                 unsigned short* pDst = (unsigned short *)(img.GetPixelsRW());
// 
//                 for (int i=0; i<nPix; ++i) 
//                 {
//                     *pDst++ = *pSrc++;
//                     *pDst++ = *pSrc++;
//                     *pDst++ = *pSrc++;
//                     *pDst++ = 0;
//                 }
//             }
//             else
//             {
//                 unsigned char* pSrc = m_frame.pBuffer + m_frame.usHeader;
//                 unsigned char* pDst = img.GetPixelsRW();
// 
//                 for (int i=0; i<nPix; ++i) 
//                 {
//                     *pDst++ = *pSrc++;
//                     *pDst++ = *pSrc++;
//                     *pDst++ = *pSrc++;
//                     *pDst++ = 0;
//                 }
//             }
//         }
//         else 
//         {
//             if (2 == m_frame.ucElemBytes)
//             {
//                 unsigned char* pSrc = m_frame.pBuffer + m_frame.usHeader;
//                 unsigned char* pDst = img.GetPixelsRW();
// 
//                 for (int i=0; i<nPix; ++i) 
//                 {
//                     *pDst++ = *(pSrc+1);
//                     *pDst++ = *pSrc;
// 
//                     pSrc += 2;
//                 }
//             }
//             else
//             {
//                 memcpy(img.GetPixelsRW(), m_frame.pBuffer+m_frame.usHeader, m_frame.uiImgSize); 
//             }           
//         }
        

        return DEVICE_OK;
    }

    return DEVICE_NATIVE_MODULE_FAILED;
}

bool CMMTUCam::SaveRaw(char *pfileName, unsigned char *pData, unsigned long ulSize)
{
    FILE *pfile = NULL;
    string szPath = pfileName;
    szPath += ".raw";

    OutputDebugString(szPath.c_str());

    pfile = fopen(szPath.c_str(), "wb");

    if(NULL != pfile) 
    {
        fwrite(pData, 1, ulSize, pfile);
        fclose(pfile);

//        delete pfile;

        pfile = NULL;
        OutputDebugString("[SaveRaw]:NULL!\n");

        return true;
    }

    return false;
}
