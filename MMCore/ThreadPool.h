///////////////////////////////////////////////////////////////////////////////
// FILE:          ThreadPool.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     MMCore
//-----------------------------------------------------------------------------
// DESCRIPTION:   A class executing queued tasks on separate threads
//                and scaling number of threads based on hardware.
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
#include <boost/thread.hpp>

#include <deque>
#include <vector>

class Task;

class ThreadPool/* final*/
{
public:
    explicit ThreadPool();
    ~ThreadPool();

    size_t GetSize() const;

    void Execute(Task* task);
    void Execute(const std::vector<Task*>& tasks);

private:
    void ThreadFunc();

private:
    // TODO: Should use boost::unique_ptr but that's available since boost 1.57
    std::vector<boost::shared_ptr<boost::thread> > threads_;
    bool abortFlag_;
    boost::mutex mx_;
    boost::condition_variable cv_;
    std::deque<Task*> queue_;
};
