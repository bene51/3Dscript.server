package animation3d.server;

import java.io.File;

import animation3d.renderer3d.OpenCLRaycaster;
import animation3d.renderer3d.Renderer3D;
import animation3d.textanim.Animator;
import ij.ImagePlus;

public class SharedFSHelper extends Animation3DHelper {

	private CIFSConnection cifsConnection = null;

	@Override
	public void setImage(Job job) {
		if(!(job instanceof SharedFSJob))
			throw new RuntimeException("Did not get SharedFSJob");

		SharedFSJob j = (SharedFSJob)job;

		this.cancel = false;
		this.job = j;

		if(cifsConnection == null || !cifsConnection.checkUsername(j.username)) {
			if(cifsConnection != null)
				cifsConnection.close();
			cifsConnection = new CIFSConnection(j.domain, j.username, j.password);
		}

		ImagePlus image = renderer == null ? null : renderer.getImage();

		String title = j.remoteBasename;
		if(image != null && image.getTitle().equals(title)) {
			j.setState(State.OPENED);
			return;
		}

		if(image != null) {
			image.close();
			OpenCLRaycaster.close();
		}

		j.setState(State.OPENING);

		if(cancel)
			return;

		image = cifsConnection.createImage(j.url, j.series);
		if(image == null)
			throw new RuntimeException("Unable to open image " + j.url + " (" + j.series + ")");

		image.setTitle(title);

		if(cancel)
			return;

		renderer = new Renderer3D(image, image.getWidth(), image.getHeight());
		animator = new Animator(renderer);

		j.setState(State.OPENED);
	}

	@Override
	public void uploadResults(Job job) {
		if(!(job instanceof SharedFSJob))
			throw new RuntimeException("Did not get SharedFSJob");

		if(!job.uploadResults)
			return;

		SharedFSJob j = (SharedFSJob)job;

		if(cancel)
			return;
		j.setState(State.ATTACHING);
		File file = new File(j.basename + ".mp4");
		Job.Type type = Job.Type.IMAGE;
		if(file.exists()) {
			type = Job.Type.VIDEO;
			cifsConnection.uploadFile(j.basename + ".mp4", j.remoteBasename + ".mp4");
		}
		file = new File(j.basename + ".png");
		if(file.exists()) {
			cifsConnection.uploadFile(j.basename + ".png", j.remoteBasename + ".png");
		}
		j.type = type;
	}
}
