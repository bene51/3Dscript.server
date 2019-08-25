package animation3d.server;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import animation3d.renderer3d.OpenCLRaycaster;
import animation3d.renderer3d.Renderer3D;
import animation3d.textanim.Animator;
import ij.IJ;
import ij.ImagePlus;
import ij.plugin.FolderOpener;
import omero.gateway.Gateway;
import omero.gateway.LoginCredentials;
import omero.gateway.SecurityContext;
import omero.gateway.facility.BrowseFacility;
import omero.gateway.model.ExperimenterData;
import omero.gateway.model.ImageData;
import omero.gateway.model.PixelsData;
import omero.log.SimpleLogger;

public class Animation3DHelper {

	private Renderer3D renderer;

	private ImagePlus image;

	private Job job;

	private Animator animator;

	private Animator.Listener animationListener = new Animator.Listener() {
		@Override
		public void animationFinished(ImagePlus result) {
			job.setState(State.SAVING);
			String outfile = job.basename + ".avi";
			IJ.save(result, outfile);
			job.setState(State.FINISHED);
		}
	};

//	private static final class AnimationListener extends Animator.Listener {

	public Animation3DHelper() {
	}

	public void setImage(Job j) {
		this.job = j;

		if(image != null && image.getTitle().equals(Integer.toString(j.imageID))) {
			j.setState(State.OPENED);
			return;
		}

		downloadAndPreprocess(j);

		// not null, open new image and create new renderer & animator
		j.setState(State.OPENING);
		if(image != null) {
			image.close();
			animator.removeAnimationListener(animationListener);
			OpenCLRaycaster.close();
		}
		String dir = "/tmp/3Dscript." + j.imageID + "/";
		FolderOpener fo = new FolderOpener();
		fo.sortFileNames(true);
		fo.openAsVirtualStack(true);
		image = fo.openFolder(dir);
		// TODO imp.setDimensions(2, 57, 3);
		image.setOpenAsHyperStack(true);
		renderer = new Renderer3D(image, image.getWidth(), image.getHeight());
		animator = new Animator(renderer);
		animator.addAnimationListener(animationListener);

		j.setState(State.OPENED);
	}

	private void downloadAndPreprocess(Job j) {
		File directory = new File("/tmp/3Dscript." + j.imageID);
		if(directory.exists() && !directory.isDirectory())
			throw new RuntimeException(directory.getAbsolutePath() + " exists but is not a directory");

		// if the directory exists, assume the data is already downloaded
		// and preprocessed
		if(directory.exists())
			return;

		j.setState(State.DOWNLOADING);
		if(!directory.mkdirs())
			throw new RuntimeException("Cannot create directory " + directory.getAbsolutePath());

		// TODO download the data and save it according to python script
		Gateway gateway = new Gateway(new SimpleLogger());
		LoginCredentials cred = new LoginCredentials(j.sessionId, null, j.host, 4064);
		ExperimenterData user = gateway.connect(cred);
		SecurityContext ctx = new SecurityContext(user.getGroupId());

		BrowseFacility browse = gateway.getFacility(BrowseFacility.class);
		ImageData image = browse.getImage(ctx, j.imageID);

		PixelsData pixels = image.getDefaultPixels();
		pixels.getPixelSizeX();
	}

	public ImagePlus getImage() {
		return image;
	}

	/**
	 * Returns immediately.
	 */
	public void render() {
		job.setState(State.RENDERING);
		String animation = null;
		try {
			String animationFile = job.basename + ".animation.txt";
			animation = loadText(animationFile);
			renderer.setTargetSize(job.w, job.h);
			animator.render(animation);
		} catch(Exception e) {
			IJ.handleException(e);
			return;
		}
	}

	public double getProgress() {
		if(job.state == State.FINISHED)
			return 1;
		int from = animator.getFrom();
		int to = animator.getTo();
		int current = animator.getCurrent();
		return (double)(current - from) / (double)(to - from);
	}

	public State getState() {
		return job.state;
	}

	private String loadText(String file) throws IOException {
		BufferedReader buf = new BufferedReader(new FileReader(file));
		String line = null;
		StringBuffer res = new StringBuffer();
		while((line = buf.readLine()) != null) {
			res.append(line).append("\n");
		}
		buf.close();
		return res.toString();
	}
}
