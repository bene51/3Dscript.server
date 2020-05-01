package animation3d.server;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

import ij.IJ;
import ij.plugin.PlugIn;

public class Animation3DServer implements PlugIn {

	public static void main(String[] args) {
		Animation3DServer server = new Animation3DServer();
		server.start();
	}

	// render <host> <sessionid> <script> <imageid> <target width> <target height> frames=<frames>
	public static void oneTimeRender(String line) throws Exception {
		Job[] jobs = createJobsFromLine(line);
		Animation3DHelper helper = null;
		if(line.startsWith("renderOMERO"))
			helper = new OMEROHelper();
		else if(line.startsWith("renderSharedFS"))
			helper = new SharedFSHelper();
		for(Job job : jobs) {
			helper.setImage(job);
			helper.render();
			helper.uploadResults(job);
			helper.saveJobInfo(job);
			job.setState(animation3d.server.State.FINISHED);
		}
	}

	private static void testResourceDiscovery() {
		AtomicBoolean shutdown = new AtomicBoolean(false);
		MulticastReceiver multicastReceiver = new MulticastReceiver(shutdown);
		multicastReceiver.start();
	}

	@Override
	public void run(String arg) {
		start();
	}

	static final int PORT = 3333;

	private final AtomicBoolean shutdown = new AtomicBoolean(false);

	private LinkedList<Job> queue = new LinkedList<Job>();

	private HashMap<String, Job> finished = new HashMap<String, Job>();

	private long lastAccessed = 0;

	private MulticastReceiver multicastReceiver;

	public void start() {
		startConsumerThread();

		multicastReceiver = new MulticastReceiver(shutdown);
		multicastReceiver.start();

		ServerSocket server = null;
		try {
			server = new ServerSocket(PORT);
			server.setSoTimeout(10000);
			lastAccessed = System.currentTimeMillis();
		} catch(Exception e) {
			IJ.handleException(e);
			return;
		}
		System.out.println("Waiting for connection...");
		while(!shutdown.get()) {
			Socket socket = null;
			BufferedReader in = null;
			long time = System.currentTimeMillis();
//			if(time - lastAccessed > 5 * 60 * 1000)
//				shutdown.set(true);
			try {
				socket = server.accept();
				lastAccessed = time;
//				System.out.println("Accepted connection from " + socket.getInetAddress());
				in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
				String line = in.readLine();
//				System.out.println(line);

				if(line.startsWith("shutdown")) {
					shutdown();
				}
				// render <host> <sessionid> <script> <imageid> <target width> <target height> frames=<frames>
				else if(line.startsWith("render")) {
					// TODO check how many jobs in queue, if more than X, reject and tell cient to come back later
					try {
						Job[] jobs = createJobsFromLine(line);
						String[] basenames = new String[jobs.length];
						for(int j = 0; j < jobs.length; j++) {
							Job job = jobs[j];
							job.setState(State.QUEUED);
							basenames[j] = job.basename;
							synchronized(this) {
	//							System.out.println("  server: queue new job");
								queue.add(job);
	//							System.out.println("  server: notify");
								notify();
	//							System.out.println("  server: notified");
							}
						}
						PrintStream out = new PrintStream(socket.getOutputStream());
						out.println(String.join("+", basenames));
						out.close();
					} catch(Exception e) {
						// TODO handle exception
						e.printStackTrace();
					}
				}
				// cancel <basename>
				else if(line.startsWith("cancel")) {
					String rem = line.substring(line.indexOf(' ')).trim();
					String[] basenames = rem.split(" ");
					cancel(basenames);
					PrintStream out = new PrintStream(socket.getOutputStream());
					out.println();
					out.close();
				}
				// return information about:
				// - how many jobs are before the one corresponding to basename
				// - how many jobs are afterwards
				// - what is the state of each job
				// - what is the progress of each job
				// return string:
				// position;basename:status:progress;basename:status:progress;...
				else if(line.startsWith("getoverallstate")) {
					String basename = line.substring(line.indexOf(' ')).trim();
					String state = getOverallState(basename);
					PrintStream out = new PrintStream(socket.getOutputStream());
					out.println(state.toString());
					out.close();
				}
				// getstate <basename>
				else if(line.startsWith("getstate")) {
					String basename = line.substring(line.indexOf(' ')).trim();
					String state = getState(basename);
					PrintStream out = new PrintStream(socket.getOutputStream());
					out.println(state.toString());
					out.close();
				}
				// getstacktrace <basename>
				else if(line.startsWith("getstacktrace")) {
					String basename = line.substring(line.indexOf(' ')).trim();
					String st = getStackTrace(basename);
					PrintStream out = new PrintStream(socket.getOutputStream());
					out.println(new String(Base64.getUrlEncoder().encode(st.getBytes())));
					out.close();
				}
				else if(line.startsWith("downloadavi")) {
					String basename = line.substring(line.indexOf(' ')).trim();
					sendAVI(socket, basename);
				}
				else if(line.startsWith("downloadpng")) {
					String basename = line.substring(line.indexOf(' ')).trim();
					sendPNG(socket, basename);
				}
				else if(line.startsWith("attachmentid")) {
					String basename = line.substring(line.indexOf(' ')).trim();
					String aid = getTypeAndAttachmentId(basename);
					PrintStream out = new PrintStream(socket.getOutputStream());
					out.println(aid);
					out.close();
				}

				in.close();
				socket.close();
//				System.out.println("Closed connection");
			} catch(SocketTimeoutException e) {
				System.out.println(e.getMessage());
			} catch(Exception e) {
				IJ.handleException(e);
				if(server != null) {
					try {
						server.close();
					} catch (IOException e1) { }
				}
				return;
			}
		}

		if(server != null) {
			try {
				server.close();
			} catch (IOException e) {
			}
		}
		System.out.println("Server shut down");
		System.exit(0);
	}

