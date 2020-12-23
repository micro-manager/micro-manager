/*
 * Provide snprintf() for MSVC prior to VS2015
 * This file is part of MMDevice
 *
 * Author: Mark Tsuchida
 *
 * (C)2020 Board of Regents of the University of Wisconsin System
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 * 3. Neither the name of the copyright holder nor the names of its
 * contributors may be used to endorse or promote products derived from this
 * software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

#pragma once

// VC++ added a proper snprintf() in VS2015. Prior to that, both _snprintf()
// and _snprintf_s() were incompatible with the C standard snprintf().
// This file serves as a single location where this issue is dealt with.
//
// This file can be included before or after stdio.h, but should be included
// outside of any conditional compilation block.

#if defined(_MSC_VER) && _MSC_VER < 1900 // VS2015

// Unlike snprintf(), _snprintf() does not null-terminate the result if it is
// longer than the buffer. However, Micro-Manager code has customarily defined
// snprintf to be _snprintf. Since we will be moving to a newer compiler in the
// near future (at which point this file can be removed), I'm just keeping that
// method rather than trying to emulate correct behavior (it's trickier than it
// initially appears).

#define snprintf _snprintf

#endif
