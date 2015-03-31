#include "NotificationEntry.h"

NotificationEntry::NotificationEntry() :
    pFrameData_(0)
{
}

NotificationEntry::NotificationEntry(const void* pData, const PvFrameInfo& metadata) :
    pFrameData_(pData),
    frameMetaData_(metadata)
{
}

const PvFrameInfo& NotificationEntry::FrameMetadata() const
{
    return frameMetaData_;
}

const void* NotificationEntry::FrameData() const
{
    return pFrameData_;
}