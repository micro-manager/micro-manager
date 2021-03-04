///////////////////////////////////////////////////////////////////////////////
// FILE:          Task.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     MMCore
//-----------------------------------------------------------------------------
// DESCRIPTION:   Base class for parallel processing via ThreadPool.
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

#include <boost/smart_ptr/shared_ptr.hpp>

#include <cstddef>

class Semaphore;

class Task
{
public:
    explicit Task(boost::shared_ptr<Semaphore> semaphore, size_t taskIndex, size_t totalTaskCount);
    virtual ~Task();

    Task(const Task&)/* = delete*/;
    Task& operator=(const Task&)/* = delete*/;

    virtual void Execute() = 0;
    void Done();

private:
    const boost::shared_ptr<Semaphore> semaphore_;

protected:
    const size_t taskIndex_;
    const size_t totalTaskCount_;
    size_t usedTaskCount_;
};
