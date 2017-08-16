package eukaryote.iota.explorer;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.Timer;
import java.util.TimerTask;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.Response.Status;
import jota.IotaAPI;
import jota.dto.response.FindTransactionResponse;
import jota.dto.response.GetBundleResponse;
import jota.dto.response.GetNodeInfoResponse;
import jota.error.NoNodeInfoException;
import jota.model.Transaction;
import jota.utils.IotaUnitConverter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Webserver extends NanoHTTPD {
	Map<String, String> files = new HashMap<>();
	IotaAPI api;

	GraphFormatter gf;
	NZBundles nzb;

	double rate;

	public static final String MIME_PLAINTEXT = "text/plain", MIME_HTML = "text/html",
			MIME_JS = "application/javascript", MIME_CSS = "text/css", MIME_PNG = "image/png",
			MIME_DEFAULT_BINARY = "application/octet-stream", MIME_XML = "text/xml", MIME_ICO = "text/x-icon";
	private SimpleDateFormat dateFormatGmt;
	private URL cmciotaprice;

	String index = "<h1>Please wait, server is starting</h1>";

	public Webserver(int port) throws IOException, URISyntaxException {
		super(port);

		Locale.setDefault(Locale.US);

		cmciotaprice = new URL("https://api.coinmarketcap.com/v1/ticker/iota/");

		// update price every 2m
		TimerTask updprice = new UpdThread(this);
		Timer timer = new Timer();
		timer.schedule(updprice, 3 * 1000, 30 * 1000);
		
		// update price every 2m
		TimerTask updfast = new FastUpdThread(this);
		Timer timer2 = new Timer();
		timer2.schedule(updfast, 3 * 1000, 1000);

		dateFormatGmt = new SimpleDateFormat("yyyy-MMM-dd HH:mm:ss");
		dateFormatGmt.setTimeZone(TimeZone.getTimeZone("GMT"));

		api = new IotaAPI.Builder().protocol("http").host("node.iotasear.ch").port("14265").build();

		updatePages();

		// log.info("${}/Mi", rate);

		start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);

		gf = new GraphFormatter(api);
		nzb = new NZBundles(api, new URI("ws://node.iotasear.ch:5557"));
	}

	protected void updatePages() throws IOException {

		files.put("/header", FileUtils.readFileToString(new File("html/header.html"), Charset.forName("UTF-8")));
		files.put("/footer", FileUtils.readFileToString(new File("html/footer.html"), Charset.forName("UTF-8")));

		files.put("/", parse(new File("html/index.html")));
		files.put("/faq", chTitle(parse(new File("html/faq.html")), "FAQ - IOTA Tangle Explorer"));
		files.put("/donors", chTitle(parse(new File("html/donors.html")), "Donors - IOTA Tangle Explorer"));
		files.put("/addr", parse(new File("html/addr.html")));
		files.put("/txn", parse(new File("html/txn.html")));
		files.put("/bundle", parse(new File("html/bundle.html")));
		files.put("/coo", parse(new File("html/coo.html")));
		files.put("/404", parse(new File("html/404.html")));
		files.put("/invalidhash", parse(new File("html/invalidhash.html")).replace("<!--noindexmeta-->",
				"<meta name=\"robots\" content=\"noindex\">"));
		files.put("/tanglegraph",
				FileUtils.readFileToString(new File("html/tanglegraph.html"), Charset.forName("UTF-8")));

		// google verification

		files.put("/google5c70ae64d13d35e2.html", "google-site-verification: google5c70ae64d13d35e2.html");

		// opensearch xml
		files.put("/opensearch.xml", FileUtils.readFileToString(new File("html/opensearch.xml"), Charset.forName("UTF-8")));
		
		addDir("html/css");
		addDir("html/js");
		addDir("html/fonts");

	}
	
	public String chTitle(String pg, String newtitle) {
		return title.matcher(pg).replaceFirst("<title>" + newtitle + "</title>");
	}

	@Override
	public Response serve(IHTTPSession session) {
		log.info("URL Requested: {}", session.getUri());
		String uri = session.getUri();

		// if (uri.equals("/test"))
		// return newFixedLengthResponse(nzb.queue.toString());

		// root
		if (uri.equals("/")) {
			return newFixedLengthResponse(index);
		} else if (uri.startsWith("/hash")) {
			// get hash
			String[] split = uri.split("/", 3);

			if (split.length != 3)
				return newFixedLengthResponse(files.get("/404"));

			String hash = split[2];

			log.info("Requested hash {}", hash);

			if (hash.equals(coordinator)) {
				GetNodeInfoResponse nodeInfo = api.getNodeInfo();
				return newFixedLengthResponse(files.get("/coo").replace("<$milestone$>", nodeInfo.getLatestMilestone())
						.replace("<$milestoneindex$>", "" + nodeInfo.getLatestMilestoneIndex()));
			}
			
			if (hash.equals("999999999999999999999999999999999999999999999999999999999999999999999999999999999"))
				return newFixedLengthResponse(files.get("/invalidhash"));
			
			if (hash.length() != 81 && hash.length() != 90)
				return newFixedLengthResponse(files.get("/404"));

			if (hash.endsWith("99"))
				try {
					// check if txn
					List<Transaction> txns = api.getTransactionsObjects(new String[] { hash });

					log.debug("txns: {}", txns);

					if (!txns.isEmpty()) {
						return newFixedLengthResponse(formatTransaction(txns.get(0)));
					}

				} catch (IllegalAccessError | Exception e) {
					log.debug("Error:", e);
					// invalid txn hash
				}

			if (hash.length() != 90)
				try {
					// check if bundle
					FindTransactionResponse gbr = api.findTransactionsByBundles(hash);

					log.debug("grb.len={}", gbr.getHashes().length);

					if (gbr.getHashes().length != 0)
						return newFixedLengthResponse(formatBundle(hash, gbr.getHashes()));

				} catch (IllegalAccessError | Exception e) {
					log.debug("Error:", e);
					// invalid bdl hash
				}

			try {
				// check if address
				FindTransactionResponse ftba = api.findTransactionsByAddresses(hash);

				log.debug("Hashreq Duration: {}", ftba.getDuration());

				log.info("addr");
				if (ftba.getHashes().length != 0)
					return newFixedLengthResponse(formatAddr(hash, ftba.getHashes()));
			} catch (IllegalAccessError | Exception e) {
				log.debug("Error:", e);
				// invalid address hash
			}

			return newFixedLengthResponse(files.get("/invalidhash"));
		}

		if (files.containsKey(uri)) {
			if (uri.endsWith(".js"))
				return newFixedLengthResponse(Status.OK, MIME_JS, files.get(uri));
			if (uri.endsWith(".css"))
				return newFixedLengthResponse(Status.OK, MIME_CSS, files.get(uri));
			if (uri.endsWith(".png"))
				return newFixedLengthResponse(Status.OK, MIME_PNG, files.get(uri));
			if (uri.endsWith(".xml"))
				return newFixedLengthResponse(Status.OK, MIME_XML, files.get(uri));
			else
				return newFixedLengthResponse(files.get(uri));
		}
		// 404
		return newFixedLengthResponse(Status.NOT_FOUND, MIME_HTML, files.get("/404"));
	}

	public String parse(File f) throws IOException {
		return (isNotHTML(f.getName()) ? "" : files.get("/header"))
				+ FileUtils.readFileToString(f, Charset.forName("UTF-8"))
				+ (isNotHTML(f.getName()) ? "" : files.get("/footer"));
	}

	private boolean isNotHTML(String fname) {
		return fname.endsWith(".js") || fname.endsWith(".css") || fname.contains("glyphicons")
				|| fname.endsWith(".png");
	}

	public String formatIndex(String dat) {

		GetNodeInfoResponse nodeInfo = api.getNodeInfo();

		StringBuilder nodes = new StringBuilder();
		String milestone = nodeInfo.getLatestMilestone();

		return gf.formatTransaction(dat.replace("<$ver$>", nodeInfo.getAppName() + " " + nodeInfo.getAppVersion())
				.replace("<$milestone$>",
						"<a href=\"/hash/" + nodeInfo.getLatestMilestone() + "\">" + nodeInfo.getLatestMilestoneIndex()
								+ "</a>")
				.replace("<$ssmilestone$>",
						"<a href=\"/hash/" + nodeInfo.getLatestSolidSubtangleMilestone() + "\">"
								+ nodeInfo.getLatestSolidSubtangleMilestoneIndex() + "</a>")
				.replace("<$neighbors$>", "" + nodeInfo.getNeighbors()).replace("<$tips$>", "" + nodeInfo.getTips())
				.replace("<$cput$>", "" + nodeInfo.getJreAvailableProcessors())
				.replace("<$memt$>", "" + readableFileSize(nodeInfo.getJreTotalMemory()))
				.replace("<$lasttxns$>", nzb.genTblBody())
				.replace("<$graph$>", files.get("/tanglegraph")), milestone);
	}

	public Transaction getTxnFromHash(String hash) {
		return api.getTransactionsObjects(new String[] { hash }).get(0);
	}
	static final Pattern title = Pattern.compile("<title>.+</title>");
	public String formatTransaction(Transaction txn) {

		StringBuilder sb = new StringBuilder();

		String addrlink = "<a href=\"/hash/" + txn.getAddress() + "\">" + txn.getAddress() + "</a>";
		String bdllink = "<a href=\"/hash/" + txn.getBundle() + "\">" + txn.getBundle() + "</a>";
		String trunk = "<a href=\"/hash/" + txn.getTrunkTransaction() + "\">" + txn.getTrunkTransaction() + "</a>";
		String branch = "<a href=\"/hash/" + txn.getBranchTransaction() + "\">" + txn.getBranchTransaction() + "</a>";

		return title.matcher(gf.formatTransaction(
				files.get("/txn")
						.replace("<$addrlink$>", addrlink).replace("<$hash$>", txn.getHash())
						.replace("<$amt$>",
								IotaUnitConverter.convertRawIotaAmountToDisplayText(Math.abs(txn.getValue()), true))
						.replace("<$time$>",
								dateFormatGmt.format(new Date(txn.getTimestamp() * 1000))
										// ... ago
										+ " (" + formatAgo(txn.getTimestamp()) + " ago)")
						.replace("<$tag$>", txn.getTag().replaceFirst("9+$", "")).replace("<$nonce$>", txn.getNonce())
						.replace("<$msgraw$>", txn.getSignatureFragments()).replace("<$branch$>", branch)
						.replace("<$trunk$>", trunk).replace("<$bundle$>", bdllink)
						.replace("<$stat$>", getConfirmed(txn)).replace("<$graph$>", files.get("/tanglegraph")),
				txn.getHash())).replaceFirst("<title>Iota Transaction " + txn.getHash() + "</title>");
	}

	public static String formatAgo(long epochsec) {
		long before = System.currentTimeMillis() / 1000 - epochsec;

		if (before < 10)
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
				+ ((sec == 0) ? "" : (sec + " sec" + ((sec == 1) ? "" : "s")));
	}

	public String getConfirmed(Transaction txn) {
		int num = getConfirmedNum(txn);

		return getConfirmed(num);
	}

	public String getConfirmed(int num) {
		if (num == 1)
			return "Confirmed";
		else if (num == 0)
			return "Unconfirmed";
		else
			return "Failed";
	}

	public int getConfirmedNum(Transaction txn) {
		final int threshold = 60 * 30;
		try {
			boolean b = api.getLatestInclusion(new String[] { txn.getHash() }).getStates()[0];
			if (b) {
				return 1;
			} else {
				return 0;
				
				/*
				if (txn.getTimestamp() + threshold > System.currentTimeMillis() / 1000)
					return 0;
				else
					return -1;
					*/
			}
		} catch (Exception e) {
			log.error("err", e);
		}

		return -1;
	}

	public boolean isConfirmed(Transaction txn) {
		try {
			return api.getLatestInclusion(new String[] { txn.getHash() }).getStates()[0];
		} catch (Exception e) {
			log.error("err", e);
		}

		return false;
	}

	public String formatBundle(String hash, String[] txns) {
		log.info("bundle");
		StringBuilder inputs = new StringBuilder();
		inputs.append("<table class=\"table table-striped table-hover\">");
		inputs.append("<thead><tr><th>Transaction Hash</th><th>Address</th><th>Amount</th></tr></thead><tbody>");

		StringBuilder outputs = new StringBuilder();
		outputs.append("<table class=\"table table-striped table-hover\">");
		outputs.append("<thead><tr><th>Transaction Hash</th><th>Address</th><th>Amount</th></tr></thead><tbody>");

		List<Transaction> txnobjs = api.getTransactionsObjects(txns);

		// sort by value descending
		Collections.sort(txnobjs, new Comparator<Transaction>() {

			@Override
			public int compare(Transaction arg0, Transaction arg1) {
				return -Long.compare(Math.abs(arg0.getValue()), Math.abs(arg1.getValue()));
			}

		});

		for (Transaction t : txnobjs) {
			StringBuilder sb = outputs;
			if (t.getValue() < 0) {
				// input
				sb = inputs;
			}

			sb.append("<tr><td class=\"monospace\">" + "<a href=\"/hash/" + t.getHash() + "\">"
					+ t.getHash().substring(0, 15) + "&hellip;</a></td>");
			sb.append("</td><td class=\"monospace\"><a href=\"/hash/" + t.getAddress() + "\">"
					+ t.getAddress().substring(0, 15) + "&hellip;</a></td>");
			sb.append("<td>" + IotaUnitConverter.convertRawIotaAmountToDisplayText(Math.abs(t.getValue()), true)
					+ "</td></tr>\n");
		}

		inputs.append("\n</tbody></table>");
		outputs.append("\n</tbody></table>");

		return title.matcher(files.get("/bundle").replace("<$inputs$>", inputs.toString()).replace("<$outputs$>", outputs.toString()))
				.replaceFirst("<title>IOTA Bundle " + hash + "</title>");
	}

	String cpybtn = "<button class=\"btn\" data-clipboard-target=\"#hashdisp\">"
			+ "<span class=\"fa fa-clipboard\" aria-hidden=\"true\"></i>" + "</button>";

	String coordinator = "KPWCHICGJZXKE9GSUDXZYUAPLHAKAHYHDXNPHENTERYMMBQOPSQIDENXKLKCEYCPVTZQLEEJVYJZV9BWU";

	public String formatAddr(String addr, String[] hashes) {
		NumberFormat formatter = NumberFormat.getCurrencyInstance();

		log.debug("Formatting hashes (count: {})", hashes.length);

		StringBuilder sb = new StringBuilder();

		// if (addr.equals(coordinator))
		// sb.append("<h2 class=\"text-center\">Coordinator</h2>");

		sb.append("<table class=\"table\">");

		sb.append("<tr><td>Address: </td><td class=\"hashtd\"> <span id=\"hashdisp\">" + addr + "</span></td></tr>");

		long bal = Long.parseLong(api.getBalances(1, new String[] { addr }).getBalances()[0]);

		sb.append("<tr><td>Final Balance: </td><td>" + IotaUnitConverter.convertRawIotaAmountToDisplayText(bal, true)
				+ "</td></tr>");

		// add usd val
		double usdval = (bal * rate / 1000000);
		sb.append(
				"<tr><td>USD Value: </td><td>" + ((usdval < 0.01 && usdval > 0) ? "<$0.01" : (formatter.format(usdval)))
						+ " (@ $" + rate + "/Mi)</td></tr>");
		sb.append("<tr><td>Number of Transactions: </td><td>" + hashes.length + "</td></tr>");
		sb.append("</table>");

		sb.append("<table class=\"table table-striped table-hover\">");

		List<Transaction> txnobjs = addr.equals(coordinator) ? null : api.getTransactionsObjects(hashes);

		if (addr.equals(coordinator) || txnobjs.size() > 200) {
			sb.append("<tr><td><h2 class=\"text-center\">Too many transactions to count!</h2></td></tr>");
		}

		Collections.sort(txnobjs, new Comparator<Transaction>() {

			@Override
			public int compare(Transaction arg0, Transaction arg1) {
				// sort by timestamp descending
				return Long.compare(arg1.getTimestamp(), arg0.getTimestamp());
			}

		});

		log.debug("txnobjs.len={}", txnobjs.size());

		sb.append(
				"<thead><tr><th>Age</th><th>Status</th><th>Transaction Hash</th><th>Bundle Hash</th><th>Tag</th><th></th><th>Amount</th></tr></thead>");

		sb.append("<tbody>");
		for (Transaction txn : txnobjs) {
			int num = getConfirmedNum(txn);
			if (num == 0) {

				sb.append("<tr class=\"danger\">");
			} else if (num == 1) {
				sb.append("<tr>");
			} else {
				sb.append("<tr class=\"warning\">");
			}
			sb.append("<td>");
			sb.append(formatAgo(txn.getTimestamp()));
			sb.append("</td>");
			sb.append("<td>");
			sb.append(getConfirmed(num));
			sb.append("</td>");
			sb.append("<td>");
			sb.append("<a href=\"/hash/" + txn.getHash() + "\">" + txn.getHash().substring(0, 15) + "&hellip;</a>");
			sb.append("</td>");
			sb.append("<td>");
			sb.append("<a href=\"/hash/" + txn.getBundle() + "\">" + txn.getBundle().substring(0, 15) + "&hellip;</a>");
			sb.append("</td>");
			sb.append("<td>");
			sb.append("<span class=\"monospace\"><small>" + txn.getTag().replaceFirst("9+$", "") + "</small></span>");
			sb.append("</td>");
			sb.append("<td>");
			int signum = Long.signum(txn.getValue());
			if (signum == 1)
				sb.append("<span class=\"label label-success\">IN</span>");
			else if (signum == -1)
				sb.append("<span class=\"label label-danger\">OUT</span>");
			sb.append("</td>");
			sb.append("<td>");
			sb.append(IotaUnitConverter.convertRawIotaAmountToDisplayText(Math.abs(txn.getValue()), true));
			sb.append("</td>");
			sb.append("</tr>");
		}
		sb.append("</tbody>");

		sb.append("</table>");

		log.debug("Built table");

		return title.matcher(
				files.get("/addr").replace("<$table$>", sb.toString())).replaceFirst("<title>IOTA Address " + addr + "</title>");
	}

	public void addDir(String dir) throws IOException {
		File fdir = new File(dir);

		for (File fn : fdir.listFiles()) {
			String fnorm = "/" + dir.substring("/html".length()) + "/" + fn.getName();
			String fcont = parse(fn);

			files.put(fnorm, fcont);
			log.debug("Adding {}", fnorm);
		}
	}

	public void updatePrice() throws IOException {
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(cmciotaprice.openStream(), "UTF-8"))) {
			for (String line; (line = reader.readLine()) != null;) {
				if (line.contains("price_usd")) {
					String sc = line.split(": ")[1].substring(1);
					sc = sc.split("\",")[0];
					rate = Double.parseDouble(sc);

					return;
				}
			}
		}
	}

	static DecimalFormat fmt = new DecimalFormat("#,##0.#");

	public static String readableFileSize(long size) {
		if (size <= 0)
			return "0";
		final String[] units = new String[] { "B", "kB", "MB", "GB", "TB" };
		int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
		return fmt.format(size / Math.pow(1024, digitGroups)) + " " + units[digitGroups];
	}
}
