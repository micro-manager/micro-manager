#ifndef _SPURIOUSNOISEFILTERCONTROL_H_
#define _SPURIOUSNOISEFILTERCONTROL_H_

#include "../../MMDevice/Property.h"

class AndorCamera;

class SpuriousNoiseFilterControl
{
public:
  SpuriousNoiseFilterControl(AndorCamera * cam);

  int OnSpuriousNoiseFilter(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnSpuriousNoiseFilterThreshold(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnSpuriousNoiseFilterDescription(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   typedef MM::Action<SpuriousNoiseFilterControl> CPropertyAction;
   enum MODE { NONE=0, MEDIAN, LEVEL, IQRANGE };
   MODE currentMode_;

   void CreateProperties();

   AndorCamera * camera_;

   typedef std::map<MODE,const char*> MODEMAP;
   MODEMAP modes_;
   MODEMAP modeDescriptions_;
   void InitialiseMaps();
   MODE GetMode(const char * mode);

   double spuriousNoiseFilterThreshold_;
};

#endif