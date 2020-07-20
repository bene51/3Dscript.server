package animation3d.server;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import ij.CompositeImage;
import ij.IJ;
import ij.ImagePlus;
import ij.VirtualStack;
import ij.io.FileInfo;
import ij.io.FileOpener;
import ij.io.TiffDecoder;
import ij.process.ByteProcessor;
import ij.process.ColorProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;
import jcifs.smb.SmbFile;
import jcifs.smb.SmbRandomAccessFile;

/** This plugin opens a multi-page TIFF file as a virtual stack. It
	implements the File/Import/TIFF Virtual Stack command. */
public class FileInfoVirtualStackFromURL extends VirtualStack {
	FileInfo[] info;
	int nImages;

	/* Default constructor. */
	public FileInfoVirtualStackFromURL() {}

	/* Constructs a FileInfoVirtualStack from a FileInfo object. */
	public FileInfoVirtualStackFromURL(FileInfo fi) {
		info = new FileInfo[1];
		info[0] = fi;
	}

	public FileInfoVirtualStackFromURL(SmbFile smbFile) {
		String name = null;
		String urlFolder = null;
		InputStream is = null;
		try {
			is = smbFile.getInputStream();
			String url = smbFile.getURL().toString();
			int pos = url.lastIndexOf("/");
			name = url.substring(pos + 1);
			urlFolder = url.substring(0, pos + 1);
		} catch(Exception e) {
			IJ.error("TiffDecoder", e.getMessage());
			return;
		}
		TiffDecoder td = new TiffDecoder(is, name);
		if (IJ.debugMode) td.enableDebugging();
		IJ.showStatus("Decoding TIFF header...");
		try {
			info = td.getTiffInfo();
			info[0].inputStream = new SmbRandomAccessStream(new SmbRandomAccessFile(smbFile, "r"));
//			is.close();
//			info[0].inputStream = null;
//			info[0].url = urlFolder;
		} catch (IOException e) {
			String msg = e.getMessage();
			if (msg==null||msg.equals("")) msg = ""+e;
			IJ.error("TiffDecoder", msg);
			return;
		}
		if (info==null || info.length==0) {
			IJ.error("Virtual Stack", "This does not appear to be a TIFF stack");
			return;
		}
		if (IJ.debugMode)
			IJ.log(info[0].debugInfo);
	}

	public ImagePlus open() {
		FileInfo fi = info[0];
		int n = fi.nImages;
		if (info.length==1 && n>1) {
			info = new FileInfo[n];
			long size = fi.width*fi.height*fi.getBytesPerPixel();
			for (int i=0; i<n; i++) {
				info[i] = (FileInfo)fi.clone();
				info[i].nImages = 1;
				info[i].longOffset = fi.getOffset() + i*(size + fi.gapBetweenImages);
			}
		}
		nImages = info.length;
		FileOpener fo = new FileOpener(info[0] );
		ImagePlus imp = fo.open(false);
		if (nImages==1 && fi.fileType==FileInfo.RGB48) {
			return imp;
		}
		Properties props = fo.decodeDescriptionString(fi);
		ImagePlus imp2 = new ImagePlus(fi.fileName, this);
		imp2.setFileInfo(fi);
		if (imp!=null && props!=null) {
			setBitDepth(imp.getBitDepth());
			imp2.setCalibration(imp.getCalibration());
			imp2.setOverlay(imp.getOverlay());
			if (fi.info!=null)
				imp2.setProperty("Info", fi.info);
			int channels = getInt(props,"channels");
			int slices = getInt(props,"slices");
			int frames = getInt(props,"frames");
			if (channels*slices*frames==nImages) {
				imp2.setDimensions(channels, slices, frames);
				if (getBoolean(props, "hyperstack"))
					imp2.setOpenAsHyperStack(true);
			}
			if (channels>1 && fi.description!=null) {
				int mode = IJ.COMPOSITE;
				if (fi.description.indexOf("mode=color")!=-1)
					mode = IJ.COLOR;
				else if (fi.description.indexOf("mode=gray")!=-1)
					mode = IJ.GRAYSCALE;
				imp2 = new CompositeImage(imp2, mode);
			}
		}
		return imp2;
	}

	int getInt(Properties props, String key) {
		Double n = getNumber(props, key);
		return n!=null?(int)n.doubleValue():1;
	}

	Double getNumber(Properties props, String key) {
		String s = props.getProperty(key);
		if (s!=null) {
			try {
				return Double.valueOf(s);
			} catch (NumberFormatException e) {}
		}
		return null;
	}

	boolean getBoolean(Properties props, String key) {
		String s = props.getProperty(key);
		return s!=null&&s.equals("true")?true:false;
	}

	/** Deletes the specified image, were 1<=n<=nImages. */
	@Override
	public void deleteSlice(int n) {
		if (n<1 || n>nImages)
			throw new IllegalArgumentException("Argument out of range: "+n);
		if (nImages<1) return;
		for (int i=n; i<nImages; i++)
			info[i-1] = info[i];
		info[nImages-1] = null;
		nImages--;
	}

	/** Returns an ImageProcessor for the specified image,
		were 1<=n<=nImages. Returns null if the stack is empty.
	*/
	@Override
	public ImageProcessor getProcessor(int n) {
		if (n<1 || n>nImages)
			throw new IllegalArgumentException("Argument out of range: "+n);
		//if (n>1) IJ.log("  "+(info[n-1].getOffset()-info[n-2].getOffset()));
		info[n-1].nImages = 1; // why is this needed?
		ImagePlus imp = null;
		if (IJ.debugMode) {
			long t0 = System.currentTimeMillis();
			FileOpener fo = new FileOpener(info[n-1]);
			imp = fo.open(false);
			IJ.log("FileInfoVirtualStack: "+n+", offset="+info[n-1].getOffset()+", "+(System.currentTimeMillis()-t0)+"ms");
		} else {
			SmbRandomAccessStream ras = (SmbRandomAccessStream)info[n-1].inputStream;
			ras.seek(0);
			FileOpener fo = new FileOpener(info[n-1]);
			imp = fo.open(false);
		}
		if (imp!=null)
			return imp.getProcessor();
		else {
			int w=getWidth(), h=getHeight();
			IJ.log("Read error or file not found ("+n+"): "+info[n-1].directory+info[n-1].fileName);
			switch (getBitDepth()) {
				case 8: return new ByteProcessor(w, h);
				case 16: return new ShortProcessor(w, h);
				case 24: return new ColorProcessor(w, h);
				case 32: return new FloatProcessor(w, h);
				default: return null;
			}
		}
	 }

	 /** Returns the number of images in this stack. */
	@Override
	public int getSize() {
		return nImages;
	}

	/** Returns the label of the Nth image. */
	@Override
	public String getSliceLabel(int n) {
		if (n<1 || n>nImages)
			throw new IllegalArgumentException("Argument out of range: "+n);
		if (info[0].sliceLabels==null || info[0].sliceLabels.length!=nImages)
			return null;
		else
			return info[0].sliceLabels[n-1];
	}

	@Override
	public int getWidth() {
		return info[0].width;
	}

	@Override
	public int getHeight() {
		return info[0].height;
	}

}
