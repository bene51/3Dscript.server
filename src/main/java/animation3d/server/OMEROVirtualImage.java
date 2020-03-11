package animation3d.server;

import java.awt.Color;
import java.util.Arrays;

import ij.CompositeImage;
import ij.ImagePlus;
import ij.measure.Calibration;
import ij.process.LUT;
import omero.ServerError;
import omero.api.RenderingEnginePrx;
import omero.gateway.Gateway;
import omero.gateway.LoginCredentials;
import omero.gateway.SecurityContext;
import omero.gateway.exception.DSOutOfServiceException;
import omero.gateway.facility.BrowseFacility;
import omero.gateway.facility.RawDataFacility;
import omero.gateway.model.ExperimenterData;
import omero.gateway.model.ImageData;
import omero.gateway.model.PixelsData;
import omero.log.SimpleLogger;
import omero.model.Length;

public class OMEROVirtualImage extends ImagePlus {

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
		createImage(host, user,  pass, 201660).show();
	}
}
