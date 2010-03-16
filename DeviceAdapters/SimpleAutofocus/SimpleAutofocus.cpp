///////////////////////////////////////////////////////////////////////////////
// FILE:          SimpleAutofocus.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   SimpleAutofocus controller adapter
// COPYRIGHT:     University of California, San Francisco, 2009
//
// AUTHOR:        Karl Hoover, UCSF
//
// LICENSE:       This file is distributed under the BSD license.
//                License text is included with the source distribution.
//
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//
// CVS:           
//



#ifdef WIN32
   #include <windows.h>
   #define snprintf _snprintf 
#endif


#include "SimpleAutofocus.h"
#include <string>
#include <math.h>
#include <sstream>
#include <algorithm>

#include "../../MMDevice/ModuleInterface.h"
#include "../../MMCore/Error.h"
#include "boost/lexical_cast.hpp"
#include "boost/tuple/tuple.hpp"
#include <set>







#include <stdio.h>
#include <math.h>
#include <stdlib.h>




// property names:
// Controller
const char* g_ControllerName = "SimpleAutofocus";

const bool messageDebug = false;

class SAFPoint
{
public:
   SAFPoint( int seqNo, float z, float meanValue, float stdOverMeanScore, double hiPassScore):thePoint_( seqNo, z, meanValue,  stdOverMeanScore, hiPassScore )
   {
   }

   SAFPoint(  boost::tuple<int,float,float,float,double> apoint) : thePoint_(apoint){}

   bool operator<(const SAFPoint& that) const
   {
      // sort by Z position
      return (boost::tuples::get<1>(thePoint_) < boost::tuples::get<1>(that.thePoint_));
   }

    const std::string DataRow() const
    {
       std::ostringstream data;
       data << std::setw(3) << boost::lexical_cast<std::string, int>( boost::tuples::get<0>(thePoint_) )<< "\t"
          << std::setprecision(5) << boost::tuples::get<1>(thePoint_) << "\t" // Z
          << std::setprecision(5) <<  boost::tuples::get<2>(thePoint_) << "\t" // mean
          << std::setprecision(6) << std::setiosflags(std::ios::scientific) << boost::tuples::get<3>(thePoint_) <<  std::resetiosflags(std::ios::scientific) << "\t"  // std / mean
          << std::setprecision(6) <<  std::setiosflags(std::ios::scientific) << boost::tuples::get<4>(thePoint_) << std::resetiosflags(std::ios::scientific) ;
       return data.str();
    }

 private:
   boost::tuple<int,float,float,float,double> thePoint_;

};

class SAFData
{
   std::set< SAFPoint > points_;

public:
   void InsertPoint( int seqNo, float z, float meanValue,float stdOverMeanScore,  double hiPassScore)
   {
      // SAFPoint value(int seqNo, float z, float meanValue,  float stdOverMeanScore, double hiPassScore);
      // VS 2008 thinks above line is a function decl !!!
      boost::tuple<int,float,float,float,double> vals(seqNo, z, meanValue, stdOverMeanScore,  hiPassScore );
      SAFPoint value(vals);
      points_.insert(value);
   }

   // default dtor is ok

   void Clear()
   {
      points_.clear();
   }
   const std::string Table()
   {
      std::ostringstream data;
      data << "Acq#\t Z\t Mean\t  Std / Mean\t Hi Pass Score";
      std::set< SAFPoint >::iterator ii;
      for( ii = points_.begin(); ii!=points_.end(); ++ii)
      {
         data << "\n";
         data << ii->DataRow();
      }
      return data.str();
   }
};




//todo : maybe nicer to move this to a library

