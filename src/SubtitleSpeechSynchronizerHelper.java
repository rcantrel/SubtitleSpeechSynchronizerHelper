import java.io.BufferedReader;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStreamReader;
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

				// print out event
				System.out.format("%s: %s\n", event.kind().name(), child);

				// if directory is created, and watching recursively, then
				// register it and its sub-directories
				if (kind == StandardWatchEventKinds.ENTRY_CREATE || kind == StandardWatchEventKinds.ENTRY_MODIFY) {
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
											String srtString = child.toString();
											File srtFile = new File(srtString);

											String[] videoFiles = child.getParent().toFile().list(new FilenameFilter() {
												@Override
												public boolean accept(File dir, String name) {
													return name.startsWith(srtFile.getName().replace(".en.srt", "")) && !name.toLowerCase().endsWith(".srt");
												}
											});

											if (videoFiles.length == 1) {
												if (OSUtils.getInstance().isWindows()) {
													executeCommand("\"" + WINDOWS_PATH + "subsync-cmd.exe\" --cli sync --sub \"" + srtString + "\" --ref \"" + child.getParent().toString() + "\\" + videoFiles[0] + "\" --out \"" + srtString + "\" --overwrite");
												} else {
													executeCommand("subsync --cli sync --sub " + srtString.replaceAll(" ", "\\\\ ") + " --ref " + child.getParent().toString() + "\\" + videoFiles[0].replaceAll(" ", "\\\\ ") + " --out " + srtString.replaceAll(" ", "\\\\ ") + " --overwrite");
												}
											} else {
												System.out.println(videoFiles.length + " video files found for " + srtString + (videoFiles.length > 0 ? "\n" + Arrays.toString(videoFiles) : ""));
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

	public Optional<String> getExtensionByStringHandling(String filename) {
		return Optional.ofNullable(filename).filter(f -> f.contains(".")).map(f -> f.substring(filename.lastIndexOf(".") + 1));
	}

	public void executeCommand(String command) throws Exception {
		System.out.println("Executing: " + command);
		Process proc = Runtime.getRuntime().exec(command);
		// Read the output
		BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()));

		String line = "";
		while ((line = reader.readLine()) != null) {
			System.out.print(line + "\n");
		}

		proc.waitFor();
		System.out.println("Execution finished");

	}
}
