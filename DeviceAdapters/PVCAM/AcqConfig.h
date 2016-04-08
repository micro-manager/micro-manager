#ifndef _ACQCONFIG_H_
#define _ACQCONFIG_H_

#include "PvRoi.h"
/**
* This class holds the camera and acquisition configuration. The idea is that
* if user changes a property during live mode the configuration is stored in
* "future" configuration class. Once the live mode is restarted the "future"
* configuration is compared to "current" configuration and the camera, buffers
* and other acquisition variables are set accordingly.
* This approach resolves the problem that comes from how MM handles property
* changes during live mode:
* 1) The OnProperty() handler is called
* 2) StopSequenceAcquisition() is called
* 3) StartSequenceAcquisition() is called
* Because the OnProperty() is called during live acquisition some intermediate
* variables cannot be updated right away - for example binning or ROI because
* these are still used in ongoing acquisition. Instead, we need to remeber the
* "new" or "future" state that must be applied once StartSequenceAcquisition()
* is called.
* Previously we had many variables like: newColorMode, currentColorMode, newBinning,
* currentBinning, etc. These are, or should be all transferred to this class.
*/
class AcqConfig
{
public:
    AcqConfig();

    /**
    * Embedded frame metadata enabled or disabled.
    */
    bool FrameMetadataEnabled;
    /**
    * Centroids - camera selected ROIs enabled or disabled.
    */
    bool CentroidsEnabled;
    /**
    * Centroids radius.
    */
    int CentroidsRadius;
    /**
    * Number of centroids to acquire.
    */
    int CentroidsCount;
    /**
    * Total number of ROIs for the current acquisition. This could be either
    * the Centroids Count or number of user defined ROIs (1 or more if supported)
    */
    int RoiCount;
    /**
    * Selected fan speed.
    */
    int FanSpeedSetpoint;
    /**
    * Region of interest.
    */
    PvRoi Roi;
    /**
    * Color on or off.
    */
    bool ColorProcessingEnabled;
    /**
    * Selected mask used for debayering algorithm (must correspond to CFA masks defined
    * in PvDebayer.h - CFA_RGGB, CFA_GRBG, etc.)
    */
    int DebayerAlgMask;
    /**
    * Enables / disables the automatic selection of sensor mask for debayering algorithm.
    * The mask changes with odd ROI and may change with different port/speed combination.
    */
    bool DebayerAlgMaskAuto;
    /**
    * This must correspond to defines in PvDebayer.h (ALG_REPLICATION, ALG_BILINEAR, etc)
    */
    int DebayerAlgInterpolation;
};

#endif