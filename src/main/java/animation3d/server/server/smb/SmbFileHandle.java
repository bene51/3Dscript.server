package animation3d.server.server.smb;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import jcifs.smb.SmbException;
import jcifs.smb.SmbFile;
import jcifs.smb.SmbRandomAccessFile;
import loci.common.DataTools;
import loci.common.IRandomAccess;

/*
 * Copied and modified from loci.common.FileHandle
 * @author bschmid
 *
 */
public class SmbFileHandle implements IRandomAccess {

  /** Byte ordering of this stream. */
  protected ByteOrder order;


  /** The random access file object backing this FileHandle. */
  protected SmbRandomAccessFile raf;

  // -- Constructors --

  /**
   * Creates a random access file stream to read from, and
   * optionally to write to, the file specified by the File argument.
   *
   * @param file a {@link File} representing a file on disk
   * @param mode a valid access mode as defined in {@link RandomAccessFile}
   * @throws FileNotFoundException if the file does not exist
   */
  public SmbFileHandle(SmbFile file, String mode) throws SmbException {
    raf = new SmbRandomAccessFile(file, mode);
    order = ByteOrder.BIG_ENDIAN;
  }

  // -- FileHandle API methods --

  /**
   * @return the {@link RandomAccessFile} object backing this FileHandle.
   */
  public SmbRandomAccessFile getRandomAccessFile() { return raf; }

  // -- IRandomAccess API methods --

  /* @see IRandomAccess.close() */
  @Override
  public void close() throws IOException {
    raf.close();
  }

  /* @see IRandomAccess.getFilePointer() */
  @Override
  public long getFilePointer() throws IOException {
    return raf.getFilePointer();
  }

  /* @see IRandomAccess#exists() */
  @Override
  public boolean exists() throws IOException {
    return length() >= 0;
  }

  /* @see IRandomAccess.length() */
  @Override
  public long length() throws IOException {
    return raf.length();
  }

  /* @see IRandomAccess.read(byte[]) */
  @Override
  public int read(byte[] b) throws IOException {
    return raf.read(b);
  }

  /* @see IRandomAccess.read(byte[], int, int) */
  @Override
  public int read(byte[] b, int off, int len) throws IOException {
    return raf.read(b, off, len);
  }

  /* @see IRandomAccess.read(ByteBuffer) */
  @Override
  public int read(ByteBuffer buffer) throws IOException {
    return read(buffer, 0, buffer.capacity());
  }

  /* @see IRandomAccess.read(ByteBuffer, int, int) */
  @Override
  public int read(ByteBuffer buffer, int off, int len) throws IOException {
    byte[] b = new byte[len];
    int n = read(b);
    buffer.put(b, off, len);
    return n;
  }

  /* @see IRandomAccess.seek(long) */
  @Override
  public void seek(long pos) throws IOException {
    raf.seek(pos);
  }

  /* @see IRandomAccess.write(ByteBuffer) */
  @Override
  public void write(ByteBuffer buf) throws IOException {
    write(buf, 0, buf.capacity());
  }

  /* @see IRandomAccess.write(ByteBuffer, int, int) */
  @Override
  public void write(ByteBuffer buf, int off, int len) throws IOException {
    // TODO
  }

  // -- DataInput API methods --

  /* @see java.io.DataInput.readBoolean() */
  @Override
  public boolean readBoolean() throws IOException {
    return raf.readBoolean();
  }

  /* @see java.io.DataInput.readByte() */
  @Override
  public byte readByte() throws IOException {
    return raf.readByte();
  }

  /* @see java.io.DataInput.readChar() */
  @Override
  public char readChar() throws IOException {
    return raf.readChar();
  }

  /* @see java.io.DataInput.readDouble() */
  @Override
  public double readDouble() throws IOException {
    double v = raf.readDouble();
    return order.equals(ByteOrder.LITTLE_ENDIAN) ? DataTools.swap(v) : v;
  }

  /* @see java.io.DataInput.readFloat() */
  @Override
  public float readFloat() throws IOException {
    float v = raf.readFloat();
    return order.equals(ByteOrder.LITTLE_ENDIAN) ? DataTools.swap(v) : v;
  }

  /* @see java.io.DataInput.readFully(byte[]) */
  @Override
  public void readFully(byte[] b) throws IOException {
    raf.readFully(b);
  }

  /* @see java.io.DataInput.readFully(byte[], int, int) */
  @Override
  public void readFully(byte[] b, int off, int len) throws IOException {
    raf.readFully(b, off, len);
  }

  /* @see java.io.DataInput.readInt() */
  @Override
  public int readInt() throws IOException {
    int v = raf.readInt();
    return order.equals(ByteOrder.LITTLE_ENDIAN) ? DataTools.swap(v) : v;
  }

  /* @see java.io.DataInput.readLine() */
  @Override
  public String readLine() throws IOException {
    return raf.readLine();
  }

