package animation3d.server;

import ij.ImageStack;
import ij.process.Blitter;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;

public class ResizedVirtualStack extends ImageStack {

	/** The desired width of this stack */
	private final int targetWidth;

	/** The desired height of this tack */
	private final int targetHeight;

	/** The number of original planes to combine into a new plane */
	private final int nTargetPlanesPerZ;

	/** The number of channels in the original stack */
	private final int nChannelsOriginal;

	/** Then number of planes in the original stack */
	private final int nPlanesOriginal;

	/** The original stack (the one to be resized) */
	private final ImageStack original;

	/** The number of planes in the resized stack */
	private final int nTargetPlanes;

	/** The number of slices in the resized stack */
	private final int N;

	private final int nT;


	public ResizedVirtualStack(int targetWidth,
			int targetHeight,
			int nTargetPlanesPerZ,
			int nPlanesOriginal,
			int nChannelsOriginal,
			ImageStack original) {
		super();
		this.targetWidth = targetWidth;
		this.targetHeight = targetHeight;
		this.nTargetPlanesPerZ = nTargetPlanesPerZ;
		this.nPlanesOriginal = nPlanesOriginal;
		this.nChannelsOriginal = nChannelsOriginal;
		this.original = original;
		int nTnC = original.getSize() / nPlanesOriginal;
		this.nT = nTnC / nChannelsOriginal;
		this.nTargetPlanes = (int)Math.ceil((double)nPlanesOriginal / nTargetPlanesPerZ);
		this.N = nTnC * nTargetPlanes;
	}

	public int getNPlanes() {
		return nTargetPlanes;
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

		// dim[0] = w, dim[1] = h, dim[2] = nC, dim[3] = d, dim[4] = nT
		// ((n-1)%dim[2])+1;
		int channel = ((n-1) % nChannelsOriginal) + 1;
		// (((n-1)/dim[2])%dim[3])+1;
		int plane = (((n - 1) / nChannelsOriginal) % nTargetPlanes) + 1;
		// (((n-1)/(dim[2]*dim[3]))%dim[4])+1;
		int frame = (((n - 1) / (nChannelsOriginal * nTargetPlanes)) % nT) + 1;

		// (frame-1)*nChannels*nSlices + (slice-1)*nChannels + channel;
		int originalPlane = (plane - 1) * nTargetPlanesPerZ + 1;
		int originalIndex = (frame - 1) * nChannelsOriginal * nPlanesOriginal + (originalPlane - 1) * nChannelsOriginal + channel;

		ImageProcessor ip = original.getProcessor(originalIndex);
		ImageProcessor averaged = ip.convertToFloat();
		int nToAverage = 1;
		for(int i = 1; i < nTargetPlanesPerZ; i++) {
			int itmp = originalIndex + i * nChannelsOriginal;
			if(itmp > nPlanesOriginal)
				break;
			ImageProcessor tmp = original.getProcessor(itmp);
			averaged.copyBits(tmp, 0, 0, Blitter.ADD);
			nToAverage++;
		}
		if(nToAverage == 1)
			return ip.resize(targetWidth, targetHeight);

		averaged.multiply(1.0 / nToAverage);
		averaged = averaged.resize(targetWidth, targetHeight);
		if(ip instanceof ByteProcessor)
			return averaged.convertToByte(false);
		else if(ip instanceof ShortProcessor)
			return averaged.convertToShort(false);
		else
			throw new RuntimeException("8-bit or 16-bit image expected");
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
		return targetWidth;
	}

	@Override
	public int getHeight() {
		return targetHeight;
	}
}
