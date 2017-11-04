package eukaryote.iota.explorer.db;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.sql.DataSource;

import org.skife.jdbi.v2.DBI;

import jota.model.Transaction;

public class HistorySearcher {
	private ExecutorService executor = Executors.newSingleThreadExecutor();
	private DBI dbi;
	private DatabaseInterface dbinterface;

	public HistorySearcher(String host, String user, String pass) {
		dbi = new DBI("jdbc:mysql://" + host + "/test?user=" + user + "&password=" + pass);
		dbi.registerMapper(new TransactionMapper());
		dbinterface = dbi.open(DatabaseInterface.class);
	}
	
	public Future<Transaction> getTransaction(String hash) {
		return executor.submit(() -> {
			return dbinterface.getTransaction(hash);
		});
	}
}
