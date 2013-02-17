/**
 * 
 */
package cn.edu.tsinghua.timeseries;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import javax.media.jai.PlanarImage;
import javax.media.jai.iterator.RandomIter;
import javax.media.jai.iterator.RandomIterFactory;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import mr.go.sgfilter.SGFilter;

import org.apache.commons.math.FunctionEvaluationException;
import org.apache.commons.math.analysis.UnivariateRealFunction;
import org.apache.commons.math.complex.Complex;
import org.apache.commons.math.stat.StatUtils;
import org.apache.commons.math.stat.descriptive.moment.VectorialCovariance;
import org.apache.commons.math.transform.FastFourierTransformer;

import JSci.awt.DefaultGraph2DModel;
import JSci.awt.Graph2D;
import JSci.awt.DefaultGraph2DModel.DataSeries;
import JSci.swing.JLineGraph;

import cn.edu.tsinghua.gui.TSDisplayer;
import cn.edu.tsinghua.gui.TimeSeriesViewer;

import com.berkenviro.gis.GISUtils;
import com.berkenviro.imageprocessing.ArrayFunction;
import com.berkenviro.imageprocessing.SplineFunction;

import ru.sscc.spline.Spline;
import ru.sscc.spline.analytic.GSplineCreator;
import ru.sscc.spline.polynomial.POddSplineCreator;
import ru.sscc.spline.polynomial.PSpline;
import ru.sscc.util.CalculatingException;
import ru.sscc.util.data.DoubleVectors;
import ru.sscc.util.data.RealVectors;

/**
 * @author Nicholas Clinton
 * Utility class for getting time series from images, fitting curves, etc.
 * 20121113 All methods converted to double[][] inputs and/or generic spline inputs.
 */
public class TSUtils {
	
	/**
	 * 
	 * @param series
	 * @param width
	 * @param degree
	 * @return
	 */
	public static double[][] sgSmooth(double[][] series, int width, int degree) {
		double[] padded = new double[series[1].length + 2*width];
		for (int i=0; i<padded.length; i++) {
			if (i < width) {
				padded[i] = series[1][0];
			}
			else if(i >= series[1].length + width) {
				padded[i] = series[1][series[1].length-1];
			}
			else {
				padded[i] = series[1][i-width];
			}
		}
		SGFilter filter = new SGFilter(width, width);
		double[] sgCoeffs = SGFilter.computeSGCoefficients(width, width, degree);
		// smooth
		double[] smooth = filter.smooth(padded, sgCoeffs);
		double[] out = new double[series[1].length];
		for (int i=0; i<out.length; i++) {
			out[i] = smooth[i+width];
		}
		return new double[][] {series[0], out};
	}
	
	
	/**
	 * Smooth a series with a low-pass filter on a Fourier transform
	 * @param spline
	 * @param min
	 * @param max
	 * @param p the proportion of frequency components to REMOVE.
	 * @return
	 */
	public static double[][] smoothFunction(final Object spline, double min, double max, double p) {
		double[] smooth = null;
		int n = 128;
		FastFourierTransformer fft = new FastFourierTransformer();
		try {
			// transform
			Complex[] transform = fft.transform(new UnivariateRealFunction() {
				public double value(double x) throws FunctionEvaluationException {
					return TSUtils.value(spline, x);
				}
			}, min, max, n);
			// symmetric series, keep the zero-frequencies on the ends
			int keep = (int)(n*(1.0-p))/2;
			System.out.println(keep);
			for (int c=0; c<transform.length; c++) {
				if (c>keep-1 && c<n-keep) {
					// blast the high frequency components
					transform[c] = Complex.ZERO;
				}
				else {
					//transform[c] = transform[c].add(Complex.ONE);
				}
			}
			// invert
			Complex[] smoothed = fft.inversetransform(transform);
			// get the real part
			smooth = new double[smoothed.length];
			for (int i=0; i<smooth.length; i++) {
				smooth[i] = smoothed[i].getReal();
			}
		} catch (FunctionEvaluationException e) {
			e.printStackTrace();
		} catch (IllegalArgumentException e) {
			e.printStackTrace();
		}
		// x values
		double s[] = new double[n];
        double h = (max - min) / n;
        for (int i = 0; i < n; i++) {
            s[i] = min + i * h;
        }
		return new double[][] {s, smooth};
	}
	
