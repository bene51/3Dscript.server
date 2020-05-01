package animation3d.server;

public class SharedFSJob extends Job {

	final String domain, username, password;

	final int series;

	final String remoteBasename;

	/**
	 * smb://server/path,
	 * e.g. smb://romulus.oice.uni-erlangen.de/users/bschmid/cell.lif
	 */
	final String url;

	public SharedFSJob(
			String domain,
			String username,
			String password,
			String url,
			int series,
			String basename, // local
			int w,
			int h,
			int[] frames,
			boolean uploadResults) {
		super(basename, w, h, frames, uploadResults);
		this.domain = domain;
		this.username = username;
		this.password = password;
		this.url = url;
		this.series = series;
		this.remoteBasename = makeBasename(url, series);
	}

	public static String makeBasename(String path, int series) {
		String basename = path.substring(0, path.lastIndexOf('.'));
		basename = basename + "_" + series;
		return basename;
	}

	@Override
	public String toString() {
		return  "{\n" +
			"	domain: " + domain + ",\n" +
			"	username: " + username + ",\n" +
			"	remoteBasename: " + remoteBasename + ",\n" +
			"	url: " + url + ",\n" +
			"	series: " + series + ",\n" +
			"	width: " + w + ",\n" +
			"	height: " + h + ",\n" +
			"	basename: " + basename + ",\n" +
			"	frames: " + (frames == null ? "all" : ScriptAnalyzer.partitionToString(frames)) + ",\n" +
			"	state: " + state + ",\n" +
			"	type: " + type.getType() + ",\n" +
			"}\n";
	}
}
