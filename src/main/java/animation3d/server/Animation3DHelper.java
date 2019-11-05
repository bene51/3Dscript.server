package animation3d.server;

import java.awt.Color;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import animation3d.renderer3d.BoundingBox;
import animation3d.renderer3d.OpenCLRaycaster;
import animation3d.renderer3d.Renderer3D;
import animation3d.renderer3d.Scalebar;
import animation3d.textanim.Animator;
import ij.CompositeImage;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.measure.Calibration;
import ij.plugin.FolderOpener;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import ij.process.LUT;
import ij.process.ShortProcessor;
import omero.ServerError;
import omero.client;
import omero.api.RenderingEnginePrx;
import omero.api.ServiceFactoryPrx;
import omero.gateway.Gateway;
import omero.gateway.LoginCredentials;
import omero.gateway.SecurityContext;
import omero.gateway.exception.DSAccessException;
import omero.gateway.exception.DSOutOfServiceException;
import omero.gateway.facility.BrowseFacility;
import omero.gateway.facility.RawDataFacility;
import omero.gateway.model.ExperimenterData;
import omero.gateway.model.ImageData;
import omero.gateway.model.PixelsData;
import omero.gateway.rnd.Plane2D;
import omero.log.SimpleLogger;
import omero.model.Length;

public class Animation3DHelper {

	private Renderer3D renderer;

	private ImagePlus image;

	private Job job;

	private Animator animator;

	private boolean cancel = false;

	public Animation3DHelper() {
	}

	public void cancel() {
		this.cancel = true;
		if(animator != null)
			animator.cancelRendering();
	}

	public void setImage(Job j) {
		this.cancel = false;
		this.job = j;

		if(image != null && image.getTitle().equals(Integer.toString(j.imageID))) {
			j.setState(State.OPENED);
			return;
		}

		downloadAndPreprocess(j);
		if(cancel)
			return;

		// not null, open new image and create new renderer & animator
		j.setState(State.OPENING);
		if(image != null) {
			image.close();
			OpenCLRaycaster.close();
		}
		String dir = "/tmp/3Dscript." + j.imageID + "/";
		FolderOpener fo = new FolderOpener();
		fo.sortFileNames(true);
		fo.openAsVirtualStack(true);
		image = fo.openFolder(dir);
		if(image == null)
			throw new RuntimeException("Unable to open image from " + dir);
		System.out.println(image.getOriginalFileInfo());
		System.out.println(image.getOriginalFileInfo().description);
		int nChannels = image.getOriginalFileInfo().displayRanges.length / 2;
		int nSlices = image.getOriginalFileInfo().nImages / nChannels;
		System.out.println("nChannels = " + nChannels);
		System.out.println("nSlices = " + nSlices);
		image.setDimensions(nChannels, nSlices, 1);
		image.setOpenAsHyperStack(true);
		image = new CompositeImage(image);
		// TODO image.setTitle(j.imageID);
		Calibration cal = image.getCalibration();
		System.out.println("pixelwidth = " + cal.pixelWidth);
		System.out.println("pixeldepth = " + cal.pixelDepth);

		System.out.println("nChannels = " + image.getNChannels());
		System.out.println("nSlices = " + image.getNSlices());

		if(cancel)
			return;

		renderer = new Renderer3D(image, image.getWidth(), image.getHeight());
		animator = new Animator(renderer);

		j.setState(State.OPENED);
	}

