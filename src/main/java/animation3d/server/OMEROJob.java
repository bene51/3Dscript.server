package animation3d.server;

public class OMEROJob extends Job {

	final int imageID;
	final String host;
	final String username, password;

	private String sessionID;

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
		this.username = null;
		this.password = null;
	}

	public OMEROJob(String host,
			String username,
			String password,
			String basename,
			int imageID,
			int w,
			int h,
			int[] frames,
			boolean uploadResults) {
		super(basename, w, h, frames, uploadResults);
		this.host = host;
		this.sessionID = null;
		this.username = username;
		this.password = password;
		this.imageID = imageID;
	}

	public String getSessionID() {
		return sessionID;
	}

	public void setSessionID(String sessionID) {
		this.sessionID = sessionID;
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
