package animation3d.server;

public enum State {
	QUEUED(0, 0),
	DOWNLOADING(40, 0),
	OPENING(5, 40),
	OPENED(0, 45),
	RENDERING(30, 45),
	SAVING(10, 75),
	CONVERTING(10, 85),
	FINISHED(0, 95),
	ERROR(0, 95);

	/** in % */
	public final double duration;
	public final double start;

	State(double duration, double start) {
		this.duration = duration;
		this.start = start;
	}
}
