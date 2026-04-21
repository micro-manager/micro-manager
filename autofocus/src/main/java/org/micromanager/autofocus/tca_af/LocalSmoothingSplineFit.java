package org.micromanager.autofocus.tca_af;

import org.apache.commons.math3.analysis.UnivariateFunction;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.CholeskyDecomposition;
import org.apache.commons.math3.linear.DecompositionSolver;
import org.apache.commons.math3.linear.LUDecomposition;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;
import org.apache.commons.math3.linear.ArrayRealVector;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class LocalSmoothingSplineFit {

    public static class FitResult {
        public final UnivariateFunction fitFunction;
        public final Model model;

        public FitResult(UnivariateFunction fitFunction, Model model) {
            this.fitFunction = fitFunction;
            this.model = model;
        }
    }

    public static class Model {
        public String kind;
        public double[] x;
        public double[] y;
        public double[] coeff;
        public double p;
        public PiecewisePolynomial pp;
        public double[] yfit;
        public double[] h;
        public double[][] Q;
        public double[][] R;
    }

    public static class PiecewisePolynomial {
        public final double[] breaks;
        public final double[][] coefs;
        public final int pieces;
        public final int order;
        public final int dim;

        public PiecewisePolynomial(double[] breaks, double[][] coefs, int pieces, int order, int dim) {
            this.breaks = breaks;
            this.coefs = coefs;
            this.pieces = pieces;
            this.order = order;
            this.dim = dim;
        }
    }

    public static FitResult fit(double[] xIn, double[] yIn, double p) {
        if (xIn == null || yIn == null) {
            throw new IllegalArgumentException("x and y must not be null.");
        }
        if (xIn.length != yIn.length) {
            throw new IllegalArgumentException("x and y must have the same length.");
        }

        List<Double> validX = new ArrayList<>();
        List<Double> validY = new ArrayList<>();
        for (int i = 0; i < xIn.length; i++) {
            double xv = xIn[i];
            double yv = yIn[i];
            if (Double.isFinite(xv) && Double.isFinite(yv)) {
                validX.add(xv);
                validY.add(yv);
            }
        }

        if (validX.size() < 2) {
            throw new IllegalArgumentException("localSmoothingSplineFit requires at least 2 valid points.");
        }

        int nValid = validX.size();
        double[] x = new double[nValid];
        double[] y = new double[nValid];
        for (int i = 0; i < nValid; i++) {
            x[i] = validX.get(i);
            y[i] = validY.get(i);
        }

        int[] order = createIndexOrder(x);
        x = reorderArray(x, order);
        y = reorderArray(y, order);

        double[][] reduced = collapseDuplicates(x, y);
        x = reduced[0];
        y = reduced[1];
        int n = x.length;

        if (n == 2) {
            double[] coeff = linearPolyFit(x, y);
            UnivariateFunction fitFunction = new PolynomialFunction(coeff);
            Model model = new Model();
            model.kind = "line_two_point";
            model.coeff = coeff;
            model.x = Arrays.copyOf(x, x.length);
            model.y = Arrays.copyOf(y, y.length);
            return new FitResult(fitFunction, model);
        }

        p = Math.max(0.0, Math.min(1.0, p));
        //System.out.println("Using local smoothing spline fit with p = " + p);
        if (p <= 0.0) {
            double[] coeff = linearLeastSquaresFit(x, y);
            UnivariateFunction fitFunction = new PolynomialFunction(coeff);
            Model model = new Model();
            model.kind = "least_squares_line";
            model.coeff = coeff;
            model.x = Arrays.copyOf(x, x.length);
            model.y = Arrays.copyOf(y, y.length);
            model.p = p;
            return new FitResult(fitFunction, model);
        }

        if (p >= 1.0) {
            PiecewisePolynomial pp = naturalCubicSplinePP(x, y);
            UnivariateFunction fitFunction = new PiecewisePolynomialFunction(pp);
            Model model = new Model();
            model.kind = "natural_cubic_interpolant";
            model.pp = pp;
            model.x = Arrays.copyOf(x, x.length);
            model.y = Arrays.copyOf(y, y.length);
            model.yfit = Arrays.copyOf(y, y.length);
            model.p = p;            
            return new FitResult(fitFunction, model);
        }

        double[] h = diff(x);
        for (double hi : h) {
            if (hi <= 0.0) {
                throw new IllegalArgumentException("x must be strictly increasing after duplicate removal.");
            }
        }

        double[][] Q = new double[n][n - 2];
        for (int k = 0; k < n - 2; k++) {
            Q[k][k] = 1.0 / h[k];
            Q[k + 1][k] = -1.0 / h[k] - 1.0 / h[k + 1];
            Q[k + 2][k] = 1.0 / h[k + 1];
        }

        double[] mainDiag = new double[n - 2];
        for (int i = 0; i < n - 2; i++) {
            mainDiag[i] = (h[i] + h[i + 1]) / 3.0;
        }
        double[] offDiag = new double[Math.max(0, n - 3)];
        for (int i = 0; i < offDiag.length; i++) {
            offDiag[i] = h[i + 1] / 6.0;
        }
        //System.out.println("Main diagonal of R: " + Arrays.toString(mainDiag));
        double[][] R = buildTridiagonalMatrix(mainDiag, offDiag);
        RealMatrix Qm = new Array2DRowRealMatrix(Q, false);
        RealMatrix Rm = new Array2DRowRealMatrix(R, false);
        DecompositionSolver rSolver = new CholeskyDecomposition(Rm, 1.0e-15, 1.0e-12).getSolver();
        RealMatrix invRQt = rSolver.solve(Qm.transpose());
        RealMatrix Kmat = Qm.multiply(invRQt);

        double[][] K = Kmat.getData();
        double[][] A = new double[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                A[i][j] = (i == j ? p : 0.0) + (1.0 - p) * K[i][j];
            }
            A[i][i] += 1.0e-12;
        }

        double[] b = new double[n];
        for (int i = 0; i < n; i++) {
            b[i] = p * y[i];
        }

        double[] yfit = solveLinearSystem(A, b);
        PiecewisePolynomial pp = naturalCubicSplinePP(x, yfit);
        UnivariateFunction fitFunction = new PiecewisePolynomialFunction(pp);

        Model model = new Model();
        model.kind = "local_penalized_natural_cubic_smoothing_spline";
        model.pp = pp;
        model.x = Arrays.copyOf(x, x.length);
        model.y = Arrays.copyOf(y, y.length);
        model.yfit = Arrays.copyOf(yfit, yfit.length);
        model.p = p;
        model.h = Arrays.copyOf(h, h.length);
        model.Q = copyMatrix(Q);
        model.R = copyMatrix(R);
        //System.out.println("Natural cubic spline fit complete with p = " + p);

        return new FitResult(fitFunction, model);
    }

    private static int[] createIndexOrder(double[] data) {
        Integer[] idx = new Integer[data.length];
        for (int i = 0; i < data.length; i++) {
            idx[i] = i;
        }
        Arrays.sort(idx, (a, b) -> Double.compare(data[a], data[b]));
        int[] order = new int[data.length];
        for (int i = 0; i < data.length; i++) {
            order[i] = idx[i];
        }
        return order;
    }

    private static double[] reorderArray(double[] array, int[] order) {
        double[] result = new double[array.length];
        for (int i = 0; i < array.length; i++) {
            result[i] = array[order[i]];
        }
        return result;
    }

    private static double[][] collapseDuplicates(double[] x, double[] y) {
        List<Double> xu = new ArrayList<>();
        List<Double> yu = new ArrayList<>();
        int i = 0;
        while (i < x.length) {
            double xi = x[i];
            double sum = y[i];
            int count = 1;
            int j = i + 1;
            while (j < x.length && Double.compare(x[j], xi) == 0) {
                sum += y[j];
                count++;
                j++;
            }
            xu.add(xi);
            yu.add(sum / count);
            i = j;
        }
        double[] xUnique = new double[xu.size()];
        double[] yUnique = new double[yu.size()];
        for (int k = 0; k < xu.size(); k++) {
            xUnique[k] = xu.get(k);
            yUnique[k] = yu.get(k);
        }
        return new double[][]{xUnique, yUnique};
    }

    private static double[] linearPolyFit(double[] x, double[] y) {
        if (x.length != 2) {
            throw new IllegalArgumentException("linearPolyFit only supports exactly two points.");
        }
        double slope = (y[1] - y[0]) / (x[1] - x[0]);
        double intercept = y[0] - slope * x[0];
        return new double[]{slope, intercept};
    }

    private static double[] linearLeastSquaresFit(double[] x, double[] y) {
        int n = x.length;
        double meanX = 0.0;
        double meanY = 0.0;
        for (int i = 0; i < n; i++) {
            meanX += x[i];
            meanY += y[i];
        }
        meanX /= n;
        meanY /= n;
        double sxx = 0.0;
        double sxy = 0.0;
        for (int i = 0; i < n; i++) {
            double dx = x[i] - meanX;
            sxx += dx * dx;
            sxy += dx * (y[i] - meanY);
        }
        double slope = sxx == 0.0 ? 0.0 : sxy / sxx;
        double intercept = meanY - slope * meanX;
        return new double[]{slope, intercept};
    }

    private static double[] diff(double[] x) {
        double[] d = new double[x.length - 1];
        for (int i = 0; i < x.length - 1; i++) {
            d[i] = x[i + 1] - x[i];
        }
        return d;
    }

    private static double[][] buildTridiagonalMatrix(double[] mainDiag, double[] offDiag) {
        int size = mainDiag.length;
        double[][] matrix = new double[size][size];
        for (int i = 0; i < size; i++) {
            matrix[i][i] = mainDiag[i];
            if (i < offDiag.length) {
                matrix[i][i + 1] = offDiag[i];
            }
            if (i > 0) {
                matrix[i][i - 1] = offDiag[i - 1];
            }
        }
        return matrix;
    }

    private static double[] solveLinearSystem(double[][] A, double[] b) {
        RealMatrix Am = new Array2DRowRealMatrix(A, false);
        RealVector bv = new ArrayRealVector(b, false);
        DecompositionSolver solver = new LUDecomposition(Am, 1.0e-15).getSolver();
        RealVector solution = solver.solve(bv);
        return solution.toArray();
    }

    private static PiecewisePolynomial naturalCubicSplinePP(double[] x, double[] y) {
        int n = x.length;
        if (n < 2) {
            throw new IllegalArgumentException("naturalCubicSplinePP requires at least two points.");
        }
        if (n == 2) {
            double h = x[1] - x[0];
            double slope = (y[1] - y[0]) / h;
            double[][] coefs = new double[1][4];
            coefs[0][0] = 0.0;
            coefs[0][1] = 0.0;
            coefs[0][2] = slope;
            coefs[0][3] = y[0];
            return new PiecewisePolynomial(Arrays.copyOf(x, x.length), coefs, 1, 4, 1);
        }

        double[] h = diff(x);
        for (double hi : h) {
            if (hi <= 0.0) {
                throw new IllegalArgumentException("x must be strictly increasing.");
            }
        }

        int m = n;
        double[][] A = new double[m][m];
        double[] rhs = new double[m];
        A[0][0] = 1.0;
        A[m - 1][m - 1] = 1.0;
        for (int i = 1; i < m - 1; i++) {
            A[i][i - 1] = h[i - 1];
            A[i][i] = 2.0 * (h[i - 1] + h[i]);
            A[i][i + 1] = h[i];
            rhs[i] = 6.0 * ((y[i + 1] - y[i]) / h[i] - (y[i] - y[i - 1]) / h[i - 1]);
        }

        double[] mValues = solveLinearSystem(A, rhs);
        double[][] coefs = new double[n - 1][4];
        for (int i = 0; i < n - 1; i++) {
            double hi = h[i];
            double a = (mValues[i + 1] - mValues[i]) / (6.0 * hi);
            double bCoef = mValues[i] / 2.0;
            double cCoef = (y[i + 1] - y[i]) / hi - hi * (2.0 * mValues[i] + mValues[i + 1]) / 6.0;
            double dCoef = y[i];
            coefs[i][0] = a;
            coefs[i][1] = bCoef;
            coefs[i][2] = cCoef;
            coefs[i][3] = dCoef;
        }
        //System.out.println("Piecewise polynomial coefficients: " + Arrays.deepToString(coefs));
        return new PiecewisePolynomial(Arrays.copyOf(x, x.length), coefs, n - 1, 4, 1);
    }

    private static double[][] copyMatrix(double[][] matrix) {
        double[][] copy = new double[matrix.length][];
        for (int i = 0; i < matrix.length; i++) {
            copy[i] = Arrays.copyOf(matrix[i], matrix[i].length);
        }
        return copy;
    }

    private static class PolynomialFunction implements UnivariateFunction {
        private final double[] coeff;

        public PolynomialFunction(double[] coeff) {
            this.coeff = Arrays.copyOf(coeff, coeff.length);
        }

        @Override
        public double value(double x) {
            //System.out.println("Evaluating polynomial at x = " + x);
            if (coeff.length == 2) {
                return coeff[0] * x + coeff[1];
            }
            double result = 0.0;
            for (int i = 0; i < coeff.length; i++) {
                result = result * x + coeff[i];
            }
            return result;
        }
    }

    private static class PiecewisePolynomialFunction implements UnivariateFunction {
        private final PiecewisePolynomial pp;

        public PiecewisePolynomialFunction(PiecewisePolynomial pp) {
            this.pp = pp;
        }

        @Override
        public double value(double xx) {
            //System.out.println("Evaluating piecewise polynomial at x = " + xx);
            int piece = locatePiece(pp.breaks, xx, pp.pieces);
            double x0 = pp.breaks[piece];
            double dx = xx - x0;
            double[] c = pp.coefs[piece];
            return ((c[0] * dx + c[1]) * dx + c[2]) * dx + c[3];
        }

        private int locatePiece(double[] breaks, double x, int pieces) {
            if (pieces == 1) {
                return 0;
            }
            int idx = Arrays.binarySearch(breaks, x);
            if (idx >= 0) {
                if (idx == breaks.length - 1) {
                    return pieces - 1;
                }
                return Math.min(idx, pieces - 1);
            }
            int bin = -idx - 2;
            if (bin < 0) {
                return 0;
            }
            if (bin >= pieces) {
                return pieces - 1;
            }
            return bin;
        }
    }
}
