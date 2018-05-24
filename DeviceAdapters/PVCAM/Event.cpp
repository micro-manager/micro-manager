#include "Event.h"

Event::Event() : manualReset_(false), signalled_(false)
{
}

Event::Event(bool manualReset, bool signalled) : manualReset_(manualReset), signalled_(signalled)
{
}

Event::~Event()
{
}

void Event::Set()
{
    mutex_.lock();
    signalled_ = true;
    mutex_.unlock();
    condVar_.notify_all();
}

void Event::Reset()
{
    mutex_.lock();
    signalled_ = false;
    mutex_.unlock();
}

bool Event::Wait()
{
    //  unique_lock(m): Stores a reference to m. Invokes m.lock().
    // ~unique_lock(m): Invokes mutex()-> unlock() if owns_lock() returns true.
    boost::unique_lock<boost::mutex> lock(mutex_);
    // consition_variable::wait():
    // Atomically call lock.unlock() and blocks the current thread. The thread
    // will unblock when notified by a call to this->notify_one() or
    // this->notify_all(), or spuriously. When the thread is unblocked (for
    // whatever reason), the lock is reacquired by invoking lock.lock() before
    // the call to wait returns. The lock is also reacquired by invoking
    // lock.lock() if the function exits with an exception.
    while (!signalled_)
        condVar_.wait(lock);

    if (!manualReset_)
        signalled_ = false;

    return true;
}

bool Event::Wait(unsigned int timeoutMs)
{
    boost::unique_lock<boost::mutex> lock(mutex_);

    bool bInTime = true;
    // condition_variable::timed_wait(): returns false if the call is returning
    // because the time specified was reached, true otherwise.
    while (!signalled_ && bInTime)
        bInTime = condVar_.timed_wait(lock, boost::posix_time::milliseconds(timeoutMs));

    if (!manualReset_ && bInTime)
        signalled_ = false;

    return bInTime;
}
