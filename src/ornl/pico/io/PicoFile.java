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

package ornl.pico.io;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import ornl.pico.PicoException;

/**
 * Provide for random access reading and writing of a Pico-encoded file.
 * <p>
 * To use this create an instance via one of the {@code static} methods.
 * <ol>
 * <li>Use {@link #create(File)} or {@link #create(String)} to create a new file
 * or replace an existing file.</li>
 * <li>Use {@link #open(File)} or {@link #open(String)} to open an existing
 * file.</li>
 * </ol>
 * <p>
 * <b>Note</b>: Because the data can be written arbitrarily, but the hash must
 * be computed sequentially, the hash is not usually available (see
 * {@link #getHeader()}).
 * <p>
 * <b>Caution</b>: If you write data, you must invoke {@link #finish()} or
 * {@link #close()} when done to be sure the header is written, since the hash
 * must be computed and written last.
 * 
 * @author Stacy Prowell (prowellsj@ornl.gov)
 */
public class PicoFile implements WritableByteChannel, ReadableByteChannel, SeekableByteChannel {

    // ======================================================================
    // Static methods.
    // ======================================================================

    public static boolean checkMagic(File file) throws IOException {

        // Attempt to extract the first 10 bytes. For files with fewer
        // than 10 bytes, use what the file contains.
        byte[] magic = new byte[2];
        FileInputStream fis = new FileInputStream(file);
        fis.read(magic);
        fis.close();

        return Arrays.equals(magic, PicoStructure.MAGIC);
    }

    /**
     * Decode the input pico bytes by blocks using a stream to stream transfer
     * and return the decoded bytes as an InputStream to use in a file parser.
     * 
     * @param picobytes the raw pico file bytes.
     * @return a new ByteArrayInputStream that can be fed to a parser.
     * @throws IOException
     * @throws PicoException
     */
    public static byte[] decode(byte[] picobytes) throws IOException, PicoException {
        // 10k buffer;
        byte[] buffer = new byte[10 * 2 ^ 10];
        PicoInputStream pis = new PicoInputStream(new ByteArrayInputStream(picobytes));
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        int n = -1;

        // decode the pico file in blocks.
        while ((n = pis.read(buffer)) > 0) {
            baos.write(buffer, 0, n);
        }

        return baos.toByteArray();
    }

    /**
     * Create or replace a Pico file. If the file exists it will be replaced. If
     * it does not exist it is created. A random key is used.
     * 
     * TODO: May want to add a String parameter that is "rw", "r", etc.
     * 
     * @param filename The filename.
     * @return The Pico file instance.
     * @throws IOException The file cannot be created.
     */
    public static PicoFile create(String filename) throws IOException {
        if (filename == null) {
            throw new NullPointerException("The filename is null.");
        }
        return create(filename, KeyUtils.makeKey());
    }

    /**
     * Create or replace a Pico file. If the file exists it will be replaced. If
     * it does not exist it is created. A random key is used.
     * 
     * TODO: May want to add a String parameter that is "rw", "r", etc.
     * 
     * @param filename The filename.
     * @return The Pico file instance.
     * @throws IOException The file cannot be created.
     */
    public static PicoFile create(File file) throws IOException {
        if (file == null) {
            throw new NullPointerException("The file is null.");
        }
        return create(file, KeyUtils.makeKey());
    }

    /**
     * Create or replace a Pico file. If the file exists it will be replaced. If
     * it does not exist it is created.
     * 
     * TODO: May want to add a String parameter that is "rw", "r", etc.
     * 
     * @param filename The filename.
     * @param key The key to use to encrypt the file.
     * @return The Pico file instance.
     * @throws IOException The file cannot be created.
     */
    public static PicoFile create(String filename, byte[] key) throws IOException {
        if (filename == null) {
            throw new NullPointerException("The filename is null.");
        }
        if (key == null) {
            throw new NullPointerException("The key is null.");
        }
        if (key.length == 0) {
            throw new IllegalArgumentException("Encryption key is empty.");
        }
        return new PicoFile(new RandomAccessFile(filename, "rw"), key);
    }