class Brent
{
public:

double local_min_rc ( double *a, double *b, int *status, double value )

//****************************************************************************80
//
//  Purpose:
//
//    LOCAL_MIN_RC seeks a minimizer of a scalar function of a scalar variable.
//
//  Discussion:
//
//    This routine seeks an approximation to the point where a function
//    F attains a minimum on the interval (A,B).
//
//    The method used is a combination of golden section search and
//    successive parabolic interpolation.  Convergence is never much
//    slower than that for a Fibonacci search.  If F has a continuous
//    second derivative which is positive at the minimum (which is not
//    at A or B), then convergence is superlinear, and usually of the
//    order of about 1.324...
//
//    The routine is a revised version of the Brent local minimization 
//    algorithm, using reverse communication.
//
//    It is worth stating explicitly that this routine will NOT be
//    able to detect a minimizer that occurs at either initial endpoint
//    A or B.  If this is a concern to the user, then the user must
//    either ensure that the initial interval is larger, or to check
//    the function value at the returned minimizer against the values
//    at either endpoint.
//
//  Licensing:
//
//    This code is distributed under the GNU LGPL license. 
//
//  Modified:
//
//    16 April 2008
//
//  Author:
//
//    John Burkardt
//
//  Reference:
//
//    Richard Brent,
//    Algorithms for Minimization Without Derivatives,
//    Dover, 2002,
//    ISBN: 0-486-41998-3,
//    LC: QA402.5.B74.
//
//    David Kahaner, Cleve Moler, Steven Nash,
//    Numerical Methods and Software,
//    Prentice Hall, 1989,
//    ISBN: 0-13-627258-4,
//    LC: TA345.K34.
//
//  Parameters
//
//    Input/output, double *A, *B.  On input, the left and right
//    endpoints of the initial interval.  On output, the lower and upper
//    bounds for an interval containing the minimizer.  It is required
//    that A < B.
//
//    Input/output, int *STATUS, used to communicate between 
//    the user and the routine.  The user only sets STATUS to zero on the first 
//    call, to indicate that this is a startup call.  The routine returns STATUS
//    positive to request that the function be evaluated at ARG, or returns
//    STATUS as 0, to indicate that the iteration is complete and that
//    ARG is the estimated minimizer.
//
//    Input, double VALUE, the function value at ARG, as requested
//    by the routine on the previous call.
//
//    Output, double LOCAL_MIN_RC, the currently considered point.  
//    On return with STATUS positive, the user is requested to evaluate the 
//    function at this point, and return the value in VALUE.  On return with
//    STATUS zero, this is the routine's estimate for the function minimizer.
//
//  Local parameters:
//
//    C is the squared inverse of the golden ratio.
//
//    EPS is the square root of the relative machine precision.
//
{
  static double arg;
  static double c;
  static double d;
  static double e;
  static double eps;
  static double fu;
  static double fv;
  static double fw;
  static double fx;
  static double midpoint;
  static double p;
  static double q;
  static double r;
  static double tol;
  static double tol1;
  static double tol2;
  static double u;
  static double v;
  static double w;
  static double x;
//
//  STATUS (INPUT) = 0, startup.
//
  if ( *status == 0 )
  {
    if ( *b <= *a )
    {
      //cout << "\n";
      //cout << "LOCAL_MIN_RC - Fatal error!\n";
      //cout << "  A < B is required, but\n";
      //cout << "  A = " << *a << "\n";
      //cout << "  B = " << *b << "\n";
      *status = -1;
      return 0.;
    }
    c = 0.5 * ( 3.0 - sqrt ( 5.0 ) );

    eps = sqrt ( r8_epsilon ( ) );
    tol = r8_epsilon ( );

    v = *a + c * ( *b - *a );
    w = v;
    x = v;
    e = 0.0;

    *status = 1;
    arg = x;

    return arg;
  }
//
//  STATUS (INPUT) = 1, return with initial function value of FX.
//
  else if ( *status == 1 )
  {
    fx = value;
    fv = fx;
    fw = fx;
  }
//
//  STATUS (INPUT) = 2 or more, update the data.
//
  else if ( 2 <= *status )
  {
    fu = value;

    if ( fu <= fx )
    {
      if ( x <= u )
      {
        *a = x;
      }
      else
      {
        *b = x;
      }
      v = w;
      fv = fw;
      w = x;
      fw = fx;
      x = u;
      fx = fu;
    }
    else
    {
      if ( u < x )
      {
        *a = u;
      }
      else
      {
        *b = u;
      }

      if ( fu <= fw || w == x )
      {
        v = w;
        fv = fw;
        w = u;
        fw = fu;
      }
      else if ( fu <= fv || v == x || v == w )
      {
        v = u;
        fv = fu;
      }
    }
  }
//
//  Take the next step.
//
  midpoint = 0.5 * ( *a + *b );
  tol1 = eps * r8_abs ( x ) + tol / 3.0;
  tol2 = 2.0 * tol1;
//
//  If the stopping criterion is satisfied, we can exit.
//
  if ( r8_abs ( x - midpoint ) <= ( tol2 - 0.5 * ( *b - *a ) ) )
  {
    *status = 0;
    return arg;
  }
//
//  Is golden-section necessary?
//
  if ( r8_abs ( e ) <= tol1 )
  {
    if ( midpoint <= x )
    {
      e = *a - x;
    }
    else
    {
      e = *b - x;
    }
    d = c * e;
  }
//
//  Consider fitting a parabola.
//
  else
  {
    r = ( x - w ) * ( fx - fv );
    q = ( x - v ) * ( fx - fw );
    p = ( x - v ) * q - ( x - w ) * r;
    q = 2.0 * ( q - r );
    if ( 0.0 < q )
    {
      p = - p;
    }
    q = r8_abs ( q );
    r = e;
    e = d;
//
//  Choose a golden-section step if the parabola is not advised.
//
    if ( 
      ( r8_abs ( 0.5 * q * r ) <= r8_abs ( p ) ) ||
      ( p <= q * ( *a - x ) ) ||
      ( q * ( *b - x ) <= p ) ) 
    {
      if ( midpoint <= x )
      {
        e = *a - x;
      }
      else
      {
        e = *b - x;
      }
      d = c * e;
    }
//
//  Choose a parabolic interpolation step.
//
    else
    {
      d = p / q;
      u = x + d;

      if ( ( u - *a ) < tol2 )
      {
        d = tol1 * r8_sign ( midpoint - x );
      }

      if ( ( *b - u ) < tol2 )
      {
        d = tol1 * r8_sign ( midpoint - x );
      }
    }
  }
//
//  F must not be evaluated too close to X.
//
  if ( tol1 <= r8_abs ( d ) ) 
  {
    u = x + d;
  }
  if ( r8_abs ( d ) < tol1 )
  {
    u = x + tol1 * r8_sign ( d );
  }
//
//  Request value of F(U).
//
  arg = u;
  *status = *status + 1;

  return arg;
};
//****************************************************************************80

double r8_abs ( double x )

//****************************************************************************80
//
//  Purpose:
//
//    R8_ABS returns the absolute value of an R8.
//
//  Licensing:
//
//    This code is distributed under the GNU LGPL license. 
//
//  Modified:
//
//    07 May 2006
//
//  Author:
//
//    John Burkardt
//
//  Parameters:
//
//    Input, double X, the quantity whose absolute value is desired.
//
//    Output, double R8_ABS, the absolute value of X.
//
{
  double value;

  if ( 0.0 <= x )
  {
    value = x;
  } 
  else
  {
    value = - x;
  }
  return value;
};
//****************************************************************************80

double r8_epsilon ( void )

//****************************************************************************80
//
//  Purpose:
//
//    R8_EPSILON returns the R8 round off unit.
//
//  Discussion:
//
//    R8_EPSILON is a number R which is a power of 2 with the property that,
//    to the precision of the computer's arithmetic,
//      1 < 1 + R
//    but 
//      1 = ( 1 + R / 2 )
//
//  Licensing:
//
//    This code is distributed under the GNU LGPL license. 
//
//  Modified:
//
//    08 May 2006
//
//  Author:
//
//    John Burkardt
//
//  Parameters:
//
//    Output, double R8_EPSILON, the double precision round-off unit.
//
{
  double r;

  r = 1.0;

  while ( 1.0 < ( double ) ( 1.0 + r )  )
  {
    r = r / 2.0;
  }

  return ( 2.0 * r );
};
//****************************************************************************80

double r8_max ( double x, double y )

//****************************************************************************80
//
//  Purpose:
//
//    R8_MAX returns the maximum of two R8's.
//
//  Licensing:
//
//    This code is distributed under the GNU LGPL license. 
//
//  Modified:
//
//    18 August 2004
//
//  Author:
//
//    John Burkardt
//
//  Parameters:
//
//    Input, double X, Y, the quantities to compare.
//
//    Output, double R8_MAX, the maximum of X and Y.
//
{
  double value;

  if ( y < x )
  {
    value = x;
  } 
  else
  {
    value = y;
  }
  return value;
};
//****************************************************************************80

double r8_sign ( double x )

//****************************************************************************80
//
//  Purpose:
//
//    R8_SIGN returns the sign of an R8.
//
//  Licensing:
//
//    This code is distributed under the GNU LGPL license. 
//
//  Modified:
//
//    18 October 2004
//
//  Author:
//
//    John Burkardt
//
//  Parameters:
//
//    Input, double X, the number whose sign is desired.
//
//    Output, double R8_SIGN, the sign of X.
//
{
  double value;

  if ( x < 0.0 )
  {
    value = -1.0;
  } 
  else
  {
    value = 1.0;
  }
  return value;
};
//****************************************************************************80

//void timestamp ( void )
//
////****************************************************************************80
////
////  Purpose:
////
////    TIMESTAMP prints the current YMDHMS date as a time stamp.
////
////  Example:
////
////    31 May 2001 09:45:54 AM
////
////  Licensing:
////
////    This code is distributed under the GNU LGPL license. 
////
////  Modified:
////
////    24 September 2003
////
////  Author:
////
////    John Burkardt
////
////  Parameters:
////
////    None
////
//{
//#define TIME_SIZE 40
//
//  static char time_buffer[TIME_SIZE];
//  const struct tm *tm;
//  size_t len;
//  time_t now;
//
//  now = time ( NULL );
//  tm = localtime ( &now );
//
//  len = strftime ( time_buffer, TIME_SIZE, "%d %B %Y %I:%M:%S %p", tm );
//
//  cout << time_buffer << "\n";
//
//  return;
//#undef TIME_SIZE
//}
//****************************************************************************80

double zero ( double a, double b, double machep, double t, 
  double f ( double x ) )

//****************************************************************************80
//
//
//  Purpose:
//
//    ZERO seeks the root of a function F(X) in an interval [A,B].
//
//  Discussion:
//
//    The interval [A,B] must be a change of sign interval for F.
//    That is, F(A) and F(B) must be of opposite signs.  Then
//    assuming that F is continuous implies the existence of at least
//    one value C between A and B for which F(C) = 0.
//
//    The location of the zero is determined to within an accuracy
//    of 6 * MACHEPS * r8_abs ( C ) + 2 * T.
//
//  Licensing:
//
//    This code is distributed under the GNU LGPL license. 
//
//  Modified:
//
//    13 April 2008
//
//  Author:
//
//    Original FORTRAN77 version by Richard Brent.
//    C++ version by John Burkardt.
//
//  Reference:
//
//    Richard Brent,
//    Algorithms for Minimization Without Derivatives,
//    Dover, 2002,
//    ISBN: 0-486-41998-3,
//    LC: QA402.5.B74.
//
//  Parameters:
//
//    Input, double A, B, the endpoints of the change of sign interval.
//
//    Input, double MACHEP, an estimate for the relative machine
//    precision.
//
//    Input, double T, a positive error tolerance.
//
//    Input, external double F, the name of a user-supplied
//    function, of the form "FUNCTION F ( X )", which evaluates the
//    function whose zero is being sought.
//
//    Output, double ZERO, the estimated value of a zero of
//    the function F.
//
{
  double c;
  double d;
  double e;
  double fa;
  double fb;
  double fc;
  double m;
  double p;
  double q;
  double r;
  double s;
  double sa;
  double sb;
  double tol;
//
//  Make local copies of A and B.
//
  sa = a;
  sb = b;
  fa = f ( sa );
  fb = f ( sb );

  c = sa;
  fc = fa;
  e = sb - sa;
  d = e;

  for ( ; ; )
  {
    if ( r8_abs ( fc ) < r8_abs ( fb ) )
    {
      sa = sb;
      sb = c;
      c = sa;
      fa = fb;
      fb = fc;
      fc = fa;
    }

    tol = 2.0 * machep * r8_abs ( sb ) + t;
    m = 0.5 * ( c - sb );

    if ( r8_abs ( m ) <= tol || fb == 0.0 )
    {
      break;
    }

    if ( r8_abs ( e ) < tol || r8_abs ( fa ) <= r8_abs ( fb ) )
    {
      e = m;
      d = e;
    }
    else
    {
      s = fb / fa;

      if ( sa == c )
      {
        p = 2.0 * m * s;
        q = 1.0 - s;
      }
      else
      {
        q = fa / fc;
        r = fb / fc;
        p = s * ( 2.0 * m * a * ( q - r ) - ( sb - sa ) * ( r - 1.0 ) );
        q = ( q - 1.0 ) * ( r - 1.0 ) * ( s - 1.0 );
      }

      if ( 0.0 < p )
      {
        q = - q;
      }
      else
      {
        p = - p;
      }

      s = e;
      e = d;

      if ( 2.0 * p < 3.0 * m * q - r8_abs ( tol * q ) &&
        p < r8_abs ( 0.5 * s * q ) )
      {
        d = p / q;
      }
      else
      {
        e = m;
        d = e;
      }
    }
    sa = sb;
    fa = fb;

    if ( tol < r8_abs ( d ) )
    {
      sb = sb + d;
    }
    else if ( 0.0 < m )
    {
      sb = sb + tol;
    }
    else
    {
      sb = sb - tol;
    }

    fb = f ( sb );

    if ( ( 0.0 < fb && 0.0 < fc ) || ( fb <= 0.0 && fc <= 0.0 ) )
    {
      c = sa;
      fc = fa;
      e = sb - sa;
      d = e;
    }
  }
  return sb;
};
//****************************************************************************80

void zero_rc ( double a, double b, double t, double *arg, int *status, 
  double value )

//****************************************************************************80
//
//  Purpose:
//
//    ZERO_RC seeks the root of a function F(X) in an interval [A,B].
//
//  Discussion:
//
//    The interval [A,B] must be a change of sign interval for F.
//    That is, F(A) and F(B) must be of opposite signs.  Then
//    assuming that F is continuous implies the existence of at least
//    one value C between A and B for which F(C) = 0.
//
//    The location of the zero is determined to within an accuracy
//    of 6 * MACHEPS * r8_abs ( C ) + 2 * T.
//
//    The routine is a revised version of the Brent zero finder 
//    algorithm, using reverse communication.
//
//  Licensing:
//
//    This code is distributed under the GNU LGPL license. 
//
//  Modified:
//
//    14 October 2008
//
//  Author:
//
//    John Burkardt
//
//  Reference:
//
//    Richard Brent,
//    Algorithms for Minimization Without Derivatives,
//    Dover, 2002,
//    ISBN: 0-486-41998-3,
//    LC: QA402.5.B74.
//
//  Parameters:
//
//    Input, double A, B, the endpoints of the change of sign interval.
//
//    Input, double T, a positive error tolerance.
//
//    Output, double *ARG, the currently considered point.  The user
//    does not need to initialize this value.  On return with STATUS positive,
//    the user is requested to evaluate the function at ARG, and return
//    the value in VALUE.  On return with STATUS zero, ARG is the routine's
//    estimate for the function's zero.
//
//    Input/output, int *STATUS, used to communicate between 
//    the user and the routine.  The user only sets STATUS to zero on the first 
//    call, to indicate that this is a startup call.  The routine returns STATUS
//    positive to request that the function be evaluated at ARG, or returns
//    STATUS as 0, to indicate that the iteration is complete and that
//    ARG is the estimated zero
//
//    Input, double VALUE, the function value at ARG, as requested
//    by the routine on the previous call.
//
{
  static double c;
  static double d;
  static double e;
  static double fa;
  static double fb;
  static double fc;
  double m;
  static double machep;
  double p;
  double q;
  double r;
  double s;
  static double sa;
  static double sb;
  double tol;
//
//  Input STATUS = 0.
//  Initialize, request F(A).
//
  if ( *status == 0 )
  {
    machep = r8_epsilon ( );

    sa = a;
    sb = b;
    e = sb - sa;
    d = e;

    *status = 1;
    *arg = a;
    return;
  }
//
//  Input STATUS = 1.
//  Receive F(A), request F(B).
//
  else if ( *status == 1 )
  {
    fa = value;
    *status = 2;
    *arg = sb;
    return;
  }
//
//  Input STATUS = 2
//  Receive F(B).
//
  else if ( *status == 2 )
  {
    fb = value;

    if ( 0.0 < fa * fb )
    {
      *status = -1;
      return;
    }
    c = sa;
    fc = fa;
  }
  else
  {
    fb = value;

    if ( ( 0.0 < fb && 0.0 < fc ) || ( fb <= 0.0 && fc <= 0.0 ) )
    {
      c = sa;
      fc = fa;
      e = sb - sa;
      d = e;
    }
  }
//
//  Compute the next point at which a function value is requested.
//
  if ( r8_abs ( fc ) < r8_abs ( fb ) )
  {
    sa = sb;
    sb = c;
    c = sa;
    fa = fb;
    fb = fc;
    fc = fa;
  }

  tol = 2.0 * machep * r8_abs ( sb ) + t;
  m = 0.5 * ( c - sb );

  if ( r8_abs ( m ) <= tol || fb == 0.0 )
  {
    *status = 0;
    *arg = sb;
    return;
  }

  if ( r8_abs ( e ) < tol || r8_abs ( fa ) <= r8_abs ( fb ) )
  {
    e = m;
    d = e;
  }
  else
  {
    s = fb / fa;

    if ( sa == c )
    {
      p = 2.0 * m * s;
      q = 1.0 - s;
    }
    else
    {
      q = fa / fc;
      r = fb / fc;
      p = s * ( 2.0 * m * a * ( q - r ) - ( sb - sa ) * ( r - 1.0 ) );
      q = ( q - 1.0 ) * ( r - 1.0 ) * ( s - 1.0 );
    }

    if ( 0.0 < p )
    {
      q = - q;
    }
    else
    {
      p = - p;
    }
    s = e;
    e = d;

    if ( 2.0 * p < 3.0 * m * q - r8_abs ( tol * q ) && 
         p < r8_abs ( 0.5 * s * q ) )
    {
      d = p / q;
    }
    else
    {
      e = m;
      d = e;
    }
  }

  sa = sb;
  fa = fb;

  if ( tol < r8_abs ( d ) )
  {
    sb = sb + d;
  }
  else if ( 0.0 < m )
  {
    sb = sb + tol;
  }
  else
  {
    sb = sb - tol;
  }

  *arg = sb;
  *status = *status + 1;

  return;
};

};



