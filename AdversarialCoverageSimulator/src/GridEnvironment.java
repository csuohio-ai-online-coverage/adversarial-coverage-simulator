import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class GridEnvironment extends Environment {
	private GridNode[][] grid;
	private Dimension gridSize = new Dimension();
	private List<GridRobot> robots;
	private int stepCount = 0;

	public GridEnvironment(Dimension gridSize) {
		this.gridSize.width = gridSize.width;
		this.gridSize.height = gridSize.height;

		// Set up the robot list
		this.robots = new ArrayList<GridRobot>();

		grid = new GridNode[gridSize.width][gridSize.height];
		for (int x = 0; x < gridSize.width; x++) {
			for (int y = 0; y < gridSize.height; y++) {
				grid[x][y] = new GridNode(x, y, NodeType.FREE);
			}
		}
	}

	/**
	 * Create an environment using data from a {@code Scanner}
	 * 
	 * @param input
	 *            a {@code Scanner} to be used in getting data (from a file or
	 *            STDIN)
	 * @return a new {@code GridEnvironment)
	 */
	public GridEnvironment(Scanner input) {
		// First, load the width and height from the first line
		this.gridSize.width = input.nextInt();
		this.gridSize.height = input.nextInt();
		input.nextLine();

		// Now, loop through the cell data on the next lines
		// For now, each cell
		for (int x = 0; x < gridSize.width; x++) {
			for (int y = 0; y < gridSize.height; y++) {

			}
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void draw(Graphics g, Dimension windowSize) {
		// Get the cell size
		Dimension cellSize = new Dimension(windowSize.width / gridSize.width, windowSize.height / gridSize.height);

		// Draw the grid
		for (int x = 0; x < gridSize.width; x++) {
			for (int y = 0; y < gridSize.height; y++) {
				int alpha = (int) (255 * 10 * grid[x][y].dangerProb);
				if (255 < alpha) {
					alpha = 255;
				}
				g.setColor(new Color(200, 50, 255, alpha));
				g.fillRect(x * cellSize.width, y * cellSize.height, cellSize.width, cellSize.height);
				g.setColor(Color.BLACK);
				if (0 < grid[x][y].getCoverCount()) {
					g.setColor(new Color(0, 64, 0));
				} else {
					g.setColor(new Color(255, 20, 40));
				}
				g.drawString("" + grid[x][y].getCoverCount(), x * cellSize.width, (y + 1) * cellSize.height);
				g.setColor(Color.BLACK);

				g.drawRect(x * cellSize.width, y * cellSize.height, cellSize.width, cellSize.height);
			}
		}

		// Draw the robots
		for (GridRobot r : this.robots) {
			g.fillOval(r.getLocation().x * cellSize.width, r.getLocation().y * cellSize.height, cellSize.width,
					cellSize.height);
		}
	}

	/**
	 * Initialize all the robots in the environment
	 */
	public void init() {
		stepCount = 1;
		for (int robotNum = 0; robotNum < this.robots.size(); robotNum++) {
			this.robots.get(robotNum).coverAlgo.init();
		}
	}

	/**
	 * Moves one time step forward. Robots move, etc.
	 */
	public void step() {
		stepCount++;
		for (int robotNum = 0; robotNum < this.robots.size(); robotNum++) {
			if (!this.robots.get(robotNum).isBroken()) {
				this.robots.get(robotNum).coverAlgo.step();
			}
		}
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
	 *            the id of the robot to get
	 * @return the robot
	 */
	public Robot getRobotById(int id) {
		for (int i = 0; i < robots.size(); i++) {
			if (robots.get(i).getId() == id) {
				return robots.get(i);
			}
		}
		return null;
	}

	/**
	 * Returns an array of all the robots at the given (x, y) coordinates
	 * 
	 * @param x
	 *            the x coordinate
	 * @param y
	 *            the y coordinate
	 * @return an array of {@code GridRobot}s
	 */
	public List<GridRobot> getRobotsByLocation(int x, int y) {
		List<GridRobot> robolist = new ArrayList<GridRobot>();
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
	 *            the x coordinate
	 * @param y
	 *            the y coordinate
	 * @return true if the coordinates are within the size of this environment's
	 *         grid, false otherwise
	 */
	public boolean isOnGrid(int x, int y) {
		return (0 <= x && x < this.getWidth() && 0 <= y && y < this.getHeight());
	}

	/**
	 * Gets the grid node at the given location
	 * 
	 * @param x
	 * @param y
	 * @return a {@code GridNode}
	 */
	public GridNode getGridNode(int x, int y) {
		if (isOnGrid(x, y)) {
			return this.grid[x][y];
		} else {
			return null;
		}
	}

	/**
	 * Checks if every grid space has been covered at least once
	 * 
	 * @return true if the graph has been covered at least once, false otherwise
	 */
	public boolean isCovered() {
		for (int x = 0; x < this.getWidth(); x++) {
			for (int y = 0; y < this.getHeight(); y++) {
				if (grid[x][y].getCoverCount() < 1) {
					return false;
				}
			}
		}
		return true;
	}

	public int getStepCount() {
		return this.stepCount;
	}

	/**
	 * Checks whether a terminal state has been reached. This could happen if
	 * the environment is covered or if there are no robots left (i.e. they all
	 * failed).
	 * 
	 * @return true if more steps can be taken, false otherwise
	 */
	public boolean isFinished() {
		return (this.allRobotsBroken() || this.isCovered());
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
}
