package ornl.pico.tool;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.filefilter.IOFileFilter;
import org.apache.commons.io.filefilter.MagicNumberFileFilter;
import org.apache.commons.io.filefilter.NotFileFilter;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.apache.log4j.Logger;

import ornl.pico.PicoException;
import ornl.pico.io.PicoInputStream;
import ornl.pico.io.PicoOutputStream;
import ornl.pico.io.PicoStructure;

/**
 * A tool that manipulates pico files.
 * 
 * This tool performs two basic functions: It uses the pico library to unwrap
 * and wrap one or more files.
 * 
 * Source file(s) can either be found in a directory tree or a single file can
 * be specified; in the case of a directory, the search can be performed
 * recursively.
 * 
 * When wrapping, any file that does NOT have the pico magic string will be
 * wrapped. When unwrapping, any file that has the pico magic string will be
 * unwrapped.
 * 
 * All files, whether wrapped or unwrapped will be placed in the target
 * directory; the the target doesn't exist it will be created. If the target is
 * a file, then the tool will halt.
 * 
 * @author jcarter
 * @version $Date$ $Version$
 */
public class PicoWrapperTool {

    /** Log4J Logger so things are easier to debug with HBase and Hadoop */
    private static Logger log = Logger.getLogger(PicoWrapperTool.class);

    /** Default transfer buffer size */
    private static int buffer_size = 10 * 2 ^ 10;

    /** File to file transfer buffer; instances can reuse this. */
    private byte[] buffer = new byte[buffer_size];

    /** The parsed command line */
    private Options options;

    /** The extension to use for the target files */
    private String extension;

    /**
     * Flag that indicates the extension of a wrapped file should be removed;
     * this flag has no function during wrapping.
     */
    private boolean removeext = false;

    /** The pico encoding key, if wrapping */
    private byte[] key;

    /** true if unwrapping, false otherwise */
    private boolean unwrap;

    /** source file or directory */
    private File source;

    /** target directory, not a file */
    private File target;

    /** true if this is a recursive operation either unwrap or wrap */
    private boolean recursive;

    /** exit code of the tool */
    private int exitcode;

    /**
     * Construct the tool by building the command line.
     */
    public PicoWrapperTool() {
        options = buildCommandLine();
    }

    /**
     * Parse the command line into the properties of the tool instance. Only one
     * argument may be specified. The target must be a directory or a directory
     * that can be created. One of -w or -u must be specified.
     * 
     * @param args
     * @throws ParseException
     * @throws IOException
     */
    public void parse(String[] args) throws ParseException, IOException {
        // Build the command line and parser the commands.
        CommandLine commandline = new GnuParser().parse(options, args);

        // Only one argument is required: the extension / encoding key argument.
        if ((commandline.getArgs().length != 1)) {
            throw new ParseException("The command line specified is incorrect.");
        }

        extension = commandline.getArgs()[0];
        source = new File(commandline.getOptionValue('s'));
        target = new File(commandline.getOptionValue('t'));
        recursive = commandline.hasOption('r');

        log.trace("The source: " + source.getCanonicalPath());
        log.trace("The target directory: " + target.getCanonicalPath());

        // Have to designate either unwrap or wrap.
        if (commandline.hasOption('u')) {
            unwrap = true;
            removeext = extension.toLowerCase().equals("remove");
        } else if (commandline.hasOption('w')) {
            unwrap = false;
            key = extension.getBytes();
            log.trace("Wrap Key (used as bytes): " + extension);
            extension = "pico";
        } else {
            throw new ParseException("The command line specified is incorrect.");
        }
        log.trace("Target Extension: " + extension);

        // If this is an existing file, halt the program.
        if (target.exists() && !target.isDirectory()) {
            log.error("The target: " + target.getCanonicalPath() + " is not a directory");
            throw new ParseException(
                    "The target is not a directory; only a directory can be specified.");
        } else {

            if (!target.exists()) {
                target.mkdir();
            }
        }
    }

