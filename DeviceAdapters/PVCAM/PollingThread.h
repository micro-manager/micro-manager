#ifndef _POLLINGTHREAD_H_
#define _POLLINGTHREAD_H_

#include "../../MMDevice/DeviceThreads.h"

class Universal;

/**
* Acquisition thread used for polling acquisition only.
*/
class PollingThread : public MMDeviceThreadBase
{
public:
    PollingThread(Universal* camera) :
      stop_(true), camera_(camera) {}
      virtual ~PollingThread() {}
      int svc (void);

      void setStop(bool stop) {stop_ = stop;}
      bool getStop() {return stop_;}
      void Start() {stop_ = false; activate();}

private:
    bool stop_;
    Universal* camera_;
};

#endif // _POLLINGTHREAD_H_