    /**
     * Create or replace a Pico file. If the file exists it will be replaced. If
     * it does not exist it is created.
     * 
     * TODO: May want to add a String parameter that is "rw", "r", etc.
     * 
     * @param filename The filename.
     * @param key The key to use to encrypt the file.
     * @return The Pico file instance.
     * @throws IOException The file cannot be created.
     */
    public static PicoFile create(File file, byte[] key) throws IOException {
        if (file == null) {
            throw new NullPointerException("The file is null.");
        }
        if (key == null) {
            throw new NullPointerException("The key is null.");
        }
        if (key.length == 0) {
            throw new IllegalArgumentException("Encryption key is empty.");
        }

        return new PicoFile(new RandomAccessFile(file, "rw"), key);
    }

    /**
     * Open an existing Pico file for reading and writing. The file must already
     * exist.
     * 
     * TODO: May want to add a String parameter that is "rw", "r", etc.
     * 
     * @param filename The filename.
     * @return The Pico file instance.
     * @throws PicoException The file format is incorrect.
     * @throws IOException The file cannot be created.
     */
    public static PicoFile open(String filename) throws PicoException, IOException {
        if (filename == null) {
            throw new NullPointerException("The filename is null.");
        }
        return new PicoFile(new RandomAccessFile(filename, "rw"));
    }

    /**
     * Open an existing Pico file for reading and writing. The file must already
     * exist.
     * 
     * TODO: May want to add a String parameter that is "rw", "r", etc.
     * 
     * @param file The file.
     * @return The Pico file instance.
     * @throws PicoException The file format is incorrect.
     * @throws IOException The file cannot be created.
     */
    public static PicoFile open(File file) throws PicoException, IOException {
        if (file == null) {
            throw new NullPointerException("The file is null.");
        }
        return new PicoFile(new RandomAccessFile(file, "rw"));
    }
    
    public static PicoFile open(File file, String method) throws PicoException, IOException {
        if (file == null) {
            throw new NullPointerException("The file is null.");
        }

        if (!(method.equals("rw") || method.equals("r") || method.equals("w"))) {
            throw new PicoException("The method: " + method + " of working with a file cannot be used.");
        }
        
        return new PicoFile(new RandomAccessFile(file, method), method);
    }

    // ======================================================================
    // Instance data.
    // ======================================================================

    /** The physical file for this logical Pico file. */
    private final RandomAccessFile _backing;

    /** Whether the file is open. */
    private boolean _open = false;

    /** The Pico header that will be written to the file. */
    private PicoHeader _head;

    /** If true then the hash stored in the header is valid. */
    private boolean _hashvalid = false;

    /** The digest is valid up to, but not including, this position. */
    private long _digestvalidto = 0L;

    /** The message digest to use to compute the hash. */
    private MessageDigest _digest;
    
    /** The access mode for the file. */
    private String mode;

    // ======================================================================
    // Constructors.
    // The constructors are protected since the static methods should be used
    // to create instances.
    // ======================================================================

    /**
     * Open an existing Pico encoded file.
     * 
     * @param backing The random access file.
     * @throws PicoException The file format is incorrect.
     * @throws IOException The file cannot be read.
     */
    protected PicoFile(RandomAccessFile backing) throws PicoException, IOException {
        assert backing != null : "Backing is null.";
        _backing = backing;
        _open = true;
        _resetDigest();
        _readHeader();
        mode = "r";
        position(0L);
    }
    
    /**
     * Open an existing Pico encoded file.
     * 
     * @param backing The random access file.
     * @throws PicoException The file format is incorrect.
     * @throws IOException The file cannot be read.
     */
    protected PicoFile(RandomAccessFile backing, String mode) throws PicoException, IOException {
        assert backing != null : "Backing is null.";
        _backing = backing;
        _open = true;
        _resetDigest();
        _readHeader();
        this.mode = mode;
        position(0L);
    }

