/**
 *  Copyright 2007-2008 University Of Southern California
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */


package edu.isi.pegasus.planner.client;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.StringTokenizer;

import edu.isi.pegasus.planner.common.PegasusProperties;

import edu.isi.pegasus.common.logging.LogManager;
import edu.isi.pegasus.common.logging.LogManagerFactory;
import edu.isi.pegasus.common.util.FactoryException;
import edu.isi.pegasus.planner.catalog.TransformationCatalog;
import edu.isi.pegasus.planner.catalog.transformation.TransformationCatalogEntry;
import edu.isi.pegasus.planner.catalog.transformation.TransformationFactory;
import edu.isi.pegasus.planner.catalog.transformation.TransformationFactoryException;
import edu.isi.pegasus.planner.catalog.transformation.classes.TCType;
import edu.isi.pegasus.planner.catalog.transformation.classes.TransformationStore;
import edu.isi.pegasus.planner.catalog.transformation.impl.CreateTCDatabase;
import gnu.getopt.Getopt;
import gnu.getopt.LongOpt;

/**
 * A client to convert transformation catalog between different formats.
 * 
 * @author Prasanth Thomas
 * @version $Revision$
 */
public class TCConverter
    extends Executable {
    
	
    /**
     * The default database .
     */
    private static String DEFAULT_DATABASE = "MySQL";
    /**
     * The database  format.
     */
    private static String DATABASE_FORMAT = "Database";
    
    /**
     * The File format.
     */
    private static String FILE_FORMAT = "File";
    
    /**
     * The textual format.
     */
    private static String TEXT_FORMAT = "Text";
    
    /**
     * YAML Format
     **/
    
    private static String YAML_FORMAT = "YAML";
    
    /**
     * The supported transformation formats.
     */
    private static final String[] SUPPORTED_TRANSFORMATION_FORMAT = {TEXT_FORMAT ,FILE_FORMAT, YAML_FORMAT};
    
    
    /**
     * List of sql initialization files
     */
    private static final String [] TC_INITIALIZATION_FILES ={"create-my-init.sql","create-my-tc.sql"};


    /**
     * The input files.
     */
    private List<String> mInputFiles;

    
    /**
     * The output file that is written out.
     */
    private String mOutputFile;
    
    /**
     * The output format for the transformation catalog.
     */
    private String mOutputFormat;
    
    /**
     * The input format for the transformation catalog.
     */
    private String mInputFormat;
    
    
    /**
     * The database type.
     */
    private String mDatabaseURL;
    
    /**
     * The database type.
     */
    private String mDatabase;
    /**
     * The database name.
     */
    private String mDatabaseName;
    
    /**
     * The database  user name.
     */
    private String mDatabaseUserName;
    
    /**
     * The database user password.
     */
    private String mDatabasePassword;
    
    /**
     * The database host .
     */
    private String mDatabaseHost;

    /**
     * Denotes the logging level that is to be used for logging the messages.
     */
    private int mLoggingLevel;
    
    /**
     * The default constructor.
     */
    public TCConverter() {
        super();
      }
    
    protected void initialize(String[] opts){
    	super.initialize(opts);
    	//the output format is whatever user specified in the properties
        mOutputFormat = TCConverter.TEXT_FORMAT;
        mInputFormat  = TCConverter.FILE_FORMAT;
        mDatabase = TCConverter.DEFAULT_DATABASE;
        mDatabaseHost ="localhost";
        mInputFiles = null;
        mOutputFile = null;
        mLoggingLevel     = LogManager.WARNING_MESSAGE_LEVEL;
    }

    /**
     * Sets up the logging options for this class. Looking at the properties
     * file, sets up the appropriate writers for output and stderr.
     */
    protected void setupLogging(){
        //setup the logger for the default streams.
        mLogger = LogManagerFactory.loadSingletonInstance( mProps );
        mLogger.logEventStart( "event.pegasus.pegasus-tc-converter", "pegasus.version",  mVersion );

    }
    /**
     * Loads all the properties
     * that would be needed
     * by the Toolkit classes
     */
    public void loadProperties() {
        
    }
   /**
    * Generates the list of valid options for the tc-converter client
    * 
    * @return LongOpt[] list of valid options
    */
    public LongOpt[] generateValidOptions() {
        LongOpt[] longopts = new LongOpt[ 13 ];
        longopts[ 0 ] = new LongOpt( "input", LongOpt.REQUIRED_ARGUMENT, null, 'i' );
        longopts[ 1 ] = new LongOpt( "iformat", LongOpt.REQUIRED_ARGUMENT, null, 'I' );
        longopts[ 2 ] = new LongOpt( "output", LongOpt.REQUIRED_ARGUMENT, null, 'o' );
        longopts[ 3 ] = new LongOpt( "oformat", LongOpt.REQUIRED_ARGUMENT, null, 'O' );
        longopts[ 4 ] = new LongOpt( "db-user-name", LongOpt.REQUIRED_ARGUMENT, null, 'N' );
        longopts[ 5 ] = new LongOpt( "db-user-password", LongOpt.REQUIRED_ARGUMENT, null, 'P' );
        longopts[ 6 ] = new LongOpt( "db-url", LongOpt.REQUIRED_ARGUMENT, null, 'U' );
        longopts[ 7 ] = new LongOpt( "db-host", LongOpt.REQUIRED_ARGUMENT, null, 'H' );
        longopts[ 8 ] = new LongOpt( "help", LongOpt.NO_ARGUMENT, null, 'h' );
        longopts[ 9 ] = new LongOpt( "version", LongOpt.NO_ARGUMENT, null, 'V' );
        longopts[ 10 ] = new LongOpt( "verbose", LongOpt.NO_ARGUMENT, null, 'v' );
        longopts[ 11 ]  = new LongOpt( "quiet", LongOpt.NO_ARGUMENT, null, 'q' );
        longopts[ 12 ] = new LongOpt( "conf", LongOpt.REQUIRED_ARGUMENT, null, 'c' );

        return longopts;

    }

    /**
     * Call the correct commands depending on options.
     * @param opts Command options
     */

    public void executeCommand() throws IOException {
    	String[] opts = getCommandLineOptions();
    	 if(opts.length == 0){
	     	mLogger.log("Please provide the required options.",LogManager.ERROR_MESSAGE_LEVEL);
	        this.printShortVersion();
	        System.exit(1);
     	}
    	 
        LongOpt[] longOptions = generateValidOptions();

        Getopt g = new Getopt( "TCConverter", opts, "hVvqI:i:O:o:U:P:N:H:c:",
            longOptions, false );

        int option = 0;
        int noOfOptions = 0;
        while ( ( option = g.getopt() ) != -1 ) {
            switch ( option ) {
                case 'i': //input
                    StringTokenizer str = new StringTokenizer( g.getOptarg(), "," );
                    mInputFiles = new ArrayList( str.countTokens() );
                    while ( str.hasMoreTokens() ) {
                        mInputFiles.add( str.nextToken() );
                    }
                    break;
                    
                case 'I': //iformat
                    mInputFormat = g.getOptarg();
                    break;
                    
                case 'o': //output
                    mOutputFile = g.getOptarg();
                    break;

                
                case 'O': //oformat
                    mOutputFormat = g.getOptarg();
                    break;
                case 'N': //name
                    mDatabaseUserName = g.getOptarg();
                    break;
                    
                case 'P': //password
                    mDatabasePassword = g.getOptarg();
                    break;
                    
                case 'U': //url
                    mDatabaseURL = g.getOptarg();
                    break;
                    
                case 'H': //host
                    mDatabaseHost = g.getOptarg();
                    break;  
                    
                case 'h': //help
                    printLongVersion();
                    System.exit( 0 );
                    break;

                case 'V': //version
                    System.out.println(getGVDSVersion());
                    System.exit( 0 );
                    break;
                    
                case 'v': //Verbose mode
                    incrementLogging();
                    break;
                    
                case 'q': //Quiet mode
                    decrementLogging();
                    break;
                    
                case 'c':
                	//do nothing
                	break;
                	
                default:
                	mLogger.log( "Unrecognized option or Invalid argument to option : " + (char)g.getOptopt(),
                                LogManager.FATAL_MESSAGE_LEVEL );
                    printShortVersion();
                    System.exit( 1 );
            }
        }
        if(getLoggingLevel() >= 0){
            //set the logging level only if -v was specified
            //else bank upon the the default logging level
            mLogger.setLevel(getLoggingLevel());
        }else{
            //set log level to FATAL only
            mLogger.setLevel( LogManager.FATAL_MESSAGE_LEVEL );
        }
        
        convertTC();
	
    }
    
    /**
     * Increments the logging level by 1.
     */
    public void incrementLogging(){
        mLoggingLevel++;
    }
    
    /**
     * Decrements the logging level by 1.
     */
    public void decrementLogging(){
        mLoggingLevel--;
    }

    /**
     * Returns the logging level.
     *
     * @return  the logging level.
     */
    public int getLoggingLevel(){
        return mLoggingLevel;
    }
    
    
    /**
     * Converts transformation catalog from one format to another
     * @throws IOException
     */
    private void convertTC() throws IOException{
    	
    	mLogger.log( "Input  format detected is " + mInputFormat , LogManager.DEBUG_MESSAGE_LEVEL );
        mLogger.log( "Output format detected is " + mOutputFormat , LogManager.DEBUG_MESSAGE_LEVEL );
    	//check if format is supported
        
        if(!isSupportedFormat(mInputFormat)){
            StringBuffer error = new StringBuffer();
            error.append( "Format not supported ! The supported input formats are [" );
            for( String format : SUPPORTED_TRANSFORMATION_FORMAT ){
                error.append( format ).append( " " );
            }
            error.append( "]" );
            throw new RuntimeException( error.toString() );
        }
        
        if(!isSupportedFormat(mOutputFormat)){
            StringBuffer error = new StringBuffer();
            error.append( "Format not supported ! The supported output formats are [" );
            for( String format : SUPPORTED_TRANSFORMATION_FORMAT ){
                error.append( format ).append( " " );
            }
            error.append( "]" );
            throw new RuntimeException( error.toString() );
        }
    	TransformationStore result = this.convertTCEntryFrom( mInputFiles, mInputFormat );
        //write out the result to the output file
        this.convertTCEntryTo(  result ,mOutputFormat ,mOutputFile);	
    }


    /**
     * Parses the input files in the input format and returns the output as a TransformationStore instance
     * 
     * @param inputFiles      list of input files that need to be converted
     * @param inputFormat     input format of the input files
     * 
     * @return  TransformationStore reference to the TransformationStore object , null if no transformation catalog entry exists.
     * 
     * @throws java.io.IOException
     */
    private TransformationStore convertTCEntryFrom( List<String> inputFiles, String inputFormat ) throws IOException{
	//sanity check
    	if(!inputFormat.equals(DATABASE_FORMAT)){
            if ( inputFiles == null || inputFiles.isEmpty() ){
                throw new IOException( "Input files not specified. Specify the --input option" );
            }
    	}else {
    		// Checks if db values are passed,else take the values from the properties file
    		if(mDatabaseURL != null && mDatabaseUserName != null && mDatabasePassword != null){
    			mProps.setProperty( "pegasus.catalog.transformation.db", mDatabase );
				mProps.setProperty( "pegasus.catalog.transformation.db.driver", mDatabase );
				mProps.setProperty( "pegasus.catalog.transformation.db.url", mDatabaseURL  );
				mProps.setProperty( "pegasus.catalog.transformation.db.user", mDatabaseUserName );
				mProps.setProperty( "pegasus.catalog.transformation.db.password", mDatabasePassword );
    		}
    	}
        TransformationStore result = new TransformationStore();
        List <TransformationCatalogEntry> entries = null;
        mProps.setProperty( "pegasus.catalog.transformation", inputFormat );
        
        if(inputFormat.equals(DATABASE_FORMAT)){
        	entries = parseTC(mProps);
        	if(entries != null){
            	for( TransformationCatalogEntry site : entries ){
                    result.addEntry( site );
                }
        	}
        }else{
        	// Sanity check
        	for( String inputFile : inputFiles ){
            	File input = new File(inputFile);
            	if(!input.canRead()){
            		throw new IOException( "File not found or cannot be read." + inputFile );
            	}
        	}
            for( String inputFile : inputFiles ){
                mProps.setProperty( "pegasus.catalog.transformation.file", inputFile );
                entries = parseTC(mProps);
                if(entries != null){
                	for( TransformationCatalogEntry site : entries ){
                		result.addEntry( site );
                	}
                }
            }//end of iteration through input files.
        }
        
        return result;
    }
    
    /**
     * Parses the input format specified in the properties file and returns list of TransfromationCatalogEntry  
     * @param pegasusProperties input format specified in the properties file
     * @return list of TransfromationCatalogEntry
     */
    private List <TransformationCatalogEntry> parseTC(PegasusProperties pegasusProperties) {
        //switch on input format.
        TransformationCatalog catalog = null;
        List <TransformationCatalogEntry> entries = null;
        try{
        	/* load the catalog using the factory */
            catalog = TransformationFactory.loadInstance( pegasusProperties );
        
            /* load all sites in transformation catalog */
            entries = (List <TransformationCatalogEntry>)catalog.getContents();
            mLogger.log( "Loaded  " + entries.size() + " number of transformations ", LogManager.DEBUG_MESSAGE_LEVEL );

            /* query for the sites, and print them out */
            mLogger.log( "Transformation loaded are "  + catalog.getContents( ) , LogManager.DEBUG_MESSAGE_LEVEL );
            
        } catch (TransformationFactoryException ife){
        	throw ife;
        } catch (Exception e) {
        	throw new RuntimeException("Failed to parse transformation catalog " + e.getMessage());
			
		}
        finally{
            /* close the connection */
            	if(catalog != null){
                catalog.close();
            	}
            
        }
        return entries;


    }

    
    /**
     * Checks if it is a supported transformation catalog format
     * @param format the format
     * @return true , if format is supported, false otherwise.
     */
    private boolean isSupportedFormat(String format){
    	for(String sformat : SUPPORTED_TRANSFORMATION_FORMAT ){
    		if(sformat.equals(format))
    			return true;
    	}
    	return false;
    }
    

    /**
     * Prints the short help.
     * 
     * 
     */
    public void printShortVersion() {
        String text =
            "\n $Id$ " +
            "\n " + getGVDSVersion() +
            "\n Usage: pegasus-tc-converter [-Dprop  [..]]  -I <input format> -O <output format> " +
            "\n        [-i <list of input files>] [-o <output file to write>] " +
            /* Disable Database conversion options
            "\n        [-N <database user name>] [-P <database user password>] [-U <database url>] [-H <database host>] " +
             */
            "\n        [-c <path to property file>] [-v] [-q] [-V] [-h] \n Type 'pegasus-tc-converter --help' for more help.";

        System.out.println(text);
    }

    public void printLongVersion() {
        StringBuffer text = new StringBuffer();
        text.append("\n $Id$ " );
        text.append("\n " + getGVDSVersion() );
        text.append("\n pegasus-tc-converter - Parses the transformation catalogs in given input format ( Text ,File ,Database ) and generates transformation catalog into given output format ( Text ,File ,Database )"  );
        text.append("\n " );
        text.append("\n Usage: pegasus-tc-converter [-Dprop  [..]]  [--iformat <input format>] [--oformat <output format>]" );
        text.append("\n       [--input <list of input files>] [--output <output file to write>] ");
        /* Disable Database conversion options
        text.append("\n       [--db-user-name <database user name>] [--db-user-pwd <database user password>] [--db-url <database url>] [--db-host <database host>]");
        */
        text.append("\n       [--conf <path to property file>] [--verbose] [--quiet][--Version] [--help]" );
        text.append("\n" );   
        text.append("\n" );
        text.append("\n Mandatory Options " );
        text.append("\n" );
        text.append("\n -I |--iformat        the input format for the files . Can be [Text ,File] "  ); 
        text.append("\n -O |--oformat        the output format of the file. Can be [Text ,File ] " );
        text.append("\n -i |--input          comma separated list of input files to convert.This option is mandatory when input format is Text or file " );
        text.append("\n -o |--output         the output file to which the output needs to be written to. This option is mandatory when output format is Text or file " );
        text.append("\n" );
        text.append("\n" );
        text.append("\n Other Options " );
        text.append("\n" );
        /* Disable Database conversion options
        text.append("\n -N |--db-user-name   the database user name "  ); 
        text.append("\n -P |--db-user-pwd    the database user password " );
        text.append("\n -U |--db-url         the database url "  ); 
        text.append("\n -H |--db-host        the database host " );
        */
        text.append("\n -c |--conf           path to  property file" );
        text.append("\n -v |--verbose        increases the verbosity of messages about what is going on" );
        text.append("\n -q |--quiet          decreases the verbosity of messages about what is going on" );
        text.append("\n -V |--version        displays the version of the Pegasus Workflow Planner" );
        text.append("\n -h |--help           generates this help." );
        text.append("\n" ); 
        text.append("\n" ); 
        text.append("\n Example Usage " );
        text.append("\n Text to file format conversion :- " ); 
        text.append("  pegasus-tc-converter  -i tc.data -I File -o tc.text  -O Text -v");
        /* Disable Database conversion options
        text.append("\n File to Database(new) format conversion  :- " ); 
        text.append("  pegasus-tc-converter  -i tc.data -I File -N mysql_user -P mysql_pwd -U jdbc:mysql://localhost:3306/tc -H localhost  -O Database -v" );
        text.append("\n Database(existing specified in properties file) to text format conversion  :-" ); 
        text.append("  pegasus-tc-converter  -I Database -o tc.txt -O Text -vvvvv");
        */
            
        System.out.println(text.toString());

    }


   
    /**
     * Converts Transformation store to the given output format.
     *
     * @param output  the reference to TransformationStore object
     * @param filename  the given output format.
     * @param output    the given output file name, null if the format is database.
     *
     * @throws IOException
     */
    private void convertTCEntryTo(TransformationStore output, String format, String filename) throws IOException {
        TransformationCatalog catalog = null;
        if (format.equals(FILE_FORMAT) || format.equals(TEXT_FORMAT) || format.equals(YAML_FORMAT)) {

            if (filename == null) {
                throw new IOException("Please specify a file to write the output to using --output option ");
            }
            mProps.setProperty("pegasus.catalog.transformation.file", filename);


        } else {
            if (mDatabaseURL != null && mDatabaseUserName != null && mDatabasePassword != null) {
                CreateTCDatabase jdbcTC;
                try {
                    jdbcTC = new CreateTCDatabase(mDatabase, mDatabaseURL, mDatabaseUserName, mDatabasePassword, mDatabaseHost);
                } catch (ClassNotFoundException e1) {
                    throw new RuntimeException("Failed to load driver " + mDatabase);
                } catch (SQLException e1) {
                    throw new RuntimeException("Failed to get connection " + mDatabaseURL);
                }
                mDatabaseName = jdbcTC.getDatabaseName(mDatabaseURL);
                if (mDatabaseName != null) {
                    try {
                        if (!jdbcTC.checkIfDatabaseExists(mDatabaseName)) {
                            if (!jdbcTC.createDatabase(mDatabaseName)) {
                                throw new RuntimeException("Failed to create database " + mDatabaseName);
                            }
                            String initFilePath = mProps.getSharedDir() + File.separator + "sql" + File.separator ;
                            for (String name : TC_INITIALIZATION_FILES) {
                                if (!jdbcTC.initializeDatabase(mDatabaseName, initFilePath + name)) {
                                    jdbcTC.deleteDatabase(mDatabaseName);
                                    throw new RuntimeException("Failed to initialize database " + mDatabaseName);
                                }
                            }


                            mProps.setProperty("pegasus.catalog.transformation.db", mDatabase);
                            mProps.setProperty("pegasus.catalog.transformation.db.driver", mDatabase);
                            mProps.setProperty("pegasus.catalog.transformation.db.url", mDatabaseURL);
                            mProps.setProperty("pegasus.catalog.transformation.db.user", mDatabaseUserName);
                            mProps.setProperty("pegasus.catalog.transformation.db.password", mDatabasePassword);
                        } else {
                            mLogger.log("Database " + mDatabaseName + " already exists", LogManager.ERROR_MESSAGE_LEVEL);
                            throw new RuntimeException("Cannot over write an existing database " + mDatabaseName);
                        }
                    } catch (SQLException e) {
                        mLogger.log("Failed connection with the database " + e.getMessage(), LogManager.ERROR_MESSAGE_LEVEL);
                        throw new RuntimeException("Connection Failed " + mDatabaseName);
                    }
                } else {
                    mLogger.log("Unable to detect database name in the URL", LogManager.ERROR_MESSAGE_LEVEL);
                    throw new RuntimeException("Unable to detect database name in the URL" + mDatabaseURL);
                }
            }
        }
    	mProps.setProperty( "pegasus.catalog.transformation", format );
    	catalog = TransformationFactory.loadInstance( mProps );
        List<TransformationCatalogEntry> entries = output.getEntries(null, (TCType)null);
        for(TransformationCatalogEntry tcentry:entries){
            try {
            	// Related to JIRA PM-228 
            	if(tcentry.getType().equals(TCType.STATIC_BINARY)){
            		tcentry.setType(TCType.STAGEABLE);
            	}
                catalog.insert(tcentry);
		
            } catch (Exception e) {
                mLogger.log( "Transformation failed to add "  + tcentry.toString() , 
                              LogManager.ERROR_MESSAGE_LEVEL  );
            }
        }
        
        //close the connection to the catalog
        catalog.close();
        
    	mLogger.log( "Successfully converted Transformation Catalog from "+ mInputFormat +" to " + mOutputFormat ,
                       LogManager.CONSOLE_MESSAGE_LEVEL );
        if( filename != null ){
            mLogger.log( "The output transfomation catalog is in file  "+ new java.io.File(filename).getAbsolutePath() ,
                       LogManager.CONSOLE_MESSAGE_LEVEL );
        }
    }

    /**
     * The main function
     * 
     * @param args  arguments passed at runtime
     * 
     * @throws java.lang.Exception
     */
    public static void main( String[] args ) throws Exception {
        
        TCConverter me = new TCConverter();
        int result = 0;
        double starttime = new Date().getTime();
        double execTime  = -1;

        try{
        	me.initialize(args); 
            me.executeCommand();
        }
        catch ( IOException ioe ){
            me.log(convertException( ioe,me.mLogger.getLevel()), LogManager.FATAL_MESSAGE_LEVEL);
            result = 1;
        }
        catch ( FactoryException fe){
            me.log( convertException(fe,me.mLogger.getLevel()) , LogManager.FATAL_MESSAGE_LEVEL);
            result = 2;
        }
        catch ( Exception e ) {
            //unaccounted for exceptions
            me.log(convertException(e,me.mLogger.getLevel()),
                         LogManager.FATAL_MESSAGE_LEVEL );
            result = 3;
        } finally {
            double endtime = new Date().getTime();
            execTime = (endtime - starttime)/1000;
        }

        // warn about non zero exit code
        if ( result != 0 ) {
            me.log("Non-zero exit-code " + result,
                         LogManager.WARNING_MESSAGE_LEVEL );
        }
        else{
            //log the time taken to execute
            me.log("Time taken to execute is " + execTime + " seconds",
                         LogManager.INFO_MESSAGE_LEVEL);
        }
        
        me.log( "Exiting with exitcode " + result, LogManager.DEBUG_MESSAGE_LEVEL );
        me.mLogger.logEventCompletion();
        System.exit(result);
        
    }

    
}
