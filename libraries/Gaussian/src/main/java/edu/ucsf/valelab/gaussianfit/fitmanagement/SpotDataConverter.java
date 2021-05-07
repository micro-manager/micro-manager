package edu.ucsf.valelab.gaussianfit.fitmanagement;

import edu.ucsf.valelab.gaussianfit.algorithm.GaussianFit;
import edu.ucsf.valelab.gaussianfit.data.GaussianInfo;
import edu.ucsf.valelab.gaussianfit.data.SpotData;
import edu.ucsf.valelab.gaussianfit.fitting.ZCalibrator;

/**
 * @author nico
 */
public class SpotDataConverter {

   /**
    * Given an input spot and the results of the Gaussian fit, populates the SpotData in the correct
    * units
    *
    * @param spot      - input spot (which will be copied)
    * @param fitResult - Results of Gaussian fit in pixel and digital counts units
    * @param info      - Information about the system needed to convert to real world values
    * @param zc        - ZCailbration information if available
    * @return SpotData with calibrated values
    */
   public static SpotData convert(
         SpotData spot,
         GaussianFit.Data fitResult,
         GaussianInfo info,
         ZCalibrator zc) {

      SpotData spotData = new SpotData(spot);
      double sx;
      double sy;
      double a = 1;
      double theta = 0;
      double gs = info.getFixedWidthNm() / info.getPixelSize() / 2;
      double cPCF = info.getPhotonConversionFactor() / info.getGain();
      if (fitResult.getParms().length >= 4) {

         double xMax = (fitResult.getParms()[GaussianFit.XC]
               - info.getHalfBoxSize() + spot.getX()) * info.getPixelSize();
         double yMax = (fitResult.getParms()[GaussianFit.YC]
               - info.getHalfBoxSize() + spot.getY()) * info.getPixelSize();
         // express background in photons after base level correction
         double bInElectrons =
               cPCF * (fitResult.getParms()[GaussianFit.BGR] - info.getBaseLevel());
         // Add the read-noise of the camera (expressed in electrons
         double bgr = Math.sqrt(bInElectrons + (info.getReadNoise() * info.getReadNoise()));

         if (fitResult.getParms().length >= 5) {
            gs = fitResult.getParms()[GaussianFit.S];
         }
         double N = cPCF * fitResult.getParms()[GaussianFit.INT]
               * (2 * Math.PI * gs * gs);

         // # of photons and background as calculated using the method by
         // Franke et al. : http://dx.doi/org/10.1038/nmeth.4073
         double NAperture = cPCF * fitResult.getApertureIntensity();
         // first calculate aperture background-noise squared 
         // (i.e. background expressed in photons)
         double bgrAperture = cPCF *
               (fitResult.getApertureBackground() - info.getBaseLevel());
         // Add the read-noise of the camera (expressed in electrons)
         bgrAperture = Math.sqrt(bgrAperture + (info.getReadNoise() * info.getReadNoise()));
         // double N = info.getPhotonConversionFactor() * fitResult.getParms()[GaussianFit.INT];

         // calculate error using formula from Thompson et al (2002)
         // (dx)2 = (s*s + (a*a/12)) / N + (8*pi*s*s*s*s * b*b) / (a*a*N*N)
         double s = gs * info.getPixelSize();
         double sasqr = s * s + (info.getPixelSize() * info.getPixelSize()) / 12;
         double varX = sasqr / N
               + (8 * Math.PI * s * s * s * s * bgr * bgr) / (info.getPixelSize() * info
               .getPixelSize() * N * N);
         // If EM gain was used, add uncertainty due to Poisson distributed noise
         if (info.getGain() > 2.0) {
            varX = 2 * varX;
         }
         double sigma = Math.sqrt(varX);

         // Calculate error using the method by Mortenson et al.
         // http://dx.doi.org/10.1038/nmeth.1447
         // sasqr = s * s + (a * a)/12
         // d(x)2 = (sasqr /N) (16/9 + 8 * pi * sasqr * b * b / N * a * a)
         double mVarX = sasqr / N
               * (16 / 9 + ((8 * Math.PI * sasqr * bgr * bgr)
               / (N * info.getPixelSize() * info.getPixelSize())));
         // If EM gain was used, add uncertainty due to Poisson distributed noise
         if (info.getGain() > 2.0) {
            mVarX = 2 * mVarX;
         }
         double mSigma = Math.sqrt(mVarX);

         // variance using the integral function (5) from Mortenson et al.
         double integral = calcIntegral(N, info.getPixelSize(), sasqr, bgr);
         double altVarX = sasqr / N * (1 / (1 + integral));
         // If EM gain was used, add uncertainty due to Poisson distributed noise
         if (info.getGain() > 2.0) {
            altVarX = 2 * altVarX;
         }
         double altSigma = Math.sqrt(altVarX);

         // variance using the integral function (5) from Mortenson et al.
         // using aprteure intensity and background
         double integralApt = calcIntegral(NAperture, info.getPixelSize(), sasqr, bgrAperture);
         double altVarXApt = sasqr / N * (1 / (1 + integralApt));
         // If EM gain was used, add uncertainty due to Poisson distributed noise
         if (info.getGain() > 2.0) {
            altVarXApt = 2 * altVarXApt;
         }
         double altSigmaApt = Math.sqrt(altVarXApt);

         if (fitResult.getParms().length >= 6) {
            sx = fitResult.getParms()[GaussianFit.S1] * info.getPixelSize();
            sy = fitResult.getParms()[GaussianFit.S2] * info.getPixelSize();
            a = sx / sy;

            double z;

            if (zc.hasFitFunctions()) {
               z = zc.getZ(2 * sx, 2 * sy);
               spotData.setZCenter(z);
            }

         }

         if (fitResult.getParms().length >= 7) {
            theta = fitResult.getParms()[GaussianFit.S3];
         }

         double width = 2 * s;

         spotData.setData(N, bgr, xMax, yMax, 0.0, width, a, theta, sigma);
         spotData.addKeyValue(SpotData.Keys.APERTUREINTENSITY, NAperture);
         // Ratio of # of photons measured by Gaussian fit and Aperture method
         spotData.addKeyValue(SpotData.Keys.INTENSITYRATIO, N / NAperture);
         spotData.addKeyValue(SpotData.Keys.APERTUREBACKGROUND, bgrAperture);
         spotData.addKeyValue(SpotData.Keys.MSIGMA, mSigma);
         spotData.addKeyValue(SpotData.Keys.INTEGRALSIGMA, altSigma);
         spotData.addKeyValue(SpotData.Keys.INTEGRALAPERTURESIGMA, altSigmaApt);

      }
      return spotData;
   }

   public static Double calcIntegral(double N, double a, double sasqr, double bgr) {
      final int count = 1000;
      final double halfstep = 0.5d / (double) count;
      final double step = 1.0d / (double) count;
      double sum = 0.0d;
      for (int i = 0; i < count; i++) {
         double t = (double) i / (double) count + halfstep;
         sum += step * (Math.log(t) /
               (1 + (N * a * a * t) / (2 * Math.PI * sasqr * bgr * bgr)));
      }
      return sum;
   }
}