	// see https://github.com/imagej/imagej-omero/blob/master/src/main/java/net/imagej/omero/DefaultOMEROSession.java
	private void downloadAndPreprocess(Job j) {
		if(cancel)
			return;

		File directory = new File("/tmp/3Dscript." + j.imageID);
		if(directory.exists() && !directory.isDirectory())
			throw new RuntimeException(directory.getAbsolutePath() + " exists but is not a directory");

		// if the directory exists, assume the data is already downloaded
		// and preprocessed
		if(directory.exists())
			return;

		j.setState(State.DOWNLOADING);
		if(!directory.mkdirs())
			throw new RuntimeException("Cannot create directory " + directory.getAbsolutePath());


		client cl = new client(j.host, 4064);

		Gateway gateway = null;
		try {
			gateway = new Gateway(new SimpleLogger());
			// TODO check out which logging options
			LoginCredentials cred = new LoginCredentials(j.sessionID, null, j.host, 4064);

			try {
				ServiceFactoryPrx session = cl.joinSession(j.sessionID);
				session.detachOnDestroy();
			} catch (Exception e2) {
				throw new RuntimeException("Cannot join session", e2);
			}

			ExperimenterData user = null;
			try {
				user = gateway.connect(cred);
			} catch(Exception e) {
				throw new RuntimeException("Cannot connect to OMERO server", e);
			}
			System.out.println("Connected");
			SecurityContext ctx = new SecurityContext(user.getGroupId());

			ImageData image = null;
			try {
				BrowseFacility browse = gateway.getFacility(BrowseFacility.class);
				image = browse.getImage(ctx, j.imageID);
			} catch(Exception e) {
				throw new RuntimeException("Cannot load image " + j.imageID, e);
			}

			PixelsData pixels = image.getDefaultPixels();
			// pixels.getPixelSizeX(null);
			int w = pixels.getSizeX();
			int h = pixels.getSizeY();
			int nChannels = pixels.getSizeC();
			int nSlices   = pixels.getSizeZ();
			int nFrames   = pixels.getSizeT();

			Color[] channelColors = new Color[nChannels];
			int[] channelMin = new int[nChannels]; Arrays.fill(channelMin, -1);
			int[] channelMax = new int[nChannels]; Arrays.fill(channelMax, -1);

			RenderingEnginePrx proxy = null;
			long pixelsId = pixels.getId();
			try {
				proxy = gateway.getRenderingService(ctx, pixelsId);
				proxy.lookupPixels(pixelsId);
			    if (!(proxy.lookupRenderingDef(pixelsId))) {
			        proxy.resetDefaultSettings(true);
			        proxy.lookupRenderingDef(pixelsId);
			    }
			    proxy.load();

			    for(int c = 0; c < nChannels; c++) {
			    	channelMin[c] = (int)proxy.getChannelWindowStart(c);
			    	channelMax[c] = (int)proxy.getChannelWindowEnd(c);
			    	int[] rgba = proxy.getRGBA(c);
			    	channelColors[c] = new Color(rgba[0], rgba[1], rgba[2]);
					System.out.println("Channel " + c + " display range: [" + channelMin[c] + ", " + channelMax[c] + "]");
			    }
			} catch (ServerError e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			} catch (DSOutOfServiceException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}

			System.out.println("w = " + w);
			System.out.println("h = " + h);
			System.out.println("nChannels = " + nChannels);
			System.out.println("nSlices = " + nSlices);
			System.out.println("nFrames = " + nFrames);

			Calibration cal = null;
			try {
				cal = getCalibration(pixels);
			} catch (Exception e) {
				e.printStackTrace();
				cal = new Calibration();
			}

			String ptype = pixels.getPixelType();
			ImageProcessor ip = null;
			if(ptype.equals(PixelsData.UINT8_TYPE) || ptype.equals(PixelsData.INT8_TYPE))
				ip = new ByteProcessor(w, h);
			else if(ptype.equals(PixelsData.UINT16_TYPE) || ptype.equals(PixelsData.INT16_TYPE))
				ip = new ShortProcessor(w, h);
			else
				throw new RuntimeException("8- or 16-bit image required, but found pixel type " + ptype);

			RawDataFacility rdf = null;
			try {
				rdf = gateway.getFacility(RawDataFacility.class);
			} catch(Exception e) {
				throw new RuntimeException("Cannot access raw pixel data", e);
			}

			for(int t = 0; t < nFrames; t++) {
				ImageStack stack = new ImageStack(w, h);
				for(int z = 0; z < nSlices; z++) {
					if(cancel)
						return;
					for(int c = 0; c < nChannels; c++) {
						Plane2D plane = null;
						try {
							plane = rdf.getPlane(ctx, pixels, z, t, c);
						} catch (DSOutOfServiceException | DSAccessException e) {
							throw new RuntimeException("Cannot retrieve plane (t = " + t + ", z = " + z + ", c = " + c + ")", e);
						}
						ip = ip.createProcessor(w, h);
						for(int y = 0, i = 0; y < h; y++) {
							for(int x = 0; x < w; x++, i++) {
								ip.setf(i, (float)plane.getPixelValue(x, y));
							}
						}
						stack.addSlice(ip);
					}
				}
				ImagePlus imp = new ImagePlus("", stack);
				imp.setDimensions(nChannels, nSlices, 1);
				imp.setOpenAsHyperStack(true);
				imp.setCalibration(cal);
				CompositeImage ci = new CompositeImage(imp);
				for(int c = 0; c < nChannels; c++) {
					Color col = channelColors[c];
					System.out.println("channelColors[" + c + "] = " + col);
					LUT lut = LUT.createLutFromColor(col == null ? Color.WHITE : col);
					if(channelMin[c] != -1) lut.min = channelMin[c];
					if(channelMax[c] != -1) lut.max = channelMax[c];
					ci.setChannelLut(lut, c + 1);
				}
				String path = new File(directory, IJ.pad(t, 4) + ".tif").getAbsolutePath();
				IJ.save(ci, path);
			}
		}
		finally {
			try {
				gateway.close();
			} catch(Exception e) {
				e.printStackTrace();
			}
			System.out.println("disconnected from OMERO server");
		}
	}