	private static int[] parsePlusMinusRange(String s) {
		ArrayList<Integer> indices = new ArrayList<Integer>();
		String[] toks = s.split("\\+");
		for(String tok : toks) {
			int p = tok.indexOf('-');
			if(p < 0)
				indices.add(Integer.parseInt(tok));
			else {
				int from = Integer.parseInt(tok.substring(0, p).trim());
				int to = Integer.parseInt(tok.substring(p + 1).trim());
				for(int i = from; i <= to; i++)
					indices.add(i);
			}
		}
		int[] ret = new int[indices.size()];
		for(int i = 0; i < ret.length; i++)
			ret[i] = indices.get(i);
		return ret;
	}

	private Animation3DHelper omeroHelper = new OMEROHelper();
	private Animation3DHelper sharedFSHelper = new SharedFSHelper();

	private Job currentJob = null;

	private Animation3DHelper getHelper(Job job) {
		if(job instanceof OMEROJob)
			return omeroHelper;
		if(job instanceof SharedFSJob)
			return sharedFSHelper;
		return null;
	}

	// render <host> <session> <urlencode(script)> <imageId1+imageId2+...> <width> <height> [frames=framerange] [basenames=basename1+basename2+...] [createAttachments=true|false]
	private static Job[] createOMEROJobsFromLine(String line) throws Exception {
		String[] toks = line.split(" ");
		String host = toks[1];
		String sessionid = toks[2];
		String script = new String(Base64.getUrlDecoder().decode(toks[3]));

		String imageidString = toks[4];
		int w = Integer.parseInt(toks[5]);
		int h = Integer.parseInt(toks[6]);

		// optional arguments:
		int[] frames = null;
		String[] basenames = null;
		boolean createAttachments = true;
		boolean basenamesSupplied = false;
		for(int i = 7; i < toks.length; i++) {
			String[] keyval = toks[i].split("=");
			if(toks[0].equals("frames"))
				frames = ScriptAnalyzer.partitionFromString(keyval[1]);
			if(toks[0].equals("createattachments"))
				createAttachments = Boolean.parseBoolean(keyval[1]);
			if(toks[0].equals("basenames")) {
				basenamesSupplied = true;
				basenames = keyval[1].split("\\+");
			}
		}

		String[] idToks = imageidString.split("\\+");
		if(!basenamesSupplied)
			basenames = new String[idToks.length];

		Job[] jobs = new Job[idToks.length];
		for(int i = 0; i < idToks.length; i++) {
			String idTok = idToks[i];
			int imageid = Integer.parseInt(idTok);
			String basename = null;
			if(basenamesSupplied)
				basename = basenames[i];
			else {
				basename = Files.createTempDirectory("3DScript").toFile().getAbsolutePath() + File.separator + new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss").format(new Date());
				basenames[i] = basename;
			}
			File scriptfile = new File(basename + ".animation.txt");
			IJ.saveString(script, scriptfile.getAbsolutePath());

			Job job = new OMEROJob(host,
					sessionid,
					basename,
					imageid,
					w, h,
					frames,
					createAttachments);
			jobs[i] = job;
		}
		return jobs;
	}

