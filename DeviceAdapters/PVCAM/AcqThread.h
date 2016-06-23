#ifndef _ACQTHREAD_H_
#define _ACQTHREAD_H_

#include "../../MMDevice/DeviceThreads.h"

#include "Event.h"

class Universal;

/**
* Acquisition thread used for non-circular buffer acquisition.
* The thread can be started once with Start() and then frequently
* resumed and paused.
* At this point the thread only periodically acquires single frame.
*/
class AcqThread : public MMDeviceThreadBase
{
public:
    /**
    * Creates the thread. The thread needs to be started with Start().
    * @param camera A pointer to the owner class.
    */
    AcqThread(Universal* camera);
    /**
    * Deletes the object, stops the thread if active.
    */
    virtual ~AcqThread();

    /**
    * Allocates and starts the thread. Resume() must be called to actually
    * begin the acquisition.
    */
    void Start();
    /**
    * Stops the thread.
    */
    void Stop();

    /**
    * Pauses the acquisition loop.
    */
    void Pause();
    /**
    * Resumes the acquisition loop.
    */
    void Resume();

    /**
    * Overrided function from MMDeviceThreadBase.
    */
    int svc();

private:
    Universal* camera_;
    bool       requestStop_;
    Event      resumeEvent_;
};

#endif // _ACQTHREAD_H_