	/**
	 * Read out a list into an array.
	 * @param series is a List<double[]>
	 * @return a double[2][series.size()]
	 */
	public static double[][] getSeriesAsArray(List<double[]> series) {
		double[][] xy = new double[2][series.size()];
		double[] y = new double[series.size()];
		for (int t=0; t<series.size(); t++) {
			xy[0][t] = series.get(t)[0];
			xy[1][t] = series.get(t)[1];
		}
		return xy;
	}
	
	/**
	 * Get a thin-plate, or Duchon spline as created by JSpline.
	 * @param series as a list of double[] where each double[] is {x,y}
	 * @return the spline
	 */
	public Spline getThinPlateSpline(List<double[]> series) {
		double[][] xy = getSeriesAsArray(series);
		return duchonSpline(xy[0], xy[1]);
	}
	
	/**
	 * Get a third-order polynomial spline as implemented by Commons Math.
	 * @param series as a list of double[] where each double[] is {x,y}
	 * @return the spline
	 */
	public SplineFunction getPolySpline(List<double[]> series) {
		return new SplineFunction(getSeriesAsArray(series));
	}
	
	/*
	 * Like gis.Utils.pixelValue(), except takes image coords and returns an
	 * array of data from each band.
	 */
	public static double[] imageValues(int pixelX, int pixelY, PlanarImage image) {
		double[] pixelVals = new double[image.getNumBands()];
		RandomIter iterator = null;
		iterator = RandomIterFactory.create(image, null);
		for (int b=0; b<image.getNumBands(); b++) {
			pixelVals[b] = iterator.getSampleDouble(pixelX,pixelY,b);
			//pixelVals[b] = iterator.getSample(pixelX,pixelY,b);
		}
		return pixelVals;
	}
	
	/**
	 * 
	 * @param x
	 * @param y
	 * @return a thin-plate spline as fitted by JSpline
	 */
	public static Spline duchonSpline(double[] x, double[] y) {
		// data type conversion
		RealVectors xPts = new DoubleVectors(1, x.length);
        for (int i=0; i<xPts.size; i++) {
        	xPts.set(i,0,x[i]);
        }
        
        Spline spline = null;
        try {
        	// the pseudo-quadratic RBF w/ polynomial kernel of 1st degree 
			spline = GSplineCreator.createSpline(2, xPts, y);
		} catch (CalculatingException e) {
			e.printStackTrace();
			System.out.println("length: "+x.length);
			for (int i=0; i<x.length; i++) {
				System.out.println(x[i]+","+y[i]);
			}
		}
		return spline;
	}
	
	/**
	 * 
	 * @param x
	 * @param y
	 * @param degree
	 * @return
	 */
	public static Spline polynomialSpline(double[] x, double[] y, int degree) {
		Spline spline = null;
		try {
			// see ru.sscc.spline.polynomial.POddSplineCreator for description
			spline = (new POddSplineCreator()).createSpline(degree, x, y);
		} catch (CalculatingException e) {
			e.printStackTrace();
		}
		return spline;
	}
	
	/**
	 * 
	 * @param spline
	 * @param x
	 * @return
	 */
	public static double[] evaluateSpline(Object spline, double[] x) throws Exception {
		double[] y = new double[x.length];
        for (int j=0; j<x.length; j++) {
        	y[j] = value(spline, x[j]);
        	if (spline instanceof Spline) {
        		y[j] = ((Spline)spline).value(x[j]);
    		}
        }
        return y;
	}
	
	/**
	 * 
	 * @param spline
	 * @param xRange
	 * @return an array of 100 equally spaced points
	 * @throws Exception
	 */
	public static double[][] splineValues(Object spline, double[] xRange) throws Exception {
		// generate 100 points in the provided range
		double[][] splineVals = new double[2][100];
        double step = (xRange[1] - xRange[0])/100.0;
        // starting point
        double x = xRange[0];
        for (int j=0; j<100; j++) {
        	splineVals[0][j] = x;
        	splineVals[1][j] = value(spline, x);
        	System.out.print(splineVals[1][j]+",");
        	x += step;
        }
        System.out.println();
        return splineVals;
	}
	
	/**
	 * The spline can be a SplineFunction (Commons Math) or a JSpline.
	 * @param spline
	 * @param x
	 * @return
	 * @throws Exception
	 */
	public static double value(Object spline, double x) throws FunctionEvaluationException {
		if (spline instanceof Spline) {
    		return ((Spline)spline).value(x);
		}
    	else if (spline instanceof SplineFunction) {
    		return ((SplineFunction)spline).value(x);
    	}
    	else if (spline instanceof ArrayFunction) {
    		return ((ArrayFunction)spline).value(x);
    	}
    	else {
    		throw new FunctionEvaluationException(x);
    	}
	}

