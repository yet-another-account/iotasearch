package eukaryote.iota.explorer.db;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.sql.DataSource;

import org.skife.jdbi.v2.DBI;

import jota.model.Transaction;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class HistorySearcher {
	private ExecutorService executor = Executors.newSingleThreadExecutor();
	private DBI dbi;
	private DatabaseInterface dbinterface;

	public HistorySearcher(String host, String user, String pass) {
		log.info("Connecting to host {}", host);
		Properties props = new Properties();
		// eliminate the warning
		props.setProperty("useSSL", "false");

		dbi = new DBI(
				"jdbc:mysql://" + host.trim() + ":3306/iotaWayBack?user=" + user.trim() + "&password=" + pass.trim(),
				props);
		dbi.registerMapper(new TransactionMapper());
		dbinterface = dbi.open(DatabaseInterface.class);
	}

	public Future<Transaction> getTransactionFuture(String hash) {
		return executor.submit(() -> {
			Transaction txn = dbinterface.getTransaction(hash);
			log.info("Retrieved txn {}", txn);
			return txn;
		});
	}

	public Transaction getTransaction(String hash) {
		Transaction txn = dbinterface.getTransaction(hash);
		return txn;
	}

	public List<Transaction> getTransactions(List<String> hashes) {
		List<Transaction> txns = new ArrayList<>();
		for(String hash : hashes) {
			Transaction tx = this.getTransaction(hash);
			if (tx != null)
				txns.add(tx);
		}
		return txns;
	}
}
