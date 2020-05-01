package animation3d.server;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

import animation3d.renderer3d.Renderer3D;
import animation3d.textanim.Animator;
import ij.IJ;
import ij.ImagePlus;

public abstract class Animation3DHelper {

	protected Renderer3D renderer;

	protected ImagePlus image;

	protected Job job;

	protected Animator animator;

	protected boolean cancel = false;

	public Animation3DHelper() {
	}

	public void cancel() {
		this.cancel = true;
		if(animator != null)
			animator.cancelRendering();
	}

	public abstract void setImage(Job j);

	public abstract void uploadResults(Job j);

	public void saveJobInfo(Job j) {
		IJ.saveString(j.toString(), j.basename + ".info");
	}

	public void convertToMP4(int nFrames) {
		job.setState(State.CONVERTING);
		String avifile = job.basename + ".avi";
		String mp4file = job.basename + ".mp4";
		String[] cmd = new String[] {
				"ffmpeg",
				"-y",
				"-i",
				avifile,
				"-vcodec",
				"libx264",
				"-an",
				"-preset",
				"slow",
				"-crf",
				"17",
				"-pix_fmt",
				"yuv420p",
				"-loglevel",
				"debug",
				mp4file
		};
		StringBuffer output = new StringBuffer();
		try {
			Process p = Runtime.getRuntime().exec(cmd);
			BufferedReader in = new BufferedReader(new InputStreamReader(p.getErrorStream()));
		    String line;
		    boolean success = false;
		    while ((line = in.readLine()) != null) {
		    	output.append(line).append('\n');
				if(cancel) {
					p.destroy();
					return;
				}
		        if(line.trim().startsWith(nFrames + " frames successfully decoded, 0 decoding errors"))
		        	success = true;
		    }
			p.waitFor(3, TimeUnit.MINUTES);
			if(!success) {
				IJ.saveString(output.toString(), job.basename + ".error");
				throw new RuntimeException("Error converting to MP4");
			}
		} catch(IOException | InterruptedException e) {
			throw new RuntimeException("Cannot convert to MP4", e);
		}
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
		ImagePlus result = null;
		try {
			String animationFile = job.basename + ".animation.txt";
			animation = loadText(animationFile);
			renderer.setTargetSize(job.w, job.h);
			if(job.frames != null)
				animator.render(animation, job.frames);
			else
				animator.render(animation);
			result = animator.waitForRendering(5, TimeUnit.MINUTES);
		} catch(Exception e) {
			throw new RuntimeException("Error while rendering", e);
		}
		job.setState(State.SAVING);
		if(result.getStackSize() > 1) {
			result.getCalibration().fps = 20;
			String outfile = job.basename + ".avi";
			IJ.save(result, outfile);
			// save poster image
			result.setSlice(1);
			IJ.save(result, job.basename + ".png");
			convertToMP4(result.getStackSize());
		} else {
			String outfile = job.basename + ".png";
			IJ.save(result, outfile);
		}
		result.close();
	}

	public double getProgress() {
		if(job.state != State.RENDERING)
			return job.state.start / 100.0;

		double ratio = animator.getProgress();
		return (State.RENDERING.start + ratio * State.RENDERING.duration) / 100.0;
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