    /**
     * Build and return the command line options. The following options are
     * available.
     * 
     * --recursive|-r recursively search for source files. No function with
     * single files.
     * 
     * One of the following must be specified:
     * 
     * --unwrap|-u unwrap source files. --wrap|-w wrap source files.
     * 
     * --source|-s the root location of the source file(s). This can either be a
     * file or a directory.
     * 
     * --target|-t the target directory; this must be a directory. All files
     * will be put here when finished.
     * 
     * A single argument is required. It is a key for encoding when wrapping and
     * an extension when unwrapping. If unwrapping and the extension is REMOVE
     * the existing extension will be removed.
     * 
     * 
     * @return the command line Options.
     */
    @SuppressWarnings("static-access")
    private Options buildCommandLine() {

        // Command line build.
        Options options = new Options();
        Option opt;

        opt = OptionBuilder.withArgName("recursive")
                .withDescription("recursively search from the source directory.").create('r');
        options.addOption(opt);

        opt = OptionBuilder.withArgName("unwrap").withDescription("unwrap files.").create('u');
        options.addOption(opt);

        opt = OptionBuilder.withArgName("wrap").withDescription("wrap files.").create('w');
        options.addOption(opt);

        opt = OptionBuilder.withArgName("source").hasArg().isRequired()
                .withDescription("The location of the file(s) to process.").create('s');
        options.addOption(opt);

        opt = OptionBuilder.withArgName("target").hasArg().isRequired()
                .withDescription("The location where the processed file(s) will be written.")
                .create('t');
        options.addOption(opt);

        return options;
    }

    /**
     * Perform the specified operations.
     * 
     * @throws IOException
     */
    public void run() {

        boolean current, status = true;

        // Assume we are unwrapping and unwrapping ONLY Pico files.
        IOFileFilter filefilter = new MagicNumberFileFilter(PicoStructure.MAGIC);

        // If we are unwrapping adjust the filter so we wrap everything besides
        // pico files.
        if (!unwrap) {
            filefilter = new NotFileFilter(filefilter);
        }

        // Is our source a collection of files in a directory structure?
        if (source.isDirectory()) {

            log.trace("Running... with source as directory.");

            // Get all files that match the filter either in this directory
            // or recursively.
            Collection<File> sourcefiles = FileUtils.listFiles(source, filefilter,
                    recursive ? TrueFileFilter.INSTANCE : null);

            for (File sourcefile : sourcefiles) {
                log.trace("Processing file: " + sourcefile.getName());
                try {

                    if (unwrap) {
                        current = unwrap(sourcefile, new File(buildTargetFileName(sourcefile)));
                        log.trace("Unwrap Status: " + current);
                        status &= current;
                    } else {
                        current = wrap(sourcefile, new File(buildTargetFileName(sourcefile)));
                        log.trace("Wrap Status: " + current);
                        status &= current;
                    }

                } catch (IOException e) {
                    // There was a problem with the file structure.
                    log.warn("Problem developing the target file name.", e);
                    status = false;
                }
            }

        } else {

            log.trace("Running... with source as file.");

            try {
                if (unwrap) {
                    status = unwrap(source, new File(buildTargetFileName(source)));
                    log.trace("Unwrap Status: " + status);
                } else {
                    status = wrap(source, new File(buildTargetFileName(source)));
                    log.trace("Wrap Status: " + status);
                }
            } catch (IOException e) {
                // There was a problem with the file structure.
                log.warn("Problem developing the target file name.", e);
                status = false;
            }
        }
        exitcode = status ? 0 : 1;
        log.trace("Operation overall status: " + status);
    }

    /**
     * Build a canonical filename. This takes into account the options the user
     * specified. Whether or not to use an extension, etc.
     * 
     * @param name
     * @return
     * @throws IOException
     */
    private String buildTargetFileName(File file) throws IOException {

        String name = file.getName();

        // Are we unwrapping and removing the extension, usually .pico.
        if (removeext) {
            int periodindex = name.lastIndexOf('.');
            if (periodindex > 0) {
                name = name.substring(0, periodindex);
            }

        } else {
            name += "." + extension;
        }

        // Use the canonical path with the new name.
        return target.getCanonicalPath() + "/" + name;
    }

