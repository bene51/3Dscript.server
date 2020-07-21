package animation3d.server.client;

import java.awt.Button;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Label;
import java.awt.Panel;
import java.awt.TextField;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

import animation3d.server.server.Animation3DServer;
import animation3d.server.server.MulticastReceiver;
import fiji.util.gui.GenericDialogPlus;
import ij.IJ;
import ij.Macro;

public class ProcessOnDialog {

	public static void main(String[] args) {
		new ij.ImageJ();
		new ProcessOnDialog().run("");
	}

	public void addMachines(Iterable<String> machines) {
		for(String machine : machines)
			this.machines.add(machine);
	}

	public List<String> findServers() throws IOException {
		byte[] buf = MulticastReceiver.DISCOVERY_3DSCRIPT_REQUEST.getBytes();
		InetAddress group = InetAddress.getByName(MulticastReceiver.DISCOVERY_GROUP);

		InetAddress localAddr = MulticastReceiver.getLocalAddress();

		MulticastSocket  socket = new MulticastSocket();
		if(localAddr != null)
			socket.setInterface(localAddr);
		DatagramPacket toSend = new DatagramPacket(buf, buf.length, group, Animation3DServer.PORT);
		socket.send(toSend);
		System.out.println(toSend.getAddress());

		buf = MulticastReceiver.DISCOVERY_3DSCRIPT_RESPONSE.getBytes();
		DatagramPacket toReceive = new DatagramPacket(buf, buf.length);
		socket.setSoTimeout(200);
		long millis = System.currentTimeMillis();
		ArrayList<String> servers = new ArrayList<String>();
		while(true) {
			int dt = (int)(System.currentTimeMillis() - millis);
			if(dt > 2000)
				break;
			IJ.showStatus("Server discovery " + (100 * dt / 2000) + "%");
			IJ.showProgress(dt, 2000);
			try {
				socket.receive(toReceive);
				String msg = new String(toReceive.getData()).trim();
				if(msg.equals(MulticastReceiver.DISCOVERY_3DSCRIPT_RESPONSE)) {
					String host = toReceive.getAddress().getHostAddress();
					NetworkInterface iface = NetworkInterface.getByInetAddress(toReceive.getAddress());
					if(iface != null)
						host = "localhost";
					System.out.println("received: from: " + host);
					servers.add(host);
				}
			} catch(SocketTimeoutException e) {
			}
		}
		IJ.showProgress(1);
		IJ.showStatus("");
		socket.close();
		return servers;
	}

