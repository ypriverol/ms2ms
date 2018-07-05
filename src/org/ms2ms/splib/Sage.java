package org.ms2ms.splib;

import com.google.common.collect.Range;
import org.apache.commons.math3.analysis.UnivariateFunction;
import org.apache.commons.math3.analysis.interpolation.AkimaSplineInterpolator;
import org.apache.commons.math3.exception.OutOfRangeException;
import org.expasy.mzjava.stats.Histogram;
import org.ms2ms.algo.MsStats;
import org.ms2ms.utils.Tools;

import java.util.ArrayList;
import java.util.List;

/** Sage: Empirical Bayes?
 *
 * Date: 8/9/14
 * Time: 8:59 AM
 */
public class Sage extends AbstractSage
{
  private Range<Double> mBound;
  private Histogram     mPositives, mNegatives;

  private UnivariateFunction mTransition;
//  private double[]      mTransitXs, mTransitYs;

  public Sage() { super(); }
  public Sage(String title, Range<Double> bound, int bins)
  {
    super();
    mTitle=title; mBins=bins; mBound=bound;
    mPositives= MsStats.newHistogram("Positives", mBins, mBound);
    mNegatives= MsStats.newHistogram("Negatives", mBins, mBound);
  }

  public Histogram   getPositives()  { return mPositives; }
  public Histogram   getNegatives()  { return mNegatives; }
//  public double[]    getTransitXs()  { return mTransitXs; }
//  public double[]    getTransitYs()  { return mTransitYs; }

  public void addPositive(double data)
  {
    if (mPositives == null) mPositives= MsStats.newHistogram("Positives", mBins, mBound);
    mPositives.addData(data); mPosCounts++;
  }
  public void addNegative(double data)
  {
    if (mNegatives == null) mNegatives= MsStats.newHistogram("Negatives", mBins, mBound);
    mNegatives.addData(data); mNegCounts++;
  }

  public double lookup(double x)
  {
    if (mPositives == null || mNegatives == null)
      throw new RuntimeException("The positive and/or negative population not specified");

    if (mTransition==null) toTransition(false, null);

    try
    {
      return mTransition.value(x);
    }
    catch (OutOfRangeException oe)
    {
      return Double.NaN;
    }
//    if (mTransitYs==null || mTransitXs==null) toTransition(false);
//
//    if      (x>=Tools.back( mTransitXs)) return Tools.back( mTransitYs);
//    else if (x<=Tools.front(mTransitXs)) return Tools.front(mTransitYs);
//
//    return MsStats.interpolate(mTransitXs, mTransitYs, 0.5d, x)[0]; // ignore zero?
  }
  public double lookup(double x, double p_pos, double p_neg)
  {
    return lookup(x);
//    if (mPositives == null || mNegatives == null)
//      throw new RuntimeException("The positive and/or negative population not specified");
//
//    if (mTransition==null) toTransition(p_pos, p_neg, false);
//
//    return mTransition.value(x);
//    if (mTransitYs==null || mTransitXs==null) toTransition(p_pos, p_neg, false);
//
//    if      (x>=Tools.back( mTransitXs)) return Tools.back( mTransitYs);
//    else if (x<=Tools.front(mTransitXs)) return Tools.front(mTransitYs);
//
//    return MsStats.interpolate(mTransitXs, mTransitYs, 0.5d, x)[0]; // ignore zero?
  }
  private StringBuffer toTransition(boolean keep_zero, StringBuffer buf)
  {
    // calculate the priors from the positive and negative lists
//    mPosPrior = ((double )mPosCounts/(mPosCounts+mNegCounts));
//    mNegPrior = ((double )mNegCounts/(mPosCounts+mNegCounts));
    return toTransition(mPosPrior, mNegPrior, keep_zero, buf);
  }
  private StringBuffer toTransition(double p_pos, double p_neg, boolean keep_zero, StringBuffer buf)
  {
      if (mPositives == null || mNegatives == null)
        throw new RuntimeException("The positive and/or negative population not specified");

      mPosPrior=p_pos; mNegPrior=p_neg;

      List<Double> tX = new ArrayList<>(), tY = new ArrayList<>();

//      // normalize the frequency to the total area of 1
//      mPositives.normalize(new Histogram.Normalization(Histogram.Normalization.NormType.BINS_CUMUL, 1d));
//      mNegatives.normalize(new Histogram.Normalization(Histogram.Normalization.NormType.BINS_CUMUL, 1d));
//
      double xstep = (mBound.upperEndpoint()-mBound.lowerEndpoint()) / (mBins*2d), lowest=Double.MAX_VALUE,
          base_pos = mPosCounts/(mBins*2d), base_neg = mNegCounts/(mBins*2d); int ii=0;
      for (double x=mBound.lowerEndpoint(); x <= mBound.upperEndpoint(); x += xstep)
      {
        double pd_pos = mPositives.getAbsoluteBinFreq(mPositives.getBinIndex(x)) / base_pos,
               pd_neg = mNegatives.getAbsoluteBinFreq(mNegatives.getBinIndex(x)) / base_neg;

        if (buf!=null) buf.append(ii+"\t"+x+"\t"+Tools.d2s(pd_pos,3)+"\tPos\t"+base_pos+"\n");
        if (buf!=null) buf.append(ii+"\t"+x+"\t"+Tools.d2s(pd_neg,3)+"\tNeg\t"+base_neg+"\n");

        pd_pos = pd_pos < 0 ? 0 : pd_pos;
        pd_neg = pd_neg < 0 ? 0 : pd_neg;

        double p = (pd_pos == 0 && pd_neg == 0) ? 0d : pd_pos * p_pos / (pd_pos * p_pos + pd_neg * p_neg);

        if (buf!=null) buf.append(ii+"\t"+x+"\t"+p+"\tTransition\t"+lowest+"\n");

        if (!Double.isNaN(p))
        {
          tX.add(x); tY.add(p);
          if (p!=0 && p<lowest) lowest=p;
        }
      }
      if (!keep_zero)
        for (int i=0; i<tY.size(); i++)
          if (tY.get(i)==0) tY.set(i, lowest*0.1);

      mTransition = new AkimaSplineInterpolator().interpolate(Tools.toDoubleArray(tX), Tools.toDoubleArray(tY));
//    mTransitXs = Tools.toDoubleArray(tX); mTransitYs = Tools.toDoubleArray(tY);

    return buf;
    }
    public StringBuffer df()
    {
      StringBuffer buf = new StringBuffer();

      buf = toTransition(false, buf);
//      if (mTransitYs==null || mTransitXs==null) toTransition(false);
      // dump the pos and neg
//      for (int i=0; i<getPositives().size(); i++)
//        buf.append(i+"\t"+(i*getPositives().getBinWidth()+mBound.lowerEndpoint())+"\t"+getPositives().getRelativeBinFreq(i)+"\tPositive\n");
//      for (int i=0; i<getNegatives().size(); i++)
//        buf.append(i+"\t"+(i*getNegatives().getBinWidth()+mBound.lowerEndpoint())+"\t"+getNegatives().getRelativeBinFreq(i)+"\tNegative\n");
//
//      // lookup the transition
//      double step = (mBound.upperEndpoint()-mBound.lowerEndpoint())/100d;
//      for (int i=0; i<100; i++)
//      {
//        double rt = mBound.lowerEndpoint() + i*step;
//        buf.append(i+"\t"+rt+"\t"+lookup(rt)+"\tTransition\n");
//      }

      return buf;
    }
}
