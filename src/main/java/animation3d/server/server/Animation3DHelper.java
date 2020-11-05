package animation3d.server.server;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import animation3d.renderer3d.OpenCLRaycaster;
import animation3d.renderer3d.Progress;
import animation3d.renderer3d.Renderer3D;
import animation3d.server.server.omero.OMEROConnection;
import animation3d.server.server.omero.OMEROJob;
import animation3d.server.server.smb.CIFSConnection;
import animation3d.server.server.smb.SharedFSJob;
import animation3d.server.util.FFmpeg;
import animation3d.textanim.Animator;
import ij.IJ;
import ij.ImagePlus;
import ij.measure.Calibration;

public class Animation3DHelper implements Progress {

	private Renderer3D renderer;

	private Job job;

	private Animator animator;

	private boolean cancel = false;

	private double openingProgress = 0;

	private OMEROConnection omeroConnection = null;
	private CIFSConnection cifsConnection = null;

	public Animation3DHelper() {
	}

	public void cancel() {
		this.cancel = true;
		if(animator != null)
			animator.cancelRendering();
	}

	private Connection getConnection(Job job) {
		if(job instanceof OMEROJob)
			return omeroConnection;
		else if(job instanceof SharedFSJob)
			return cifsConnection;
		return null;
	}

	private Connection newConnection(Job job) {
		if(job instanceof OMEROJob) {
			this.omeroConnection = new OMEROConnection((OMEROJob)job);
			return this.omeroConnection;
		}
		else if(job instanceof SharedFSJob) {
			this.cifsConnection = new CIFSConnection((SharedFSJob)job);
			return this.cifsConnection;
		}
		return null;
	}

	public void setImage(Job j) {
		this.cancel = false;
		this.job = j;

		Connection connection = getConnection(j);
		if(connection == null || !connection.checkSession(j)) {
			if(connection != null)
				connection.close();
		}
		connection = newConnection(j);

		ImagePlus image = renderer == null ? null : renderer.getImage();

		String title = connection.getImageTitle(j);
		if(image != null && image.getTitle().equals(title)) {
			j.setState(State.OPENED);
			renderer.reset();
			return;
		}

		if(image != null) {
			image.close();
			OpenCLRaycaster.close();
		}


		j.setState(State.OPENING);

		if(cancel)
			return;

		image = connection.createImage(j);
		if(image == null)
			throw new RuntimeException("Unable to open image from source:\n" + j);

		// image.show();
		image.setTitle(title);

		// check whether the image needs to be resized
		// it should be resized, if either
		// - image width > 2 * target width or
		// - image height > 2 * target height
		//
		// in z, it should b resized if the pixel depth of the resized image is better then the pixel width/height
		// we average an integer number of planes, and we use so many planes that the pixel depth is just equal or
		// worse than pixel width

		Calibration cal = image.getCalibration();
		double pw = cal.pixelWidth;
		double pd = cal.pixelDepth;
		int tgtW = image.getWidth();
		int tgtH = image.getHeight();
		double scaleX = 2.0 * j.w / tgtW;
		double scaleY = 2.0 * j.h / tgtH;
		double scale = 1;
		if(scaleX < 1 || scaleY < 1) {
			scale = Math.min(scaleX, scaleY);
			tgtW = (int)Math.round(scale * tgtW);
			tgtH = (int)Math.round(scale * tgtH);
			pw /= scale;
		}

		// assume pw = 2, pd = 0.6, then
		// to get isotropic resolution, we would take n = pw / pd = 2 / 0.6 = 3.33 planes, so just worse means 4 planes
		// - 0.1: to avoid rounding errors, e.g. pw = 1.500001, pd = 1.5
		int nPlanesToAverage = (int)Math.ceil(pw / pd - 0.1);

		if(nPlanesToAverage > 1 || scale < 1) {
			ResizedVirtualStack stack = new ResizedVirtualStack(
					tgtW, tgtH, nPlanesToAverage,
					image.getNSlices(), image.getNChannels(), image.getStack());
			image.setStack(stack, image.getNChannels(), stack.getNPlanes(), image.getNFrames());
			cal.pixelWidth = cal.pixelHeight = pw;
			cal.pixelDepth *= nPlanesToAverage;
		}

		if(cancel)
			return;

		renderer = new Renderer3D(image, image.getWidth(), image.getHeight(), this);
		animator = new Animator(renderer);

		j.setState(State.OPENED);
	}

	// LoadingProgress interface
	@Override
	public void setProgress(double progress) {
		this.openingProgress = progress;
	}

	public void uploadResults(Job j) {
		if(!j.uploadResults)
			return;

		if(cancel)
			return;

		j.setState(State.ATTACHING);
		Connection connection = getConnection(j);

		File file = new File(j.basename + ".mp4");
		Job.Type type = Job.Type.IMAGE;
		if(file.exists()) {
			type = Job.Type.VIDEO;
			connection.uploadVideo(j, file);
		}
		file = new File(j.basename + ".png");
		if(file.exists()) {
			connection.uploadStill(j, file);
		}
		j.type = type;
	}

	public void saveJobInfo(Job j) {
		IJ.saveString(j.toString(), j.basename + ".info");
	}

	public void convertToMP4(int nFrames) {
		job.setState(State.CONVERTING);
		String avifile = job.basename + ".avi";
		String mp4file = job.basename + ".mp4";
		String logfile = job.basename + ".error";
		FFmpeg.convertToMP4(avifile, mp4file, logfile, new BooleanSupplier() {
			@Override
			public boolean getAsBoolean() {
				return cancel;
			}
		});
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
//			try {
//				new AVI_Writer().writeImage(result, outfile, AVI_Writer.NO_COMPRESSION, 100);
//			} catch(Exception e) {
//				throw new RuntimeException("Error saving AVI", e);
//			}
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
		if(job.state == State.RENDERING) {
			double ratio = animator.getProgress();
			return (State.RENDERING.start + ratio * State.RENDERING.duration) / 100.0;
		}
		if(job.state == State.OPENING) {
			return (State.OPENING.start + openingProgress * State.OPENING.duration) / 100.0;
		}
		return job.state.start / 100.0;
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
