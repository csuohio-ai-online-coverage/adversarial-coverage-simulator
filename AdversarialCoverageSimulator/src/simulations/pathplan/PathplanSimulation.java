package simulations.pathplan;

import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import adsim.Algorithm;
import adsim.ConsoleController;
import adsim.Display;
import adsim.SettingsReloadable;
import adsim.Simulation;
import adsim.SimulatorEngine;
import adsim.SimulatorMain;
import adsim.SimulatorSettings;
import adsim.TerminalCommand;
import adsim.stats.SampledVariableLong;
import gridenv.Coordinate;
import gridenv.GridEnvironment;
import gridenv.GridNode;
import gridenv.GridNodeGenerator;
import gridenv.GridRobot;
import gridenv.GridSensor;
import gridenv.NodeType;
import simulations.coverage.CoverageStats;
import simulations.generic.algo.DQL;
import simulations.generic.algo.ExternalDQL;
import simulations.generic.algo.RandomActionAlgo;
import simulations.pathplan.display.PathplanGUIDisplay;

public class PathplanSimulation implements Simulation, SettingsReloadable {

	private double[][] dangerDeltas = null;
	private GridEnvironment env = null;
	private SimulatorEngine engine = null;
	private Coordinate goalPos = new Coordinate();
	private Random random = new Random();
	private GridNodeGenerator nodegen = new GridNodeGenerator();
	private int MAX_STEPS_PER_RUN;
	private boolean VARIABLE_GRID_SIZE;
	private boolean FORCE_SQUARE;
	private int MAX_HEIGHT;
	private int MAX_WIDTH;
	private int MIN_HEIGHT;
	private int MIN_WIDTH;
	private double DANGER_SPREAD_FACTOR;
	private double DANGER_DECAY_FACTOR;
	private double DANGER_CAP;
	private List<SettingsReloadable> settingsReloadableObjs = new ArrayList<>();

	private SampledVariableLong batch_goalReached = new SampledVariableLong();
	private SampledVariableLong batch_manhattanDist = new SampledVariableLong();


	public PathplanSimulation() {

	}


	private Algorithm createNewCoverageAlgoInstance(GridRobot robot) {
		GridSensor sensor = new GridSensor(this.env, robot);
		PathplanActuator actuator = new PathplanActuator(this.env, robot, this);

		String coverageAlgoName = SimulatorMain.settings.getString("adsim.algorithm_name");
		String metaCoverageAlgoName = "";

		Algorithm algo = null;

		if (coverageAlgoName.indexOf('+') != -1) {
			metaCoverageAlgoName = coverageAlgoName.substring(0, coverageAlgoName.indexOf('+')).trim();
			coverageAlgoName = coverageAlgoName.substring(coverageAlgoName.indexOf('+') + 1).trim();
		}

		if (coverageAlgoName.equalsIgnoreCase("DQL")) {
			algo = new DQL(sensor, actuator);
			((DQL) algo).setStatePreprocessor(new PathplanStatePreprocessor(sensor, this));
		} else if (coverageAlgoName.equalsIgnoreCase("Random")) {
			algo = new RandomActionAlgo(sensor, actuator);
		} else {
			algo = new DQL(sensor, actuator);
			((DQL) algo).setStatePreprocessor(new PathplanStatePreprocessor(sensor, this));
		}

		if (!metaCoverageAlgoName.isEmpty()) {
			if (metaCoverageAlgoName.equalsIgnoreCase("ExternalDQL")) {
				algo = new ExternalDQL(sensor, actuator, algo);
				((ExternalDQL) algo).setStatePreprocessor(new PathplanStatePreprocessor(sensor, this));
			}
		}

		return algo;
	}


	@Override
	public void init() {
		this.registerConsoleCommands();
		this.registerDefaultSettings();
		if (!SimulatorMain.args.HEADLESS) {
			PathplanGUIDisplay gd = PathplanGUIDisplay.createInstance(this);
			if (gd != null) {
				gd.setup();
				this.engine.setDisplay(gd);
			}
		}
		this.reloadSettings();
	}


