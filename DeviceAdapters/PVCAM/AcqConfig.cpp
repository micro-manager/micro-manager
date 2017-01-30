#include "AcqConfig.h"

/**
* Values set in constructor reperesents the default camera state. Each value can be
* overriden during Initialize().
*/
AcqConfig::AcqConfig() :
    ExposureMs(10), ExposureRes(EXP_RES_ONE_MICROSEC),
    AcquisitionType(AcqType_Snap),
    FrameMetadataEnabled(false),
    CentroidsEnabled(false), CentroidsRadius(0), CentroidsCount(0),
    RoiCount(1), FanSpeedSetpoint(0),
    ColorProcessingEnabled(false),
    DebayerAlgMask(0), DebayerAlgMaskAuto(false), DebayerAlgInterpolation(0),
    PMode(0), AdcOffset(0), PortId(0), SpeedIndex(0), CircBufEnabled(true), CircBufSizeAuto(true), CallbacksEnabled(true),
    SmartStreamingEnabled(false), SmartStreamingActive(false), SmartStreamingExposures()
{
    // S.M.A.R.T streaming exposures are in msec, floating point.
    SmartStreamingExposures.push_back(10);
    SmartStreamingExposures.push_back(20);
    SmartStreamingExposures.push_back(30);
    SmartStreamingExposures.push_back(40);
}