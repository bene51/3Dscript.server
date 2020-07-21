package animation3d.server.server;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.concurrent.atomic.AtomicBoolean;

import ij.IJ;

public class MulticastReceiver extends Thread {

	public static final String DISCOVERY_3DSCRIPT_REQUEST  = "DISCOVERY_3DSCRIPT_REQUEST";
	public static final String DISCOVERY_3DSCRIPT_RESPONSE = "DISCOVERY_3DSCRIPT_RESPONSE";

	public static final String DISCOVERY_GROUP = "230.0.0.0";

	protected byte[] buf = new byte[256];

	private final AtomicBoolean shutdown;

	private final InetAddress localAddress;

	public MulticastReceiver(AtomicBoolean shutdown) {
		this.shutdown = shutdown;
		this.localAddress = getLocalAddress();
	}

	public static InetAddress getLocalAddress() {
		InetAddress localAddr = null;
		try (final DatagramSocket socket = new DatagramSocket()) {
			try {
				socket.connect(InetAddress.getByName("8.8.8.8"), 10002);
			} catch (UnknownHostException e) {
			}
			localAddr = socket.getLocalAddress();
		} catch (SocketException e1) {
		}
		return localAddr;
	}

	public InetAddress getLocalAdress() {
		return localAddress;
	}

	@Override
	public void run() {
		MulticastSocket socket = null;
		InetAddress group = null;
		try {
			socket = new MulticastSocket(Animation3DServer.PORT);
			if(localAddress != null)
				socket.setInterface(localAddress);

			group = InetAddress.getByName(DISCOVERY_GROUP);
			socket.joinGroup(group);
			while (!shutdown.get()) {
				DatagramPacket packet = new DatagramPacket(buf, buf.length);
				try {
					socket.receive(packet);
					String received = new String(packet.getData(), 0, packet.getLength()).trim();
					if (received.equals(DISCOVERY_3DSCRIPT_REQUEST)) {
						byte[] buf = DISCOVERY_3DSCRIPT_RESPONSE.getBytes();
						int port = packet.getPort();
						InetAddress address = packet.getAddress();
						System.out.println("Server: received packet from " + address);
						packet = new DatagramPacket(buf, buf.length, address, port);
						socket.send(packet);
					}
				} catch (Exception e) {
					IJ.log("Error reading multicast packet");
				}
			}
		} catch (Exception e) {
			IJ.log("Error setting up broadcast receiver, this computer will not be detected automatically.");
		} finally {
			if (socket != null) {
				if (group != null) {
					try {
						socket.leaveGroup(group);
					} catch (Exception e) {
					}
				}
				socket.close();
			}
		}
	}
}