package animation3d.server;

import java.awt.Button;
import java.awt.Choice;
import java.awt.FlowLayout;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Label;
import java.awt.Panel;
import java.awt.TextField;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import animation3d.renderer3d.OpenCLRaycaster;
import fiji.util.gui.GenericDialogPlus;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.Macro;
import ij.Prefs;
import ij.gui.GenericDialog;
import ij.plugin.AVI_Reader;
import ij.plugin.PlugIn;
import ij.process.ImageProcessor;
import jcifs.CIFSContext;
import jcifs.context.SingletonContext;
import jcifs.smb.NtlmPasswordAuthenticator;
import jcifs.smb.SmbFile;
import jcifs.smb.SmbFileOutputStream;

public class Animation3D_Client implements PlugIn {

	public static void main(String[] args) {
		new ij.ImageJ();
		new Animation3D_Client().run(null);
		// testSamba();
	}

	public static void testSamba() {
		String url = "smb://romulus.oice.uni-erlangen.de/users/bschmid/test.txt";
		CIFSContext base = SingletonContext.getInstance();
		String password = "";
		CIFSContext authed1 = base.withCredentials(new NtlmPasswordAuthenticator("OICEAD", "bschmid", password));
		try {
			SmbFile f = new SmbFile(url, authed1);
			SmbFileOutputStream fos = new SmbFileOutputStream(f);
			fos.write("Hi there".getBytes());
			fos.close();
			f.close();
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	@Override
	public void run(String arg) {
		try {
			test();
		} catch (UnknownHostException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private List<String> processingMachines = new ArrayList<String>();

	private static int[] indicesForRange(String range) {
		ArrayList<Integer> indices = new ArrayList<Integer>();
		String[] toks = range.split(",");
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

	private String omeroHost;
	private String omeroUser;
	private String omeroImageId;
	private String omeroPassword;

	private String cifsUserAtDomain;
	private String cifsUrlAndSeries;
	private String cifsPassword;

	public void test() throws UnknownHostException, IOException {
		omeroHost = Prefs.get("Animation3DClient.omeroHost", "");
		omeroUser = Prefs.get("Animation3DClient.omeroUser", "");
		omeroImageId = Prefs.get("Animation3DClient.omeroImageId", "");
		String animationScript = Prefs.get("Animation3DClient.animationScript", "");
		cifsUserAtDomain = Prefs.get("Animation3DClient.cifsUserAtDomain", "");
		cifsUrlAndSeries = Prefs.get("Animation3DClient.cifsUrlAndSeries", "");
		int targetWidth = (int)Prefs.get("Animation3DClient.targetWidth", 800);
		int targetHeight = (int)Prefs.get("Animation3DClient.targetHeight", 600);
		String dataSource = Prefs.get("Animation3DClient.imageSource", "Shared file system");
		String processingMachinesString = Prefs.get("Animation3DClient.processingServers", "");
		processingMachines = Arrays.asList(processingMachinesString.split(","));

		GenericDialogPlus gd = new GenericDialogPlus("Animation3DClient");
		if(!dataSource.equals("OMERO") && !dataSource.equals("Shared file system"))
			dataSource = "Shared file system";
		String[] dataSourceChoice = new String[] {"OMERO", "Shared file system"};
		addChoiceFieldWithConfigure(gd, "Image_source", dataSourceChoice, dataSource);
		gd.addNumericField("Target_Width", targetWidth, 0);
		gd.addNumericField("Target_Height", targetHeight, 0);
		gd.addFileField("Animation_Script", animationScript, 30);
		gd.addButton("Run remotely", new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				ProcessOnDialog pod = new ProcessOnDialog();
				pod.addMachines(processingMachines);
				pod.run("");
				processingMachines = pod.getMachines();
			}
		});
		gd.showDialog();
		if(Macro.getOptions() != null) {
			ProcessOnDialog pod = new ProcessOnDialog();
			pod.run("");
			processingMachines = pod.getMachines();
			configureOMERO();
			configureSharedFS();
		}
		if(gd.wasCanceled())
			return;

		final int choiceIndex = gd.getNextChoiceIndex();
		dataSource = dataSourceChoice[choiceIndex];

		targetWidth = (int)gd.getNextNumber();
		targetHeight = (int)gd.getNextNumber();
		animationScript = gd.getNextString();

		Prefs.set("Animation3DClient.omeroHost", omeroHost);
		Prefs.set("Animation3DClient.omeroUser", omeroUser);
		Prefs.set("Animation3DClient.omeroImageId", omeroImageId);
		Prefs.set("Animation3DClient.cifsUserAtDomain", cifsUserAtDomain);
		Prefs.set("Animation3DClient.cifsUrlAndSeries", cifsUrlAndSeries);
		Prefs.set("Animation3DClient.targetWidth", targetWidth);
		Prefs.set("Animation3DClient.targetHeight", targetHeight);
		Prefs.set("Animation3DClient.animationScript", animationScript);
		Prefs.set("Animation3DClient.imageSource", dataSource);
		Prefs.set("Animation3DClient.processingServers", String.join(",", processingMachines));
		Prefs.savePreferences();

		if(processingMachines.isEmpty()) {
			IJ.error("No processing servers configured");
			return;
		}

		if(choiceIndex == 0 && omeroPassword == null) {
			if((omeroPassword = getPassword("OMERO_Password")).isEmpty())
				return;
		}
		else if(choiceIndex == 1 && cifsPassword == null) {
			if((cifsPassword = getPassword("Share_Password")).isEmpty())
				return;
		}

		System.out.println("Processing on ...");
		for(String m : processingMachines)
			System.out.println(" - " + m);

		String script = IJ.openAsString(animationScript);
		ScriptAnalyzer sa = new ScriptAnalyzer(script);
		int[][] partitions = null;
		try {
			partitions = sa.partition(processingMachines.size());
			for(int[] p : partitions) {
				System.out.println("partition: " + Arrays.toString(p));
				// System.out.println("partition: " + ScriptAnalyzer.partitionToString(p));
			}
		} catch(Exception e) {
			IJ.handleException(e);
		}

		int[] imageIds = indicesForRange(omeroImageId);

		final String session = choiceIndex == 0 ?
			Animation3DClient.omeroLogin(omeroHost, 4064, omeroUser, omeroPassword) :
			null;

		final int nPartitions = partitions.length;
		// NOTE: there might be less partitions than processing machines

		final ProgressBar[] progresses = new ProgressBar[nPartitions];
		for(int i = 0; i < nPartitions; i++)
			progresses[i] = new ProgressBar();

		GenericDialog progressGD = new GenericDialog("");
		GridBagLayout gridbag = new GridBagLayout();
		GridBagConstraints c = new GridBagConstraints();
		Panel p = new Panel(gridbag);
		c.insets = new Insets(5, 5, 5, 5);
		c.anchor = GridBagConstraints.WEST;
		c.gridy = 0;
		for(int i = 0; i < nPartitions; i++) {
			c.gridx = 0;
			p.add(new Label(processingMachines.get(i)), c);
			c.gridx++;
			p.add(progresses[i], c);
			progresses[i].show(0);
			c.gridy++;
		}
		progressGD.addPanel(p);
		progressGD.setModal(false);
		progressGD.hideCancelButton();
		progressGD.showDialog();
		// progressGD.getButtons()[1].setVisible(false);

		ExecutorService exec = Executors.newFixedThreadPool(nPartitions);
		int nFrames = 0;
		for(int i = 0; i < nPartitions; i++)
			nFrames += partitions[i].length;
		ImageProcessor[][] rendered = new ImageProcessor[imageIds.length][nFrames];
		long start = System.currentTimeMillis();
		for(int i = 0; i < nPartitions; i++) {
			final String processingHost = processingMachines.get(i);
			final int processingPort = 3333;
			final ProgressBar progressbar = progresses[i];
			final int[] iIds = imageIds;
			final int tgtW = targetWidth;
			final int tgtH = targetHeight;
			final int[] partition = partitions[i];
			exec.submit(new Runnable() {
				@Override
				public void run() {
					String[] basenames = null;
					switch(choiceIndex) {
					case 0: basenames = Animation3DClient.startRendering(
								omeroHost, session, iIds, script, ScriptAnalyzer.partitionToString(partition),
								tgtW, tgtH,
								processingHost, processingPort, null);
								break;
					case 1: basenames = Animation3DClient.startRendering(
								cifsUserAtDomain, cifsPassword, cifsUrlAndSeries, script,
								ScriptAnalyzer.partitionToString(partition),
								tgtW, tgtH,
								processingHost, processingPort);
								break;
					}
					outer:
					for(int b = 0; b < basenames.length; b++) {
						String basename = basenames[b];
						while(true) {
							String positionProgressState = Animation3DClient.getState(processingHost, processingPort, basename);
							String[] toks = positionProgressState.split(" ");
							int position = Integer.parseInt(toks[0]);
							double progress = Double.parseDouble(toks[1]);
							String state = toks[2];
							System.out.println(progress);
							progressbar.setState(state);
							progressbar.show(progress);
							if(state.startsWith("ERROR")) {
								progressbar.setState("ERROR");
								String msg = "An error happened during rendering\n";
								msg += Animation3DClient.getStacktrace(processingHost, processingPort, basename);
								IJ.log(msg);
								continue outer;
							}
							if(state.startsWith("FINISHED")) {
								progressbar.setState("FINISHED");
								break;
							}
							try {
								Thread.sleep(500);
							} catch (InterruptedException e) {
								e.printStackTrace();
							}
						}
						progressbar.show(0.99);
						int nFrames = partition.length;
						ImagePlus video = null;
						if(nFrames > 1) {
							progressbar.setState("Downloading");
							long size = Animation3DClient.getAVISize(processingHost, processingPort, basename);
							File f = Animation3DClient.downloadAVI(processingHost, processingPort, basename, progressbar, size);

							AVI_Reader reader = new AVI_Reader();
							reader.setVirtual(false);
							reader.displayDialog(false);
							reader.run(f.getAbsolutePath());
							video = reader.getImagePlus();
							if(nPartitions == 1)
								video.show();
						}
						else {
							File f = Animation3DClient.downloadPNG(processingHost, processingPort, basename);
							video = IJ.openImage(f.getAbsolutePath());
							if(nPartitions == 1)
								video.show();
						}
						if(nPartitions > 1)
							for(int f = 0; f < partition.length; f++)
								rendered[b][partition[f]] = video.getStack().getProcessor(f + 1);
					}
				}
			});
		}
		exec.shutdown();
		try {
			exec.awaitTermination(1, TimeUnit.DAYS);
		} catch (InterruptedException e1) {
			e1.printStackTrace();
		}
		long end = System.currentTimeMillis();
		System.out.println("Rendering took " + (end - start) + " ms");

		if(nPartitions == 1)
			return;

		for(int b = 0; b < imageIds.length; b++) {
			ImageStack stack = new ImageStack(rendered[b][0].getWidth(), rendered[b][0].getHeight());
			for(int i = 0; i < rendered[b].length; i++)
				stack.addSlice(rendered[b][i]);
			ImagePlus result = new ImagePlus("output", stack);
			result.show();
		}
	}

	private static String getPassword(String prompt) {
		GenericDialog gd = new GenericDialog("Enter password");
		gd.setEchoChar('*');
		gd.addStringField(prompt, "", 20);
		gd.showDialog();
		if (gd.wasCanceled())
			return "";
		return gd.getNextString();
	}

	private void configureOMERO() {
		GenericDialog gd = new GenericDialog("Configure");
		gd.addStringField("OMERO_Server", omeroHost, 30);
		gd.addStringField("OMERO_User", omeroUser, 30);
		gd.setEchoChar('*');
		gd.addStringField("OMERO_Password", omeroPassword, 30);
		gd.addStringField("OMERO_Image_ID", omeroImageId);
		gd.showDialog();
		if(gd.wasCanceled())
			return;
		omeroHost = gd.getNextString();
		omeroUser = gd.getNextString();
		omeroPassword = gd.getNextString();
		omeroImageId = gd.getNextString();
	}

	private static final class CifsDropTargetAdapter extends DropTargetAdapter {

		TextField text;
		DataFlavor flavor = DataFlavor.stringFlavor;

		public CifsDropTargetAdapter(TextField text) {
			this.text = text;
		}

		@Override
		public void drop(DropTargetDropEvent event) {
			try {
				String string = getString(event);
				if(IJ.isWindows()) {
					File f = new File(string);
					if(f.exists()) {
						String unc = OpenCLRaycaster.getUNCForPath(f.getAbsolutePath());
						if(unc != null)
							string = unc;
					}
				}
				text.setText(string);
			} catch (Exception e) { e.printStackTrace(); }
		}

		static String stripSuffix(String s, String suffix) {
			return !s.endsWith(suffix) ? s :
				s.substring(0, s.length() - suffix.length());
		}

		@SuppressWarnings("unchecked")
		static String getString(DropTargetDropEvent event)
				throws IOException, UnsupportedFlavorException {
			String text = null;
			DataFlavor fileList = DataFlavor.javaFileListFlavor;

			if (event.isDataFlavorSupported(fileList)) {
				event.acceptDrop(DnDConstants.ACTION_COPY);
				List<File> list = (List<File>)event.getTransferable().getTransferData(fileList);
				text = list.get(0).getAbsolutePath();
			}
			else if (event.isDataFlavorSupported(DataFlavor.stringFlavor)) {
				event.acceptDrop(DnDConstants.ACTION_COPY);
				text = (String)event.getTransferable()
					.getTransferData(DataFlavor.stringFlavor);
				if (text.startsWith("file://"))
					text = text.substring(7);
				text = stripSuffix(stripSuffix(text, "\n"),
						"\r").replaceAll("%20", " ");
			}
			else {
				event.rejectDrop();
				return null;
			}

			event.dropComplete(text != null);
			return text;
		}
	}

	private static boolean isHeadless() {
		return GraphicsEnvironment.isHeadless();
	}

	private void configureSharedFS() {
		GenericDialogPlus gd = new GenericDialogPlus("Configure");
		gd.addMessage(
				"Please enter in the form: 'url1|series, url2|series', etc.\n" +
				"where\n" +
				"- url is written like \n" +
				// "    smb://<host>/path or\n" +
				"    \\\\<host>\\path and\n" +
				"- series is written like, e.g.,\n" +
				"    1-3+5+7 (rendering series 1, 2, 3, 5, 7)");
		gd.addFileField("URL_and_series", cifsUrlAndSeries, 30);

		if(IJ.isWindows() && !isHeadless()) {
			TextField text = (TextField)gd.getStringFields().lastElement();
			text.setDropTarget(null);
			new DropTarget(text, new CifsDropTargetAdapter(text));
		}

		gd.addStringField("User (user@domain)", cifsUserAtDomain, 30);
		gd.setEchoChar('*');
		gd.addStringField("Share_Password", cifsPassword, 30);
		gd.showDialog();
		if(gd.wasCanceled())
			return;
		cifsUrlAndSeries = gd.getNextString();
		// "\\server\path" -> "smb://server/path"
		if(cifsUrlAndSeries.startsWith("\\\\"))
			cifsUrlAndSeries = cifsUrlAndSeries.replaceAll("\\\\", "/");
		if(!cifsUrlAndSeries.startsWith("smb:"))
			cifsUrlAndSeries = "smb:";

		cifsUserAtDomain = gd.getNextString();
		cifsPassword = gd.getNextString();
	}

	private void addChoiceFieldWithConfigure(GenericDialogPlus gd, String label, String[] choices, String defaultChoice) {
		gd.addChoice(label, choices, defaultChoice);

		final Choice ch = (Choice)gd.getChoices().lastElement();
		GridBagLayout layout = (GridBagLayout)gd.getLayout();
		GridBagConstraints constraints = layout.getConstraints(ch);

		Button button = new Button("Configure");
		button.addActionListener(e -> {
			if(ch.getSelectedIndex() == 0) // OMERO
				configureOMERO();
			else // Shared file system
				configureSharedFS();
		});

		Panel panel = new Panel();
		panel.setLayout(new FlowLayout(FlowLayout.LEFT, 10, 0));
		panel.add(ch);
		panel.add(button);

		layout.setConstraints(panel, constraints);
		gd.add(panel);
	}
}
