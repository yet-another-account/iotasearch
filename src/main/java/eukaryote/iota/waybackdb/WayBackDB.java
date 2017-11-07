package eukaryote.iota.waybackdb;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.sql.DataSource;

import org.skife.jdbi.v2.DBI;

import jota.model.Transaction;

public class WayBackDB implements IWayBack {
	private ExecutorService executor = Executors.newSingleThreadExecutor();
	private DBI dbi;
	private DatabaseInterface dbinterface;

	public WayBackDB(String host, String user, String pass) {
		Properties props = new Properties();
		// eliminate the warning
		props.setProperty("useSSL", "false");

		dbi = new DBI(
				"jdbc:mysql://" + host.trim() + ":3306/iotaWayBack?user=" + user.trim() + "&password=" + pass.trim(),
				props);
		dbi.registerMapper(new TransactionMapper());
		dbinterface = dbi.open(DatabaseInterface.class);
	}

	@Override
	public Transaction getTransaction(String hash) {
		Transaction txn = dbinterface.getTransaction(hash);
		return txn;
	}
}
