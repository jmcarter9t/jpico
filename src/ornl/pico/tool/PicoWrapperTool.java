package ornl.pico.tool;

import java.io.File;
import java.io.FileInputStream;
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
import org.apache.commons.io.filefilter.IOFileFilter;
import org.apache.commons.io.filefilter.MagicNumberFileFilter;
import org.apache.commons.io.filefilter.NotFileFilter;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.apache.log4j.Logger;

import ornl.pico.io.PicoInputStream;
import ornl.pico.io.PicoOutputStream;
import ornl.pico.io.PicoStructure;

/**
 * This tool manipulates Pico files.
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
     * 
     */
    public PicoWrapperTool() {
        options = buildCommandLine();
    }

    /**
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
        } else if (commandline.hasOption('w')) {
            unwrap = false;
            key = extension.getBytes();
            log.trace("Wrap Key (used as bytes): " + extension);
            extension = "pico";
        } else {
            throw new ParseException("The command line specified is incorrect.");
        }
        log.trace("Target Extension: " + extension);
    }

    /**
     * Build and return the command line options.
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
     * @throws IOException
     * 
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

                    // path/to/target/infile.extension
                    String targetfile = target.getCanonicalPath() + "/" + sourcefile.getName() + "." + extension;

                    if (unwrap) {
                        current = unwrap(sourcefile, new File(targetfile));
                        log.trace("Unwrap Status: " + current);
                        status &= current;
                    } else {
                        current = wrap(sourcefile, new File(targetfile));
                        log.trace("Wrap Status: " + current);
                        status &= current;
                    }

                } catch (IOException e) {
                    // There was a problem with the file structure.
                    log.warn("Problem developing the target file name.",
                            e);
                    status = false;
                }
            }

        } else {

                try {
                    // Process the single file.
                    // path/to/target/infile.extension
                    String targetfile = target.getCanonicalPath() + "/" + source.getName() + "." + extension;
                    if (unwrap) {
                        status = unwrap(source, new File(targetfile));
                        log.trace("Unwrap Status: " + status);
                    } else {
                        status = wrap(source, new File(targetfile));
                        log.trace("Wrap Status: " + status);
                    }
                } catch (IOException e) {
                    // There was a problem with the file structure.
                    log.warn("Problem developing the target file name.",
                            e);
                    status = false;
                }
        }
        exitcode = status ? 0 : 1;
        log.trace("Operation overall status: " + status);
    }
    
    /**
     * 
     * @param is
     * @param os
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
     * @param key the Pico wrap key.
     * @return 0 on success; 1 on fail.
     */
    public boolean wrap(File unwrappedfile, File wrappedfile) {

        try {

            PicoOutputStream pos = new PicoOutputStream(key, new FileOutputStream(wrappedfile));
            FileInputStream fis = new FileInputStream(unwrappedfile);
            int total = transfer(fis, pos);
            log.info("Transferred " + total + " bytes");

        } catch (Exception e) {
            log.error("IO problem during unwrapping", e);
            return true;
        }
        return false;
    }

    /**
     * Unwrap a Pico encoded file.
     * 
     * @return 0 on success; 1 on failure.
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
     * Utility for working with Pico files. Provides the following
     * functionality:
     * 
     * The utility will either wrap or unwrap a file or a directory. If a
     * directory, then all files in the directory will be examined. If the -r
     * option is used then it will search recursively. The source (-s) is where
     * the input files will be drawn; the target (-t) is where the output files
     * will be written; it is always a directory. The only argument is a single
     * string that is used as either the key for wrapping or the extension of
     * files that are unwrapped.
     * 
     * $ pico [-r] -u|-w -s=<source> -t=<target> <key|extension>
     * 
     * @param args
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
            HelpFormatter hformatter = new HelpFormatter();
            hformatter.printHelp("java -Dlog4j.configuration=log4j.picotool.properties -jar pico.jar <options> <extension|key>", tool.options);
            log.warn("User command line specification incorrect.");
            System.exit(1);
        }
    }
}
