import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;


public class StreamGobbler extends Thread {
	private static final String BLANK_STRING = "";
    private InputStream is;
    private String type;

    public StreamGobbler(InputStream is, String type) {
        this.is = is;
        this.type = type;
    }

    @Override
    public void run() {
        try {
            InputStreamReader isr = new InputStreamReader(is);
            BufferedReader br = new BufferedReader(isr);
            String line = null;
            while ((line = br.readLine()) != null) {
            	if(BLANK_STRING.equals(line.trim())) {
            		System.out.println("    " + type + "> " + line);
            	}
            }
        }
        catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }
}