    /**
     * Create a new Pico file instance from the given random access file. If the
     * file exists and it is not empty then it is truncated. If it does not
     * exist then it is created.
     * 
     * @param backing The random access file.
     * @param key The key to use to encrypt the file.
     * @throws IOException The file cannot be read.
     */
    protected PicoFile(RandomAccessFile backing, byte[] key) throws IOException {
        assert backing != null : "Backing is null.";
        assert key != null : "Key is null.";
        assert key.length > 0 : "Key is missing.";
        _backing = backing;
        _open = true;
        _resetDigest();
        // We are creating a new file, so truncate any existing file and
        // generate a new header.
        _backing.setLength(0L);
        _head = new PicoHeader();
        _head.setKey(key);

        // Now the Header size is fixed since we have the key and know the size
        // of the hash
        // we will write later.

        // This actually positions us to _head.offset + 0
        position(0L);
    }

    // ======================================================================
    // Internal methods.
    // ======================================================================

    /**
     * Reset the digest. After calling this all bytes must be re-processed to
     * obtain the hash.
     */
    private void _resetDigest() {
        try {
            _digest = MessageDigest.getInstance(PicoStructure.HASH);
            _digestvalidto = 0L;
        } catch (NoSuchAlgorithmException nsae) {
            throw new RuntimeException("Failed to create hash.", nsae);
        }
    }

    /**
     * Update the digest so it is valid up to the current file position. After
     * invoking this the digest is valid up to the current file position given
     * by {@link #position()}.
     */
    private void _updateDigest() throws IOException {
        long pos = position();
        if (_digestvalidto == pos)
            return;

        // If I move to an earlier position, write, and move back, the digest
        // will
        // remain the same.
        if (_digestvalidto > pos)
            _resetDigest();
        int blocksize = 16384;
        ByteBuffer buf = ByteBuffer.allocate(blocksize);
        position(_digestvalidto);
        while (_digestvalidto < pos) {
            _digestvalidto += read(buf);
            _digest.update(buf);
            buf.clear();
        } // Compute the digest through the rest of the file.
    }

    /**
     * Read the header. This reads the header from an existing file. After this
     * method completes the header reflects what it stored in the file,
     * including the hash and key.
     * 
     * @throws PicoException The header structure is incorrect.
     * @throws IOException The header cannot be read.
     */
    private void _readHeader() throws PicoException, IOException {
        if (!_open)
            return;

        // Save the current position and move to the start of the header.
        long pos = _backing.getFilePointer();
        _backing.seek(PicoStructure.HEAD_START);

        // Read the header from the file. We read the fixed length portion
        // here, up through the start of the key.
        byte[] _fixedhdr = new byte[(int) PicoStructure.FIXED_HEADER_LENGTH];
        int length = _backing.read(_fixedhdr);

        // Make sure we have enough bytes for the magic string.
        if (length < PicoStructure.MAGIC_LENGTH - PicoStructure.MAGIC_OFFSET) {
            // The magic string was not present. This cannot be a Pico wrapper
            // file.
            throw new PicoException("File too short; missing magic string.");
        }

        // Process the fixed portion of the header. After calling this the
        // key is allocated, but not populated.
        _head = PicoHeader.getHeader(_fixedhdr);

        // The hash is valid because we just read it from the file and nothing
        // has yet been written. All write methods must invalidate the hash.
        _hashvalid = true;

        // Go and read the key, now that we know its length. Note that we read
        // it directly into the array returned by getKey.
        length = _backing.read(_head.getKey());

        // Make sure we have the complete key. The only bad case is that the
        // file ends before the key is complete.
        if (length != _head.getKey().length) {
            throw new PicoException("File too short; incomplete key.");
        }

        // Move to the original position in the file.
        _backing.seek(pos);

        // Ka-presto! The header has been read. Life is good.
    }

    // ======================================================================
    // General access methods.
    // ======================================================================

    /**
     * Get the header for this Pico file. The returned header is a copy of the
     * actual header.
     * <p>
     * Note that the returned hash may be {@code null} if it has not been
     * computed. If the file has been opened, but not written, then the hash
     * will be available via this method.
     * 
     * @return The header of this file.
     * @throws IOException The file length cannot be read.
     */
    public PicoHeader getHeader() throws IOException {
        PicoHeader head = _head.clone();
        if (!_hashvalid) {
            head.hash = null;
        }
        return head;
    }

