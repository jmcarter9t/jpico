/*------------------------------------------------------------------------------
 *        _        
 *   _ __(_)__ ___ 
 *  | '_ \ / _/ _ \
 *  | .__/_\__\___/
 *  |_|            Pico
 * 
 * Copyright (c) 2012 by UT-Battelle, LLC.
 * All rights reserved.
 *----------------------------------------------------------------------------*/

package ornl.pico.io.test;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.Assert;

import ornl.pico.PicoException;
import ornl.pico.io.PicoFile;
import ornl.pico.io.PicoInputStream;
import ornl.pico.io.PicoOutputStream;
import ornl.pico.tool.PicoWrapperTool;

/**
 * @author Stacy Prowell (prowellsj@ornl.gov)
 */
public class JMCTest {

    /** The key to use to encode the data. */
    static byte[] key = {
                    (byte) 0x00, (byte) 0x11, (byte) 0x77, (byte) 0x55, (byte) 0xff,
                    (byte) 0xa8, (byte) 0x23, (byte) 0x26, (byte) 0xa4, (byte) 0x3e,
                    (byte) 0x2a, (byte) 0x7a, (byte) 0x43,
    };

    /** The test data file to write and read back. */
    static byte[] testdata = {
                    (byte) 'T', (byte) 'h', (byte) 'i', (byte) 's',
                    (byte) ' ', (byte) 'i', (byte) 's', (byte) ' ',
                    (byte) 'a', (byte) ' ', (byte) 'T', (byte) 'E',
                    (byte) 'S', (byte) 'T', (byte) '.', (byte) 0x0a,
                    (byte) 0x00, (byte) 0xff, (byte) 0x55, (byte) 0xaa,
                    (byte) 'A', (byte) ' ', (byte) 't', (byte) 'e',
                    (byte) 's', (byte) 't', (byte) ' ', (byte) 't',
                    (byte) 'h', (byte) 'i', (byte) 's', (byte) ' ',
                    (byte) 'i', (byte) 's', (byte) '.', (byte) 0x37,
    };

    /* The above data, when correctly in a Pico wrapper file, looks like this.
     * 
     * 0000000 91 c0 00 00 00 00 00 00 00 29 9f 20 7f 81 09 be
     * 0000010 e5 4d 7f c9 d4 04 d6 df ca 20 00 0d 00 11 77 55
     * 0000020 ff a8 23 26 a4 3e 2a 7a 43 54 79 1e 26 df c1 50
     * 0000030 06 c5 1e 7e 3f 10 54 3f 7d 55 00 fd 89 67 84 4a
     * 0000040 4f 09 37 20 65 1f 3c 8c 88 4a 55 8a 09
     * 
     * 0000000 91 c0 --> magic string
     * 0000002 00 00 --> major version number (0)
     * 0000004 00 00 --> minor version number (0)
     * 0000006 00 00 00 29 --> offset to data
     * 000000a 9f 20 7f 81 09 be ef 4d 7f c9 d4 04 d6 df ca 20 --> hash (md5)
     * 000001a 00 0d --> key length (13)
     * 000001c 00 11 77 55 ff a8 23 26 a4 3e 2a 7a 43 --> key
     * 0000029 54 79 1e 26 .. 09 --> encrypted data
     */

    private File tmpPicoFile;
    private File tmpNormFile;

    private File pico1;
    private File zip1;
    private File regular1;
    private File clean1;
    private byte[] concordiakey = "concordia".getBytes();

    /**
     * Write a test PicoFile to the temp directory.
     * 
     * @throws Exception
     */
    @Before
    public void setup() throws Exception {
        // Make a temporary file and Pico-encode the data.
        tmpPicoFile = File.createTempFile("test", "pico");
        //tmpPicoFile.deleteOnExit();

        PicoOutputStream pos =
                        new PicoOutputStream(key, new FileOutputStream(tmpPicoFile));
        pos.write(testdata, 0, testdata.length);
        pos.close();

        tmpNormFile = File.createTempFile("normal", "dat");
        //tmpNormFile.deleteOnExit();

        FileOutputStream fos = new FileOutputStream(tmpNormFile);
        fos.write(testdata);
        fos.close();

        zip1 = new File("/home/jcarter/concordia/testdata/zip/1000/1/3/write.zip");
        regular1 = new File("/home/jcarter/concordia/concordia/testfiles/execs/x86/write.exe");
        pico1 = new File("/home/jcarter/concordia/testdata/pico/write.pico");
        clean1 = new File("/home/jcarter/concordia/testdata/clean/write.exe");
    }

    @Test
    public void toolWrapTest() {
        PicoWrapperTool tool = new PicoWrapperTool(regular1,pico1);
        tool.wrap(concordiakey);
    }
    
    @Test
    public void toolUnwrapTest() {
        PicoWrapperTool tool = new PicoWrapperTool(pico1,clean1);
        tool.unwrap();
    }
    
    @Test
    public void toolProcessCatalog() {
        PicoWrapperTool tool = new PicoWrapperTool("/home/jcarter/concordia/testdata/catalog.dat","/home/jcarter/concordia/testdata/pico" );
        tool.processCatalog(concordiakey, "infected");
    }
    
    @Test
    public void toolFileNameTest() {
        PicoWrapperTool tool = new PicoWrapperTool("/home/jcarter/concordia/testdata/pico","/home/jcarter/concordia/testdata/pico" );
        String name = tool.makeFileName(zip1.getAbsolutePath());
        System.out.println(name);
    }

    @Test
    public void fileSizeTest() throws IOException {
        PicoFile pf = PicoFile.create(tmpPicoFile, key);

        // Newly created file should be size 0.
        Assert.assertEquals(0, pf.size());
        System.out.printf("Pico File Size: %d\n", pf.size());

        // Newly created file should be positioned at 0.
        Assert.assertEquals(0, pf.position());
        System.out.printf("Pico File Position: %d\n", pf.position());

    }

