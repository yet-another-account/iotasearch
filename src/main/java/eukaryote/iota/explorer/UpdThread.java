package eukaryote.iota.explorer;

import java.io.IOException;
import java.util.TimerTask;

public class UpdThread extends TimerTask {
	Webserver ws;
	
	public UpdThread(Webserver ws) {
		this.ws = ws;
	}
	
	@Override
	public void run() {
		try {
			ws.updatePrice();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
}
