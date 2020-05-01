package animation3d.server;

public class OMEROJob extends Job {

	final int imageID;
	final String host, sessionID;

	int videoAnnotationId = -1;
	int imageAnnotationId = -1;


	public OMEROJob(String host,
			String sessionID,
			String basename,
			int imageID,
			int w,
			int h,
			int[] frames,
			boolean uploadResults) {
		super(basename, w, h, frames, uploadResults);
		this.host = host;
		this.sessionID = sessionID;
		this.imageID = imageID;
	}

	@Override
	public String toString() {
		return  "{\n" +
			"	imageID: " + imageID + ",\n" +
			"	width: " + w + ",\n" +
			"	height: " + h + ",\n" +
			"	host: " + host + ",\n" +
			"	basename: " + basename + ",\n" +
			"	frames: " + (frames == null ? "all" : ScriptAnalyzer.partitionToString(frames)) + ",\n" +
			"	state: " + state + ",\n" +
			"	videoAnnotationId: " + videoAnnotationId + ",\n" +
			"	imageAnnotationId: " + imageAnnotationId + ",\n" +
			"	type: " + type.getType() + ",\n" +
			"}\n";
	}
}
