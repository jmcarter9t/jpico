package ornl.pico.tool;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.TrueFileFilter;

public class Test {

    public static void main(String[] args) throws IOException {
        Collection<File> files = FileUtils.listFiles(new File(args[0]), TrueFileFilter.TRUE,
                TrueFileFilter.TRUE);

        for (File fin : files) {
            String[] fnameparts = fin.getName().split("\\.");
            // If the file is already encoded, ignore it.
            if (fnameparts.length>1 && fnameparts[1].equals(args[1])) {
                continue;
            }
            String fname = fnameparts[0] + "." + args[1];
            System.out.printf("Filename: %s\n", fname);
            System.out.printf("Path: %s\n", fin.getParent());
            System.out.printf("New Path: %s\n", fin.getParent() + File.separator + fname);
        }

    }

}
