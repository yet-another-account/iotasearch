package eukaryote.iota.waybackdb;

import java.util.Collections;
import java.util.List;

import jota.model.Transaction;

public class DummyWayBack implements IWayBack {

	@Override
	public Transaction getTransaction(String hash) {
		return null;
	}

	@Override
	public List<Transaction> getTransactions(List<String> hash) {
		return Collections.emptyList();
	}

	@Override
	public List<Transaction> getBundle(String hash) {
		return null;
	}

	@Override
	public List<Transaction> getTransactionsByAddress(String address) {	
		return null;
	}

}
