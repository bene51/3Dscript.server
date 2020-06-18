package animation3d.server;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

import animation3d.parser.Interpreter;
import animation3d.parser.NoSuchMacroException;
import animation3d.parser.ParsingResult;
import animation3d.parser.Preprocessor;
import animation3d.parser.Preprocessor.PreprocessingException;
import animation3d.renderer3d.ExtendedRenderingState;
import animation3d.renderer3d.KeywordFactory;
import animation3d.renderer3d.RenderingAlgorithm;
import animation3d.renderer3d.RenderingSettings;
import animation3d.textanim.Animation;
import animation3d.textanim.CombinedTransform;
import animation3d.textanim.RenderingState;
import animation3d.util.Transform;

public class ScriptAnalyzer {

	private final String script;
	private final ArrayList<Animation> animations;

	public ScriptAnalyzer(String script) {
		this.script = script;
		this.animations = new ArrayList<Animation>();
	}

	public void analyze() {

	}

	public static void main(String[] args) throws PreprocessingException, NoSuchMacroException {
		// testPartition();
		String script = "From frame 0 to frame 100 change timepoint to 50";
		ScriptAnalyzer sa = new ScriptAnalyzer(script);
		int[][] partitionArray = sa.partition(2);
		for(int i = 0; i < partitionArray.length; i++) {
			String partString = partitionToString(partitionArray[i]);
			System.out.println(partString + ": \n" + Arrays.toString(partitionArray[i]));
			System.out.println(partString + ": \n" + Arrays.toString(partitionFromString(partString)));
		}
		System.out.println(partitionToString(new int[] {1, 2, 3, 4, 7}));
	}

	public static final String partitionToString(int[] partition) {
		// Arrays.sort(partition);
		int rstart = partition[0];
		int rcurrent = partition[0];
		StringBuffer buf = new StringBuffer();
		for(int i = 1; i < partition.length; i++) {
			// next value is next in range:
			if(partition[i] == rcurrent + 1) {
				rcurrent++;
				continue;
			}
			// gab between range end and next value;
			// output the current range:

			// was a single value:
			if(rstart == rcurrent)
				buf.append(rstart).append("+");
			else
				buf.append(rstart).append("-").append(rcurrent).append("+");

			rstart = rcurrent = partition[i];
		}
		// add last range/value
		if(rstart == rcurrent)
			buf.append(rstart);
		else
			buf.append(rstart).append("-").append(rcurrent);

		return buf.toString();
	}

	public static final int[] partitionFromString(String s) {
		List<Integer> list = new ArrayList<Integer>();
		String[] toks = s.split("\\+");
		for(String tok : toks) {
			if(tok.trim().isEmpty())
				continue;
			// single value:
			if(tok.indexOf('-') == -1)
				list.add(Integer.parseInt(tok));
			else {
				String[] toks2 = tok.split("-");
				if(toks2.length != 2)
					throw new RuntimeException("Expected something like 1-4");
				int from = Integer.parseInt(toks2[0]);
				int to = Integer.parseInt(toks2[1]);
				for(int j = from; j <= to; j++)
					list.add(j);

			}
		}
		int[] ret = new int[list.size()];
		for(int i = 0; i < ret.length; i++)
			ret[i] = list.get(i);
		return ret;
	}

	private static void testPartition() {
		HashMap<Integer, List<Integer>> partitions;
		partitions = new HashMap<Integer, List<Integer>>();
		partitions.put(3, new ArrayList<Integer>(Arrays.asList(3, 5, 10, 4)));
		partitions.put(8, new ArrayList<Integer>(Arrays.asList(2, 1, 9, 6)));
		partitions.put(2, new ArrayList<Integer>(Arrays.asList(11, 7)));
		testPartition(partitions, 2);
	}

	private static void testPartition(HashMap<Integer, List<Integer>> partitions, int nPartitions) {
		int[][] partitionArray = partition(partitions, nPartitions);
		for(int i = 0; i < partitionArray.length; i++) {
			System.out.println(Arrays.toString(partitionArray[i]));
		}
	}

	public int[][] partition() throws PreprocessingException, NoSuchMacroException {
		return partition(-1);
	}

