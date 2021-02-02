import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
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
	private static File logFile = new File(getAppDirectory() + "/subtitle_sync.log");
	private static Path successfulProcessedSubtitlesPath = Paths.get(getAppDirectory() + "/successful_processed_subtitles.txt");
	private static Path unsuccessfulProcessedSubtitlesPath = Paths.get(getAppDirectory() + "/unsuccessful_processed_subtitles.txt");
	private static Path missingVideoFilePath = Paths.get(getAppDirectory() + "/missing_video_file.txt");
	private static Path toManyVideosPath = Paths.get(getAppDirectory() + "/to_many_video_files.txt");
	private static Path fixedFilePath = Paths.get(getAppDirectory() + "/fixed_files.txt");
	private static Map<String, String> subtitleFilesToProcess = new HashMap<>();
	private static List<String> processingSrtFile = new ArrayList<>();
	private static List<String> processingFailedSrtFile = new ArrayList<>();
	private static List<String> successfulProcessedSrtFiles = new ArrayList<>();
	private static List<String> unsuccessfulProcessedSrtFiles = new ArrayList<>();
	private static List<String> missingVideoFileSrtFiles = new ArrayList<>();
	private static List<String> toManyVideosSrtFiles = new ArrayList<>();
	private static List<String> processingVideoFile = new ArrayList<>();
	private static final List<String> VIDEO_EXTENSIONS = Arrays.asList(new String[] { "avi", "divx", "m2ts", "m4v", "mkv", "mov", "mp4", "mpg", "wmv" });
	private int pauseTime = 180000;
	private static SubtitleSpeechSynchronizerHelper helper;
	private static final String DASHES = "----------------------------------------";

	private Map<String, Integer> processTime = new HashMap<>();
	private Map<String, Integer> processFailedTime = new HashMap<>();
	private Map<String, Integer> processDownloadTime = new HashMap<>();
	private boolean processingFiledFiles = false;

	/**
	 * @param args
	 */
	public static void main(String[] args) {

		try {
			if (!logFile.exists()) {
				logFile.createNewFile();
			}
			if (!successfulProcessedSubtitlesPath.toFile().exists()) {
				successfulProcessedSubtitlesPath.toFile().createNewFile();
			}
			if (!unsuccessfulProcessedSubtitlesPath.toFile().exists()) {
				unsuccessfulProcessedSubtitlesPath.toFile().createNewFile();
			}
			if (!missingVideoFilePath.toFile().exists()) {
				missingVideoFilePath.toFile().createNewFile();
			}
			if (!toManyVideosPath.toFile().exists()) {
				toManyVideosPath.toFile().createNewFile();
			}
			if (!fixedFilePath.toFile().exists()) {
				fixedFilePath.toFile().createNewFile();
			}

			FileOutputStream logFileOutStream = new FileOutputStream(logFile);
			SuperPrintStream tee = new SuperPrintStream(logFileOutStream, System.out);
			System.setOut(tee);

			String watchFoldersFileStr = getAppDirectory() + "/watch_folders.txt";
			File watchFoldersFile = new File(watchFoldersFileStr);
			System.out.println("Looking for: " + watchFoldersFileStr);

			successfulProcessedSrtFiles = Files.readAllLines(successfulProcessedSubtitlesPath);
			unsuccessfulProcessedSrtFiles = Files.readAllLines(unsuccessfulProcessedSubtitlesPath);
			missingVideoFileSrtFiles = Files.readAllLines(missingVideoFilePath);
			toManyVideosSrtFiles = Files.readAllLines(toManyVideosPath);

			removeMissingSubtitles(successfulProcessedSrtFiles, successfulProcessedSubtitlesPath);
			removeMissingSubtitles(unsuccessfulProcessedSrtFiles, unsuccessfulProcessedSubtitlesPath);
			removeMissingSubtitles(missingVideoFileSrtFiles, missingVideoFilePath);
			removeMissingSubtitles(toManyVideosSrtFiles, toManyVideosPath);

			if (!watchFoldersFile.exists()) {
				watchFoldersFile.createNewFile();
				System.out.println("Created file to define the folders to watch.\n  " + watchFoldersFile.getAbsolutePath() + "\n  Each folder should be defined on each line.");
				System.exit(0);
			} else {
				helper = new SubtitleSpeechSynchronizerHelper();
				helper.processSubtitleFiles();

				List<String> foldersToWatch = Files.readAllLines(Paths.get(watchFoldersFile.getAbsolutePath()));

				System.out.println("Found " + foldersToWatch.size() + " folders in watch file.");
				for (String folderString : foldersToWatch) {
					new Thread(new Runnable() {
						@Override
						public void run() {
							if (new File(folderString).exists()) {
								try {
									Path folderToWatchPath = Paths.get(folderString);
									new SubtitleSpeechSynchronizerWatcher(helper, folderToWatchPath, args).processEvents();
								} catch (IOException e) {
									e.printStackTrace();
								}
							} else {
								System.out.println(folderString + " does not exist.");
							}
						}
					}).start();
				}

				if (args.length > 0 && "existing".equals(args[0])) {
					Thread.sleep(2000);
					System.out.println("Going to process existing subtitles");
					List<File> subTitleFiles = new ArrayList<>();
					for (String folderString : foldersToWatch) {
						if (new File(folderString).exists()) {
							System.out.println("  Looking for subtitle files in " + folderString);
							List<File> newSubTitleFiles = new ArrayList<>();
							findSubTitleFiles(folderString, newSubTitleFiles);
							System.out.println("    Found " + newSubTitleFiles.size() + " subtitle files in " + folderString);
							subTitleFiles.addAll(newSubTitleFiles);
						} 
					}

					System.out.println("Removing previously processed and failed subtitles");
					int numberOfSubtitles = subTitleFiles.size();
					Iterator<File> i = subTitleFiles.iterator();
					while (i.hasNext()) {
						File foundSubTitleFile = i.next();
						if (successfulProcessedSrtFiles.contains(foundSubTitleFile.getAbsolutePath())) {
							i.remove();
						}
						if (unsuccessfulProcessedSrtFiles.contains(foundSubTitleFile.getAbsolutePath())) {
							i.remove();
						}
						if (missingVideoFileSrtFiles.contains(foundSubTitleFile.getAbsolutePath())) {
							i.remove();
						}
						if (toManyVideosSrtFiles.contains(foundSubTitleFile.getAbsolutePath())) {
							i.remove();
						}
					}
					System.out.println("Removed " + (numberOfSubtitles - subTitleFiles.size()) + " subtitles");
					System.out.println("Sorting " + subTitleFiles.size() + " subtitles files by most recently added");
					Collections.sort(subTitleFiles, Comparator.comparingLong(File::lastModified).reversed());

					if (args.length == 2 && "list".equals(args[1])) {
						for (File subtitleFile : subTitleFiles) {
							System.out.println(subtitleFile.getAbsolutePath());
						}
						System.exit(0);
					} else {
						helper.processExistingSubtitles(subTitleFiles);
					}
				} else if (args.length >= 1 && "find".equals(args[0])) {
					Thread.sleep(2000);
					System.out.println("Going to find video files with missing subtitles");
					List<File> videoFiles = new ArrayList<>();
					for (String folderString : foldersToWatch) {
						if (new File(folderString).exists()) {
							System.out.println("  Looking for video files in " + folderString);
							List<File> newVideoFiles = new ArrayList<>();

							findVideoFilesWithoutSubtitleFile(folderString, newVideoFiles);
							System.out.println("    Found " + newVideoFiles.size() + " video files in " + folderString + " that does not have a subtitle file.");
							videoFiles.addAll(newVideoFiles);
						}
					}

					System.out.println("  Sorting " + videoFiles.size() + " video files by most recently added");
					Collections.sort(videoFiles, Comparator.comparingLong(File::lastModified).reversed());
					System.out.println("  Found " + videoFiles.size() + " video files that does not have a subtitle file.");
					
					if (args.length == 1 && "find".equals(args[0])) {
						Thread.sleep(10000);
						for (File videoFile : videoFiles) {
							System.out.println(videoFile.getAbsolutePath());
						}
					} else if (args.length >= 2 && "download".equals(args[1])) {
						helper.processSubtitlesToDownload(videoFiles, (args.length == 3 && "process".equals(args[2])));				
					}

				} else if (args.length == 1 && "fix".equals(args[0])) {
					Thread.sleep(2000);
					System.out.println("Going to attempt to download failed subtitles");

					helper.processFailedSubtitles(new ArrayList<String>(unsuccessfulProcessedSrtFiles));
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Creates a WatchService and registers the given directory
	 * 
	 * @param args
	 */
	SubtitleSpeechSynchronizerHelper() throws IOException {

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

	void processExistingSubtitles(List<File> subTitleFiles) {
		System.out.println("Adding " + subTitleFiles.size() + " subtitle files to process");
		for (File subTitleFile : subTitleFiles) {
			try {
				helper.addSubtitleFileToProcess(subTitleFile.getAbsolutePath(), subTitleFile.getParent(), false);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		System.out.println("  Added " + subTitleFiles.size() + " subtitle files to process");
	}

	void processFailedSubtitles(List<String> failedSubTitleFiles) {
		processingFiledFiles = true;
		System.out.println("Starting to process " + failedSubTitleFiles.size() + " failed subtitle files.");

		appendToFile(fixedFilePath, DASHES + getCurrentDateTimeString() + DASHES);
		int count = 1;
		for (String subTitleFileStr : failedSubTitleFiles) {
			File subTitleFile = new File(subTitleFileStr);
			if (subTitleFile.exists()) {
				try {
					long startTime = System.currentTimeMillis();
					System.out.println("Processing " + count + "/" + failedSubTitleFiles.size() + " failed subtitle files.");
					processFailedSubtitleFile(subTitleFile.getAbsolutePath(), subTitleFile.getParent());
					Thread.sleep(5000);
					while (processingFailedSrtFile.size() > 0 || processingSrtFile.size() > 0) {
						Thread.sleep(1000);
					}
					long endTime = System.currentTimeMillis();
					int executionTime = (int) ((endTime - startTime) / 1000);
					processFailedTime.put(subTitleFile.getAbsolutePath(), executionTime);

					int totalProcessTime = 0;
					for (int srtProcessTime : processFailedTime.values()) {
						totalProcessTime += srtProcessTime;
					}

					int averageExecutionTime = totalProcessTime / processFailedTime.values().size();
					int remainingTime = ((failedSubTitleFiles.size() - count) * averageExecutionTime) + ((failedSubTitleFiles.size() - count - ((failedSubTitleFiles.size() - count) > 1 ? -1 : 0)) * 10);
					Calendar calendar = Calendar.getInstance();
					calendar.add(Calendar.SECOND, remainingTime);

					System.out.println("Finished processing " + count + " of " + failedSubTitleFiles.size() + ".  Execution time: " + formatSeconds(executionTime));

					if (count != failedSubTitleFiles.size()) {
						System.out.println("Estimated time remaining: " + formatSeconds(remainingTime) + " (" + new SimpleDateFormat("MM-dd-yyyy hh:mm aa").format(calendar.getTime()) + ")");
						int countDown = pauseTime;
						while (countDown >= 0) {
							clearPrint("Waiting " + (countDown > 0 ? countDown / 1000 : countDown) + " seconds to start the next.");
							countDown = countDown - 1000;

							Thread.sleep(1000);
						}
					} else {
						System.out.println("Processing completed at " + getCurrentDateTimeString());
					}
					System.out.print("\n");
					clearPrintSize = -1;
					count++;
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
		System.out.println("Processed " + failedSubTitleFiles.size() + " failed subtitle files.");
		appendToFile(fixedFilePath, DASHES + "END " + getCurrentDateTimeString() + DASHES + "\n");
		processingFiledFiles = false;
	}


	void processSubtitlesToDownload(List<File> videoFiles, boolean process) {
		try {
			System.out.println("Beginning to download subtitles.");
			processingFiledFiles = true;
			int count = 1;
			for (File videoFile : videoFiles) {
				long startTime = System.currentTimeMillis();

				processDownloadSubtitleFile(videoFile, process);
				while (processingVideoFile.size() > 0 || processingSrtFile.size() > 0) {
					Thread.sleep(1000);
				}

				long endTime = System.currentTimeMillis();
				int executionTime = (int) ((endTime - startTime) / 1000);
				processDownloadTime.put(videoFile.getAbsolutePath(), executionTime);

				int totalProcessTime = 0;
				for (int srtProcessTime : processDownloadTime.values()) {
					totalProcessTime += srtProcessTime;
				}

				int averageExecutionTime = totalProcessTime / processDownloadTime.values().size();
				int remainingTime = ((videoFiles.size() - count) * averageExecutionTime) + ((videoFiles.size() - count - ((videoFiles.size() - count) > 1 ? -1 : 0)) * (process ? 120 : 5));
				Calendar calendar = Calendar.getInstance();
				calendar.add(Calendar.SECOND, remainingTime);

				System.out.println("Finished processing " + count + " of " + videoFiles.size() + ".  Execution time: " + formatSeconds(executionTime));

				if (count != videoFiles.size()) {
					System.out.println("Estimated time remaining: " + formatSeconds(remainingTime) + " (" + new SimpleDateFormat("MM-dd-yyyy hh:mm aa").format(calendar.getTime()) + ")");
					int countDown = pauseTime;
					while (countDown >= 0) {
						clearPrint("Waiting " + (countDown > 0 ? countDown / 1000 : countDown) + " seconds to start the next.");
						countDown = countDown - 1000;

						Thread.sleep(1000);
					}
				} else {
					System.out.println("Processing completed at " + getCurrentDateTimeString());
				}
				System.out.print("\n");
				clearPrintSize = -1;
				count++;
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			processingFiledFiles = false;
		}
	}

	public void addSubtitleFileToProcess(String srtPath, String parentPath, boolean announce) {
		if (!subtitleFilesToProcess.containsKey(srtPath)) {
			subtitleFilesToProcess.put(srtPath, parentPath);
			if (announce) {
				System.out.println("Added subtitle file to process: " + srtPath);
			}
		}
	}

	public void removeSubtitleFileToProcess(String srtPath) {
		subtitleFilesToProcess.remove(srtPath);
	}

	public void processSubtitleFiles() {
		new Thread(new Runnable() {

			@Override
			public void run() {
				while (true) {
					try {						
						while (processingFiledFiles) {
							Thread.sleep(1000);
						}
						if (!subtitleFilesToProcess.isEmpty()) {
							System.out.println("Processing " + subtitleFilesToProcess.size() + " subtitle files.");

							Map<String, String> subtitleFilesToProcessClone = new HashMap<>();
							Iterator<Map.Entry<String, String>> subtitleFilesToProcessItr = subtitleFilesToProcess.entrySet().iterator();
							while (subtitleFilesToProcessItr.hasNext()) {
								Map.Entry<String, String> entry = subtitleFilesToProcessItr.next();
								if (!successfulProcessedSrtFiles.contains(entry.getKey()) && !unsuccessfulProcessedSrtFiles.contains(entry.getKey()) && !missingVideoFileSrtFiles.contains(entry.getKey()) && !toManyVideosSrtFiles.contains(entry.getKey())) {
									subtitleFilesToProcessClone.put(entry.getKey(), entry.getValue());
								} else {
									subtitleFilesToProcessItr.remove();
								}
							}
							
							if(subtitleFilesToProcessClone.isEmpty()) {
								System.out.println("No subtitles to process.");
							}

							int count = 1;
							Iterator<Map.Entry<String, String>> itr = subtitleFilesToProcessClone.entrySet().iterator();
							while (itr.hasNext()) {
								long startTime = System.currentTimeMillis();
								System.out.println("Starting to process subtitle file " + count + " of " + subtitleFilesToProcessClone.size());
								Map.Entry<String, String> subTitleEntry = itr.next();
								String srtPath = subTitleEntry.getKey();
								String parentPath = subTitleEntry.getValue();

								processSubtitleFile(srtPath, parentPath);
								Thread.sleep(5000);

								if (processingSrtFile.size() > 0) {
									while (processingSrtFile.size() > 0) {
										Thread.sleep(1000);
									}
									long endTime = System.currentTimeMillis();
									int executionTime = (int) ((endTime - startTime) / 1000);
									processTime.put(srtPath, executionTime);

									int totalProcessTime = 0;
									for (int srtProcessTime : processTime.values()) {
										totalProcessTime += srtProcessTime;
									}

									int averageExecutionTime = totalProcessTime / processTime.values().size();
									int remainingTime = ((subtitleFilesToProcessClone.size() - count) * averageExecutionTime) + ((subtitleFilesToProcessClone.size() - count - ((subtitleFilesToProcessClone.size() - count) > 1 ? -1 : 0)) * 180);
									Calendar calendar = Calendar.getInstance(); 
									calendar.add(Calendar.SECOND, remainingTime);

									System.out.println("Finished processing " + count + " of " + subtitleFilesToProcessClone.size() + ".  Execution time: " + formatSeconds(executionTime));
									if (itr.hasNext()) {
										System.out.println("Estimated time remaining: " + formatSeconds(remainingTime) + " (" + new SimpleDateFormat("MM-dd-yyyy hh:mm aa").format(calendar.getTime()) + ")");
										int countDown = pauseTime;
										while (countDown >= 0) {
											clearPrint("Waiting " + (countDown > 0 ? countDown / 1000 : countDown) + " seconds to start the next.");
											countDown = countDown - 1000;

											Thread.sleep(1000);
										}
										System.out.print("\n");
									} else if (subtitleFilesToProcessClone.size() > 1) {
										System.out.println("Batch processing completed at " + getCurrentDateTimeString() + ". Processed " + subtitleFilesToProcessClone.size() + " subtitle files.");
									} else {
										System.out.println("Processing completed at " + getCurrentDateTimeString());
									}
									clearPrintSize = -1;
								} else {
									Thread.sleep(5000);
								}
								count++;
							}
						} else {
							Thread.sleep(10000);
						}
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}
		}).start();
	}

	public void processSubtitleFile(String srtPath, String parentPath) {
		new Thread(new Runnable() {

			@Override
			public void run() {
				try {
					processingSrtFile.add(srtPath);
					File srtFile = new File(srtPath);
					File parentFile = new File(parentPath);

					System.out.println("Looking for matching video file");
					String[] videoFiles = parentFile.list(new FilenameFilter() {
						@Override
						public boolean accept(File dir, String name) {
							Optional<String> extensionOption = getExtensionByStringHandling(name);
							return (name.startsWith(srtFile.getName().replace(".srt", "")) || name.startsWith(srtFile.getName().replace(".en.srt", "")) || name.startsWith(srtFile.getName().replace(".eng.srt", ""))) && !name.contains("-trailer.") && !name.contains(".sample.") && extensionOption.isPresent() && VIDEO_EXTENSIONS.contains(extensionOption.get());
						}
					});

					pauseTime = 180000;
					if (videoFiles.length == 1) {
						BasicFileAttributes srtAttributes = Files.readAttributes(srtFile.toPath(), BasicFileAttributes.class);
						System.out.println("Processing: " + srtPath + "\n            (Created: " + formatDateTime(srtAttributes.creationTime()) + ") (Last Modified: " + formatDateTime(srtAttributes.lastModifiedTime()) + ")");

						boolean success = false;
						if (OSUtils.getInstance().isWindows()) {
							success = executeCommand("\"" + WINDOWS_PATH + "subsync-cmd.exe\" --cli sync --sub \"" + srtPath + "\" --ref \"" + parentPath + "\\" + videoFiles[0] + "\" --out \"" + srtPath + "\" --ref-lang eng --sub-stream-by-lang en --overwrite", srtFile.getName());
						} else {
							List<String> subsyncCmds = Arrays.asList("subsync", "--cli", "sync", "--sub-file", srtPath, "--ref", (parentPath + "/" + videoFiles[0]), "--ref-lang", "eng", "--out", srtPath, "--overwrite");
							success = executeCommand(subsyncCmds, srtFile.getName());

							List<String> subnukerCmds = Arrays.asList("subnuker", "--regex", srtPath, "--yes");
							executeCommand(subnukerCmds, srtFile.getName());
						}

						if (success) {
							appendToFile(successfulProcessedSubtitlesPath, srtPath);
							successfulProcessedSrtFiles.add(srtPath);
						} else {
							appendToFile(unsuccessfulProcessedSubtitlesPath, srtPath);
							unsuccessfulProcessedSrtFiles.add(srtPath);
						}
					} else if (videoFiles.length != 1) {
						System.out.println(videoFiles.length + " video files found for " + srtPath + (videoFiles.length > 0 ? "\n" + Arrays.toString(videoFiles) : ""));
						pauseTime = 0;
						if (videoFiles.length == 0) {
							appendToFile(missingVideoFilePath, srtPath);
							missingVideoFileSrtFiles.add(srtPath);
						} else if (videoFiles.length > 1) {
							appendToFile(toManyVideosPath, srtPath);
							toManyVideosSrtFiles.add(srtPath);
						}
					}
					System.out.println(DASHES + DASHES + "\n");

				} catch (Exception e) {
					e.printStackTrace();
					processingSrtFile.remove(srtPath);
				} finally {
					processingSrtFile.remove(srtPath);
					removeSubtitleFileToProcess(srtPath);
				}
			}
		}).start();
	}

	public void processFailedSubtitleFile(String srtPath, String parentPath) {
		new Thread(new Runnable() {

			@Override
			public void run() {
				try {
					File srtFile = new File(srtPath);
					File parentFile = new File(parentPath);

					System.out.println("Looking for video file for: " + srtPath);
					String[] videoFiles = parentFile.list(new FilenameFilter() {
						@Override
						public boolean accept(File dir, String name) {
							Optional<String> extensionOption = getExtensionByStringHandling(name);
							return (name.startsWith(srtFile.getName().replace(".srt", "")) || name.startsWith(srtFile.getName().replace(".en.srt", "")) || name.startsWith(srtFile.getName().replace(".eng.srt", ""))) && !name.contains("-trailer.") && !name.contains(".sample.") && extensionOption.isPresent() && VIDEO_EXTENSIONS.contains(extensionOption.get());
						}
					});

					if (videoFiles.length == 1 && !processingSrtFile.contains(srtPath)) {
						processingFailedSrtFile.add(srtPath);
						boolean success = false;
						File backupFile = new File(srtPath + "_bak");
						srtFile.renameTo(backupFile);
						List<String> subsyncCmds = Arrays.asList("subliminal", "--opensubtitles", "rcantrel", "Dragons7", "download", "-l", "en", (parentPath + "/" + videoFiles[0]));
						success = executeCommand(subsyncCmds, srtFile.getName());

						if (!success || !srtFile.exists()) {
							if (!srtPath.contains(".en.srt") && !(new File(srtPath.replace(".srt", ".en.srt")).exists())) {
								backupFile.renameTo(srtFile);
								processingFailedSrtFile.remove(srtPath);
								pauseTime = 10000;
							} else {
								if (!backupFile.delete()) {
									System.out.println(backupFile.getAbsolutePath() + " not deleted");
								}
								removeLineFromFile(unsuccessfulProcessedSubtitlesPath, srtPath);
								unsuccessfulProcessedSrtFiles.remove(srtPath);
								processingFailedSrtFile.remove(srtPath);
								Thread.sleep(10000);
							}
						} else if (srtFile.exists()) {
							if (!backupFile.delete()) {
								System.out.println(backupFile.getAbsolutePath() + " not deleted");
							}
							removeLineFromFile(unsuccessfulProcessedSubtitlesPath, srtPath);
							unsuccessfulProcessedSrtFiles.remove(srtPath);
							helper.addSubtitleFileToProcess(srtPath, parentPath, true);
							pauseTime = 10000;
							processingFailedSrtFile.remove(srtPath);
							appendToFile(fixedFilePath, srtPath);
						}
						System.out.println(DASHES + DASHES + "\n");
					}
				} catch (Exception e) {
					e.printStackTrace();
					processingFailedSrtFile.remove(srtPath);
				}
			}
		}).start();
	}
	
	public void processDownloadSubtitleFile(File videoFile, boolean process) {
		String videoFileStr = videoFile.getAbsolutePath();
		processingVideoFile.add(videoFileStr);
		new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					if (videoFile.exists()) {
						BasicFileAttributes srtAttributes = Files.readAttributes(videoFile.toPath(), BasicFileAttributes.class);
						System.out.println("Downloading subtitle file for: " + videoFileStr + "\n            (Created: " + formatDateTime(srtAttributes.creationTime()) + ") (Last Modified: " + formatDateTime(srtAttributes.lastModifiedTime()) + ")");
						Optional<String> extensionOption = getExtensionByStringHandling(videoFileStr);
						File subTitleFile = new File(videoFileStr.replaceAll("."+extensionOption.get(), ".en.srt"));
						
						boolean success = false;
						List<String> subsyncCmds = Arrays.asList("subliminal", "--opensubtitles", "rcantrel", "Dragons7", "download", "-l", "en", videoFileStr);
						success = executeCommand(subsyncCmds, subTitleFile.getName());

						if (!success || !subTitleFile.exists()) {
							System.out.println("Failed to download subtitle file for: " + videoFileStr);
							pauseTime = 5000;
						} else if(process) {
							processSubtitleFile(subTitleFile.getAbsolutePath(), subTitleFile.getParent());
							Thread.sleep(2000);
							while (processingSrtFile.size() > 0) {
								Thread.sleep(1000);
							}
						} else {
							helper.addSubtitleFileToProcess(subTitleFile.getAbsolutePath(), subTitleFile.getParent(), true);
							pauseTime = 5000;
						}
						System.out.println(DASHES + DASHES + "\n");
					}
				} catch (Exception e) {
					e.printStackTrace();
				} finally {
					processingVideoFile.remove(videoFile.getAbsolutePath());
				}
			}
		}).start();
	}

	/**
	 * Returns the extension is a Optional object
	 * 
	 * @param filename
	 * @return
	 */
	public static Optional<String> getExtensionByStringHandling(String filename) {
		return Optional.ofNullable(filename).filter(f -> f.contains(".")).map(f -> f.substring(filename.lastIndexOf(".") + 1));
	}

	/**
	 * Executes a system command
	 * 
	 * @param command
	 * @throws Exception
	 */
	public boolean executeCommand(String command, String outputPrefix) {
		System.out.println("Executing: " + command);
		try {
			ProcessBuilder pb = new ProcessBuilder(command);
			Process proc = pb.start();

			// Process proc = Runtime.getRuntime().exec(command);
			StreamGobbler errorGobbler = new StreamGobbler(proc.getErrorStream(), outputPrefix + " ERROR");

			// any output?
			StreamGobbler outputGobbler = new StreamGobbler(proc.getInputStream(), outputPrefix + " OUTPUT");

			// start gobblers
			outputGobbler.start();
			errorGobbler.start();

			proc.waitFor();
			List<String> outputList = outputGobbler.getOutputList();

			if (outputList.contains("[-] couldn't synchronize!")) {
				System.out.println("  Execution finished -- unsuccessful");
				return false;
			}

			System.out.println("  Execution finished -- successful");
			return true;
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("  Execution failed.");
			return false;
		}
	}

	/**
	 * Executes a system command
	 * 
	 * @param command
	 * @throws Exception
	 */
	public boolean executeCommand(List<String> command, String outputPrefix) {

		try {
			ProcessBuilder pb = new ProcessBuilder(command);
			System.out.println("Executing: " + getRunnableCommand(pb));
			Process proc = pb.start();

			// Process proc = Runtime.getRuntime().exec(command);
			StreamGobbler errorGobbler = new StreamGobbler(proc.getErrorStream(), outputPrefix + " ERROR");

			// any output?
			StreamGobbler outputGobbler = new StreamGobbler(proc.getInputStream(), outputPrefix + " OUTPUT");

			// start gobblers
			outputGobbler.start();
			errorGobbler.start();

			proc.waitFor();

			List<String> outputList = outputGobbler.getOutputList();

			if (outputList.contains("[-] couldn't synchronize!") || outputList.contains("Downloaded 0 subtitle")) {
				System.out.println("  Execution finished -- unsuccessful");
				return false;
			}

			if (outputList.contains("Downloaded 1 subtitle")) {
				System.out.println("  Execution finished -- successful");
				return true;
			}

			System.out.println("  Execution finished -- successful");
			return true;
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("  Execution failed.");
			return false;
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

	public static void findSubTitleFiles(String directoryName, List<File> files) {
		File directory = new File(directoryName);

		// Get all files from a directory.
		List<File> fList = Arrays.asList(directory.listFiles());
		if (fList != null) {
			for (File file : fList) {
				if (file.isFile()) {
					Optional<String> extensionOption = getExtensionByStringHandling(file.getAbsolutePath());
					if (extensionOption.isPresent() && "srt".equals(extensionOption.get())) {
						files.add(file);
					}
				} else if (file.isDirectory()) {
					findSubTitleFiles(file.getAbsolutePath(), files);
				}
			}
		}
	}

	public static void findVideoFilesWithoutSubtitleFile(String directoryName, List<File> files) {
		File directory = new File(directoryName);

		// Get all files from a directory.
		List<File> fList = Arrays.asList(directory.listFiles());
		if (fList != null) {
			for (File file : fList) {
				if (file.isFile()) {
					Optional<String> extensionOption = getExtensionByStringHandling(file.getAbsolutePath());
					if (extensionOption.isPresent() && VIDEO_EXTENSIONS.contains(extensionOption.get())) {
						for (String extension : VIDEO_EXTENSIONS) {
							if (file.getName().endsWith("." + extension)) {
								if (!(new File(file.getAbsolutePath().replace("." + extension, ".en.srt"))).exists() && !(new File(file.getAbsolutePath().replace("." + extension, ".srt"))).exists()) {
									files.add(file);
								}
							}
						}
					}
				} else if (file.isDirectory()) {
					findVideoFilesWithoutSubtitleFile(file.getAbsolutePath(), files);
				}
			}
		}
	}

	public static void appendToFile(Path filePath, String srtPath) {
		try {
			Files.write(filePath, (srtPath + "\r\n").getBytes(), StandardOpenOption.APPEND);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static void removeLineFromFile(Path filePath, String line) {
		try {
			File tempFile = new File("myTempFile.txt");

			BufferedReader reader = new BufferedReader(new FileReader(filePath.toFile()));
			BufferedWriter writer = new BufferedWriter(new FileWriter(tempFile));

			String currentLine;

			while ((currentLine = reader.readLine()) != null) {
				// trim newline when comparing with lineToRemove
				String trimmedLine = currentLine.trim();
				if (trimmedLine.equals(line))
					continue;
				writer.write(currentLine + "\r\n");
			}
			writer.close();
			reader.close();
			tempFile.renameTo(filePath.toFile());
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static void removeMissingSubtitles(List<String> subtitleFilePaths, Path toUpdatePath) {
		System.out.println("Checking for removed subtitles in: " + toUpdatePath);
		int count = 0;
		for (String subtitleFilePath : new ArrayList<String>(subtitleFilePaths)) {
			File subtitleFile = new File(subtitleFilePath);
			if (!subtitleFile.exists()) {
				removeLineFromFile(toUpdatePath, subtitleFilePath);
				subtitleFilePaths.remove(subtitleFilePath);
				count++;
			}
		}
		System.out.println("  Removed " + count + " subtitle pathes from " + toUpdatePath);
	}

	int clearPrintSize = -1;

	private void clearPrint(String text) {
		if (clearPrintSize != -1) {
			StringBuffer buf = new StringBuffer();
			for (int i = 0; i < clearPrintSize; i++) {
				buf.append("\b");
			}
			System.out.print(buf.toString());
		}
		clearPrintSize = text.length();
		System.out.print(text);
	}

	public boolean canProcess(String filePath) {
		return !processingSrtFile.contains(filePath) && !successfulProcessedSrtFiles.contains(filePath) && !unsuccessfulProcessedSrtFiles.contains(filePath) && !missingVideoFileSrtFiles.contains(filePath) && !toManyVideosSrtFiles.contains(filePath);
	}

	public static String formatDateTime(FileTime fileTime) {

		LocalDateTime localDateTime = fileTime.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();

		return localDateTime.format(DateTimeFormatter.ofPattern("MM/dd/yyyy hh:mm:ss"));
	}

	public String getCurrentDateTimeString() {
		return new SimpleDateFormat("MM-dd-MM-yyyy hh:mm:ss aa").format(new Date());
	}

	public static String formatSeconds(int timeInSeconds) {
		int hours = timeInSeconds / 3600;
		int secondsLeft = timeInSeconds - hours * 3600;
		int minutes = secondsLeft / 60;
		int seconds = secondsLeft - minutes * 60;

		String formattedTime = "";
		if (hours < 10)
			formattedTime += "0";
		formattedTime += hours + ":";

		if (minutes < 10)
			formattedTime += "0";
		formattedTime += minutes + ":";

		if (seconds < 10)
			formattedTime += "0";
		formattedTime += seconds;

		return formattedTime;
	}

}

class SubtitleSpeechSynchronizerWatcher {
	private WatchService watcher = null;
	private Map<WatchKey, Path> keys = null;
	private SubtitleSpeechSynchronizerHelper helper;

	/**
	 * Creates a WatchService and registers the given directory
	 * 
	 * @param helper
	 * 
	 * @param args
	 */
	SubtitleSpeechSynchronizerWatcher(SubtitleSpeechSynchronizerHelper helper, Path dir, String[] args) throws IOException {
		this.helper = helper;
		this.watcher = FileSystems.getDefault().newWatchService();
		this.keys = new HashMap<>();

		System.out.println("  Now watching " + dir);
		walkAndRegisterDirectories(dir);
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
				if ((kind == StandardWatchEventKinds.ENTRY_CREATE || kind == StandardWatchEventKinds.ENTRY_MODIFY) && helper.canProcess(filePath)) {
					try {
						if (Files.isDirectory(child)) {
							walkAndRegisterDirectories(child);
							System.out.println("Now watching " + child.toString());
						} else {
							Optional<String> extensionOption = SubtitleSpeechSynchronizerHelper.getExtensionByStringHandling(child.toString());
							if (extensionOption.isPresent() && "srt".equals(extensionOption.get())) {
								try {
									Thread.sleep(5000);
									helper.addSubtitleFileToProcess(child.toString(), child.getParent().toString(), true);
								} catch (InterruptedException e) {
									e.printStackTrace();
								}
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
}