///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////
MODULE_API void InitializeModuleData()
{
   AddAvailableDeviceName(g_ControllerName, "SimpleAutofocus Finder");
   
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
   if (deviceName == 0)
      return 0;

   if (strcmp(deviceName, g_ControllerName) == 0)
   {
      // create Controller
      SimpleAutofocus* pSimpleAutofocus = new SimpleAutofocus(g_ControllerName);
      return pSimpleAutofocus;
   }

   return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}

///////////////////////////////////////////////////////////////////////////////
// Controller implementation
// ~~~~~~~~~~~~~~~~~~~~

SimpleAutofocus::SimpleAutofocus(const char* name) : name_(name), pCore_(NULL), cropFactor_(0.2), busy_(false),
   coarseStepSize_(1.), coarseSteps_ (5), fineStepSize_ (0.3), fineSteps_ ( 5), threshold_( 0.1), enableAutoShuttering_(1), 
   sizeOfTempShortBuffer_(0), pShort_(NULL),latestSharpness_(0.), recalculate_(0), mean_(0.), standardDeviationOverMean_(0.), 
   pPoints_(NULL), pSmoothedIm_(NULL), sizeOfSmoothedIm_(0), offset_(0.)
{

}

int SimpleAutofocus::Shutdown()
{
return DEVICE_OK;
}


