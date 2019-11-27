package animation3d.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Base64;

import ij.IJ;
import ij.Prefs;
import ij.gui.GenericDialog;
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

	private Gateway gateway = null;

	public String omeroLogin(String hostname, int port, String userName, String password) {
		long start = System.currentTimeMillis();
		String session = null;
		gateway = new Gateway(new SimpleLogger());
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

	public String startRendering(
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

	public String getState(String processingHost, int processingPort, String basename) {
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

	// TODO implement:
	// read contents of "${basename}.error.txt"
	// maybe get the error automatically with getState:
	// ERROR base64(stack trace)
	public String getError() {
		return null;
	}

	// TODO implement
	// what? the raw video stack? or the mp4? the latter would require ffmpeg to be installed
	public void downloadResult() {

	}

	// TODO put getState and getProgress together (on the server)
	public double getProgress(String processingHost, int processingPort, String basename) {
		Socket socket;
		try {
			socket = new Socket(processingHost, processingPort);
		} catch (UnknownHostException e) {
			throw new RuntimeException("Cannot get progress", e);
		} catch (IOException e) {
			throw new RuntimeException("Cannot get progress", e);
		}

		try {
			PrintStream out = new PrintStream(socket.getOutputStream());
			BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

			out.println("getprogress " + basename);
			double progress = Double.parseDouble(in.readLine().trim());
			return progress;
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

	public void test() throws UnknownHostException, IOException {
		String omeroHost = Prefs.get("Animation3DClient.omeroHost", "");
		String omeroUser = Prefs.get("Animation3DClient.omeroUser", "");
		String processingHost = Prefs.get("Animation3DClient.processingHost", "localhost");
		int processingPort = Prefs.getInt("Animation3DClient.processingHost", 3333);

		GenericDialog gd = new GenericDialog("Animation3DClient");
		gd.addStringField("OMERO_Host", omeroHost, 30);
		gd.addStringField("OMERO_User", omeroUser, 30);
		gd.setEchoChar('*');
		gd.addStringField("OMERO_Password", "", 30);
		gd.addStringField("Processing_Host", processingHost + ":" + processingPort, 30);
		gd.showDialog();
		if(gd.wasCanceled())
			return;

		omeroHost = gd.getNextString();
		omeroUser = gd.getNextString();
		String omeroPass = gd.getNextString();
		String[] tmp = gd.getNextString().split(":");
		processingHost = tmp[0].trim();
		processingPort = Integer.parseInt(tmp[1]);

		Prefs.set("Animation3DClient.omeroHost", omeroHost);
		Prefs.set("Animation3DClient.omeroUser", omeroUser);
		Prefs.set("Animation3DClient.processingHost", processingHost);
		Prefs.set("Animation3DClient.processingPort", processingPort);
		Prefs.savePreferences();

		String session = omeroLogin(omeroHost, 4064, omeroUser, omeroPass);

		String script = "At frame 0 zoom by a factor of 0.7\nFrom frame 0 to frame 100 rotate by 360 degrees horizontally";
		int imageId = 201660;
		int tgtWidth = 800;
		int tgtHeight = 600;

		String basename = startRendering(
				omeroHost, session, imageId, script,
				tgtWidth, tgtHeight,
				processingHost, processingPort);
		IJ.log("basename = " + basename);
		while(true) {
			String state = getState(processingHost, processingPort, basename);
			if(state.startsWith("ERROR")) {
				IJ.error("Error with the rendering");
				return;
			}
			if(state.startsWith("FINISHED")) {
				IJ.log("Done");
				return;
			}
			double progress = getProgress(processingHost, processingPort, basename);
			IJ.log(state + ": " + progress);
			try {
				Thread.sleep(500);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}
}
