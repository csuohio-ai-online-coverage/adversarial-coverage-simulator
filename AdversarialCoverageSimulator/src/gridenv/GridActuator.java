package gridenv;

import adsim.NodeType;
import adsim.Simulation;
import adsim.SimulatorMain;
import simulations.coverage.CoverageSimulation;

public class GridActuator {
	/**
	 * The environment in which this actuator exists
	 */
	private GridEnvironment env;
	/**
	 * The robot to which this actuator is attached.
	 */
	private GridRobot robot;
	private double lastReward = 0.0;
	private int lastActionId = -1;
	private boolean ROBOTS_BREAKABLE = SimulatorMain.settings.getBoolean("robots.breakable");


	/**
	 * Constructs an actuator for the given environment and robot
	 * 
	 * @param env
	 *                the environment that this actuator will affect
	 * @param robot
	 *                the robot that controls this actuator
	 */
	public GridActuator(GridEnvironment env, GridRobot robot) {
		this.env = env;
		this.robot = robot;
	}


	/**
	 * Move the robot 1 cell to the right on the grid
	 */
	public void moveRight() {
		Coordinate newLoc = new Coordinate(this.robot.getLocation().x + 1, this.robot.getLocation().y);
		moveTo(newLoc);
		this.lastActionId = 0;
	}


	/**
	 * Move the robot 1 cell to the left on the grid
	 */
	public void moveLeft() {
		Coordinate newLoc = new Coordinate(this.robot.getLocation().x - 1, this.robot.getLocation().y);
		moveTo(newLoc);
		this.lastActionId = 2;
	}


	/**
	 * Move the robot 1 cell upward (North) on the grid
	 */
	public void moveUp() {
		Coordinate newLoc = new Coordinate(this.robot.getLocation().x, this.robot.getLocation().y + 1);
		moveTo(newLoc);
		this.lastActionId = 1;
	}


	/**
	 * Move the robot 1 cell downward (South) on the grid
	 */
	public void moveDown() {
		Coordinate newLoc = new Coordinate(this.robot.getLocation().x, this.robot.getLocation().y - 1);
		moveTo(newLoc);
		this.lastActionId = 3;
	}


	private void moveTo(Coordinate newLoc) {
		this.lastReward = 0.0;
		if (this.robot.isBroken()) {
			return;
		}

		// Move, if possible
		if (this.env.isOnGrid(newLoc.x, newLoc.y) && this.env.getGridNode(newLoc.x, newLoc.y).getNodeType() != NodeType.OBSTACLE
				&& this.env.getRobotsByLocation(newLoc.x, newLoc.y).size() == 0) {
			this.robot.setLocation(newLoc.x, newLoc.y);
		}

		this.processCoveringCurrentNode();
	}


	/**
	 * Don't move, just cover the current node again.
	 */
	public void coverCurrentNode() {
		this.processCoveringCurrentNode();
		this.lastActionId = 4;
	}


	private void processCoveringCurrentNode() {
		double rand = Math.random();
		boolean isThreat = rand < this.env.getGridNode(this.robot.getLocation().x, this.robot.getLocation().y).getDangerProb()
				&& this.ROBOTS_BREAKABLE;
		int coverCount = this.env.getGridNode(this.robot.getLocation().x, this.robot.getLocation().y).getCoverCount();
		Simulation sim = SimulatorMain.getEngine().getSimulation();
		if (sim instanceof CoverageSimulation) {
			((CoverageSimulation) sim).getCellCoverageReward(coverCount, isThreat);
		}
		SimulatorMain.getStats().updateCellCovered(this.robot);
		this.env.getGridNode(this.robot.getLocation().x, this.robot.getLocation().y).incrementCoverCount();
		if (isThreat) {
			this.env.getRobotById(this.robot.getId()).setBroken(true);
		}
	}


	public void takeActionById(int actionNum) {
		if (actionNum == 0) {
			this.moveRight();
		} else if (actionNum == 1) {
			this.moveUp();
		} else if (actionNum == 2) {
			this.moveLeft();
		} else if (actionNum == 3) {
			this.moveDown();
		} else if (actionNum == 4) {
			this.coverCurrentNode();
		} else {
			this.coverCurrentNode();
		}
	}


	public int getLastActionId() {
		return this.lastActionId;
	}


	/**
	 * Get the reward value from the most recent action (usually useful for MDP-based
	 * coverage algorithms)
	 * 
	 * @return
	 */
	public double getLastReward() {
		return this.lastReward;
	}


	public void reloadSettings() {
		this.ROBOTS_BREAKABLE = SimulatorMain.settings.getBoolean("robots.breakable");
	}
}
