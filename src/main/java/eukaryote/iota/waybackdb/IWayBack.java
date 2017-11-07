package eukaryote.iota.waybackdb;

import java.util.List;

import jota.model.Transaction;

public interface IWayBack {
	Transaction getTransaction(String hash);

	List<Transaction> getTransactions(List<String> hash);

	List<Transaction> getBundle(String hash);

	List<Transaction> getTransactionsByAddress(String address);

}
