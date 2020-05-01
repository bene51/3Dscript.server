package animation3d.server;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import animation3d.renderer3d.OpenCLRaycaster;
import animation3d.renderer3d.Renderer3D;
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

public class OMEROHelper extends Animation3DHelper {

	private OMEROConnection omeroConnection = null;

	@Override
	public void setImage(Job job) {
		if(!(job instanceof OMEROJob))
			throw new RuntimeException("Did not get OMEROJob");

		OMEROJob j = (OMEROJob)job;

		this.cancel = false;
		this.job = j;

		if(omeroConnection == null || !omeroConnection.checkSession(j.sessionID)) {
			if(omeroConnection != null)
				omeroConnection.close();
			omeroConnection = new OMEROConnection(j.host, j.sessionID, null);
		}

		if(image != null && image.getTitle().equals(Integer.toString(j.imageID))) {
			j.setState(State.OPENED);
			return;
		}

		if(image != null) {
			image.close();
			OpenCLRaycaster.close();
		}

		j.setState(State.OPENING);

		if(cancel)
			return;

		image = omeroConnection.createImage(j.imageID);
		if(image == null)
			throw new RuntimeException("Unable to open image " + j.imageID);

		image.setTitle(Integer.toString(j.imageID));

		if(cancel)
			return;

		renderer = new Renderer3D(image, image.getWidth(), image.getHeight());
		animator = new Animator(renderer);

		j.setState(State.OPENED);
	}

	@Override
	public void uploadResults(Job job) {
		if(!(job instanceof OMEROJob))
			throw new RuntimeException("Did not get OMEROJob");

		if(!job.uploadResults)
			return;

		OMEROJob j = (OMEROJob)job;

		if(cancel)
			return;
		j.setState(State.ATTACHING);
		File file = new File(j.basename + ".mp4");
		Job.Type type = Job.Type.IMAGE;
		if(file.exists()) {
			type = Job.Type.VIDEO;
			j.videoAnnotationId = omeroConnection.createAttachment(j.imageID, file);
		}
		file = new File(j.basename + ".png");
		if(file.exists()) {
			j.imageAnnotationId = omeroConnection.createAttachment(j.imageID, file);
		}
		j.type = type;
	}

	// Old code which is not used any more, but I don't want to delete it, maybe
	// I'll need it at some time
	public void setImageOld(OMEROJob j) {
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
		if(nChannels > 1)
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

	public static void test1(String[] args) throws Exception {
		new ij.ImageJ();
		String dir = "/tmp/3Dscript.214729/";
		// dir = "/tmp/3Dscript.201660/";
		FolderOpener fo = new FolderOpener();
		fo.sortFileNames(true);
		fo.openAsVirtualStack(true);
		ImagePlus image = fo.openFolder(dir);
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
		image = new CompositeImage(image, CompositeImage.COMPOSITE);
		image.show();
	}

	public static void test2(String[] args) throws Exception {
		ImagePlus image = IJ.openImage("C:\\tmp\\3Dscript.214729\\0000.tif");
		Renderer3D renderer = new Renderer3D(image, image.getWidth(), image.getHeight());
		Animator animator = new Animator(renderer);

		String animation = IJ.openAsString("d:/tmp.animation.txt");

		renderer.setTargetSize(600, 450);
		animator.render(animation);
		ImagePlus result = animator.waitForRendering(5, TimeUnit.MINUTES);
		// result.show();
		IJ.save(result, "c:/users/bschmid/Desktop/test.avi");
	}

	/**
	 * https://stackoverflow.com/questions/35988192/java-nio-most-concise-recursive-directory-delete
	 * @param dir
	 * @throws IOException
	 */
	private void deleteRecursively(File dir) {
		Path rootPath = dir.toPath();
		// before you copy and paste the snippet
		// - read the post till the end
		// - read the javadoc to understand what the code will do
		//
		// a) to follow softlinks (removes the linked file too) use
		// Files.walk(rootPath, FileVisitOption.FOLLOW_LINKS)
		//
		// b) to not follow softlinks (removes only the softlink) use
		// the snippet below
		try (Stream<Path> walk = Files.walk(rootPath)) {
		    walk.sorted(Comparator.reverseOrder())
		        .map(Path::toFile)
		        .peek(System.out::println)
		        .forEach(File::delete);
		} catch (IOException e) {
			e.printStackTrace();
		}
		dir.delete();
	}

	// see https://github.com/imagej/imagej-omero/blob/master/src/main/java/net/imagej/omero/DefaultOMEROSession.java
	private void downloadAndPreprocess(OMEROJob j) {
		if(cancel)
			return;

		File directory = new File("/tmp/3Dscript." + j.imageID);
		if(directory.exists() && !directory.isDirectory())
			throw new RuntimeException(directory.getAbsolutePath() + " exists but is not a directory");

		// if the directory exists, assume the data is already downloaded
		// and preprocessed
		if(directory.exists())
			return;

		// j.setState(State.DOWNLOADING);
		if(!directory.mkdirs())
			throw new RuntimeException("Cannot create directory " + directory.getAbsolutePath());


		client cl = new client(j.host, 4064);

		Gateway gateway = null;
		try {
			gateway = new Gateway(new SimpleLogger());
			// TODO check out which logging options
			LoginCredentials cred = new LoginCredentials(j.sessionID, null, j.host, 4064);

			try {
				// TODO are the following lines needed?
				// ServiceFactoryPrx session = cl.joinSession(j.sessionID);
				// session.detachOnDestroy();
			} catch (Exception e2) {
				deleteRecursively(directory);
				throw new RuntimeException("Cannot join session", e2);
			}

			ExperimenterData user = null;
			try {
				user = gateway.connect(cred);
			} catch(Exception e) {
				deleteRecursively(directory);
				throw new RuntimeException("Cannot connect to OMERO server", e);
			}
			System.out.println("Connected");
			SecurityContext ctx = new SecurityContext(user.getGroupId());

			ImageData image = null;
			try {
				BrowseFacility browse = gateway.getFacility(BrowseFacility.class);
				image = browse.getImage(ctx, j.imageID);
			} catch(Exception e) {
				deleteRecursively(directory);
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
					System.out.println("Channel " + c + "color: " + channelColors[c]);
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

}
