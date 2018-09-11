__kernel void subtract( __global short* a,
                        __global short* b) 
{
   int i = get_global_id(0);
  
   a[i] = a[i] - b[i];
}

__kernel void divide(__global short* a,
                     __global short* b)
{
    int i = get_global_id(0);

    a[i] = a[i] / b[i];
}

__kernel void subtractAndDivide(__global short* a,
                                __global short* background,
                                __global short* flatField)
{
    int i = get_global_id(0);

    a[i] = (a[i] - background[i]) / flatField[i];
}