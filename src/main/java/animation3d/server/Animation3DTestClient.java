package animation3d.server;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.net.UnknownHostException;

import ij.CompositeImage;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.plugin.FolderOpener;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import ij.process.LUT;
import omero.gateway.rnd.Plane2D;

public class Animation3DTestClient {

	public static void main(String[] args) throws UnknownHostException, IOException {
//		Socket socket = new Socket("localhost", 3333);
//		PrintStream out = new PrintStream(socket.getOutputStream());
//		BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
//		out.println("bla");

		for(int t = 0; t < 3; t++) {
			ImageStack stack = new ImageStack(256, 256);
			for(int z = 0; z < 57; z++) {
				for(int c = 0; c < 2; c++) {
					Plane2D plane = null;
					ImageProcessor ip = new ByteProcessor(256, 256);
					for(int y = 0, i = 0; y < 256; y++) {
						for(int x = 0; x < 256; x++, i++) {
							ip.setf(i, 150);
						}
					}
					stack.addSlice(ip);
				}
			}
			ImagePlus imp = new ImagePlus("", stack);
			imp.setDimensions(2, 57, 1);
			imp.setOpenAsHyperStack(true);
			CompositeImage ci = new CompositeImage(imp);
			Color[] channelColors = new Color[] {Color.BLUE, Color.YELLOW};
			for(int c = 0; c < 2; c++) {
				Color col = channelColors[c];
				LUT lut = LUT.createLutFromColor(col == null ? Color.WHITE : col);
				lut.min = 0;
				lut.max = 50;
				ci.setChannelLut(lut, c + 1);
			}
			String path = new File("d:\\flybrain\\temp", IJ.pad(t, 4) + ".tif").getAbsolutePath();
			IJ.save(ci, path);
		}

		new ij.ImageJ();
		FolderOpener fo = new FolderOpener();
		fo.openAsVirtualStack(true);
		fo.sortFileNames(true);
		ImagePlus imp = fo.openFolder("D:\\flybrain\\temp\\");
		CompositeImage ci = new CompositeImage(imp);
		ci.show();
	}
}
