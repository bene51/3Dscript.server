package animation3d.server;

import java.io.BufferedReader;
import java.io.File;
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

	@Override
	public void run(String arg) {
		start();
	}

	private static final int PORT = 3333;

	private final AtomicBoolean shutdown = new AtomicBoolean(false);

	private LinkedList<Job> queue = new LinkedList<Job>();

	private HashMap<String, Job> finished = new HashMap<String, Job>();

	private long lastAccessed = 0;

	public void start() {
		startConsumerThread();

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
				// render <host> <sessionid> <basename> <imageid> <target width> <target height>
				else if(line.startsWith("render")) {
					// TODO check how many jobs in queue, if more than X, reject and tell cient to come back later
					try {
						String[] toks = line.split(" ");
						String host = toks[1];
						String sessionid = toks[2];
						String script = new String(Base64.getUrlDecoder().decode(toks[3]));
						int imageid = Integer.parseInt(toks[4]);
						int w = Integer.parseInt(toks[5]);
						int h = Integer.parseInt(toks[6]);

						new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss").format(new Date());

						String basename = Files.createTempDirectory("3DScript").toFile().getAbsolutePath() + File.separator + new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss").format(new Date());
						File scriptfile = new File(basename + ".animation.txt");
						IJ.saveString(script, scriptfile.getAbsolutePath());

						Job job = new Job(host,
								sessionid,
								basename,
								imageid,
								w, h);
						job.setState(State.QUEUED);
						synchronized(this) {
//							System.out.println("  server: queue new job");
							queue.add(job);
//							System.out.println("  server: notify");
							notify();
//							System.out.println("  server: notified");
						}
						PrintStream out = new PrintStream(socket.getOutputStream());
						out.println(basename);
						out.close();
					} catch(Exception e) {
						// TODO handle exception
						e.printStackTrace();
					}
				}
				// cancel <basename>
				else if(line.startsWith("cancel")) {
					String basename = line.substring(line.indexOf(' ')).trim();
					cancel(basename);
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

	private Animation3DHelper helper = new Animation3DHelper();

	private Job currentJob = null;

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
							helper.setImage(currentJob);
							System.out.println("  consumer: Rendering new job");
							helper.render();
							System.out.println("  consumer: Rendered new job");
							currentJob.setState(animation3d.server.State.FINISHED);
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
			double progress = helper.getProgress();
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
					.append(helper.getProgress()).append(";");
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

	public synchronized void cancel(String basename) {
		if(currentJob != null && currentJob.basename.equals(basename)) {
			helper.cancel();
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
