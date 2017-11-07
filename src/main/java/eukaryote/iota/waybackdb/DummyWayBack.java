package eukaryote.iota.waybackdb;

import jota.model.Transaction;

public class DummyWayBack implements IWayBack {

	@Override
	public Transaction getTransaction(String hash) {
		return null;
	}

}
