/* GenericMemory.java - hades.models.rtl.GenericMemory
 *
 * 09.07.03 - support InstructionDecoder
 * 20.09.01 - use DesignManager.getInputStream
 * 20.08.01 - use NameMangler, added needs/getExternalResources
 * 17.01.00 - parser improvements (accept comments etc.)
 * 30.11.99 - start with null resourcename
 * 26.11.99 - use getVersionId() loadButton.actionPerformed to make javac happy
 * 28.07.99 - added getPropertySheet()
 * 09.07.99 - rewrote save() and parse() to support memory width != 8
 * 09.06.99 - use ValidName to encode/decode resource names
 * 03.06.99 - use DesignManager.getRAS
 * 29.08.98 - added isConnected() method
 * 26.08.98 - first version
 *
 * (C) F.N.Hendrich, hendrich@informatik.uni-hamburg.de
 */ 

package gshare;

import gshare.*;
import hades.gui.PropertySheet;
import  hades.models.*;
import  hades.models.memory.*;
import  hades.simulator.*;
import  hades.utils.StringTokenizer;
import  hades.utils.NameMangler;

import  jfig.utils.SetupManager;

import  java.awt.*;       // needed for config dialog
import  java.io.*;
import  java.util.Hashtable;
import  java.util.Enumeration;


/**
 * GenericMemory - the base class for all Hades RTLIB memory components.
 *
 * Currently, the word size of the memory is limited to 63 bits.
 * <p>
 *
 * @author  F.N.Hendrich
 */
