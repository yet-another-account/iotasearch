package eukaryote.iota.explorer;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.TreeSet;

import org.apache.commons.collections4.queue.CircularFifoQueue;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import com.google.gson.Gson;

import jota.IotaAPI;
import jota.utils.IotaUnitConverter;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class NZBundles extends WebSocketClient {
	Gson gson = new Gson();
	IotaAPI api;
	Webserver ws;
	
	public TreeSet<Message> queue = new TreeSet<>(new Comparator<Message>() {

		@Override
		public int compare(Message arg0, Message arg1) {
			return -Long.compare(arg0.getTimestampLong(), arg1.getTimestampLong());
		}
		
	});
	private URI serverUri;

	public NZBundles(Webserver ws, IotaAPI api, URI serverUri) {
		super(serverUri);
		this.api = api;
		this.serverUri = serverUri;
		this.ws = ws;
		
		this.connect();
	}

	@Override
	public void onOpen(ServerHandshake handshakedata) {
		log.info("ZMQ WS open");
		ws.nzbattempts = 0;
	}

	@Override
	public void onMessage(String message) {
		Message m = gson.fromJson(message, Message.class);
		m.getTimestampLong();
		
		if (m.getValue() == 0)
			return;
		
		queue.add(m);
		if (queue.size() >= 9)
			queue.remove(queue.last());
	}

	@Override
	public void onClose(int code, String reason, boolean remote) {
		log.info("ZMQ ws closed");
		
		log.warn("Auto restarting ZMQ websocket...");
		
		if (ws.nzbattempts > 10)
			return;
		
		ws.nzb = new NZBundles(ws, api, serverUri);
		ws.nzbattempts++;
	}

	@Override
	public void onError(Exception ex) {
		log.error("error in zmq ws: ", ex);
	}

	public String genTblBody() {
		StringBuilder sb = new StringBuilder();

		for (Message m : queue) {

			String tag;
			int signum = Long.signum(m.getValue());
			if (signum == 1)
				tag = "<span class=\"label label-success\">IN</span>";
			else if (signum == -1)
				tag = "<span class=\"label label-danger\">OUT</span>";
			else
				tag = "";
			
			sb.append(String.format(
					  "<tr><td>%s</td>"
					+ "<td><span class=\"monospace\"><a href=\"/hash/%s\">%s</a></span></td>"
					+ "<td><span class=\"monospace\"><a href=\"/hash/%s\">%s</a></span></td>"
					+ "<td>%s</td>"
					+ "<td>%s</td></tr>",
					Webserver.formatAgo(m.getTimestampLong()), 
					m.getHash(), truncate(m.getHash(), 10), 
					m.getAddress(), truncate(m.getAddress(), 10), 
					tag,
					IotaUnitConverter.convertRawIotaAmountToDisplayText(Math.abs(m.getValue()), true)));
		}

		return sb.toString();
	}

	public String truncate(String s, int len) {
		return s.substring(0, len) + "\u2026";
	}

	@Data
	class Bundle {
		String hash;
		int ninputs;
		int noutputs;
		long value;
		long timestamp;
	}

	@Data
	public class Message {
		public String hash;
		public String address;
		public long value;
		public String tag;
		public String timestamp;
		public String currentIndex;
		public String lastIndex;
		public String bundleHash;
		public String trunkTransaction;
		public String branchTransaction;
		public String arrivalTime;
		
		public transient long timestamplong = -1;
		
		public long getTimestampLong() {
			if (timestamplong <= 0)
				timestamplong = Long.parseLong(timestamp);
			
			return timestamplong;
		}
		
		public String formatAgo() {
			long before = System.currentTimeMillis() / 1000 - timestamplong;

			if (before <= 0)
				return "invalid timestamp";
			
			if (before < 3 && before > 0)
				return "just now";

			int sec = (int) (before % 60);
			before /= 60;
			int min = (int) (before % 60);
			before /= 60;
			int hrs = (int) (before % 24);
			before /= 24;
			int days = (int) before;

			// only show 2

			if (days != 0 || hrs != 0)
				sec = 0;

			if (days != 0)
				min = 0;

			return ((days == 0) ? "" : (days + " day" + ((days == 1) ? "" : "s") + " "))
					+ ((hrs == 0) ? "" : (hrs + " hr" + ((hrs == 1) ? "" : "s") + " "))
					+ ((min == 0) ? "" : (min + " min" + ((min == 1) ? "" : "s") + " "))
					+ ((sec == 0) ? "" : (sec + " sec" + ((sec == 1) ? "" : "s"))) + " ago";
		}
		
		public String formatAmt() {
			return IotaUnitConverter.convertRawIotaAmountToDisplayText(value, true);
		}
	}
}
