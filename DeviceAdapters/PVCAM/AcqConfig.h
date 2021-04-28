#ifndef _ACQCONFIG_H_
#define _ACQCONFIG_H_

#include "PvRoiCollection.h"

#include <map>
#include <string>
#include <vector>

enum AcqType
{
    AcqType_Snap = 0,
    AcqType_Live
};

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
* these are still used in ongoing acquisition. Instead, we need to remember the
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
    * Micro-Manager exposure time in milli-seconds. This is later converted
    * to PVCAM exposure time based on current exposure resolution.
    */
    double ExposureMs;
    /**
    * Current PVCAM exposure resolution - EXP_RES_ONE_MILLISEC, MICROSEC, etc.
    */
    int    ExposureRes;

    /**
    * Type of acquisition the camera should be prepared for
    */
    AcqType AcquisitionType;

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
    * Total number of "output" ROIs for the current acquisition. This could be either
    * the Centroids Count or number of user defined ROIs (1 or more if supported)
    */
    int RoiCount;
    /**
    * Selected fan speed.
    */
    int FanSpeedSetpoint;
    /**
    * Regions of interest. Array of input ROIs that will be sent to the camera.
    */
    PvRoiCollection Rois;
    /**
    * Number of sensor clearing cycles.
    */
    int ClearCycles;
    /**
    * Selected clearing mode. PARAM_CLEAR_MODE values.
    */
    int ClearMode;
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
    /**
    * A map of trigger signals and their muxing settings.
    *  key = PARAM_TRIGTAB_SIGNAL value
    *  val = PARAM_LAST_MUXED_SIGNAL value
    * Example:
    *  ExposeOutSignal: 4
    *  ReadoutSignal: 2
    */
    std::map<int, int> TrigTabLastMuxMap;
    /**
    * Current PMode value
    */
    int PMode;
    /**
    * Current ADC offset
    */
    int AdcOffset;
    /**
    * Scan Mode
    */
    int ScanMode;
    /**
    * Scan Direction
    */
    int ScanDirection;
    /**
    * Scan Direction Reset State (ON/OFF)
    */
    bool ScanDirectionReset;
    /**
    * Scan Line Delay
    */
    int ScanLineDelay;
    /**
    * Scan Line Width
    */
    int ScanWidth;
    /**
    * Current port ID.
    */
    int PortId;
    /**
    * Current speed index.
    */
    int SpeedIndex;
    /**
    * Current gain number.
    */
    int GainNum;
    /**
    * Whether to use circular buffer for live acquisition or not
    */
    bool CircBufEnabled;
    /**
    * Whether to use adjust the circular size automatically based on acquisition configuration
    */
    bool CircBufSizeAuto;
    /**
    * True if PVCAM callbacks are active, false to use polling
    */
    bool CallbacksEnabled;
    /**
    * Enables or disables custom streaming to disk.
    * Please note that this streaming is enabled for continuous acquisition only.
    * The streaming to disk is fully controlled by PVCAM adapter and should only
    * be used in cases where standard MDA is unable to keep up with camera speed
    * when storing the data, esp. at high data rates, greater than 2GB/s.
    */
    bool DiskStreamingEnabled;
    /**
    * The path where files with raw data will be stored.
    */
    std::string DiskStreamingPath;
    /**
    * Ratio of images forwarded to the core.
    * The value 1 means all frames are sent, value 2 means every second frame is sent, etc.
    * Values higher than 1 result in frame rate counter showing a lower value
    * than the actual acquisition rate.
    */
    int DiskStreamingCoreSkipRatio;
    /**
    * Enables or disables the S.M.A.R.T streaming. Please note that the S.M.A.R.T streaming
    * mode is enabled for continuous acquisition only. See the "Active" variable.
    */
    bool SmartStreamingEnabled;
    /**
    * Controls whether the S.M.A.R.T streaming is actually active or not. In Single snap mode
    * we always temporarily disable the S.M.A.R.T streaming and use the exposure value from
    * the main GUI. (S.M.A.R.T streaming does not have any effect in single snaps)
    */
    bool SmartStreamingActive;
    /**
    * Exposure values for S.M.A.R.T streaming
    */
    std::vector<double> SmartStreamingExposures;
};

#endif