	// render <urlencode(script)> user[@domain] urlencode(password) url|series,url:series <width> <height> [frames=framerange] [uploadResults=false]
	// where url = smb://romulus.oice.uni-erlangen.de/users/bschmid/cell.lif
	// where series = 1-3+5-7+8+10
	private static Job[] createSharedFSJobsFromLine(String line) throws Exception {
		String[] toks = line.split(" ");
		String script = new String(Base64.getUrlDecoder().decode(toks[1]));
		String userdomain = toks[2];
		String password = new String(Base64.getUrlDecoder().decode(toks[3]));
		String urlsAndSeries = toks[4];

		int w = Integer.parseInt(toks[5]);
		int h = Integer.parseInt(toks[6]);

		// optional arguments:
		int[] frames = null;
		boolean uploadResults = true;
		for(int i = 7; i < toks.length; i++) {
			String[] keyval = toks[i].split("=");
			if(toks[0].equals("frames"))
				frames = ScriptAnalyzer.partitionFromString(keyval[1]);
			if(toks[0].equals("uploadresults"))
				uploadResults = Boolean.parseBoolean(keyval[1]);
		}

		String domain = "";
		String username = userdomain;
		int atIndex = userdomain.indexOf('@');
		if(atIndex >= 0) {
			username = userdomain.substring(0, atIndex);
			domain = userdomain.substring(atIndex + 1);
		}

		ArrayList<Job> jobs = new ArrayList<Job>();

		String[] ptoks = urlsAndSeries.split(",");
		for(String ptok : ptoks) {
			int colon = ptok.indexOf('|');
			String url = ptok.substring(0, colon);
			int[] series = parsePlusMinusRange(ptok.substring(colon + 1));
			for(int s : series) {
				String basename = Files.createTempDirectory("3DScript").toFile().getAbsolutePath() + File.separator + new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss").format(new Date());
				Job j = new SharedFSJob(domain, username, password, url, s, basename, w, h, frames, uploadResults);
				jobs.add(j);
				File scriptfile = new File(j.basename + ".animation.txt");
				IJ.saveString(script, scriptfile.getAbsolutePath());
			}
		}

		return jobs.toArray(new Job[] {});
	}

	private static Job[] createJobsFromLine(String line) throws Exception {
		if(line.startsWith("renderSharedFS"))
			return createSharedFSJobsFromLine(line);
		if(line.startsWith("renderOMERO"))
			return createOMEROJobsFromLine(line);
		return null;
	}

	private void startConsumerThread() {
		new Thread() {
			@Override
			public void run() {
				System.out.println("  consumer: thread started");
				while(!shutdown.get()) {
					synchronized(Animation3DServer.this) {
						while(queue.isEmpty()) {
							try {
								System.out.println("  consumer: waiting for next job");
								Animation3DServer.this.wait();
							} catch(InterruptedException e) {
								e.printStackTrace();
							}
						}
						currentJob = queue.removeFirst();
						System.out.println("  consumer: polled job");
					}
					if(currentJob != null) {
//						PrintStream stdout = null;
//						PrintStream stderr = null;
//						PrintStream backupstdout = System.out;
//						PrintStream backupstderr = System.err;
						try {
//							stdout = new PrintStream(new FileOutputStream(currentJob.basename + ".stdout.txt"));
//							stderr = new PrintStream(new FileOutputStream(currentJob.basename + ".stderr.txt"));
//							System.setOut(stdout);
//							System.setErr(stderr);
							Animation3DHelper helper = getHelper(currentJob);
							helper.setImage(currentJob);
							System.out.println("  consumer: Rendering new job");
							helper.render();
							System.out.println("  consumer: Rendered new job");
							helper.uploadResults(currentJob);
							helper.saveJobInfo(currentJob);
							currentJob.setState(animation3d.server.State.FINISHED);
							System.out.println("  consumer: Finished");
							// TODO inform cancel that it is done
//							System.exit(0);

						} catch(Throwable e) {
							e.printStackTrace();
							currentJob.setState(animation3d.server.State.ERROR);
							PrintWriter out = null;
							try {
								out = new PrintWriter(new FileWriter(currentJob.basename + ".err"));
								out.println(e.getMessage());
								e.printStackTrace(out);
							} catch(Exception ex) {
								// Ignore exceptions, if we are not able to write the stacktrace,
								// we'll still see 'ERROR'
							} finally {
								out.close();
							}
						} finally {
//							System.setOut(backupstdout);
//							System.setErr(backupstderr);
//							if(stdout != null)
//								stdout.close();
//							if(stderr != null)
//								stderr.close();
							synchronized(currentJob) {
								currentJob.notifyAll();
							}
							System.out.println("now currentJob is really done");
						}
					}
				}
			}
		}.start();
	}

