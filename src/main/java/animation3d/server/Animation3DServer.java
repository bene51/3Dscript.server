package animation3d.server;

import java.awt.Color;
import java.awt.GraphicsEnvironment;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
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
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

import fiji.util.gui.GenericDialogPlus;
import ij.IJ;
import ij.Prefs;
import ij.plugin.PlugIn;

public class Animation3DServer implements PlugIn {

	public static void main(String[] args) {
		new ij.ImageJ();
		Animation3DServer server = new Animation3DServer();
		server.start();
	}

	// render <host> <sessionid> <script> <imageid> <target width> <target height> frames=<frames>
	public static void oneTimeRender(String line) throws Exception {
		Job[] jobs = createJobsFromLine(line);
		Animation3DHelper helper = new Animation3DHelper();
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

	public String run(String[] cmd) {
		StringBuffer output = new StringBuffer();
		try {
			System.out.println(Arrays.toString(cmd));
			Process p = Runtime.getRuntime().exec(cmd);
			BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream()));
			String line;
			while ((line = in.readLine()) != null)
				output.append(line).append('\n');

			p.waitFor(3, TimeUnit.MINUTES);
			return output.toString();
		} catch (IOException | InterruptedException e) {
			throw new RuntimeException("Cannot run " + Arrays.toString(cmd), e);
		}
	}

	private static boolean isHeadless() {
		return GraphicsEnvironment.isHeadless();
	}

	public void showNonmodalDialog() {
		if(isHeadless())
			return;
		GenericDialogPlus gd = new GenericDialogPlus("3Dscript.server");
		gd.setOKLabel("Shutdown");
		gd.addMessage("Server is running...", gd.getFont(), Color.GREEN.darker());
		if(IJ.isWindows()) {
			gd.addMessage("Configure TaskScheduler to automatically start\n3Dscript.server after booting.");
			gd.addButton("Install", new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					try {
						String fijidir = Prefs.getImageJDir();
						String taskXMLString = getTaskSchedulerText(fijidir);
						String tmpPath = File.createTempFile("3Dscript_task", ".xml").getAbsolutePath();
						IJ.saveString(taskXMLString, tmpPath);
						String batchfile = File.createTempFile("3Dscript_task", ".bat").getAbsolutePath();
						String command = "SCHTASKS /Create /TN \\3Dscript /F /XML \"" + tmpPath + "\"";
						IJ.saveString(batFileContent + command, batchfile);
						String output = run(new String[] {batchfile});
						System.out.println("Batchfile: " + batchfile);
						IJ.log(output);
					} catch(Exception ex) {
						throw new RuntimeException("Cannot configure TaskScheduler for 3Dscript.server", ex);
					}
				}
			});
			gd.addMessage("Remove 3Dscript.server from the TaskScheduler.");
			gd.addButton("Uninstall", new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					try {
						String batchfile = File.createTempFile("3Dscript_task", ".bat").getAbsolutePath();
						String command = "SCHTASKS /Delete /F /TN \\3Dscript";
						IJ.saveString(batFileContent + command, batchfile);
						String output = run(new String[] {batchfile});
						System.out.println("Batchfile: " + batchfile);
						IJ.log(output);
					} catch(Exception ex) {
						throw new RuntimeException("Cannot configure TaskScheduler for 3Dscript.server", ex);
					}
				}
			});
		}
		gd.hideCancelButton();
		gd.setModal(false);
		gd.addWindowListener(new WindowListener() {

			@Override public void windowClosed(WindowEvent arg0) {
				shutdown();
				System.out.println("Closing");
			}

			@Override public void windowActivated(WindowEvent arg0) {}
			@Override public void windowClosing(WindowEvent arg0) {}
			@Override public void windowDeactivated(WindowEvent arg0) {}
			@Override public void windowDeiconified(WindowEvent arg0) {}
			@Override public void windowIconified(WindowEvent arg0) {}
			@Override public void windowOpened(WindowEvent arg0) {}
		});
		gd.showDialog();
	}

	private void log(String s) {
		IJ.log(s);
	}

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

		showNonmodalDialog();

		log("Server is up and ready...");
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
					log(line);
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
					log(line);
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
		log("Server shut down");
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

	private Animation3DHelper helper = new Animation3DHelper();

	private Job currentJob = null;

	// render <host> <session> <urlencode(script)> <imageId1+imageId2+...> <width> <height> [frames=framerange] [basenames=basename1+basename2+...] [createAttachments=true|false]
	// session can be replaced with user/base64(password)
	private static Job[] createOMEROJobsFromLine(String line) throws Exception {
		String[] toks = line.split(" ");
		String host = toks[1];
		String sessionid = toks[2];
		String script = new String(Base64.getUrlDecoder().decode(toks[3]));
		String username = null, password = null;

		int slash = sessionid.indexOf('/');
		if(slash >= 0) {
			username = sessionid.substring(0, slash);
			password = new String(Base64.getUrlDecoder().decode(sessionid.substring(slash + 1)));
			sessionid = null;
		}

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

			Job job = sessionid != null ?
					new OMEROJob(host,
						sessionid,
						basename,
						imageid,
						w, h,
						frames,
						createAttachments) :
					new OMEROJob(host,
							username,
							password,
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
			String url;
			int[] series;
			if(colon > -1) {
				url = ptok.substring(0, colon);
				series = parsePlusMinusRange(ptok.substring(colon + 1));
			} else { // no series given, assume 0
				url = ptok;
				series = new int[] {0};
			}
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
							log("  - " + currentJob.basename + " has finised");
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
				helper.cancel();
				synchronized(currentJob) {
					try {
						currentJob.wait();
						System.out.println("Job done (cancelled)");
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			}

			queue.removeIf(new Predicate<Job>() {
				@Override
				public boolean test(Job t) {
					return t.basename.equals(basename);
				}
			});
		}
	}

	private String getTaskSchedulerText(String fijidir) {
		return
"<?xml version=\"1.0\" encoding=\"UTF-16\"?>\n" +
"<Task version=\"1.2\" xmlns=\"http://schemas.microsoft.com/windows/2004/02/mit/task\">\n" +
"  <RegistrationInfo>\n" +
"    <Date>2020-01-30T14:36:41.0902839</Date>\n" +
"    <Author>3Dscript</Author>\n" +
"    <URI>\\Test</URI>\n" +
"  </RegistrationInfo>\n" +
"  <Triggers>\n" +
"    <BootTrigger>\n" +
"      <Enabled>true</Enabled>\n" +
"    </BootTrigger>\n" +
"  </Triggers>\n" +
"  <Principals>\n" +
"    <Principal id=\"Author\">\n" +
"      <UserId>S-1-5-18</UserId>\n" +
"      <RunLevel>LeastPrivilege</RunLevel>\n" +
"    </Principal>\n" +
"  </Principals>\n" +
"  <Settings>\n" +
"    <MultipleInstancesPolicy>IgnoreNew</MultipleInstancesPolicy>\n" +
"    <DisallowStartIfOnBatteries>true</DisallowStartIfOnBatteries>\n" +
"    <StopIfGoingOnBatteries>true</StopIfGoingOnBatteries>\n" +
"    <AllowHardTerminate>true</AllowHardTerminate>\n" +
"    <StartWhenAvailable>false</StartWhenAvailable>\n" +
"    <RunOnlyIfNetworkAvailable>false</RunOnlyIfNetworkAvailable>\n" +
"    <IdleSettings>\n" +
"      <StopOnIdleEnd>true</StopOnIdleEnd>\n" +
"      <RestartOnIdle>false</RestartOnIdle>\n" +
"    </IdleSettings>\n" +
"    <AllowStartOnDemand>true</AllowStartOnDemand>\n" +
"    <Enabled>true</Enabled>\n" +
"    <Hidden>false</Hidden>\n" +
"    <RunOnlyIfIdle>false</RunOnlyIfIdle>\n" +
"    <WakeToRun>false</WakeToRun>\n" +
"    <ExecutionTimeLimit>PT0S</ExecutionTimeLimit>\n" +
"    <Priority>7</Priority>\n" +
"    <RestartOnFailure>\n" +
"      <Interval>PT1M</Interval>\n" +
"      <Count>3</Count>\n" +
"    </RestartOnFailure>\n" +
"  </Settings>\n" +
"  <Actions Context=\"Author\">\n" +
"    <Exec>\n" +
"      <Command>" + fijidir + "\\ImageJ-win64.exe</Command>\n" +
"      <Arguments>--console --headless -eval \"run(\\\"3Dscript Server\\\", \\\"\\\");\" &gt; bla.txt 2&gt;&amp;1</Arguments>\n" +
"      <WorkingDirectory>" + fijidir + "</WorkingDirectory>\n" +
"    </Exec>\n" +
"  </Actions>\n" +
"</Task>\n";
	}

	private static final String batFileContent =
"@echo off\n" +
"\n" +
"REM Copied from https://gist.github.com/Bomret/0a130778ffbe3a3f0322\n" +
"\n" +
"REM credits: https://sites.google.com/site/eneerge/scripts/batchgotadmin\n" +
"REM Stored here in case that site goes down some day\n" +
":: BatchGotAdmin\n" +
":-------------------------------------\n" +
"REM  --> Check for permissions\n" +
">nul 2>&1 \"%SYSTEMROOT%\\system32\\cacls.exe\" \"%SYSTEMROOT%\\system32\\config\\system\"\n" +
"\n" +
"REM --> If error flag set, we do not have admin.\n" +
"if '%errorlevel%' NEQ '0' (\n" +
"    echo Requesting administrative privileges...\n" +
"    goto UACPrompt\n" +
") else ( goto gotAdmin )\n" +
"\n" +
":UACPrompt\n" +
"    echo Set UAC = CreateObject^(\"Shell.Application\"^) > \"%temp%\\getadmin.vbs\"\n" +
"    set params = %*:\"=\"\"\n" +
"    echo UAC.ShellExecute \"cmd.exe\", \"/c %~s0 %params% > \"\"%temp%\\getadmin.log\"\"\", \"\", \"runas\", 1 >> \"%temp%\\getadmin.vbs\"\n" +
"\n" +
"    \"%temp%\\getadmin.vbs\"\n" +
"    :: del \"%temp%\\getadmin.vbs\"\n" +
"    waitfor SomethingThatIsNeverHappening /t 3 2>NUL\n" +
"    for /f \"tokens=*\" %%i in (%temp%\\getadmin.log) do @echo %%i\n" +
"    exit /B\n" +
"\n" +
":gotAdmin\n" +
"    pushd \"%CD%\"\n" +
"    CD /D \"%~dp0\"\n" +
":--------------------------------------\n" +
":: <YOUR BATCH SCRIPT HERE>\n";


}
