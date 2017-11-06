package eukaryote.iota.explorer;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.Timer;
import java.util.TimerTask;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;

import eukaryote.iota.confstat.ConfirmationStat;
import jota.IotaAPI;
import jota.dto.response.FindTransactionResponse;
import jota.dto.response.GetNodeInfoResponse;
import jota.error.NoNodeInfoException;
import jota.model.Transaction;
import jota.utils.Checksum;
import jota.utils.Converter;
import jota.utils.IotaUnitConverter;
import lombok.extern.slf4j.Slf4j;
import spark.Request;
import spark.Response;

import static spark.Spark.*;

@Slf4j
public class Webserver {
	Map<String, String> files = new HashMap<>();
	IotaAPI api;

	GraphFormatter gf;
	NZBundles nzb;

	SnapshotLoader sl;

	int nzbattempts = 0;

	ConfirmationStat stat;

	double rate;

	private SimpleDateFormat dateFormatGmt;
	private URL cmciotaprice;

	String index = "Please wait";

	public Webserver(int port) throws IOException, URISyntaxException {
		staticFiles.location("public");
		port(port);

		// prevent node from overloading
		threadPool(16);

		get("/", (req, res) -> {
			return index;
		});

		get("/hash/:hash", (req, res) -> {
			return serve(req, res);
		});

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

		String[] hosts = { "node01.iotatoken.nl" };

		int nodeindex = 0;
		do {
			try {
				api = new IotaAPI.Builder().protocol("http").host(hosts[nodeindex++]).port("14265").build();
			} catch (Exception e) {
				e.printStackTrace();
				api = null;
			}
		} while (api == null);
		log.info("node info {}", api.getNodeInfo());
		updatePages();
		
		gf = new GraphFormatter(api);
		nzb = new NZBundles(this, api, new URI("ws://tangle.blox.pm:8080"));
		stat = new ConfirmationStat(api);
		sl = new SnapshotLoader(new File("snapshot"));
	}

	private String serve(Request req, Response res) {

		String hash = req.params("hash");

		log.info("Requested hash {}", hash);

		if (hash.equals(coordinator)) {
			GetNodeInfoResponse nodeInfo = api.getNodeInfo();
			return files.get("/coo").replace("<$milestone$>", nodeInfo.getLatestMilestone())
					.replace("<$milestoneindex$>", "" + nodeInfo.getLatestMilestoneIndex());
		}

		if (hash.equals("999999999999999999999999999999999999999999999999999999999999999999999999999999999"))
			return files.get("invalidhash");

		if (hash.length() != 81 && hash.length() != 90)
			return files.get("/404");

		if (hash.length() == 81 && hash.endsWith("999"))
			try {
				// check if txn
				List<Transaction> txns = api.getTransactionsObjects(new String[] { hash });

				log.debug("txns: {}", txns);

				if (!txns.isEmpty() || (txns.get(0).getHash()
						.equals("999999999999999999999999999999999999999999999999999999999999999999999999999999999"))) {
					return formatTransaction(txns.get(0));
				}

			} catch (IllegalAccessError | Exception e) {
				log.error("Error:", e);
				// invalid txn hash
			}

		if (hash.length() != 90)
			try {
				// check if bundle
				FindTransactionResponse gbr = api.findTransactionsByBundles(hash);

				log.debug("grb.len={}", gbr.getHashes().length);

				if (gbr.getHashes().length != 0)
					return formatBundle(hash, gbr.getHashes());

			} catch (IllegalAccessError | Exception e) {
				log.debug("Error:", e);
				// invalid bdl hash
			}

		try {

			// redirect with checksum
			if (hash.length() == 81 && !hash.endsWith("999")) {
				hash = Checksum.addChecksum(hash);

				res.redirect("/hash/" + hash);
				return "";
			}

			// check if address
			FindTransactionResponse ftba = api.findTransactionsByAddresses(hash);

			long presnapshotval = sl.getPreSnapshot(hash.substring(0, 81));

			if (ftba.getHashes().length != 0 || presnapshotval != 0)
				return formatAddr(hash, ftba.getHashes(), presnapshotval);
		} catch (IllegalAccessError | Exception e) {
			log.debug("Error:", e);
			// invalid address hash
		}

		return files.get("invalidhash");
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
		files.put("invalidhash", parse(new File("html/invalidhash.html")).replace("<!--noindexmeta-->",
				"<meta name=\"robots\" content=\"noindex\">"));
		files.put("/tanglegraph",
				FileUtils.readFileToString(new File("html/tanglegraph.html"), Charset.forName("UTF-8")));

		// google verification

		files.put("/google5c70ae64d13d35e2.html", "google-site-verification: google5c70ae64d13d35e2.html");

	}