	private void registerDefaultSettings() {
		SimulatorSettings settings = SimulatorMain.settings;
		String settingName = "pathplan.env.danger_decay_factor";
		if (!settings.hasProperty(settingName)) {
			settings.setDouble(settingName, 0.1);
		}

		settingName = "pathplan.env.danger_spread_factor";
		if (!settings.hasProperty(settingName)) {
			settings.setDouble(settingName, 0.1);
		}

		settingName = "pathplan.env.danger_cap";
		if (!settings.hasProperty(settingName)) {
			settings.setDouble(settingName, 0.25);
		}

		settingName = "pathplan.env.clear_obstacles_adjacent_to_goal";
		if (!settings.hasProperty(settingName)) {
			settings.setBoolean(settingName, true);
		}
	}


	private void registerConsoleCommands() {
		final ConsoleController controller = SimulatorMain.controller;
		controller.registerCommand(":setdisplay", new TerminalCommand() {
			@Override
			public void execute(String[] args) {
				if (args.length < 1) {
					return;
				}
				Display display = null;
				if (args[0].equals("gui")) {
					if (GraphicsEnvironment.isHeadless()) {
						return;
					}
					PathplanGUIDisplay gd = PathplanGUIDisplay.createInstance(PathplanSimulation.this);
					gd.setup();
					display = gd;
				} else if (args[0].equals("none")) {
					display = null;
				}
				PathplanSimulation.this.engine.setDisplay(display);
			}
		});


		controller.registerCommand(":restart", new TerminalCommand() {
			@Override
			public void execute(String[] args) {
				PathplanSimulation.this.restartSimulation();
			}
		});


		controller.registerCommand(":set_goal_pos", new TerminalCommand() {
			@Override
			public void execute(String[] args) {
				if (args.length < 2) {
					return;
				}

				try {
					int x = Integer.parseInt(args[0]);
					int y = Integer.parseInt(args[1]);
					PathplanSimulation.this.goalPos.x = x;
					PathplanSimulation.this.goalPos.y = y;
				} catch (NumberFormatException e) {
					System.err.println("Unable to parse input. Usage: :set_goal_pos x y");
				}
			}
		});

	}


	private void resetGoal() {
		this.goalPos.x = this.random.nextInt(this.env.getWidth());
		this.goalPos.y = this.random.nextInt(this.env.getHeight());
		if (SimulatorMain.settings.getBoolean("pathplan.env.clear_obstacles_adjacent_to_goal")) {
			this.env.clear4AdjactentCells(this.goalPos.x, this.goalPos.y);
			this.env.getGridNode(this.goalPos.x, this.goalPos.y).setNodeType(NodeType.FREE);
		}
	}


	@Override
	public boolean isTerminalState() {
		// Terminal states occur when all robots die, one robot reaches the goal
		// or the max number of time steps is reached
		return (this.env.allRobotsBroken() || this.doesGoalHaveRobot() || this.MAX_STEPS_PER_RUN <= this.env.getStepCount());
	}


	private boolean doesGoalHaveRobot() {
		if (this.env == null || this.goalPos == null) {
			return false;
		}

		for (GridRobot r : this.env.robots) {
			if (this.goalPos.equals(r.getLocation())) {
				return true;
			}
		}

		return false;
	}


	@Override
	public void onEnvInit() {
		if (this.env == null) {
			return;
		}
	}


	@Override
	public void onNewRun() {
		this.resetEnvironment();
	}


	@Override
	public void onRunEnd() {
		CoverageStats stats = SimulatorMain.getStats();
		if (this.isTerminalState() && stats != null) {
			int minManhattanDistance = calcMinManhattanGoalDist();
			this.batch_manhattanDist.addSample(minManhattanDistance);
			if (minManhattanDistance == 0) {
				this.batch_goalReached.addSample(1);
			} else {
				this.batch_goalReached.addSample(0);
			}
			System.out.printf("Run end: steps=%d, minMdst=%d, tSv=%.1f, bots=%d/%d\n", stats.getNumTimeSteps(), minManhattanDistance,
					stats.getTeamSurvivability(), stats.getNumSurvivingRobots(), stats.getNumRobots());
			if (this.isBatchEnd()) {
				this.onBatchEnd();
			}
		}

		if (SimulatorMain.settings.getBoolean("autorun.finished.newgrid")) {
			for (GridRobot r : this.env.getRobotList()) {
				r.setBroken(false);
			}

			this.regenerateGrid();
			this.env.init();
		} else {
			this.engine.pauseSimulation();
		}
	}


