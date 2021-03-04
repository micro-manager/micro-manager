///////////////////////////////////////////////////////////////////////////////
// FILE:          Semaphore.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     MMCore
//-----------------------------------------------------------------------------
// DESCRIPTION:   Synchronization primitive with counter.
//
// AUTHOR:        Tomas Hanak, tomas.hanak@teledyne.com, 03/03/2021
//                Andrej Bencur, andrej.bencur@teledyne.com, 03/03/2021
//
// COPYRIGHT:     Teledyne Digital Imaging US, Inc., 2021
//
// LICENSE:       This file is distributed under the "Lesser GPL" (LGPL) license.
//                License text is included with the source distribution.
//
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.

#include "Semaphore.h"

#include <boost/thread/locks.hpp>

Semaphore::Semaphore()
    : count_(0)
{
}

Semaphore::Semaphore(size_t initCount)
    : count_(initCount)
{
}

void Semaphore::Wait(size_t count)
{
    boost::unique_lock<boost::mutex> lock(mx_);
    cv_.wait(lock, [&]() { return count_ >= count; });
    count_ -= count;
}

void Semaphore::Release(size_t count)
{
    {
        boost::lock_guard<boost::mutex> lock(mx_);
        count_ += count;
    }
    cv_.notify_all();
}
