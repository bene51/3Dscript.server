package animation3d.server;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Label;
import java.awt.Panel;
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

import fiji.util.gui.GenericDialogPlus;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.Prefs;
import ij.gui.GenericDialog;
import ij.plugin.AVI_Reader;
import ij.plugin.PlugIn;
import ij.process.ImageProcessor;

public class Animation3D_Client implements PlugIn {

	public static void main(String[] args) {
		new ij.ImageJ();
		new Animation3D_Client().run(null);
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

	List<String> processingMachines = Arrays.asList(new String[] {"localhost"});

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

	public void test() throws UnknownHostException, IOException {
		String omeroHost = Prefs.get("Animation3DClient.omeroHost", "");
		String omeroUser = Prefs.get("Animation3DClient.omeroUser", "");
		String omeroImageId = Prefs.get("Animation3DClient.omeroImageId", "");
		String animationScript = Prefs.get("Animation3DClient.animationScript", "");
		int targetWidth = Prefs.getInt("Animation3DClient.targetWidth", 800);
		int targetHeight = Prefs.getInt("Animation3DClient.targetHeight", 600);


		GenericDialogPlus gd = new GenericDialogPlus("Animation3DClient");
		gd.addStringField("OMERO_Host", omeroHost, 30);
		gd.addStringField("OMERO_User", omeroUser, 30);
		gd.setEchoChar('*');
		gd.addStringField("OMERO_Password", "", 30);
		gd.addStringField("OMERO_Image_ID", omeroImageId);
		gd.addNumericField("Target_Width", targetWidth, 0);
		gd.addNumericField("Target_Height", targetHeight, 0);
		gd.addFileField("Animation_Script", animationScript, 30);
		gd.addButton("Run remotely", new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				ProcessOnDialog pod = new ProcessOnDialog();
				pod.run("");
				processingMachines = pod.getMachines();
			}
		});
		gd.showDialog();
		if(gd.wasCanceled())
			return;

		omeroHost = gd.getNextString();
		omeroUser = gd.getNextString();
		String omeroPass = gd.getNextString();
		omeroImageId = gd.getNextString();
		targetWidth = (int)gd.getNextNumber();
		targetHeight = (int)gd.getNextNumber();
		animationScript = gd.getNextString();

		Prefs.set("Animation3DClient.omeroHost", omeroHost);
		Prefs.set("Animation3DClient.omeroUser", omeroUser);
		Prefs.set("Animation3DClient.omeroImageId", omeroImageId);
		Prefs.set("Animation3DClient.targetWidth", targetWidth);
		Prefs.set("Animation3DClient.targetHeight", targetHeight);
		Prefs.set("Animation3DClient.animationScript", animationScript);
		Prefs.savePreferences();

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

		final String session = Animation3DClient.omeroLogin(omeroHost, 4064, omeroUser, omeroPass);

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
		progressGD.showDialog();
		progressGD.getButtons()[1].setVisible(false);

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
			final String omeroH = omeroHost;
			final int[] iIds = imageIds;
			final int tgtW = targetWidth;
			final int tgtH = targetHeight;
			final int[] partition = partitions[i];
			exec.submit(new Runnable() {
				@Override
				public void run() {
					String[] basenames = Animation3DClient.startRendering(
							omeroH, session, iIds, script, ScriptAnalyzer.partitionToString(partition),
							tgtW, tgtH,
							processingHost, processingPort, null);
					IJ.log("basename = " + Arrays.toString(basenames));
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
							File f = Animation3DClient.downloadAVI(processingHost, processingPort, basename);
							AVI_Reader reader = new AVI_Reader();
							reader.setVirtual(false);
							reader.displayDialog(false);
							reader.run(f.getAbsolutePath());
							video = reader.getImagePlus();
							// video.show();
						}
						else {
							File f = Animation3DClient.downloadPNG(processingHost, processingPort, basename);
							video = IJ.openImage(f.getAbsolutePath());
							// video.show();
						}
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

		for(int b = 0; b < imageIds.length; b++) {
			ImageStack stack = new ImageStack(rendered[b][0].getWidth(), rendered[b][0].getHeight());
			for(int i = 0; i < rendered[b].length; i++)
				stack.addSlice(rendered[b][i]);
			ImagePlus result = new ImagePlus("output", stack);
			result.show();
		}
	}
}
