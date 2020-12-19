import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 *
 */

/**
 * @author rcantrel
 *
 */
public class SubtitleSpeechSynchronizerHelper {

	private static final String WINDOWS_PATH = "C:/Program Files/subsync/";
	private WatchService watcher = null;
	private Map<WatchKey, Path> keys = null;
	private List<String> processingSrtFile = new ArrayList<>();
	private static final List<String> VIDEO_EXTENSIONS = Arrays.asList(new String[] { "avi", "divx", "m4v", "mkv", "mp4", "mpg", "wmv" });

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		try {
			String watchFoldersFileStr = getAppDirectory() + "/watch_folders.txt";
			File watchFoldersFile = new File(watchFoldersFileStr);
			System.out.println("Looking for: " + watchFoldersFileStr);

			if (!watchFoldersFile.exists()) {
				watchFoldersFile.createNewFile();
				System.out.println("Created file to define the folders to watch.\n  " + watchFoldersFile.getAbsolutePath() + "\n  Each folder should be defined on each line.");
				System.exit(0);
			} else {
				List<String> foldersToWatch = Files.readAllLines(Paths.get(watchFoldersFile.getAbsolutePath()));
				System.out.println("Found " + foldersToWatch.size() + " folders to watch.");
				for (String folderString : foldersToWatch) {
					if (new File(folderString).exists()) {
						new Thread(new Runnable() {

							@Override
							public void run() {
								try {
									Path folderToWatchPath = Paths.get(folderString);
									new SubtitleSpeechSynchronizerHelper(folderToWatchPath).processEvents();
								} catch (IOException e) {
									e.printStackTrace();
								}
							}
						}).start();

					} else {
						System.out.println(folderString + " does not exist.");
					}
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Creates a WatchService and registers the given directory
	 */
	SubtitleSpeechSynchronizerHelper(Path dir) throws IOException {
		this.watcher = FileSystems.getDefault().newWatchService();
		this.keys = new HashMap<>();

		System.out.println("Now watching " + dir);
		walkAndRegisterDirectories(dir);
	}

	/**
	 * Creates the application data directory if it does not exist and returns
	 * the path
	 * 
	 * @return path to the application data directory
	 */
	private static String getAppDirectory() {
		File appFile = new File(OSUtils.getInstance().getWorkingDirectory() + "/SubtitleSpeechSynchronizerHelper");
		if (!appFile.exists()) {
			appFile.mkdirs();
		}
		return appFile.getAbsolutePath();
	}

	/**
	 * Register the given directory with the WatchService; This function will be
	 * called by FileVisitor
	 */
	private void registerDirectory(Path dir) throws IOException {
		WatchKey key = dir.register(watcher, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY);
		keys.put(key, dir);
	}

	/**
	 * Register the given directory, and all its sub-directories, with the
	 * WatchService.
	 */
	private void walkAndRegisterDirectories(final Path start) throws IOException {
		// register directory and sub-directories
		Files.walkFileTree(start, new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
				registerDirectory(dir);
				return FileVisitResult.CONTINUE;
			}
		});
	}

	/**
	 * Process all events for keys queued to the watcher
	 */
	void processEvents() {
		for (;;) {

			// wait for key to be signalled
			WatchKey key;
			try {
				key = watcher.take();
			} catch (InterruptedException x) {
				return;
			}

			Path dir = keys.get(key);
			if (dir == null) {
				System.err.println("WatchKey not recognized!!");
				continue;
			}

			for (WatchEvent<?> event : key.pollEvents()) {
				@SuppressWarnings("rawtypes")
				WatchEvent.Kind kind = event.kind();

				// Context for directory entry event is the file name of entry
				@SuppressWarnings("unchecked")
				Path name = ((WatchEvent<Path>) event).context();
				Path child = dir.resolve(name);

				String filePath = child.toString();
				// if directory is created, and watching recursively, then
				// register it and its sub-directories
				if ((kind == StandardWatchEventKinds.ENTRY_CREATE || kind == StandardWatchEventKinds.ENTRY_MODIFY) && !processingSrtFile.contains(filePath)) {
					// print out event
					try {
						if (Files.isDirectory(child)) {
							walkAndRegisterDirectories(child);
							System.out.println("Now watching " + child.toString());
						} else {
							Optional<String> extensionOption = getExtensionByStringHandling(child.toString());
							if (extensionOption.isPresent() && "srt".equals(extensionOption.get())) {
								new Thread(new Runnable() {

									@Override
									public void run() {
										try {
											Thread.sleep(5000);
											File srtFile = new File(filePath);

											System.out.println("Looking for matching video files.");
											String[] videoFiles = child.getParent().toFile().list(new FilenameFilter() {
												@Override
												public boolean accept(File dir, String name) {
													Optional<String> extensionOption = getExtensionByStringHandling(name);
													return (name.startsWith(srtFile.getName().replace(".srt", "")) || name.startsWith(srtFile.getName().replace(".en.srt", ""))) && extensionOption.isPresent() && VIDEO_EXTENSIONS.contains(extensionOption.get());
												}
											});

											if (videoFiles.length == 1 && !processingSrtFile.contains(filePath)) {
												System.out.format("%s: %s\n", event.kind().name(), child);
												processingSrtFile.add(filePath);
												if (OSUtils.getInstance().isWindows()) {
													executeCommand("\"" + WINDOWS_PATH + "subsync-cmd.exe\" --cli sync --sub \"" + filePath + "\" --ref \"" + child.getParent().toString() + "\\" + videoFiles[0] + "\" --out \"" + filePath + "\" --overwrite");
												} else {
													String[] subsyncCmds = {"subsync", "--cli", "sync", "--sub-file", filePath, "--ref", (child.getParent().toString() + "/" + videoFiles[0]), "--out", filePath, "--overwrite"};
													executeCommand(subsyncCmds);
													//executeCommand("subsync", "--cli sync --sub-file \"" + filePath + "\" --ref \"" + child.getParent().toString() + "/" + videoFiles[0] + "\" --out \"" + filePath + "\" --overwrite");
													//executeCommand("subsync --cli sync --sub " + filePath.replaceAll(" ", "\\\\ ") + " --ref " + (child.getParent().toString() + "/" + videoFiles[0]).replaceAll(" ", "\\\\ ") + " --out " + filePath.replaceAll(" ", "\\\\ ") + " --overwrite");
													//executeCommand("subnuker", "--regex \"" + filePath + "\" --yes");
													
													String[] subnukerCmds = {"subnuker", "--regex", filePath, "--yes"};
													executeCommand(subnukerCmds);
												}
												processingSrtFile.remove(filePath);
											} else if (videoFiles.length != 1) {
												System.out.println(videoFiles.length + " video files found for " + filePath + (videoFiles.length > 0 ? "\n" + Arrays.toString(videoFiles) : ""));
											}
										} catch (Exception e) {
											e.printStackTrace();
										}
									}
								}).start();
							}
						}
					} catch (IOException x) {
						x.printStackTrace();
					}
				}
			}

			// reset key and remove from set if directory no longer accessible
			boolean valid = key.reset();
			if (!valid) {
				keys.remove(key);

				// all directories are inaccessible
				if (keys.isEmpty()) {
					break;
				}
			}
		}
	}

	/**
	 * Returns the extension is a Optional object
	 * 
	 * @param filename
	 * @return
	 */
	public Optional<String> getExtensionByStringHandling(String filename) {
		return Optional.ofNullable(filename).filter(f -> f.contains(".")).map(f -> f.substring(filename.lastIndexOf(".") + 1));
	}

	/**
	 * Executes a system command
	 * 
	 * @param command
	 * @throws Exception
	 */
	public void executeCommand(String command) {
		System.out.println("Executing: " + command);
		try {
			ProcessBuilder pb = new ProcessBuilder(command);
			Process proc = pb.start();

			// Process proc = Runtime.getRuntime().exec(command);
			StreamGobbler errorGobbler = new StreamGobbler(proc.getErrorStream(), "ERROR");

			// any output?
			StreamGobbler outputGobbler = new StreamGobbler(proc.getInputStream(), "OUTPUT");

			// start gobblers
			outputGobbler.start();
			errorGobbler.start();

			proc.waitFor();

			System.out.println("Execution finished");
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("Execution failed.");
		}
	}

	/**
	 * Executes a system command
	 * 
	 * @param command
	 * @throws Exception
	 */
	public void executeCommand(String[] command) {
		
		try {
			ProcessBuilder pb = new ProcessBuilder(command);
			System.out.println("Executing: " + getRunnableCommand(pb));
			Process proc = pb.start();

			// Process proc = Runtime.getRuntime().exec(command);
			StreamGobbler errorGobbler = new StreamGobbler(proc.getErrorStream(), "ERROR");

			// any output?
			StreamGobbler outputGobbler = new StreamGobbler(proc.getInputStream(), "OUTPUT");

			// start gobblers
			outputGobbler.start();
			errorGobbler.start();

			proc.waitFor();

			System.out.println("Execution finished");
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("Execution failed.");
		}
	}

	private String getRunnableCommand(ProcessBuilder processBuilder) {
		List<String> commandsList = processBuilder.command();
		StringBuilder runnableCommandBuilder = new StringBuilder();
		int commandIndex = 0;
		for (String command : commandsList) {
			if (command.contains(" ")) {
				runnableCommandBuilder.append("\"");
			}
			runnableCommandBuilder.append(command);

			if (command.contains(" ")) {
				runnableCommandBuilder.append("\"");
			}

			if (commandIndex != commandsList.size() - 1) {
				runnableCommandBuilder.append(" ");
			}

			commandIndex++;
		}
		return runnableCommandBuilder.toString();
	}
}
