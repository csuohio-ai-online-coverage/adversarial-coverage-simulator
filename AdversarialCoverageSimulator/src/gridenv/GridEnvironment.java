package gridenv;

import java.awt.Dimension;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import adsim.NodeType;
import adsim.Robot;
import adsim.SimulatorMain;
import adsim.TerminalCommand;

public class GridEnvironment {
	public GridNode[][] grid;
	public Dimension gridSize = new Dimension();
	public List<GridRobot> robots;
	private int stepCount = 0;
	

	private Random randgen = new Random();

	private GridNodeGenerator nodegen = new GridNodeGenerator();

	private boolean RANDOMIZE_ROBOT_LOCATION_ON_INIT = SimulatorMain.settings.getBoolean("autorun.randomize_robot_start");
	private boolean CLEAR_ADJACENT_CELLS_ON_INIT = SimulatorMain.settings.getBoolean("env.clear_adjacent_cells_on_init");
	private boolean VARIABLE_GRID_SIZE = SimulatorMain.settings.getBoolean("env.variable_grid_size");
	private boolean FORCE_SQUARE = SimulatorMain.settings.getBoolean("env.grid.force_square");
	private int MAX_HEIGHT = SimulatorMain.settings.getInt("env.grid.maxheight");
	private int MAX_WIDTH = SimulatorMain.settings.getInt("env.grid.maxwidth");
	private int MIN_HEIGHT = SimulatorMain.settings.getInt("env.grid.minheight");
	private int MIN_WIDTH = SimulatorMain.settings.getInt("env.grid.minwidth");


	public GridEnvironment(Dimension gridSize) {
		this.gridSize.width = gridSize.width;
		this.gridSize.height = gridSize.height;

		// Set up the robot list
		this.robots = new ArrayList<>();

		this.grid = new GridNode[gridSize.width][gridSize.height];
		for (int x = 0; x < gridSize.width; x++) {
			for (int y = 0; y < gridSize.height; y++) {
				this.grid[x][y] = new GridNode(x, y, NodeType.FREE);
			}
		}

		registerCustomCommands();

	}


	private void registerCustomCommands() {
		SimulatorMain.controller.registerCommand(":env_printgrid", new TerminalCommand() {
			@Override
			public void execute(String[] args) {
				String streamname = "stdout";
				if (1 <= args.length) {
					streamname = args[0];
				}

				PrintStream stream = System.out;
				if (streamname.equalsIgnoreCase("stderr")) {
					stream = System.err;
				}

				printToWriter_onelayer(stream);

			}
		});

		SimulatorMain.controller.registerCommand(":env_set_robot_pos", new TerminalCommand() {
			@Override
			public void execute(String[] args) {
				if (args.length < 3) {
					System.err.println("Usage: :env_set_robot_pos <robotId> <x> <y>");
					return;
				}
				try {
					int robotId = Integer.parseInt(args[0]);
					int xPos = Integer.parseInt(args[1]);
					int yPos = Integer.parseInt(args[2]);

					GridRobot robot = GridEnvironment.this.getRobotById(robotId);
					if (robot == null) {
						System.err.println("No robot found with specified robotId.");
						return;
					}
					robot.setLocation(xPos, yPos);
				} catch (NumberFormatException e) {
					System.err.println("One or more numbers were formatted incorrectly.");
				}
			}
		});
	}


	/**
	 * Initialize all the robots in the environment
	 */
	public void init() {
		
		this.stepCount = 1;
		for (int robotNum = 0; robotNum < this.robots.size(); robotNum++) {
			if (this.RANDOMIZE_ROBOT_LOCATION_ON_INIT) {
				Coordinate location = new Coordinate(-1, -1);
				while (location.x == -1 || this.getGridNode(location.x, location.y).getNodeType() == NodeType.OBSTACLE) {
					location.x = (int) (Math.random() * this.getWidth());
					location.y = (int) (Math.random() * this.getHeight());
				}
				this.robots.get(robotNum).setLocation(location.x, location.y);
				if (this.CLEAR_ADJACENT_CELLS_ON_INIT) {
					clearAdjactentCells(this.robots.get(robotNum).getLocation().x, this.robots.get(robotNum).getLocation().y);
				}

				this.getGridNode(this.robots.get(robotNum).getLocation().x, this.robots.get(robotNum).getLocation().y)
						.setNodeType(NodeType.FREE);
			}
			this.robots.get(robotNum).coverAlgo.init();

		}
		
		SimulatorMain.controller.runCommand_noEcho(SimulatorMain.settings.getString("hooks.env.post_init.cmd"));
		SimulatorMain.getEngine().getSimulation().onEnvInit();
	}


