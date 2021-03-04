///////////////////////////////////////////////////////////////////////////////
// FILE:          Semaphore.h
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

#pragma once

#include <boost/thread/condition_variable.hpp>
#include <boost/thread/mutex.hpp>

#include <cstddef>

class Semaphore/* final*/
{
public:
    explicit Semaphore();
    explicit Semaphore(size_t initCount);

    void Wait(size_t count = 1);
    void Release(size_t count = 1);

private:
    size_t count_;
    boost::mutex mx_;
    boost::condition_variable cv_;
};
