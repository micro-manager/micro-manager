///////////////////////////////////////////////////////////////////////////////
//FILE:          Galvo.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     Projector plugin
//-----------------------------------------------------------------------------
//AUTHOR:        Arthur Edelstein
//COPYRIGHT:     University of California, San Francisco, 2010-2014
//LICENSE:       This file is distributed under the BSD license.
//               License text is included with the source distribution.
//               This file is distributed in the hope that it will be useful,
//               but WITHOUT ANY WARRANTY; without even the implied warranty
//               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.

package org.micromanager.projector.internal;

import ij.process.FloatPolygon;
import java.awt.geom.Point2D;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import mmcorej.CMMCore;
import org.micromanager.Studio;

public class Galvo implements ProjectionDevice {
   private final String galvo_;
   private final CMMCore mmc_;
   private final Studio app_;
   private final ExecutorService galvoExecutor_;
   private final HashSet<OnStateListener> onStateListeners_ = 
           new HashSet<OnStateListener>();
   private long interval_us_;

   public Galvo(Studio app, CMMCore mmc) {
      mmc_ = mmc;
      app_ = app;
      galvo_ = mmc_.getGalvoDevice();
      galvoExecutor_ = Executors.newSingleThreadExecutor();
   }

   @Override
   public String getName() {
       return galvo_;
   }
   
   @Override
   public void displaySpot(final double x, final double y) {
      galvoExecutor_.execute(new Runnable() {
         @Override
         public void run() {
            try {
               mmc_.pointGalvoAndFire(galvo_, x, y, Galvo.this.getExposure());
            } catch (Exception ex) {
               app_.logs().logError(ex);
            }
         }
      });
   }

   @Override
   public void waitForDevice() {
      Future result = galvoExecutor_.submit(new Runnable() {
         @Override
         public void run() {
            // do nothing;
         }
      });
      try {
         result.get();
      } catch (InterruptedException ex) {
         app_.logs().logError(ex);
      } catch (ExecutionException ex) {
         app_.logs().logError(ex);
      }
   }

   @Override
   public double getXRange() {
      try {
         Double result = galvoExecutor_.submit(new Callable<Double>() {

            @Override
            public Double call() {
               try {
                  return mmc_.getGalvoXRange(galvo_);
               } catch (Exception ex) {
                  return 0.0;
               }
            }
         }).get();
         if (result == 0) {
            app_.logs().logError("Unable to get galvo width");
         }
         return result;
      } catch (InterruptedException ex) {
         app_.logs().logError("Unable to get galvo width");
         return 0;
      } catch (ExecutionException ex) {
         app_.logs().logError("Unable to get galvo width");
         return 0;
      }
   }
   
   @Override
   public double getYRange() {
      try {
         Double result = galvoExecutor_.submit(new Callable<Double>() {

            @Override
            public Double call() {
               try {
                  return mmc_.getGalvoYRange(galvo_);
               } catch (Exception ex) {
                  return 0.0;
               }
            }
         }).get();
         if (result == 0) {
            app_.logs().logError("Unable to get galvo height");
         }
         return result;
      } catch (InterruptedException ex) {
         app_.logs().logError("Unable to get galvo height");
         return 0;
      } catch (ExecutionException ex) {
         app_.logs().logError("Unable to get galvo height");
         return 0;
      }
   }
   
   @Override
   public double getXMinimum() {
      try {
         Double result = galvoExecutor_.submit(new Callable<Double>() {

            @Override
            public Double call() {
               try {
                  return mmc_.getGalvoXMinimum(galvo_);
               } catch (Exception ex) {
                  return 0.0;
               }
            }
         }).get();
         if (result == 0) {
            app_.logs().logError("Unable to get galvo X minimum");
         }
         return result;
      } catch (InterruptedException ex) {
         app_.logs().logError("Unable to get galvo X minimum");
         return 0;
      } catch (ExecutionException ex) {
         app_.logs().logError("Unable to get galvo X minimum");
         return 0;
      }
   }
   
   @Override
   public double getYMinimum() {
      try {
         Double result = galvoExecutor_.submit(new Callable<Double>() {

            @Override
            public Double call() {
               try {
                  return mmc_.getGalvoYMinimum(galvo_);
               } catch (Exception ex) {
                  return 0.0;
               }
            }
         }).get();
         if (result == 0) {
            app_.logs().logError("Unable to get galvo Y minimum");
         }
         return result;
      } catch (InterruptedException ex) {
         app_.logs().logError("Unable to get galvo Y minimum");
         return 0;
      } catch (ExecutionException ex) {
         app_.logs().logError("Unable to get galvo Y minimum");
         return 0;
      }
   }