public class  GenericMemory 
       extends  SimObject 
       implements  hades.models.memory.Memory {

  //String   resourcename= "/hades/models/rtlib/memory/GenericMemory.rom";
  protected String resourcename = null;


  protected int    n_words, n_bits;
  protected long   data[];
  protected long   bit_mask;

  protected long   last_read_data, last_write_data;
  protected int    last_read_addr, last_write_addr;

  protected  StdLogicVector     vector_UUU, vector_XXX, vector_ZZZ,
                                vector_000, vector_111;

  protected boolean  enableAnimationFlag = true;


  public final static int  UNDEFINED  = -1;
  public final static int  TRISTATED  = -2;



  private   Hashtable        _listenerTable;  // the old variant
  private   MemoryListener   _listenerArray[];  
  private   int              _listenerCount;

  private   InstructionDecoder  _decoder;



  public GenericMemory() {
    super();
    n_bits  = 18; 
    n_words = 18;

    constructStandardValues();
    createMemory();
    initializeWithZeroes();

    enableAnimationFlag = SetupManager.getBoolean(
                            "Hades.LayerTable.RtlibAnimation", false );
  }




  /**
   * we use one external resource: the data file to initialize the memory.
   */
  public boolean needsExternalResources() {
    return true;
  }


  /**
   * return a String[] array of length 1 with our memory initialization file.
   */
  public String[] getExternalResources() {
    String[] externals = new String[1];
    externals[0] = resourcename;
    return externals;
  }


  protected void constructPorts() {
    message( "-E- Internal: don't call constructPorts() on a GenericMemory!" );
  }


  protected void constructStandardValues() {
    vector_UUU = new StdLogicVector(n_bits, Const1164.__U );
    vector_XXX = new StdLogicVector(n_bits, Const1164.__X );
    vector_ZZZ = new StdLogicVector(n_bits, Const1164.__Z );
    vector_000 = new StdLogicVector(n_bits, Const1164.__0 );
    vector_111 = new StdLogicVector(n_bits, Const1164.__1 );

    bit_mask   = vector_111.getBitMask();

    last_read_addr  = -1;
    last_read_data  = -1;

    last_write_addr = -1;
    last_write_data = -1;
  }


  public boolean getEnableAnimationFlag() {
    return enableAnimationFlag;
  }

  public void setEnableAnimationFlag( boolean b ) {
    enableAnimationFlag = b;
  }

  public void setEnableAnimationFlag( String s ) {
    try {
      setEnableAnimationFlag( s.trim().toLowerCase().startsWith( "t" ) );
    }
    catch( Exception e ) {
      message( "-E- illegal value for setEnableAnimationFlag, using false" );
      setEnableAnimationFlag( false );
    }
  }

  public void setInstructionDecoder( InstructionDecoder decoder ) {
    _decoder = decoder;
  }

  public InstructionDecoder getInstructionDecoder() {
    return _decoder;
  }



  public boolean isConnected() {
    if (ports == null) return false;
    else {
      for( int i=0; i < ports.length; i++ ) {
        if (ports[i].getSignal() != null) return true;
      }
    }
    return false;
  }


  public void createMemory() {
    data = new long[n_words];
  }

 
  public boolean dataAtAddressIsUndefined( int addr ) {
    if (data == null) createMemory();
    if (addr < 0 || addr >= data.length) return true;
    else                                 return (data[addr] == UNDEFINED);
  }


  public void initializeWithZeroes() {
    if (data == null) createMemory();
    for( int i=0; i < n_words; i++ ) {
      data[i] = 0;
    }
  }

  public void initializeWithDefaultValues() {
    initializeWithZeroes();
  }

  public void initializeWithX() {
    if (data == null) createMemory();
    for( int i=0; i < n_words; i++ ) {
      data[i] = UNDEFINED;
    }
  }

  public void initializeWithRandomValues() {
    if (data == null) createMemory();
    for( int i=0; i < n_words; i++ ) {
      
      data[i] = Double.doubleToLongBits(Math.random()) & bit_mask;
    }
    dbg( "-I- initializeWithRandomValues ok." );
  }





  public String getResourcename() { 
    return resourcename;
  }

  public void setResourcename( String s ) {
    resourcename = s;
  }


 
  public boolean initialize( String s ) {
    resourcename = "";
    try {
      StringTokenizer st = new StringTokenizer( s );
      int n_tokens = st.countTokens();

      versionId    = Integer.parseInt( st.nextToken() );
      n_words      = Integer.parseInt( st.nextToken() );
      n_bits       = Integer.parseInt( st.nextToken() );

      createMemory();
      constructStandardValues();
      constructPorts();

      if (n_tokens > 3) {
        resourcename = NameMangler.decodeUnicodeEscapes(st.nextToken());
        parseRAM( resourcename ); 
      }
      else {
        initializeWithX();
      }
    }
    catch( Exception e ) {
      message( "-E- " + toString() + ".initialize(): " + e );
      message( "-E- offending input is '" + s + "'" );
      jfig.utils.ExceptionTracer.trace( e );
    }
    return true;
  }


  public void write( java.io.PrintWriter ps ) {
    String s =  " " + versionId 
              + " " + n_words 
              + " " + n_bits;
    if (resourcename != null) {
        s = s + " " + NameMangler.encodeWithUnicodeEscapes(resourcename);
    }
    ps.print( s );
  }



  public int getSize() { 
    return data.length;
  }

  public void setSize( int n_words ) throws Exception {
    throw new Exception( "Cannot change the GenericMemory size!" );
  }

  public boolean resize( int n_words, int n_bits_per_word ) throws Exception {
    throw new Exception( "Cannot change the GenericMemory size!" );
  }

  public int getAddressBusWidth() {
    return (int) Math.ceil(Math.log(n_words) / Math.log(2.0));
  }

  public void setDataAt( int address, long value ) {
    if ((address < 0) || (address > data.length-1)) {
      message( "-W- " + toString() 
               + ".setDataAt: address out-of-range, ignored: " + address);
      return;
    }

    if (value != -1) { 
      value = value & bit_mask;
    }

    last_write_addr = address;
    last_write_data = value;
    data[address]   = value;

  }

  public long getDataAt( int address ) {
    last_read_addr = address;
    last_read_data = data[address];

    return last_read_data;
  }

  public int getBitsPerWord() {
    return n_bits;
  }

  public void setBitsPerWord( int n_bits ) throws Exception {
    if (n_bits == this.n_bits) return; 
    else {
      this.n_bits = n_bits;
      createMemory();
    }
  }

  public int getHexDigitsPerWord() {
    return (int) (Math.ceil( 0.25 * n_bits ));
  }

  public boolean canChangeSize() {
    return false;
  }

  public long[] getDataArray() {
    return data;
  }

  public void setDataArray( long[] data ) {
    this.data = data;
  }

  public boolean merge( java.io.BufferedReader reader ) {
    try {
      return parse( reader );
    }
    catch( Exception e ) {
      message( "-W- " + toString() + ".merge: " + e );
      message( "    while reading from: " + reader );
      jfig.utils.ExceptionTracer.trace( e );
      return false;
    }
  }

  public synchronized 
  void addMemoryListener( MemoryListener ML ) {

    MemoryListener  tmp[] = new MemoryListener[ _listenerCount+1 ];
    for( int i=0; i < _listenerCount; i++ ) {    
      tmp[i] = _listenerArray[i];
    }
    tmp[ _listenerCount ] = ML;                  

    _listenerArray = tmp;
    _listenerCount = tmp.length;
  }


  public synchronized
  void removeMemoryListener( MemoryListener ML ) {

   
    if (_listenerCount == 0) {
      message( "-W- No memory listeners registers. Cannot remove " + ML );
      return;
    }

   
    boolean found = false;
    for( int i=0; i < _listenerCount; i++ ) {
      if (_listenerArray[i] == ML) {
        found = true; break;
      }
    }
    if (!found) {
      message( "-W- Cannot remove unregistered MemoryListener " + ML );
      return;
    }

    if (_listenerCount == 1) { 
      _listenerArray = null;
      _listenerCount = 0;
      return;
    }

    MemoryListener tmp[] = new MemoryListener[ _listenerCount-1 ];
    for( int i=0, j=0; i < _listenerCount; i++ ) {
      if (_listenerArray[i] == ML) continue;
      tmp[j++] = _listenerArray[i];
    }

    _listenerArray = tmp;
    _listenerCount = tmp.length;
  }


  public void addMemoryListenerOld( MemoryListener ML ) {
    try {
      if (_listenerTable == null) _listenerTable = new Hashtable();

      _listenerTable.put( ML, ML );
    }
    catch( Exception e ) {
      message( "-E- failed to add MemoryListener: " + e );
    }
  }

  public void removeMemoryListenerOld( MemoryListener ML ) {
    try {
      _listenerTable.remove( ML );
    }
    catch( Exception e ) {
      message( "-E- failed to remove MemoryListener: " + e );
    }
  }


  protected void notifyWriteListenersOld( int  address,
                                          long old_value,
                                          long new_value ) 
  {
    if (_listenerTable != null) {
      for( Enumeration E=_listenerTable.keys(); E.hasMoreElements(); ) {
         ((MemoryListener) E.nextElement()).
            memoryWrite( address, old_value, new_value );
      }
    }
  }


  protected void notifyReadListenersOld( int address, long value ) {
    if (_listenerTable != null) {
      for( Enumeration E=_listenerTable.keys(); E.hasMoreElements(); ) {
         ((MemoryListener) E.nextElement()).
            memoryRead( address, value );
      }
    }
  }


 

  public void parseRAM( String resourcename ) {
    InputStream is = null;
    BufferedReader br = null;

    try {
      is = hades.manager.DesignManager.getDesignManager().
             getInputStream( this, resourcename);
      br = new BufferedReader( new InputStreamReader( is ));

      parse( br );

      br.close();
      is.close();
    }
    catch( Exception e ) {
      message( "-E- " + toString() + ".parseRAM(): Couldn't read from " 
               + resourcename );
      jfig.utils.ExceptionTracer.trace( e );
    }
  }



  public boolean parse( BufferedReader br ) throws Exception {
    if (br == null) return false;

    LineNumberReader lnr = new LineNumberReader( br );
    String          line = null;
    StringTokenizer   st = null;

    int  addr = 0;
    long  row = 0;

    if (debug) message( "-I- " + toString() + ": parsing... " );

    while( (line = lnr.readLine()) != null) {
      
      try {
        if      (line.startsWith( "#label" )) parseLabelLine( line );
        else if (line.startsWith( "#" )) continue;  
        else if (line.startsWith( ";" )) continue;  


        st = new StringTokenizer( line, " \t:" );
        if (st.countTokens() < 2) continue;    

        String  addrString = st.nextToken();
        String  dataString = st.nextToken();

        addr = Integer.parseInt( addrString, 16 );

        if (dataString.indexOf( 'X' ) >= 0) {
          setDataAt( addr, StdLogicVector.INVALID_XXX );
        }
        else {
          setDataAt( addr, Long.parseLong( dataString, 16 ));
        }
      }
      catch( Exception e ) {
        message( "-E- " + toString() + ".parse: " + e.getMessage() );
        message( "-E- on line " + lnr.getLineNumber() + ": " + line );
      }
    }

    lnr.close(); 
    return true;
  }


  protected void parseLabelLine( String line ) throws Exception {
    if (_decoder == null) return; 

    StringTokenizer st = new StringTokenizer( line, " \t:" );
    String  dummy = st.nextToken();
    int     addr  = Integer.parseInt( st.nextToken(), 16 );
    String  name  = st.nextToken();

    _decoder.addLabel( addr, name );
  }



  protected String getHexString( long value, int n_chars ) {
    String raw;

    if (value < 0) { 
      StringBuffer tmp = new StringBuffer();
      for( int i=0; i < n_chars; i++ ) tmp.append( 'X' );
      raw = tmp.toString();
    }
    else {
      raw  = Long.toHexString( value );
    }
    StringBuffer sb = new StringBuffer();
    int n_zeroes = n_chars - raw.length();
    for( int i=0; i < n_zeroes; i++ ) sb.append( '0' );
    sb.append( raw );
    return sb.toString();
  }


  public boolean save( java.io.PrintWriter PW ) {
    String  addrString, valueString;
    int n_chars = getHexDigitsPerWord();

    for( int i=0; i < data.length; i++ ) {
      addrString  = getHexString( i, 4 );
      valueString = getHexString( data[i], n_chars );
      PW.println( addrString + ":" + valueString );
    } 
    PW.flush();
    return false;
  }


  

  protected hades.gui.MemoryEditorFrame  MEF = null;

  public void configure() {
    if (MEF == null) {
       int n_lines = (int) Math.ceil(getSize() / 8.0);
       if (n_lines > 40) n_lines = 40;
       MEF = new hades.gui.MemoryEditorFrame( this, n_lines, 8,
                   "Edit " + getName() + " " + getClass().getName() );
       this.addMemoryListener( MEF );
    } 
    MEF.pack();
    MEF.setVisible( true );
  }


  public Component getPropertySheet() {
    if (MEF != null) return MEF;
    else             return null;
  }




  public void elaborate( Object arg ) {
    message( "-E- you shouldn't call elaborate on a GenericMemory!" );
  }



  public void evaluate( Object arg ) {
    message( "-E- you shouldn't call evaluate on a GenericMemory!" );
  }



  protected void notifyWriteListeners( int  address,
                                       long old_value,
                                       long new_value ) 
  {
    if (_listenerCount > 0) {
      for( int i=0; i < _listenerCount; i++ ) {
        _listenerArray[i].memoryWrite( address, old_value, new_value );
      }
    }
  }


  protected void notifyReadListeners( int address, long value ) {
    if (_listenerCount > 0) {
      for( int i=0; i < _listenerCount; i++ ) {
        _listenerArray[i].memoryRead( address, value );
      }
    }
  }


  public void dbg( String msg ) {
    System.out.println( msg );
  }

  public String getToolTip( java.awt.Point position, long millis ) {
    return 
          getName() + "\n"
        + getClass().getName() + "\n"
        + "[" + n_words + "x" + n_bits + "]\n"
        + "\n"
        + "last read at " + last_read_addr + " data= " + last_read_data + "\n"
        + "last write at " + last_write_addr + " data= " + last_write_data
    ;
  }




  public String toString() {
    return getClass().getName() + ": " + getFullName();
  }

  public static void main( String argv[] ) {
    GenericMemory GM = new GenericMemory();
    GM.configure();
  }

	public int getAddrOffset() {
		return 0;
	}

	public PropertySheet getConfigDialog() {
		return propertySheet;
	}

} 
