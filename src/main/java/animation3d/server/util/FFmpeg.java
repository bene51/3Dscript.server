package animation3d.server.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import fiji.util.gui.GenericDialogPlus;
import ij.IJ;
import ij.Prefs;

public class FFmpeg {

	/**
	 * This will be used for invoking ffmpeg.
	 */
	private static String exepath;

	private static final String URL_FFMPEG = "https://github.com/vot/ffbinaries-prebuilt/releases/download/v4.2.1/";

	private static final String ZIP_FFMPEG_WIN = "ffmpeg-4.2.1-win-64.zip";
	private static final String ZIP_FFMPEG_OSX = "ffmpeg-4.2.1-osx-64.zip";
	private static final String ZIP_FFMPEG_LIN = "ffmpeg-4.2.1-linux-64.zip";

	private static final String EXE_FFMPEG_WIN = "ffmpeg.exe";
	private static final String EXE_FFMPEG_OSX = "ffmpeg";
	private static final String EXE_FFMPEG_LIN = "ffmpeg";

	public static void convertToMP4(String avifile, String mp4file, String logfile, BooleanSupplier cancel) {
		if(exepath == null) {
			IJ.log("Skip conversion to MP4 because ffmpeg wasn't set up correctly");
			return;
		}
		String[] cmd = new String[] {
				exepath,
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
				if(cancel.getAsBoolean()) {
					p.destroy();
					return;
				}
				if(line.trim().startsWith("frames successfully decoded, 0 decoding errors"))
					success = true;
			}
			p.waitFor(3, TimeUnit.MINUTES);
			if(!success) {
				IJ.saveString(output.toString(), logfile);
				throw new RuntimeException("Error converting to MP4");
			}
		} catch(IOException | InterruptedException e) {
			throw new RuntimeException("Cannot convert to MP4", e);
		}
	}

	private static Path getDefaultExePath() {
		String ijdir = IJ.getDirectory("imagej");
		if(ijdir == null)
			ijdir = System.getProperty("java.io.temp");
		return Paths.get(ijdir, getExeFile());
	}

	/**
	 * First checks whether ffmpeg[.exe] is in the path, if not, tries to
	 * find ffmpeg[.exe] where it would download it (i.e. <fijidir>/ffmpeg[.exe], if
	 * it's not there, it returns false and sets exepath = null, otherwise it sets
	 * exepath accordingly.
	 * @return
	 */
	public static boolean testFFmpeg() {
		String exefile = getExeFile();
		try {

			String exe = Prefs.get("Animation3DServer.ffmpeg_path", "");
			if(!exe.isEmpty()) {
				Process proc = Runtime.getRuntime().exec(exe + " -version");
				boolean succ = proc.waitFor() == 0;
				if(succ) {
					System.out.println("ffmpeg executable found");
					exepath = exe;
					return true;
				}
			}

			exe = exefile;
			Process proc = Runtime.getRuntime().exec(exe + " -version");
			boolean succ = proc.waitFor() == 0;
			if(succ) {
				System.out.println("ffmpeg executable found");
				exepath = exe;
				Prefs.set("Animation3DServer.ffmpeg_path", exepath);
				Prefs.savePreferences();
				return true;
			}

			exe = getDefaultExePath().toString();
			proc = Runtime.getRuntime().exec(exe + " -version");
			succ = proc.waitFor() == 0;
			if(succ) {
				System.out.println("ffmpeg executable found");
				exepath = exe;
				Prefs.set("Animation3DServer.ffmpeg_path", exepath);
				Prefs.savePreferences();
				return true;
			}
			System.out.println("ffmpeg executable not found");
			exepath = null;
			return succ;
		} catch(Exception e) {
			return false;
		}
	}

	private static String getZipFile() {
		if(IJ.isWindows())
			return ZIP_FFMPEG_WIN;
		if(IJ.isLinux())
			return ZIP_FFMPEG_LIN;
		if(IJ.isMacOSX())
			return ZIP_FFMPEG_OSX;
		else
			throw new RuntimeException("Cannot determine operating system");
	}

	private static String getExeFile() {
		if(IJ.isWindows())
			return EXE_FFMPEG_WIN;
		if(IJ.isLinux())
			return EXE_FFMPEG_LIN;
		if(IJ.isMacOSX())
			return EXE_FFMPEG_OSX;
		else
			throw new RuntimeException("Cannot determine operating system");
	}

	public static void downloadAndExtractFFmpeg() {
		String zipfile = getZipFile();
		String exefile = getExeFile();
		URL website = null;
		try {
			website = new URL(URL_FFMPEG + zipfile);
		} catch(Exception e) {
			throw new RuntimeException("Cannot download FFmpeg", e);
		}
		String tmpFolder = System.getProperty("java.io.tmpdir");
		Path tmpFile = Paths.get(tmpFolder, zipfile);

		if(Files.exists(tmpFile)) {
			System.out.println(tmpFile + " already exists");
		}
		else {
			try (
					InputStream inputStream = website.openStream();
					ReadableByteChannel readableByteChannel = Channels.newChannel(inputStream);
			) {
				Files.copy(inputStream, tmpFile);
				System.out.println("Successfully downloaded " + tmpFile);
			} catch(Exception e) {
				throw new RuntimeException("Cannot download FFmpeg", e);
			}
		}

		Path exep = getDefaultExePath();
		extractFile(tmpFile, exefile, exep);
		exepath = exep.toAbsolutePath().toString();
	}

	private static void extractFile(Path zipFile, String fileName, Path outputFile) {
		// Wrap the file system in a try-with-resources statement
		// to auto-close it when finished and prevent a memory leak
		try (FileSystem fileSystem = FileSystems.newFileSystem(zipFile, null)) {
			Path fileToExtract = fileSystem.getPath(fileName);
			Files.copy(fileToExtract, outputFile, StandardCopyOption.REPLACE_EXISTING);
			System.out.println("Successfully extracted " + outputFile);
		} catch(IOException e) {
			throw new RuntimeException("Cannot extract " + zipFile, e);
		}
	}

	public static void testAndSetupFFmpeg() {
		testAndSetupFFmpeg(false);
	}

	public static void testAndSetupFFmpeg(boolean dontAsk) {
		if(testFFmpeg())
			return;
		boolean oked = dontAsk;
		if(!oked) {
			oked = IJ.showMessageWithCancel("FFmpeg",
				"FFmpeg, a 3rd party program that is used to\n" +
				"encode uncompressed raw AVI to compressed MP4\n" +
				"videos, was not found.\n" +
				"Please click OK to download it automatically to\n" +
				getDefaultExePath() + ",\n" +
				"or click Cancel to either\n" +
				"enter the path to FFmpeg manually or skip video\n" +
				"compression");
		}
		if(oked) {
			try {
				downloadAndExtractFFmpeg();
			} catch(Exception e) {
				IJ.handleException(e);
				oked = false;
			}
		}
		// if cancelled or download failed
		if(!oked && !dontAsk) {
			GenericDialogPlus gd = new GenericDialogPlus("FFmpeg");
			gd.addMessage("Please enter the path to FFmpeg manually\n" +
						"or click on Cancel to skip video compression.");
			gd.addFileField("Path to FFmpeg", "");
			gd.showDialog();
			if(!gd.wasCanceled()) {
				exepath = gd.getNextString();
				Prefs.set("Animation3DServer.ffmpeg_path", exepath);
				Prefs.savePreferences();
			}
			else {
				exepath = null;
			}
		}
	}

	public static void main(String[] args) {
		new ij.ImageJ();
		testAndSetupFFmpeg();
		ij.IJ.getInstance().quit();
	}
}
