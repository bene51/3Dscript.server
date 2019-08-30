package animation3d.server;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;

public class Job {

	final int imageID;

	final int w, h;

	final String host, sessionID, basename;

	State state;

	public Job(String host, String sessionID, String basename, int imageID, int w, int h) {
		this.host = host;
		this.sessionID = sessionID;
		this.basename = basename;
		this.imageID = imageID;
		this.w = w;
		this.h = h;
	}

	public void setState(State state) {
		this.state = state;
	}

	public static void main(String[] args) throws IOException, InterruptedException {
//		new ij.ImageJ();
//		String dir = "d:/flybrain/";
//		FolderOpener fo = new FolderOpener();
//		fo.sortFileNames(true);
//		fo.openAsVirtualStack(true);
//		ImagePlus imp = fo.openFolder(dir);
////		imp.setDimensions(2, 57, 3);
////		imp.setOpenAsHyperStack(true);
//		imp.show();
		System.out.println("output1");
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		PrintStream backup = System.out;
		System.setOut(new PrintStream(out));
		System.out.println("output2");
		Process p = Runtime.getRuntime().exec("cmd /c dir c:\\");
		BufferedReader buf = new BufferedReader(new InputStreamReader(p.getInputStream()));
		String line = null;
		while((line = buf.readLine()) != null)
			System.out.println(line);
		System.err.println(out.toString());

		System.setOut(backup);
	}
}