   @Override
   public void turnOn() {
      galvoExecutor_.submit(new Runnable() {
         @Override
         public void run() {
            try {
               mmc_.setGalvoIlluminationState(galvo_, true);
            } catch (Exception ex) {
               app_.logs().showError(ex);
            }
         }
      });
      for (OnStateListener listener:onStateListeners_) {
        listener.stateChanged(true);
      }
   }

   @Override
   public void turnOff() {
      galvoExecutor_.submit(new Runnable() {
         @Override
         public void run() {
            try {
               mmc_.setGalvoIlluminationState(galvo_, false);
            } catch (Exception ex) {
               app_.logs().showError(ex);
            }
         }
      });
      for (OnStateListener listener:onStateListeners_) {
        listener.stateChanged(false);
      }
   }

   @Override
   public void loadRois(final List<FloatPolygon> rois) {
      galvoExecutor_.submit(new Runnable() {
         @Override
         public void run() {
            try {
               mmc_.deleteGalvoPolygons(galvo_);
            } catch (Exception ex) {
               app_.logs().logError(ex);
            }
            int roiCount = 0;
            try {
               for (FloatPolygon poly : rois) {
                  Point2D lastGalvoPoint = null;
                  for (int i = 0; i < poly.npoints; ++i) {
                     Point2D.Double galvoPoint = new Point2D.Double(
                             poly.xpoints[i], poly.ypoints[i]);
                     if (i == 0) {
                        lastGalvoPoint = galvoPoint;
                     }
                     mmc_.addGalvoPolygonVertex(galvo_, roiCount, galvoPoint.getX(), 
                             galvoPoint.getY());
                     if (poly.npoints == 1) {
                        ++roiCount;
                     }
                  }
                  if (poly.npoints > 1 && lastGalvoPoint != null) {
                     mmc_.addGalvoPolygonVertex(galvo_, roiCount, 
                             lastGalvoPoint.getX(), lastGalvoPoint.getY());
                     ++roiCount;
                  }
               }
            } catch (Exception e) {
               app_.logs().showError(e);
            }

            try {
               mmc_.loadGalvoPolygons(galvo_);
            } catch (Exception ex) {
               app_.logs().showError(ex);
            }
         }
      });
   }


   @Override
   public void runPolygons() {
      galvoExecutor_.submit(new Runnable() {
         @Override
         public void run() {
            try {
               mmc_.runGalvoPolygons(galvo_);
            } catch (Exception ex) {
               app_.logs().showError(ex);
            }
         }
      });

   }

   @Override
   public void addOnStateListener(OnStateListener listener) {
      onStateListeners_.add(listener);
   }

   public void removeOnStateListener(OnStateListener listener) {
      onStateListeners_.remove(listener);
   }

   @Override
   public void setPolygonRepetitions(final int reps) {
      galvoExecutor_.submit(new Runnable() {
         @Override
         public void run() {

            try {
               mmc_.setGalvoPolygonRepetitions(galvo_, reps);
            } catch (Exception ex) {
               app_.logs().showError(ex);
            }
         }
      });
   }

    @Override
    public String getChannel() {
        Future<String> channel = galvoExecutor_.submit(new Callable<String>() {
            @Override
            public String call() {
                try {
                    return mmc_.getGalvoChannel(galvo_);
                } catch (Exception ex) {
                    app_.logs().logError(ex);
                    return null;
                }
            }
        });
        try {
            return channel.get();
        } catch (InterruptedException e) {
            return null;
        } catch (ExecutionException e) {
           return null;
      }
    }

   @Override
   public void setExposure(long interval_us) {
      try {
         interval_us_ = interval_us;
         mmc_.setGalvoSpotInterval(galvo_, interval_us);
      } catch (Exception ex) {
         app_.logs().showError(ex);
      }
   }
   
      // Reads the exposure time in us
   @Override
   public long getExposure() {
      return interval_us_;
   }

    @Override
    public void activateAllPixels() {
        // Do nothing.
    }
    
    @Override
    public void showCheckerBoard(int x, int y) {
       // Do nothing
    }
}
