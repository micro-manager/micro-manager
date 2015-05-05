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
    NotificationEntry(const void* pData, const PvFrameInfo& metadata);

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

private:

    // Copy of the frame metadata
    PvFrameInfo frameMetaData_;

    // Pointer to the frame in circular buffer
    const void* pFrameData_;
};

#endif // _NOTIFICATIONENTRY_H_