SimpleAutofocus::~SimpleAutofocus()
{
  delete pPoints_;

  if( NULL!=pShort_)
      free(pShort_);

   if(NULL!=pSmoothedIm_)
      free(pSmoothedIm_);

   delete pBrent_;

   Shutdown();
}

bool SimpleAutofocus::Busy()
{
     return busy_;
}

void SimpleAutofocus::GetName(char* name) const
{
   assert(name_.length() < CDeviceUtils::GetMaxStringLength());
   CDeviceUtils::CopyLimitedString(name, name_.c_str());
}






// channels are not available during initialization....
void SimpleAutofocus::RefreshChannelsToSelect(void)
{
   std::vector<std::string> channelConfigs;
   std::vector<std::string> cf2;

   char value[MM::MaxStrLength];
   std::string coreChannelGroup;
   
   if( DEVICE_OK == pCore_->GetDeviceProperty(MM::g_Keyword_CoreDevice, MM::g_Keyword_CoreChannelGroup, value))
      coreChannelGroup = std::string(value);

   if(  0 < coreChannelGroup.length())
   {
      LogMessage(" Core channel group is : " + coreChannelGroup, true);
      // this list of 'configs' is called 'presets' in the main UI
      if ( DEVICE_OK != pCore_->GetChannelConfigs(channelConfigs))
            LogMessage(" error retrieving channel configs! " , false);
      cf2 = channelConfigs;
   }
   cf2.push_back("");

   std::ostringstream os;
   os<<" channels in " << coreChannelGroup << ": ";
   std::vector<std::string>::iterator jj;
   for(jj = channelConfigs.begin(); jj != channelConfigs.end(); ++jj)
   {
      if( channelConfigs.begin() != jj)
         os << ", ";
      os << *jj;
   }
   LogMessage(os.str(), true);
   possibleChannels_ = cf2;
}

//const std::vector<std::string> SimpleAutofocus::RefreshChannelsToSelect(void)
//{
//   std::vector<std::string> channelConfigs;
//
//   char value[MM::MaxStrLength];
//   std::string coreChannelGroup;
//   
//   if( DEVICE_OK == pCore_->GetDeviceProperty(MM::g_Keyword_CoreDevice, MM::g_Keyword_CoreChannelGroup, value))
//   {
//      coreChannelGroup = std::string(value);
//   }
//   LogMessage(" Core channel group is : " + coreChannelGroup, true);
//
//
//   // this list of 'configs' is called 'presets' in the main UI
//   if ( DEVICE_OK != pCore_->GetChannelConfigs(channelConfigs))
//   {
//         LogMessage(" error retrieving channel configs! " , false);
//   }
//   std::vector<std::string> cf2 = channelConfigs;
//
//   cf2.push_back("");
//
//   assert( cf2.size() == channelConfigs.size()+1);
//
//   return cf2;
//   
//
//}


