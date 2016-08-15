package adsim;

import java.awt.GraphicsEnvironment;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import gui.GUIDisplay;

public class ConsoleController {
	private SimulatorEngine engine = null;
	private Thread inputThread = null;
	private Map<String, TerminalCommand> commandList = new HashMap<>();
	private boolean hasQuitInput = false;
	private boolean useEcho = false;


	public ConsoleController() {
		this(null);
	}


	public ConsoleController(SimulatorEngine engine) {
		this.engine = engine;
		this.inputThread = new Thread() {
			@Override
			public void run() {
				runInputLoop();
			}
		};
		this.registerDefaultCommands();
	}


	public void setEngine(SimulatorEngine engine) {
		this.engine = engine;
	}


	public void start() {
		this.inputThread.start();
		if (SimulatorMain.args.USE_AUTOSTART) {
			this.engine.runSimulation();
		}
	}


	private void registerDefaultCommands() {
		this.registerCommand(":quit", new TerminalCommand() {
			@Override
			public void execute(String[] args) {
				ConsoleController.this.engine.pauseSimulation();
				ConsoleController.this.engine.kill();
				ConsoleController.this.hasQuitInput = true;
			}
		});

		this.registerCommand("#", new TerminalCommand() {
			@Override
			public void execute(String[] args) {
				// Do nothing, this command serves as a comment in rc
				// files
			}
		});

		this.registerCommand(":help", new TerminalCommand() {
			@Override
			public void execute(String[] args) {
				System.out.println(getCommandList());
			}
		});

		this.registerCommand(":setecho", new TerminalCommand() {
			@Override
			public void execute(String[] args) {
				if (args.length < 1) {
					return;
				}

				if (args[0].equals("on")) {
					ConsoleController.this.useEcho = true;
				} else if (args[0].equals("off")) {
					ConsoleController.this.useEcho = false;
				}
			}
		});

		this.registerCommand(":pause", new TerminalCommand() {
			@Override
			public void execute(String[] args) {
				ConsoleController.this.engine.pauseSimulation();
			}
		});

		this.registerCommand(":step", new TerminalCommand() {
			@Override
			public void execute(String[] args) {
				ConsoleController.this.engine.stepSimulation();
			}
		});

		this.registerCommand(":run", new TerminalCommand() {
			@Override
			public void execute(String[] args) {
				ConsoleController.this.engine.runSimulation();
			}
		});

		this.registerCommand(":restart", new TerminalCommand() {
			@Override
			public void execute(String[] args) {
				ConsoleController.this.engine.restartSimulation();
			}
		});

		this.registerCommand(":new", new TerminalCommand() {
			@Override
			public void execute(String[] args) {
				ConsoleController.this.engine.newSimulation();
			}
		});

		this.registerCommand(":showstate", new TerminalCommand() {
			@Override
			public void execute(String[] args) {
				System.out.printf("isRunning = %s\n", ConsoleController.this.engine.isRunning() ? "true" : "false");
			}
		});

		this.registerCommand(":set", new TerminalCommand() {
			@Override
			public void execute(String[] args) {
				if (args.length < 2) {
					return;
				}
				SimulatorMain.settings.setAuto(args[0], args[1]);
				ConsoleController.this.engine.getEnv().reloadSettings();
			}
		});

		this.registerCommand(":get", new TerminalCommand() {
			@Override
			public void execute(String[] args) {
				if (args.length < 1) {
					return;
				}
				System.out.print(SimulatorMain.settings.getAsString(args[0]));
			}
		});

		this.registerCommand(":print", new TerminalCommand() {
			@Override
			public void execute(String[] args) {
				if (args.length < 1) {
					return;
				}
				System.out.print(args[0]);
			}
		});

		this.registerCommand(":showsettings", new TerminalCommand() {
			@Override
			public void execute(String[] args) {
				if (0 < args.length && args[0].equals("ascommands")) {
					System.out.println(SimulatorMain.settings.exportToCommandString());
				} else {
					System.out.println(SimulatorMain.settings.exportToString());
				}
			}
		});

		this.registerCommand(":setdisplay", new TerminalCommand() {
			@Override
			public void execute(String[] args) {
				if (args.length < 1) {
					return;
				}
				DisplayAdapter display = null;
				if (args[0].equals("gui")) {
					if (GraphicsEnvironment.isHeadless()) {
						return;
					}
					GUIDisplay gd = GUIDisplay.createInstance(ConsoleController.this.engine);
					gd.setup();
					display = gd;
				} else if (args[0].equals("none")) {
					display = null;
				}
				ConsoleController.this.engine.setDisplay(display);
			}
		});

		this.registerCommand(":runfile", new TerminalCommand() {
			@Override
			public void execute(String[] args) {
				if (args.length < 1) {
					return;
				}
				loadCommandFile(args[0]);
			}
		});


		this.registerCommand(":execAll", new TerminalCommand() {
			@Override
			public void execute(String[] args) {
				for (int i = 0; i < args.length; i++) {
					handleLine(args[i]);
				}
			}
		});

		this.registerCommand(":registerCommand", new TerminalCommand() {
			@Override
			public void execute(String[] args) {
				if (args.length < 2) {
					return;
				}
				final String newCommandName = args[0];
				final String baseCmdToExec = args[1];
				final List<String> baseCmdArgs = splitCmdStr(baseCmdToExec);
				registerCommand(newCommandName, new TerminalCommand() {
					@Override
					public void execute(String[] args) {
						final List<String> newArgs = new ArrayList<>();
						newArgs.addAll(baseCmdArgs);
						newArgs.addAll(Arrays.asList(args));
						String[] cmdArgs = null;
						if (newArgs.size() == 0) {
							return;
						}
						cmdArgs = new String[newArgs.size() - 1];
						cmdArgs = newArgs.subList(1, newArgs.size()).toArray(cmdArgs);
						executeCommand(newArgs.get(0), cmdArgs);
					}
				});
			}
		});

		this.registerCommand(":isRegistered", new TerminalCommand() {
			@Override
			public void execute(String[] args) {
				if (args.length < 1) {
					return;
				}
				if (ConsoleController.this.commandList.containsKey(args[0])) {
					System.out.printf("true\n");
				} else {
					System.out.printf("false\n");
				}
			}
		});

	}