	/**
	 * Uses the bisection method to find roots.  Input values should
	 * bracket a root, or else an endpoint will be returned.
	 * @param spline
	 * @param xRange
	 * @return
	 */
	public static Double root(Object spline, double[] xRange) throws Exception {
		double mid;
		double x1 = xRange[0];
		double x2 = xRange[1];	
		while (Math.abs(x2-x1) > 0.000000001) {
			mid = (x1 + x2)/2.0;
			
			if ( value(spline, x1)*value(spline, mid) < 0 ) {
				x2 = mid;
			} else { 
				x1 = mid;
			} 
		}
		return new Double((x1 + x2)/2.0);
	}
	

	/**
	 * Find all the maxima and minima of a Spline by finding the roots
	 * of its derivative.  Return a list of Extrema in chronological order.
	 * @param deriv
	 * @param bounds
	 * @return
	 * @throws Exception
	 */
	public static List<Extremum> getExtrema(Object deriv, double[] bounds) throws Exception {
		
		List<Extremum> extrema = new LinkedList<Extremum>();
		Extremum extremum = null;
		
		double[][] dVals = splineValues(deriv, bounds);
		// the second derivative is used to characterize the extrema
		Spline pSpline = TSUtils.polynomialSpline(dVals[0], dVals[1], 1);
        Spline secondDeriv = PSpline.derivative(pSpline);
		
        // scan over the derivative, find roots
		for (int t=1; t<dVals[0].length; t++) {
			
			Double root = null;
			// if there's a root in there, find it
			if (dVals[1][t-1]*dVals[1][t] < 0) {
				double[] range = {dVals[0][t-1], dVals[0][t]};
				root = root(deriv, range);
			}
			if (root != null) {
				// determine the type of root
				if (secondDeriv.value(root.doubleValue()) < 0) {
					extremum = new Extremum(root.doubleValue(), Extremum.EXTREMUM_TYPE_MAX);
				}
				else if (secondDeriv.value(root.doubleValue()) > 0) {
					extremum = new Extremum(root.doubleValue(), Extremum.EXTREMUM_TYPE_MIN);
				}
				extrema.add(extremum);
			}
		}
		return extrema;
	}
	
