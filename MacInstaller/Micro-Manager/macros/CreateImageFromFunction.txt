// This macro uses a math function to generate an image.

  width = 500; height = 500;
  xc = width/2; yc = height/2;
  newImage("Math", "32-bit black", width, height, 1);
  for (y= 0; y<height; y++) {
      for (x= 0; x<width; x++) {
          xx = sqrt ((x-xc)*(x-xc)+(y-yc)*(y-yc));
          setPixel(x, y, f1(xx));
      }
      if (y%20==0) showProgress(y, height);
  }
  resetMinAndMax();
  makeLine(0, 0, width, height);
  run("Plot Profile");
  exit;

  function f1(x) {return 2*x*x-3;}
  function f2(x) {return cos(x/10);}
  function f3(x) {return sqrt(x);}
  function f4(x) {if (x==0) return 0; else return log(x);}
  function f5(x) {return exp(x/100);}