int SimpleAutofocus::Initialize()
{

   if(NULL == pPoints_)
   {
      pPoints_ = new SAFData();
   }
   pBrent_ = new Brent();
   LogMessage("SimpleAutofocus::Initialize()");
   pCore_ = GetCoreCallback();


   // Set Exposure
   CPropertyAction *pAct = new CPropertyAction (this, &SimpleAutofocus::OnExposure);
   CreateProperty(MM::g_Keyword_Exposure, "10", MM::Integer, false, pAct); 

   pAct = new CPropertyAction(this, &SimpleAutofocus::OnCoarseStepNumber);
   CreateProperty("CoarseSteps from center","5",MM::Integer, false, pAct);

   pAct = new CPropertyAction(this, &SimpleAutofocus::OnStepsizeCoarse);
   CreateProperty("CoarseStepSize","1.0",MM::Float, false, pAct);

   pAct = new CPropertyAction(this, &SimpleAutofocus::OnFineStepNumber);
   CreateProperty("FineSteps from center","5",MM::Integer, false, pAct);

   pAct = new CPropertyAction(this, &SimpleAutofocus::OnStepSizeFine);
   CreateProperty("FineStepSize","0.3",MM::Float, false, pAct);

   // Set the sharpness threshold
   pAct = new CPropertyAction(this, &SimpleAutofocus::OnThreshold);
   CreateProperty("Threshold","0.1",MM::Float, false, pAct);

   // Set the cropping factor to speed up computation
   pAct = new CPropertyAction(this, &SimpleAutofocus::OnCropFactor);
   CreateProperty("ROI CropFactor","0.2",MM::Float, false, pAct);

   pAct = new CPropertyAction(this, &SimpleAutofocus::OnSharpnessScore);
   CreateProperty("SharpnessScore","0.0",MM::Float, true, pAct);

   pAct = new CPropertyAction(this, &SimpleAutofocus::OnMean);
   CreateProperty("Mean","0",MM::Float, true, pAct);

   pAct = new CPropertyAction(this, &SimpleAutofocus::OnEnableAutoShutter);
   CreateProperty("EnableAutoshutter","0",MM::Integer, false, pAct);

   pAct = new CPropertyAction(this, &SimpleAutofocus::OnRecalculate);
   CreateProperty("Re-acquire&EvaluateSharpness","0",MM::Integer, false, pAct);

   pAct = new CPropertyAction(this, &SimpleAutofocus::OnStandardDeviationOverMean);
   CreateProperty("StandardDeviation/Mean","0",MM::Float, false, pAct);

   pAct = new CPropertyAction(this, &SimpleAutofocus::OnChannel);
   CreateProperty("Channel","",MM::String, false, pAct);
   AddAllowedValue("Channel","");
   AddAllowedValue("Channel","...");
   selectedChannelConfig_ = "";

   pAct = new CPropertyAction(this, &SimpleAutofocus::OnSearchAlgorithm);
   CreateProperty("SearchAlgorithm","Brent",MM::String, false, pAct);
   AddAllowedValue("SearchAlgorithm","Brent");
   AddAllowedValue("SearchAlgorithm","BruteForce");
   searchAlgorithm_ = "Brent";

   UpdateStatus();

   return DEVICE_OK;
}


// API

bool SimpleAutofocus::IsContinuousFocusLocked(){ 
   return locked_;} ;

int SimpleAutofocus::FullFocus()
{ 
	if( searchAlgorithm_ == "Brent")
	{
	   return BrentSearch();
	}
	else if( searchAlgorithm_ == "BruteForce")
	{
		return BruteForceSearch();
	}
	else
		return DEVICE_ERR;
};

int SimpleAutofocus::IncrementalFocus(){ 
   return -1;};
int SimpleAutofocus::GetLastFocusScore(double& score){
   score = latestSharpness_;
   return 0;};
int SimpleAutofocus::GetCurrentFocusScore(double& score){ 
   score = latestSharpness_ = SharpnessAtZ(Z());
   return 0;};
int SimpleAutofocus::AutoSetParameters(){ 
   return 0;};
int SimpleAutofocus::GetOffset(double &offset){ 
   offset = offset_;
   return 0;};
int SimpleAutofocus::SetOffset(double offset){ 
   offset_ = offset;
   return 0;};


/////////////////////////////////////////////
// Property Generators
/////////////////////////////////////////////




///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////

// action interface
// ---------------
int SimpleAutofocus::OnExposure(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   try
   {
      if (eAct == MM::BeforeGet)
      {
         // retrieve value from the camera via the core
         double v;
         pCore_->GetExposure(v);
         pProp->Set(v);
      }
      else if (eAct == MM::AfterSet)
      {
         // set the value to the camera via the core
         double val;
         pProp->Get(val);
         pCore_->SetExposure(val);
      }
   }
   catch(CMMError& e)
   {
      return e.getCode();

   }
   catch(...)
   {
      return DEVICE_ERR;
   }
   return DEVICE_OK;
}


 
int SimpleAutofocus::OnCoarseStepNumber(MM::PropertyBase* pProp, MM::ActionType eAct)
{       
   if (eAct == MM::BeforeGet)      
   {
      pProp->Set(coarseSteps_);      
   }
   else if (eAct == MM::AfterSet)
   { 
      pProp->Get(coarseSteps_);      
   }   
   return DEVICE_OK;
};

int SimpleAutofocus::OnFineStepNumber(MM::PropertyBase* pProp, MM::ActionType eAct){       if (eAct == MM::BeforeGet)      {         pProp->Set(fineSteps_);      }      else if (eAct == MM::AfterSet)      {         pProp->Get(fineSteps_);      }   return DEVICE_OK;};;
int SimpleAutofocus::OnStepsizeCoarse(MM::PropertyBase* pProp, MM::ActionType eAct){       if (eAct == MM::BeforeGet)      {         pProp->Set(coarseStepSize_);      }      else if (eAct == MM::AfterSet)      {         pProp->Get(coarseStepSize_);      }   return DEVICE_OK;};;
int SimpleAutofocus::OnStepSizeFine(MM::PropertyBase* pProp, MM::ActionType eAct){       if (eAct == MM::BeforeGet)      {         pProp->Set(fineStepSize_);      }      else if (eAct == MM::AfterSet)      {         pProp->Get(fineStepSize_);      }   return DEVICE_OK;};;
int SimpleAutofocus::OnThreshold(MM::PropertyBase* pProp, MM::ActionType eAct){       if (eAct == MM::BeforeGet)      {         pProp->Set(threshold_);      }      else if (eAct == MM::AfterSet)      {         pProp->Get(threshold_);      }   return DEVICE_OK;};;
int SimpleAutofocus::OnEnableAutoShutter(MM::PropertyBase* pProp, MM::ActionType eAct){       if (eAct == MM::BeforeGet)      {         pProp->Set(enableAutoShuttering_);      }      else if (eAct == MM::AfterSet)      {         pProp->Get(enableAutoShuttering_);      }   return DEVICE_OK;};;