	private int calcMinManhattanGoalDist() {
		int minManhattanDistance = Integer.MAX_VALUE;
		for (GridRobot r : this.env.robots) {
			Coordinate loc = r.getLocation();
			int manhattanDist = Math.abs(loc.x - this.getGoalX()) + Math.abs(loc.y - this.getGoalY());
			if (manhattanDist < minManhattanDistance) {
				minManhattanDistance = manhattanDist;
			}
		}
		return minManhattanDistance;
	}


	private boolean isBatchEnd() {
		return (SimulatorMain.settings.getInt("stats.multirun.batch_size") <= SimulatorMain.getStats().getRunsInCurrentBatch());
	}


	private void onBatchEnd() {
		final CoverageStats stats = SimulatorMain.getStats();
		final SampledVariableLong stepsPerRunInfo = stats.getBatchStepsPerRunInfo();
		System.out.printf("Batch end (size=%d): steps=%.1f (%.1f), minMdst=%.1f (%.1f), success=%d (%.1f%%)\n", stats.getRunsInCurrentBatch(),
				stepsPerRunInfo.mean(), stepsPerRunInfo.stddev(), this.batch_manhattanDist.mean(), this.batch_manhattanDist.stddev(),
				(int) this.batch_goalReached.sum(), this.batch_goalReached.mean() * 100.0);
		this.resetAllBatchStats();
	}


	private void resetAllBatchStats() {
		SimulatorMain.getStats().resetBatchStats();
		this.batch_goalReached.reset();
		this.batch_manhattanDist.reset();
	}


	@Override
	public void onStep() {
		this.env.step();
		this.updateGridStep();
	}


	private void updateGridStep() {
		GridNode[][] envgrid = this.env.grid;
		int gridWidth = this.env.getWidth();
		int gridHeight = this.env.getHeight();
		for (int x = 0; x < gridWidth; x++) {
			for (int y = 0; y < gridHeight; y++) {
				this.dangerDeltas[x][y] = 0.0;
			}
		}

		for (int x = 0; x < gridWidth; x++) {
			for (int y = 0; y < gridHeight; y++) {
				double curDanger = envgrid[x][y].getDangerProb();
				double dangerSpread = curDanger * envgrid[x][y].spreadability * this.DANGER_SPREAD_FACTOR;
				if (0.0 < envgrid[x][y].dangerFuel) {
					double fuelDelta = Math.min(envgrid[x][y].dangerFuel, curDanger * this.DANGER_DECAY_FACTOR);
					envgrid[x][y].dangerFuel -= fuelDelta;
					this.dangerDeltas[x][y] += fuelDelta;
				} else {
					this.dangerDeltas[x][y] -= curDanger * this.DANGER_DECAY_FACTOR;
				}

				if ((x + 1) < gridWidth) {
					this.dangerDeltas[x + 1][y] += dangerSpread;
				}
				if (0 < x) {
					this.dangerDeltas[x - 1][y] += dangerSpread;
				}
				if ((y + 1) < gridHeight) {
					this.dangerDeltas[x][y + 1] += dangerSpread;
				}
				if (0 < y) {
					this.dangerDeltas[x][y - 1] += dangerSpread;
				}
			}
		}

		for (int x = 0; x < gridWidth; x++) {
			for (int y = 0; y < gridHeight; y++) {
				double newVal = envgrid[x][y].getDangerProb() + this.dangerDeltas[x][y];
				if (newVal < 0.0) {
					newVal = 0;
				} else if (this.DANGER_CAP < newVal) {
					newVal = this.DANGER_CAP;
				}
				envgrid[x][y].setDangerProb(newVal);
			}
		}
	}


	public int getGoalX() {
		return this.goalPos.x;
	}


	public int getGoalY() {
		return this.goalPos.y;
	}


