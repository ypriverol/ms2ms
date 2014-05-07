package org.ms2ms.nosql;

import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.HTableInterface;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.util.Bytes;
import org.expasy.mzjava.core.ms.peaklist.DoublePeakList;
import org.expasy.mzjava.core.ms.peaklist.PeakList;
import org.expasy.mzjava.core.ms.spectrum.IonType;
import org.expasy.mzjava.core.ms.spectrum.Peak;
import org.expasy.mzjava.core.ms.spectrum.PeakAnnotation;
import org.ms2ms.alg.Peaks;

import java.io.*;
import java.util.UUID;

/** An Entity class for I/O with HBase database. Not to be used directly for data analysis
 *  Only expose the PeakList for public consumption
 *
 * Created by wyu on 4/18/14.
 */
public final class HBasePeakList implements Serializable
{
  private static final long serialVersionUID = 8472732522296541667L;

  static public String TBL_PEAKLIST  = "PeakList";
  static public String TBL_MSMSINDEX = "MsMsIndex";

  // Try to keep the ColumnFamily names as small as possible, preferably one character (e.g. "d" for data/default).
  // https://hbase.apache.org/book/rowkey.design.html
  static public String FAM_PRECURSOR = "p";
  static public String FAM_FLAG      = "f";
  static public String FAM_PROP      = "P";
  static public String FAM_ID        = "i";

  static public String COL_MZ        = "mz";
  static public String COL_Z         =  "z";
  //static final byte[] COL_AI        = "ai".getBytes();
  static public String COL_IONS      = "io";
  static public String COL_UUID      = "id";
  static public String COL_MMOD      = "mm"; // mass of the modification on the fragment
  static public String COL_SIG       = "sg"; // m/z of the signature fragment
  static public String COL_SNR       = "sr"; // m/z of the signature fragment

  private      int size,              // length of the peak list
                   cursor,            // current position of the peak
                   precursorZ;        // the precursor charge
  private double[] mzList;            // m/z of the peaks
  private  short[] intensityList;     // relative intensities of the peaks
  private   byte[] ppmList, flucList; // the variances of the peaks in m/z and intensity

  // the upper bound of the peak intensity and variance in m/z and intensity
  private float maxIntensity, maxPPM, maxFluc, precursorAi;
  private double precursorMz;         // the precursor charge

  public <A extends PeakAnnotation> HBasePeakList(PeakList<A> src)
  {
    super();
    // TODO copy the data from the source
    precursorMz =        src.getPrecursor().getMz();
    precursorAi =(float )src.getPrecursor().getIntensity();
    precursorZ  =        src.getPrecursor().getCharge();
    // the stats
    maxIntensity=(float )src.getBasePeakIntensity();
    size        =        src.size();

    mzList        = new double[src.size()];
    intensityList = new short[ src.size()];
    for (int i=0; i<src.size(); i++)
    {
      mzList[       i]=src.getMz(i);
      intensityList[i]=(short )(src.getIntensity(i)*Short.MAX_VALUE/maxIntensity+Short.MIN_VALUE);
    }
  }

  /** compression and uncompression routines  **/
  private double getMz(int i)
  {
    rangeCheck(i); return mzList[i];
  }
  private double getIntensity(int i)
  {
    rangeCheck(i);
    return maxIntensity * (intensityList[i]-Short.MIN_VALUE)/(2d*Short.MAX_VALUE);
  }

  private void rangeCheck(int index)
  {
    if (index >= size) throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + size);
  }

  public <A extends PeakAnnotation> DoublePeakList<A> toPeakList()
  {
    DoublePeakList<A> peaks = new DoublePeakList<A>(size);
    // initialize the content of the new list
    peaks.setPrecursor(new Peak(precursorMz, precursorAi, precursorZ));
    for (int i=0; i<size; i++)
    {
      peaks.add(getMz(i), getIntensity(i));
    }

    return peaks;
  }
  public <A extends PeakAnnotation> PeakList<A> toPeakList(PeakList<A> peaks)
  {
    peaks.clear();
    // initialize the content of the new list
    peaks.setPrecursor(new Peak(precursorMz, precursorAi, precursorZ));
    for (int i=0; i<size; i++)
      peaks.add(getMz(i), getIntensity(i));

    return peaks;
  }

  public static HBasePeakList fromBytes(byte[] s)
  {
    try
    {
      ByteArrayInputStream bai = new ByteArrayInputStream(s);
      ObjectInputStream     in = new ObjectInputStream(bai);
      HBasePeakList          e = (HBasePeakList) in.readObject();
      in.close(); bai.close();
      return e;
    }
    catch(IOException i)
    {
      i.printStackTrace();
    }
    catch(ClassNotFoundException c)
    {
      System.out.println("Employee class not found");
      c.printStackTrace();
    }
    return null;
  }
  public static byte[] toBytes(HBasePeakList s)
  {
    ByteArrayOutputStream bao = new ByteArrayOutputStream();
    try
    {
      ObjectOutputStream out = new ObjectOutputStream(bao);
      out.writeObject(s);
      out.close(); bao.close();
    }
    catch (IOException e)
    { throw new RuntimeException("Error during persistence", e); }

    return bao.toByteArray();
  }

  public static void save(HTableInterface tbl, PeakList spec) throws IOException
  {
    if (spec==null) return;

    Put row = new Put(row4PeakList(spec.getId()));
    // byte[] family, byte[] qualifier, byte[] value
    row.add(Bytes.toBytes(FAM_PRECURSOR), Bytes.toBytes(COL_MZ), Bytes.toBytes(spec.getPrecursor().getMz()));
    row.add(Bytes.toBytes(FAM_PRECURSOR), Bytes.toBytes(COL_Z),  Bytes.toBytes(spec.getPrecursor().getCharge()));
    // create the peaks bytes
    row.add(Bytes.toBytes(FAM_PROP),      Bytes.toBytes(COL_IONS), toBytes(new HBasePeakList(spec)));

    tbl.put(row);
  }
  public static HBasePeakList getPeakList(HTableInterface tbl, UUID id) throws IOException
  {
    Get g = new Get(Bytes.toBytes(id.toString()));
    Result r = tbl.get(g);
    byte[] value = r.getValue(Bytes.toBytes(FAM_PROP), Bytes.toBytes(COL_IONS));
    HBasePeakList peaks = HBasePeakList.fromBytes(value);

    return peaks;
  }
  public static byte[] row4PeakList(UUID id) { return Bytes.toBytes(id.toString()); }
  public static byte[] row4MsMsIndex(float mz, byte z)
  {
    // tag the system time in nanosec to ensure unique row key
    return Bytes.add(Bytes.toBytes(mz), new byte[] {z}, Bytes.toBytes(System.nanoTime()));
  }
  public static byte[] row4MsMsIndex(double mz, int z)
  {
    return row4MsMsIndex((float )mz, (byte )z);
  }
}