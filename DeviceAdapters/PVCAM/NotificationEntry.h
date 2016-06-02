#ifndef _NOTIFICATIONENTRY_H_
#define _NOTIFICATIONENTRY_H_

#include "PvFrameInfo.h"

/**
* A class that contains a ponter to frame data and corresponding
* frame metadata. This class is used by the NotificationThread.
*/
class NotificationEntry
{
public:

    NotificationEntry();
    NotificationEntry(const void* pData, unsigned int dataSz, const PvFrameInfo& metadata);

    /**
    * Returns the frame metadata
    * @return Frame metadata
    */
    const PvFrameInfo& FrameMetadata() const;
    /**
    * Return the pointer to the frame data.
    * @return address of the frame data
    */
    const void* FrameData() const;

    /**
    * Returns the size of the frame data in bytes
    * @return Frame data size in bytes
    */
    unsigned int FrameDataSize() const;

private:

    // Copy of the frame metadata
    PvFrameInfo frameMetaData_;

    const void*  pFrameData_;  ///< Pointer to the frame in circular buffer
    unsigned int frameDataSz_; ///< Size of the data in bytes
};

#endif // _NOTIFICATIONENTRY_H_