int SimpleAutofocus::OnRecalculate(MM::PropertyBase* pProp, MM::ActionType eAct)
{ 
      if (eAct == MM::BeforeGet)
      {
         pProp->Set(recalculate_);
      }
      else if (eAct == MM::AfterSet)
      {
         pProp->Get(recalculate_);
         if( 0!= recalculate_)
         {
            latestSharpness_ = SharpnessAtZ(Z());
            recalculate_ = 0;
            pProp->Set(recalculate_);
         }
      }
   return DEVICE_OK;

};


int SimpleAutofocus::OnStandardDeviationOverMean(MM::PropertyBase* pProp, MM::ActionType eAct)
{ 
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(standardDeviationOverMean_);
   }
   else if (eAct == MM::AfterSet)
   {
      // never do anything for a read-only property
   }
   return DEVICE_OK;
};


int SimpleAutofocus::OnSearchAlgorithm(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(searchAlgorithm_.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(selectedChannelConfig_);
   }
   return DEVICE_OK;
}


int SimpleAutofocus::OnChannel(MM::PropertyBase* pProp, MM::ActionType eAct)
{ 
   if (eAct == MM::BeforeGet)
   {
      RefreshChannelsToSelect();
      SetAllowedValues("Channel", possibleChannels_);
      std::vector<std::string>::iterator isThere = std::find(possibleChannels_.begin(),possibleChannels_.end(), selectedChannelConfig_);
      if( possibleChannels_.end() == isThere)
         selectedChannelConfig_ = "";

      //todo - triple check that this doesn't wipe out AF channel selections!!
      if( selectedChannelConfig_.length() < 1) // no channel is selected for AF
      {
         // get the channel selected for the mainframe
         char value[MM::MaxStrLength];
         std::string coreChannelGroup;
         if( DEVICE_OK == pCore_->GetDeviceProperty(MM::g_Keyword_CoreDevice, MM::g_Keyword_CoreChannelGroup, value))
            coreChannelGroup = std::string(value);
         if( 0 < coreChannelGroup.length() )
         {
            pCore_->GetCurrentConfig(coreChannelGroup.c_str(),MM::MaxStrLength,value);
            selectedChannelConfig_ = std::string(value);
         }
      }
      pProp->Set(selectedChannelConfig_.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(selectedChannelConfig_);
      if ( 0 < selectedChannelConfig_.length())
      {
         char value[MM::MaxStrLength];
         std::string coreChannelGroup;
         if( DEVICE_OK == pCore_->GetDeviceProperty(MM::g_Keyword_CoreDevice, MM::g_Keyword_CoreChannelGroup, value))
            coreChannelGroup = std::string(value);
         int ret = pCore_->SetConfig(coreChannelGroup.c_str(),selectedChannelConfig_.c_str());
	      if(ret != DEVICE_OK)
		      return ret;
      }
   }
   return DEVICE_OK;
};


//int SimpleAutofocus::OnChannel(MM::PropertyBase* pProp, MM::ActionType eAct)
//{ 
//   if (eAct == MM::BeforeGet)
//   {
//      // user can always surreptitiously change the 'channel group' without our knowing it!
//      /*const ? */ std::vector<std::string> possible = this->RefreshChannelsToSelect();
//      ClearAllowedValues("Channel");
//      SetAllowedValues("Channel", possible);
//      std::vector<std::string>::iterator isThere = std::find(possible.begin(),possible.end(), selectedChannelConfig_);
//      if( possible.end() == isThere)
//         selectedChannelConfig_ = "";
//      pProp->Set(selectedChannelConfig_.c_str());
//   }
//   else if (eAct == MM::AfterSet)
//   {
//      char value[MM::MaxStrLength];
//
//      pProp->Get(selectedChannelConfig_);
//      if( 0 < selectedChannelConfig_.length())
//      {
//         // need to find the 'channel group' so we can set the channel...  
//         pCore_->GetDeviceProperty(MM::g_Keyword_CoreDevice, MM::g_Keyword_CoreChannelGroup, value);
//         pCore_->SetConfig(value, selectedChannelConfig_.c_str());
//      }
//   }
//   return DEVICE_OK;
//};





int SimpleAutofocus::OnCropFactor(MM::PropertyBase* pProp, MM::ActionType eAct)
{ 
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(cropFactor_);
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(cropFactor_);
   }
   return DEVICE_OK;

};

int SimpleAutofocus::OnSharpnessScore(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(latestSharpness_);
   }
   else if (eAct == MM::AfterSet)
   {
      // never do anything for a read-only property
   }
   return DEVICE_OK;

}

int SimpleAutofocus::OnMean(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(mean_);
   }
   else if (eAct == MM::AfterSet)
   {
      // never do anything for a read-only property
   }
   return DEVICE_OK;
}





// computational utilities
short SimpleAutofocus::findMedian(short* arr, const int lengthMinusOne)
{ 
  short tmp;

   // n.b. this was ported from java, looks like a bubble sort....
  // todo use qsort
   for(int i=0; i<lengthMinusOne; ++i)
   {
      for(int j=0; j<lengthMinusOne-i; ++j)
      {
         if (arr[j+1]<arr[j])
         {
            tmp = arr[j];
            arr[j]=arr[j+1];
            arr[j+1]=tmp;
         }
      }
   }
   return arr[lengthMinusOne/2 +1];
}


