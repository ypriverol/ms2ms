package org.ms2ms.apps;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Range;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.ms2ms.graph.Property;
import org.ms2ms.utils.Strs;
import org.ms2ms.utils.Tools;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

/**
 * Created with IntelliJ IDEA.
 * User: wyu
 * Date: 6/11/14
 * Time: 11:06 PM
 * To change this template use File | Settings | File Templates.
 */
abstract public class App
{
  public static final String KEY_THREAD   = "thread";
  public static final String KEY_WORKING  = "w";
  public static final String KEY_OUT      = "o";
  public static final String MODE_EXEC    = "exec";

  protected static BiMap<String, String> sParamKeys;

  protected Property                 mParameters;
  protected String                   mOutfileRoot, mWorkingRoot, mMode;
  protected boolean                  mVerbose;
  protected List<String>             mUsages;
  protected Config                   mConfig = ConfigFactory.load();
  //**************************************************************************
  // PRIVATE FIELDS
  //***-*********-*********-*********-*********-*********-*********-*********-
  protected String mAppName = "unTitled", mVersion, mBuild;

  static
  {
    sParamKeys = HashBiMap.create();

    sParamKeys.put(KEY_OUT,      "Output file");
    sParamKeys.put(KEY_THREAD,   "Number of concurrent threads");
    sParamKeys.put(KEY_WORKING,  "Working folder");
  }

  //--------------------------------------------------------------------------
  public int tryRun(String args[]) throws Exception
  {
    int exitStatus = 0;

    try
    {
      run(args);
    }
    catch (Throwable e)
    {
      e.printStackTrace();
      exitStatus = 1;
    }

    return(exitStatus);
  }

  public void run(String[] inArgs) throws Exception
  {
    processCommandLine(inArgs);

    Long msec0 = System.currentTimeMillis();
    // the actual process
    doRun();

    System.out.println("Duration (sec):" + Tools.d2s((System.currentTimeMillis() - msec0) * 0.001d, 2));
  }

  private boolean verbose() { return mVerbose; }

  protected void processCommandLine(String args[])
  {
    for (int i = 0; i < args.length; i++)
    {
      if (args[i].length() == 0 || args[i].charAt(0) != '-') continue;

      // Is the argument a command-line option (starts with a '-') ?
      if      (args[i].equals("-h") || args[i].equals("-help"))
      {
        usage();
        System.exit(0);
      }
      else if (args[i].equals("-d") || args[i].equals("-debug"))
      {
        setVerbose(true); // Turn verbose mode on
      }
      else if (args[i].equals("-x") || args[i].equals("-mode"))
      {
        mMode = args[i+1];
      }
      else if (args[i].equals("-v") || args[i].equals("-verbose"))
      {
        setVerbose(true); // Turn verbose mode on
      }
      else if (args[i].equals("-w") || args[i].equals("-working"))
      {
        mWorkingRoot = args[i+1];
      }
      else if (args[i].equals("-o") || args[i].equals("-outfile"))
      {
        mOutfileRoot = args[i+1];
      }
      else if (args[i].equals("-cfg") || args[i].equals("-config"))
      {
        readConfig(args[i+1]);
      }
    }
  }//--------------------------------------------------------------------------
  private void verboseMsg(String inMsg)
  {
    if (verbose())
    {
      System.out.println(inMsg);
    }
  }
  public Boolean isMode(String s) { return Strs.equals(s, mMode); }
  public void setVerbose(boolean inValue)
  {
    mVerbose = inValue;
  }

  protected void usage()
  {
    System.out.println("");
    System.out.println("USAGE FOR " + mAppName + ": " + mBuild + " [options]");
    System.out.println("\n    COMMAND-LINE OPTIONS:");

    if (Tools.isSet(mUsages))
      for (String u : mUsages)
        System.out.println("    " + u);
  }
  abstract protected boolean doRun() throws Exception;
//  abstract protected void    addProperty(String... vals);
  protected void addProperty(String... vals)
  {
    if (vals==null || vals.length<2) return;

    if (mParameters==null) mParameters = new Property();

    // check to make sure that name is recognized
    for (String key : sParamKeys.keySet())
      if (Strs.equalsIgnoreCase(vals[0], key) || Strs.equalsIgnoreCase(vals[0], sParamKeys.get(key)))
      {
        mParameters.setProperty(key, vals[1]);
      }
  }