	public void printToWriter_onelayer(PrintStream pw) {
		for (int y = 0; y < this.getHeight(); y++) {
			for (int x = 0; x < this.getWidth(); x++) {
				GridNode node = this.grid[x][y];
				if (node.getNodeType() == NodeType.OBSTACLE) {
					pw.printf("%4s", "OBS");
				} else if (node.getDangerProb() == 0.0) {
					pw.printf("%4s", "FREE");
				} else {
					pw.printf("%4.2f", node.getDangerProb());
				}

				int robotId = -1;
				for (GridRobot r : this.robots) {
					if (r.getLocation().x == x && r.getLocation().y == y) {
						robotId = r.getId();
						break;
					}
				}

				if (robotId == -1) {
					pw.printf("%c ", node.getCoverCount() <= 0 ? 'N' : 'Y');
				} else {
					pw.printf("* ");
				}

			}
			pw.println();
		}
	}


	/**
	 * Clears the four cells directly adjacent to the given grid coordinates
	 * 
	 * @param x
	 *                the x coordinate
	 * @param y
	 *                the y coordinate
	 */
	private void clearAdjactentCells(int x, int y) {
		if (this.isOnGrid(x + 1, y)) {
			this.getGridNode(x + 1, y).setNodeType(NodeType.FREE);
		}
		if (this.isOnGrid(x - 1, y)) {
			this.getGridNode(x - 1, y).setNodeType(NodeType.FREE);
		}
		if (this.isOnGrid(x, y + 1)) {
			this.getGridNode(x, y + 1).setNodeType(NodeType.FREE);
		}
		if (this.isOnGrid(x, y - 1)) {
			this.getGridNode(x, y - 1).setNodeType(NodeType.FREE);
		}
	}


	public synchronized void setSize(Dimension newGridSize) {
		GridNode[][] newGrid = new GridNode[newGridSize.width][newGridSize.height];
		for (int x = 0; x < newGridSize.width; x++) {
			for (int y = 0; y < newGridSize.height; y++) {
				if (x < this.gridSize.width && y < this.gridSize.height) {
					newGrid[x][y] = this.grid[x][y];
				} else {
					newGrid[x][y] = new GridNode(x, y, NodeType.FREE);
				}
			}
		}

		this.grid = newGrid;

		this.gridSize.width = newGridSize.width;
		this.gridSize.height = newGridSize.height;
	}


	public void regenerateGrid() {
		if (this.VARIABLE_GRID_SIZE) {
			int newWidth = (int) (this.randgen.nextDouble() * (this.MAX_WIDTH - this.MIN_WIDTH) + this.MIN_WIDTH);
			int newHeight = (int) (this.randgen.nextDouble() * (this.MAX_HEIGHT - this.MIN_HEIGHT) + this.MIN_HEIGHT);
			if (this.FORCE_SQUARE) {
				newHeight = newWidth;
			}

			this.setSize(new Dimension(newWidth, newHeight));
		}

		String dangerValStr = SimulatorMain.settings.getString("env.grid.dangervalues");

		// To save time, only recompile the generator if the string has changed
		if (!this.nodegen.getGeneratorString().equals(dangerValStr)) {
			this.nodegen.setGeneratorString(dangerValStr);
		}

		this.nodegen.setRandomMap();

		for (int x = 0; x < this.getWidth(); x++) {
			for (int y = 0; y < this.getHeight(); y++) {
				this.nodegen.genNext(this.grid[x][y]);
			}
		}
	}


	/**
	 * Moves one time step forward. Robots move, etc.
	 */
	public void step() {
		this.stepCount++;
		for (int robotNum = 0; robotNum < this.robots.size(); robotNum++) {
			if (!this.robots.get(robotNum).isBroken()) {
				this.robots.get(robotNum).coverAlgo.step();
			}
		}
	}


