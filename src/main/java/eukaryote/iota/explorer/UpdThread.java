package eukaryote.iota.explorer;

import java.io.IOException;
import java.util.TimerTask;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class UpdThread extends TimerTask {
	Webserver ws;
	
	public UpdThread(Webserver ws) {
		this.ws = ws;
	}
	
	@Override
	public void run() {
		try {
			ws.updatePrice();
		} catch (Exception e) {
			log.error("Timer error ", e);
		}
	}
	
}
