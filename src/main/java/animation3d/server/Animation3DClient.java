package animation3d.server;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Base64;

import fiji.util.gui.GenericDialogPlus;
import ij.IJ;
import ij.Prefs;
import ij.plugin.AVI_Reader;
import ij.plugin.PlugIn;
import omero.gateway.Gateway;
import omero.gateway.LoginCredentials;
import omero.gateway.exception.DSOutOfServiceException;
import omero.gateway.model.ExperimenterData;
import omero.log.SimpleLogger;

public class Animation3DClient implements PlugIn {

	public static void main(String[] args) {
		new ij.ImageJ();
		new Animation3DClient().run(null);

	}

	public static String omeroLogin(String hostname, int port, String userName, String password) {
		long start = System.currentTimeMillis();
		String session = null;
		Gateway gateway = new Gateway(new SimpleLogger());
		LoginCredentials cred = new LoginCredentials();
        cred.getServer().setHostname(hostname);
        if (port > 0) {
            cred.getServer().setPort(port);
        }
        cred.getUser().setUsername(userName);
        cred.getUser().setPassword(password);
        try {
			ExperimenterData user = gateway.connect(cred);
			session = gateway.getSessionId(user);
		} catch (DSOutOfServiceException e) {
			e.printStackTrace();
		}
        long end = System.currentTimeMillis();
        System.out.println("omero login took "  + (end - start) + " ms");
        return session;
	}

