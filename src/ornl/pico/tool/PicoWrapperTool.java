package ornl.pico.tool;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.filefilter.IOFileFilter;
import org.apache.commons.io.filefilter.MagicNumberFileFilter;
import org.apache.commons.io.filefilter.NotFileFilter;
import org.apache.commons.io.filefilter.TrueFileFilter;

import ornl.pico.PicoException;
import ornl.pico.io.PicoFile;
import ornl.pico.io.PicoInputStream;
import ornl.pico.io.PicoOutputStream;
import ornl.pico.io.PicoStructure;

/**
 * This tool unzips the file specified and then pico wraps it with the provided
 * key.
 * 
 * @author jcarter
 * @version $Date$ $Version$
 */
public class PicoWrapperTool {

    // /////////////////////////////////////////////////////////////////////////////
    // Class fields.
    // /////////////////////////////////////////////////////////////////////////////

    /** Size in bytes of the buffer. */
    private static int buffer_size = 10 * 2 ^ 10;

    /** File to file transfer buffer; instances can reuse this. */
    private static byte[] buffer = new byte[buffer_size];

    // /////////////////////////////////////////////////////////////////////////////
    // Class methods.
    // /////////////////////////////////////////////////////////////////////////////

    public static void usage() {
        System.err
                .println("Usage: java -jar PicoWrapperTool.jar [-catalog|-unwrap|-wrap] <infile> <outfile> [keystring] [password]");
        System.exit(1);
    }

    /**
     * 
     * @param is
     * @param os
     * @return
     */
    private static boolean transfer(InputStream is, OutputStream os) {
        int n = -1;
        try {
            while ((n = is.read(buffer)) > 0) {
                os.write(buffer, 0, n);
            }

            is.close();
            os.close();
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    /**
     * Explicitly set the size of the r/w buffer.
     * 
     * @param size
     */
    public static void setBufferSize(int size) {
        buffer_size = size;
        buffer = new byte[buffer_size];
    }

    public static boolean wrap(String unwrappedfile, String wrappedfile, byte[] key) {
        return wrap(new File(unwrappedfile), new File(wrappedfile), key);
    }

    public static boolean unwrap(String wrappedfile, String unwrappedfile) {
        return unwrap(new File(wrappedfile), new File(unwrappedfile));
    }

    /**
     * Pico wrap the infile to an outfile.
     * 
     * @param key the Pico wrap key.
     * @return true on success; false on failure.
     */
    public static boolean wrap(File unwrappedfile, File wrappedfile, byte[] key) {

        boolean result = false;

        try {

            PicoOutputStream pos = new PicoOutputStream(key, new FileOutputStream(wrappedfile));
            FileInputStream fis = new FileInputStream(unwrappedfile);
            result = transfer(fis, pos);

        } catch (Exception e) {
            System.err.println(e.getMessage());
        }
        return result;
    }

    /**
     * Unwrap a Pico encoded file when called with the file name
     * 
     * @param fileName
     * @return byte array
     */
    public static byte[] Pico_File(String fileName) {

        File file = new File(fileName);
        byte[] B = new byte[(int) (file.length())];
        try {
            PicoInputStream pis = new PicoInputStream(new FileInputStream(file));
            B = IOUtils.toByteArray(pis);
        } catch (Throwable t) {
            System.err.println(t.getMessage());
        }
        return B;
    }

    /**
     * Unwrap a Pico encoded file when called file as a byte array
     * 
     * @param byte array
     * @return byte array
     */
    public static byte[] Pico_File(byte[] sourceFile) {

        try {
            PicoInputStream pis = new PicoInputStream(new ByteArrayInputStream(sourceFile));
            sourceFile = IOUtils.toByteArray(pis);
        } catch (Throwable t) {
            System.err.println(t.getMessage());
        }
        return sourceFile;
    }

    /**
     * Unwrap a Pico encoded file.
     * 
     * @return true on success; false on failure.
     * @throws IOException
     * @throws PicoException
     * @throws FileNotFoundException
     */
    public static boolean unwrap(File wrappedfile, File unwrappedfile) {

        boolean result = false;

        try {

            PicoInputStream pis = new PicoInputStream(new FileInputStream(wrappedfile));
            FileOutputStream fos = new FileOutputStream(unwrappedfile);
            result = transfer(pis, fos);

        } catch (Exception e) {
            System.err.println(e.getMessage());
        }

        return result;
    }

    /**
     * Search through the specified directory structure and either Pico wrap the
     * files or unwrap the Pico files.
     * 
     * $ tool -unwrap|-wrap <root directory> <extension for unwrapped files|key
     * for wrapped files> <buffersize>
     * 
     * @param args
     * @throws IOException
     */
    public static void main(String[] args) throws IOException {

        int rcode = 0;

        // use a zip stream into a pico output stream.

        // -unwrap pico file infile to file outfile -- no keystring needed.
        // -wrap file infile to pico file outfile using keystring.
        // -catalog catalog infile line by line. unzip, wrap, and dump into
        // outfile base
        // directory using keystring.

        if (args.length < 3 || args.length > 4) {
            usage();
        }

        // Expecting at least 3 arguments.
        String command = args[0];
        File file_source = new File(args[1]);
        Collection<File> files = Collections.EMPTY_SET;

        // The third argument is either the extension to use when unwrapping
        // pico files or the key to use when wrapping.
        String ext_or_key = args[2];

        // Fourth parameter is optional; it is the size of the buffer to use.
        if (args.length == 4) {
            setBufferSize(Integer.parseInt(args[3]));
        }

        // Assume we are unwrapping and will look for Pico files.
        IOFileFilter ff = new MagicNumberFileFilter(PicoStructure.MAGIC);

        // If we are wrapping instead negate the filter; will only wrap those
        // things that are NOT wrapped already.
        if ("-wrap".equals(command)) {
            ff = new NotFileFilter(ff);
        }

        if (file_source.isDirectory()) {

            // We are working with a directory; grab all the files starting at
            // root that match the File Filter; recursively traverse the
            // directories.
            files = FileUtils.listFiles(file_source, ff, TrueFileFilter.TRUE);

        } else if (file_source.isFile()) {

            // Working with a single file.
            files = new ArrayList<File>();
            files.add(file_source);

        } else {
            usage();
        }

        if ("-unwrap".equalsIgnoreCase(command)) {
            for (File fin : files) {
                try {
                    // Checks the pico file header; we use the streams to
                    // unwrap.
                    PicoFile.open(fin);
                    String fout = fin.getCanonicalPath() + "." + ext_or_key;
                    rcode = unwrap(fin.getCanonicalPath(), fout) ? 0 : 1;
                } catch (PicoException pe) {
                    // There was a problem with the file structure.
                    System.err.printf("The file: %s is probably not a pico file.\n", fin.getName());
                    continue;
                }
            }

        } else if ("-wrap".equalsIgnoreCase(command)) {
            for (File fin : files) {
                String fout = fin.getCanonicalPath() + ".pico";
                rcode = wrap(fin.getCanonicalPath(), fout, ext_or_key.getBytes()) ? 0 : 1;
            }
        }

        System.exit(rcode);
    }
}