  /* @see java.io.DataInput.readLong() */
  @Override
  public long readLong() throws IOException {
    long v = raf.readLong();
    return order.equals(ByteOrder.LITTLE_ENDIAN) ? DataTools.swap(v) : v;
  }

  /* @see java.io.DataInput.readShort() */
  @Override
  public short readShort() throws IOException {
    short v = raf.readShort();
    return order.equals(ByteOrder.LITTLE_ENDIAN) ? DataTools.swap(v) : v;
  }

  /* @see java.io.DataInput.readUnsignedByte() */
  @Override
  public int readUnsignedByte() throws IOException {
    return raf.readUnsignedByte();
  }

  /* @see java.io.DataInput.readUnsignedShort() */
  @Override
  public int readUnsignedShort() throws IOException {
    return raf.readUnsignedShort();
  }

  /* @see java.io.DataInput.readUTF() */
  @Override
  public String readUTF() throws IOException {
    return raf.readUTF();
  }

  /* @see java.io.DataInput.skipBytes(int) */
  @Override
  public int skipBytes(int n) throws IOException {
    return raf.skipBytes(n);
  }

  /* @see #skipBytes(int) */
  @Override
  public long skipBytes(long n) throws IOException {
    if (n < 1) {
      return 0;
    }
    final long currentPosition = getFilePointer();
    n = Math.min(n, length() - currentPosition);
    if (n <= Integer.MAX_VALUE) {
      /* use standard library if possible */
      return skipBytes((int) n);
    }
    seek(currentPosition + n);
    return n;
  }

  // -- DataOutput API metthods --

  /* @see java.io.DataOutput.write(byte[]) */
  @Override
  public void write(byte[] b) throws IOException {
    raf.write(b);
  }

  /* @see java.io.DataOutput.write(byte[], int, int) */
  @Override
  public void write(byte[] b, int off, int len) throws IOException {
    raf.write(b, off, len);
  }

  /* @see java.io.DataOutput.write(int b) */
  @Override
  public void write(int b) throws IOException {
    if (order.equals(ByteOrder.LITTLE_ENDIAN)) b = DataTools.swap(b);
    raf.write(b);
  }

  /* @see java.io.DataOutput.writeBoolean(boolean) */
  @Override
  public void writeBoolean(boolean v) throws IOException {
    raf.writeBoolean(v);
  }

  /* @see java.io.DataOutput.writeByte(int) */
  @Override
  public void writeByte(int v) throws IOException {
    raf.writeByte(v);
  }

  /* @see java.io.DataOutput.writeBytes(String) */
  @Override
  public void writeBytes(String s) throws IOException {
    raf.writeBytes(s);
  }

  /* @see java.io.DataOutput.writeChar(int) */
  @Override
  public void writeChar(int v) throws IOException {
    if (order.equals(ByteOrder.LITTLE_ENDIAN)) v = DataTools.swap(v);
    raf.writeChar(v);
  }

  /* @see java.io.DataOutput.writeChars(String) */
  @Override
  public void writeChars(String s) throws IOException {
    raf.writeChars(s);
  }

  /* @see java.io.DataOutput.writeDouble(double) */
  @Override
  public void writeDouble(double v) throws IOException {
    if (order.equals(ByteOrder.LITTLE_ENDIAN)) v = DataTools.swap(v);
    raf.writeDouble(v);
  }

  /* @see java.io.DataOutput.writeFloat(float) */
  @Override
  public void writeFloat(float v) throws IOException {
    if (order.equals(ByteOrder.LITTLE_ENDIAN)) v = DataTools.swap(v);
    raf.writeFloat(v);
  }

  /* @see java.io.DataOutput.writeInt(int) */
  @Override
  public void writeInt(int v) throws IOException {
    if (order.equals(ByteOrder.LITTLE_ENDIAN)) v = DataTools.swap(v);
    raf.writeInt(v);
  }

  /* @see java.io.DataOutput.writeLong(long) */
  @Override
  public void writeLong(long v) throws IOException {
    if (order.equals(ByteOrder.LITTLE_ENDIAN)) v = DataTools.swap(v);
    raf.writeLong(v);
  }

  /* @see java.io.DataOutput.writeShort(int) */
  @Override
  public void writeShort(int v) throws IOException {
    if (order.equals(ByteOrder.LITTLE_ENDIAN)) v = DataTools.swap(v);
    raf.writeShort(v);
  }

  /* @see java.io.DataOutput.writeUTF(String)  */
  @Override
  public void writeUTF(String str) throws IOException {
    raf.writeUTF(str);
  }

  /* @see IRandomAccess.getOrder() */
  @Override
  public ByteOrder getOrder() {
    return order;
  }

  /* @see IRandomAccess.setOrder(ByteOrder) */
  @Override
  public void setOrder(ByteOrder order) {
    this.order = order;
  }
}