	private void runInputLoop() {
		Scanner inReader = new Scanner(System.in);
		while (!this.hasQuitInput) {
			handleLine(inReader.nextLine());
		}
		inReader.close();
	}


	/**
	 * Parses a line of text into a command, and then executes the command.
	 * 
	 * @param line
	 *                the line of input to parse and execute
	 */
	private void handleLine(String line) {
		List<String> argList = splitCmdStr(line);
		if (argList.size() == 0) {
			return;
		}

		if (this.useEcho) {
			System.out.println(line);
		}

		String command = argList.get(0);

		String[] args = new String[argList.size() - 1];
		for (int i = 1; i < argList.size(); i++) {
			args[i - 1] = argList.get(i);
		}
		executeCommand(command, args);
	}


	private synchronized void executeCommand(String commandName, String[] args) {
		TerminalCommand cmd = this.commandList.get(commandName);
		if (cmd != null) {
			cmd.execute(args);
		} else {
			System.err.println("Invalid command name. Run :help to see a list of commands.");
		}
	}


	public synchronized void registerCommand(String command, TerminalCommand action) {
		this.commandList.put(command, action);
	}


	public String getCommandList() {
		StringBuilder cmdList = new StringBuilder();
		for (String key : this.commandList.keySet()) {
			cmdList.append(key);
			cmdList.append('\n');
		}
		return cmdList.toString();
	}


	private List<String> splitCmdStr(String cmdStr) {
		if (cmdStr == null || cmdStr.length() == 0) {
			return new ArrayList<>();
		}

		List<String> argList = new ArrayList<>();
		StringBuilder curArg = new StringBuilder("");

		int pos = 0;
		boolean inQuote = false;
		boolean hasArg = false;

		while (pos < cmdStr.length()) {
			char curChar = cmdStr.charAt(pos);

			if (curChar == '\\') {

				if ((pos + 1) < cmdStr.length()) {
					curArg.append(escapeChar(cmdStr.charAt(pos + 1)));
				} else {
					// Reached end of string
					curArg.append(curChar);
				}
				hasArg = true;
				pos++;

			} else if (isQuoteChar(curChar)) {

				inQuote = !inQuote;
				hasArg = true;

			} else if (inQuote || (!Character.isWhitespace(curChar))) {

				curArg.append(curChar);
				hasArg = true;

			} else if (Character.isWhitespace(curChar) && (hasArg || 0 < curArg.length())) {

				argList.add(curArg.toString());
				curArg.setLength(0);
				hasArg = false;

			}

			pos++;
		}

		if (hasArg || 0 < curArg.length()) {
			argList.add(curArg.toString());
			curArg.setLength(0);
		}

		return argList;
	}


	private char escapeChar(char c) {
		switch (c) {
		case 'n':
			return '\n';
		case 'r':
			return '\r';
		case 't':
			return '\t';
		case 'b':
			return '\b';
		default:
			return c;
		}
	}


	private boolean isQuoteChar(char c) {
		return (c == '\'' || c == '"');
	}


	public void loadCommandFile(String filename) {
		Scanner fileScanner = new Scanner("");
		try {
			fileScanner = new Scanner(new File(filename));
		} catch (FileNotFoundException e) {
			System.err.printf("The file \"%s\" was not found or could not be opened.\n", filename);
		}

		while (fileScanner.hasNextLine()) {
			handleLine(fileScanner.nextLine());
		}

		fileScanner.close();
	}


	public void runCommand(String cmdStr) {
		if (cmdStr == null || cmdStr.length() == 0) {
			return;
		}
		handleLine(cmdStr);
	}


	public void runCommand_noEcho(String cmdStr) {
		if (cmdStr == null || cmdStr.length() == 0) {
			return;
		}
		boolean echo_tmp = this.useEcho;
		try {
			this.useEcho = false;
			handleLine(cmdStr);
		} finally {
			this.useEcho = echo_tmp;
		}
	}

}