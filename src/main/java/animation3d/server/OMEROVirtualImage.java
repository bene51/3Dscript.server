package animation3d.server;

import java.awt.Color;
import java.io.File;
import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;

import ij.CompositeImage;
import ij.ImagePlus;
import ij.measure.Calibration;
import ij.process.LUT;
import omero.ServerError;
import omero.api.RawFileStorePrx;
import omero.api.RenderingEnginePrx;
import omero.gateway.Gateway;
import omero.gateway.LoginCredentials;
import omero.gateway.SecurityContext;
import omero.gateway.exception.DSOutOfServiceException;
import omero.gateway.facility.BrowseFacility;
import omero.gateway.facility.DataManagerFacility;
import omero.gateway.facility.RawDataFacility;
import omero.gateway.model.ExperimenterData;
import omero.gateway.model.ImageData;
import omero.gateway.model.PixelsData;
import omero.log.SimpleLogger;
import omero.model.ChecksumAlgorithm;
import omero.model.ChecksumAlgorithmI;
import omero.model.FileAnnotation;
import omero.model.FileAnnotationI;
import omero.model.ImageAnnotationLink;
import omero.model.ImageAnnotationLinkI;
import omero.model.Length;
import omero.model.OriginalFile;
import omero.model.OriginalFileI;
import omero.model.enums.ChecksumAlgorithmSHA1160;

public class OMEROVirtualImage {

	public static final String NAME_SPACE_TO_SET = "oice/3Dscript";

	public static ImagePlus createImage(String host, String sessionID, int imageID) {
		return createImage(host, sessionID, null, imageID);
	}

	private static ImagePlus createImage(String host, String username, String password, int imageID) {

		Gateway gateway = null;
		gateway = new Gateway(new SimpleLogger());
		// TODO check out which logging options
		LoginCredentials cred = new LoginCredentials(username, password, host, 4064);

		try {
			// TODO are the following lines needed?
			// ServiceFactoryPrx session = cl.joinSession(j.sessionID);
			// session.detachOnDestroy();
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
			image = browse.getImage(ctx, imageID);
		} catch(Exception e) {
			throw new RuntimeException("Cannot load image " + imageID, e);
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

		RawDataFacility rdf = null;
		try {
			rdf = gateway.getFacility(RawDataFacility.class);
		} catch(Exception e) {
			throw new RuntimeException("Cannot access raw pixel data", e);
		}

		OMEROVirtualStack stack = new OMEROVirtualStack(rdf, ctx, pixels);

		ImagePlus imp = new ImagePlus("", stack);
		imp.setDimensions(nChannels, nSlices, nFrames);
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
		return ci;
	}

	public static int createAttachment(String host, String username, String password, int imageID, File file) {
		Gateway gateway = null;
		gateway = new Gateway(new SimpleLogger());
		// TODO check out which logging options
		LoginCredentials cred = new LoginCredentials(username, password, host, 4064);

		try {
			// TODO are the following lines needed?
			// ServiceFactoryPrx session = cl.joinSession(j.sessionID);
			// session.detachOnDestroy();
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
			image = browse.getImage(ctx, imageID);
		} catch(Exception e) {
			throw new RuntimeException("Cannot load image " + imageID, e);
		}

		int INC = 262144;
		DataManagerFacility dm = null;
		try {
			dm = gateway.getFacility(DataManagerFacility.class);
		} catch (ExecutionException e) {
			throw new RuntimeException("Cannot create attachment: image id = " + imageID, e);
		}

		//To retrieve the image see above.
		String name = file.getName();
		String absolutePath = file.getAbsolutePath();
		String path = absolutePath.substring(0,
		        absolutePath.length()-name.length());
		String fileMimeType = name.endsWith("mp4")
				? "video/mp4"
				: "image/png";

		//create the original file object.
		OriginalFile originalFile = new OriginalFileI();
		originalFile.setName(omero.rtypes.rstring(name));
		originalFile.setPath(omero.rtypes.rstring(path));
		originalFile.setSize(omero.rtypes.rlong(file.length()));
		final ChecksumAlgorithm checksumAlgorithm = new ChecksumAlgorithmI();
		checksumAlgorithm.setValue(omero.rtypes.rstring(ChecksumAlgorithmSHA1160.value));
		originalFile.setHasher(checksumAlgorithm);
		originalFile.setMimetype(omero.rtypes.rstring(fileMimeType)); // or "application/octet-stream"
		//Now we save the originalFile object
		try {
			originalFile = (OriginalFile) dm.saveAndReturnObject(ctx, originalFile);
		} catch (Exception e) {
			throw new RuntimeException("Cannot create attachment: image id = " + imageID, e);
		}

		//Initialize the service to load the raw data
		RawFileStorePrx rawFileStore = null;
		try {
			rawFileStore = gateway.getRawFileService(ctx);
		} catch (DSOutOfServiceException e) {
			throw new RuntimeException("Cannot create attachment: image id = " + imageID, e);
		}

		long pos = 0;
		int rlen;
		byte[] buf = new byte[INC];
		ByteBuffer bbuf;
		//Open file and read stream
		try (FileInputStream stream = new FileInputStream(file)) {
		    rawFileStore.setFileId(originalFile.getId().getValue());
		    while ((rlen = stream.read(buf)) > 0) {
		        rawFileStore.write(buf, pos, rlen);
		        pos += rlen;
		        bbuf = ByteBuffer.wrap(buf);
		        bbuf.limit(rlen);
		    }
		    originalFile = rawFileStore.save();
		} catch (Exception e) {
			throw new RuntimeException("Cannot create attachment: image id = " + imageID, e);
		} finally {
			try {
				rawFileStore.close();
			} catch(Exception e) {
				e.printStackTrace();
			}
		}
		//now we have an original File in DB and raw data uploaded.
		//We now need to link the Original file to the image using
		//the File annotation object. That's the way to do it.
		FileAnnotation fa = new FileAnnotationI();
		fa.setFile(originalFile);
		fa.setDescription(omero.rtypes.rstring("description")); // The description set above e.g. PointsModel
		fa.setNs(omero.rtypes.rstring(NAME_SPACE_TO_SET)); // The name space you have set to identify the file annotation.

		//save the file annotation.
		try {
			fa = (FileAnnotation) dm.saveAndReturnObject(ctx, fa);
		} catch (Exception e) {
			throw new RuntimeException("Cannot create attachment: image id = " + imageID, e);
		}

		//now link the image and the annotation
		ImageAnnotationLink link = new ImageAnnotationLinkI();
		link.setChild(fa);
		link.setParent(image.asImage());
		//save the link back to the server.
		try {
			link = (ImageAnnotationLink)dm.saveAndReturnObject(ctx, link);
		} catch (Exception e) {
			throw new RuntimeException("Cannot create attachment: image id = " + imageID, e);
		}
		return (int)fa.getId().getValue();
	}

	private static Calibration getCalibration(PixelsData pixels) throws Exception {
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

	public static void main(String[] args) {
		String host = "romulus.oice.uni-erlangen.de";
		String user = "bschmid";
		String pass = "";
		new ij.ImageJ();
		// createImage(host, user,  pass, 201660).show();
		createAttachment(host, user, "", 201660, new File("D:/flybrain.rgb.ffmpeg.mp4"));
	}
}