	public void restartSimulation() {
		this.engine.pauseSimulation();
		reinitializeSimulation();
		this.engine.refreshDisplay();
	}


	private void reinitializeSimulation() {
		for (int x = 0; x < this.env.getWidth(); x++) {
			for (int y = 0; y < this.env.getHeight(); y++) {
				this.env.getGridNode(x, y).setCoverCount(0);
			}
		}
		this.env.init();
	}


	@Override
	public void reloadSettings() {
		if (this.env != null) {
			this.env.reloadSettings();
		}
		for (SettingsReloadable s : this.settingsReloadableObjs) {
			if (s != null) {
				s.reloadSettings();
			}
		}

		final SimulatorSettings settings = SimulatorMain.settings;

		this.MAX_STEPS_PER_RUN = settings.getInt("autorun.max_steps_per_run");
		this.VARIABLE_GRID_SIZE = settings.getBoolean("env.variable_grid_size");
		this.FORCE_SQUARE = settings.getBoolean("env.grid.force_square");
		this.MAX_HEIGHT = settings.getInt("env.grid.maxheight");
		this.MAX_WIDTH = settings.getInt("env.grid.maxwidth");
		this.MIN_HEIGHT = settings.getInt("env.grid.minheight");
		this.MIN_WIDTH = settings.getInt("env.grid.minwidth");
		this.DANGER_DECAY_FACTOR = settings.getDouble("pathplan.env.danger_decay_factor");
		this.DANGER_SPREAD_FACTOR = settings.getDouble("pathplan.env.danger_spread_factor");
		this.DANGER_CAP = settings.getDouble("pathplan.env.danger_cap");
	}


	/**
	 * Sets up the environment using the settings
	 */
	private void resetEnvironment() {
		this.env = new GridEnvironment(
				new Dimension(SimulatorMain.settings.getInt("env.grid.width"), SimulatorMain.settings.getInt("env.grid.height")));
		this.dangerDeltas = new double[SimulatorMain.settings.getInt("env.grid.width")][SimulatorMain.settings.getInt("env.grid.height")];
		// Set up the coverage environment
		this.regenerateGrid();

		// Set up the robots
		for (int i = 0; i < SimulatorMain.settings.getInt("robots.count"); i++) {
			GridRobot robot = new GridRobot(i, (int) (Math.random() * this.env.getWidth()), (int) (Math.random() * this.env.getHeight()));
			robot.coverAlgo = this.createNewCoverageAlgoInstance(robot);
			this.env.addRobot(robot);
		}
		SimulatorMain.setStats(new CoverageStats(this.env, this.env.getRobotList()));
		SimulatorMain.getStats().resetBatchStats();

		this.env.init();
	}


	private void regenerateGrid() {
		if (this.VARIABLE_GRID_SIZE) {
			int newWidth = (int) (this.random.nextDouble() * (this.MAX_WIDTH - this.MIN_WIDTH) + this.MIN_WIDTH);
			int newHeight = (int) (this.random.nextDouble() * (this.MAX_HEIGHT - this.MIN_HEIGHT) + this.MIN_HEIGHT);
			if (this.FORCE_SQUARE) {
				newHeight = newWidth;
			}

			this.env.setSize(new Dimension(newWidth, newHeight));
		}

		String dangerValStr = SimulatorMain.settings.getString("env.grid.dangervalues");

		// To save time, only recompile the generator if the string has changed
		if (!this.nodegen.getGeneratorString().equals(dangerValStr)) {
			this.nodegen.setGeneratorString(dangerValStr);
		}

		this.nodegen.setRandomMap();

		int gridWidth = this.env.getWidth();
		int gridHeight = this.env.getHeight();

		for (int x = 0; x < gridWidth; x++) {
			for (int y = 0; y < gridHeight; y++) {
				this.nodegen.genNext(this.env.grid[x][y]);
			}
		}


		this.resetGoal();
	}


	@Override
	public void setEngine(SimulatorEngine engine) {
		this.engine = engine;
	}


	public SimulatorEngine getEngine() {
		return this.engine;
	}


	public GridEnvironment getEnv() {
		return this.env;
	}


	@Override
	public void dispose() {
		// TODO Auto-generated method stub

	}

}
