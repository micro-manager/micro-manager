#ifndef _PVCIRCULARBUFFER_H_
#define _PVCIRCULARBUFFER_H_

#include "PVCAMIncludes.h"
#include "PvFrameInfo.h"

/**
* A wrapper class over the PVCAM circular buffer. The class keeps track of
* arrived frames (via Reporting functions called from callback) and stores
* frame metadata (FRAME_INFO) for each frame.
* The class is essential for "Frame Recovery" feature as it can be used to
* retrieve the latest known frame.
*/
class PvCircularBuffer
{
public:
    PvCircularBuffer();
    ~PvCircularBuffer();

    /**
    * Returns the maximum number of frames the buffer can hold before it overruns
    * @return Number of frames 
    */
    int Capacity() const;
    /**
    * Returns the size of a single frame in bytes
    * @return Size in bytes
    */
    size_t FrameSize() const;
    /**
    * Returns the base buffer pointer, i.e. the pointer to the 0-th frame.
    * @return Address of the buffer
    */
    void* Data() const;
    /**
    * Returns the total size of the buffer in bytes.
    * @return Size in bytes
    */
    size_t Size() const;

    /**
    * Returns the index of the latest known frame. Since PVCAM uses this
    * buffer to directly write image data the buffer may already contain newer
    * data. However the latest know frame is the one that has been reported
    * by the "ReportFrameArrived" functions.
    * @return Index of the latest known frame
    */
    int LatestFrameIndex() const;
    /**
    * Returns the address of the frame data at given index
    * @return Address of the requested frame
    */
    void* FrameData(int index) const;
    /**
    * Returns the metadata of the frame at given index
    * @return Metadata of the requested frame
    */
    const PvFrameInfo& FrameInfo(int index) const;

    /**
    * Resizes the buffer. This should be called before starting every acquisition.
    * The function will check whether re-allocation is necessary.
    * Calls Reset() automatically.
    */
    void Resize(size_t frameSize, int count);
    /**
    * Resets the buffer to prepare it for new acquisition. This must be called
    * before starting every acquisition. (If Resize() is not called)
    */
    void Reset();

    /**
    * Call this function each time a frame callback notification is received from
    * PVCAM or each time a new frame arrival is detected with polling.
    * This function notifies the buffer that a new frame has arrived and assigns
    * the frame metadata to correct location.
    */
    void ReportFrameArrived(const PvFrameInfo& frameNfo, void* pFrameData);

private:
    unsigned char* pBuffer_;
    size_t         size_;
    size_t         frameSize_;
    int            frameCount_;

    int            latestFrameIdx_;

    PvFrameInfo*   pFrameInfoArray_;
};


#endif // _PVCIRCULARBUFFER_H_
