package eukaryote.iota.waybackdb;

import jota.model.Transaction;

public interface IWayBack {
	Transaction getTransaction(String hash);

}
