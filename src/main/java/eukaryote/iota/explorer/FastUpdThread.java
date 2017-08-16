package eukaryote.iota.explorer;

import java.io.IOException;
import java.util.TimerTask;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FastUpdThread extends TimerTask {
	Webserver ws;
	
	public FastUpdThread(Webserver ws) {
		this.ws = ws;
	}
	
	@Override
	public void run() {
		try {
			ws.index = ws.formatIndex(ws.files.get("/"));
		} catch (Exception e) {
			log.error("Timer error ", e);
		}
	}
	
}
