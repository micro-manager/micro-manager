/**
  Simple kernels for manipulation of images
  Treat images as buffers
*/


__kernel void subtractUS( __global ushort* a,
                          const __global ushort* b) 
{  
   int i = get_global_id(0);
  
   a[i] = a[i] - b[i];
}

__kernel void subtractUB( __global uchar* a,
                          const __global uchar* b) 
{
   int i = get_global_id(0);
  
   a[i] = a[i] - b[i];
}

__kernel void multiplyUSF(__global ushort* a,
                     const __global float* b)
{
    int i = get_global_id(0);

    a[i] = (ushort) ( (a[i] * b[i]) + 0.5f);
}

__kernel void multiplyUBF(__global uchar* a,
                     const __global float* b)
{
    int i = get_global_id(0);

    a[i] = (uchar) ( (a[i] * b[i]) + 0.5f);
}


__kernel void subtractAndMultiplyUSF(__global ushort* a,
                                const __global ushort* background,
                                const __global float* flatField)
{
    int i = get_global_id(0);

    a[i] = convert_ushort ( ((a[i] - background[i]) * (flatField[i]) + 0.5f));
}

__kernel void subtractAndMultiplyUBF(__global uchar* a,
                                const __global uchar* background,
                                const __global float* flatField)
{
    int i = get_global_id(0);

    a[i] = convert_uchar ( ((a[i] - background[i]) * (flatField[i]) + 0.5f));
}