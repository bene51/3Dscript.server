package animation3d.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Base64;

import ij.IJ;
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
		IJ.run("Record...");
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
			// TODO Auto-generated catch block
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
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public String startRendering(
			String omeroHost,
			String session,
			int imageId,
			String script,
			int tgtWidth, int tgtHeight) {

		Socket socket;
		try {
			socket = new Socket("localhost", 3333);
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

	public void test() throws UnknownHostException, IOException {
		GenericDialog gd = new GenericDialog("Animation3DClient");
		gd.addStringField("OMERO_Host", "", 30);
		gd.addStringField("OMERO_User", "", 30);
		gd.setEchoChar('*');
		gd.addStringField("OMERO_Password", "", 30);
		gd.showDialog();
		if(gd.wasCanceled())
			return;

		String omeroHost = gd.getNextString();
		String omeroUser = gd.getNextString();
		String omeroPass = gd.getNextString();

		String session = omeroLogin(omeroHost, 4064, omeroUser, omeroPass);

		String script = "At frame 0 zoom by a factor of 0.7\nFrom frame 0 to frame 100 rotate by 360 degrees horizontally";
		int imageId = 201660;
		int tgtWidth = 800;
		int tgtHeight = 600;

		String basename = startRendering(
				omeroHost, session, imageId, script, tgtWidth, tgtHeight);
		IJ.log("basename = " + basename);

		try {
			gateway.close();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
