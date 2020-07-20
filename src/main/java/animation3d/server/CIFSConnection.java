package animation3d.server;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.MalformedURLException;

import ij.ImagePlus;
import jcifs.CIFSContext;
import jcifs.CIFSException;
import jcifs.context.SingletonContext;
import jcifs.smb.NtlmPasswordAuthenticator;
import jcifs.smb.SmbException;
import jcifs.smb.SmbFile;
import jcifs.smb.SmbFileInputStream;
import jcifs.smb.SmbFileOutputStream;
import loci.common.IRandomAccess;
import loci.common.Location;
import loci.plugins.BF;
import loci.plugins.in.ImporterOptions;

public class CIFSConnection implements Connection, AutoCloseable {

	private CIFSContext ctx;

	public CIFSConnection(SharedFSJob job) {
		this(job.domain, job.username, job.password);
	}

	public CIFSConnection(String domain, String username, String password) {
		ctx = SingletonContext.getInstance().withCredentials(new NtlmPasswordAuthenticator(domain, username, password));
	}

	@Override
	public boolean checkSession(Job job) {
		if(!(job instanceof SharedFSJob))
			throw new RuntimeException("Did not get SharedFSJob");
		return checkUsername(((SharedFSJob)job).username);
	}

	private boolean checkUsername(String username) {
		return ((NtlmPasswordAuthenticator)ctx.getCredentials()).getUsername().equals(username);
	}

	@Override
	public void close() {
		try {
			ctx.close();
		} catch (CIFSException e) {
			throw new RuntimeException("Cannot close CIFSConnection", e);
		}
	}

	@Override
	public String getImageTitle(Job j) {
		if(!(j instanceof SharedFSJob))
			throw new RuntimeException("Did not get SharedFSJob");
		return ((SharedFSJob)j).remoteBasename;
	}

	@Override
	public ImagePlus createImage(Job j) {
		if(!(j instanceof SharedFSJob))
			throw new RuntimeException("Did not get SharedFSJob");
		SharedFSJob fs = (SharedFSJob)j;
		return createImage(fs.url, fs.series);
	}

	// http://imagej.1557.x6.nabble.com/creating-an-ImagePlus-from-an-InputStream-or-a-byte-td3697788.html
	public ImagePlus createImage(String url, int series) {
		try {
			SmbFile smbFile = new SmbFile(url, ctx);
			if(url.endsWith(".tif") && !url.endsWith("ome.tif")) {
				return openVirtualTiff(smbFile);
			}
			IRandomAccess ira = new SmbFileHandle(smbFile, "r");
			Location.mapFile("bla", ira);
			ImporterOptions options = new ImporterOptions();
			options.clearSeries();
			options.setSeriesOn(series, true);
			options.setId("bla");
			options.setVirtual(true);
			return BF.openImagePlus(options)[0];
		} catch(Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	public static ImagePlus openVirtualTiff(SmbFile smbFile) throws Exception {
		FileInfoVirtualStackFromURL furl = new FileInfoVirtualStackFromURL(smbFile);
		return furl.open();
	}

	public void uploadFile(String localfile, String targetURL) {
		try(
			SmbFile tgtFile = new SmbFile(targetURL, ctx);
			SmbFileOutputStream fos = new SmbFileOutputStream(tgtFile);
			BufferedInputStream in = new BufferedInputStream(new FileInputStream(localfile));
		) {
			final byte[] buffer = new byte[4096];
		    for(int read = in.read(buffer); read >= 0; read = in.read(buffer))
		        fos.write(buffer, 0, read);
		} catch(Exception e) {
			throw new RuntimeException("Cannot upload file " + localfile + " to " + targetURL);
		}
	}

	@Override
	public void uploadStill(Job j, File f) {
		if(!(j instanceof SharedFSJob))
			throw new RuntimeException("Did not get SharedFSJob");
		SharedFSJob fs = (SharedFSJob)j;
		uploadFile(f.getAbsolutePath(), fs.remoteBasename + ".png");
	}

	@Override
	public void uploadVideo(Job j, File f) {
		if(!(j instanceof SharedFSJob))
			throw new RuntimeException("Did not get SharedFSJob");
		SharedFSJob fs = (SharedFSJob)j;
		uploadFile(f.getAbsolutePath(), fs.remoteBasename + ".mp4");

	}

	public static void main(String[] args) {
		new ij.ImageJ();
		String password = "";
		CIFSConnection conn = new CIFSConnection("OICEAD", "bschmid", password);
		conn.createImage("smb://romulus.oice.uni-erlangen.de/users/bschmid/cell.lif", 0).show();
		System.out.println("checkUsername: " + conn.checkUsername("bschmid"));
		conn.close();
	}
}