double SimpleAutofocus::SharpnessAtZ(const double z)
{
   busy_ = true;
   Z(z);
   short windo[9];

   	int w0 = 0, h0 = 0, d0 = 0;
      double sharpness = 0;
      pCore_->GetImageDimensions(w0, h0, d0);
      
      int width =  (int)(cropFactor_*w0);
      int height = (int)(cropFactor_*h0);
      int ow = (int)(((1-cropFactor_)/2)*w0);
      int oh = (int)(((1-cropFactor_)/2)*h0);
      
      const unsigned long thisSize = sizeof(*pSmoothedIm_)*width*height;

      if( thisSize != sizeOfSmoothedIm_)
      {
         if(NULL!=pSmoothedIm_)
           free(pSmoothedIm_);
         // malloc is faster than new...
         pSmoothedIm_ = (float*)malloc(thisSize);
         if(NULL!=pSmoothedIm_)
         {
            sizeOfSmoothedIm_ = thisSize;
         }
         else // todo throw out of here...
            return sharpness;
      }


      // copy from MM image to the working buffer

	   ImgBuffer image(w0,h0,d0);
      //snap an image
      const unsigned char* pI = reinterpret_cast<const unsigned char*>(pCore_->GetImage());
      const unsigned short* pSInput = reinterpret_cast<const unsigned short*>(pI);

      int iindex;
      bool legalFormat = false;
      // to keep it simple always copy to a short array
      switch( d0)
      {
      case 1:
         legalFormat = true;
         if( sizeOfTempShortBuffer_ != sizeof(short)*w0*h0)
         {
            if( NULL != pShort_)
               free(pShort_);
            // malloc is faster than new...
            pShort_ = (short*)malloc( sizeof(short)*w0*h0);
            if( NULL!=pShort_)
            {
               sizeOfTempShortBuffer_ = sizeof(short)*w0*h0;
            }
         }

         for(iindex = 0; iindex < w0*h0; ++iindex)
         {
            pShort_[iindex] = pI[iindex];
         }
         break;

      case 2:
         legalFormat = true;
         if( sizeOfTempShortBuffer_ != sizeof(short)*w0*h0)
         {
            if( NULL != pShort_)
               free(pShort_);
            pShort_ = (short*)malloc( sizeof(short)*w0*h0);
            if( NULL!=pShort_)
            {
               sizeOfTempShortBuffer_ = sizeof(short)*w0*h0;
            }
         }
         for(iindex = 0; iindex < w0*h0; ++iindex)
         {
            pShort_[iindex] = pSInput[iindex];
         }
         break;
      default:
         break;
      }

      if(legalFormat)
      {
         // calculate the standard deviation & mean

         long nPts = 0;
         mean_ = 0;
         double M2 = 0;
         double delta;

         // one-pass algorithm for mean and std from Welford / Knuth

         for (int i=0; i<width; i++)
         {
            for (int j=0; j<height; j++)
            {
               ++nPts;
               long value = pShort_[ow+i+ width*(oh+j)];
               delta = value - mean_;
               mean_ = mean_ + delta/nPts;
               M2 = M2 + delta*(value - mean_); // #This expression uses the new value of mean_
            } 
         }

         //double variance_n = M2/nPts;
         double variance = M2/(nPts - 1);
         standardDeviationOverMean_ = 0.;
         double meanScaling = 1.;
         if( 0. != mean_)
         {
            standardDeviationOverMean_ = pow(variance,0.5)/mean_;
            meanScaling = 1./mean_;
         }


         LogMessage("N " + boost::lexical_cast<std::string,long>(nPts) + " mean " +  boost::lexical_cast<std::string,float>((float)mean_) + " nrmlzd std " +  boost::lexical_cast<std::string,float>((float)standardDeviationOverMean_) );

         // ToDO -- eliminate copy above.

         /*Apply 3x3 median filter to reduce shot noise*/
         for (int i=0; i<width; i++){
            for (int j=0; j<height; j++){

               windo[0] = pShort_[ow+i-1 + width*(oh+j-1)];
               windo[1] = pShort_[ow+i+ width*(oh+j-1)];
               windo[2] = pShort_[ow+i+1+ width*(oh+j-1)];
               windo[3] = pShort_[ow+i-1+ width*(oh+j)];
               windo[4] = pShort_[ow+i+ width*(oh+j)];
               windo[5] = pShort_[ow+i+1+ width*(oh+j)];
               windo[6] = pShort_[ow+i-1+ width*(oh+j+1)];
               windo[7] = pShort_[ow+i+ width*(oh+j+1)];
               windo[8] = pShort_[ow+i+1+ width*(oh+j+1)];

               pSmoothedIm_[i + j*width] = (float)((double)findMedian(windo,8)*meanScaling);
            } 
         }

         /*Edge detection using a 3x3 filter: [-2 -1 0; -1 0 1; 0 1 2]. Then sum all pixel values. Ideally, the sum is large if most edges are sharp*/

         for (int k=1; k<width-1; k++){
            for (int l=1; l<height-1; l++)
            {
               double convolvedValue = -2.0*pSmoothedIm_[k-1 + width*(l-1)] - pSmoothedIm_[k+ width*(l-1)]-pSmoothedIm_[k-1 + width*l]+pSmoothedIm_[k+1 + width*l]+pSmoothedIm_[k+ width*(l+1)]+2.0*pSmoothedIm_[k+1+ width*(l+1)];
               sharpness = sharpness + convolvedValue*convolvedValue;

            } 
         }

         //free(pShort);

      }
     // delete medPix;
      //delete windo;
      busy_ = false;
      latestSharpness_ = sharpness;
      return sharpness;
   }



void  SimpleAutofocus::Z(const double value)
{
   pCore_->SetFocusPosition(value);
}

double SimpleAutofocus::Z(void)
{
   double value;
   pCore_->GetFocusPosition(value);
   return value;
}


void SimpleAutofocus::Exposure(const int value)
{ 
   pCore_->SetExposure(value);
};

int SimpleAutofocus::Exposure(void){
   double value;
   pCore_->GetExposure(value);
   return (int)(0.5+value);
};


// always calls member function SharpnessAtZ

