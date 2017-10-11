package eukaryote.iota.explorer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.apache.commons.lang3.StringUtils;

import jota.IotaAPI;
import jota.model.Transaction;

public class GraphFormatter {

	IotaAPI api;
	int truncatelen = 8;

	final String[] colors = { "FE2F00", "F78700", "F0DB00", "AAEA00", "00DD00" };

	public GraphFormatter(IotaAPI api) {
		this.api = api;
	}

	public void formatTransaction(String txnhash, Map<String, Object> data) {
		final int depth = 5;
		final String[] strarr = new String[] {};

		Map<Transaction, Integer> txns = new TreeMap<Transaction, Integer>(new Comparator<Transaction>() {

			@Override
			public int compare(Transaction arg0, Transaction arg1) {
				return Integer.compare(arg0.getHash().hashCode(), arg1.getHash().hashCode());
			}

		});

		List<String> toadd = new ArrayList<>(64);
		toadd.add(txnhash);

		for (int i = 0; i < depth; i++) {
			List<Transaction> newtxns = api.getTransactionsObjects(toadd.toArray(strarr));

			for (Transaction t : newtxns)
				txns.putIfAbsent(t, i);

			toadd.clear();

			for (Transaction t : newtxns) {
				if (!t.getBranchTransaction()
						.equals("999999999999999999999999999999999999999999999999999999999999999999999999999999999"))
					toadd.add(t.getBranchTransaction());
				if (!t.getTrunkTransaction()
						.equals("999999999999999999999999999999999999999999999999999999999999999999999999999999999"))
					toadd.add(t.getTrunkTransaction());
			}
		}

		// pack into html

		StringBuilder nodes = new StringBuilder();
		StringBuilder edges = new StringBuilder();

		for (Entry<Transaction, Integer> e : txns.entrySet()) {
			Transaction t = e.getKey();
			int i = e.getValue().intValue();

			if (i < depth)
				nodes.append(formatColorNode(t.getHash(), colors[i]));
			else
				nodes.append(formatNode(t.getHash()));
			edges.append(formatEdge(t.getHash(), t.getTrunkTransaction()));
			edges.append(formatEdge(t.getHash(), t.getBranchTransaction()));
		}

		data.put("edges", edges.toString());
		data.put("nodes", nodes.toString());
	}

	private String formatNode(String node) {
		return "{id:'" + node + "',label:'" + truncate(node, truncatelen) + "'},\n";
	}

	private String formatColorNode(String node, String color) {
		return "{id:'" + node + "',label:'" + truncate(node, truncatelen) + "',color:'#" + color + "'},\n";
	}

	private String formatEdge(String from, String to) {
		return "{from:'" + from + "',to:'" + to + "',arrows:'to'},\n";
	}

	public String truncate(String s, int len) {
		return s.substring(0, len) + "\u2026";
	}
}
