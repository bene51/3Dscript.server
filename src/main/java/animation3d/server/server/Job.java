package animation3d.server.server;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;

import animation3d.server.util.ScriptAnalyzer;

public abstract class Job {

	public static enum Type {
		IMAGE("image"),
		VIDEO("video");

		public final String s;

		Type(String s) {
			this.s = s;
		}

		public String getType() {
			return s;
		}
	}

	protected final int w, h;

	protected final String basename;

	protected final int[] frames;

	protected State state;

	final boolean uploadResults;

	protected Type type = Type.VIDEO;

	public Job(String basename,
			int w,
			int h,
			int[] frames,
			boolean uploadResults) {
		this.basename = basename;
		this.w = w;
		this.h = h;
		this.frames = frames;
		this.uploadResults = uploadResults;
	}

	@Override
	public String toString() {
		return  "{\n" +
			"	width: " + w + ",\n" +
			"	height: " + h + ",\n" +
			"	basename: " + basename + ",\n" +
			"	frames: " + (frames == null ? "all" : ScriptAnalyzer.partitionToString(frames)) + ",\n" +
			"	state: " + state + ",\n" +
			"	type: " + type.getType() + ",\n" +
			"}\n";
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
