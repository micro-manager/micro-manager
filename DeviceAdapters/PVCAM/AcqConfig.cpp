#include "AcqConfig.h"

/**
* Values set in constructor represents the default camera state. Each value can be
* overridden during Initialize().
*/
AcqConfig::AcqConfig() :
    ExposureMs(10),
    ExposureRes(EXP_RES_ONE_MICROSEC),
    AcquisitionType(AcqType_Snap),
    FrameMetadataEnabled(false),
    CentroidsEnabled(false),
    CentroidsRadius(0),
    CentroidsCount(0),
    RoiCount(1),
    FanSpeedSetpoint(0),
    Rois(),
    ClearCycles(2),
    ClearMode(CLEAR_PRE_EXPOSURE),
    ColorProcessingEnabled(false),
    DebayerAlgMask(0),
    DebayerAlgMaskAuto(false),
    DebayerAlgInterpolation(0),
    TrigTabLastMuxMap(),
    PMode(0),
    AdcOffset(0),
    ScanMode(PL_SCAN_MODE_AUTO),
    ScanDirection(PL_SCAN_DIRECTION_DOWN),
    ScanDirectionReset(true),
    ScanLineDelay(0),
    ScanWidth(0),
    PortId(0),
    SpeedIndex(0),
    GainNum(0),
    CircBufEnabled(true),
    CircBufSizeAuto(true),
    CallbacksEnabled(true),
    DiskStreamingEnabled(false),
    DiskStreamingPath(""),
    DiskStreamingCoreSkipRatio(100),
    SmartStreamingEnabled(false),
    SmartStreamingActive(false),
    SmartStreamingExposures()
{
    // S.M.A.R.T streaming exposures are in milliseconds, floating point.
    SmartStreamingExposures.push_back(10);
    SmartStreamingExposures.push_back(20);
    SmartStreamingExposures.push_back(30);
    SmartStreamingExposures.push_back(40);
}