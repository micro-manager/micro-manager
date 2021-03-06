///////////////////////////////////////////////////////////////////////////////
// FILE:          TaskSet_CopyMemory.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     MMCore
//-----------------------------------------------------------------------------
// DESCRIPTION:   Task set for parallelized memory copy.
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

#include "TaskSet.h"

class TaskSet_CopyMemory : public TaskSet
{
private:
    class ATask : public Task
    {
    public:
        explicit ATask(boost::shared_ptr<Semaphore> semDone, size_t taskIndex, size_t totalTaskCount);

        void SetUp(void* dst, const void* src, size_t bytes, size_t usedTaskCount);

        virtual void Execute()/* override*/;

    private:
        void* dst_;
        const void* src_;
        size_t bytes_;
    };

public:
    explicit TaskSet_CopyMemory(boost::shared_ptr<ThreadPool> pool);

    void SetUp(void* dst, const void* src, size_t bytes);

    virtual void Execute()/* override*/;
    virtual void Wait()/* override*/;

    // Helper blocking method calling SetUp, Execute and Wait
    void MemCopy(void* dst, const void* src, size_t bytes);
};