    /**
     * Predicates that indicates the original magic number of this pico file
     * matches the magic (byte array) provided. The magic number is used as a
     * quick means to verify a file's type.
     * 
     * @param magic the magic number to verify.
     * @return true if this file's initial bytes match the array of bytes
     *         provided.
     */
    public byte[] getMagic() {
        byte[] magic = null;
        long pos = 0;
        try {
            // cache the current position in the file so we can return to it.
            pos = _backing.getFilePointer();
            // Position to the 0 byte of the actual file.
            position(0L);
            ByteBuffer magic_buffer = ByteBuffer.allocate(2);
            int num = read(magic_buffer);
            if (num == 2) {
                // successful read of the 2 bytes.
                _backing.seek(pos);
                magic = magic_buffer.array();
            }
        } catch (IOException e1) {
            e1.printStackTrace();
        }
        return magic;
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.nio.channels.Channel#isOpen()
     */
    @Override
    public boolean isOpen() {
        return _open;
    }

    // ======================================================================
    // Finish and close the file.
    // ======================================================================

    /**
     * Flush the file, computing the hash if necessary. After this method
     * completes the hash obtained in {@link #getHeader()} will be valid, but
     * the file will not be closed. If you want the file to be closed, use
     * {@link #close()} instead, which invokes this method.
     * <p>
     * <b>Warning</b>: Because of the hash computation this method can be
     * costly! After calling this the internal hash computation must be
     * completely reset, so a subsequent write will cause the hash to be updated
     * from scratch. Use caution with this method. In fact, this is the reason
     * this method is not named {@code flush}.
     * 
     * @throws IOException An error occurred writing the file.
     */
    public void finish() throws IOException {
        if (!_open)
            return;

        // Save the current position so we can restore it later.
        long pos = _backing.getFilePointer();

        // If the hash is not valid, compute it now.
        if (!_hashvalid) {
            // The hash is not valid. Complete the computation now and store
            // the resulting hash.
            _backing.seek(_backing.length());
            _updateDigest();
            _head.hash = _digest.digest();
            _hashvalid = true;

            // Reset the digest now to force it to be re-computed next time.
            // This must be done since we just "used up" the existing digest
            // instance.
            _resetDigest();
        }

        // Write the header to the backing store.
        _backing.seek(PicoStructure.HEAD_START);
        _backing.write(_head.putHeader());

        // Restore the file position.
        _backing.seek(pos);
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.nio.channels.Channel#close()
     */
    @Override
    public void close() throws IOException {
        if (!_open)
            return;

        // We only need to execute the finish method if we are writing.
        if (mode.contains("w")) {
            finish();
        }

        _open = false;
        _backing.close();
    }

    // ======================================================================
    // Position in channel and length of channel.
    // These methods operate on the packaged data, excluding the header.
    // ======================================================================

    /*
     * (non-Javadoc)
     * 
     * @see java.nio.channels.SeekableByteChannel#position()
     * 
     * NOTE: The position in the file is 0-based AFTER the header has been
     * skipped.
     */
    @Override
    public long position() throws IOException {
        return _backing.getFilePointer() - _head.offset;
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.nio.channels.SeekableByteChannel#position(long)
     * 
     * NOTE: newPosition is 0-based into the encrypted file bytes; this is AFTER
     * the header.
     */
    @Override
    public SeekableByteChannel position(long newPosition) throws IOException {
        _backing.seek(newPosition + _head.offset);
        return this;
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.nio.channels.SeekableByteChannel#size()
     * 
     * NOTE: This is the size of the file WITHOUT the header being counted.
     */
    @Override
    public long size() throws IOException {

        // size will be negative if nothing has been written or read;
        // _backing does not "recognize" the first jump past the header until
        // that happens.
        // This just ensures we don't return negative lengths.
        long size = _backing.length() - _head.offset;
        return (size >= 0) ? size : 0;
    }

    /**
     * Return the size of the backing Pico file, i.e., it includes the header.
     * 
     * @return
     * @throws IOException
     */
    public long picoSize() throws IOException {
        return _backing.length();
    }

    // ======================================================================
    // Read methods.
    // ======================================================================

    /*
     * (non-Javadoc)
     * 
     * @see java.nio.channels.SeekableByteChannel#read(java.nio.ByteBuffer)
     */
    @Override
    public int read(ByteBuffer dst) throws IOException {
        if (dst == null) {
            throw new NullPointerException("The destination buffer is null.");
        }
        if (dst.limit() == 0) {
            throw new IllegalArgumentException("The destination buffer has zero length.");
        }
        if (!_open)
            return -1;

        // Make an attempt to read up to r bytes from the channel, where
        // r is the number of bytes remaining in the buffer, that is,
        // dst.remaining(), at the moment this method is invoked.
        long _here = position();

        // Build temporary storage.
        int remain = dst.remaining();
        byte[] data = new byte[remain];

        int length = _backing.read(data, 0, remain);

        if (length > 0) {

            // Iterate thru each byte of temporary storage and decrypt those
            // bytes back
            // into the buffer
            for (int index = 0; index < length; index++) {
                data[index] = _head.crypt(data[index], index + _here);
            } // Decrypt all bytes.
            dst.put(data, 0, length);
        }
        return length;
    }

    /**
     * Read the next byte at the current position, and return it. Return -1 if
     * the end of the file is read.
     * 
     * @return The next byte.
     * @throws IOException The next byte cannot be read.
     */
    public int read() throws IOException {
        if (!_open)
            return -1;
        long _here = position();
        int val = _backing.read();
        if (val >= 0) {
            val = _head.crypt((byte) val, _here);
        }
        return val;
    }

    public ByteBuffer readBacking() throws IOException {
        if (!_open) {
            return null;
        }

        ByteBuffer dst = ByteBuffer.allocate((int) _backing.length());

        int length;

        // 4K buffer.
        byte[] buf = new byte[4 * (2 ^ 10)];

        _backing.seek(0);
        
        // Read up to 4K blocks from the backing file
        while ((length = _backing.read(buf)) > 0) {
            try {
                // store the block of the correct size into the ByteBuffer.
                dst.put(buf, 0, length);
            } catch (BufferOverflowException e) {
                return null;
            }
        }
        return dst;
    }

    // ======================================================================
    // Write methods.
    // ======================================================================

    /*
     * (non-Javadoc)
     * 
     * @see java.nio.channels.SeekableByteChannel#truncate(long)
     */
    @Override
    public PicoFile truncate(long size) throws IOException {
        if (_open)
            _backing.setLength(size + _head.offset);
        _hashvalid = false;
        _resetDigest();
        return this;
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.nio.channels.WritableByteChannel#write(java.nio.ByteBuffer)
     * 
     * NOTE: Changes the state of src in the normal case, i.e., position =
     * limit.
     */
    @Override
    public int write(ByteBuffer src) throws IOException {
        if (src == null) {
            throw new NullPointerException("The source buffer is null.");
        }

        // Is there something to actually write in source.
        if (src.limit() == 0 || src.remaining() == 0) {
            throw new IllegalArgumentException(
                    "The source buffer has zero length or zero remaining bytes.");
        }

        if (!_open)
            return -1;

        // based on the specification _hear is p.
        long _here = position();

        // Make an attempt to write up to r bytes to the channel, where
        // r is the number of bytes remaining in the buffer, that is,
        // src.remaining(), at the moment this method is invoked.
        // NOTE: not good to use the src.array() since that is the backing array
        // which
        // reflects the entire buffer and not from p to remaining.
        byte[] encr = new byte[src.remaining()];

        // Update the digest to the start of the write.
        _updateDigest();

        // Have we reached source's limit?
        for (int index = 0; src.hasRemaining(); index++) {
            // The unencrypted data.
            byte b = src.get();
            _digest.update(b);
            _digestvalidto++;
            // encrypt it into the buffer.
            encr[index] = _head.crypt(b, index + _here);
        }
        _backing.write(encr);
        return encr.length;
    }

    /**
     * Write a byte at the current position.
     * 
     * @param datum The byte.
     * @throws IOException The byte cannot be written.
     */
    public void write(int datum) throws IOException {
        if (!_open)
            return;
        datum &= 0xff;
        long _here = position();

        // Are we synced up?
        if (_here == _digestvalidto) {

            // Yes, digest the byte and move the valid to pointer.
            _digest.update((byte) datum);
            _digestvalidto++;  // JMC: Was missing.

        }  // Otherwise, advancing of the position will destroy the digest sync.

        datum = _head.crypt((byte) datum, _here);
        _backing.write(datum);
    }
}