	public void shutdown() {
		shutdown.set(true);
	}

	public synchronized String getStackTrace(String basename) {
		File f = new File(basename + ".err");
		if(!f.exists())
			return "";
		return IJ.openAsString(f.getAbsolutePath());
	}

	// position progress state
	public synchronized String getState(String basename) {
		if(currentJob != null && currentJob.basename.equals(basename)) {
			String state = currentJob.state.toString();
			int position = 0;
			double progress = getHelper(currentJob).getProgress();
			return position + " " + progress + " " + state;
		}
		int idx = 1; // start with 1 because currentJob is index 0
		for(Job j : queue) {
			if(j.basename.equals(basename)) {
				double progress = 0;
				String state = j.state.toString();
				return idx + " " + progress + " " + state;
			}
			idx++;
		}
		idx = -1;
		double progress = 1;
		return idx + " " + progress + " " + State.FINISHED.toString();
	}

	// position;basename:status:progress;basename:status:progress;...
	public synchronized String getOverallState(String basename) {
		StringBuffer buf = new StringBuffer();
		Job job = currentJob;
		int indexOfBasename = -1;
		int idx = 0;
		if(job != null) {
			buf.append(job.basename).append(":")
					.append(job.state.toString()).append(":")
					.append(getHelper(currentJob).getProgress()).append(";");
			if(job.basename.equals(basename))
				indexOfBasename = idx;
			idx++;
		}
		for(Job j : queue) {
			buf.append(j.basename).append(":")
				.append(j.state.toString()).append(":")
				.append("0").append(";");
			if(j.basename.equals(basename))
				indexOfBasename = idx;
			idx++;
		}
		buf.insert(0, indexOfBasename);
		if(buf.charAt(buf.length() - 1) == ';')
			buf.deleteCharAt(buf.length() - 1);
		return buf.toString();
	}

	public void sendAVI(Socket socket, String basename) {
		File f = new File(basename + ".avi");
		if(f.exists()) {
			sendFile(socket, basename, f);
			return;
		}
	}

	public String getTypeAndAttachmentId(String basename) {
		File infofile = new File(basename + ".info");
		if(!infofile.exists())
			return "";
		String imageAnnotationId = "-1";
		String videoAnnotationId = "-1";
		String type = "unknown";
		String line = null;
		try {
			BufferedReader buf = new BufferedReader(new FileReader(infofile));
			while((line = buf.readLine()) != null) {
				line = line.trim();
				if(line.startsWith("videoAnnotationId"))
					videoAnnotationId = line.split(":")[1].replaceAll(",", "");
				if(line.startsWith("imageAnnotationId"))
					imageAnnotationId = line.split(":")[1].replaceAll(",", "");
				else if(line.startsWith("type"))
					type = line.split(":")[1].replaceAll(",", "");
			}
			buf.close();
		} catch(Exception e) {
			e.printStackTrace();
		}
		return type + " " + videoAnnotationId + " " + imageAnnotationId;
	}

	public void sendPNG(Socket socket, String basename) {
		File f = new File(basename + ".png");
		if(f.exists()) {
			sendFile(socket, basename, f);
			return;
		}
	}

	public void sendFile(Socket socket, String basename, File f) {
		BufferedOutputStream outStream;
		try {
			outStream = new BufferedOutputStream(socket.getOutputStream());
		} catch (IOException e) {
			e.printStackTrace();
			return;
		}
	    BufferedInputStream inStream = null;
		try {
			inStream = new BufferedInputStream(new FileInputStream(f));
			final byte[] buffer = new byte[4096];
		    for (int read = inStream.read(buffer); read >= 0; read = inStream.read(buffer))
		        outStream.write(buffer, 0, read);
		} catch(Exception e) {
			e.printStackTrace();
		} finally {
		    try {
				inStream.close();
			    outStream.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	public synchronized void cancel(String... basenames) {
		for(String basename : basenames) {
			if(currentJob != null && currentJob.basename.equals(basename)) {
				getHelper(currentJob).cancel();
				synchronized(currentJob) {
					try {
						currentJob.wait();
						System.out.println("Job done (cancelled)");
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
				return;
			}

			queue.removeIf(new Predicate<Job>() {
				@Override
				public boolean test(Job t) {
					return t.basename.equals(basename);
				}
			});
		}
	}
}