	@Override
	public void run(String arg) {
		try {
			test();
		} catch (UnknownHostException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static String startRendering(
			String omeroHost,
			String session,
			int imageId,
			String script,
			int tgtWidth, int tgtHeight,
			String processingHost, int processingPort) {

		Socket socket;
		try {
			socket = new Socket(processingHost, processingPort);
		} catch (UnknownHostException e) {
			throw new RuntimeException("Cannot start rendering", e);
		} catch (IOException e) {
			throw new RuntimeException("Cannot start rendering", e);
		}

		try {
			PrintStream out = new PrintStream(socket.getOutputStream());
			BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

			out.println(
					"render " + omeroHost + " " +
							session + " " +
							new String(Base64.getUrlEncoder().encode(script.getBytes())) + " " +
							imageId + " " +
							tgtWidth + " " +
							tgtHeight );
			String basename = in.readLine();
			return basename;
		} catch(Exception e) {
			throw new RuntimeException("Cannot start rendering", e);
		} finally {
			try {
				socket.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	// position progress state
	public static String getState(String processingHost, int processingPort, String basename) {
		Socket socket;
		try {
			socket = new Socket(processingHost, processingPort);
		} catch (UnknownHostException e) {
			throw new RuntimeException("Cannot get state", e);
		} catch (IOException e) {
			throw new RuntimeException("Cannot get state", e);
		}

		try {
			PrintStream out = new PrintStream(socket.getOutputStream());
			BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

			out.println("getstate " + basename);
			String state = in.readLine();
			return state;
		} catch(Exception e) {
			throw new RuntimeException("Cannot start rendering", e);
		} finally {
			try {
				socket.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	public static String getStacktrace(String processingHost, int processingPort, String basename) {
		Socket socket;
		try {
			socket = new Socket(processingHost, processingPort);
		} catch (UnknownHostException e) {
			throw new RuntimeException("Cannot get stacktrace", e);
		} catch (IOException e) {
			throw new RuntimeException("Cannot get stacktrace", e);
		}

		try {
			PrintStream out = new PrintStream(socket.getOutputStream());
			BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

			out.println("getstacktrace " + basename);
			String line = in.readLine();
			return new String(Base64.getUrlDecoder().decode(line.getBytes()));
		} catch(Exception e) {
			throw new RuntimeException("Cannot start rendering", e);
		} finally {
			try {
				socket.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	public static File downloadAVI(String processingHost, int processingPort, String basename) {
		Socket socket;
		try {
			socket = new Socket(processingHost, processingPort);
		} catch (UnknownHostException e) {
			throw new RuntimeException("Cannot download result", e);
		} catch (IOException e) {
			throw new RuntimeException("Cannot download result", e);
		}

		BufferedInputStream inStream = null;
		BufferedOutputStream outStream = null;
		try {
			PrintStream out = new PrintStream(socket.getOutputStream());
			inStream = new BufferedInputStream(socket.getInputStream());

			out.println("downloadavi " + basename);

			File f = File.createTempFile(basename, ".avi");
			long fSize = f.length();
			System.out.println("Animation3DClient: download result to " + f.getAbsolutePath());
			outStream = new BufferedOutputStream(new FileOutputStream(f));

			final byte[] buffer = new byte[4096];

			long total = 0;
			for (int read = inStream.read(buffer); read >= 0; read = inStream.read(buffer)) {
		        outStream.write(buffer, 0, read);
		        total += read;
		        System.out.println("Animation3DClient: downloaded "  + total + " bytes out of " + fSize);
			}
			return f;
		} catch(Exception e) {
			throw new RuntimeException("Cannot start rendering", e);
		} finally {
			try {
				if(inStream != null)
					inStream.close();
				if(outStream != null)
					outStream.close();
				socket.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	// TODO implement
	// attach the result to the image in OMERO
	public static void attachResultToImage() {

	}

	public static void test() throws UnknownHostException, IOException {
		String omeroHost = Prefs.get("Animation3DClient.omeroHost", "");
		String omeroUser = Prefs.get("Animation3DClient.omeroUser", "");
		int omeroImageId = Prefs.getInt("Animation3DClient.omeroImageId", 0);
		String processingHost = Prefs.get("Animation3DClient.processingHost", "localhost");
		int processingPort = Prefs.getInt("Animation3DClient.processingHost", 3333);
		String animationScript = Prefs.get("Animation3DClient.animationScript", "");
		int targetWidth = Prefs.getInt("Animation3DClient.targetWidth", 800);
		int targetHeight = Prefs.getInt("Animation3DClient.targetHeight", 600);


		GenericDialogPlus gd = new GenericDialogPlus("Animation3DClient");
		gd.addStringField("OMERO_Host", omeroHost, 30);
		gd.addStringField("OMERO_User", omeroUser, 30);
		gd.setEchoChar('*');
		gd.addStringField("OMERO_Password", "", 30);
		gd.addNumericField("OMERO_Image_ID", omeroImageId, 0);
		gd.addNumericField("Target_Width", targetWidth, 0);
		gd.addNumericField("Target_Height", targetHeight, 0);
		gd.addStringField("Processing_Host", processingHost + ":" + processingPort, 30);
		gd.addFileField("Animation_Script", animationScript, 30);
		gd.showDialog();
		if(gd.wasCanceled())
			return;

		omeroHost = gd.getNextString();
		omeroUser = gd.getNextString();
		String omeroPass = gd.getNextString();
		omeroImageId = (int)gd.getNextNumber();
		targetWidth = (int)gd.getNextNumber();
		targetHeight = (int)gd.getNextNumber();
		String[] tmp = gd.getNextString().split(":");
		processingHost = tmp[0].trim();
		processingPort = Integer.parseInt(tmp[1]);
		animationScript = gd.getNextString();

		Prefs.set("Animation3DClient.omeroHost", omeroHost);
		Prefs.set("Animation3DClient.omeroUser", omeroUser);
		Prefs.set("Animation3DClient.omeroImageId", omeroImageId);
		Prefs.set("Animation3DClient.processingHost", processingHost);
		Prefs.set("Animation3DClient.processingPort", processingPort);
		Prefs.set("Animation3DClient.targetWidth", targetWidth);
		Prefs.set("Animation3DClient.targetHeight", targetHeight);
		Prefs.set("Animation3DClient.animationScript", animationScript);
		Prefs.savePreferences();

		String session = omeroLogin(omeroHost, 4064, omeroUser, omeroPass);

		String script = IJ.openAsString(animationScript);

		String basename = startRendering(
				omeroHost, session, omeroImageId, script,
				targetWidth, targetHeight,
				processingHost, processingPort);
		IJ.log("basename = " + basename);
		while(true) {
			String positionProgressState = getState(processingHost, processingPort, basename);
			String[] toks = positionProgressState.split(" ");
			int position = Integer.parseInt(toks[0]);
			double progress = Double.parseDouble(toks[1]);
			String state = toks[2];
			if(state.startsWith("ERROR")) {
				String msg = "An error happened during rendering\n";
				msg += getStacktrace(processingHost, processingPort, basename);
				IJ.log(msg);
				return;
			}
			if(state.startsWith("FINISHED")) {
				IJ.log("Done");
				break;
			}
			IJ.log(state + ": " + progress);
			try {
				Thread.sleep(500);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		File f = downloadAVI(processingHost, processingPort, basename);
		AVI_Reader reader = new AVI_Reader();
		reader.setVirtual(false);
		reader.displayDialog(false);
		reader.run(f.getAbsolutePath());
		reader.getImagePlus().show();
	}
}