	public void convertToMP4(int nFrames) {
		job.setState(State.CONVERTING);
		String avifile = job.basename + ".avi";
		String mp4file = job.basename + ".mp4";
		String[] cmd = new String[] {
				"ffmpeg",
				"-y",
				"-i",
				avifile,
				"-vcodec",
				"libx264",
				"-an",
				"-preset",
				"slow",
				"-crf",
				"17",
				"-pix_fmt",
				"yuv420p",
				"-loglevel",
				"debug",
				mp4file
		};
		StringBuffer output = new StringBuffer();
		try {
			Process p = Runtime.getRuntime().exec(cmd);
			BufferedReader in = new BufferedReader(new InputStreamReader(p.getErrorStream()));
		    String line;
		    boolean success = false;
		    while ((line = in.readLine()) != null) {
		    	output.append(line).append('\n');
				if(cancel) {
					p.destroy();
					return;
				}
		        if(line.trim().startsWith(nFrames + " frames successfully decoded, 0 decoding errors"))
		        	success = true;
		    }
			p.waitFor(3, TimeUnit.MINUTES);
			if(!success) {
				IJ.saveString(output.toString(), job.basename + ".error");
				throw new RuntimeException("Error converting to MP4");
			}
		} catch(IOException | InterruptedException e) {
			throw new RuntimeException("Cannot convert to MP4", e);
		}
	}

	private Calibration getCalibration(PixelsData pixels) throws Exception {
		Calibration cal = new Calibration();
		Length lx = pixels.getPixelSizeX(null);
		if(lx == null)
			return cal;

		String unit = lx.getSymbol();
		Length ly = pixels.getPixelSizeY(lx.getUnit());
		Length lz = pixels.getPixelSizeZ(lx.getUnit());

		cal.pixelWidth  = lx.getValue();
		cal.pixelHeight = ly.getValue();
		cal.pixelDepth  = lz.getValue();
		cal.setValueUnit(unit);

		System.out.println(cal);

		return cal;
	}

	public ImagePlus getImage() {
		return image;
	}

	/**
	 * Returns immediately.
	 */
	public void render() {
		job.setState(State.RENDERING);
		String animation = null;
		ImagePlus result = null;
		try {
			String animationFile = job.basename + ".animation.txt";
			animation = loadText(animationFile);
			renderer.setTargetSize(job.w, job.h);
			BoundingBox bb = renderer.getBoundingBox();
			bb.setVisible(job.bbVisible);
			bb.setColor(Color.decode(job.bbColor));
			bb.setWidth(job.bbLinewidth);
			Scalebar sb = renderer.getScalebar();
			sb.setVisible(job.sbVisible);
			sb.setColor(Color.decode(job.sbColor));
			sb.setWidth(job.sbLinewidth);
			sb.setPosition(Scalebar.Position.fromName(job.sbPosition));
			sb.setOffset(job.sbOffset);
			sb.setLength(job.sbLength);
			animator.render(animation);
			result = animator.waitForRendering(5, TimeUnit.MINUTES);
		} catch(Exception e) {
			throw new RuntimeException("Error while rendering", e);
		}
		job.setState(State.SAVING);
		result.getCalibration().fps = 20;
		String outfile = job.basename + ".avi";
		IJ.save(result, outfile);
		convertToMP4(result.getStackSize());
	}

	public double getProgress() {
		if(job.state != State.RENDERING)
			return job.state.start / 100.0;
		int from = animator.getFrom();
		int to = animator.getTo();
		int current = animator.getCurrent();
		double ratio = (double)(current - from + 1) / (double)(to - from + 1);
		return (State.RENDERING.start + ratio * State.RENDERING.duration) / 100.0;
	}

	public State getState() {
		return job.state;
	}

	private String loadText(String file) throws IOException {
		BufferedReader buf = new BufferedReader(new FileReader(file));
		String line = null;
		StringBuffer res = new StringBuffer();
		while((line = buf.readLine()) != null) {
			res.append(line).append("\n");
		}
		buf.close();
		return res.toString();
	}
}
