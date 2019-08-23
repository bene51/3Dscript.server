package animation3d.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicBoolean;

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
		while(!shutdown.get()) {
			Socket socket = null;
			BufferedReader in = null;
			System.out.println("Waiting for connection...");
			long time = System.currentTimeMillis();
			if(time - lastAccessed > 5 * 60 * 1000)
				shutdown.set(true);
			try {
				socket = server.accept();
				lastAccessed = time;
				System.out.println("Accepted connection from " + socket.getInetAddress());
				in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
				String line = in.readLine().toLowerCase().trim();
				System.out.println(line);

				if(line.startsWith("shutdown")) {
					shutdown();
				}
				// render <basename> <imageid> <target width> <target height>
				else if(line.startsWith("render")) {
					// TODO check how many jobs in queue, if more than X, reject and tell cient to come back later
					String[] toks = line.split(" ");
					int w = 0, h = 0, imageid = 0;
					String basename = null;
					try {
						basename = toks[1];
						imageid = Integer.parseInt(toks[2]);
						w = Integer.parseInt(toks[3]);
						h = Integer.parseInt(toks[4]);
						Job job = new Job(basename, imageid, w, h);
						job.setState(State.QUEUED);
						synchronized(this) {
							System.out.println("  server: queue new job");
							queue.add(job);
							System.out.println("  server: notify");
							notify();
							System.out.println("  server: notified");
						}
						PrintStream out = new PrintStream(socket.getOutputStream());
						out.println("done");
						out.close();
					} catch(Exception e) {
						// TODO handle exception
						e.printStackTrace();
					}
				}
				// getstate <basename>
				else if(line.startsWith("getstate")) {
					String basename = line.substring(line.indexOf(' ')).trim();
					String state = getState(basename);
					PrintStream out = new PrintStream(socket.getOutputStream());
					out.println(state.toString());
					out.close();
				}
				// getprogress <basename>
				else if(line.startsWith("getprogress")) {
					String basename = line.substring(line.indexOf(' ')).trim();
					double progress = getProgress(basename);
					PrintStream out = new PrintStream(socket.getOutputStream());
					out.println(Double.toString(progress));
					out.close();
				}
				else if(line.startsWith("getlog")) {
				}

				in.close();
				socket.close();
				System.out.println("Closed connection");
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
						helper.setImage(currentJob);
						System.out.println("  consumer: Rendering new job");
						helper.render();
						System.out.println("  consumer: Rendered new job");
					}
				}
			}
		}.start();
	}

	public void shutdown() {
		shutdown.set(true);
	}

	public synchronized String getState(String basename) {
		System.out.println("  consume: current job is " + (currentJob == null ? "null" : currentJob.basename));
		if(currentJob != null && currentJob.basename.equals(basename)) {
			System.out.println("  consumer: Querying state of current job");
			return currentJob.state.toString();
		}

		for(Job job : queue)
			if(job.basename.equals(basename))
				return job.state.toString();

		System.out.println("  consumer: getState: didn't find job in queue, assume it's finished");
		return State.FINISHED.toString();
	}

	public synchronized double getProgress(String basename) {
		if(currentJob != null && currentJob.basename.equals(basename))
			return helper.getProgress();

		for(Job job : queue)
			if(job.basename.equals(basename))
				return 0;

		return 1;
	}
}
