// This macro demonstrate how to use the getHistogram() and
// Plot.create() functions. Fix the Y-axis by assigning a value
// greater than zero to the  'maxCount' variable. With 16-bit 
// and 32-bit images, change the bin count by assigning a 
// different value to the 'bins' variable and fix the X-axis by 
// assigning non-zero values to 'histMin' and 'histMax'. 

  bins = 256;
  maxCount = 0;
  histMin = 0;
  histMax = 0;

  if (histMax>0)
      getHistogram(values, counts, bins, histMin, histMax);
  else
      getHistogram(values, counts, bins);
  is8bits = bitDepth()==8 || bitDepth()==24;
  Plot.create("Histogram", "Pixel Value", "Count", values, counts);
  if (maxCount>0)
      Plot.setLimits(0, 256, 0, maxCount);

  n = 0;
  sum = 0;
  min = 9999999;
  max = -9999999;

  for (i=0; i<bins; i++) {
      count = counts[i];
      if (count>0) {
          n += count;
          sum += count*i;
          if (i<min) min = i;
          if (i>max) max = i;
      }
  }
  var x=0.025, y=0.1; // global variables
  draw("Pixel Count: "+n);
  if (is8bits) {
      if (counts[0]>0 || counts[255]>0) {
          draw("Black Pixels: "+counts[0]+" ("+counts[0]*100/n+"%)");
          draw("White Pixels: "+counts[255]+" ("+counts[255]*100/n+"%)");
      }
      draw("Mean: "+sum/n);
      draw("Min: "+min);
      draw("Max: "+max);   
  }

  function draw(text) {
      Plot.addText(text, x, y);
      y += 0.08;
  }