int SimpleAutofocus::BruteForceSearch( )
{

   pPoints_->Clear();
   int acquisitionSequenceNumber = 0;
   double baseDist = 0.;
   double bestDist = 0.;
   double curSh = 0. ;
   double bestSh = 0.;
   MM::MMTime tPrev;
   MM::MMTime tcur;
   double curDist = Z();

   char value[MM::MaxStrLength];

   std::string coreChannelGroup;
   if( DEVICE_OK == pCore_->GetDeviceProperty(MM::g_Keyword_CoreDevice, MM::g_Keyword_CoreChannelGroup, value))
      coreChannelGroup = std::string(value);

   int ret = pCore_->SetConfig(coreChannelGroup.c_str(),selectedChannelConfig_.c_str());
	if(ret != DEVICE_OK)
		return ret;

   char shutterDeviceName[MM::MaxStrLength];
   pCore_->GetDeviceProperty(MM::g_Keyword_CoreDevice, MM::g_Keyword_CoreAutoShutter, value);
   std::istringstream iss(value);
   int ivalue ;
   iss >> ivalue;
   bool previousAutoShutterSetting = static_cast<bool>(ivalue);
   bool currentAutoShutterSetting = previousAutoShutterSetting;

   // allow auto-shuttering or continuous illumination
   if((0==enableAutoShuttering_) && previousAutoShutterSetting)
   {
      pCore_->SetDeviceProperty(MM::g_Keyword_CoreDevice, MM::g_Keyword_CoreAutoShutter, "0"); // disable auto-shutter
      currentAutoShutterSetting = false;
   }

   if( !currentAutoShutterSetting)
   {
      pCore_->GetDeviceProperty(MM::g_Keyword_CoreDevice, MM::g_Keyword_CoreShutter, shutterDeviceName);
      pCore_->SetDeviceProperty(shutterDeviceName, MM::g_Keyword_State, "1"); // open shutter
   }

   baseDist = curDist - coarseStepSize_ * coarseSteps_;

   // start of coarse search
   LogMessage("AF start coarse search range is  " + boost::lexical_cast<std::string,double>(baseDist) + " to " + boost::lexical_cast<std::string,double>(baseDist + coarseStepSize_*(2 * coarseSteps_)), messageDebug);
   for (int i = 0; i < 2 * coarseSteps_ + 1; ++i)
   {
      tPrev = GetCurrentMMTime();
      curDist = baseDist + i * coarseStepSize_;
      std::ostringstream progressMessage;
      progressMessage << "\nAF evaluation @ " + boost::lexical_cast<std::string,double>(curDist);
      curSh = SharpnessAtZ( curDist);
      pPoints_->InsertPoint(acquisitionSequenceNumber++,(float)curDist,(float)mean_,(float)standardDeviationOverMean_,latestSharpness_);
      progressMessage <<  " AF metric is: " + boost::lexical_cast<std::string,double>(curSh);
      LogMessage( progressMessage.str(),  messageDebug);

      if (curSh > bestSh)
      {
         bestSh = curSh;
         bestDist = curDist;
      } else if (bestSh - curSh > threshold_ * bestSh)
      {
         break;
      }
      tcur = GetCurrentMMTime() - tPrev;
   }
   baseDist = bestDist - fineStepSize_ * fineSteps_;
   LogMessage("AF start fine search range is  " + boost::lexical_cast<std::string,double>(baseDist)+" to " + boost::lexical_cast<std::string,double>( baseDist+(2*fineSteps_)*fineStepSize_),  messageDebug);
   //Fine search
   for (int i = 0; i < 2 * fineSteps_ + 1; i++)
   {
      tPrev = GetCurrentMMTime();
      curDist =  baseDist + i * fineStepSize_;
      std::ostringstream progressMessage;
      progressMessage << "\nAF evaluation @ " + boost::lexical_cast<std::string,double>(curDist);
      curSh = SharpnessAtZ(curDist);
      pPoints_->InsertPoint(acquisitionSequenceNumber++,(float)curDist,(float)mean_,(float)standardDeviationOverMean_,latestSharpness_);
      progressMessage <<  " AF metric is: " + boost::lexical_cast<std::string,double>(curSh);
      LogMessage( progressMessage.str(),  messageDebug);

      if (curSh > bestSh)
      {
         bestSh = curSh;
         bestDist = curDist;
      } else if (bestSh - curSh > threshold_ * bestSh)
      {
         break;
      }
      tcur = GetCurrentMMTime() - tPrev;
   }
   LogMessage("AF best position is " + boost::lexical_cast<std::string,double>(bestDist),  messageDebug);
   LogMessage("AF Performance Table:\n" + pPoints_->Table(), messageDebug);
   if( !currentAutoShutterSetting)
   {
      pCore_->SetDeviceProperty(shutterDeviceName, MM::g_Keyword_State, "0"); // close
   }
   pCore_->SetDeviceProperty(MM::g_Keyword_CoreDevice, MM::g_Keyword_CoreAutoShutter, previousAutoShutterSetting?"1":"0"); // restore auto-shutter

  Z(bestDist);
  latestSharpness_ = bestSh;
  return DEVICE_OK;
}





int SimpleAutofocus::BrentSearch( )
{
   int ret = DEVICE_OK;
   pPoints_->Clear();
   int acquisitionSequenceNumber = 0;
   double baseDist = 0.;
   double bestDist = 0.;
   double curSh = 0. ;
   double bestSh = 0.;
   MM::MMTime tPrev;
   MM::MMTime tcur;
   double curDist = Z();
   char value[MM::MaxStrLength];

   std::string coreChannelGroup;
   if( DEVICE_OK == pCore_->GetDeviceProperty(MM::g_Keyword_CoreDevice, MM::g_Keyword_CoreChannelGroup, value))
      coreChannelGroup = std::string(value);

   ret = pCore_->SetConfig(coreChannelGroup.c_str(),selectedChannelConfig_.c_str());
	if(ret != DEVICE_OK)
		return ret;

   char shutterDeviceName[MM::MaxStrLength];
   pCore_->GetDeviceProperty(MM::g_Keyword_CoreDevice, MM::g_Keyword_CoreAutoShutter, value);
   std::istringstream iss(value);
   int ivalue ;
   iss >> ivalue;
   bool previousAutoShutterSetting = static_cast<bool>(ivalue);
   bool currentAutoShutterSetting = previousAutoShutterSetting;

   // allow auto-shuttering or continuous illumination
   if((0==enableAutoShuttering_) && previousAutoShutterSetting)
   {
      pCore_->SetDeviceProperty(MM::g_Keyword_CoreDevice, MM::g_Keyword_CoreAutoShutter, "0"); // disable auto-shutter
      currentAutoShutterSetting = false;
   }

   if( !currentAutoShutterSetting)
   {
      pCore_->GetDeviceProperty(MM::g_Keyword_CoreDevice, MM::g_Keyword_CoreShutter, shutterDeviceName);
      pCore_->SetDeviceProperty(shutterDeviceName, MM::g_Keyword_State, "1"); // open shutter
   }
   baseDist = curDist - coarseStepSize_ * coarseSteps_;
 
   double z0 = baseDist;
   double z1 = baseDist + coarseStepSize_*(2 * coarseSteps_);
   LogMessage("AF start search range is  " + boost::lexical_cast<std::string,double>(z0) + " to " + boost::lexical_cast<std::string,double>(z1), messageDebug);
   int status=0;
   double dvalue = -1.*SharpnessAtZ(z1);
   // save this point
   pPoints_->InsertPoint(acquisitionSequenceNumber++,(float)z1,(float)mean_,(float)standardDeviationOverMean_,latestSharpness_);

   for ( ; ; )
   {
      // query for next position to evaluate
      bestDist = pBrent_->local_min_rc ( &z0, &z1, &status, dvalue );
      if ( status < 0 )
      {
         ret = status;
         break;
      }
       // next position
       dvalue = -1.*SharpnessAtZ( bestDist );
       pPoints_->InsertPoint(acquisitionSequenceNumber++,(float)bestDist,(float)mean_,(float)standardDeviationOverMean_,latestSharpness_);
       if ( status == 0 )
       {
            break;
       }
	   if( z1 - z0 < fineStepSize_/3.)
		   break;
       if ( 27 < acquisitionSequenceNumber )
       {
          LogMessage("too many steps!",false);
          ret = DEVICE_ERR;
          break;
       }
   }


   LogMessage("AF best position is " + boost::lexical_cast<std::string,double>(bestDist),  messageDebug);
   LogMessage("AF Performance Table:\n" + pPoints_->Table(), messageDebug);
   if( !currentAutoShutterSetting)
   {
      pCore_->SetDeviceProperty(shutterDeviceName, MM::g_Keyword_State, "0"); // close
   }
   pCore_->SetDeviceProperty(MM::g_Keyword_CoreDevice, MM::g_Keyword_CoreAutoShutter, previousAutoShutterSetting?"1":"0"); // restore auto-shutter

   Z(bestDist);
   return DEVICE_OK;
}