	/**
	 * TODO: Add absolute maximum and minimum?
	 * This method to evaluate the list of extrema and return a double[] that
	 * corresponds to {firstMinX, firstMinY, firstMaxX, firstMaxY, 
	 * secondMinX, secondMinY, secondMaxX, secondMaxY}
	 * @param extrema
	 * @param spline
	 * @param bounds
	 * @return
	 */
	public static double[] evaluateExtrema(List<Extremum> extrema, Object spline, double[] bounds) throws Exception {
		Extremum[] eArray = new Extremum[extrema.size()];
		Extremum e;
		// iterate over the List, write into the Array
		Iterator<Extremum> iter = extrema.iterator();
		int count = 0;
		while (iter.hasNext()) {
			e = iter.next();
			//System.out.println("Reading extremum "+count+": "+e.toString());
			eArray[count] = e;
			count++;
		}
		
		// initialize this way for debugging
		double firstMinX = -9999;
		double firstMinY = -9999;
		double firstMaxX = -9999;
		double firstMaxY = -9999;
		double secondMinX = -9999;
		double secondMinY = -9999;
		double secondMaxX = -9999;
		double secondMaxY = -9999;
		double range = -9999;
		double range1 = 0;
		double range2 = 0;

		// special cases:
		if (eArray.length == 0) {  // error condition
			System.err.println("Error: no extrema!");
		}
		else if (eArray.length == 1) {  // one extrema
			// compute the range from the endpts 
			if (eArray[0].getType() == Extremum.EXTREMUM_TYPE_MAX) {
				firstMinX = bounds[0];
				firstMaxX = eArray[0].getX();
			}
			else { // it's convex
				// just keep the initialization values
				//firstMinX = eArray[0].getX();
				//firstMaxX = bounds[1];
				System.err.println("Error: curve is convex!");
			}
		}
		else if (eArray.length == 2) { // a max and a min
			// if the first extrema is a min, return the min->max slope
			if (eArray[0].getType() == Extremum.EXTREMUM_TYPE_MIN) {
				firstMinX = eArray[0].getX();
				firstMaxX = eArray[1].getX();
			}
			// it does this: /\_/  check the ends, return the larger
			else {
				if ( (value(spline, eArray[0].getX()) - value(spline, bounds[0])) > 
					 (value(spline, bounds[1])) - value(spline, eArray[1].getX()) ) {
					firstMinX = bounds[0];
					firstMaxX = eArray[0].getX();
				}
				else {
					firstMinX = eArray[1].getX();
					firstMaxX = bounds[1];
				}
			}

		}
		else { // more than two extrema
			for (int i=0; i<eArray.length; i++) {
				double x1 = 0, x2 = 0;
				// use a minimum -> maximum combination
				if (eArray[i].getType() == Extremum.EXTREMUM_TYPE_MIN && i != eArray.length-1) {
					x1 = eArray[i].getX();
					x2 = eArray[i+1].getX();
				}
				// use the first part of the curve if a first max
				else if (eArray[i].getType() == Extremum.EXTREMUM_TYPE_MAX && i == 0) {
					//System.err.println("Warning: first extrema is a max.");
					x1 = bounds[0];
					x2 = eArray[i].getX();
				}
				// compute the range
				double y1 = (value(spline, x1) < 0) ? 0 : value(spline, x1);
				double y2 = (value(spline, x2) < 0) ? 0 : value(spline, x2);
				range = y2 - y1;
				// debugging
				if (range < 0) { // error condition
					System.err.println("Error: range is negative!");
					System.out.println("When checking: "+eArray[i+1].toString()+" - "+eArray[i].toString());
					System.out.println("y2= "+y2+" , y1= "+y1);
				}
				if (range > range1) {
					range1 = range;
					firstMinX = x1;
					firstMaxX = x2;
				}
				else {
					if (range > range2) {
						range2 = range;
						secondMinX = x1;
						secondMaxX = x2;
					}
				}
			}
		}
		// if x != initialization value, -9999
		if (firstMinX >= 0 ) {
			// if the spline value is below 0.0, set to 0.0
			firstMinY = (value(spline, firstMinX) < 0.0) ? 0.0 : value(spline, firstMinX);		 
		}
		if (firstMaxX >= 0) {
			firstMaxY = (value(spline, firstMaxX) < 0.0) ? 0.0 : value(spline, firstMaxX);
		}
		if (secondMinX >= 0) {
			secondMinY = (value(spline, secondMinX) < 0.0) ? 0.0 : value(spline, secondMinX);
		}
		if (secondMaxX >= 0) {
			secondMaxY = (value(spline, secondMaxX) < 0.0) ? 0.0 : value(spline, secondMaxX);
		}
		
		return new double[] {firstMinX, firstMinY, firstMaxX, firstMaxY, secondMinX, secondMinY, secondMaxX, secondMaxY};	
	}
	
	
	/**
	 * Implement according to White et al. 1997.
	 * @param series
	 * @param bounds
	 * @return the t value of the first time the series exceeds the VI ratio.
	 * @throws Exception
	 */
	public static double greenUpWhite(double[][] series, double[] bounds) throws Exception  {
		double[][] ratioVals = ndviRatio(series, bounds);
		// scan the series values for the first time ratio>0.5
		for (int i=0; i<ratioVals[0].length; i++) {
			if (ratioVals[1][i] > 0.5) {
				return ratioVals[0][i];
			}
		}
		// error condition:
		return -9999;
	}
	

