package animation3d.server;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

import animation3d.renderer3d.OpenCLRaycaster;
import animation3d.renderer3d.Renderer3D;
import animation3d.textanim.Animator;
import ij.IJ;
import ij.ImagePlus;
import ij.plugin.FolderOpener;

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
