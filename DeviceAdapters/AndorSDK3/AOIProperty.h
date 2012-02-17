#ifndef _AOIPROPERTY_H_
#define _AOIPROPERTY_H_

#include <map>
#include "andorvartypes.h"
#include "MMDeviceConstants.h"
#include "Property.h"

namespace andor
{
   class IDevice;
   class ISubject;
   class IInteger;
}

class MySequenceThread;
class CAndorSDK3Camera;
class SnapShotControl;

class TAOIProperty
{
public:
   TAOIProperty(const std::string MM_name, CAndorSDK3Camera* camera,
      andor::IDevice* device_hndl, MySequenceThread* thd,
      SnapShotControl* snapShotController, bool readOnly);
   ~TAOIProperty();

   void Update(andor::ISubject* Subject);
   int OnAOI(MM::PropertyBase* pProp, MM::ActionType eAct);
   typedef MM::Action<TAOIProperty> CPropertyAction;

   unsigned GetWidth();
   unsigned GetHeight();
   unsigned GetBytesPerPixel();
   unsigned GetStride();
   double GetBytesPerPixelF();
   void SetReadOnly(bool set_to);
   void SetCustomAOISize(unsigned left, unsigned top, unsigned width, unsigned height);
   void ResetToFullImage();

private:
   typedef std::map<andor64, int> TMapAOIIndexType;
   typedef std::map<andor64, andor64> TMapAOIWidthHeightType;
   andor::IInteger* aoi_height_;
   andor::IInteger* aoi_width_;
   andor::IInteger* aoi_top_;
   andor::IInteger* aoi_left_;
   andor::IInteger* aoi_stride_;
   CAndorSDK3Camera* camera_;
   andor::IDevice* device_hndl_;
   std::string MM_name_;
   MySequenceThread * thd_;
   SnapShotControl* snapShotController_;
   MM::Property* pbProp_;

   TMapAOIIndexType aoiWidthIndexMap_;
   TMapAOIWidthHeightType aoiWidthHeightMap_;
   bool fullAoiControl_;
   andor64 leftOffset_;
   andor64 topOffset_;
};

#endif // _AOIPROPERTY_H_