	public String chTitle(String pg, String newtitle) {
		return title.matcher(pg).replaceFirst("<title>" + newtitle + "</title>");
	}

	public String parse(File f) throws IOException {
		return (files.get("/header")) + FileUtils.readFileToString(f, Charset.forName("UTF-8"))
				+ (files.get("/footer"));
	}

	public String formatIndex(String dat) {
		GetNodeInfoResponse nodeInfo = api.getNodeInfo();

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
				.replace("<$memt$>", "" + FileUtils.byteCountToDisplaySize(nodeInfo.getJreTotalMemory()))
				.replace("<$lasttxns$>", nzb == null ? "" : nzb.genTblBody())
				.replace("<$graph$>", files.get("/tanglegraph")), milestone);
	}

	public Transaction getTxnFromHash(String hash) {
		return api.getTransactionsObjects(new String[] { hash }).get(0);
	}

	static final Pattern title = Pattern.compile("<title>.+</title>");

	/*
	 * FORMAT TRANSACTION
	 */
	public String formatTransaction(Transaction txn) {

		StringBuilder sb = new StringBuilder();

		String addrlink = "<a href=\"/hash/" + txn.getAddress() + "\">" + txn.getAddress() + "</a>";
		String bdllink = "<a href=\"/hash/" + txn.getBundle() + "\">" + txn.getBundle() + "</a>";
		String trunk = "<a href=\"/hash/" + txn.getTrunkTransaction() + "\">" + txn.getTrunkTransaction() + "</a>";
		String branch = "<a href=\"/hash/" + txn.getBranchTransaction() + "\">" + txn.getBranchTransaction() + "</a>";

		try {
			return title
					.matcher(
							gf.formatTransaction(
									files.get("/txn").replace("<$addrlink$>", addrlink)
											.replace("<$hash$>", txn.getHash())
											.replace("<$amt$>",
													IotaUnitConverter.convertRawIotaAmountToDisplayText(
															Math.abs(txn.getValue()), true))
											.replace("<$time$>",
													dateFormatGmt.format(new Date(txn.getTimestamp() * 1000))
															// ... ago
															+ " (" + formatAgo(txn.getTimestamp()) + " ago)")

											.replace("<$tag$>", txn.getTag().replaceFirst("9+$", ""))
											.replace("<$nonce$>", txn.getNonce())
											.replace("<$msgraw$>", txn.getSignatureFragments())
											.replace("<$branch$>", branch).replace("<$trunk$>", trunk)
											.replace("<$bundle$>", bdllink)
											.replace("<$stat$>", stat.statusOf(txn).toString())
											.replace("<$index$>", "" + (txn.getCurrentIndex()))
											.replace("<$totalindex$>", "" + (txn.getLastIndex()))
											.replace("<$wmag$>", "" + getWM(txn))
											.replace("<$graph$>", files.get("/tanglegraph")),
									txn.getHash()))
					.replaceFirst("<title>Iota Transaction " + txn.getHash() + "</title>");
		} catch (NoNodeInfoException e) {
			return "<h1>500 Internal Server Error</h1>\n"
					+ "Node is probably down, try again later. If stuff is still broken, scream at me on slack (@eukaryote) until I fix things.";
		}
	}

	private static int getWM(Transaction txn) {
		int ret = 0;

		String hash = txn.getHash();

		int[] trits = Converter.trits(hash);

		for (int i = trits.length - 1; i >= 0; i--, ret++)
			if (trits[i] != 0)
				break;

		return ret;
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
				 * if (txn.getTimestamp() + threshold > System.currentTimeMillis() / 1000)
				 * return 0; else return -1;
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

		return title.matcher(files.get("/bundle").replace("<$inputs$>", inputs.toString()).replace("<$outputs$>",
				outputs.toString())).replaceFirst("<title>IOTA Bundle " + hash + "</title>");
	}

	String cpybtn = "<button class=\"btn\" data-clipboard-target=\"#hashdisp\">"
			+ "<span class=\"fa fa-clipboard\" aria-hidden=\"true\"></i>" + "</button>";

	String coordinator = "KPWCHICGJZXKE9GSUDXZYUAPLHAKAHYHDXNPHENTERYMMBQOPSQIDENXKLKCEYCPVTZQLEEJVYJZV9BWU";

	public String formatAddr(String addr, String[] hashes, long presnapshotval) {
		NumberFormat formatter = NumberFormat.getCurrencyInstance();

		log.debug("Formatting hashes (count: {})", hashes.length);

		StringBuilder sb = new StringBuilder();

		// if (addr.equals(coordinator))
		// sb.append("<h2 class=\"text-center\">Coordinator</h2>");

		sb.append("<div class=\"row\">");
		sb.append("<div class=\"col-lg-3 col-md-4\"> <div id=\"qr\" class=\"text-center\"></div>");
		sb.append("</div>"); // /col-lg-2
		sb.append("<div class=\"col-lg-9 col-md-8\">");

		sb.append("<table class=\"table\">");

		sb.append("<tr><td>Address: </td><td class=\"hashtd\"> <span id=\"hashdisp\">" + addr.substring(0, 81)
				+ "<span title=\"Checksum\" id=\"addr-checksum\" class=\"text-muted\">" + addr.substring(81)
				+ "</span></span></td></tr>");

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

		sb.append("</div>"); // /col-lg-10
		sb.append("</div>"); // /row

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

		sb.append(
				"<thead><tr><th>Age</th><th>Status</th><th>Transaction Hash</th><th>Bundle Hash</th><th>Tag</th><th></th><th>Amount</th></tr></thead>");

		sb.append("<tbody>");

		Set<String> confirmedbdls = new HashSet<>(32);
		Map<String, Integer> confirmed = new HashMap<>(32);

		Map<String, Integer> unconfirmed = new HashMap<>(32);

		for (Transaction txn : txnobjs) {
			int num = getConfirmedNum(txn);

			confirmed.put(txn.getHash(), num);

			if (num != 1) {
				unconfirmed.compute(txn.getBundle(), (key, oldval) -> {
					return oldval == null ? 1 : oldval + 1;
				});
			}

			if (num == 1)
				confirmedbdls.add(txn.getBundle());
		}

		for (Transaction txn : txnobjs) {
			int num = confirmed.get(txn.getHash());

			if (num == 0) {
				sb.append("<tr class=\"danger\">");
			} else if (num == 1) {
				sb.append("<tr>");
			} else {
				sb.append("<tr class=\"warning\">");
			}

			// skip if not confirmed but bundle is confirmed
			if (num != 1 && confirmedbdls.contains(txn.getBundle()))
				continue;

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
			if (num == 1 && unconfirmed.containsKey(txn.getBundle())) {
				// main, confirmed tx

				int txncount = unconfirmed.get(txn.getBundle());
				sb.append("<tr><td colspan=\"5\">&emsp;<i class=\"fa fa-level-up fa-rotate-90\"></i>&emsp;... "
						+ txncount + " more duplicate unconfirmed transaction" + (txncount == 1 ? "" : "s")
						+ " (probably reattaches)</td></tr>");
			}
		}

		// snapshot
		if (presnapshotval != 0) {
			sb.append("<tr><td colspan = \"5\" class=\"text-center\">Snapshot</td>"
					+ "<td><span class=\"label label-success\">IN</span></td><td>"
					+ IotaUnitConverter.convertRawIotaAmountToDisplayText(presnapshotval, true) + "</td>");
		}

		sb.append("</tbody>");

		sb.append("</table>");

		return title.matcher(files.get("/addr").replace("<$addr$>", addr).replace("<$table$>", sb.toString()))
				.replaceFirst("<title>IOTA Address " + addr + "</title>");
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
}
