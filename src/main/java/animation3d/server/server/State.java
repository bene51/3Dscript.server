package animation3d.server.server;

public enum State {
	QUEUED(0, 0),
	OPENING(10, 0),
	OPENED(0, 10),
	RENDERING(60, 10),
	SAVING(10, 70),
	CONVERTING(10, 80),
	ATTACHING(10, 90),
	FINISHED(0, 100),
	ERROR(0, 100);

	/** in % */
	public final double duration;
	public final double start;

	State(double duration, double start) {
		this.duration = duration;
		this.start = start;
	}
}
