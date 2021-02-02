import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;


public class StreamGobbler extends Thread {
	private static final String BLANK_STRING = "";
    private InputStream is;
    private String type;
    private List<String> outputList = new ArrayList<>();

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
            	if(!BLANK_STRING.equals(line.trim())) {
            		outputList.add(line);
            		if(line.contains("%,")) {
            			int percentageDone = Integer.valueOf(line.substring(line.indexOf("progress  ")+10, line.indexOf("%")).replaceAll("progress ", "").replace(":", "").trim());
            			if(percentageDone%10 == 0) {
            				System.out.println("    " + type + "> " + line);
            			}
            		} else {
            			System.out.println("    " + type + "> " + line);
            		}
            	}
            }
        }
        catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }
    
    public List<String> getOutputList() {
    	return outputList;
    }
    //Aquaman2018.en.srt OUTPUT> [-] couldn't synchronize!

}