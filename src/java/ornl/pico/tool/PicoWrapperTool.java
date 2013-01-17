package ornl.pico.tool;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

import ornl.pico.io.PicoInputStream;
import ornl.pico.io.PicoOutputStream;

/**
 * This tool unzips the file specified and then pico wraps it with the provided key.
 * 
 * @author jcarter
 * @version $Date$ $Version$
 */
public class PicoWrapperTool {

    ///////////////////////////////////////////////////////////////////////////////
    // Class fields. 
    ///////////////////////////////////////////////////////////////////////////////

    /** Size in bytes of the buffer. */
    private static final int BUFSIZE = 100 * 2 ^ 10;

    ///////////////////////////////////////////////////////////////////////////////
    // Class methods. 
    ///////////////////////////////////////////////////////////////////////////////

    public static void usage() {
        System.err.println("Usage: java -jar PicoWrapperTool.jar [-catalog|-unwrap|-wrap] <infile> <outfile> [keystring] [password]");
        System.exit(1);
    }

    ///////////////////////////////////////////////////////////////////////////////
    // Instance fields. 
    ///////////////////////////////////////////////////////////////////////////////

    /** File to file transfer buffer; instances can reuse this. */
    private byte[] buffer;

    /** Input file or directory. */
    private File infile;

    /** Output file or directory. */
    private File outfile;
    
    private long start;
    private long stop;

    ///////////////////////////////////////////////////////////////////////////////
    // Constructor.
    ///////////////////////////////////////////////////////////////////////////////

    /**
     * Create a PicoWrapperTool.
     * 
     * @param infile the source file or directory.
     * @param outfile the target file or directory.
     */
    public PicoWrapperTool(String infile, String outfile, int size) {
        this(new File(infile), new File(outfile), size);
    }
    
    public PicoWrapperTool(File infile, File outfile, int size) {
        this.infile = infile;
        this.outfile = outfile;
        this.buffer = new byte[size * 2^10];
    }

    public PicoWrapperTool(String infile, String outfile) {
        this(new File(infile), new File(outfile), 8);
    }
    
    public PicoWrapperTool(File infile, File outfile) {
        this(infile,outfile,8);
    }

    ///////////////////////////////////////////////////////////////////////////////
    // Instance methods.
    ///////////////////////////////////////////////////////////////////////////////

    /**
     * Expects
     * 
     * @param parts
     * @return
     */
    public String makeFileName(String filename) {

        // Split the path.
        String[] parts = filename.split("/");
        
        String fname = parts[parts.length-4] + "/" + parts[parts.length-3] + "_" + parts[parts.length-2] + "_malware.pico"; 

        // Make the directory where this pico file resides if necessary.
        return outfile.getAbsolutePath() + "/" + fname;
    }

    /**
     * 
     * @param is
     * @param os
     * @return
     */
    private boolean transfer(InputStream is, OutputStream os) {
        int n = -1;
        try {
            while ((n = is.read(buffer)) > 0) {
                os.write(buffer, 0, n);
            }

            is.close();
            os.close();
        } catch (IOException e) {
            System.err.println(e.getMessage());
            return false;
        }
        return true;
    }

    /**
     * Pico wrap the infile to an outfile.
     * 
     * @param key the Pico wrap key.
     * @return true on success; false on failure.
     */
    public boolean wrap(byte[] key) {

        try {
            // Pico Output File.
            PicoOutputStream pos = new PicoOutputStream(key, new FileOutputStream(this.outfile));

            // Unwrapped Input File.
            FileInputStream fis = new FileInputStream(this.infile);
            return transfer(fis, pos);
        } catch (Exception e) {
            System.err.println(e.getMessage());
            return false;
        }
    }

    /**
     * Unwrap a Pico encoded file.
     * 
     * @return true on success; false on failure.
     */
    public boolean unwrap() {
        // Pico input file.
        try {

            PicoInputStream pis = new PicoInputStream(new FileInputStream(this.infile));
            // Unwrapped output file.
            FileOutputStream fos = new FileOutputStream(this.outfile);
            return transfer(pis, fos);

        } catch (Exception e) {
            System.err.println(e.getMessage());
            return false;
        }

    }

    /**
     * 
     * @param key
     * @param password
     * @return
     */
    // FIXME : Broken, missing unzip library
    public boolean processCatalog(byte[] key, String password) {
        String zipfilename;
        boolean result = true;
        int count = 0;
        start = System.currentTimeMillis();

        try {
            BufferedReader reader = new BufferedReader(new FileReader(infile));
            

            // Process each line in the catalog.
            while ((zipfilename = reader.readLine()) != null) {
                
                if (count>0 && count%20==0) {
                    stop = System.currentTimeMillis();
                    System.out.printf("Per file approx: %.4f sec\n", (stop-start)/20000.0);
                    start = stop;
                }
                
                // Merges the outfile path with the end bits from the zipfile name.
                File picofile = new File(makeFileName(zipfilename.trim()));
                if (!picofile.getParentFile().exists()) {
                    picofile.getParentFile().mkdirs();
                }

                // Any one failure signals a complete failure.
//                result &= unzipWrap(new File(zipfilename), picofile, key, password);
                
                count++;
            }

        } catch (IOException e) {
            System.err.println(e.getMessage());
            return false;
        }

        return result;
    }

    /**
     * @param args
     */
    public static void main(String[] args) {

        int rcode = 0;

        // use a zip stream into a pico output stream.

        // -unwrap   pico file infile to file outfile -- no keystring needed.
        // -wrap     file infile to pico file outfile using keystring.
        // -catalog  catalog infile line by line.  unzip, wrap, and dump into outfile base 
        //           directory using keystring.

        if (args.length < 3 || args.length > 6) {
            usage();
        }

        String command = args[0];
        String infile = args[1];
        String outfile = args[2];
        int size = 8;
        
        if (args.length==6) {
            size = Integer.parseInt(args[5]);
        }

        PicoWrapperTool tool = new PicoWrapperTool(infile, outfile, size);

        if ("-unwrap".equalsIgnoreCase(command)) {
            rcode = tool.unwrap() ? 0 : 1;

        } else if ("-wrap".equalsIgnoreCase(command) && args.length == 4) {
            rcode = tool.wrap(args[3].getBytes()) ? 0 : 1;

        } else if ("-catalog".equalsIgnoreCase(command) && args.length >= 5) {
            rcode = tool.processCatalog(args[3].getBytes(), args[4]) ? 0 : 1;

        } else {
            usage();
        }

        System.exit(rcode);
    }
}