	/**
	 * Calculates an array of NDVIratio as defined in White et al. 1997
	 * @param series
	 * @param bounds
	 * @return
	 * @throws Exception
	 */
	public static double[][] ndviRatio(double[][] series, double[] bounds) throws Exception {
		double min = StatUtils.min(series[1]); // min of y
		double max = StatUtils.max(series[1]); // max of y
		double[][] ratioVals = new double[2][series[0].length];
		for (int i=0; i<series[0].length; i++) {
			// copy the x coordinate
			ratioVals[0][i] = series[0][i];
			// compute the ratio
			ratioVals[1][i] = (series[1][i] - min) / (max - min);
		}
		return ratioVals;
	}
	
	
	/**
	 * Implement according to Reed et al. 1994.  Moving window of 42 (?? legacy).
	 * @param series
	 * @param bounds
	 * @param width
	 * @return
	 * @throws Exception
	 */
	public static double greenUpReed(double[][] series, double[] bounds, int width) throws Exception {
		
		// the second derivative is used to beginning of the curve
		Spline pSpline = TSUtils.polynomialSpline(series[0], series[1], 1);
        Spline deriv = PSpline.derivative(pSpline);
		
        // get the running mean and start checking it.
		double[][] meanVals = runningMean(series, width);
		// modify Reed's definition slightly as follows:
        double xStart = 0;
		if (deriv.value(series[0][1]) < 0) { // decreasing, scan from next minimum
			List extrema = getExtrema(deriv, bounds);
			Extremum e;
			for (int i=0; i<extrema.size(); i++) {
				// if this is a minima, start here
				e = (Extremum)extrema.get(i);
				if (e.getType() == Extremum.EXTREMUM_TYPE_MIN) {
					xStart = e.getX();
					break;
				}
			}
		}
		// increasing, but already above the mean, scan from next minimum
		else if (deriv.value(series[0][1]) > 0 && series[1][0] > meanVals[1][0]) {
			List extrema = getExtrema(deriv, bounds);
			Extremum e;
			for (int i=0; i<extrema.size(); i++) {
				// if this is a minima, start here
				e = (Extremum)extrema.get(i);
				if (e.getType() == Extremum.EXTREMUM_TYPE_MIN) {
					xStart = e.getX();
					break;
				}
			}
		}
		else { // increasing, scan from here
			xStart = series[0][1];
		}
		// simply scan the whole thing, checking the xStart
		double greenUp = -9999;
		for (int i=0; i<series[0].length; i++) {
			// if not checking yet, keep going
			if (series[0][i] < xStart) { continue; }
			// if the mean is greater than the series, keep going
			if (series[1][i] < meanVals[1][i]) { 
				continue;
			// otherwise, it's in the last interval, return the midpoint
			} else {
				if (i != 0) {
					greenUp = (series[0][i] + series[0][i-1])/2.0;
					break;
				}
				else {
					greenUp = series[0][i];
				}
			}	
		}
		return greenUp;
	}
	

	/**
	 * Return an array of the running average, truncated at the ends
	 * @param series
	 * @param width of the mean window in array-units
	 * @return 
	 * @throws Exception
	 */
	public static double[][] runningMean(double[][] series, int width) throws Exception {

		double[][] meanVals = new double[2][series[0].length];
		int start = -1;
		int end = -1;
		int count = 0;
		int halfWidth = width/2;
		double meanSum;
		double mean;

		for (int i=0; i<series[0].length; i++) {
			// copy the x coordinate
			meanVals[0][i] = series[0][i];
			
			// figure out the range over which to average
			if (i-halfWidth < 0) { // start
				start = 0;
			}
			else {
				start = i-halfWidth;
			}
			if (i+halfWidth > series[1].length-1) { // end
				end = series[1].length-1;
			}
			else {
				end = i+halfWidth;
			}
			
			// compute the mean
			count = 0;
			meanSum = 0;
			for (int j=start; j<end; j++) {
				meanSum += series[1][j];
				count++;
			}
			mean = meanSum/(double)count;
			meanVals[1][i] = mean;
		}
		return meanVals;
	}
	
	
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		String dir1 = "D:/MOD13A2/2010/";
		String dir2 = "D:/MOD13A2/2011/";
		try {
			ImageLoadr2 loadr = new ImageLoadr2(new String[] {dir2, dir1});
			List<double[]>pixelValues = loadr.getSeries(GISUtils.makePoint(-83.1438, 9.594));
			final double[][] series = TSUtils.getSeriesAsArray(pixelValues);
			// splines on the original data, un-scaled
			final Spline dSpline = TSUtils.duchonSpline(series[0], series[1]);
			double[] minMax = {StatUtils.min(series[0]), StatUtils.max(series[0])};
			final double[][] smooth1 = TSUtils.sgSmooth(series, 5, 2);
			final double[][] smooth2 = TSUtils.smoothFunction(dSpline, minMax[0], minMax[1], 0.7);
			final double[][] smooth3 = TSUtils.smoothFunction(dSpline, minMax[0], minMax[1], 0.05);

			SwingUtilities.invokeLater(new Runnable() {
	            public void run() {
	            	TSDisplayer disp = new TSDisplayer(series);
	            	disp.graphSeries();
	            	disp.graphSpline(dSpline, true);
	            	disp.graphSeries(smooth1);
	            	//disp.graphSeries(smooth2);
	            	disp.graphSeries(smooth3);

	            }
	        });
	        
	        
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

}

