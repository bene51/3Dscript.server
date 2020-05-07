package animation3d.server;

import java.io.File;

import ij.ImagePlus;

public interface Connection {

	public boolean checkSession(Job j);

	public void close();

	public String getImageTitle(Job j);

	public ImagePlus createImage(Job j);

	public void uploadStill(Job j, File f);

	public void uploadVideo(Job j, File f);

}
