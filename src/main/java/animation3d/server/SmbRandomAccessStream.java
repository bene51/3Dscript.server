package animation3d.server;

import java.io.IOException;
import java.io.InputStream;

import jcifs.smb.SmbException;
import jcifs.smb.SmbRandomAccessFile;

public class SmbRandomAccessStream extends InputStream {

	private final SmbRandomAccessFile raf;

	public SmbRandomAccessStream(SmbRandomAccessFile raf) {
		this.raf = raf;
	}

	@Override
	public int available() {
		try {
			return (int)(raf.length() - raf.getFilePointer());
		} catch(SmbException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void close() {
		try {
			raf.close();
		} catch (SmbException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public boolean markSupported() {
		return false;
	}

	@Override
	public int read() throws IOException {
		return raf.read();
	}

	@Override
	public int read(byte[] b) {
		try {
			return raf.read(b);
		} catch (SmbException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public int read(byte[] b, int off, int len) {
		try {
			return raf.read(b, off, len);
		} catch (SmbException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public long skip(long n) {
		try {
			return raf.skipBytes((int)n);
		} catch (SmbException e) {
			throw new RuntimeException(e);
		}
	}

	public void seek(int n) {
		raf.seek(n);
	}
}