	public void run(String args) {
		String macroOptions = Macro.getOptions();
		boolean macro = macroOptions != null;
		List<String> machineLabels = new ArrayList<String>();
		if(macro) {
			int startIdx = 0;
			while(true) {
				int machineIdx = macroOptions.indexOf("machine_", startIdx);
				if(machineIdx == -1)
					break;
				int equalIdx = macroOptions.indexOf('=', machineIdx);
				if(equalIdx == -1)
					throw new RuntimeException("Error parsing macro options");
				String machine = macroOptions.substring(machineIdx, equalIdx);
				machineLabels.add(machine);
				startIdx = equalIdx;
			}
			System.out.println("Found the following machine labels: " + machineLabels);
		} else {

		}

		GenericDialogPlus gd = new GenericDialogPlus("Processing on ...");

		gd.addButton("Detect servers in local network", new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				new Thread() {
					@Override
					public void run() {
						try {
							Vector stringFields = gd.getStringFields();
							System.out.println(stringFields);
							List<String> servers = findServers();
							moveButtonsDown(gd);
							if(servers.size() > 0)
								removeEmptyMachines(gd);
							for (String server : servers) {
								if (!contains(stringFields, server)) {
									lastUsedMachineIdx++;
									addMachine(gd, "Machine_" + lastUsedMachineIdx, server);
								}
							}

							gd.pack();
						} catch (IOException e1) {
							// TODO Auto-generated catch block
							e1.printStackTrace();
						}
					}
				}.start();
			}
		});

		gd.addMessage("Process on");
		GridBagLayout grid = (GridBagLayout)gd.getLayout();
		GridBagConstraints c = grid.getConstraints(gd.getMessage());
		c.insets.left = 0;
		grid.setConstraints(gd.getMessage(), c);

		if(!macro) {
			for(String machine : machines) {
				lastUsedMachineIdx++;
				addMachine(gd, "Machine_" + lastUsedMachineIdx, machine);
			}
		} else {
			for(String l : machineLabels) {
				addMachine(gd, l, Macro.getValue(macroOptions, l, "localhost"));
			}
		}

		gd.showDialog();
		if(gd.wasCanceled())
			return;

		Vector stringFields = gd.getStringFields();
		int nMachines = stringFields == null ? 0 : stringFields.size();

		machines.clear();

		for(int i = 0; i < nMachines; i++) {
			String m = gd.getNextString();
			if(!m.trim().isEmpty())
				machines.add(m);
		}
	}

	private List<String> machines = new ArrayList<String>();

	public List<String> getMachines() {
		return machines;
	}

	private static boolean contains(Vector<TextField> v, String s) {
		for(TextField tf : v) {
			if(tf.getText().equals(s))
				return true;
		}
		return false;
	}

	private void moveButtonsDown(GenericDialogPlus gd) {
		int buttonsIdx = -1;
		for(int i = gd.getComponentCount() - 1; i >= 0; i--) {
			Component c = gd.getComponent(i);
			if(c instanceof Panel && !c.getName().startsWith("Machine")) {
				buttonsIdx = i;
				break;
			}
		}
		Panel buttons = (Panel)gd.getComponent(buttonsIdx);
		GridBagLayout layout = (GridBagLayout)gd.getLayout();
		GridBagConstraints c = layout.getConstraints(buttons);
		c.gridy += 100;
		layout.setConstraints(buttons, c);
	}

	private void removeEmptyMachines(GenericDialogPlus gd) {
		ArrayList<Integer> indicesToRemove = new ArrayList<Integer>();
		for(int i = 0; i < gd.getComponentCount(); i++) {
			Component c = gd.getComponent(i);
			if(c instanceof Panel) {
				Panel p = (Panel)c;
				if(p.getName().startsWith("Machine")) {
					TextField tf = (TextField)p.getComponent(0);
					if(tf.getText().trim().isEmpty()) {
						indicesToRemove.add(i);
						gd.getStringFields().remove(tf);
					}
				}
			}
		}
		for(int i = indicesToRemove.size() - 1; i >= 0; i--) {
			int idx = indicesToRemove.get(i);
			gd.remove(idx); // the panel
			gd.remove(idx - 1); // the label
		}

		gd.pack();
	}

	private int lastUsedMachineIdx = 0;
	private void addMachine(GenericDialogPlus gd, String label, String host) {
		Vector stringFields = gd.getStringFields();
		int nMachines = stringFields == null ? 0 : stringFields.size();

		gd.addStringField(label, host, 30);

		TextField ch = (TextField)gd.getStringFields().lastElement();
		GridBagLayout layout = (GridBagLayout)gd.getLayout();
		GridBagConstraints constraints = layout.getConstraints(ch);

		Button plus = new Button("+");
		plus.addActionListener(e -> {
			moveButtonsDown(gd);
			lastUsedMachineIdx++;
			addMachine(gd, "Machine_" + lastUsedMachineIdx, "localhost");

			gd.pack();
		});
		Button minus = new Button(" - ");
		minus.addActionListener(e -> {
			int labelToRemoveIdx = -1;
			String thelabel = label.replaceAll("_", " ");
			for(int i = 0; i < gd.getComponentCount(); i++) {
				Component c = gd.getComponent(i);
				if(c instanceof Label) {
					Label l = (Label)c;
					if(l.getText().equals(thelabel)) {
						labelToRemoveIdx = i;
						break;
					}
				}
			}
			gd.remove(labelToRemoveIdx); // the label
			gd.remove(labelToRemoveIdx); // the panel
			gd.getStringFields().remove(ch);

			gd.pack();
		});

		Panel panel = new Panel();
		panel.setName("Machine" + (nMachines + 1));
		panel.setLayout(new FlowLayout(FlowLayout.LEFT, 5, 0));
		panel.add(ch);
		panel.add(plus);
		panel.add(minus);

		layout.setConstraints(panel, constraints);
		gd.add(panel);
	}
}