    /**
     * Transfer bytes from one stream to another; this can either be from a pico
     * stream to a file string or vice versa. Both streams are closed on exit
     * and the total number of bytes transferred is returned.
     * 
     * @param is
     * @param os
     * @return the total number of bytes transferred.
     */
    private int transfer(InputStream is, OutputStream os) throws IOException {

        int n = -1;
        int total = 0;

        while ((n = is.read(buffer)) > 0) {
            os.write(buffer, 0, n);
            total += n;
        }

        is.close();
        os.close();
        return total;
    }

    /**
     * Explicitly set the size of the r/w buffer.
     * 
     * @param size
     */
    public void setBufferSize(int size) {
        buffer_size = size;
        buffer = new byte[buffer_size];
    }

    /**
     * Pico wrap the infile to an outfile.
     * 
     * @param unwrappedfile the file that is being wrapped.
     * @param wrappedfile the file that is being created.
     * @return true on success; false on failure.
     */
    public boolean wrap(File unwrappedfile, File wrappedfile) {

        try {

            PicoOutputStream pos = new PicoOutputStream(key, new FileOutputStream(wrappedfile));
            FileInputStream fis = new FileInputStream(unwrappedfile);
            int total = transfer(fis, pos);
            log.info("Transferred " + total + " bytes");

        } catch (Exception e) {
            log.error("IO problem during unwrapping", e);
            return false;
        }
        return true;
    }

    /**
     * Unwrap a pico file.
     * 
     * @param wrappedfile the file that is being unwrapped.
     * @param unwrappedfile the file that is being created.
     * @return true on success; false on failure.
     */
    public boolean unwrap(File wrappedfile, File unwrappedfile) {

        try {

            PicoInputStream pis = new PicoInputStream(new FileInputStream(wrappedfile));
            FileOutputStream fos = new FileOutputStream(unwrappedfile);
            int total = transfer(pis, fos);
            log.info("Transferred " + total + " bytes");

        } catch (Exception e) {
            log.error("IO problem during unwrapping", e);
            return false;
        }
        return true;
    }

    /**
     * Unwrap a Pico encoded file.
     * 
     * @return true on success; false on failure.
     * @throws IOException
     * @throws PicoException
     * @throws FileNotFoundException
     */
    // ------------------------------------
    // Added function
    public static byte[] unWrapped(File wrappedFile) {

        System.err.println("In the unWrapped Method.");
        byte[] B = null;

        try {

            PicoInputStream pis = new PicoInputStream(new FileInputStream(wrappedFile));
            B = IOUtils.toByteArray(pis);

            for (int i = 0; i < B.length; i++) {
                System.err.printf("%0X ", (byte) B[i]);
            }

        } catch (Exception e) {
            System.err.println(e.getMessage());
        }

        return B;
    }

    /**
     * Build the tool and run the application according the specified
     * parameters.
     * 
     * $ java -Dlog4j.configuration=log4j.picotool.properties -jar pico.jar
     * <options> <argument>
     * 
     * The following options are available.
     * 
     * --recursive|-r recursively search for source files. No function with
     * single files.
     * 
     * One of the following must be specified:
     * 
     * --unwrap|-u unwrap source files. --wrap|-w wrap source files.
     * 
     * --source|-s the root location of the source file(s). This can either be a
     * file or a directory.
     * 
     * --target|-t the target directory; this must be a directory. All files
     * will be put here when finished.
     * 
     * A single argument is required. It is a key for encoding when wrapping and
     * an extension when unwrapping. If unwrapping and the extension is REMOVE
     * the existing extension will be removed.
     * 
     * @param args the command line arguments.
     * @throws IOException
     */
    public static void main(String[] args) throws IOException {

        PicoWrapperTool tool = new PicoWrapperTool();

        try {

            tool.parse(args);
            log.trace("Finished Parsing Arguments.");
            tool.run();
            log.trace("Finished Running Tool.");
            System.exit(tool.exitcode);

        } catch (ParseException e) {
            log.warn("User command line specification incorrect.");
            HelpFormatter hformatter = new HelpFormatter();
            hformatter
                    .printHelp(
                            "java -Dlog4j.configuration=log4j.picotool.properties -jar pico.jar <options> <extension|key>",
                            tool.options);
            System.exit(1);
        }
    }
}
