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

import animation3d.renderer3d.Progress;
import omero.gateway.Gateway;
import omero.gateway.LoginCredentials;
import omero.gateway.exception.DSOutOfServiceException;
import omero.gateway.model.ExperimenterData;
import omero.log.SimpleLogger;

public class Animation3DClient {

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
		} finally {
//			try {
//				gateway.close();
//			} catch (Exception e) {
//				e.printStackTrace();
//			}
		}
        long end = System.currentTimeMillis();
        System.out.println("omero login took "  + (end - start) + " ms");
        return session;
	}

	// renderSharedFS <urlencode(script)> user[@domain] urlencode(password) url:series,url:series <width> <height> [frames=framerange] [uploadResults=false]
	// where url = smb://romulus.oice.uni-erlangen.de/users/bschmid/cell.lif
	// where series = 1-3+5-7+8+10
	public static String[] startRendering(
			String userAtDomain,
			String password,
			String urlsAndSeries,
			String script, String frameRange,
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

			String command =
					"renderSharedFS " + new String(Base64.getUrlEncoder().encode(script.getBytes())) + " " +
							userAtDomain + " " +
							new String(Base64.getUrlEncoder().encode(password.getBytes())) + " " +
							urlsAndSeries + " " +
							tgtWidth + " " +
							tgtHeight;
			if(frameRange != null)
				command = command + " frames=" + frameRange;

			out.println(command);
			// IJ.log(command);
			String[] basenames = in.readLine().split("\\+");
			return basenames;
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

	public static String[] startRendering(
			String omeroHost,
			String session,
			int[] imageId,
			String script, String frameRange,
			int tgtWidth, int tgtHeight,
			String processingHost, int processingPort,
			String[] basenames) {

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

			StringBuilder imString = new StringBuilder();
			imString.append(imageId[0]);
			for(int i = 1; i < imageId.length; i++)
				imString.append("+").append(imageId[i]);

			String command =
					"renderOMERO " + omeroHost + " " +
							session + " " +
							new String(Base64.getUrlEncoder().encode(script.getBytes())) + " " +
							imString.toString() + " " +
							tgtWidth + " " +
							tgtHeight;
			if(frameRange != null)
				command = command + " frames=" + frameRange;
			if(basenames != null)
				command = command + " basenames=" + String.join("+", basenames);
			out.println(command);
			// IJ.log(command);
			basenames = in.readLine().split("\\+");
			return basenames;
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

	public static long getAVISize(String processingHost, int processingPort, String basename) {
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

			out.println("getavisize " + basename);
			String line = in.readLine();
			return Long.parseLong(line);
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
		return downloadAVI(processingHost, processingPort, basename, null, 0);
	}

	public static File downloadAVI(String processingHost, int processingPort, String basename, Progress progress, long expectedSize) {
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
			System.out.println("Animation3DClient: download result to " + f.getAbsolutePath());
			outStream = new BufferedOutputStream(new FileOutputStream(f));

			final byte[] buffer = new byte[4096];

			long total = 0;
			int c = 0;
			for (int read = inStream.read(buffer); read >= 0; read = inStream.read(buffer)) {
				outStream.write(buffer, 0, read);
				total += read;
				if (progress != null && c++ % 100 == 0)
					progress.setProgress((double) total / expectedSize);
			}
			progress.setProgress(0.99);
			return f;
		} catch (Exception e) {
			throw new RuntimeException("Cannot start rendering", e);
		} finally {
			try {
				if (inStream != null)
					inStream.close();
				if (outStream != null)
					outStream.close();
				socket.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	public static File downloadPNG(String processingHost, int processingPort, String basename) {
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

			out.println("downloadpng " + basename);

			File f = File.createTempFile(basename, ".png");
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
}