  //  abstract public    String  getOutFile();
  public String getOutFile()
  {
    return Strs.isSet(mOutfileRoot)?mOutfileRoot:getWorkingRoot();
  }
  public String getWorkingRoot() { return (Strs.isSet(mWorkingRoot) ? mWorkingRoot:System.getProperty("user.dir"))+"/"; }
  public String getLogFile()     { return getOutFile().replaceAll("\\*","_")+".log"; }

  protected void close() throws IOException
  {
  }

  public String getAppName()         { return mAppName; }
  public void   setAppName(String s) { mAppName = s; }
  protected App addUsage(String tags, String usage)
  {
    if (mUsages==null) mUsages = new ArrayList<>();
    mUsages.add(tags + (Strs.isSet(usage) ? "\t:\t" + usage : ""));

    return this;
  }
  protected String option(String var, String usage, String[] args, int i, String... tags)
  {
    if (args[i].charAt(0)=='-' && Tools.isSet(args) && args.length>i+1 && Tools.isA(args[i], tags))
    {
      var=args[i+1];
      if (mUsages==null) mUsages = new ArrayList<>();
      mUsages.add(Strs.toString(tags, "\t") + (Strs.isSet(usage) ? "\t:\t" + usage : ""));
    }
    return var;
  }
  public String showParams(String... keys)
  {
    StringBuffer buf = new StringBuffer();
    buf.append("Param\tValue\tDescription\n");

    if (Tools.isSet(keys))
    {
      for (String key : keys)
        if (param(key)!=null) buf.append(key+"\t"+param(key) + "\t" + sParamKeys.get(key)+"\n");
    }
    else
    {
      for (String key : sParamKeys.keySet())
        if (param(key)!=null && sParamKeys.get(key)!=null)
          buf.append(key+"\t\t"+param(key) + "\t" + sParamKeys.get(key)+"\n");
    }

    return buf.toString();
  }
  public String   param( String s)            { return mParameters!=null?mParameters.getProperty(s):null; }
  public Double   param( String s, Double d)  { return mParameters!=null?mParameters.getProperty(s,d):null; }
  public Float    param( String s, Float d)   { return mParameters!=null?mParameters.getProperty(s,d):null; }
  public Integer  param( String s, Integer d) { return mParameters!=null?mParameters.getProperty(s,d):null; }
  public Boolean  param( String s, boolean d) { return mParameters!=null?(mParameters.getProperty(s)=="Y"):d; }
  public String[] params(String s, char t)    { return mParameters!=null?mParameters.getProperties(s,t):null; }
  public float[]  params(String s, char t, Float d)   { return mParameters!=null?mParameters.getFloats(s,t):null; }

  public Property param()                    { return mParameters; }

  protected void addParamKey(String... names)
  {
    if (names!=null && names.length>1) sParamKeys.put(names[0], names[1]);
  }
  protected App readConfig(String cfgname)
  {
    try
    {
      System.out.println("Reading the configuration: " + getWorkingRoot()+cfgname);
      BufferedReader cfg = null;
      try
      {
        cfg = new BufferedReader(new InputStreamReader(new FileInputStream(getWorkingRoot()+cfgname)));
        while (cfg.ready())
        {
          String line = cfg.readLine().trim();
          // ignore the comments
          if (line.indexOf("//")==0 || line.indexOf("#")==0) continue;

          if (line.indexOf(":")>0) addParamKey(Strs.split(line, ':', true));
          else                     addProperty(Strs.split(line, '=', true));
        }
      }
      finally {
        if (cfg!=null) cfg.close();
      }
    }
    catch (IOException ie)
    {
      ie.printStackTrace();
    }
    return this;
  }
  protected void logn()         { log("\n"); }
  protected void logn(String s) { log(s+"\n"); }
  protected void log( String s)
  {
    try
    {
      FileWriter w = null;
      try
      {
        w = new FileWriter(getLogFile(), true);
        w.write(s);
      }
      finally { if (w!=null) w.close(); }
    }
    catch (Exception e) { System.out.print(s); }
  }

//  protected Config option(Config config, String usage, String[] args, int i, String... tags)
//  {
//    if (args[i].charAt(0)=='-' && Tools.isSet(args) && args.length>i+1 && Tools.isA(args[i], tags))
//    {
//      var=args[i+1];
//      if (mUsages==null) mUsages = new ArrayList<>();
//      mUsages.add(Strs.toString(tags, "\t") + (Strs.isSet(usage) ? "\t:\t" + usage : ""));
//    }
//    return var;
//  }
}