	public String exportToString() {
		StringBuilder sb = new StringBuilder();
		for (int x = 0; x < this.getWidth(); x++) {
			for (int y = 0; y < this.getHeight(); y++) {
				sb.append(String.format("%f ", this.grid[x][y].getDangerProb()));
			}
		}
		return sb.toString();
	}


	/**
	 * Add the given robot to the environment
	 */
	public void addRobot(GridRobot robot) {
		this.robots.add(robot);
	}


	/**
	 * Gets the robot in this environment that has the specified id
	 * 
	 * @param id
	 *                the id of the robot to get
	 * @return the robot
	 */
	public GridRobot getRobotById(int id) {
		for (int i = 0; i < this.robots.size(); i++) {
			if (this.robots.get(i).getId() == id) {
				return this.robots.get(i);
			}
		}
		return null;
	}


	/**
	 * Returns an array of all the robots at the given (x, y) coordinates
	 * 
	 * @param x
	 *                the x coordinate
	 * @param y
	 *                the y coordinate
	 * @return an array of {@code GridRobot}s
	 */
	public List<GridRobot> getRobotsByLocation(int x, int y) {
		List<GridRobot> robolist = new ArrayList<>();
		for (int i = 0; i < this.robots.size(); i++) {
			if (this.robots.get(i).getLocation().x == x && this.robots.get(i).getLocation().y == y) {
				robolist.add(this.robots.get(i));
			}
		}
		return robolist;
	}


	/**
	 * Gets the list of all robots in the environment
	 * 
	 * @return
	 */
	public List<GridRobot> getRobotList() {
		return this.robots;
	}


	/**
	 * Gets the width of the grid
	 * 
	 * @return the width of the grid
	 */
	public int getWidth() {
		return this.gridSize.width;
	}


	/**
	 * Gets the height of the grid
	 * 
	 * @return the height of the grid
	 */
	public int getHeight() {
		return this.gridSize.height;
	}


	/**
	 * Checks if the given coordinates are on the grid
	 * 
	 * @param x
	 *                the x coordinate
	 * @param y
	 *                the y coordinate
	 * @return true if the coordinates are within the size of this environment's grid,
	 *         false otherwise
	 */
	public boolean isOnGrid(int x, int y) {
		return (0 <= x && x < this.getWidth() && 0 <= y && y < this.getHeight());
	}


	/**
	 * Gets the grid node at the given location
	 * 
	 * @param x
	 * @param y
	 * @return a {@code GridNode}, or null if the given coordinates are not on the
	 *         grid
	 */
	public GridNode getGridNode(int x, int y) {
		if (isOnGrid(x, y)) {
			return this.grid[x][y];
		}
		return null;
	}


	public int getStepCount() {
		return this.stepCount;
	}


	/**
	 * Checks if all the robots in the environment are broken
	 * 
	 * @return true if all robots are borken, false otherwise
	 */
	public boolean allRobotsBroken() {
		for (Robot r : this.robots) {
			if (!r.isBroken()) {
				return false;
			}
		}
		return true;
	}


	public void reloadSettings() {
		for (Robot r : this.robots) {
			r.reloadSettings();
		}
		this.RANDOMIZE_ROBOT_LOCATION_ON_INIT = SimulatorMain.settings.getBoolean("autorun.randomize_robot_start");
		this.CLEAR_ADJACENT_CELLS_ON_INIT = SimulatorMain.settings.getBoolean("env.clear_adjacent_cells_on_init");
		this.VARIABLE_GRID_SIZE = SimulatorMain.settings.getBoolean("env.variable_grid_size");
		this.FORCE_SQUARE = SimulatorMain.settings.getBoolean("env.grid.force_square");
		this.MAX_HEIGHT = SimulatorMain.settings.getInt("env.grid.maxheight");
		this.MAX_WIDTH = SimulatorMain.settings.getInt("env.grid.maxwidth");
		this.MIN_HEIGHT = SimulatorMain.settings.getInt("env.grid.minheight");
		this.MIN_WIDTH = SimulatorMain.settings.getInt("env.grid.minwidth");
	}
}
