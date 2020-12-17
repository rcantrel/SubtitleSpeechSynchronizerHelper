import java.io.File;
import java.util.Locale;

public class OSUtils {
	private static final String OS_NAME = "os.name";
	private static final String MAC = "mac";
	private static final String WINDOWS = "win";
	private static OSUtils instance;
	private static String osType = System.getProperty(OS_NAME).toLowerCase(Locale.ENGLISH);

	private OSUtils() {

	}

	public static OSUtils getInstance() {
		if (instance == null) {
			instance = new OSUtils();
		}
		return instance;
	}

	public boolean isMac() {
		return osType.indexOf(MAC) >= 0;
	}

	public boolean isWindows() {
		return osType.indexOf(WINDOWS) >= 0;
	}

	public String getWorkingDirectory() {
		String workingDirectory;
		// here, we assign the name of the OS, according to Java, to a
		// variable...
		String OS = System.getProperty("os.name").toUpperCase();
		// to determine what the workingDirectory is.
		// if it is some version of Windows
		if (OS.contains("WIN")) {
			// it is simply the location of the "AppData" folder
			workingDirectory = System.getenv("AppData");
		}
		// Otherwise, we assume Linux or Mac
		else {
			// in either case, we would start in the user's home directory
			workingDirectory = System.getProperty("user.home");
			// if we are on a Mac, we are not done, we look for "Application
			// Support"
			workingDirectory += "/Library/Application Support";
		}
		return workingDirectory;
	}

	public File getWorkingDirectoryFile() {
		return new File(getWorkingDirectory());
	}
}
