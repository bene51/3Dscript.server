package animation3d.server;

import ij.ImageStack;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;
import omero.gateway.SecurityContext;
import omero.gateway.exception.DSAccessException;
import omero.gateway.exception.DSOutOfServiceException;
import omero.gateway.facility.RawDataFacility;
import omero.gateway.model.PixelsData;
import omero.gateway.rnd.Plane2D;

public class OMEROVirtualStack extends ImageStack {

	private final RawDataFacility rdf;
	private final SecurityContext ctx;

	private final int w, h, d, nChannels, nFrames, N;
	private final PixelsData pixels;

	private final ImageProcessor template;

	public OMEROVirtualStack(RawDataFacility rdf, SecurityContext ctx, PixelsData pixels) {
		this.rdf = rdf;
		this.ctx = ctx;
		this.pixels = pixels;
		this.w = pixels.getSizeX();
		this.h = pixels.getSizeY();
		this.nChannels = pixels.getSizeC();
		this.d = pixels.getSizeZ();
		this.nFrames = pixels.getSizeT();
		this.N = d * nChannels * nFrames;

		String ptype = pixels.getPixelType();
		if(ptype.equals(PixelsData.UINT8_TYPE) || ptype.equals(PixelsData.INT8_TYPE))
			template = new ByteProcessor(1, 1);
		else if(ptype.equals(PixelsData.UINT16_TYPE) || ptype.equals(PixelsData.INT16_TYPE))
			template = new ShortProcessor(1, 1);
		else
			throw new RuntimeException("8- or 16-bit image required, but found pixel type " + ptype);
	}

	/** Does nothing. */
	@Override
	public void addSlice(String sliceLabel, Object pixels) {
	}

	/** Does nothing.. */
	@Override
	public void addSlice(String sliceLabel, ImageProcessor ip) {
	}

	/** Does noting. */
	@Override
	public void addSlice(String sliceLabel, ImageProcessor ip, int n) {
	}

	/** Deletes the specified image, were 1<=n<=nImages. */
	@Override
	public void deleteSlice(int n) {
	}

	/** Deletes the last slice in the stack. */
	@Override
	public void deleteLastSlice() {
	}

   /** Returns the pixel array for the specified slice, were 1<=n<=nslices. */
	@Override
	public Object getPixels(int n) {
		ImageProcessor ip = getProcessor(n);
		if (ip!=null)
			return ip.getPixels();
		else
			return null;
	}

	 /** Assigns a pixel array to the specified slice,
		were 1<=n<=nslices. */
	@Override
	public void setPixels(Object pixels, int n) {
	}

	/** Returns an ImageProcessor for the specified image,
		were 1<=n<=nImages. Returns null if the stack is empty.
	*/
	@Override
	public ImageProcessor getProcessor(int n) {
		if( n < 1 || n > N)
			throw new IllegalArgumentException("Argument out of range: "+n);

		int idx = n - 1;
		// convert n to c, z, t
		// n = t * d * nChannels + z * nChannels + c = nChannels * (t * d + z) + c
		int nPerTimepoint = d * nChannels;
		int t = idx / nPerTimepoint;
		int tmp = idx % nPerTimepoint;
		int z = tmp / nChannels;
		int c = tmp % nChannels;

		Plane2D plane = null;
		try {
			plane = rdf.getPlane(ctx, pixels, z, t, c);
		} catch (DSOutOfServiceException | DSAccessException e) {
			throw new RuntimeException("Cannot retrieve plane (t = " + t + ", z = " + z + ", c = " + c + ")", e);
		}
		ImageProcessor ip = template.createProcessor(w, h);
		for(int y = 0, i = 0; y < h; y++) {
			for(int x = 0; x < w; x++, i++) {
				ip.setf(i, (float)plane.getPixelValue(x, y));
			}
		}
		return ip;
	}

	 /** Returns the number of images in this stack. */
	@Override
	public int getSize() {
		return N;
	}

	/** Returns the label of the Nth image. */
	@Override
	public String getSliceLabel(int n) {
		return "";
	}

	/** Returns null. */
	@Override
	public Object[] getImageArray() {
		return null;
	}

   /** Does nothing. */
	@Override
	public void setSliceLabel(String label, int n) {
	}

	/** Always return true. */
	@Override
	public boolean isVirtual() {
		return true;
	}

   /** Does nothing. */
	@Override
	public void trim() {
	}

	@Override
	public int getWidth() {
		return w;
	}

	@Override
	public int getHeight() {
		return h;
	}
}
