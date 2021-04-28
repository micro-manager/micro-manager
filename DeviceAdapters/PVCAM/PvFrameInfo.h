#ifndef _PVFRAMEINFO_H_
#define _PVFRAMEINFO_H_

#include "PVCAMIncludes.h"

/**
* A local definition for the FRAME_INFO. Used to copy the
* essential FRAME_INFO metadata together with additional 
* frame metadata.
*/
class PvFrameInfo
{
public:
    PvFrameInfo();
    ~PvFrameInfo();

    /// FRAME_INFO related metadata
    void SetPvHCam(short int);
    short int PvHCam() const;
    void SetPvFrameNr(int);
    int PvFrameNr() const;
    void SetPvTimeStamp(long long);
    long long PvTimeStamp() const;
    void SetPvReadoutTime(int);
    int PvReadoutTime() const;
    void SetPvTimeStampBOF(long long);
    long long PvTimeStampBOF() const;
    ///

    /// Other metadata added by the adapter
    void SetTimestampMsec(double msec);
    double TimeStampMsec() const;

    void SetRecovered(bool recovered);
    bool IsRecovered() const;

private:

    // FRAME_INFO Metadata
    short int pvHCam_;         // int16  FRAME_INFO.hCam
    int       pvFrameNr_;      // int32  FRAME_INFO.FrameNr
    long long pvTimeStamp_;    // long64 FRAME_INFO.TImeStamp (EOF)
    int       pvReadoutTime_;  // int32  FRAME_INFO.ReadoutTime
    long long pvTimeStampBOF_; // long64 FRAME_INFO.TimeStampBOF

    // Additional Metadata
    double    timestampMsec_; // MM Timestamp
    bool      isRecovered_;   // Recovered from missed callback
};

#endif // _PVFRAMEINFO_H_