    @Test
    public void fileWriteComparisonTest() throws IOException {
        File f2 = File.createTempFile("bytewrite", "pico");
        f2.deleteOnExit();
        PicoFile pf2 = PicoFile.create(f2, key);

        RandomAccessFile ris = new RandomAccessFile(tmpNormFile, "r");

        int b;
        while ((b = ris.read()) != -1) {
            pf2.write(b);
        }
        pf2.finish();
        Assert.assertEquals(f2.length(), tmpPicoFile.length());

        File f1 = File.createTempFile("bufferwrite", "pico");
        f1.deleteOnExit();
        PicoFile pf1 = PicoFile.create(f1, key);

        FileChannel fc = ris.getChannel();
        ByteBuffer buf = ByteBuffer.allocate(32);
        fc.position(0);

        int p;
        while (fc.read(buf) > 0) {
            buf.flip();
            pf1.write(buf);
            buf.rewind();
        }
        pf1.finish();
        ris.close();

        Assert.assertEquals(f1.length(), tmpPicoFile.length());
        Assert.assertEquals(pf1.size(), pf2.size());

        File f3 = File.createTempFile("streamwrite", "pico");
        //f3.deleteOnExit();

        PicoOutputStream pos = new PicoOutputStream(key, new FileOutputStream(f3));

        InputStream is = new FileInputStream(tmpNormFile);
        byte[] bytes = new byte[32];
        int n;
        while ((n = is.read(bytes)) > 0) {
            pos.write(bytes, 0, n);
        }
        is.close();
        //pos.finish();
        pos.close();

        Assert.assertEquals(f3.length(), tmpPicoFile.length());
        Assert.assertArrayEquals(pf1.getHeader().hash, pf2.getHeader().hash);

        pf1.close();
        pf2.close();
    }

    @Test
    public void fileTest2() throws IOException, PicoException {

        File fout = new File("/home/jcarter/concordia/test/write2.pico");
        File fin = new File("/home/jcarter/concordia/concordia/testfiles/execs/x86/write.exe");

        PicoFile pf = PicoFile.create(fout, key);
        FileInputStream fis = new FileInputStream(fin);
        FileChannel fc = fis.getChannel();

        System.out.printf("Pico File Size: %d\n", pf.size());
        System.out.printf("Pico File Position: %d\n", pf.position());

        //        ByteBuffer buf = ByteBuffer.allocate(32);
        //
        //                while ((fc.read(buf) > 0)) {
        //                    buf.rewind();
        //                    pf.write(buf);
        //                }
        //
        //        int b;
        //        while ((b = fis.read()) != -1) {
        //            pf.write(b);
        //        }

        byte[] buf2 = new byte[1];

        for (int i = 0; i < 10; i++) {
            int n = fis.read(buf2);
            for (int j = 0; j < n; j++) {
                System.out.printf("%02X ", buf2[j]);
                pf.write(buf2[j]);
            }
        }
        System.out.println();

        pf.position(0);
        for (int i = 0; i < 5; i++) {
            int c = pf.read();
            System.out.printf("%02X ", 0xFF & c);
        }

        while (fis.available() > 0) {
            int n = fis.read(buf2);
            for (int j = 0; j < n; j++) {
                pf.write(buf2[j]);
            }
        }

        fis.close();
        pf.finish();
        pf.close();
    }

    @Test
    public void fileTest3() throws IOException, PicoException {
        File fout = new File("/tmp/test3.pico");
        File fin = new File("/home/jcarter/concordia/concordia/testfiles/execs/x86/ping.exe");

        OutputStream os = new FileOutputStream(fout);

        byte[] key = "concordia".getBytes();

        PicoOutputStream pos = new PicoOutputStream(key, os);

        InputStream is = new FileInputStream(fin);
        byte[] b = new byte[32];
        int n;
        while ((n = is.read(b)) > 0) {
            pos.write(b, 0, n);
        }
        is.close();
        //pos.finish();
        pos.close();
    }

    @Test
    public void fileTest() throws IOException, PicoException {
        // Now the data is encoded.  Open the file and read it back.
        ByteBuffer bb = ByteBuffer.allocate(8);
        PicoFile pf = PicoFile.open(tmpPicoFile);
        int length;
        int index = 0;
        do {
            // Try to fill the buffer.
            bb.clear();
            length = pf.read(bb);
            if (length > 0) {
                // Check.
                for (int here = 0; here < length; here++) {
                    Assert.assertEquals("Incorrect byte on read at index " +
                                    (index + here) + ":",
                                    bb.get(here), testdata[index + here]);
                }
                index += length;
            }
        } while (length > 0);
        Assert.assertEquals("Not all data was read back:", testdata.length, index);
        pf.close();
    }

    @Test
    public void streamTest() throws IOException, PicoException {
        // Now the data is encoded.  Open the file and read it back.
        byte[] data = new byte[8];
        PicoInputStream pf = new PicoInputStream(new FileInputStream(tmpPicoFile));
        int length;
        int index = 0;
        do {
            // Try to fill the buffer.
            length = pf.read(data);
            if (length > 0) {
                // Check.
                for (int here = 0; here < length; here++) {
                    Assert.assertEquals("Incorrect byte on read at index " +
                                    (here + index) + ":",
                                    data[here], testdata[index + here]);
                }
                index += length;
            }
        } while (length > 0);
        Assert.assertEquals("Not all data was read back:", testdata.length, index);
        pf.close();
    }

    @After
    public void shutdown() throws Exception {
        // Discard the temp file.
        // tmpPicoFile.delete();
    }
}
