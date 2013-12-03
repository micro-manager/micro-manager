This code is a candidate to replace the current FastLogger class in the Core.

It is based on Boost.Log (available in Boost >= 1.54; tested with 1.55.0 on
Windows).

Some trivial patching of the Core is needed to use this code (it is currently
functional, except for the unimplemented functionality noted below).

Need to see how feasible it is to compile (and compile against) Boost.Log on
other platforms, esp. with old-ish compilers - the library uses template
metaprogramming and can be demanding of the compiler.

Difference from FastLogger:
- Multiple Logger objects can act as entry points. Each component (Core,
  application, each device) can have its own logger, which will automatically
  attach a label to the log entry.
- Will actually work with multiple levels (not just debug and normal): trace,
  debug, info, warning, error, and fatal. We could imagine channeling the
  warning and higher severity entries back to the application.
- More maintainable code due to use of library. Auto-flushing, asynchronous
  logging, etc., can be easily configured. New sinks can be added.
- New file per process, named with time and pid, saved in dedicated directory
  (not hard to backport to existing logger).

Not implemented yet:
- Clearing log and getting contents of log file (as a transitional measure).
- Proposed socket interface (see Logging.h).
- Deletion of old logs (see Logging.h).

Other library candidates:
- glog - Macros don't execute message-computing statements when logging turned
  off; In general not flexible enough for our kind of application
- log4c - Among other downsides, requires GCC extensions
- log4cpp - No builtin asynchronous (queued) logging (we need this if sending
  log to stderr (terminal) or sockets). Thread safety not clearly documented.
- log4cxx - Among other downsides, depends on the APR
So Boost.Log is the only major C++ logging library that we can really use.

Mark Tsuchida, Dec 2, 2013.