	public int[][] partition(int nPartitions) throws PreprocessingException, NoSuchMacroException {
		HashMap<String, String> macros = new HashMap<String, String>();
		ArrayList<String> lines = new ArrayList<String>();

		Preprocessor.preprocess(script, lines, macros);

		float[] rotcenter = new float[] {0, 0, 0};
		int from = 0;
		int to = 0;

		for(String line : lines) {
			ParsingResult pr = new ParsingResult();
			Interpreter.parse(new KeywordFactory(), line, rotcenter, pr);
			to   = Math.max(to, pr.getTo());
			Animation ta = pr.getResult();
			if(ta != null) {
				ta.pickScripts(macros);
				animations.add(ta);
			}
		}
		List<RenderingState> frames = createRenderingStates(from, to);
		HashMap<Integer, List<Integer>> partitions = new HashMap<Integer, List<Integer>>();
		for(int fIdx = 0; fIdx < frames.size(); fIdx++) {
			RenderingState rs = frames.get(fIdx);
			int t = (int)rs.getNonChannelProperty(ExtendedRenderingState.TIMEPOINT);
			System.out.println("t = " + t);
			List<Integer> framesAtT = partitions.get(t);
			if(framesAtT == null) {
				framesAtT = new ArrayList<Integer>();
				partitions.put(t, framesAtT);
			}
			framesAtT.add(fIdx);
		}
		return partition(partitions, nPartitions);
	}

	private static int[][] partition(HashMap<Integer, List<Integer>> partitions, int nPartitions) {
		List<List<Integer>> partitionList = new ArrayList<List<Integer>>(partitions.values());

		while(nPartitions > 0 && partitionList.size() > nPartitions) {
			partitionList.sort(new Comparator<List<Integer>>() {
				@Override
				public int compare(List<Integer> o1, List<Integer> o2) {
					return -Integer.compare(o1.size(), o2.size());
				}
			});

//			List<Integer> secondSmallest = partitionList.remove(1);
//			partitionList.get(0).addAll(secondSmallest);
			List<Integer> largestRemaining = partitionList.remove(nPartitions);
			partitionList.get(nPartitions - 1).addAll(largestRemaining);
		}

		int[][] partitionArray = new int[partitionList.size()][];
		for(int i = 0; i < partitionArray.length; i++) {
			List<Integer> list = partitionList.get(i);
			partitionArray[i] = new int[list.size()];
			for(int j = 0; j < partitionArray[i].length; j++)
				partitionArray[i][j] = list.get(j);
		}
		return partitionArray;
	}

	private static final ExtendedRenderingState getDefaultRenderingState() {
		final int nC = 100;
		float[] pdIn = new float[] { 1, 1, 1 };
		float[] p = new float[] {1, 1, 1};

		float near = 0;
		float far  = 1000;
		float[] rotcenter = new float[] { 100, 100, 100 };

		RenderingSettings[] renderingSettings = new RenderingSettings[nC];
		Color[] channelColors = new Color[nC];
		for(int c = 0; c < nC; c++) {
			renderingSettings[c] = new RenderingSettings(
					0, 255, 1,
					0, 255, 2,
					1,
					0, 0, 0,
					200, 200, 200,
					near, far);
			channelColors[c] = Color.WHITE;
		}

		CombinedTransform transformation = new CombinedTransform(pdIn, p, rotcenter);

		ExtendedRenderingState rs = new ExtendedRenderingState(0,
				1,
				renderingSettings,
				channelColors,
				Color.BLACK,
				RenderingAlgorithm.INDEPENDENT_TRANSPARENCY,
				transformation);
		return rs;
	}

	// from Animator
	public List<RenderingState> createRenderingStates(int from, int to) {
		List<RenderingState> renderingStates = new ArrayList<RenderingState>();
		RenderingState previous = getDefaultRenderingState();
		for(int t = from; t <= to; t++) {
			RenderingState kf = previous.clone();
			kf.getFwdTransform().setTransformation(Transform.fromIdentity(null));
			kf.setFrame(t);
			for(Animation a : animations)
				a.adjustRenderingState(kf, renderingStates, 1);
			renderingStates.add(kf);
			previous = kf;
		}
		return renderingStates;
	}

}
