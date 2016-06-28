package adversarialcoverage;

import java.awt.Dimension;
import java.io.PrintStream;

import coveragealgorithms.*;
import adversarialcoverage.stats.*;

public class CoverageEngine {

	private boolean isRunning = false;
	private DisplayAdapter display = null;
	private GridEnvironment env = null;
	private Thread coverageThread = null;
	private static final DisplayAdapter EmptyDisplayAdapter = new DisplayAdapter() {
		@Override
		public void refresh() {
			// Do nothing
		}


		@Override
		public void dispose() {
			// Do nothing
		}
	};


	public CoverageEngine(DisplayAdapter display) {
		this.setDisplay(display);
		this.init();
	}


	public CoverageEngine() {
		this(CoverageEngine.EmptyDisplayAdapter);
	}


	public void init() {

	}


	public GridEnvironment getEnv() {
		return this.env;
	}


	public void stepCoverage() {
		if (!this.env.isFinished()) {
			this.step();
		}
		refreshDisplay();
	}


	public void runCoverage() {
		this.isRunning = true;
		startCoverageLoop();
	}


	public void pauseCoverage() {
		this.isRunning = false;
	}


	public void restartCoverage() {
		this.isRunning = false;
		reinitializeCoverage();
		refreshDisplay();
	}


	public void newCoverage() {
		this.isRunning = false;
		this.resetCoverageEnvironment();
		refreshDisplay();
	}


	/**
	 * Updates the display, which may be a GUI window, or in a headless environment, a
	 * terminal.
	 */
	public void refreshDisplay() {
		this.display.refresh();
	}


	public void setDisplay(DisplayAdapter display) {
		// Careful with these if statements

		// If the OLD display is not null
		if (this.display != null) {
			this.display.dispose();
		}

		// If the NEW display is not null
		if (display != null) {
			this.display = display;
		} else {
			this.display = CoverageEngine.EmptyDisplayAdapter;
		}
	}


	private void reinitializeCoverage() {
		for (int x = 0; x < this.env.getWidth(); x++) {
			for (int y = 0; y < this.env.getHeight(); y++) {
				this.env.getGridNode(x, y).setCoverCount(0);
			}
		}
		this.env.init();
	}


	/**
	 * Sets up the environment using the settings
	 */
	public void resetCoverageEnvironment() {
		this.env = new GridEnvironment(new Dimension(AdversarialCoverage.settings.getInt("env.grid.width"),
				AdversarialCoverage.settings.getInt("env.grid.height")));


		// Set up the coverage environment
		this.env.regenerateGrid();

		// Set up the robots
		for (int i = 0; i < AdversarialCoverage.settings.getInt("robots.count"); i++) {
			GridRobot robot = new GridRobot(i, (int) (Math.random() * this.env.getWidth()), (int) (Math.random() * this.env.getHeight()));
			robot.coverAlgo = this.createNewCoverageAlgoInstance(robot);
			this.env.addRobot(robot);
		}
		AdversarialCoverage.stats = new SimulationStats(this.env, this.env.getRobotList());
		AdversarialCoverage.stats.startNewBatch();

		this.env.init();

	}


	private GridCoverageAlgorithm createNewCoverageAlgoInstance(GridRobot robot) {
		GridSensor sensor = new GridSensor(this.env, robot);
		GridActuator actuator = new GridActuator(this.env, robot);

		String coverageAlgoName = AdversarialCoverage.settings.getString("coverage.algorithm_name");
		String metaCoverageAlgoName = "";

		GridCoverageAlgorithm algo = null;

		if (coverageAlgoName.indexOf('+') != -1) {
			metaCoverageAlgoName = coverageAlgoName.substring(0, coverageAlgoName.indexOf('+')).trim();
			coverageAlgoName = coverageAlgoName.substring(coverageAlgoName.indexOf('+') + 1).trim();
		}

		if (coverageAlgoName.equalsIgnoreCase("DQLGC")) {
			algo = new DQLGC(sensor, actuator);
		} else if (coverageAlgoName.equalsIgnoreCase("RandomGC")) {
			algo = new RandomGC(sensor, actuator);
		} else if (coverageAlgoName.equalsIgnoreCase("GSACGC")) {
			algo = new GSACGC(sensor, actuator);
		} else {
			algo = new DQLGC(sensor, actuator);
		}

		if (!metaCoverageAlgoName.isEmpty()) {
			if (metaCoverageAlgoName.equalsIgnoreCase("ExternalDQLGC")) {
				algo = new ExternalDQLGC(sensor, actuator, algo);
			}
		}

		return algo;
	}


	private void startCoverageLoop() {
		if (this.coverageThread != null && this.coverageThread.isAlive()) {
			System.err.print("Coverage thread is already running. No action will be taken.\n");
			return;
		}

		this.coverageThread = new Thread() {
			@Override
			public void run() {
				coverageLoop();
			}
		};
		this.coverageThread.start();
	}


	private void coverageLoop() {

		// Update settings
		this.env.reloadSettings();

		long delay = AdversarialCoverage.settings.getInt("autorun.stepdelay");
		boolean doRepaint = AdversarialCoverage.settings.getBoolean("autorun.do_repaint");

		while (this.isRunning) {
			long time = System.currentTimeMillis();
			this.step();
			if (doRepaint && !AdversarialCoverage.args.HEADLESS) {
				refreshDisplay();
			}
			if (this.env.isFinished()) {
				handleCoverageCompletion();
			}

			time = System.currentTimeMillis() - time;
			if (time < delay) {
				try {
					Thread.sleep(delay - time);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
	}


	private void handleCoverageCompletion() {
		long statsBatchSize = AdversarialCoverage.settings.getInt("stats.multirun.batch_size");
		SimulationStats stats = AdversarialCoverage.stats;
		if (this.env.isFinished() && stats != null) {
			System.out.printf("END OF RUN: steps=%d, cover=%d/%d, teamSurv=%.3f, bots=%d/%d\n", stats.getNumTimeSteps(),
					stats.getTotalCellsCovered(), stats.getTotalFreeCells(), stats.getTeamSurvivability(),
					stats.getNumSurvivingRobots(), stats.getNumRobots());
			if (stats.getRunsInCurrentBatch() % statsBatchSize == 0 && stats.getRunsInCurrentBatch() != 0) {
				SampledVariableLong stepsPerRunInfo = stats.getBatchStepsPerRunInfo();
				SampledVariableDouble survivabilityInfo = stats.getBatchSurvivability();
				System.out.printf("Batch averages (size=%d): steps=%.3f (%.2f), teamSurv=%.3f (%.2f)\n",
						stats.getRunsInCurrentBatch(), stepsPerRunInfo.mean(), stepsPerRunInfo.stddev(),
						survivabilityInfo.mean(), survivabilityInfo.stddev());
				stats.startNewBatch();
			}
		}

		if (AdversarialCoverage.settings.getBoolean("autorun.finished.display_full_stats")) {
			AdversarialCoverage.printStats(new PrintStream(System.out));
		}
		if (AdversarialCoverage.settings.getBoolean("autorun.finished.newgrid")) {
			for (GridRobot r : this.env.getRobotList()) {
				r.setBroken(false);
			}

			this.env.regenerateGrid();
			this.env.init();

			refreshDisplay();

		} else {
			this.isRunning = false;
		}

		if (stats != null) {
			stats.startNewRun();
		}
	}


	private void step() {
		this.env.step();
		AdversarialCoverage.stats.updateTimeStep();
	}


	public boolean isRunning() {
		return this.isRunning;
	}


	public void kill() {
		this.isRunning = false;
		this.display.dispose();
	}

}
